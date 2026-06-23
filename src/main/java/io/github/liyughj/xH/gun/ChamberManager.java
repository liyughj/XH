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
    private static final NamespacedKey KEY_CHAMBER_AMMO_TYPE = new NamespacedKey("xh", "chamber_ammo_type");

    /** 膛内是否有弹 */
    public static boolean isChamberLoaded(ItemStack weapon) {
        Byte val = getPDC(weapon, KEY_CHAMBER_LOADED);
        return val != null && val == 1;
    }

    /** 设置为膛内有弹 */
    public static void setChamberLoaded(ItemStack weapon, boolean loaded) {
        setPDC(weapon, KEY_CHAMBER_LOADED, loaded ? (byte) 1 : (byte) 0);
        if (!loaded) {
            clearChamberAmmoType(weapon);
        }
    }

    /** 获取膛内弹种名称 */
    public static String getChamberAmmoType(ItemStack weapon) {
        return getStringPDC(weapon, KEY_CHAMBER_AMMO_TYPE);
    }

    /** 设置膛内弹种 */
    public static void setChamberAmmoType(ItemStack weapon, String ammoType) {
        if (weapon == null || ammoType == null) return;
        ItemMeta meta = weapon.hasItemMeta() ? weapon.getItemMeta()
            : org.bukkit.Bukkit.getItemFactory().getItemMeta(weapon.getType());
        if (meta == null) return;
        meta.getPersistentDataContainer().set(KEY_CHAMBER_AMMO_TYPE, PersistentDataType.STRING, ammoType);
        weapon.setItemMeta(meta);
    }

    private static void clearChamberAmmoType(ItemStack weapon) {
        if (weapon == null || !weapon.hasItemMeta()) return;
        ItemMeta meta = weapon.getItemMeta();
        meta.getPersistentDataContainer().remove(KEY_CHAMBER_AMMO_TYPE);
        weapon.setItemMeta(meta);
    }

    /** 检查枪械是否启用了枪膛系统 */
    public static boolean isEnabled(ItemStack weapon) {
        double val = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_CHAMBER_ENABLED);
        return val >= 1.0;
    }

    /**
     * 射击时消耗弹药。对于膛内已装弹的枪，消耗膛弹后自动从弹夹装填下一发。
     * @return true=膛弹已消耗（可射击），false=无膛弹且弹夹也为空
     */
    public static boolean consumeChamber(Player player, ItemStack weapon) {
        if (!isEnabled(weapon)) return false;

        if (isChamberLoaded(weapon)) {
            // 消耗膛内弹用于本次射击
            String chamberType = getChamberAmmoType(weapon);
            setChamberLoaded(weapon, false);
            MagazineManager.setCurrentShotType(weapon, chamberType);

            // 自动拉栓：射击后自动从弹夹装填下一发入膛
            if (isAutoBolt(weapon)) {
                int mag = MagazineManager.getAmmo(weapon);
                if (mag > 0) {
                    String nextType = MagazineManager.peekNextAmmoType(weapon);
                    MagazineManager.setAmmo(weapon, mag - 1);
                    MagazineManager.popTopFromStack(weapon);
                    setChamberLoaded(weapon, true);
                    setChamberAmmoType(weapon, nextType);
                }
            }
            return true;
        }

        // 膛内无弹且有弹夹 → 手动拉栓枪不允许自动装填；自动拉栓枪无法到达此分支（换弹后已上膛）
        if (!isAutoBolt(weapon)) return false;

        // 自动拉栓但膛空：仅在初始状态（刚获得枪械，未换弹/未射击过）触发
        // 此时弹夹有弹但膛空，直接消耗弹夹发射（不装膛，因为发射后会自动装下一发）
        int magAmmo = MagazineManager.getAmmo(weapon);
        if (magAmmo <= 0) return false;

        String ammoType = MagazineManager.peekNextAmmoType(weapon);
        MagazineManager.setAmmo(weapon, magAmmo - 1);
        MagazineManager.popTopFromStack(weapon);
        MagazineManager.setCurrentShotType(weapon, ammoType);
        return true;
    }

    /** 换弹完成后调用：从弹夹取一发压入膛内 */
    public static void afterReload(ItemStack weapon) {
        if (!isEnabled(weapon)) return;
        // 膛内已有弹：不覆盖（战术换弹保留膛内原有弹种）
        if (isChamberLoaded(weapon)) return;
        int mag = MagazineManager.getAmmo(weapon);
        if (mag <= 0) return;
        // 从弹夹栈顶取一发压入枪膛
        String ammoType = MagazineManager.peekNextAmmoType(weapon);
        // 兜底：栈为空时（如刚创建的枪），使用枪械默认弹种
        if (ammoType == null) {
            if (GunSystemConfig.gun() != null) {
                ammoType = GunSystemConfig.gun().getDefaultAmmo(weapon.getType());
            }
            if (ammoType == null) return;
        }
        MagazineManager.setAmmo(weapon, mag - 1);
        MagazineManager.popTopFromStack(weapon);
        setChamberLoaded(weapon, true);
        setChamberAmmoType(weapon, ammoType);
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

    private static String getStringPDC(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
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
