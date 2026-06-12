package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeRange;
import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 散布状态管理器 —— 维护每个玩家每把枪的扩散累加/恢复状态。
 *
 * <pre>
 * 状态字段：
 *   currentAngle      — 当前扩散角（min ≤ current ≤ max）
 *   lastShootMs       — 上次射击时间戳
 *   firstShotCount    — 已连射发数（停火重置后归零）
 *   cooldownExpired   — 是否已过恢复延时
 * </pre>
 */
public final class SpreadManager {

    /* ==================== 状态存储 ==================== */

    private static final Map<UUID, SpreadState> stateMap = new ConcurrentHashMap<>();
    private static long lastTickMs = System.currentTimeMillis();

    private SpreadManager() {}

    static SpreadState getOrCreate(UUID uuid) {
        return stateMap.computeIfAbsent(uuid, k -> new SpreadState());
    }

    static void remove(UUID uuid) {
        stateMap.remove(uuid);
    }

    /* ==================== 状态类 ==================== */

    public static final class SpreadState {
        /** 当前扩散角（度） */
        double currentAngle = 0.0;

        /** 上次射击时间戳 ms */
        long lastShootMs = 0;

        /** 已连射发数（停火重置后从0开始） */
        int firstShotCount = 0;

        /** 恢复延时已走完？ */
        boolean cooldownExpired = true;

        /* —— 武器属性缓存（切换武器时刷新） —— */
        int    lastWeaponId;
        double weaponMinAngle        = 0.0;
        double weaponMaxAngle        = 5.0;
        double weaponGrowth          = 0.5;
        double weaponRecoveryPerSec  = 3.0;
        long   weaponResetDelayTicks = 0;
        double weaponFirstShotBonus  = 100.0;
        double weaponFirstShotCount  = 1.0;
        double weaponMovePenalty     = 0.0;
        double weaponJumpPenalty     = 80.0;
        double weaponCrouchBonus     = 30.0;
        double weaponAdsBonus        = 50.0;

        public double getCurrentAngle() { return currentAngle; }
    }

    /* ==================== 定时恢复（tickRecovery 由 GunTickTask 每20tick调用） ==================== */

    static void tickRecovery() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastTickMs;
        if (elapsed <= 0) { lastTickMs = now; return; }
        double seconds = elapsed / 1000.0;
        lastTickMs = now;

        for (SpreadState state : stateMap.values()) {
            if (state.lastShootMs <= 0) continue;

            long sinceLastShot = now - state.lastShootMs;
            long delayMs = state.weaponResetDelayTicks * 50L;

            // 处理恢复延时
            if (!state.cooldownExpired && state.weaponResetDelayTicks > 0) {
                if (sinceLastShot >= delayMs) {
                    state.cooldownExpired = true;
                    state.firstShotCount = 0;
                }
            }

            // 只有延时已过或无延时才恢复
            if (state.cooldownExpired || state.weaponResetDelayTicks <= 0) {
                double recover = state.weaponRecoveryPerSec * seconds;
                state.currentAngle = Math.max(state.weaponMinAngle,
                    state.currentAngle - recover);
            }
        }
    }

    /* ==================== 射击时消费扩散（SpreadCalculator 调用） ==================== */

    /**
     * @return 本次有效扩散角（度，已含首发/移动/蹲下/跳跃修正）
     */
    static double consumeSpread(Player player, ItemStack weapon) {
        SpreadState state = getOrCreate(player.getUniqueId());
        long now = System.currentTimeMillis();

        refreshWeaponSettings(state, weapon);

        /* —— 恢复延时检查 —— */
        long sinceLastShot = now - state.lastShootMs;
        if (state.weaponResetDelayTicks > 0 && state.lastShootMs > 0) {
            long delayMs = state.weaponResetDelayTicks * 50L;
            state.cooldownExpired = (sinceLastShot >= delayMs);
            if (state.cooldownExpired) {
                state.firstShotCount = 0;
            }
        }

        /* —— 判定是否首发 —— */
        boolean firstShot = (state.weaponFirstShotCount > 0)
            && (state.firstShotCount < (int) state.weaponFirstShotCount)
            && state.cooldownExpired;

        /* —— 当前扩散 —— */
        double angle = state.currentAngle;

        /* 首发精度加成 */
        if (firstShot && state.weaponFirstShotBonus > 0) {
            angle = state.currentAngle * (1.0 - state.weaponFirstShotBonus / 100.0);
            angle = Math.max(state.weaponMinAngle, angle);
        }

        /* —— 状态修正（乘算） —— */
        double mul = 1.0;

        // 移动中：sprinting || 有水平速度
        if (player.isSprinting() || player.getVelocity().lengthSquared() > 0.007) {
            mul *= (1.0 + state.weaponMovePenalty / 100.0);
        }

        // 空中（跳跃/掉落）—— 服务端检查脚下方块，避免 isOnGround() 客户端欺骗
        if (player.getLocation().clone().subtract(0, 0.1, 0).getBlock().isEmpty()) {
            mul *= (1.0 + state.weaponJumpPenalty / 100.0);
        }

        // 蹲下
        if (player.isSneaking()) {
            mul *= (1.0 - state.weaponCrouchBonus / 100.0);
        }

        // 开镜修正
        if (AdsManager.isActive(player)) {
            mul *= (1.0 - state.weaponAdsBonus / 100.0);
        }

        // 压制修正
        mul *= SuppressionManager.getSpreadMultiplier(player);

        angle *= mul;
        angle = Math.max(0.0, angle);
        angle = Math.min(state.weaponMaxAngle, Math.max(state.weaponMinAngle, angle));

        double resultAngle = angle;

        /* —— 扩散累加 —— */
        state.currentAngle = Math.min(state.weaponMaxAngle,
            state.currentAngle + state.weaponGrowth);
        state.lastShootMs = now;
        state.firstShotCount++;

        return resultAngle;
    }

    /* ==================== 内部 ==================== */

    /** 如果武器已切换，重新读取覆盖属性缓存 */
    private static void refreshWeaponSettings(SpreadState state, ItemStack weapon) {
        int weaponId = System.identityHashCode(weapon);
        if (weaponId == state.lastWeaponId) return;
        state.lastWeaponId = weaponId;

        state.weaponMinAngle        = rollIfConfigured(weapon, RpgAttribute.GUN_SPREAD_MIN);
        state.weaponMaxAngle        = rollIfConfigured(weapon, RpgAttribute.GUN_SPREAD_MAX);
        state.weaponGrowth          = rollIfConfigured(weapon, RpgAttribute.GUN_SPREAD_GROWTH);
        state.weaponRecoveryPerSec  = rollIfConfigured(weapon, RpgAttribute.GUN_SPREAD_RECOVERY);
        state.weaponResetDelayTicks = (long) rollIfConfigured(weapon, RpgAttribute.GUN_SPREAD_RESET_DELAY);
        state.weaponFirstShotBonus  = rollIfConfigured(weapon, RpgAttribute.GUN_SPREAD_FIRSTSHOT_BONUS);
        state.weaponFirstShotCount  = rollIfConfigured(weapon, RpgAttribute.GUN_SPREAD_FIRSTSHOT_COUNT);
        state.weaponMovePenalty     = rollIfConfigured(weapon, RpgAttribute.GUN_SPREAD_MOVE);
        state.weaponJumpPenalty     = rollIfConfigured(weapon, RpgAttribute.GUN_SPREAD_JUMP);
        state.weaponCrouchBonus     = rollIfConfigured(weapon, RpgAttribute.GUN_SPREAD_CROUCH);
        state.weaponAdsBonus        = rollIfConfigured(weapon, RpgAttribute.GUN_SPREAD_ADS);
    }

    private static double rollIfConfigured(ItemStack item, RpgAttribute attr) {
        AttributeRange range = AttributeStorage.getItemAttrRange(item, attr);
        if (range.getMin() == attr.getDefaultValue() && range.getMax() == attr.getDefaultValue()) {
            return attr.getDefaultValue();
        }
        return range.roll();
    }
}
