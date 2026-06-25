package io.github.liyughj.xH.enchantmentLevel.EnchantmentsList;

import io.github.liyughj.xH.enchantmentLevel.EnchantmentLevelConfig;
import org.bukkit.enchantments.Enchantment;

import java.util.*;

/**
 * Minecraft 1.21.4 全部原版附魔清单
 * 用于后续原版附魔属性修改（最高等级、互斥关系、适用物品等）
 *
 * 共 42 个附魔，按原版注册顺序排列
 *
 * 最高等级规则：
 * 1. 优先读取 enchantmentLevel.yml 中 max-levels 配置
 * 2. 未配置则使用 Info.vanillaMaxLevel（原版默认等级）
 * 3. 无论如何不超过硬锁上限 HARD_MAX_LEVEL（10级）
 */
public final class VanillaEnchantments {

    /** 硬锁：所有附魔实际最高等级上限（与 EnchantmentLevelManager 保持一致） */
    public static final int HARD_MAX_LEVEL = 10;

    /** 配置文件引用（插件启用时通过 init() 注入） */
    private static EnchantmentLevelConfig config;

    /**
     * 初始化配置引用（插件 onEnable 时调用一次）
     *
     * @param levelConfig 附魔升级配置实例
     */
    public static void init(EnchantmentLevelConfig levelConfig) {
        config = levelConfig;
    }

    /**
     * 附魔类别
     */
    public enum Category {
        /** 武器伤害 */
        WEAPON_DAMAGE,
        /** 武器功能 */
        WEAPON_UTILITY,
        /** 工具 */
        TOOL,
        /** 护甲保护 */
        ARMOR_PROTECTION,
        /** 护甲功能 */
        ARMOR_UTILITY,
        /** 护甲移动 */
        ARMOR_MOVEMENT,
        /** 弓 */
        BOW,
        /** 弩 */
        CROSSBOW,
        /** 三叉戟 */
        TRIDENT,
        /** 钓鱼竿 */
        FISHING,
        /** 锤（Mace） */
        MACE,
        /** 通用 */
        GENERAL,
        /** 诅咒 */
        CURSE
    }

    /**
     * 附魔信息记录
     *
     * @param key            Bukkit key（小写，如 "sharpness"）
     * @param chineseName    中文名
     * @param maxLevel       原版默认最高等级（仅作参考，实际等级由配置+硬锁决定）
     * @param treasure       是否为宝藏附魔
     * @param category       附魔类别
     * @param description    效果描述
     */
    public record Info(String key, String chineseName, int maxLevel, boolean treasure,
                       Category category, String description) {

        /**
         * 获取实际生效的最高等级
         * 优先级：配置文件 max-levels > 原版默认 maxLevel > 硬锁上限 10
         *
         * @return 实际最高等级（1~10）
         */
        public int getEffectiveMaxLevel() {
            /* 从配置文件读取（key 自动转为大写匹配） */
            if (config != null) {
                return Math.min(config.getMaxLevel(key, maxLevel), HARD_MAX_LEVEL);
            }
            /* 未初始化配置则使用原版默认值，受硬锁限制 */
            return Math.min(maxLevel, HARD_MAX_LEVEL);
        }
    }

    /* ================================================================
     *  1. 武器伤害类 (Weapon Damage)
     * ================================================================ */

    /** 锋利 - 增加近战伤害 */
    public static final Info SHARPNESS = new Info(
        "sharpness", "锋利", 5, false, Category.WEAPON_DAMAGE,
        "每级增加伤害：I=+1, II=+1.5, III=+2, IV=+2.5, V=+3"
    );

    /** 亡灵杀手 - 对亡灵生物造成额外伤害 */
    public static final Info SMITE = new Info(
        "smite", "亡灵杀手", 5, false, Category.WEAPON_DAMAGE,
        "每级对亡灵生物+2.5伤害（骷髅、僵尸、凋零骷髅、凋零等）"
    );

    /** 节肢杀手 - 对节肢生物造成额外伤害和缓慢效果 */
    public static final Info BANE_OF_ARTHROPODS = new Info(
        "bane_of_arthropods", "节肢杀手", 5, false, Category.WEAPON_DAMAGE,
        "每级对节肢生物+2.5伤害（蜘蛛、蠹虫、末影螨等）并附加缓慢效果"
    );

    /* ================================================================
     *  2. 武器功能类 (Weapon Utility)
     * ================================================================ */

    /** 横扫之刃 - 增加横扫攻击伤害 */
    public static final Info SWEEPING_EDGE = new Info(
        "sweeping_edge", "横扫之刃", 3, false, Category.WEAPON_UTILITY,
        "增加横扫攻击伤害：I=50%, II=66.7%, III=75%"
    );

    /** 火焰附加 - 点燃目标 */
    public static final Info FIRE_ASPECT = new Info(
        "fire_aspect", "火焰附加", 2, false, Category.WEAPON_UTILITY,
        "攻击使目标燃烧：I=3秒, II=7秒"
    );

    /** 击退 - 增加击退距离 */
    public static final Info KNOCKBACK = new Info(
        "knockback", "击退", 2, false, Category.WEAPON_UTILITY,
        "增加击退距离"
    );

    /** 抢夺 - 增加生物掉落物 */
    public static final Info LOOTING = new Info(
        "looting", "抢夺", 3, false, Category.WEAPON_UTILITY,
        "每级+1最大掉落数量，增加稀有掉落概率"
    );

    /* ================================================================
     *  3. 工具类 (Tool)
     * ================================================================ */

    /** 效率 - 加快挖掘速度 */
    public static final Info EFFICIENCY = new Info(
        "efficiency", "效率", 5, false, Category.TOOL,
        "加快挖掘速度，V级可使大部分方块瞬间挖掘"
    );

    /** 精准采集 - 采集方块本身 */
    public static final Info SILK_TOUCH = new Info(
        "silk_touch", "精准采集", 1, false, Category.TOOL,
        "挖掘方块时掉落方块本身（如石头而非圆石、矿石而非掉落物）"
    );

    /** 时运 - 增加方块掉落物 */
    public static final Info FORTUNE = new Info(
        "fortune", "时运", 3, false, Category.TOOL,
        "增加矿石、农作物等方块的掉落数量"
    );

    /* ================================================================
     *  4. 通用类 (General - 所有物品)
     * ================================================================ */

    /** 耐久 - 减少物品耐久消耗 */
    public static final Info UNBREAKING = new Info(
        "unbreaking", "耐久", 3, false, Category.GENERAL,
        "降低耐久消耗概率：I=50%, II=66.7%, III=80%"
    );

    /** 经验修补 - 用经验修复物品 */
    public static final Info MENDING = new Info(
        "mending", "经验修补", 1, true, Category.GENERAL,
        "获得的经验球用于修复物品耐久（宝藏附魔）"
    );

    /* ================================================================
     *  5. 护甲保护类 (Armor Protection)
     * ================================================================ */

    /** 保护 - 减少大多数伤害 */
    public static final Info PROTECTION = new Info(
        "protection", "保护", 4, false, Category.ARMOR_PROTECTION,
        "每级减少4%伤害，最大减少64%（全套保护IV）"
    );

    /** 火焰保护 - 减少火焰伤害 */
    public static final Info FIRE_PROTECTION = new Info(
        "fire_protection", "火焰保护", 4, false, Category.ARMOR_PROTECTION,
        "减少火焰伤害和燃烧时间"
    );

    /** 爆炸保护 - 减少爆炸伤害 */
    public static final Info BLAST_PROTECTION = new Info(
        "blast_protection", "爆炸保护", 4, false, Category.ARMOR_PROTECTION,
        "减少爆炸伤害和击退效果"
    );

    /** 弹射物保护 - 减少弹射物伤害 */
    public static final Info PROJECTILE_PROTECTION = new Info(
        "projectile_protection", "弹射物保护", 4, false, Category.ARMOR_PROTECTION,
        "减少箭矢、火球等弹射物伤害"
    );

    /* ================================================================
     *  6. 护甲功能类 (Armor Utility)
     * ================================================================ */

    /** 荆棘 - 反弹伤害 */
    public static final Info THORNS = new Info(
        "thorns", "荆棘", 3, false, Category.ARMOR_UTILITY,
        "受到攻击时有概率反弹伤害：I=15%, II=30%, III=45%"
    );

    /** 水下呼吸 - 延长水下呼吸时间 */
    public static final Info RESPIRATION = new Info(
        "respiration", "水下呼吸", 3, false, Category.ARMOR_UTILITY,
        "每级+15秒水下呼吸时间（仅头盔）"
    );

    /** 水下速掘 - 水下挖掘速度正常 */
    public static final Info AQUA_AFFINITY = new Info(
        "aqua_affinity", "水下速掘", 1, false, Category.ARMOR_UTILITY,
        "消除水下挖掘速度惩罚（仅头盔）"
    );

    /* ================================================================
     *  7. 护甲移动类 (Armor Movement)
     * ================================================================ */

    /** 深海探索者 - 加快水中移动速度 */
    public static final Info DEPTH_STRIDER = new Info(
        "depth_strider", "深海探索者", 3, false, Category.ARMOR_MOVEMENT,
        "每级减少33%水下减速（仅靴子）"
    );

    /** 冰霜行者 - 水面行走 */
    public static final Info FROST_WALKER = new Info(
        "frost_walker", "冰霜行者", 2, false, Category.ARMOR_MOVEMENT,
        "在水面上生成霜冰（仅靴子，宝藏附魔）"
    );

    /** 摔落保护 - 减少摔落伤害 */
    public static final Info FEATHER_FALLING = new Info(
        "feather_falling", "摔落保护", 4, false, Category.ARMOR_MOVEMENT,
        "每级减少12%摔落伤害（仅靴子）"
    );

    /** 灵魂疾行 - 在灵魂沙上加速 */
    public static final Info SOUL_SPEED = new Info(
        "soul_speed", "灵魂疾行", 3, false, Category.ARMOR_MOVEMENT,
        "在灵魂沙/灵魂土上加速移动（仅靴子，宝藏附魔）"
    );

    /** 迅捷潜行 - 加快潜行速度 */
    public static final Info SWIFT_SNEAK = new Info(
        "swift_sneak", "迅捷潜行", 3, false, Category.ARMOR_MOVEMENT,
        "增加潜行移动速度（仅护腿，宝藏附魔）"
    );

    /* ================================================================
     *  8. 弓类 (Bow)
     * ================================================================ */

    /** 力量 - 增加箭矢伤害 */
    public static final Info POWER = new Info(
        "power", "力量", 5, false, Category.BOW,
        "每级增加25%箭矢伤害"
    );

    /** 冲击 - 增加箭矢击退 */
    public static final Info PUNCH = new Info(
        "punch", "冲击", 2, false, Category.BOW,
        "增加箭矢击退距离"
    );

    /** 火矢 - 点燃箭矢 */
    public static final Info FLAME = new Info(
        "flame", "火矢", 1, false, Category.BOW,
        "射出的箭矢附带火焰"
    );

    /** 无限 - 箭矢无限（1支即可） */
    public static final Info INFINITY = new Info(
        "infinity", "无限", 1, false, Category.BOW,
        "只需1支箭即可无限射击（与经验修补互斥）"
    );

    /* ================================================================
     *  9. 弩类 (Crossbow)
     * ================================================================ */

    /** 多重射击 - 一次射出3支箭 */
    public static final Info MULTISHOT = new Info(
        "multishot", "多重射击", 1, false, Category.CROSSBOW,
        "一次射出3支箭（与穿透互斥）"
    );

    /** 快速装填 - 加快弩装填速度 */
    public static final Info QUICK_CHARGE = new Info(
        "quick_charge", "快速装填", 3, false, Category.CROSSBOW,
        "加快弩装填速度"
    );

    /** 穿透 - 箭矢穿透实体 */
    public static final Info PIERCING = new Info(
        "piercing", "穿透", 4, false, Category.CROSSBOW,
        "箭矢穿透多个实体（与多重射击互斥）"
    );

    /* ================================================================
     *  10. 三叉戟类 (Trident)
     * ================================================================ */

    /** 引雷 - 雷雨天击中生物召唤闪电 */
    public static final Info CHANNELING = new Info(
        "channeling", "引雷", 1, false, Category.TRIDENT,
        "雷雨天击中生物时召唤闪电（与激流互斥）"
    );

    /** 穿刺 - 对水生生物额外伤害 */
    public static final Info IMPALING = new Info(
        "impaling", "穿刺", 5, false, Category.TRIDENT,
        "对水生生物造成额外伤害"
    );

    /** 忠诚 - 掷出后自动返回 */
    public static final Info LOYALTY = new Info(
        "loyalty", "忠诚", 3, false, Category.TRIDENT,
        "掷出后返回，等级越高返回越快（与激流互斥）"
    );

    /** 激流 - 水中/雨中掷出伴随飞行 */
    public static final Info RIPTIDE = new Info(
        "riptide", "激流", 3, false, Category.TRIDENT,
        "水中或雨中掷出时伴随玩家飞行（与引雷、忠诚互斥）"
    );

    /* ================================================================
     *  11. 钓鱼竿类 (Fishing Rod)
     * ================================================================ */

    /** 海之眷顾 - 增加钓到宝藏概率 */
    public static final Info LUCK_OF_THE_SEA = new Info(
        "luck_of_the_sea", "海之眷顾", 3, false, Category.FISHING,
        "降低钓到垃圾的概率，增加钓到宝藏的概率"
    );

    /** 饵钓 - 减少钓鱼等待时间 */
    public static final Info LURE = new Info(
        "lure", "饵钓", 3, false, Category.FISHING,
        "减少钓鱼等待时间，每级减少5秒"
    );

    /* ================================================================
     *  12. 诅咒类 (Curse - 宝藏附魔，不可通过附魔台获取)
     * ================================================================ */

    /** 绑定诅咒 - 穿戴后无法取下 */
    public static final Info BINDING_CURSE = new Info(
        "binding_curse", "绑定诅咒", 1, true, Category.CURSE,
        "物品穿戴后无法取下，只有死亡或物品损坏时才掉落"
    );

    /** 消失诅咒 - 死亡后物品消失 */
    public static final Info VANISHING_CURSE = new Info(
        "vanishing_curse", "消失诅咒", 1, true, Category.CURSE,
        "玩家死亡时物品消失而非掉落"
    );

    /* ================================================================
     *  13. 锤类 (Mace - 1.21+ 新增)
     * ================================================================ */

    /** 致密 - 根据坠落高度增加伤害 */
    public static final Info DENSITY = new Info(
        "density", "致密", 5, false, Category.MACE,
        "根据坠落高度增加伤害，每级每格+1伤害（与破甲、锋利、亡灵杀手、节肢杀手互斥）"
    );

    /** 破甲 - 降低目标护甲效果 */
    public static final Info BREACH = new Info(
        "breach", "破甲", 4, false, Category.MACE,
        "降低目标护甲效果：I=15%, II=30%, III=45%, IV=60%"
    );

    /** 风爆 - 攻击后弹起 */
    public static final Info WIND_BURST = new Info(
        "wind_burst", "风爆", 3, false, Category.MACE,
        "攻击后弹飞到空中，等级越高弹起高度越大"
    );

    /* ================================================================
     *  14. 矛类 (Spear - 1.21.5+)
     * ================================================================ */

    /** 突进 - 刺击命中后获得短暂移速加成 */
    public static final Info LUNGE = new Info(
        "lunge", "突进", 3, false, Category.WEAPON_UTILITY,
        "刺击命中后获得短暂移速加成"
    );

    /* ================================================================
     *  15. 汇总列表（按注册顺序，与 EnchantmentLevelManager 一致）
     * ================================================================ */

    /** 所有附魔信息列表（按注册顺序） */
    public static final List<Info> ALL = Collections.unmodifiableList(Arrays.asList(
        /* 武器伤害 */
        SHARPNESS, SMITE, BANE_OF_ARTHROPODS,
        /* 武器功能 */
        SWEEPING_EDGE, FIRE_ASPECT, KNOCKBACK, LOOTING,
        /* 工具 */
        EFFICIENCY, SILK_TOUCH, FORTUNE,
        /* 通用 */
        UNBREAKING, MENDING,
        /* 护甲保护 */
        PROTECTION, FIRE_PROTECTION, BLAST_PROTECTION, PROJECTILE_PROTECTION,
        /* 护甲功能 */
        THORNS, RESPIRATION, AQUA_AFFINITY,
        /* 护甲移动 */
        DEPTH_STRIDER, FROST_WALKER, FEATHER_FALLING, SOUL_SPEED, SWIFT_SNEAK,
        /* 弓 */
        POWER, PUNCH, FLAME, INFINITY,
        /* 弩 */
        MULTISHOT, QUICK_CHARGE, PIERCING,
        /* 三叉戟 */
        CHANNELING, IMPALING, LOYALTY, RIPTIDE,
        /* 钓鱼竿 */
        LUCK_OF_THE_SEA, LURE,
        /* 诅咒 */
        BINDING_CURSE, VANISHING_CURSE,
        /* 锤 */
        DENSITY, BREACH, WIND_BURST,
        /* 矛 */
        LUNGE
    ));

    /** Bukkit key -> Info 快速查找 */
    private static final Map<String, Info> BY_KEY = new HashMap<>();
    static {
        for (Info info : ALL) {
            BY_KEY.put(info.key(), info);
        }
    }

    /* ================================================================
     *  15. 工具方法
     * ================================================================ */

    /**
     * 根据 Bukkit Enchantment 对象获取附魔信息
     *
     * @param enchant Bukkit 附魔
     * @return 附魔信息，未找到返回 null
     */
    public static Info fromEnchantment(Enchantment enchant) {
        return BY_KEY.get(enchant.getKey().getKey());
    }

    /**
     * 根据 Bukkit key（小写）获取附魔信息
     *
     * @param key Bukkit key，如 "sharpness"
     * @return 附魔信息，未找到返回 null
     */
    public static Info fromKey(String key) {
        return BY_KEY.get(key.toLowerCase());
    }

    /**
     * 获取所有附魔的数量
     *
     * @return 42
     */
    public static int count() {
        return ALL.size();
    }

    /* 禁止实例化 */
    private VanillaEnchantments() {
        throw new UnsupportedOperationException("工具类不可实例化");
    }
}
