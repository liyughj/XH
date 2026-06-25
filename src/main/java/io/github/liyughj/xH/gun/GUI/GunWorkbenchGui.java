package io.github.liyughj.xH.gun.GUI;

import io.github.liyughj.xH.gun.AmmoConfig;
import io.github.liyughj.xH.gun.GunSystemConfig;
import io.github.liyughj.xH.gun.GunWorkbenchConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

import static io.github.liyughj.xH.gun.GUI.GuiUtils.*;

/**
 * 枪械自定义工作台 GUI —— 5×5 材料格 + 配方自动匹配 + 一键合成。
 *
 * <pre>
 * GUI 布局（54槽）：
 *   Row 0 (0-4):   材料 0-4,   (5-8) 分隔玻璃
 *   Row 1 (9-13):  材料 5-9,   (14-17) 分隔玻璃
 *   Row 2 (18-22): 材料 10-14, (23)玻璃, (24)输出预览, (25-26)玻璃
 *   Row 3 (27-31): 材料 15-19, (32-35) 分隔玻璃
 *   Row 4 (36-40): 材料 20-24, (41-44) 分隔玻璃
 *   Row 5: (45)清空, (49)确认合成, (53)关闭
 * </pre>
 */
public class GunWorkbenchGui implements Listener {

    private static final String GUI_TYPE = "gun_workbench";
    private static final int GUI_SIZE = 54;
    private static final Component GUI_TITLE = Component.text("枪械工作台");

    /** 5×5 材料格在 54 格背包中的槽位索引 */
    static final int[] MATERIAL_SLOTS = {
        0,1,2,3,4, 9,10,11,12,13, 18,19,20,21,22, 27,28,29,30,31, 36,37,38,39,40
    };

    private static final int OUTPUT_SLOT = 24;
    private static final int BTN_CRAFT = 49;
    private static final int BTN_CLEAR = 45;
    private static final int BTN_CLOSE = 53;

    /** 右侧分隔玻璃占用的槽位 */
    private static final int[] GLASS_SLOTS = {
        5,6,7,8, 14,15,16,17, 23,25,26, 32,33,34,35, 41,42,43,44, 46,47,48,50,51
    };

    private final GunWorkbenchConfig config;

    public GunWorkbenchGui(GunWorkbenchConfig config) {
        this.config = config;
    }

    /* ==================== 打开 GUI ==================== */

    /** 为指定玩家打开工作台 */
    public void open(Player player) {
        BaseGuiHolder holder = new BaseGuiHolder(player.getUniqueId(), null, -1);
        holder.guiType = GUI_TYPE;
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, GUI_TITLE);
        holder.inventory = inv;

        /* 填充右侧分隔玻璃 */
        ItemStack glass = createPlaceholder(" ");
        for (int slot : GLASS_SLOTS) {
            inv.setItem(slot, glass);
        }

        /* 底部按钮 */
        inv.setItem(BTN_CLEAR, createButton(Material.BARRIER, "§c清空材料", "将材料格中的所有物品返回背包"));
        inv.setItem(BTN_CLOSE, createButton(Material.BARRIER, "§7关闭", "关闭工作台，材料自动返回背包"));

        updateCraftButton(inv, null);

        player.openInventory(inv);
        playOpenSound(player);
    }

    /* ==================== GUI 点击 ==================== */

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        BaseGuiHolder holder = getHolder(event.getInventory());
        if (holder == null || !GUI_TYPE.equals(holder.guiType)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!holder.playerId.equals(player.getUniqueId())) return;

        int slot = event.getRawSlot();
        Inventory inv = event.getInventory();

        /* 点击 GUI 外部（玩家背包）→ 允许 */
        if (slot < 0 || slot >= GUI_SIZE) return;

        /* 底部按钮 */
        if (slot == BTN_CLEAR) {
            event.setCancelled(true);
            clearMaterials(inv, player);
            return;
        }
        if (slot == BTN_CLOSE) {
            event.setCancelled(true);
            returnMaterials(inv, player);
            player.closeInventory();
            return;
        }
        if (slot == BTN_CRAFT) {
            event.setCancelled(true);
            GuildRecipe recipe = matchCurrent(inv);
            if (recipe != null) {
                craftRecipe(inv, player, recipe);
            }
            return;
        }

        /* 输出预览槽 → 禁止操作 */
        if (slot == OUTPUT_SLOT) {
            event.setCancelled(true);
            return;
        }

        /* 分隔玻璃 → 禁止 */
        for (int g : GLASS_SLOTS) {
            if (slot == g) { event.setCancelled(true); return; }
        }

        /* 材料槽：允许放置/取出，延迟更新预览 */
        boolean materialSlot = false;
        for (int ms : MATERIAL_SLOTS) {
            if (slot == ms) { materialSlot = true; break; }
        }
        if (!materialSlot) { event.setCancelled(true); return; }

        /* 延迟一 tick 更新预览（等物品实际放入） */
        final Inventory fInv = inv;
        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("XH"),
            () -> updatePreview(fInv));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryDrag(InventoryDragEvent event) {
        BaseGuiHolder holder = getHolder(event.getInventory());
        if (holder == null || !GUI_TYPE.equals(holder.guiType)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!holder.playerId.equals(player.getUniqueId())) return;

        /* Bug 2 修复：拖拽只能放入材料槽，放入其他槽取消 */
        for (int dragSlot : event.getRawSlots()) {
            boolean isMaterial = false;
            for (int ms : MATERIAL_SLOTS) {
                if (dragSlot == ms) { isMaterial = true; break; }
            }
            if (!isMaterial) { event.setCancelled(true); return; }
        }

        Inventory inv = event.getInventory();
        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("XH"),
            () -> updatePreview(inv));
    }

    /* ==================== 关闭 → 返还材料 ==================== */

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        BaseGuiHolder holder = getHolder(event.getInventory());
        if (holder == null || !GUI_TYPE.equals(holder.guiType)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!holder.playerId.equals(player.getUniqueId())) return;

        returnMaterials(event.getInventory(), player);
    }

    /* ==================== 配方匹配 ==================== */

    /** 当前材料格 → 匹配配方（含预览生成） */
    private void updatePreview(Inventory inv) {
        GuildRecipe recipe = matchCurrent(inv);
        ItemStack preview = (recipe != null) ? createOutputPreview(recipe) : null;
        inv.setItem(OUTPUT_SLOT, preview);
        updateCraftButton(inv, recipe);
    }

    /** 从 25格材料中匹配一个配方 */
    private GuildRecipe matchCurrent(Inventory inv) {
        Material[] grid = new Material[25];
        for (int i = 0; i < MATERIAL_SLOTS.length; i++) {
            ItemStack item = inv.getItem(MATERIAL_SLOTS[i]);
            grid[i] = (item != null && !item.getType().isAir()) ? item.getType() : null;
        }
        GunWorkbenchConfig.RecipeDef def = config.matchRecipe(grid);
        if (def == null) return null;

        return new GuildRecipe(def, grid.clone());
    }

    /** 创建配方输出预览物品 */
    private ItemStack createOutputPreview(GuildRecipe recipe) {
        GunWorkbenchConfig.RecipeDef def = recipe.def;
        ItemStack preview = switch (def.type) {
            case "gun" -> GunSystemConfig.gun().createGunItem(def.outputId);
            case "ammo" -> createAmmoPreview(def.outputId);
            case "mag" -> GunSystemConfig.gun().createMagazineItem(def.outputId);
            case "custom" -> createCustomItem(def);
            default -> null;
        };
        if (preview == null) {
            preview = new ItemStack(Material.BARRIER);
            ItemMeta meta = preview.getItemMeta();
            meta.displayName(Component.text("§c配方错误"));
            preview.setItemMeta(meta);
            return preview;
        }

        ItemMeta meta = preview.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("§e▸ 配方: §f" + def.name, NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("§7点击下方铁砧按钮确认合成", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        preview.setItemMeta(meta);
        return preview;
    }

    private ItemStack createAmmoPreview(String outputId) {
        /* 格式: caliber:ammoType */
        String[] parts = outputId.split(":", 2);
        if (parts.length != 2) return null;
        AmmoConfig ammo = GunSystemConfig.ammo();
        if (ammo == null) return null;
        return ammo.createAmmoItemStack(parts[0], parts[1]);
    }

    /**
     * custom 类型：创建自定义 RPG 属性物品。
     * 配方中指定 Material + displayName + PDC 键值对。
     */
    private ItemStack createCustomItem(GunWorkbenchConfig.RecipeDef def) {
        Material mat = Material.getMaterial(def.outputMaterial.toUpperCase());
        if (mat == null) return null;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        /* 显示名 */
        if (!def.outputDisplayName.isEmpty()) {
            meta.displayName(Component.text(def.outputDisplayName));
        }

        /* 写入 PDC 属性（支持 RPG 属性格式） */
        for (Map.Entry<String, String> e : def.outputAttrs.entrySet()) {
            /* 尝试解析为 double → 存 DOUBLE 类型 */
            try {
                double dv = Double.parseDouble(e.getValue());
                meta.getPersistentDataContainer().set(
                    new NamespacedKey("xh", e.getKey()), PersistentDataType.DOUBLE, dv);
                continue;
            } catch (NumberFormatException ignored) {}
            /* 尝试解析为 int → 存 INTEGER 类型 */
            try {
                int iv = Integer.parseInt(e.getValue());
                meta.getPersistentDataContainer().set(
                    new NamespacedKey("xh", e.getKey()), PersistentDataType.INTEGER, iv);
                continue;
            } catch (NumberFormatException ignored) {}
            /* 默认存 STRING */
            meta.getPersistentDataContainer().set(
                new NamespacedKey("xh", e.getKey()), PersistentDataType.STRING, e.getValue());
        }

        item.setItemMeta(meta);
        return item;
    }

    /** 更新合成按钮状态 */
    private void updateCraftButton(Inventory inv, GuildRecipe recipe) {
        if (recipe != null) {
            inv.setItem(BTN_CRAFT, createButton(Material.ANVIL,
                "§a§l确认合成",
                "§7产出: §f" + recipe.def.name));
        } else {
            inv.setItem(BTN_CRAFT, createButton(Material.GRAY_DYE,
                "§8无匹配配方",
                "§7放入材料后自动匹配"));
        }
    }

    /* ==================== 合成 ==================== */

    private void craftRecipe(Inventory inv, Player player, GuildRecipe recipe) {
        /* 消耗材料（每格减1） */
        GunWorkbenchConfig.RecipeDef def = recipe.def;
        for (int i = 0; i < MATERIAL_SLOTS.length; i++) {
            if (def.materials.containsKey(i)) {
                int ms = MATERIAL_SLOTS[i];
                ItemStack slotItem = inv.getItem(ms);
                if (slotItem != null && slotItem.getAmount() > 0) {
                    slotItem.setAmount(slotItem.getAmount() - 1);
                    /* Bug 1 修复：消耗完清空槽位，防止 ghost item */
                    if (slotItem.getAmount() <= 0) {
                        inv.setItem(ms, null);
                    }
                }
            }
        }

        /* 产出物品 */
        ItemStack output = switch (def.type) {
            case "gun" -> GunSystemConfig.gun().createGunItem(def.outputId);
            case "ammo" -> {
                String[] parts = def.outputId.split(":", 2);
                AmmoConfig ammo = GunSystemConfig.ammo();
                if (ammo == null || parts.length != 2) yield null;
                ItemStack a = ammo.createAmmoItemStack(parts[0], parts[1]);
                /* Bug 4 修复：弹药数量用配方 output_amount 配置，默认 16 */
                if (a != null) a.setAmount(def.outputAmount > 0 ? def.outputAmount : 16);
                yield a;
            }
            case "mag" -> GunSystemConfig.gun().createMagazineItem(def.outputId);
            case "custom" -> createCustomItem(def);
            default -> null;
        };

        if (output != null) {
            returnToPlayer(player, output);
            player.sendMessage("§a已合成: §e" + def.name);
        }

        /* 清除输出预览，重新匹配 */
        updatePreview(inv);
        playConfirmSound(player);
    }

    /* ==================== 材料管理 ==================== */

    /** 清空材料格 → 全部返还玩家 */
    private void clearMaterials(Inventory inv, Player player) {
        for (int slot : MATERIAL_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                returnToPlayer(player, item);
                inv.setItem(slot, null);
            }
        }
        updatePreview(inv);
        playCancelSound(player);
    }

    /** 关闭时返还所有材料格物品 */
    private void returnMaterials(Inventory inv, Player player) {
        for (int slot : MATERIAL_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                returnToPlayer(player, item);
                inv.setItem(slot, null);
            }
        }
    }

    /* ==================== 内部数据类 ==================== */

    /** 匹配到的配方 + 原始材料快照 */
    private static class GuildRecipe {
        final GunWorkbenchConfig.RecipeDef def;
        final Material[] grid;

        GuildRecipe(GunWorkbenchConfig.RecipeDef def, Material[] grid) {
            this.def = def;
            this.grid = grid;
        }
    }
}
