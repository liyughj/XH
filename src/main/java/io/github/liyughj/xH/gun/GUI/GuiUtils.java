package io.github.liyughj.xH.gun.GUI;

import io.github.liyughj.xH.gun.AmmoConfig;
import io.github.liyughj.xH.gun.GunSystemConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * 枪械 GUI 共享工具类。
 * 配件 GUI / 弹药 GUI / 弹夹 GUI 等所有枪械相关 GUI 的公共方法。
 *
 * <h3>扩展方式</h3>
 * <pre>
 * public class MyGunGui implements Listener {
 *     public void open(Player player, ItemStack sourceItem, int heldSlot) {
 *         BaseGuiHolder holder = new BaseGuiHolder(player.getUniqueId(), sourceItem, heldSlot);
 *         Inventory inv = Bukkit.createInventory(holder, 54, Component.text("我的GUI"));
 *         holder.inventory = inv;
 *         // 用 GuiUtils.createButton / createPlaceholder 填充...
 *         player.openInventory(inv);
 *     }
 *
 *     &#64;EventHandler
 *     public void onClick(InventoryClickEvent e) {
 *         if (!GuiUtils.isGunGuiHolder(e.getInventory())) return;
 *         // ...
 *     }
 * }
 * </pre>
 */
public final class GuiUtils {

    private GuiUtils() {}

    /* ==================== Holder 检测 ==================== */

    /** 判断是否任意枪械 GUI 的 Holder */
    public static boolean isGunGuiHolder(Inventory inv) {
        return inv != null && inv.getHolder() instanceof BaseGuiHolder;
    }

    /** 获取 Holder（安全转换） */
    public static BaseGuiHolder getHolder(Inventory inv) {
        return isGunGuiHolder(inv) ? (BaseGuiHolder) inv.getHolder() : null;
    }

    /* ==================== 通用按钮 ==================== */

    /** 创建按钮物品 */
    public static ItemStack createButton(Material mat, String name, String loreText) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
            meta.lore(Collections.singletonList(Component.text(loreText)));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 创建按钮物品（多行 lore） */
    public static ItemStack createButton(Material mat, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) lore.add(Component.text(line));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 创建占位符填充 */
    public static ItemStack createPlaceholder(String name) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 创建已安装/已装备的标记 */
    public static ItemStack createEquippedMarker(String name) {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 创建锁定槽位 */
    public static ItemStack createLockedSlot(String reason) {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§c锁定的槽位"));
            meta.lore(Collections.singletonList(Component.text("§7" + reason)));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 为一行填充占位符 */
    public static void fillRow(Inventory inv, int startSlot, int count, String name) {
        ItemStack placeholder = createPlaceholder(name);
        for (int i = 0; i < count; i++) inv.setItem(startSlot + i, placeholder);
    }

    /* ==================== PDC 工具 ==================== */

    public static String getStringPDC(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public static Integer getIntPDC(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
    }

    public static int getIntPDCDefault(ItemStack item, NamespacedKey key, int def) {
        Integer v = getIntPDC(item, key);
        return v != null ? v : def;
    }

    public static void setStringPDC(ItemStack item, NamespacedKey key, String value) {
        if (item == null) return;
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
        if (meta == null) return;
        if (value == null || value.isEmpty()) {
            meta.getPersistentDataContainer().remove(key);
        } else {
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        }
        item.setItemMeta(meta);
    }

    public static void setIntPDC(ItemStack item, NamespacedKey key, int value) {
        if (item == null) return;
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
        if (meta == null) return;
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
        item.setItemMeta(meta);
    }

    public static boolean hasPDC(ItemStack item, NamespacedKey key, PersistentDataType<?, ?> type) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, type);
    }

    /* ==================== 物品管理 ==================== */

    /**
     * 从玩家背包消耗一个匹配 PDC 条件的物品。
     * @return true = 成功消耗
     */
    public static boolean consumePlayerItem(Player player, NamespacedKey matchKey, String matchValue) {
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            if (item == null || !item.hasItemMeta()) continue;
            String val = getStringPDC(item, matchKey);
            if (matchValue.equals(val) && item.getAmount() > 0) {
                item.setAmount(item.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    /**
     * 从玩家背包消耗一个匹配两个 PDC 条件的物品。
     */
    public static boolean consumePlayerItemDual(Player player, NamespacedKey key1, String val1,
                                                 NamespacedKey key2, String val2) {
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            if (item == null || !item.hasItemMeta()) continue;
            if (!val1.equals(getStringPDC(item, key1))) continue;
            if (!val2.equals(getStringPDC(item, key2))) continue;
            if (item.getAmount() <= 0) continue;
            item.setAmount(item.getAmount() - 1);
            return true;
        }
        return false;
    }

    /** 将物品返还到玩家背包，满则丢地上 */
    public static void returnToPlayer(Player player, ItemStack item) {
        if (item == null) return;
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    /** 根据口径+弹种创建一颗弹药并返还到玩家背包 */
    public static void returnAmmoToPlayer(Player player, String caliber, String ammoType) {
        AmmoConfig cfg = GunSystemConfig.ammo();
        if (cfg == null) return;
        ItemStack ammo = cfg.createAmmoItemStack(caliber, ammoType);
        if (ammo == null) return;
        ammo.setAmount(1);
        returnToPlayer(player, ammo);
    }

    /** 播放 GUI 打开音效 */
    public static void playOpenSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
    }

    /** 播放确认音效 */
    public static void playConfirmSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.6f, 1.5f);
    }

    /** 播放撤销音效 */
    public static void playCancelSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 0.6f, 1.5f);
    }
}
