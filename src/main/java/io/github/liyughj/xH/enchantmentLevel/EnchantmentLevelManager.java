package io.github.liyughj.xH.enchantmentLevel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 附魔等级管理核心类
 * 处理经验计算、等级升级、罗马数字转换和 Lore 生成
 */
public class EnchantmentLevelManager {

    /* 罗马数字转换表（贪心算法，支持1~3999） */
    private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    /* 经验条字符 - 新样式：使用 Unicode 方块字符 */
    private static final String BAR_FILL = "█";
    private static final String BAR_EMPTY = "░";

    /* Lore 颜色 */
    private static final TextColor ENCHANT_COLOR = TextColor.color(0xAAAAAA);
    private static final TextColor EXP_BAR_FILL = TextColor.color(0x55AA55);
    private static final TextColor EXP_BAR_EMPTY = TextColor.color(0x333333);
    private static final TextColor EXP_TEXT_COLOR = TextColor.color(0x888888);
    private static final TextColor MAX_LEVEL_COLOR = TextColor.color(0xFFAA00);

    private final EnchantmentLevelConfig config;

    /**
     * 构造函数
     *
     * @param config 配置实例
     */
    public EnchantmentLevelManager(EnchantmentLevelConfig config) {
        this.config = config;
    }

    /**
     * 罗马数字转换（贪心算法）
     *
     * @param number 1~3999
     * @return 罗马数字字符串
     */
    public static String toRoman(int number) {
        if (number <= 0) return String.valueOf(number);
        if (number > 3999) return String.valueOf(number);

        StringBuilder roman = new StringBuilder();
        int n = number;
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (n >= ROMAN_VALUES[i]) {
                roman.append(ROMAN_SYMBOLS[i]);
                n -= ROMAN_VALUES[i];
            }
        }
        return roman.toString();
    }

    /**
     * 计算升级所需经验值
     *
     * @param currentLevel 当前等级
     * @param enchant      附魔类型（用于检查该附魔的最高等级）
     * @return 升级到下一级所需经验值
     */
    public int getExpForNextLevel(int currentLevel, Enchantment enchant) {
        int targetLevel = currentLevel + 1;
        int maxLevel = getMaxLevel(enchant);

        /* 检查是否达到最大等级 */
        if (targetLevel > maxLevel) {
            return Integer.MAX_VALUE;
        }

        if (config.isExponential()) {
            return (int) (config.getBaseExp() * Math.pow(config.getMultiplier(), targetLevel - 1));
        } else {
            return config.getBaseExp() + config.getIncrement() * (targetLevel - 1);
        }
    }

    /**
     * 计算升级所需经验值（不检查最大等级，用于显示）
     *
     * @param currentLevel 当前等级
     * @return 升级到下一级所需经验值
     */
    public int getExpForNextLevel(int currentLevel) {
        if (config.isExponential()) {
            return (int) (config.getBaseExp() * Math.pow(config.getMultiplier(), currentLevel));
        } else {
            return config.getBaseExp() + config.getIncrement() * currentLevel;
        }
    }

    /**
     * 获取附魔的最大等级
     *
     * @param enchant 附魔类型
     * @return 最大等级（从配置读取，0表示原版最高等级）
     */
    public int getMaxLevel(Enchantment enchant) {
        return config.getMaxLevel(enchant);
    }

    /**
     * 初始化物品上所有附魔的经验数据
     *
     * @param item 物品
     */
    public void initializeExp(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;

        Map<Enchantment, Integer> enchants = EnchantmentLevelData.getAllEnchantmentLevels(item);
        if (enchants.isEmpty()) return;

        Map<Enchantment, EnchantmentLevelData> dataMap = EnchantmentLevelData.loadFromItem(item);

        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            Enchantment enchant = entry.getKey();
            if (!dataMap.containsKey(enchant)) {
                int level = entry.getValue();
                EnchantmentLevelData data = new EnchantmentLevelData(level, 0, level);
                data.setExpToNextLevel(getExpForNextLevel(level, enchant));
                dataMap.put(enchant, data);
                changed = true;
            }
        }

        if (changed) {
            EnchantmentLevelData.saveToItem(item, dataMap);
        }
    }

    /**
     * 检查物品是否已有经验数据
     *
     * @param item 物品
     * @return 是否有经验数据
     */
    public boolean hasExpData(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        Map<Enchantment, EnchantmentLevelData> dataMap = EnchantmentLevelData.loadFromItem(item);
        Set<Enchantment> enchants = EnchantmentLevelData.getAllEnchantments(item);

        /* 每个存在的附魔都应有经验数据 */
        for (Enchantment enchant : enchants) {
            if (!dataMap.containsKey(enchant)) return false;
        }
        return !enchants.isEmpty() && !dataMap.isEmpty();
    }

    /**
     * 为单个附魔添加经验
     *
     * @param item     物品
     * @param enchant  附魔类型
     * @param amount   经验数量
     * @return 升级的附魔列表
     */
    public List<Enchantment> addExp(ItemStack item, Enchantment enchant, int amount) {
        List<Enchantment> upgraded = new ArrayList<>();
        if (item == null || !item.hasItemMeta()) return upgraded;

        /* 检查是否达到最大等级 */
        Map<Enchantment, Integer> currentLevels = EnchantmentLevelData.getAllEnchantmentLevels(item);
        Integer currentLevel = currentLevels.get(enchant);
        if (currentLevel == null) return upgraded;

        int maxLevel = getMaxLevel(enchant);
        if (currentLevel >= maxLevel) return upgraded;

        Map<Enchantment, EnchantmentLevelData> dataMap = EnchantmentLevelData.loadFromItem(item);
        EnchantmentLevelData data = dataMap.get(enchant);
        if (data == null) return upgraded;

        /* 设置升级所需经验 */
        data.setExpToNextLevel(getExpForNextLevel(data.getLevel(), enchant));

        boolean didUpgrade = data.addExp(amount);
        EnchantmentLevelData.saveToItem(item, dataMap);

        if (didUpgrade) {
            /* 同步原版附魔等级 */
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                storageMeta.addStoredEnchant(enchant, data.getLevel(), true);
            } else {
                meta.addEnchant(enchant, data.getLevel(), true);
            }
            item.setItemMeta(meta);
            upgraded.add(enchant);
        }

        return upgraded;
    }

    /**
     * 为一组附魔添加经验（用于护甲等多附魔场景）
     *
     * @param item     物品
     * @param amount   经验数量
     * @return 升级的附魔列表
     */
    public List<Enchantment> addExpToEnchantments(ItemStack item, int amount) {
        List<Enchantment> upgraded = new ArrayList<>();
        if (item == null || !item.hasItemMeta()) return upgraded;

        Set<Enchantment> enchants = EnchantmentLevelData.getAllEnchantments(item);
        if (enchants.isEmpty()) return upgraded;

        Map<Enchantment, EnchantmentLevelData> dataMap = EnchantmentLevelData.loadFromItem(item);
        boolean anyChanged = false;

        for (Enchantment enchant : enchants) {
            EnchantmentLevelData data = dataMap.get(enchant);
            if (data == null) continue;

            int maxLevel = getMaxLevel(enchant);
            Map<Enchantment, Integer> currentLevels = EnchantmentLevelData.getAllEnchantmentLevels(item);
            Integer level = currentLevels.get(enchant);
            if (level != null && level >= maxLevel) continue;

            data.setExpToNextLevel(getExpForNextLevel(data.getLevel(), enchant));

            boolean didUpgrade = data.addExp(amount);
            if (didUpgrade) {
                upgraded.add(enchant);
            }
            anyChanged = true;
        }

        if (anyChanged) {
            EnchantmentLevelData.saveToItem(item, dataMap);

            /* 同步原版附魔等级 */
            if (!upgraded.isEmpty()) {
                ItemMeta meta = item.getItemMeta();
                for (Enchantment enchant : upgraded) {
                    EnchantmentLevelData data = dataMap.get(enchant);
                    if (data == null) continue;
                    if (meta instanceof EnchantmentStorageMeta storageMeta) {
                        storageMeta.addStoredEnchant(enchant, data.getLevel(), true);
                    } else {
                        meta.addEnchant(enchant, data.getLevel(), true);
                    }
                }
                item.setItemMeta(meta);
            }
        }

        return upgraded;
    }

    /**
     * 生成附魔的显示 Lore（用于背包Shift悬停）
     * 按照原版附魔注册顺序排序
     *
     * @param item 物品
     * @return Lore Component 列表
     */
    public List<Component> getDisplayLoreComponents(ItemStack item) {
        List<Component> lore = new ArrayList<>();
        if (item == null || !item.hasItemMeta()) return lore;

        /* 直接从 ItemMeta 获取附魔 */
        ItemMeta meta = item.getItemMeta();
        Map<Enchantment, Integer> enchantLevels;
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            enchantLevels = storageMeta.getStoredEnchants();
        } else {
            enchantLevels = meta.getEnchants();
        }

        if (enchantLevels.isEmpty()) return lore;

        Map<Enchantment, EnchantmentLevelData> dataMap = EnchantmentLevelData.loadFromItem(item);

        /* 按照原版附魔注册顺序排序 */
        List<Map.Entry<Enchantment, Integer>> sortedEnchants = new ArrayList<>(enchantLevels.entrySet());
        sortedEnchants.sort(Comparator.comparingInt(e -> getEnchantmentOrder(e.getKey())));

        /* 按排序后的顺序遍历附魔 */
        for (Map.Entry<Enchantment, Integer> entry : sortedEnchants) {
            Enchantment enchant = entry.getKey();
            int level = entry.getValue();
            EnchantmentLevelData data = dataMap.get(enchant);

            int maxLevel = getMaxLevel(enchant);

            /* 附魔名 + 等级 + 经验条（合并为一行） */
            if (data != null && level < maxLevel) {
                int exp = data.getExp();
                int expNeeded = data.getExpToNextLevel();
                if (expNeeded <= 0) {
                    expNeeded = getExpForNextLevel(level, enchant);
                }

                String bar = buildExpBar(exp, expNeeded);
                Component enchantLine = Component.text()
                    .append(enchant.displayName(level))
                    .append(Component.text(" "))
                    .append(Component.text(bar, EXP_BAR_FILL))
                    .append(Component.text(" " + exp + "/" + expNeeded, EXP_TEXT_COLOR))
                    .color(ENCHANT_COLOR)
                    .build();
                lore.add(enchantLine);
            } else if (level >= maxLevel) {
                /* 满级显示 */
                Component maxLine = Component.text()
                    .append(enchant.displayName(level))
                    .append(Component.text(" "))
                    .append(Component.text("[MAX]", MAX_LEVEL_COLOR))
                    .color(ENCHANT_COLOR)
                    .build();
                lore.add(maxLine);
            } else {
                /* 无经验数据时只显示附魔名 */
                Component enchantLine = Component.text()
                    .append(enchant.displayName(level))
                    .color(ENCHANT_COLOR)
                    .build();
                lore.add(enchantLine);
            }
        }

        return lore;
    }

    /**
     * 获取附魔的排序序号（按照原版注册顺序）
     *
     * @param enchant 附魔
     * @return 排序序号
     */
    private int getEnchantmentOrder(Enchantment enchant) {
        /* 按照原版附魔注册顺序定义排序权重 */
        String key = enchant.getKey().getKey().toLowerCase();
        return switch (key) {
            /* 剑类伤害附魔 */
            case "sharpness" -> 1;           // 锋利
            case "smite" -> 2;               // 亡灵杀手
            case "bane_of_arthropods" -> 3;  // 节肢杀手
            case "sweeping" -> 4;            // 横扫之刃
            case "fire_aspect" -> 5;         // 火焰附加
            case "knockback" -> 6;           // 击退
            case "looting" -> 7;             // 抢夺
            /* 工具类 */
            case "efficiency" -> 8;          // 效率
            case "silk_touch" -> 9;          // 精准采集
            case "fortune" -> 10;            // 时运
            /* 通用 */
            case "unbreaking" -> 11;         // 耐久
            case "mending" -> 12;            // 经验修补
            /* 护甲保护类 */
            case "protection" -> 13;         // 保护
            case "fire_protection" -> 14;    // 火焰保护
            case "blast_protection" -> 15;   // 爆炸保护
            case "projectile_protection" -> 16; // 弹射物保护
            case "thorns" -> 17;             // 荆棘
            case "respiration" -> 18;        // 水下呼吸
            case "aqua_affinity" -> 19;      // 水下速掘
            case "depth_strider" -> 20;      // 深海探索者
            case "frost_walker" -> 21;       // 冰霜行者
            case "feather_falling" -> 22;    // 摔落保护
            case "soul_speed" -> 23;         // 灵魂疾行
            case "swift_sneak" -> 24;        // 迅捷潜行
            /* 弓类 */
            case "power" -> 25;              // 力量
            case "punch" -> 26;              // 冲击
            case "flame" -> 27;              // 火矢
            case "infinity" -> 28;           // 无限
            /* 弩类 */
            case "multishot" -> 29;          // 多重射击
            case "quick_charge" -> 30;       // 快速装填
            case "piercing" -> 31;           // 穿透
            /* 三叉戟 */
            case "channeling" -> 32;         // 引雷
            case "impaling" -> 33;           // 穿刺
            case "loyalty" -> 34;            // 忠诚
            case "riptide" -> 35;            // 激流
            /* 其他 */
            case "luck_of_the_sea" -> 36;    // 海之眷顾
            case "lure" -> 37;               // 饵钓
            case "binding_curse" -> 38;      // 绑定诅咒
            case "vanishing_curse" -> 39;    // 消失诅咒
            case "density" -> 40;            // 致密
            case "breach" -> 41;             // 破甲
            case "wind_burst" -> 42;         // 风爆
            default -> 100;                  // 未知附魔放最后
        };
    }

    /**
     * 构建经验条字符串 - 新样式
     * 格式: [████████░░] 325/400
     *
     * @param current 当前经验
     * @param needed  所需经验
     * @return 经验条字符串
     */
    private String buildExpBar(int current, int needed) {
        int barLength = config.getExpBarLength();
        if (needed <= 0) needed = 1;

        double progress = (double) current / needed;
        int filledCount = (int) (progress * barLength);
        filledCount = Math.max(0, Math.min(filledCount, barLength));

        StringBuilder bar = new StringBuilder();
        bar.append("[");
        for (int i = 0; i < filledCount; i++) {
            bar.append(BAR_FILL);
        }
        for (int i = filledCount; i < barLength; i++) {
            bar.append(BAR_EMPTY);
        }
        bar.append("]");
        return bar.toString();
    }
}