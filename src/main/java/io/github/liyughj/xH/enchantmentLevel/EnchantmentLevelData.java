package io.github.liyughj.xH.enchantmentLevel;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * 附魔经验数据持久化存储类
 * 使用 PersistentDataContainer (PDC) 将附魔经验数据存储在物品的 NBT 中
 * 数据随物品掉落、交易、死亡不掉落时自动保留
 */
public class EnchantmentLevelData {

    /* PDC 容器命名空间 */
    private static final String NAMESPACE = "xh_data";

    /* 静态插件引用（在插件启用时通过 init() 设置） */
    private static JavaPlugin plugin;

    /**
     * 初始化静态插件引用
     * 必须在插件 onEnable 时调用一次
     *
     * @param javaPlugin 插件实例
     */
    public static void init(JavaPlugin javaPlugin) {
        plugin = javaPlugin;
    }
    private static final int DEFAULT_LEVEL = 1;
    private static final int DEFAULT_EXP = 0;

    /* 当前等级 */
    private int level;

    /* 当前经验值 */
    private int exp;

    /* 达到过的最高等级 */
    private int maxLevelReached;

    /* 升级所需经验值（运行时缓存，不存储） */
    private int expToNextLevel;

    /**
     * 构造函数
     *
     * @param level 当前等级
     * @param exp   当前经验值
     * @param maxLevelReached 达到过的最高等级
     */
    public EnchantmentLevelData(int level, int exp, int maxLevelReached) {
        this.level = Math.max(1, level);
        this.exp = Math.max(0, exp);
        this.maxLevelReached = Math.max(this.level, maxLevelReached);
    }

    /**
     * 创建默认数据（1级，0经验）
     */
    public EnchantmentLevelData() {
        this(DEFAULT_LEVEL, DEFAULT_EXP, DEFAULT_LEVEL);
    }

    /**
     * 添加经验值
     *
     * @param amount 经验数量
     * @return 是否升级
     */
    public boolean addExp(int amount) {
        this.exp += amount;
        boolean upgraded = false;

        /* 循环检查是否可以升级 */
        while (this.exp >= this.expToNextLevel && this.expToNextLevel > 0) {
            this.exp -= this.expToNextLevel;
            this.level++;
            this.maxLevelReached = Math.max(this.maxLevelReached, this.level);
            upgraded = true;
        }

        return upgraded;
    }

    /**
     * 向经验数据写入 PDC
     *
     * @param plugin    插件实例
     * @param meta      物品 Meta
     * @param enchant   附魔类型
     * @param data      经验数据
     */
    public static void saveSingleToItem(JavaPlugin plugin, ItemMeta meta, Enchantment enchant, EnchantmentLevelData data) {
        String key = enchantKey(enchant);
        NamespacedKey levelKey = new NamespacedKey(plugin, NAMESPACE + "/" + key + "_level");
        NamespacedKey expKey = new NamespacedKey(plugin, NAMESPACE + "/" + key + "_exp");
        NamespacedKey maxKey = new NamespacedKey(plugin, NAMESPACE + "/" + key + "_max");

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(levelKey, PersistentDataType.INTEGER, data.level);
        pdc.set(expKey, PersistentDataType.INTEGER, data.exp);
        pdc.set(maxKey, PersistentDataType.INTEGER, data.maxLevelReached);
    }

    /**
     * 批量保存所有附魔经验数据到物品
     *
     * @param item      物品
     * @param dataMap   附魔到经验数据的映射
     */
    public static void saveToItem(ItemStack item, Map<Enchantment, EnchantmentLevelData> dataMap) {
        if (item == null || !item.hasItemMeta() || plugin == null) return;

        ItemMeta meta = item.getItemMeta();

        for (Map.Entry<Enchantment, EnchantmentLevelData> entry : dataMap.entrySet()) {
            saveSingleToItem(plugin, meta, entry.getKey(), entry.getValue());
        }

        item.setItemMeta(meta);
    }

    /**
     * 从物品加载所有附魔的经验数据
     * 保持原版附魔顺序（使用 LinkedHashMap）
     *
     * @param item 物品
     * @return 附魔到经验数据的映射（只包含物品上实际存在的附魔）
     */
    public static Map<Enchantment, EnchantmentLevelData> loadFromItem(ItemStack item) {
        /* 使用 LinkedHashMap 保持顺序 */
        Map<Enchantment, EnchantmentLevelData> result = new LinkedHashMap<>();

        if (item == null || !item.hasItemMeta() || plugin == null) return result;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        /* 直接从 ItemMeta 获取附魔，保持原版顺序 */
        Map<Enchantment, Integer> enchantLevels;
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            enchantLevels = storageMeta.getStoredEnchants();
        } else {
            enchantLevels = meta.getEnchants();
        }

        /* 按原版顺序遍历附魔 */
        for (Enchantment enchant : enchantLevels.keySet()) {
            String key = enchantKey(enchant);
            NamespacedKey levelKey = new NamespacedKey(plugin, NAMESPACE + "/" + key + "_level");
            NamespacedKey expKey = new NamespacedKey(plugin, NAMESPACE + "/" + key + "_exp");
            NamespacedKey maxKey = new NamespacedKey(plugin, NAMESPACE + "/" + key + "_max");

            if (pdc.has(levelKey, PersistentDataType.INTEGER)) {
                int level = pdc.getOrDefault(levelKey, PersistentDataType.INTEGER, DEFAULT_LEVEL);
                int exp = pdc.getOrDefault(expKey, PersistentDataType.INTEGER, DEFAULT_EXP);
                int maxLevel = pdc.getOrDefault(maxKey, PersistentDataType.INTEGER, level);
                result.put(enchant, new EnchantmentLevelData(level, exp, maxLevel));
            }
        }

        return result;
    }

    /**
     * 获取物品上所有附魔类型（支持附魔书）
     *
     * @param item 物品
     * @return 附魔集合
     */
    public static Set<Enchantment> getAllEnchantments(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Collections.emptySet();

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            return storageMeta.getStoredEnchants().keySet();
        }
        return meta.getEnchants().keySet();
    }

    /**
     * 获取物品上所有附魔及其等级（支持附魔书）
     *
     * @param item 物品
     * @return 附魔到等级的映射
     */
    public static Map<Enchantment, Integer> getAllEnchantmentLevels(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Collections.emptyMap();

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            return new HashMap<>(storageMeta.getStoredEnchants());
        }
        return new HashMap<>(meta.getEnchants());
    }

    /* ========== Getter ========== */

    public int getLevel() {
        return level;
    }

    public int getExp() {
        return exp;
    }

    public int getMaxLevelReached() {
        return maxLevelReached;
    }

    public int getExpToNextLevel() {
        return expToNextLevel;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
        this.maxLevelReached = Math.max(this.maxLevelReached, this.level);
    }

    public void setExpToNextLevel(int expToNextLevel) {
        this.expToNextLevel = expToNextLevel;
    }

    /* ========== 工具方法 ========== */

    /**
     * 生成附魔对应的 PDC key 字符串（不含命名空间）
     * Bukkit API 保证 enchant.getKey().getKey() 返回小写字符串
     */
    private static String enchantKey(Enchantment enchant) {
        return enchant.getKey().getKey();
    }
}