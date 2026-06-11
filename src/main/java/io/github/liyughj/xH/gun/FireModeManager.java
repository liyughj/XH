package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeRange;
import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 射击模式状态机 —— 管理每玩家当前模式、连发进度、全自动状态。
 *
 * <h3>模式</h3>
 * <pre>
 *   0 = 安全 (Safe)     — 禁止射击
 *   1 = 单发 (Semi)     — 每次点击发射 1 发
 *   2 = 连发 (Burst)    — 每次点击连续发射 N 发
 *   3 = 全自动 (Auto)    — 点击激活，点击关闭（Toggle）
 * </pre>
 *
 * <h3>全自动 Toggle 机制</h3>
 * 因 Bukkit 无法直接检测"松手右键"事件，采用 Toggle 模式：
 * 点击右键 → 开始全自动；再次点击右键 → 停止全自动；
 * 左键点击 / 切槽 / F键切换模式 → 也停止全自动。
 */
public final class FireModeManager {

    private static final Map<UUID, FireState> stateMap = new ConcurrentHashMap<>();

    static final String[] MODE_NAMES = {"§7安全", "§a单发", "§e连发", "§c全自动"};

    private FireModeManager() {}

    /* ==================== 状态 ==================== */

    static final class FireState {
        int mode = 1;                // 当前模式 0~3
        boolean autoActive;          // 全自动是否激活中
        boolean burstActive;         // 连发是否正在连发中
        int burstRemaining;          // 连发剩余弹数
        long lastShootMs;            // 上次射击时间（RPM 控制）
        BukkitTask autoTask;         // 全自动调度任务引用

        // 武器属性缓存（避免每帧重复 roll）
        int  lastWeaponId;
        int  defaultMode        = 1;
        int  availableModes     = 15;
        int  burstCount         = 3;
        long burstIntervalMs    = 80;
        long autoDelayMs        = 0;
        long rpmIntervalMs      = 100; // 600 RPM
    }

    static FireState getOrCreate(UUID uuid) {
        return stateMap.computeIfAbsent(uuid, k -> {
            FireState s = new FireState();
            s.mode = 1; // 默认单发
            return s;
        });
    }

    /* ==================== 模式切换（F 键） ==================== */

    /**
     * F 键切换射击模式。跳过不可用的模式。
     * @return 切换后模式名（给提示用），null 表示无法切换
     */
    static String cycleMode(Player player, ItemStack weapon, JavaPlugin plugin) {
        FireState state = getOrCreate(player.getUniqueId());
        refreshSettings(state, weapon);

        /* 停止全自动 */
        stopAutoInternal(state);

        int availMask = state.availableModes;
        if (availMask <= 0 || Integer.bitCount(availMask) <= 1) {
            return null;
        }

        int next = state.mode;
        int attempts = 0;
        do {
            next = (next + 1) % 4;
            attempts++;
            if (attempts > 4) return null;
        } while ((availMask & (1 << next)) == 0);

        state.mode = next;
        player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.5f, 1.5f);
        return MODE_NAMES[next];
    }

    /* ==================== 射击 ==================== */

    /**
     * 点击开火。
     * <ul>
     *   <li>单发：直接发射 1 发</li>
     *   <li>连发：启动连发调度（不可中断）</li>
     *   <li>全自动：Toggle 开/关</li>
     *   <li>安全：不响应</li>
     * </ul>
     *
     * @return true=触发了射击动作
     */
    static boolean fire(Player player, ItemStack weapon, JavaPlugin plugin,
                        Runnable shootCallback) {
        FireState state = getOrCreate(player.getUniqueId());
        refreshSettings(state, weapon);

        int mode = state.mode;

        if (mode == 0) return false; // 安全

        /* -- 单发 -- */
        if (mode == 1) {
            if (!checkRpm(state)) return false;
            shootCallback.run();
            return true;
        }

        /* -- 连发 -- */
        if (mode == 2) {
            if (state.burstActive) return false;
            int count = Math.max(1, state.burstCount);
            long interval = Math.max(10, state.burstIntervalMs);
            state.burstActive = true;
            state.burstRemaining = count;
            scheduleBurst(player, plugin, shootCallback, state, interval);
            return true;
        }

        return false;
    }

    /**
     * 全自动 Toggle。
     * @return true=当前已激活（刚刚开启了），false=已关闭或未操作
     */
    static boolean toggleAuto(Player player, ItemStack weapon, JavaPlugin plugin,
                              Runnable shootCallback) {
        FireState state = getOrCreate(player.getUniqueId());
        refreshSettings(state, weapon);

        if (state.mode != 3) return false;

        if (state.autoActive) {
            stopAutoInternal(state);
            return false;
        }

        /* 启动全自动 */
        state.autoActive = true;
        long delay = Math.max(0, state.autoDelayMs);
        long interval = Math.max(1, state.rpmIntervalMs / 50L);

        // 先用 runTaskLater 处理首发延迟
        state.autoTask = new BukkitRunnable() {
            boolean firstShotFired = false;
            @Override
            public void run() {
                if (shouldStopAuto(player, state)) {
                    stopAutoInternal(state);
                    return;
                }
                shootCallback.run();
                if (!firstShotFired) {
                    firstShotFired = true;
                    // 重新调度为定期任务（RPM 间隔）
                    state.autoTask = new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (shouldStopAuto(player, state)) {
                                stopAutoInternal(state);
                                return;
                            }
                            shootCallback.run();
                        }
                    }.runTaskTimer(plugin, interval, interval);
                    this.cancel();
                }
            }
        }.runTaskLater(plugin, Math.max(0, delay / 50L));

        return true;
    }

    /** 停止全自动（公开，供外部事件调用） */
    static void stopAuto(Player player) {
        FireState state = stateMap.get(player.getUniqueId());
        if (state != null) stopAutoInternal(state);
    }

    static boolean isAutoActive(Player player) {
        FireState s = stateMap.get(player.getUniqueId());
        return s != null && s.autoActive;
    }

    static int getMode(Player player) {
        FireState s = stateMap.get(player.getUniqueId());
        return s != null ? s.mode : 1;
    }

    /* ==================== 刷新属性缓存 ==================== */

    static void refreshSettings(FireState state, ItemStack weapon) {
        if (weapon == null) return;
        int weaponId = System.identityHashCode(weapon);
        if (weaponId == state.lastWeaponId) return;
        state.lastWeaponId = weaponId;

        state.defaultMode     = (int) roll(weapon, RpgAttribute.GUN_FIRE_DEFAULT_MODE);
        state.availableModes  = (int) roll(weapon, RpgAttribute.GUN_FIRE_AVAILABLE_MODES);
        state.burstCount      = (int) roll(weapon, RpgAttribute.GUN_BURST_COUNT);
        state.burstIntervalMs = (long) roll(weapon, RpgAttribute.GUN_BURST_INTERVAL_MS);
        state.autoDelayMs     = (long) roll(weapon, RpgAttribute.GUN_AUTO_TRIGGER_DELAY_MS);
        state.rpmIntervalMs   = getRpmInterval(weapon);

        // 首次：切为默认模式
        if (state.mode == 0 && (state.availableModes & (1 << state.defaultMode)) != 0) {
            state.mode = state.defaultMode;
        }
        // 确保当前模式可用（配置变更后）
        if ((state.availableModes & (1 << state.mode)) == 0) {
            state.mode = state.defaultMode;
        }
    }

    /* ==================== 内部 ==================== */

    private static boolean shouldStopAuto(Player player, FireState state) {
        if (!player.isOnline()) return true;
        if (!state.autoActive) return true;
        if (state.mode != 3) return true;
        // 主手不再是枪 → 停止
        ItemStack weapon = player.getInventory().getItemInMainHand();
        return !GunListener.isGunStatic(weapon);
    }

    private static void stopAutoInternal(FireState state) {
        state.autoActive = false;
        if (state.autoTask != null) {
            state.autoTask.cancel();
            state.autoTask = null;
        }
    }

    private static boolean checkRpm(FireState state) {
        long now = System.currentTimeMillis();
        if (state.lastShootMs > 0 && now - state.lastShootMs < state.rpmIntervalMs) {
            return false;
        }
        state.lastShootMs = now;
        return true;
    }

    private static void scheduleBurst(Player player, JavaPlugin plugin,
                                      Runnable shootCallback, FireState state, long intervalMs) {
        long tickInterval = Math.max(1, intervalMs / 50L);
        new BukkitRunnable() {
            int fired = 0;
            final int total = state.burstRemaining;
            @Override
            public void run() {
                if (!player.isOnline() || !state.burstActive) {
                    cancel(); return;
                }
                if (fired >= total) {
                    state.burstActive = false;
                    cancel(); return;
                }
                shootCallback.run();
                fired++;
                if (fired >= total) {
                    state.burstActive = false;
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, tickInterval);
    }

    private static long getRpmInterval(ItemStack weapon) {
        AttributeRange range = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.GUN_RPM);
        double rpm = 600.0;
        if (range.getMin() != RpgAttribute.GUN_RPM.getDefaultValue()
            || range.getMax() != RpgAttribute.GUN_RPM.getDefaultValue()) {
            rpm = range.roll();
        }
        if (rpm <= 0) rpm = 600.0;
        return Math.round(60000.0 / rpm);
    }

    private static double roll(ItemStack item, RpgAttribute attr) {
        AttributeRange range = AttributeStorage.getItemAttrRange(item, attr);
        if (range.getMin() == attr.getDefaultValue() && range.getMax() == attr.getDefaultValue()) {
            return attr.getDefaultValue();
        }
        return range.roll();
    }
}
