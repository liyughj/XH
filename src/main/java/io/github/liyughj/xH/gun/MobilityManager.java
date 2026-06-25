package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 机动性管理器 —— 持枪移速/疾跑速度/开镜移速/跳跃修正/禁疾跑。
 */
public final class MobilityManager {

    private static final Map<UUID, Float> originalWalkSpeed  = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> sprintBlocked    = new ConcurrentHashMap<>();
    /** 当前持枪物品引用，供疾跑/跳跃时读取属性 */
    private static final Map<UUID, ItemStack> heldWeapon     = new ConcurrentHashMap<>();

    private MobilityManager() {}

    /* ==================== 武器切换时应用 ==================== */

    static void applyWeaponSpeed(Player player, ItemStack weapon) {
        if (!GunListener.isGunStatic(weapon)) return;
        UUID uid = player.getUniqueId();

        originalWalkSpeed.putIfAbsent(uid, player.getWalkSpeed());
        heldWeapon.put(uid, weapon);

        refreshPlayerSpeed(player);

        if (AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_CAN_SPRINT) < 1.0) {
            player.setSprinting(false);
            sprintBlocked.put(uid, true);
        }
    }

    /** 切走枪械时调用，恢复移速 */
    static void restoreWeaponSpeed(Player player) {
        UUID uid = player.getUniqueId();
        Float orig = originalWalkSpeed.remove(uid);
        heldWeapon.remove(uid);
        sprintBlocked.remove(uid);
        if (orig != null) {
            player.setWalkSpeed(orig);
        }
    }

    /** 疾跑切换时调用，重新计算 walkSpeed */
    static void refreshSprintSpeed(Player player) {
        UUID uid = player.getUniqueId();
        ItemStack weapon = heldWeapon.get(uid);
        if (weapon == null || !GunListener.isGunStatic(weapon)) return;
        refreshPlayerSpeed(player);
    }

    /** 获取跳跃高度乘数（0.0~3.0，1.0=正常） */
    static double getJumpMultiplier(Player player) {
        ItemStack weapon = heldWeapon.get(player.getUniqueId());
        if (weapon == null || !GunListener.isGunStatic(weapon)) return 1.0;
        return AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_JUMP_HEIGHT) / 100.0;
    }

    /** 是否被禁止疾跑 */
    static boolean isSprintBlocked(Player player) {
        return sprintBlocked.getOrDefault(player.getUniqueId(), false);
    }

    /** 完全移除玩家机动性状态（死亡/退出时调用），恢复默认移速 */
    static void remove(Player player) {
        UUID uid = player.getUniqueId();
        Float orig = originalWalkSpeed.remove(uid);
        heldWeapon.remove(uid);
        sprintBlocked.remove(uid);
        // 优先恢复保存的原移速，无记录时才用MC默认0.2
        player.setWalkSpeed(orig != null ? orig : 0.2f);
    }

    /* ==================== 内部 ==================== */

    private static void refreshPlayerSpeed(Player player) {
        UUID uid = player.getUniqueId();
        Float orig = originalWalkSpeed.get(uid);
        ItemStack weapon = heldWeapon.get(uid);
        if (orig == null || weapon == null) return;

        double moveSpeed = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_MOVE_SPEED) / 100.0;
        double sprintSpeed = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_SPRINT_SPEED) / 100.0;

        // 原版疾跑 = walkSpeed × 1.3，枪械自定义 sprintSpeed 覆盖
        double finalSpeed = orig * moveSpeed;
        if (player.isSprinting() && sprintSpeed != 1.0) {
            finalSpeed = orig * sprintSpeed;
        }
        player.setWalkSpeed((float) Math.max(0.02f, Math.min(1.0f, finalSpeed)));
    }
}
