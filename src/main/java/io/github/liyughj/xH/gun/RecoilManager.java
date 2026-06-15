package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeRange;
import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 后坐力状态管理器 —— 维护每个玩家每把枪的后坐累加/恢复状态。
 *
 * <pre>
 * 状态字段：
 *   accumulatedPitch   — 累计上跳度数（正数为向上偏移量）
 *   lastShootMs        — 上次射击时间戳
 *   fireCount          — 本次连射已发射数（恢复延时后归零）
 *   cooldownExpired    — 是否已过恢复延时
 * </pre>
 *
 * <h3>恢复方向</h3>
 * 后坐使准星上移（pitch减小/变负），恢复使准星回落（pitch增大）。
 * 这里 accumulatedPitch 存储"已施加的偏移量"，值越大表示准星越靠上。
 */
public final class RecoilManager {

    private static final Map<UUID, RecoilState> stateMap = new ConcurrentHashMap<>();
    private static long lastTickMs = System.currentTimeMillis();

    private RecoilManager() {}

    static RecoilState getOrCreate(UUID uuid) {
        return stateMap.computeIfAbsent(uuid, k -> new RecoilState());
    }

    static void remove(UUID uuid) {
        stateMap.remove(uuid);
    }

    /* ==================== 状态类 ==================== */

    public static final class RecoilState {
        /** 已累计上跳度数（需要回落的方向量） */
        double accumulatedPitch = 0.0;

        /** 上次射击时间戳 ms */
        long lastShootMs = 0;

        /** 连射计数（恢复延时重置后从0开始） */
        int fireCount = 0;

        /** 恢复延时已走完？ */
        boolean cooldownExpired = true;

        /* —— 武器属性缓存 —— */
        int    lastWeaponId;
        double weaponVertical        = 2.0;
        double weaponHorizontal      = 0.5;
        double weaponGrowth          = 0.1;
        double weaponMaxAngle        = 15.0;
        double weaponRecoveryPerSec  = 8.0;
        long   weaponResetDelayTicks = 2;
        double weaponFirstShotBonus  = 100.0;
        double weaponFirstShotCount  = 1.0;
        double weaponHorizontalBias  = 50.0;
        double weaponCrouchBonus     = 30.0;
        double weaponAdsBonus        = 50.0;
        int    weaponPattern         = 0;
        double weaponViewKick        = 0.0;

        public double getAccumulatedPitch() { return accumulatedPitch; }
    }

    /* ==================== 定时恢复（由 GunTickTask 每 5 tick 调用） ==================== */

    static void tickRecovery() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastTickMs;
        if (elapsed <= 0) { lastTickMs = now; return; }
        double seconds = elapsed / 1000.0;
        lastTickMs = now;

        for (RecoilState state : stateMap.values()) {
            if (state.lastShootMs <= 0 || state.accumulatedPitch <= 0) continue;

            long sinceLastShot = now - state.lastShootMs;
            long delayMs = state.weaponResetDelayTicks * 50L;

            // 处理恢复延时
            if (!state.cooldownExpired && state.weaponResetDelayTicks > 0) {
                if (sinceLastShot >= delayMs) {
                    state.cooldownExpired = true;
                    state.fireCount = 0;
                }
            }

            // 只有延时已过才恢复
            if (state.cooldownExpired || state.weaponResetDelayTicks <= 0) {
                double recover = state.weaponRecoveryPerSec * seconds;
                state.accumulatedPitch = Math.max(0.0, state.accumulatedPitch - recover);
            }
        }
    }

    /* ==================== 应用后坐到玩家视角 ==================== */

    /**
     * 计算本次后坐偏移量，直接旋转玩家视角。
     * 同时返回视角震动量供客户端抖动（暂为服务端简单实现）。
     *
     * @return { deltaPitch, deltaYaw, viewKick } — 正 pitch=向下看，后坐是负pitch增量
     */
    static double[] applyRecoil(Player player, ItemStack weapon) {
        RecoilState state = getOrCreate(player.getUniqueId());
        long now = System.currentTimeMillis();

        refreshWeaponSettings(state, weapon);

        /* —— 恢复延时检查 —— */
        long sinceLastShot = now - state.lastShootMs;
        if (state.weaponResetDelayTicks > 0 && state.lastShootMs > 0) {
            long delayMs = state.weaponResetDelayTicks * 50L;
            state.cooldownExpired = (sinceLastShot >= delayMs);
            if (state.cooldownExpired) {
                state.fireCount = 0;
            }
        }

        /* —— 判定是否首发 —— */
        boolean firstShot = (state.weaponFirstShotCount > 0)
            && (state.fireCount < (int) state.weaponFirstShotCount)
            && state.cooldownExpired;

        /* —— 计算本次垂直后坐 —— */
        double vertical = state.weaponVertical + state.weaponGrowth * state.fireCount;
        vertical = Math.min(vertical, state.weaponMaxAngle);

        /* 首发减免 */
        if (firstShot && state.weaponFirstShotBonus > 0) {
            vertical *= (1.0 - state.weaponFirstShotBonus / 100.0);
        }

        /* —— 后坐模式：决定水平偏移 —— */
        double horizontal = state.weaponHorizontal;
        double dX;
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();

        switch (state.weaponPattern) {
            case 0: // 直线：纯上跳，无水平
                dX = 0;
                break;
            case 1: // 锯齿：奇数发左，偶数发右
                dX = (state.fireCount % 2 == 0) ? horizontal : -horizontal;
                break;
            case 2: // S线：正弦映射
                double sinPhase = Math.sin(state.fireCount * Math.PI / 4.0);
                dX = sinPhase * horizontal;
                break;
            case 3: // 倒T：垂直减半，水平加倍
                vertical *= 0.5;
                dX = horizontal * 2.0 * (rng.nextBoolean() ? 1.0 : -1.0);
                break;
            default:
                dX = 0;
                break;
        }

        /* —— 水平偏向加权 —— */
        double bias = state.weaponHorizontalBias / 100.0; // 0=全左, 0.5=均匀, 1=全右
        // 把 dX 范围 [−H, +H] 偏置
        if (bias != 0.5) {
            double rand = rng.nextDouble();
            // power transform: bias<0.5→左偏, bias>0.5→右偏
            double power = 1.0 + Math.abs(bias - 0.5) * 4.0;
            double t = (bias > 0.5) ? Math.pow(rand, power) : (1.0 - Math.pow(1.0 - rand, power));
            // t ∈ [0,1], 0=全左(-H), 1=全右(+H)
            dX = horizontal * (t * 2.0 - 1.0);
        }

        /* —— 状态修正 —— */
        double mul = 1.0;
        if (player.isSneaking()) {
            mul *= (1.0 - state.weaponCrouchBonus / 100.0);
        }
        // 开镜修正
        if (AdsManager.isActive(player)) {
            mul *= (1.0 - state.weaponAdsBonus / 100.0);
        }

        /* —— 过热/耐久/弹药修正 —— */
        mul *= OverheatManager.getRecoilMultiplier(player, weapon);
        mul *= DurabilityManager.getRecoilPenalty(player, weapon);
        AmmoConfig.AmmoTypeDef ammo = MagazineManager.getCurrentAmmoType(weapon);
        if (ammo != null) mul *= ammo.recoilMult;

        // 压制修正
        mul *= SuppressionManager.getRecoilMultiplier(player);

        vertical *= mul;
        dX *= mul;

        // 垂直上限保护
        vertical = Math.max(0, vertical);

        /* —— 视角震动 —— */
        double kick = state.weaponViewKick;
        double kickPitch = kick > 0 ? (rng.nextDouble() * 2.0 - 1.0) * kick : 0;
        double kickYaw   = kick > 0 ? (rng.nextDouble() * 2.0 - 1.0) * kick : 0;

        /* —— 应用旋转到玩家（Paper Location 不可变，必须 teleport）—— */
        // 后坐使准星上移 → pitch减小
        Location loc = player.getLocation();
        float newPitch = (float) (loc.getPitch() - vertical + kickPitch);
        float newYaw   = (float) (loc.getYaw() + dX + kickYaw);

        // clamp pitch to [-90, 90]
        if (newPitch < -90f) newPitch = -90f;
        if (newPitch > 90f) newPitch = 90f;
        // wrap yaw
        newYaw = newYaw % 360f;
        if (newYaw < -180f) newYaw += 360f;
        if (newYaw > 180f) newYaw -= 360f;

        loc.setYaw(newYaw);
        loc.setPitch(newPitch);
        player.teleport(loc);

        /* —— 累加并记录 —— */
        state.accumulatedPitch += vertical;
        state.lastShootMs = now;
        state.fireCount++;

        return new double[]{vertical, dX, kick};
    }

    /* ==================== 内部 ==================== */

    private static void refreshWeaponSettings(RecoilState state, ItemStack weapon) {
        int weaponId = System.identityHashCode(weapon);
        if (weaponId == state.lastWeaponId) return;
        state.lastWeaponId = weaponId;

        state.weaponVertical        = rollIfConfig(weapon, RpgAttribute.GUN_RECOIL_VERTICAL);
        state.weaponHorizontal      = rollIfConfig(weapon, RpgAttribute.GUN_RECOIL_HORIZONTAL);
        state.weaponGrowth          = rollIfConfig(weapon, RpgAttribute.GUN_RECOIL_GROWTH);
        state.weaponMaxAngle        = rollIfConfig(weapon, RpgAttribute.GUN_RECOIL_MAX);
        state.weaponRecoveryPerSec  = rollIfConfig(weapon, RpgAttribute.GUN_RECOIL_RECOVERY);
        state.weaponResetDelayTicks = (long) rollIfConfig(weapon, RpgAttribute.GUN_RECOIL_RESET_DELAY);
        state.weaponFirstShotBonus  = rollIfConfig(weapon, RpgAttribute.GUN_RECOIL_FIRSTSHOT_BONUS);
        state.weaponFirstShotCount  = rollIfConfig(weapon, RpgAttribute.GUN_RECOIL_FIRSTSHOT_COUNT);
        state.weaponHorizontalBias  = rollIfConfig(weapon, RpgAttribute.GUN_RECOIL_HORIZONTAL_BIAS);
        state.weaponCrouchBonus     = rollIfConfig(weapon, RpgAttribute.GUN_RECOIL_CROUCH);
        state.weaponAdsBonus        = rollIfConfig(weapon, RpgAttribute.GUN_RECOIL_ADS);
        state.weaponPattern         = (int) rollIfConfig(weapon, RpgAttribute.GUN_RECOIL_PATTERN);
        state.weaponViewKick        = rollIfConfig(weapon, RpgAttribute.GUN_RECOIL_VIEW_KICK);
    }

    private static double rollIfConfig(ItemStack item, RpgAttribute attr) {
        AttributeRange range = AttributeStorage.getItemAttrRange(item, attr);
        if (range.getMin() == attr.getDefaultValue() && range.getMax() == attr.getDefaultValue()) {
            return attr.getDefaultValue();
        }
        return range.roll();
    }
}
