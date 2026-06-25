package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 压制管理器 —— 开火时对附近敌人施加散布/后坐惩罚。
 *
 * <h3>什么算被压制</h3>
 * 满足以下全部条件时进入压制状态：
 * <ol>
 *   <li>有敌人在 {@code gun_suppress_radius} 范围内开枪</li>
 *   <li>开枪者 ≠ 自己（敌人）</li>
 *   <li>开枪者的枪有 {@code gun_suppress_amount > 0} 且 {@code gun_suppress_duration_ticks > 0}</li>
 * </ol>
 *
 * <h3>压制效果</h3>
 * <ul>
 *   <li>散布倍率 = 1 + amount/100</li>
 *   <li>后坐倍率 = 1 + amount/100</li>
 *   <li>持续 duration_ticks 后自动解除</li>
 * </ul>
 *
 * <h3>叠加策略：强者优先</h3>
 * 若已在压制中，新压制 amount 更大才覆盖；否则只延长 duration。
 */
public final class SuppressionManager {

    private static final Map<UUID, SuppressData> suppressed = new ConcurrentHashMap<>();

    static class SuppressData {
        final UUID sourceId;    // 压制者UUID
        int remainingTicks;     // 剩余tick
        double amount;          // 压制幅度（%）

        SuppressData(UUID sourceId, int ticks, double amount) {
            this.sourceId = sourceId;
            this.remainingTicks = ticks;
            this.amount = amount;
        }
    }

    private SuppressionManager() {}

    /** 是否正在被压制 */
    static boolean isSuppressed(Player player) {
        return suppressed.containsKey(player.getUniqueId());
    }

    /** 获取压制者UUID */
    static UUID getSuppressorUuid(Player player) {
        SuppressData d = suppressed.get(player.getUniqueId());
        return d != null ? d.sourceId : null;
    }

    /** 获取压制幅度（%） */
    static double getAmount(Player player) {
        SuppressData d = suppressed.get(player.getUniqueId());
        return d != null ? d.amount : 0;
    }

    /** 获取剩余压制tick */
    static int getRemainingTicks(Player player) {
        SuppressData d = suppressed.get(player.getUniqueId());
        return d != null ? d.remainingTicks : 0;
    }

    /** 散布倍率 = 1 + amount/100 */
    static double getSpreadMultiplier(Player player) {
        SuppressData d = suppressed.get(player.getUniqueId());
        return d != null ? (1.0 + d.amount / 100.0) : 1.0;
    }

    /** 后坐倍率 = 1 + amount/100 */
    static double getRecoilMultiplier(Player player) {
        SuppressData d = suppressed.get(player.getUniqueId());
        return d != null ? (1.0 + d.amount / 100.0) : 1.0;
    }

    /* ==================== 开火时施加压制 ==================== */

    /** 玩家开火时调用，对半径内敌人施加压制 */
    static void onShoot(Player shooter, ItemStack weapon) {
        double radius = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_SUPPRESS_RADIUS);
        if (radius <= 0) return;

        double amount  = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_SUPPRESS_AMOUNT);
        int duration   = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_SUPPRESS_DURATION_TICKS);

        if (amount <= 0 || duration <= 0) return;

        for (var e : shooter.getWorld().getNearbyEntities(
                shooter.getLocation(), radius, radius, radius)) {
            if (!(e instanceof LivingEntity le) || le == shooter) continue;
            if (!(e instanceof Player p)) continue;

            SuppressData existing = suppressed.get(p.getUniqueId());
            // 强者优先：amount 更大才覆盖，弱者只延长 duration
            if (existing == null || existing.remainingTicks <= 0 || amount >= existing.amount) {
                suppressed.put(p.getUniqueId(),
                    new SuppressData(shooter.getUniqueId(), duration, amount));
            } else {
                existing.remainingTicks = Math.max(existing.remainingTicks, duration);
            }
        }
    }

    /* ==================== tick 衰减 ==================== */

    static void tickAll() {
        suppressed.entrySet().removeIf(entry -> {
            entry.getValue().remainingTicks--;
            return entry.getValue().remainingTicks <= 0;
        });
    }

    /** 完全移除玩家压制状态（死亡/退出时调用），避免离线玩家状态残留 */
    static void remove(Player player) {
        suppressed.remove(player.getUniqueId());
    }
}
