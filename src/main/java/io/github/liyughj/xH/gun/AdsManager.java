package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 开镜状态管理器 —— ADS 开关 + 呼吸/屏息系统。
 *
 * <h3>呼吸值机制</h3>
 * <ol>
 *   <li>开镜后默认自动屏息（消耗呼吸值）</li>
 *   <li>呼吸值低于 max×threshold% 时自动停止屏息，标记 exhausted</li>
 *   <li>exhausted 后开始恢复呼吸值，恢复至≥阈值时清除 exhausted</li>
 *   <li>退出 ADS 再进入：若 exhausted，默认不屏息；否则默认屏息</li>
 * </ol>
 *
 * <h3>开镜晃动</h3>
 * 不修改视角，通过 {@link SpreadCalculator} 注入散布加成。
 *
 * <h3>热成像</h3>
 * 扫描 FOV 内实体施加 Glowing，每玩家独立 thermalTargets 集合。
 */
public final class AdsManager {

    private static final Map<UUID, AdsState> stateMap = new ConcurrentHashMap<>();
    private static final int ADS_SLOWNESS_LEVEL = 0;

    private AdsManager() {}

    /* ==================== 状态 ==================== */

    static final class AdsState {
        boolean active;
        double magnification;
        float walkSpeed;

        // 呼吸值系统
        double breathMax;
        double breathValue;
        double breathDrain;
        double breathRegen;
        double breathThreshold;
        boolean breathHolding;
        boolean exhausted;
        /** 热成像：当前帧已套上 Glowing 的实体（每玩家独立） */
        final Set<LivingEntity> thermalTargets = new HashSet<>();

        // 渐入渐出
         int transitionTimer;
         int adsInTicks;
         int adsOutTicks;
         double adsMoveSpeed;       // 开镜移速乘数（100=正常）

        /** 渐入完成度 [0,1]，1=完全进入 */
        float transitionProgress() {
            if (adsInTicks <= 0) return 1f;
            return Math.min(1f, (float) transitionTimer / adsInTicks);
        }

        AdsState() {
            this.walkSpeed = 0.2f;
            this.breathValue = 100;
            this.breathMax = 100;
            this.breathThreshold = 30;
            this.exhausted = false;
        }
    }

    static AdsState getOrCreate(UUID uuid) {
        return stateMap.computeIfAbsent(uuid, k -> new AdsState());
    }

    /* ==================== 开/关镜 ==================== */

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

    static double getMagnification(Player player) {
        AdsState s = stateMap.get(player.getUniqueId());
        return (s != null && s.active) ? s.magnification : 1.0;
    }

    static boolean isBreathHolding(Player player) {
        AdsState s = stateMap.get(player.getUniqueId());
        return s != null && s.active && s.breathHolding;
    }

    /** 开镜晃动倍率（散布修正用） */
    static double getSwayMultiplier(Player player, ItemStack weapon) {
        if (!isActive(player)) return 1.0;
        double sway = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_ADS_SWAY_AMOUNT);
        if (sway <= 0) return 1.0;
        if (isBreathHolding(player)) sway *= 0.3;
        return 1.0 + sway / 100.0;
    }

    /* ==================== tick ==================== */

    static void tick(Player player) {
        AdsState s = stateMap.get(player.getUniqueId());
        if (s == null) return;

        // 渐入渐出计时
         if (s.active) {
             if (s.transitionTimer < s.adsInTicks) {
                 s.transitionTimer++;
                 float progress = s.transitionProgress();
                 // 从基准移速线性过渡到 adsMoveSpeed（避免与 MobilityManager 循环覆盖）
                 float target = (float) Math.max(0.02f, s.walkSpeed * s.adsMoveSpeed);
                 float interp = (float) (s.walkSpeed + (target - s.walkSpeed) * progress);
                 player.setWalkSpeed((float) Math.max(0.02f, interp));
             }
         }

        if (s.active && s.breathHolding) {
            s.breathValue -= s.breathDrain;
            if (s.breathValue <= s.breathMax * s.breathThreshold / 100.0) {
                s.breathValue = Math.max(0, s.breathMax * s.breathThreshold / 100.0);
                s.breathHolding = false;
                s.exhausted = true;
            }
        } else {
            if (s.breathValue < s.breathMax) {
                s.breathValue += s.breathRegen;
                if (s.breathValue >= s.breathMax) {
                    s.breathValue = s.breathMax;
                    s.exhausted = false;
                }
                if (s.exhausted && s.breathValue > s.breathMax * s.breathThreshold / 100.0) {
                    s.exhausted = false;
                }
            }
        }
    }

    /* ==================== 热成像 ==================== */

    static void thermalGlowTick(Player player) {
        AdsState s = stateMap.get(player.getUniqueId());
        if (s == null || !s.active) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!GunListener.isGunStatic(weapon)) return;

        if (AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_ADS_SCOPE_TYPE) < 5.0) return;

        double range = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_RANGE);
        if (range <= 0) range = 60;

        // 清除旧帧 Glowing
        for (LivingEntity e : s.thermalTargets) {
            if (e.isValid()) e.removePotionEffect(PotionEffectType.GLOWING);
        }
        s.thermalTargets.clear();

        for (Entity e : player.getNearbyEntities(range, range, range)) {
            if (!(e instanceof LivingEntity le) || le == player) continue;
            if (!le.isValid() || le.isDead()) continue;

            var toEntity = le.getEyeLocation().toVector()
                .subtract(player.getEyeLocation().toVector());
            if (toEntity.length() < 0.1) continue;
            double dot = player.getEyeLocation().getDirection().dot(toEntity.normalize());
            if (dot < 0.5) continue;

            var blockResult = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(), toEntity.normalize(), toEntity.length());
            if (blockResult != null && blockResult.getHitBlock() != null) continue;

            le.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING, 20, 0, false, false, false));
            s.thermalTargets.add(le);
        }
    }

    /* ==================== 内部 ==================== */

    private static void enter(Player player, ItemStack weapon, AdsState state) {
        state.active = true;
        state.magnification = ScopeProvider.compute(weapon);

        state.breathMax = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_ADS_BREATH_MAX);
        state.breathDrain = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_ADS_BREATH_DRAIN);
        state.breathRegen = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_ADS_BREATH_REGEN);
        state.breathThreshold = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_ADS_BREATH_THRESHOLD);

        if (state.exhausted) {
            state.breathHolding = false;
        } else {
            state.breathHolding = true;
            if (state.breathValue <= 0) state.breathValue = state.breathMax;
        }

        // 渐入渐出
        state.adsInTicks = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_ADS_IN_TIME_TICKS);
        state.adsOutTicks = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_ADS_OUT_TIME_TICKS);
        state.transitionTimer = 0;
        state.adsMoveSpeed = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_ADS_MOVE_SPEED) / 100.0;

        // 渐入基准速度：当前 MobilityManager 设置的移速（用于退出时恢复）
        state.walkSpeed = player.getWalkSpeed();

        player.addPotionEffect(new PotionEffect(
            PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION,
            ADS_SLOWNESS_LEVEL, false, false, true));

        if (AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_ADS_NIGHT_VISION) >= 1.0) {
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION,
                0, false, false, true));
        }
    }

    private static void exit(Player player, AdsState state) {
        state.active = false;
        state.magnification = 1.0;
        state.breathHolding = false;

        // 清除热成像
        for (LivingEntity e : state.thermalTargets) {
            if (e.isValid()) e.removePotionEffect(PotionEffectType.GLOWING);
        }
        state.thermalTargets.clear();

        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);

        // 恢复移速到进 ADS 前的值（即 MobilityManager 设置的移速）
        // 如果玩家已不持枪，则恢复默认值
        if (GunListener.isGunStatic(player.getInventory().getItemInMainHand())) {
            player.setWalkSpeed(Math.max(0.05f, state.walkSpeed));
        } else {
            player.setWalkSpeed(0.2f);
        }
    }

    /** 完全移除玩家 ADS 状态（死亡/退出时调用），不修改移速（由 MobilityManager 处理） */
    static void remove(Player player) {
        AdsState state = stateMap.remove(player.getUniqueId());
        if (state == null) return;
        for (LivingEntity e : state.thermalTargets) {
            if (e.isValid()) e.removePotionEffect(PotionEffectType.GLOWING);
        }
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }
}
