package io.github.liyughj.xH.gun.GUI;

import io.github.liyughj.xH.gun.AmmoConfig;
import io.github.liyughj.xH.gun.GunSystemConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;

import java.util.*;

import static io.github.liyughj.xH.gun.GUI.GuiUtils.*;

/**
 * 弹夹管理 GUI —— 右键弹夹物品打开，支持逐颗装弹/卸弹/混装。
 *
 * <pre>
 * GUI 布局（54槽）：
 *   Row 0 (0-2):  弹夹信息
 *   Row 0-2 (3-26): 子弹栈（最多24颗，左=顶/先发射，右=底/先装填）
 *   Row 3 (27-35): 分隔/空
 *   Row 4 (36-44): 背包内匹配口径的弹药（按弹种分组）
 *   Row 5 (45-53): 操作按钮
 * </pre>
 */
public class MagazineGUI implements Listener {

    private static final String GUI_TYPE = "magazine";
    private static final int GUI_SIZE = 54;
    private static final Component GUI_TITLE = Component.text("弹夹管理");
    private static final int BULLET_SLOTS = 24;
    private static final int BULLET_START = 3;
    private static final int AMMO_START = 36;
    private static final int AMMO_END = 44;
    private static final int BTN_UNLOAD_ONE = 45;
    private static final int BTN_UNLOAD_ALL = 46;
    private static final int BTN_CLOSE = 53;

    private static final NamespacedKey KEY_MAG_ID     = new NamespacedKey("xh", "magazine_id");
    private static final NamespacedKey KEY_MAG_CALIBER = new NamespacedKey("xh", "mag_caliber");
    private static final NamespacedKey KEY_MAG_CAPACITY= new NamespacedKey("xh", "mag_capacity");
    private static final NamespacedKey KEY_MAG_AMMO    = new NamespacedKey("xh", "mag_ammo");
    private static final NamespacedKey KEY_MAG_STACK   = new NamespacedKey("xh", "mag_ammo_stack");
    private static final NamespacedKey KEY_AMMO_CALIBER= new NamespacedKey("xh", "ammo_caliber");
    private static final NamespacedKey KEY_AMMO_TYPE   = new NamespacedKey("xh", "ammo_type");

    /* ==================== 右键弹夹 → 打开 GUI ==================== */

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRightClickMagazine(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!isMagazine(item)) return;

        event.setCancelled(true);
        open(player, item, player.getInventory().getHeldItemSlot());
    }

    /* ==================== GUI 点击 ==================== */

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        BaseGuiHolder holder = getHolder(event.getInventory());
        if (holder == null || !GUI_TYPE.equals(holder.guiType)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!holder.playerId.equals(player.getUniqueId())) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= GUI_SIZE) return;
        ItemStack magItem = holder.sourceItem;

        if (slot >= BULLET_START && slot < BULLET_START + BULLET_SLOTS) {
            handleUnloadBullet(player, magItem, slot - BULLET_START, holder);
        } else if (slot >= AMMO_START && slot <= AMMO_END) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() != Material.AIR && clicked.getType() != Material.GRAY_STAINED_GLASS_PANE) {
                String ammoType = getStringPDC(clicked, KEY_AMMO_TYPE);
                if (ammoType != null) handleLoadBullet(player, magItem, ammoType, holder);
            }
        } else if (slot == BTN_UNLOAD_ONE) {
            handleUnloadTop(player, magItem, holder);
        } else if (slot == BTN_UNLOAD_ALL) {
            handleUnloadAll(player, magItem, holder);
        } else if (slot == BTN_CLOSE) {
            player.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isGunGuiHolder(event.getInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        BaseGuiHolder holder = getHolder(event.getInventory());
        if (holder == null || !GUI_TYPE.equals(holder.guiType)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!holder.playerId.equals(player.getUniqueId())) return;
        // PDC 已原地写入 sourceItem，无需额外保存
    }

    /* ==================== 打开 GUI ==================== */

    public void open(Player player, ItemStack magItem, int heldSlot) {
        if (!isMagazine(magItem)) return;

        BaseGuiHolder holder = new BaseGuiHolder(player.getUniqueId(), magItem, heldSlot);
        holder.guiType = GUI_TYPE;
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, GUI_TITLE);
        holder.inventory = inv;

        refreshGUI(inv, magItem, player);
        player.openInventory(inv);
        playOpenSound(player);
    }

    /* ==================== GUI 刷新 ==================== */

    private void refreshGUI(Inventory inv, ItemStack magItem, Player player) {
        inv.clear();
        String caliber = getStringPDC(magItem, KEY_MAG_CALIBER);

        // 头部信息
        ItemStack infoItem = magItem.clone();
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            int ammo = getIntPDCDefault(magItem, KEY_MAG_AMMO, 0);
            int cap  = getIntPDCDefault(magItem, KEY_MAG_CAPACITY, 0);
            infoMeta.lore(List.of(
                Component.text("§7口径: " + (caliber != null ? caliber : "未知")),
                Component.text("§7容量: §e" + ammo + "§7/§e" + cap)));
            infoItem.setItemMeta(infoMeta);
        }
        inv.setItem(0, infoItem);

        // 子弹栈
        String[] stack = getStackArray(magItem);
        for (int i = 0; i < BULLET_SLOTS; i++) {
            inv.setItem(BULLET_START + i,
                (i < stack.length && stack[i] != null)
                    ? createBulletDisplay(stack[i], caliber)
                    : createPlaceholder("§7空"));
        }

        // 分隔
        fillRow(inv, 27, 9, "§8--------");

        // 匹配弹药
        if (caliber != null) {
            Map<String, Integer> groups = scanPlayerAmmo(player, caliber);
            int idx = AMMO_START;
            for (Map.Entry<String, Integer> e : groups.entrySet()) {
                if (idx > AMMO_END) break;
                inv.setItem(idx++, createAmmoDisplay(e.getKey(), caliber, e.getValue()));
            }
            while (idx <= AMMO_END) inv.setItem(idx++, createPlaceholder("§7无可用弹药"));
        } else {
            for (int i = AMMO_START; i <= AMMO_END; i++)
                inv.setItem(i, createPlaceholder("§c无口径信息"));
        }

        // 按钮
        inv.setItem(BTN_UNLOAD_ONE, createButton(Material.ARROW, "§c卸载一发", "§7卸下顶部一发子弹"));
        inv.setItem(BTN_UNLOAD_ALL, createButton(Material.BARRIER, "§c全部卸载", "§7卸下弹夹内全部子弹"));
        inv.setItem(BTN_CLOSE,        createButton(Material.BARRIER, "§8关闭",   "§7关闭弹夹管理"));
    }

    /* ==================== 装弹 ==================== */

    private void handleLoadBullet(Player player, ItemStack magItem, String ammoType, BaseGuiHolder holder) {
        String caliber = getStringPDC(magItem, KEY_MAG_CALIBER);
        int current = getIntPDCDefault(magItem, KEY_MAG_AMMO, 0);
        int cap     = getIntPDCDefault(magItem, KEY_MAG_CAPACITY, 0);
        if (current >= cap) {
            player.sendActionBar(Component.text("弹夹已满", NamedTextColor.RED));
            return;
        }
        if (!consumePlayerAmmoSafe(player, caliber, ammoType)) {
            player.sendActionBar(Component.text("背包中没有 §e" + ammoType + " §c弹药", NamedTextColor.RED));
            return;
        }
        pushToStackTop(magItem, ammoType);
        setIntPDC(magItem, KEY_MAG_AMMO, current + 1);

        playConfirmSound(player);
        refreshGUI(holder.inventory, magItem, player);
        player.sendActionBar(Component.text("装填 §e" + ammoType + " §7(" + (current + 1) + "/" + cap + ")", NamedTextColor.GREEN));
    }

    /* ==================== 卸弹 — 指定位置 ==================== */

    private void handleUnloadBullet(Player player, ItemStack magItem, int bulletIndex, BaseGuiHolder holder) {
        String[] stack = getStackArray(magItem);
        if (bulletIndex >= stack.length) return;

        String ammoType = stack[bulletIndex];
        int current = getIntPDCDefault(magItem, KEY_MAG_AMMO, 0);
        if (current <= 0) return;

        removeFromStack(magItem, bulletIndex);
        setIntPDC(magItem, KEY_MAG_AMMO, current - 1);
        returnAmmoToPlayer(player, getStringPDC(magItem, KEY_MAG_CALIBER), ammoType);

        playCancelSound(player);
        refreshGUI(holder.inventory, magItem, player);
    }

    private void handleUnloadTop(Player player, ItemStack magItem, BaseGuiHolder holder) {
        String[] stack = getStackArray(magItem);
        if (stack.length == 0) {
            player.sendActionBar(Component.text("弹夹为空", NamedTextColor.RED));
            return;
        }
        handleUnloadBullet(player, magItem, 0, holder);
    }

    private void handleUnloadAll(Player player, ItemStack magItem, BaseGuiHolder holder) {
        String[] stack = getStackArray(magItem);
        if (stack.length == 0) {
            player.sendActionBar(Component.text("弹夹为空", NamedTextColor.RED));
            return;
        }
        String caliber = getStringPDC(magItem, KEY_MAG_CALIBER);
        for (String t : stack) { if (t != null) returnAmmoToPlayer(player, caliber, t); }
        setStringPDC(magItem, KEY_MAG_STACK, "");
        setIntPDC(magItem, KEY_MAG_AMMO, 0);

        playCancelSound(player);
        refreshGUI(holder.inventory, magItem, player);
        player.sendActionBar(Component.text("已卸下全部 " + stack.length + " 发", NamedTextColor.GREEN));
    }

    /* ==================== 弹夹栈操作 ==================== */

    private String[] getStackArray(ItemStack item) {
        String raw = getStringPDC(item, KEY_MAG_STACK);
        if (raw == null || raw.isEmpty()) return new String[0];
        String[] parts = raw.split(",");
        String[] result = new String[parts.length];
        for (int i = 0; i < parts.length; i++)
            result[i] = parts[parts.length - 1 - i].trim();
        return result;
    }

    private void pushToStackTop(ItemStack item, String ammoType) {
        String existing = getStringPDC(item, KEY_MAG_STACK);
        setStringPDC(item, KEY_MAG_STACK,
            (existing == null || existing.isEmpty()) ? ammoType : existing + "," + ammoType);
    }

    private void removeFromStack(ItemStack item, int bulletIndex) {
        String[] arr = getStackArray(item);
        if (bulletIndex >= arr.length) return;
        StringBuilder sb = new StringBuilder();
        // arr 已反转：index 0=栈顶(先发射), index n=栈底(先装填)
        // 从栈底到栈顶遍历构建 PDC 格式（左=底, 右=顶）
        for (int i = arr.length - 1; i >= 0; i--) {
            if (i == bulletIndex) continue; // 跳过要删除的 GUI 位置
            if (sb.length() > 0) sb.append(',');
            sb.append(arr[i]);
        }
        setStringPDC(item, KEY_MAG_STACK, sb.toString());
    }

    /* ==================== 背包弹药 ==================== */

    /** 安全消耗背包弹药，排除枪械和弹夹 */
    private boolean consumePlayerAmmoSafe(Player player, String caliber, String ammoType) {
        NamespacedKey gunIdKey = new NamespacedKey("xh", "gun_id");
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            if (item == null || !item.hasItemMeta() || item.getAmount() <= 0) continue;
            var pdc = item.getItemMeta().getPersistentDataContainer();
            if (pdc.has(gunIdKey, PersistentDataType.STRING)) continue;       // 排除枪械
            if (pdc.has(KEY_MAG_ID, PersistentDataType.STRING)) continue;      // 排除弹夹
            if (!caliber.equals(getStringPDC(item, KEY_AMMO_CALIBER))) continue;
            if (!ammoType.equals(getStringPDC(item, KEY_AMMO_TYPE))) continue;
            item.setAmount(item.getAmount() - 1);
            return true;
        }
        return false;
    }

    private Map<String, Integer> scanPlayerAmmo(Player player, String caliber) {
        Map<String, Integer> result = new LinkedHashMap<>();
        NamespacedKey gunIdKey = new NamespacedKey("xh", "gun_id");
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            if (item.getItemMeta().getPersistentDataContainer().has(gunIdKey, PersistentDataType.STRING)) continue; // 排除枪械
            if (item.getItemMeta().getPersistentDataContainer().has(KEY_MAG_ID, PersistentDataType.STRING)) continue; // 排除弹夹
            if (!caliber.equals(getStringPDC(item, KEY_AMMO_CALIBER))) continue;
            String type = getStringPDC(item, KEY_AMMO_TYPE);
            if (type == null) continue;
            result.merge(type, item.getAmount(), Integer::sum);
        }
        return result;
    }

    /* ==================== 显示物品 ==================== */

    private ItemStack createBulletDisplay(String ammoType, String caliber) {
        AmmoConfig cfg = GunSystemConfig.ammo();
        AmmoConfig.AmmoTypeDef def = (cfg != null) ? cfg.getAmmoType(ammoType) : null;
        Material mat = (def != null) ? Material.getMaterial(def.itemMaterial) : Material.IRON_NUGGET;
        if (mat == null || !mat.isItem()) mat = Material.IRON_NUGGET;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§e● " + ((def != null) ? def.displayName : ammoType), NamedTextColor.YELLOW));
            meta.lore(List.of(
                Component.text("§7弹种: " + ammoType),
                Component.text("§7口径: " + (caliber != null ? caliber : "?")),
                Component.text("§c左键点击 → 卸下此弹")));
            meta.getPersistentDataContainer().set(KEY_AMMO_TYPE, PersistentDataType.STRING, ammoType);
            if (caliber != null) meta.getPersistentDataContainer().set(KEY_AMMO_CALIBER, PersistentDataType.STRING, caliber);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createAmmoDisplay(String ammoType, String caliber, int count) {
        AmmoConfig cfg = GunSystemConfig.ammo();
        AmmoConfig.AmmoTypeDef def = (cfg != null) ? cfg.getAmmoType(ammoType) : null;
        Material mat = (def != null) ? Material.getMaterial(def.itemMaterial) : Material.IRON_NUGGET;
        if (mat == null || !mat.isItem()) mat = Material.IRON_NUGGET;

        ItemStack item = new ItemStack(mat);
        item.setAmount(Math.min(count, 64));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§a" + ((def != null) ? def.displayName : ammoType), NamedTextColor.GREEN));
            meta.lore(List.of(
                Component.text("§7弹种: " + ammoType),
                Component.text("§7背包剩余: " + count + " 发"),
                Component.text("§a左键点击 → 压入一发")));
            meta.getPersistentDataContainer().set(KEY_AMMO_TYPE, PersistentDataType.STRING, ammoType);
            meta.getPersistentDataContainer().set(KEY_AMMO_CALIBER, PersistentDataType.STRING, caliber);
            item.setItemMeta(meta);
        }
        return item;
    }

    /* ==================== 工具 ==================== */

    private boolean isMagazine(ItemStack item) {
        return item != null && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(KEY_MAG_ID, PersistentDataType.STRING);
    }
}
