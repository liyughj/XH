package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 人体工学管理器 —— 切枪/收枪延迟 + 疾跑开火延迟。
 */
public final class EquipManager {

    private static final Map<UUID, Long> equipBlockUntil  = new ConcurrentHashMap<>(); // 切到此枪的禁止时间
    private static final Map<UUID, Long> holsterBlockUntil = new ConcurrentHashMap<>(); // 收枪禁止切换时间
    private static final Map<UUID, Long> sprintEndTime     = new ConcurrentHashMap<>(); // 疾跑结束时间

    private EquipManager() {}

    /* ==================== 切枪 ==================== */

    /** 记录切到此枪的冷却（ticks），由 GunListener 在切换时调用 */
    static void markEquip(Player player, ItemStack weapon) {
        if (!GunListener.isGunStatic(weapon)) return;
        int ticks = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_EQUIP_TIME_TICKS);
        if (ticks > 0) {
            double swapSpeed = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_WEAPON_SWAP_SPEED);
            if (swapSpeed > 0) ticks = (int) Math.ceil(ticks * 100.0 / swapSpeed);
            equipBlockUntil.put(player.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
        }
    }

    /** 记录收枪冷却（切走时调用） */
    static void markHolster(Player player, ItemStack weapon) {
        if (!GunListener.isGunStatic(weapon)) return;
        int ticks = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HOLSTER_TIME_TICKS);
        if (ticks > 0) {
            holsterBlockUntil.put(player.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
        }
    }

    /** 切枪是否被阻拦（在 equip 冷却内） */
    static boolean isEquipBlocked(Player player) {
        Long until = equipBlockUntil.get(player.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    /** 收枪是否被阻拦 */
    static boolean isHolsterBlocked(Player player) {
        Long until = holsterBlockUntil.get(player.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    /* ==================== 疾跑延迟 ==================== */

    /** 玩家停止疾跑时调用 */
    static void markSprintEnd(Player player) {
        sprintEndTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /** 疾跑刚结束时调用 */
    static void markSprintStart(Player player) {
        sprintEndTime.remove(player.getUniqueId());
    }

    /** 疾跑→开火是否在冷却中（返回剩余延迟 ticks，0=可以开火） */
    static int getSprintToFireDelay(Player player, ItemStack weapon) {
        Long sprintEnd = sprintEndTime.get(player.getUniqueId());
        if (sprintEnd == null) return 0;
        int delayTicks = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_SPRINT_TO_FIRE_TICKS);
        if (delayTicks <= 0) return 0;
        long elapsed = System.currentTimeMillis() - sprintEnd;
        long delayMs = delayTicks * 50L;
        if (elapsed >= delayMs) {
            sprintEndTime.remove(player.getUniqueId());
            return 0;
        }
        return delayTicks - (int)(elapsed / 50);
    }

    /** 完全移除玩家人体工学状态（死亡/退出时调用），避免重生后被旧冷却阻拦切枪 */
    static void remove(Player player) {
        UUID uid = player.getUniqueId();
        equipBlockUntil.remove(uid);
        holsterBlockUntil.remove(uid);
        sprintEndTime.remove(uid);
    }
}
