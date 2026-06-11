package io.github.liyughj.xH.rpg.Attribute;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RPG 属性持久化存储层（PDC）。
 * <p>
 * 存储规范：
 * <ul>
 *   <li>物品属性 → ItemMeta PDC，key = "item.&lt;attrKey&gt;"</li>
 *   <li>玩家属性 → Player PDC，key = "player.&lt;attrKey&gt;"</li>
 *   <li>单值（兼容）：存为 "item.&lt;key&gt;" = double</li>
 *   <li>区间（新格式）：存为 "item.&lt;key&gt;_min" + "item.&lt;key&gt;_max" = double</li>
 *   <li>百分比属性由上层 /100 使用</li>
 * </ul>
 */
public class AttributeStorage {

    private static final String PDC_PREFIX_ITEM = "item.";
    private static final String PDC_PREFIX_PLAYER = "player.";

    private static JavaPlugin plugin;
    private static final Map<String, NamespacedKey> keyCache = new HashMap<>();

    /** 插件启用时由外部调用一次 */
    public static void init(JavaPlugin javaPlugin) {
        plugin = javaPlugin;
    }

    /** 获取或缓存 NamespacedKey */
    private static NamespacedKey key(String pdcKey) {
        return keyCache.computeIfAbsent(pdcKey, k -> new NamespacedKey(plugin, k));
    }

    /* ==================== 物品属性 ==================== */

    /**
     * 从物品读取单个属性值。不存在返回该属性的默认值。
     */
    public static double getItemAttr(ItemStack item, RpgAttribute attr) {
        if (item == null || !item.hasItemMeta()) return attr.getDefaultValue();
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey nk = key(PDC_PREFIX_ITEM + attr.getKey());
        Double val = pdc.get(nk, PersistentDataType.DOUBLE);
        return val != null ? attr.clamp(val) : attr.getDefaultValue();
    }

    /**
     * 设置物品的单个属性值。自动 clamp 到合法范围。
     */
    public static void setItemAttr(ItemMeta meta, RpgAttribute attr, double value) {
        if (meta == null) return;
        double clamped = attr.clamp(value);
        meta.getPersistentDataContainer().set(
            key(PDC_PREFIX_ITEM + attr.getKey()), PersistentDataType.DOUBLE, clamped);
    }

    /**
     * 检查物品是否有指定属性（值 ≠ 默认值即为有）。
     */
    public static boolean hasItemAttr(ItemStack item, RpgAttribute attr) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(key(PDC_PREFIX_ITEM + attr.getKey()), PersistentDataType.DOUBLE);
    }

    /**
     * 移除物品的单个属性。
     */
    public static void removeItemAttr(ItemMeta meta, RpgAttribute attr) {
        if (meta == null) return;
        meta.getPersistentDataContainer().remove(key(PDC_PREFIX_ITEM + attr.getKey()));
    }

    /**
     * 获取物品上所有非默认 RPG 属性（保持添加顺序）。
     */
    public static Map<RpgAttribute, Double> getAllItemAttrs(ItemStack item) {
        Map<RpgAttribute, Double> result = new LinkedHashMap<>();
        if (item == null || !item.hasItemMeta()) return result;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        for (RpgAttribute attr : RpgAttribute.values()) {
            NamespacedKey nk = key(PDC_PREFIX_ITEM + attr.getKey());
            Double val = pdc.get(nk, PersistentDataType.DOUBLE);
            if (val != null) {
                result.put(attr, attr.clamp(val));
            }
        }
        return result;
    }

    /**
     * 从物品读取属性区间值。兼容新旧 PDC 格式。
     * 不存在返回 min=max=默认值 的区间。
     */
    public static AttributeRange getItemAttrRange(ItemStack item, RpgAttribute attr) {
        if (item == null || !item.hasItemMeta()) return new AttributeRange(attr.getDefaultValue(), attr.getDefaultValue());
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return AttributeRange.fromPdc(pdc,
            key(PDC_PREFIX_ITEM + attr.getKey()),
            key(PDC_PREFIX_ITEM + attr.getKey() + "_min"),
            key(PDC_PREFIX_ITEM + attr.getKey() + "_max"),
            attr);
    }

    /**
     * 设置物品的属性区间值。
     */
    public static void setItemAttrRange(ItemMeta meta, RpgAttribute attr, AttributeRange range) {
        if (meta == null || range == null) return;
        range.writeToPdc(meta.getPersistentDataContainer(),
            key(PDC_PREFIX_ITEM + attr.getKey()),
            key(PDC_PREFIX_ITEM + attr.getKey() + "_min"),
            key(PDC_PREFIX_ITEM + attr.getKey() + "_max"));
    }

    /* ==================== 玩家属性 ==================== */

    /**
     * 从玩家读取单个属性值。不存在返回默认值。
     */
    public static double getPlayerAttr(Player player, RpgAttribute attr) {
        if (player == null) return attr.getDefaultValue();
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        NamespacedKey nk = key(PDC_PREFIX_PLAYER + attr.getKey());
        Double val = pdc.get(nk, PersistentDataType.DOUBLE);
        return val != null ? attr.clamp(val) : attr.getDefaultValue();
    }

    /**
     * 设置玩家的单个属性值。
     */
    public static void setPlayerAttr(Player player, RpgAttribute attr, double value) {
        if (player == null) return;
        double clamped = attr.clamp(value);
        player.getPersistentDataContainer().set(
            key(PDC_PREFIX_PLAYER + attr.getKey()), PersistentDataType.DOUBLE, clamped);
    }

    /**
     * 检查玩家是否有指定属性。
     */
    public static boolean hasPlayerAttr(Player player, RpgAttribute attr) {
        if (player == null) return false;
        return player.getPersistentDataContainer().has(
            key(PDC_PREFIX_PLAYER + attr.getKey()), PersistentDataType.DOUBLE);
    }

    /**
     * 移除玩家的单个属性。
     */
    public static void removePlayerAttr(Player player, RpgAttribute attr) {
        if (player == null) return;
        player.getPersistentDataContainer().remove(key(PDC_PREFIX_PLAYER + attr.getKey()));
    }

    /**
     * 获取玩家所有非默认 RPG 属性。
     */
    public static Map<RpgAttribute, Double> getAllPlayerAttrs(Player player) {
        Map<RpgAttribute, Double> result = new LinkedHashMap<>();
        if (player == null) return result;

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        for (RpgAttribute attr : RpgAttribute.values()) {
            NamespacedKey nk = key(PDC_PREFIX_PLAYER + attr.getKey());
            Double val = pdc.get(nk, PersistentDataType.DOUBLE);
            if (val != null) {
                result.put(attr, attr.clamp(val));
            }
        }
        return result;
    }

    /**
     * 从玩家读取属性区间值。兼容新旧 PDC 格式。
     */
    public static AttributeRange getPlayerAttrRange(Player player, RpgAttribute attr) {
        if (player == null) return new AttributeRange(attr.getDefaultValue(), attr.getDefaultValue());
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        return AttributeRange.fromPdc(pdc,
            key(PDC_PREFIX_PLAYER + attr.getKey()),
            key(PDC_PREFIX_PLAYER + attr.getKey() + "_min"),
            key(PDC_PREFIX_PLAYER + attr.getKey() + "_max"),
            attr);
    }

    /**
     * 设置玩家的属性区间值。
     */
    public static void setPlayerAttrRange(Player player, RpgAttribute attr, AttributeRange range) {
        if (player == null || range == null) return;
        range.writeToPdc(player.getPersistentDataContainer(),
            key(PDC_PREFIX_PLAYER + attr.getKey()),
            key(PDC_PREFIX_PLAYER + attr.getKey() + "_min"),
            key(PDC_PREFIX_PLAYER + attr.getKey() + "_max"));
    }
}
