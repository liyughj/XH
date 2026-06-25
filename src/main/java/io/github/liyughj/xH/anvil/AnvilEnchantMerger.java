package io.github.liyughj.xH.anvil;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 铁砧附魔合并工具类
 * 提供附魔合并、互斥检查、兼容性检查等功能
 */
public class AnvilEnchantMerger {

    /* ========== 互斥附魔组定义 ========== */

    /**
     * 剑类/武器伤害附魔组
     */
    private static final Set<Enchantment> WEAPON_DAMAGE_ENCHANTS = new HashSet<>();

    /**
     * 护甲保护附魔组
     */
    private static final Set<Enchantment> ARMOR_PROTECTION_ENCHANTS = new HashSet<>();

    /**
     * 挖掘工具附魔组
     */
    private static final Set<Enchantment> TOOL_ENCHANTS = new HashSet<>();

    /**
     * 弓类附魔组
     */
    private static final Set<Enchantment> BOW_ENCHANTS = new HashSet<>();

    /**
     * 弩类附魔组
     */
    private static final Set<Enchantment> CROSSBOW_ENCHANTS = new HashSet<>();

    /**
     * 所有互斥附魔组
     */
    private static final List<Set<Enchantment>> EXCLUSIVE_GROUPS = new ArrayList<>();

    static {
        /* 剑类/武器伤害附魔组 */
        WEAPON_DAMAGE_ENCHANTS.add(Enchantment.SHARPNESS);
        WEAPON_DAMAGE_ENCHANTS.add(Enchantment.SMITE);
        WEAPON_DAMAGE_ENCHANTS.add(Enchantment.BANE_OF_ARTHROPODS);
        WEAPON_DAMAGE_ENCHANTS.add(Enchantment.DENSITY);
        WEAPON_DAMAGE_ENCHANTS.add(Enchantment.BREACH);

        /* 护甲保护附魔组 */
        ARMOR_PROTECTION_ENCHANTS.add(Enchantment.PROTECTION);
        ARMOR_PROTECTION_ENCHANTS.add(Enchantment.FIRE_PROTECTION);
        ARMOR_PROTECTION_ENCHANTS.add(Enchantment.BLAST_PROTECTION);
        ARMOR_PROTECTION_ENCHANTS.add(Enchantment.PROJECTILE_PROTECTION);

        /* 挖掘工具附魔组 */
        TOOL_ENCHANTS.add(Enchantment.SILK_TOUCH);
        TOOL_ENCHANTS.add(Enchantment.FORTUNE);

        /* 弓类附魔组 */
        BOW_ENCHANTS.add(Enchantment.INFINITY);
        BOW_ENCHANTS.add(Enchantment.MENDING);

        /* 弩类附魔组 */
        CROSSBOW_ENCHANTS.add(Enchantment.MULTISHOT);
        CROSSBOW_ENCHANTS.add(Enchantment.PIERCING);

        /* 添加到互斥组列表 */
        EXCLUSIVE_GROUPS.add(WEAPON_DAMAGE_ENCHANTS);
        EXCLUSIVE_GROUPS.add(ARMOR_PROTECTION_ENCHANTS);
        EXCLUSIVE_GROUPS.add(TOOL_ENCHANTS);
        EXCLUSIVE_GROUPS.add(BOW_ENCHANTS);
        EXCLUSIVE_GROUPS.add(CROSSBOW_ENCHANTS);
    }

    /**
     * 获取物品的附魔（支持普通物品和附魔书）
     *
     * @param item 物品
     * @return 附魔映射
     */
    public static Map<Enchantment, Integer> getEnchantments(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return new HashMap<>();
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return new HashMap<>();
        }

        /* 检查是否是附魔书 */
        if (meta instanceof EnchantmentStorageMeta) {
            /* 附魔书的附魔存储在 EnchantmentStorageMeta 中 */
            EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) meta;
            return new HashMap<>(bookMeta.getStoredEnchants());
        } else {
            /* 普通物品的附魔存储在 ItemMeta 中 */
            return new HashMap<>(meta.getEnchants());
        }
    }

    /**
     * 检查物品是否是附魔书
     *
     * @param item 物品
     * @return 是否是附魔书
     */
    public static boolean isEnchantedBook(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return item.getItemMeta() instanceof EnchantmentStorageMeta;
    }

    /**
     * 合并两个物品的附魔
     *
     * @param first  第一个物品
     * @param second 第二个物品
     * @return 合并后的附魔映射
     */
    public static Map<Enchantment, Integer> mergeEnchantments(ItemStack first, ItemStack second) {
        Map<Enchantment, Integer> result = new HashMap<>();

        /* 获取两个物品的附魔 */
        Map<Enchantment, Integer> firstEnchants = getEnchantments(first);
        Map<Enchantment, Integer> secondEnchants = getEnchantments(second);

        /* 判断是否需要检查互斥 */
        boolean checkExclusive = !isEnchantedBook(first) && !isEnchantedBook(second);

        /* 添加第一个物品的所有附魔 */
        result.putAll(firstEnchants);

        /* 处理第二个物品的附魔 */
        for (Map.Entry<Enchantment, Integer> entry : secondEnchants.entrySet()) {
            Enchantment enchant = entry.getKey();
            int secondLevel = entry.getValue();

            /* 检查该附魔是否已存在于结果中 */
            if (result.containsKey(enchant)) {
                /* 已存在：保留较高等级，不升级 */
                int firstLevel = result.get(enchant);
                if (secondLevel > firstLevel) {
                    result.put(enchant, secondLevel);
                }
                /* 如果等级相同或较低，保持原样（不升级） */
            } else {
                /* 不存在：检查是否互斥 */
                if (checkExclusive) {
                    /* 需要检查互斥 */
                    if (!isExclusiveWithAny(enchant, result.keySet())) {
                        result.put(enchant, secondLevel);
                    }
                } else {
                    /* 不需要检查互斥（至少有一个是附魔书） */
                    result.put(enchant, secondLevel);
                }
            }
        }

        return result;
    }

    /**
     * 检查附魔是否与集合中的任何附魔互斥
     *
     * @param enchant      要检查的附魔
     * @param existingSet  已存在的附魔集合
     * @return 是否互斥
     */
    private static boolean isExclusiveWithAny(Enchantment enchant, Set<Enchantment> existingSet) {
        for (Enchantment existing : existingSet) {
            if (areExclusive(enchant, existing)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查两个附魔是否互斥
     *
     * @param enchant1 第一个附魔
     * @param enchant2 第二个附魔
     * @return 是否互斥
     */
    private static boolean areExclusive(Enchantment enchant1, Enchantment enchant2) {
        /* 如果是同一个附魔，不互斥 */
        if (enchant1.equals(enchant2)) {
            return false;
        }

        /* 检查是否在同一个互斥组中 */
        for (Set<Enchantment> group : EXCLUSIVE_GROUPS) {
            if (group.contains(enchant1) && group.contains(enchant2)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查附魔是否可以应用到目标物品
     *
     * @param target  目标物品
     * @param enchant 附魔
     * @return 是否可以应用
     */
    public static boolean canApplyEnchantment(ItemStack target, Enchantment enchant) {
        if (target == null || target.getType().isAir() || enchant == null) {
            return false;
        }

        /* 如果目标是附魔书，可以存储任何附魔 */
        if (isEnchantedBook(target)) {
            return true;
        }

        /* 普通物品：使用原版方法检查 */
        return enchant.canEnchantItem(target);
    }

    /**
     * 过滤掉不能应用到目标物品的附魔
     *
     * @param target    目标物品
     * @param enchants  附魔映射
     * @return 过滤后的附魔映射
     */
    public static Map<Enchantment, Integer> filterCompatibleEnchants(ItemStack target,
                                                                      Map<Enchantment, Integer> enchants) {
        Map<Enchantment, Integer> result = new HashMap<>();

        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            if (canApplyEnchantment(target, entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        return result;
    }
}
