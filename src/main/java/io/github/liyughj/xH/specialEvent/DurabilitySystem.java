package io.github.liyughj.xH.specialEvent;

import io.github.liyughj.xH.gun.AmmoConfig;
import io.github.liyughj.xH.gun.GunSystemConfig;
import io.github.liyughj.xH.gun.MagazineManager;
import io.github.liyughj.xH.lore.LoreManager;
import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 耐久系统管理器（specialEvent 版本）。
 * <p>
 * 统一管理武器/工具的耐久消耗、维修、惩罚等。
 *
 * <h3>核心属性（Category: GENERAL）</h3>
 * <ul>
 *   <li>{@code item_dura_max}                      — 最大耐久</li>
 *   <li>{@code item_dura_loss_per_use}             — 每次使用消耗的耐久</li>
 *   <li>{@code item_dura_consumption_coefficient}  — 耐久消耗系数（>1多消耗，默认100%=1.0）</li>
 *   <li>{@code item_dura_threshold}                — 耐久阀%（低于此值开始增加负面概率）</li>
 *   <li>{@code item_dura_threshold_penalty_factor} — 每低于阀值1%增加的概率%</li>
 *   <li>{@code item_dura_repair_coefficient_factor}— 每次维修增加的消耗系数%</li>
 *   <li>{@code item_dura_warning_threshold}        — 耐久警告阈值%</li>
 *   <li>{@code item_dura_spread_penalty}           — 耐久散布惩罚%</li>
 *   <li>{@code item_dura_recoil_penalty}           — 耐久后坐惩罚%</li>
 *   <li>{@code item_dura_broken_spread_penalty}    — 破损散布惩罚%</li>
 *   <li>{@code item_dura_broken_repairable}        — 破损可修复（1=可修复）</li>
 *   <li>{@code item_dura_repair_cost}              — 修理成本</li>
 *   <li>{@code item_dura_repair_material}          — 修理材料</li>
 * </ul>
 *
 * <h3>PDC 存储</h3>
 * <ul>
 *   <li>{@code xh:dura}               — 当前耐久值</li>
 *   <li>{@code xh:dura_repair_count}  — 维修次数（影响消耗系数）</li>
 * </ul>
 *
 * <h3>消耗公式</h3>
 * 实际消耗 = 基础消耗 × 消耗系数 × 弹药修正 + 热量额外损耗
 * 消耗系数 = 配置系数/100 × (1 + 维修次数 × 修复系数因子/100)
 *
 * <h3>耐久阀</h3>
 * 当耐久%低于阀值%时，每低1%增加 penalty_factor% 的故障概率。
 */
public class DurabilitySystem {

    private static final NamespacedKey KEY_DURA = new NamespacedKey("xh", "dura");
    private static final NamespacedKey KEY_REPAIR_COUNT = new NamespacedKey("xh", "dura_repair_count");

    /* ──────── 基础读写 ──────── */

    /** 获取当前耐久 */
    public static double getDurability(ItemStack weapon) {
        Double val = getPDC(weapon, KEY_DURA);
        if (val != null) return val;
        double max = getMaxDurability(weapon);
        setDurability(weapon, max);
        return max;
    }

    /** 设置当前耐久 */
    public static void setDurability(ItemStack weapon, double dura) {
        setPDC(weapon, KEY_DURA, dura);
    }

    /** 获取最大耐久 */
    public static double getMaxDurability(ItemStack weapon) {
        return AttributeStorage.getAttrValue(weapon, RpgAttribute.ITEM_DURA_MAX);
    }

    /** 获取耐久百分比（0~1），系统关闭时返回1.0 */
    public static double getDurabilityPercent(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "durability")) return 1.0;
        double max = getMaxDurability(weapon);
        if (max <= 0) return 1.0;
        return Math.max(0, getDurability(weapon) / max);
    }

    /* ──────── 维修次数 ──────── */

    /** 获取维修次数 */
    public static int getRepairCount(ItemStack weapon) {
        Integer val = getPDCInt(weapon, KEY_REPAIR_COUNT);
        return val != null ? val : 0;
    }

    /** 增加维修次数（+1） */
    private static void incRepairCount(ItemStack weapon) {
        int count = getRepairCount(weapon) + 1;
        setPDCInt(weapon, KEY_REPAIR_COUNT, count);
    }

    /* ──────── 消耗系数 ──────── */

    /**
     * 获取当前耐久消耗系数。
     * 配置系数/100 × (1 + 维修次数 × 修复系数因子/100)。
     */
    public static double getConsumptionCoefficient(ItemStack weapon) {
        // 配置中省略此属性时 getAttrValue 返回默认值 100（相当于1.0）
        double base = AttributeStorage.getAttrValue(weapon, RpgAttribute.ITEM_DURA_CONSUMPTION_COEFFICIENT) / 100.0;
        double repairFactor = AttributeStorage.getAttrValue(weapon, RpgAttribute.ITEM_DURA_REPAIR_COEFFICIENT_FACTOR) / 100.0;
        if (repairFactor > 0) {
            int repairs = getRepairCount(weapon);
            base *= (1.0 + repairs * repairFactor);
        }
        return base;
    }

    /* ──────── 耐久消耗 ──────── */

    /** 直接扣耐久（炸膛等外部调用）。返回 true = 破损 */
    public static boolean loseDurability(Player player, ItemStack weapon, double loss) {
        if (!GunSystemConfig.isSystemEnabled(player, "durability")) return false;

        double current = getDurability(weapon);
        double newVal = current - loss;
        setDurability(weapon, newVal);

        LoreManager.refreshGunLore(weapon);

        double max = getMaxDurability(weapon);
        double pct = Math.max(0, newVal / max);

        // 低耐久警告
        double warnThreshold = AttributeStorage.getAttrValue(weapon, RpgAttribute.ITEM_DURA_WARNING_THRESHOLD);
        if (warnThreshold > 0 && pct * 100 <= warnThreshold && pct > 0) {
            player.sendActionBar(Component.text(
                "武器耐久低! (" + (int)(pct * 100) + "%)", NamedTextColor.YELLOW));
        }

        if (newVal <= 0) {
            player.sendActionBar(Component.text("武器破损!", NamedTextColor.DARK_RED));
            return true;
        }
        return false;
    }

    /** 射击时扣除耐久 */
    public static boolean shootLoss(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "durability")) return false;

        // 基础消耗
        double loss = AttributeStorage.getAttrValue(weapon, RpgAttribute.ITEM_DURA_LOSS_PER_USE);

        // × 消耗系数
        loss *= getConsumptionCoefficient(weapon);

        // × 弹药修正
        AmmoConfig.AmmoTypeDef ammo = MagazineManager.peekNextAmmoTypeDef(weapon);
        if (ammo != null) loss *= ammo.durabilityMult;

        // + 热量额外损耗
        loss += HeatSystem.getHeatDuraLoss(player, weapon);

        return loseDurability(player, weapon, loss);
    }

    /* ──────── 散布/后坐惩罚 ──────── */

    /** 获取散布惩罚倍率 */
    public static double getSpreadPenalty(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "durability")) return 1.0;
        double pct = getDurabilityPercent(player, weapon);
        if (pct >= 1.0) return 1.0;
        double max = getMaxDurability(weapon);
        if (max <= 0) return 1.0;
        double penalty = AttributeStorage.getAttrValue(weapon, RpgAttribute.ITEM_DURA_SPREAD_PENALTY);
        double result = 1.0 + ((1.0 - pct) * penalty / 100.0);
        if (pct <= 0) {
            double brokenPenalty = AttributeStorage.getAttrValue(weapon, RpgAttribute.ITEM_DURA_BROKEN_SPREAD_PENALTY);
            result += brokenPenalty / 100.0;
        }
        return result;
    }

    /** 获取后坐惩罚倍率 */
    public static double getRecoilPenalty(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "durability")) return 1.0;
        double pct = getDurabilityPercent(player, weapon);
        if (pct >= 1.0) return 1.0;
        double penalty = AttributeStorage.getAttrValue(weapon, RpgAttribute.ITEM_DURA_RECOIL_PENALTY);
        return 1.0 + ((1.0 - pct) * penalty / 100.0);
    }

    /* ──────── 耐久阀（故障概率加成） ──────── */

    /**
     * 获取耐久阀提供的故障概率加成%。
     * 当前耐久%低于阀值%时才生效：加成 = (阀值% - 当前耐久%) × 惩罚因子/100。
     */
    public static double getDurabilityMalfunctionBonus(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "durability")) return 0;
        double threshold = AttributeStorage.getAttrValue(weapon, RpgAttribute.ITEM_DURA_THRESHOLD);
        if (threshold <= 0) return 0;
        double currentPct = getDurabilityPercent(player, weapon) * 100.0;
        if (currentPct >= threshold) return 0;
        double gap = threshold - currentPct;
        double penaltyFactor = AttributeStorage.getAttrValue(weapon, RpgAttribute.ITEM_DURA_THRESHOLD_PENALTY_FACTOR);
        return gap * penaltyFactor / 100.0;
    }

    /* ──────── 修复 ──────── */

    /** 修复耐久（增加amount，不超过最大值），并增加维修次数 */
    public static void repair(ItemStack weapon, double amount) {
        double max = getMaxDurability(weapon);
        double current = getDurability(weapon);
        setDurability(weapon, Math.min(max, current + amount));
        incRepairCount(weapon);
        LoreManager.refreshGunLore(weapon);
    }

    /** 完美修复（恢复满耐久），并增加维修次数 */
    public static void setDurabilityFullAndCount(ItemStack weapon) {
        setDurability(weapon, getMaxDurability(weapon));
        incRepairCount(weapon);
        LoreManager.refreshGunLore(weapon);
    }

    /** 是否已破损 */
    public static boolean isBroken(ItemStack weapon) {
        return getDurability(weapon) <= 0;
    }

    /** 是否可修复 */
    public static boolean isRepairable(ItemStack weapon) {
        return AttributeStorage.getAttrValue(weapon, RpgAttribute.ITEM_DURA_BROKEN_REPAIRABLE) >= 1.0;
    }

    /* ──────── PDC 工具 ──────── */

    private static Double getPDC(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
    }

    private static Integer getPDCInt(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
    }

    private static void setPDC(ItemStack item, NamespacedKey key, double value) {
        if (item == null) return;
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta()
            : org.bukkit.Bukkit.getItemFactory().getItemMeta(item.getType());
        if (meta == null) return;
        meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, value);
        item.setItemMeta(meta);
    }

    private static void setPDCInt(ItemStack item, NamespacedKey key, int value) {
        if (item == null) return;
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta()
            : org.bukkit.Bukkit.getItemFactory().getItemMeta(item.getType());
        if (meta == null) return;
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
        item.setItemMeta(meta);
    }
}
