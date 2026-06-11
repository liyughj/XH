package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 枪膛系统管理器。
 * 膛内一发独立于弹夹，换弹后自动上膛。
 * 换弹后额外拉栓时间。
 */
public class ChamberManager {

    private static final NamespacedKey KEY_CHAMBER_LOADED = new NamespacedKey("xh", "chamber_loaded");

    /** 膛内是否有弹 */
    public static boolean isChamberLoaded(ItemStack weapon) {
        Byte val = getPDC(weapon, KEY_CHAMBER_LOADED);
        return val != null && val == 1;
    }

    /** 设置为膛内有弹 */
    public static void setChamberLoaded(ItemStack weapon, boolean loaded) {
        setPDC(weapon, KEY_CHAMBER_LOADED, loaded ? (byte) 1 : (byte) 0);
    }

    /** 检查枪械是否启用了枪膛系统 */
    public static boolean isEnabled(ItemStack weapon) {
        double val = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_CHAMBER_ENABLED);
        return val >= 1.0;
    }

    /** 射击时：如果膛内有弹且弹夹>0，不消耗弹夹，只消耗膛弹 */
    public static boolean consumeChamber(Player player, ItemStack weapon) {
        if (!isEnabled(weapon)) return false;

        if (isChamberLoaded(weapon)) {
            // 只消耗膛内弹，弹夹不变
            setChamberLoaded(weapon, false);
            return true; // 膛弹已消耗
        }

        // 膛内无弹，需弹夹消耗 + 自动上膛
        int magAmmo = MagazineManager.getAmmo(weapon);
        if (magAmmo <= 0) return false;

        // 消耗弹夹放膛内
        MagazineManager.setAmmo(weapon, magAmmo - 1);
        setChamberLoaded(weapon, true);
        return false; // 弹夹已消耗
    }

    /** 换弹完成后调用：弹夹上膛 */
    public static void afterReload(ItemStack weapon) {
        if (!isEnabled(weapon)) return;
        setChamberLoaded(weapon, true);
    }

    /** 拉栓时间 */
    public static int getBoltTime(ItemStack weapon) {
        if (!isEnabled(weapon)) return 0;
        return (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_CHAMBER_BOLT_TIME_TICKS);
    }

    /** 是否自动拉栓 */
    public static boolean isAutoBolt(ItemStack weapon) {
        if (!isEnabled(weapon)) return true;
        return AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_CHAMBER_AUTO_BOLT) >= 1.0;
    }

    /** 检查是否有可用弹药（膛内+弹夹至少其一） */
    public static boolean hasChamberRound(Player player, ItemStack weapon) {
        if (!isEnabled(weapon)) return !MagazineManager.isEmpty(weapon);
        // 膛内有弹 或 弹夹有弹 → 可射击
        if (isChamberLoaded(weapon)) return true;
        return !MagazineManager.isEmpty(weapon);
    }

    /* ==================== PDC ==================== */

    private static Byte getPDC(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE);
    }

    private static void setPDC(ItemStack item, NamespacedKey key, byte value) {
        if (item == null) return;
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta()
            : org.bukkit.Bukkit.getItemFactory().getItemMeta(item.getType());
        if (meta == null) return;
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, value);
        item.setItemMeta(meta);
    }
}
