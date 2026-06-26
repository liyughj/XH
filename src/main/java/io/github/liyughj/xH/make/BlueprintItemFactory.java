package io.github.liyughj.xH.make;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 图纸物品工厂 —— 生成带 PDC 标记的图纸 ItemStack。
 *
 * <p>PDC 键：
 * <ul>
 *   <li>{@code blueprint_id} (STRING) — 图纸 ID，对应 blueprints.yml</li>
 *   <li>{@code blueprint_durability} (INTEGER) — 当前剩余耐久</li>
 *   <li>{@code blueprint_max_durability} (INTEGER) — 最大耐久（用于 lore 显示）</li>
 * </ul>
 * </p>
 */
public class BlueprintItemFactory {

    public static final NamespacedKey KEY_ID = new NamespacedKey("xh", "blueprint_id");
    public static final NamespacedKey KEY_DURABILITY = new NamespacedKey("xh", "blueprint_durability");
    public static final NamespacedKey KEY_MAX_DURABILITY = new NamespacedKey("xh", "blueprint_max_durability");

    private final BlueprintConfig config;

    public BlueprintItemFactory(BlueprintConfig config) {
        this.config = config;
    }

    public BlueprintConfig getConfig() { return config; }

    /**
     * 根据图纸 ID 创建图纸物品。
     * @param blueprintId 图纸 ID
     * @return 图纸 ItemStack，若 ID 不存在返回 null
     */
    public ItemStack createBlueprint(String blueprintId) {
        BlueprintConfig.BlueprintDef def = config.get(blueprintId);
        if (def == null) return null;

        ItemStack item = new ItemStack(def.material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        /* 显示名 */
        meta.displayName(Component.text(def.name).decoration(TextDecoration.ITALIC, false));

        /* Lore */
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7耐久: §e" + def.durability + " §7/ " + def.durability));
        if (def.lore != null) {
            for (String line : def.lore) {
                lore.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
            }
        }
        lore.add(Component.text(""));
        lore.add(Component.text("§8图纸 ID: " + def.id));
        meta.lore(lore);

        /* PDC */
        meta.getPersistentDataContainer().set(KEY_ID, PersistentDataType.STRING, def.id);
        meta.getPersistentDataContainer().set(KEY_DURABILITY, PersistentDataType.INTEGER, def.durability);
        meta.getPersistentDataContainer().set(KEY_MAX_DURABILITY, PersistentDataType.INTEGER, def.durability);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * 读取物品的图纸 ID。
     * @return 图纸 ID，若不是图纸返回 null
     */
    public static String getBlueprintId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY_ID, PersistentDataType.STRING);
    }

    /**
     * 读取物品的图纸剩余耐久。
     * @return 耐久值，若不是图纸返回 -1
     */
    public static int getDurability(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return -1;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(KEY_DURABILITY, PersistentDataType.INTEGER);
        return v != null ? v : -1;
    }

    /**
     * 消耗 1 点耐久，更新 PDC 和 Lore。
     * @return true=成功消耗，false=耐久不足或不是图纸
     */
    public static boolean consumeDurability(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        Integer current = meta.getPersistentDataContainer().get(KEY_DURABILITY, PersistentDataType.INTEGER);
        Integer max = meta.getPersistentDataContainer().get(KEY_MAX_DURABILITY, PersistentDataType.INTEGER);
        if (current == null || current <= 0 || max == null) return false;

        int newDur = current - 1;
        meta.getPersistentDataContainer().set(KEY_DURABILITY, PersistentDataType.INTEGER, newDur);

        /* 更新 lore 中的耐久行 */
        if (meta.hasLore()) {
            List<Component> lore = new ArrayList<>(meta.lore());
            for (int i = 0; i < lore.size(); i++) {
                String text = toPlain(lore.get(i));
                if (text.startsWith("耐久:")) {
                    lore.set(i, Component.text("§7耐久: §e" + newDur + " §7/ " + max)
                        .decoration(TextDecoration.ITALIC, false));
                    break;
                }
            }
            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return true;
    }

    private static String toPlain(Component c) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
    }
}