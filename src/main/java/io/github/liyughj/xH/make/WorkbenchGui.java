package io.github.liyughj.xH.make;

import io.github.liyughj.xH.gun.AmmoConfig;
import io.github.liyughj.xH.gun.GunSystemConfig;
import io.github.liyughj.xH.gun.GUI.BaseGuiHolder;
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
 * 枪械工作台 GUI —— 图纸驱动合成。
 *
 * <pre>
 * GUI 布局（54槽）：
 *   Row 0: [0-4 材料]    [5-8 玻璃]
 *   Row 1: [9-13 材料]   [14-17 玻璃]
 *   Row 2: [18-22 材料]  [23 图纸] [24 输出] [25-26 玻璃]
 *   Row 3: [27-31 材料]  [32-35 玻璃]
 *   Row 4: [36-40 材料]  [41-44 玻璃]
 *   Row 5: [45 清空] [46-48 玻璃] [49 合成] [50-52 玻璃] [53 关闭]
 * </pre>
 */
public class WorkbenchGui implements Listener {

    private static final String GUI_TYPE = "gun_workbench";
    private static final int GUI_SIZE = 54;
    private static final Component GUI_TITLE = Component.text("枪械工作台");

    static final int[] MATERIAL_SLOTS = {
        0,1,2,3,4, 9,10,11,12,13, 18,19,20,21,22, 27,28,29,30,31, 36,37,38,39,40
    };

    private static final int BLUEPRINT_SLOT = 23;
    private static final int OUTPUT_SLOT = 24;
    private static final int BTN_CRAFT = 49;
    private static final int BTN_CLEAR = 45;
    private static final int BTN_CLOSE = 53;

    private static final int[] GLASS_SLOTS = {
        5,6,7,8, 14,15,16,17, 25,26, 32,33,34,35, 41,42,43,44, 46,47,48,50,51,52
    };

    private final WorkbenchRecipeConfig recipeConfig;
    private final BlueprintItemFactory blueprintFactory;

    public WorkbenchGui(WorkbenchRecipeConfig recipeConfig, BlueprintItemFactory blueprintFactory) {
        this.recipeConfig = recipeConfig;
        this.blueprintFactory = blueprintFactory;
    }

    public WorkbenchRecipeConfig getRecipeConfig() { return recipeConfig; }
    public BlueprintItemFactory getBlueprintFactory() { return blueprintFactory; }

    /* ==================== 打开 GUI ==================== */

    public void open(Player player) {
        BaseGuiHolder holder = new BaseGuiHolder(player.getUniqueId(), null, -1);
        holder.guiType = GUI_TYPE;
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, GUI_TITLE);
        holder.inventory = inv;

        ItemStack glass = createPlaceholder(" ");
        for (int slot : GLASS_SLOTS) {
            inv.setItem(slot, glass);
        }

        inv.setItem(BLUEPRINT_SLOT, createPlaceholder("§b放入图纸"));

        inv.setItem(BTN_CLEAR, createButton(Material.BARRIER, "§c清空材料", "将材料格中的物品返回背包"));
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

        /* 点击背包 → 延迟更新预览 */
        if (slot < 0 || slot >= GUI_SIZE) {
            final Inventory fInv = inv;
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("XH"),
                () -> updatePreview(fInv));
            return;
        }

        /* 底部按钮 */
        if (slot == BTN_CLEAR) {
            event.setCancelled(true);
            clearMaterials(inv, player);
            return;
        }
        if (slot == BTN_CLOSE) {
            event.setCancelled(true);
            returnAll(inv, player);
            player.closeInventory();
            return;
        }
        if (slot == BTN_CRAFT) {
            event.setCancelled(true);
            MatchedRecipe matched = matchCurrent(inv);
            if (matched != null) craftRecipe(inv, player, matched);
            return;
        }

        /* 输出预览 → 禁止 */
        if (slot == OUTPUT_SLOT) { event.setCancelled(true); return; }

        /* 分隔玻璃 → 禁止 */
        for (int g : GLASS_SLOTS) {
            if (slot == g) { event.setCancelled(true); return; }
        }

        /* 图纸槽 → 只接受图纸，允许取出 */
        if (slot == BLUEPRINT_SLOT) {
            ItemStack cursor = event.getCursor();
            ItemStack current = inv.getItem(slot);
            if (cursor != null && !cursor.getType().isAir()) {
                /* 放入：必须是图纸 */
                if (BlueprintItemFactory.getBlueprintId(cursor) == null) {
                    event.setCancelled(true);
                    return;
                }
            }
            final Inventory fInv = inv;
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("XH"),
                () -> updatePreview(fInv));
            return;
        }

        /* 材料槽 */
        boolean materialSlot = false;
        for (int ms : MATERIAL_SLOTS) {
            if (slot == ms) { materialSlot = true; break; }
        }
        if (!materialSlot) { event.setCancelled(true); return; }

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

        Inventory inv = event.getInventory();

        for (int dragSlot : event.getRawSlots()) {
            if (dragSlot >= GUI_SIZE) continue;
            /* 允许材料槽和图纸槽 */
            boolean valid = (dragSlot == BLUEPRINT_SLOT);
            for (int ms : MATERIAL_SLOTS) {
                if (dragSlot == ms) { valid = true; break; }
            }
            if (!valid) { event.setCancelled(true); return; }
        }

        /* 拖拽到图纸槽时检查是否是图纸 */
        if (event.getRawSlots().contains(BLUEPRINT_SLOT)) {
            ItemStack cursor = event.getOldCursor();
            if (cursor != null && !cursor.getType().isAir()
                && BlueprintItemFactory.getBlueprintId(cursor) == null) {
                event.setCancelled(true);
                return;
            }
        }

        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("XH"),
            () -> updatePreview(inv));
    }

    /* ==================== 关闭 ==================== */

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        BaseGuiHolder holder = getHolder(event.getInventory());
        if (holder == null || !GUI_TYPE.equals(holder.guiType)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!holder.playerId.equals(player.getUniqueId())) return;

        returnAll(event.getInventory(), player);
    }

    /* ==================== 配方匹配 ==================== */

    private void updatePreview(Inventory inv) {
        MatchedRecipe matched = matchCurrent(inv);
        ItemStack preview = (matched != null) ? createOutputPreview(matched) : null;
        inv.setItem(OUTPUT_SLOT, preview);
        updateCraftButton(inv, matched);
    }

    /** 从图纸 + 材料计数匹配配方 */
    private MatchedRecipe matchCurrent(Inventory inv) {
        /* 读取图纸 */
        ItemStack blueprint = inv.getItem(BLUEPRINT_SLOT);
        if (blueprint == null || blueprint.getType().isAir()) return null;
        String blueprintId = BlueprintItemFactory.getBlueprintId(blueprint);
        if (blueprintId == null) return null;

        /* 统计 5×5 区域材料数量 */
        Map<Material, Integer> counts = new HashMap<>();
        for (int slot : MATERIAL_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                counts.merge(item.getType(), item.getAmount(), Integer::sum);
            }
        }

        WorkbenchRecipeConfig.RecipeDef def = recipeConfig.matchRecipe(blueprintId, counts);
        if (def == null) return null;
        return new MatchedRecipe(def, blueprint);
    }

    private ItemStack createOutputPreview(MatchedRecipe matched) {
        WorkbenchRecipeConfig.RecipeDef def = matched.def;
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
        lore.add(Component.text("§7图纸耐久: §e" + BlueprintItemFactory.getDurability(matched.blueprint),
            NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("§7点击下方铁砧按钮确认合成", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        preview.setItemMeta(meta);
        return preview;
    }

    private ItemStack createAmmoPreview(String outputId) {
        String[] parts = outputId.split(":", 2);
        if (parts.length != 2) return null;
        AmmoConfig ammo = GunSystemConfig.ammo();
        if (ammo == null) return null;
        return ammo.createAmmoItemStack(parts[0], parts[1]);
    }

    private ItemStack createCustomItem(WorkbenchRecipeConfig.RecipeDef def) {
        Material mat = Material.getMaterial(def.outputMaterial.toUpperCase());
        if (mat == null) return null;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (!def.outputDisplayName.isEmpty()) {
            meta.displayName(Component.text(def.outputDisplayName));
        }

        for (Map.Entry<String, String> e : def.outputAttrs.entrySet()) {
            try {
                double dv = Double.parseDouble(e.getValue());
                meta.getPersistentDataContainer().set(
                    new NamespacedKey("xh", e.getKey()), PersistentDataType.DOUBLE, dv);
                continue;
            } catch (NumberFormatException ignored) {}
            try {
                int iv = Integer.parseInt(e.getValue());
                meta.getPersistentDataContainer().set(
                    new NamespacedKey("xh", e.getKey()), PersistentDataType.INTEGER, iv);
                continue;
            } catch (NumberFormatException ignored) {}
            meta.getPersistentDataContainer().set(
                new NamespacedKey("xh", e.getKey()), PersistentDataType.STRING, e.getValue());
        }

        item.setItemMeta(meta);
        return item;
    }

    private void updateCraftButton(Inventory inv, MatchedRecipe matched) {
        if (matched != null) {
            inv.setItem(BTN_CRAFT, createButton(Material.ANVIL,
                "§a§l确认合成", "§7产出: §f" + matched.def.name));
        } else {
            inv.setItem(BTN_CRAFT, createButton(Material.GRAY_DYE,
                "§8无匹配配方", "§7放入图纸和材料后自动匹配"));
        }
    }

    /* ==================== 合成 ==================== */

    private void craftRecipe(Inventory inv, Player player, MatchedRecipe matched) {
        WorkbenchRecipeConfig.RecipeDef def = matched.def;

        /* 消耗材料（按计数扣除） */
        Map<Material, Integer> toConsume = new HashMap<>(def.materials);
        for (int slot : MATERIAL_SLOTS) {
            if (toConsume.isEmpty()) break;
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            int needed = toConsume.getOrDefault(item.getType(), 0);
            if (needed <= 0) continue;

            int take = Math.min(needed, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) inv.setItem(slot, null);

            int remaining = needed - take;
            if (remaining <= 0) toConsume.remove(item.getType());
            else toConsume.put(item.getType(), remaining);
        }

        /* 消耗图纸耐久 */
        boolean blueprintBroken = false;
        ItemStack blueprint = inv.getItem(BLUEPRINT_SLOT);
        if (blueprint != null && BlueprintItemFactory.getBlueprintId(blueprint) != null) {
            int dur = BlueprintItemFactory.getDurability(blueprint);
            if (dur <= 1) {
                /* 耐久归零 → 图纸损坏 */
                inv.setItem(BLUEPRINT_SLOT, null);
                blueprintBroken = true;
            } else {
                BlueprintItemFactory.consumeDurability(blueprint);
            }
        }

        /* 产出 */
        ItemStack output = switch (def.type) {
            case "gun" -> GunSystemConfig.gun().createGunItem(def.outputId);
            case "ammo" -> {
                String[] parts = def.outputId.split(":", 2);
                AmmoConfig ammo = GunSystemConfig.ammo();
                if (ammo == null || parts.length != 2) yield null;
                ItemStack a = ammo.createAmmoItemStack(parts[0], parts[1]);
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
            if (blueprintBroken) {
                player.sendMessage("§c图纸耐久耗尽，已损坏");
            }
        }

        updatePreview(inv);
        playConfirmSound(player);
    }

    /* ==================== 材料管理 ==================== */

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

    /** 关闭时返还材料 + 图纸 */
    private void returnAll(Inventory inv, Player player) {
        for (int slot : MATERIAL_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                returnToPlayer(player, item);
                inv.setItem(slot, null);
            }
        }
        /* 返还图纸 */
        ItemStack blueprint = inv.getItem(BLUEPRINT_SLOT);
        if (blueprint != null && !blueprint.getType().isAir()) {
            returnToPlayer(player, blueprint);
            inv.setItem(BLUEPRINT_SLOT, null);
        }
    }

    /* ==================== 内部数据类 ==================== */

    private static class MatchedRecipe {
        final WorkbenchRecipeConfig.RecipeDef def;
        final ItemStack blueprint;

        MatchedRecipe(WorkbenchRecipeConfig.RecipeDef def, ItemStack blueprint) {
            this.def = def;
            this.blueprint = blueprint;
        }
    }
}