package io.github.liyughj.xH.specialEvent;

import io.github.liyughj.xH.gun.GunSystemConfig;
import io.github.liyughj.xH.gun.MagazineManager;
import io.github.liyughj.xH.gun.AdsManager;
import io.github.liyughj.xH.gun.AmmoConfig;
import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 热量系统管理器（specialEvent 版本）。
 * <p>
 * 每发射击增加热量，停止射击时自然冷却。
 * 热量值以 {@code gun_heat_max} 为上限，通过百分比判定各类事件阈值。
 * <p>
 * 热量状态存储在武器 PDC 中（xh:heat, xh:heat_penalty），每把枪独立。
 *
 * <h3>核心属性</h3>
 * <ul>
 *   <li>{@code gun_heat_max}           — 最大热量（热量上限）</li>
 *   <li>{@code gun_heat_per_shot}      — 单发热量（热量系数）</li>
 *   <li>{@code gun_heat_cool_rate}     — 冷却速率/秒（冷却系数）</li>
 *   <li>{@code gun_heat_ads_cool_bonus}— 开镜冷却加成（%）</li>
 *   <li>{@code gun_heat_smoke_threshold} — 冒烟热量阈值（%）</li>
 * </ul>
 *
 * <h3>事件触发阈值（占最大热量的百分比）</h3>
 * <ul>
 *   <li>{@code gun_heat_overheat_trigger} — 热量% ≥ 此值触发过热（禁止射击+惩罚持续）</li>
 *   <li>{@code gun_heat_malfunc_trigger}  — 热量% ≥ 此值才可能触发卡壳/哑火/炸膛</li>
 *   <li>{@code gun_heat_dura_loss_max}    — 热量100%时每发额外耐久损耗上限</li>
 * </ul>
 *
 * <h3>已有惩罚因子（热量线性插值）</h3>
 * <ul>
 *   <li>{@code gun_heat_spread_factor} — 散布因子</li>
 *   <li>{@code gun_heat_recoil_factor} — 后坐因子</li>
 *   <li>{@code gun_heat_malfunction_factor} — 故障因子</li>
 * </ul>
 *
 * <h3>PDC 存储</h3>
 * <ul>
 *   <li>{@code xh:heat}           — 当前热量值（DOUBLE）</li>
 *   <li>{@code xh:heat_penalty}   — 过热惩罚结束的世界tick（LONG，0=无惩罚）</li>
 * </ul>
 */
public class HeatSystem {

    private static final NamespacedKey KEY_HEAT = new NamespacedKey("xh", "heat");
    private static final NamespacedKey KEY_PENALTY = new NamespacedKey("xh", "heat_penalty");

    /** 获取热量百分比（0~1），忽略过热系统开关时返回0 */
    public static double getHeatPercent(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "overheat")) return 0;
        if (weapon == null) return 0;
        double heat = getPDC(weapon, KEY_HEAT);
        if (heat <= 0) return 0;
        double max = getMaxHeat(weapon);
        if (max <= 0) return 0;
        return Math.min(1.0, heat / max);
    }

    /** 获取枪械最大热量 */
    private static double getMaxHeat(ItemStack weapon) {
        return weapon != null ? AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_MAX) : 100.0;
    }

    /* ──────── 射击 ──────── */

    /**
     * 射击前调用。增加热量，检测是否过热。
     *
     * @return true = 过热禁止射击
     */
    public static boolean onShoot(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "overheat")) return false;

        // 过热惩罚期间仍然禁止射击
        if (isPenaltyActive(player, weapon)) return true;

        double heat = getPDC(weapon, KEY_HEAT);

        // 热量系数：单发热量 + 弹药热量修正
        double heatPerShot = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_PER_SHOT);
        AmmoConfig.AmmoTypeDef ammo = MagazineManager.peekNextAmmoTypeDef(weapon);
        if (ammo != null) heatPerShot *= ammo.heatMult;

        heat = Math.min(getMaxHeat(weapon), heat + heatPerShot);
        setPDC(weapon, KEY_HEAT, heat);

        // 检测是否触发过热
        double triggerPct = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_OVERHEAT_TRIGGER);
        if (triggerPct > 0 && getHeatPercent(player, weapon) * 100.0 >= triggerPct) {
            double penaltyTicks = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_OVERHEAT_PENALTY_TICKS);
            long penaltyUntil = currentTick(player) + (long) penaltyTicks;
            setPDCLong(weapon, KEY_PENALTY, penaltyUntil);

            player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.5f, 1.0f);
            player.sendActionBar(Component.text("过热! 冷却中...", NamedTextColor.RED));
            return true;
        }
        return false;
    }

    /* ──────── 冷却 ──────── */

    /**
     * 每 tick 调用。自然冷却 + 过热惩罚倒计时。
     * 热量绑定在武器上，weapon 为 null 时不操作。
     */
    public static void doCool(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "overheat")) return;
        if (weapon == null) return;

        long now = currentTick(player);
        long penaltyUntil = getPDCLong(weapon, KEY_PENALTY);

        // 过热惩罚倒计时
        if (penaltyUntil > 0) {
            if (now >= penaltyUntil) {
                setPDCLong(weapon, KEY_PENALTY, 0);
                setPDC(weapon, KEY_HEAT, 0);
                player.sendActionBar(Component.text("冷却完成", NamedTextColor.GREEN));
            }
            return;
        }

        double heat = getPDC(weapon, KEY_HEAT);
        if (heat <= 0) return;

        // 冷却系数：冷却速率/秒
        double coolRate = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_COOL_RATE);
        // 开镜冷却加成
        if (AdsManager.isActive(player)) {
            double adsBonus = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_ADS_COOL_BONUS);
            coolRate *= (1.0 + adsBonus / 100.0);
        }

        heat = Math.max(0, heat - coolRate / 20.0);
        setPDC(weapon, KEY_HEAT, heat);
    }

    /* ──────── 惩罚倍率 ──────── */

    /** 获取散布倍率（热量线性插值到散布因子） */
    public static double getSpreadMultiplier(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "overheat")) return 1.0;
        double heatPct = getHeatPercent(player, weapon);
        if (heatPct <= 0) return 1.0;
        double factor = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_SPREAD_FACTOR);
        return 1.0 + heatPct * (factor / 100.0);
    }

    /** 获取后坐倍率 */
    public static double getRecoilMultiplier(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "overheat")) return 1.0;
        double heatPct = getHeatPercent(player, weapon);
        if (heatPct <= 0) return 1.0;
        double factor = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_RECOIL_FACTOR);
        return 1.0 + heatPct * (factor / 100.0);
    }

    /* ──────── 事件门控 ──────── */

    /**
     * 热量是否达到故障触发阈值。
     * 热量% ≥ {@code gun_heat_malfunc_trigger} 时返回 true。
     */
    public static boolean canMalfunction(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "overheat")) return true; // 系统关闭时允许故障
        double triggerPct = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_MALFUNC_TRIGGER);
        if (triggerPct <= 0) return true; // 未配置阈值时允许故障
        return getHeatPercent(player, weapon) * 100.0 >= triggerPct;
    }

    /**
     * 获取热量提供的故障率加成（%）。
     */
    public static double getMalfunctionBonus(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "overheat")) return 0;
        double heatPct = getHeatPercent(player, weapon);
        if (heatPct <= 0) return 0;
        double factor = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_MALFUNCTION_FACTOR);
        return heatPct * (factor / 100.0);
    }

    /* ──────── 热量耐久损耗 ──────── */

    /**
     * 获取热量导致的额外耐久损耗。
     * 线性插值：热量% × {@code gun_heat_dura_loss_max}。
     * 系统关闭或未配置时返回0。
     */
    public static double getHeatDuraLoss(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "overheat")) return 0;
        double maxLoss = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_DURA_LOSS_MAX);
        if (maxLoss <= 0) return 0;
        return getHeatPercent(player, weapon) * maxLoss;
    }

    /* ──────── 状态查询 ──────── */

    /** 是否处于过热惩罚中 */
    public static boolean isPenaltyActive(Player player, ItemStack weapon) {
        if (weapon == null) return false;
        long penaltyUntil = getPDCLong(weapon, KEY_PENALTY);
        if (penaltyUntil <= 0) return false;
        return currentTick(player) < penaltyUntil;
    }

    /**
     * 清除玩家状态（已废弃——热量存储在武器 PDC 中，不再需要清理玩家状态）。
     * 保留方法签名以兼容现有调用，方法体为空。
     */
    public static void remove(Player player) {
        // 热量状态存储在武器 PDC 中，无需清理玩家状态
    }

    /* ──────── PDC 工具 ──────── */

    private static double getPDC(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return 0;
        Double val = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
        return val != null ? val : 0;
    }

    private static long getPDCLong(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return 0;
        Long val = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.LONG);
        return val != null ? val : 0;
    }

    private static void setPDC(ItemStack item, NamespacedKey key, double value) {
        if (item == null) return;
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta()
            : org.bukkit.Bukkit.getItemFactory().getItemMeta(item.getType());
        if (meta == null) return;
        meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, value);
        item.setItemMeta(meta);
    }

    private static void setPDCLong(ItemStack item, NamespacedKey key, long value) {
        if (item == null) return;
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta()
            : org.bukkit.Bukkit.getItemFactory().getItemMeta(item.getType());
        if (meta == null) return;
        meta.getPersistentDataContainer().set(key, PersistentDataType.LONG, value);
        item.setItemMeta(meta);
    }

    private static long currentTick(Player player) {
        return player.getWorld().getGameTime();
    }
}
