package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 过热系统管理器。
 * 每发射击积累热量，超过阈值触发过热惩罚（禁止射击 + 散布/后坐变大）。
 * 未射击时自然冷却。
 */
public class OverheatManager {

    private static final Map<UUID, OverheatState> states = new ConcurrentHashMap<>();

    public static class OverheatState {
        double heat;           // 当前热量
        boolean overheated;    // 过热状态中
        long penaltyUntilTick; // 过热惩罚结束的世界tick（0=无惩罚）
    }

    /** 获取或创建玩家过热状态 */
    private static OverheatState getState(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), k -> new OverheatState());
    }

    /** 每发射击后调用，返回 true 表示触发过热禁止射击 */
    public static boolean addHeat(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "overheat")) return false;

        OverheatState state = getState(player);
        if (state.overheated) return true; // 仍然过热

        double heatPerShot = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_PER_SHOT);

        // 弹药热量修正
        AmmoConfig.AmmoTypeDef ammo = MagazineManager.getCurrentAmmoType(weapon);
        if (ammo != null) heatPerShot *= ammo.heatMult;

        state.heat += heatPerShot;

        double threshold = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_THRESHOLD);
        if (state.heat >= threshold) {
            // 触发过热
            state.overheated = true;
            double penaltyTicks = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_OVERHEAT_PENALTY_TICKS);
            state.penaltyUntilTick = currentTick(player) + (long) penaltyTicks;

            player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.5f, 1.0f);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                "过热! 冷却中...", net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }
        return false;
    }

    /** 每tick冷却，射击间隔时调用 */
    public static void doCool(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "overheat")) return;

        OverheatState state = getState(player);
        long now = currentTick(player);

        // 过热冷却检查
        if (state.overheated) {
            if (now >= state.penaltyUntilTick) {
                state.overheated = false;
                state.heat = 0; // 过热后完全冷却
                state.penaltyUntilTick = 0;
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "冷却完成", net.kyori.adventure.text.format.NamedTextColor.GREEN));
            }
            return;
        }

        if (state.heat <= 0) return;

        // 未持枪时使用默认冷却速率 10/s
        double coolRate;
        if (weapon != null) {
            coolRate = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_COOL_RATE);
            // 开镜冷却加成
            if (AdsManager.isActive(player)) {
                double adsBonus = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_ADS_COOL_BONUS);
                coolRate *= (adsBonus / 100.0);
            }
        } else {
            coolRate = 10.0;
        }

        // 每秒冷却，tick粒度
        state.heat = Math.max(0, state.heat - coolRate / 20.0);
    }

    /** 获取热量百分比（0~1） */
    public static double getHeatPercent(Player player) {
        OverheatState state = states.get(player.getUniqueId());
        return state != null ? state.heat : 0;
    }

    /** 获取散布因子（热量导数），已经转为倍率 */
    public static double getSpreadMultiplier(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "overheat")) return 1.0;
        double heatPct = getHeatPercent(player);
        if (heatPct <= 0) return 1.0;
        double factor = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_SPREAD_FACTOR);
        return 1.0 + (heatPct / 100.0) * (factor / 100.0);
    }

    /** 获取后坐因子 */
    public static double getRecoilMultiplier(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "overheat")) return 1.0;
        double heatPct = getHeatPercent(player);
        if (heatPct <= 0) return 1.0;
        double factor = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_RECOIL_FACTOR);
        return 1.0 + (heatPct / 100.0) * (factor / 100.0);
    }

    /** 获取故障率加成 */
    public static double getMalfunctionBonus(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "overheat")) return 0;
        double heatPct = getHeatPercent(player);
        if (heatPct <= 0) return 0;
        double factor = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HEAT_MALFUNCTION_FACTOR);
        return (heatPct / 100.0) * (factor / 100.0);
    }

    /** 是否处于过热状态 */
    public static boolean isOverheated(Player player) {
        OverheatState state = states.get(player.getUniqueId());
        return state != null && state.overheated;
    }

    /** 清除玩家状态（离线/切换世界） */
    public static void remove(Player player) {
        states.remove(player.getUniqueId());
    }

    private static long currentTick(Player player) {
        return player.getWorld().getGameTime();
    }
}
