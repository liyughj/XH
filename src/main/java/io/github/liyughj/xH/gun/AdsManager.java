package io.github.liyughj.xH.gun;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 开镜状态管理器 —— 管理每玩家 ADS 开关状态、倍率、FOV 效果。
 *
 * <h3>FOV 实现</h3>
 * 目前通过施加缓慢效果模拟开镜减速。实际视觉 FOV 由客户端控制，
 * 此处保留 FOV 倍率供未来发包方案使用。
 */
public final class AdsManager {

    private static final Map<UUID, AdsState> stateMap = new ConcurrentHashMap<>();

    /** 开镜时施加的缓慢等级（0 = I 级 = -15% 速度） */
    private static final int ADS_SLOWNESS_LEVEL = 0;

    private AdsManager() {}

    /* ==================== 状态 ==================== */

    static final class AdsState {
        boolean active;       // 是否开镜中
        double magnification; // 当前倍率（1.0 = 1×）
        float  walkSpeed;     // 开镜前速度（退出时恢复）

        AdsState() {
            this.walkSpeed = 0.2f; // 默认步行速度
        }
    }

    static AdsState getOrCreate(UUID uuid) {
        return stateMap.computeIfAbsent(uuid, k -> new AdsState());
    }

    /* ==================== 开/关镜 ==================== */

    /** 切换开镜状态。开镜 → 关，关 → 开。 */
    static boolean toggle(Player player, ItemStack weapon) {
        AdsState state = getOrCreate(player.getUniqueId());
        if (state.active) {
            exit(player, state);
            return false;
        } else {
            enter(player, weapon, state);
            return true;
        }
    }

    /** 强制退出开镜 */
    static void forceExit(Player player) {
        AdsState state = stateMap.get(player.getUniqueId());
        if (state != null && state.active) {
            exit(player, state);
        }
    }

    static boolean isActive(Player player) {
        AdsState s = stateMap.get(player.getUniqueId());
        return s != null && s.active;
    }

    /** 获取当前倍率（未开镜返回 1.0） */
    static double getMagnification(Player player) {
        AdsState s = stateMap.get(player.getUniqueId());
        return (s != null && s.active) ? s.magnification : 1.0;
    }

    /* ==================== 内部 ==================== */

    private static void enter(Player player, ItemStack weapon, AdsState state) {
        state.active = true;
        // 通过 ScopeProvider 链计算倍率
        state.magnification = ScopeProvider.compute(weapon);
        // 保存原速并减慢
        state.walkSpeed = player.getWalkSpeed();
        float adsSpeed = state.walkSpeed * 0.5f; // 开镜半速
        player.setWalkSpeed(Math.max(0.05f, adsSpeed));
        // 缓慢药水效果（轻微减速 + 画面效果）
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION,
            ADS_SLOWNESS_LEVEL, false, false, true));
    }

    private static void exit(Player player, AdsState state) {
        state.active = false;
        state.magnification = 1.0;
        player.setWalkSpeed(Math.max(0.05f, state.walkSpeed));
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }
}
