package io.github.liyughj.xH.rpg.Attribute;

/**
 * RPG 属性枚举 —— 统一定义所有可用属性及其元数据。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>每个枚举值对应一种属性，包含 key / 显示名 / 数值类型 / 边界</li>
 *   <li>Category 分类便于配置和 UI 分组展示</li>
 *   <li>新增属性只需在此处加一行枚举，无需改其他代码</li>
 * </ul>
 * <p>
 * 数值类型：
 * <ul>
 *   <li>PERCENT：以百分比存储（如 50.0 = 50%），计算时 /100</li>
 *   <li>FLAT：以绝对值存储（如 5.0 = 5 点生命）</li>
 * </ul>
 * <p>
 * 区间支持：属性值可为单个值或区间（min~max），每次攻击时随机取值。
 */
public enum RpgAttribute {

    /* ==================== 已实现 ==================== */

    /** 近战攻击固定加成（绝对值），仅对近战生效（剑/斧/锤/三叉戟近战），与 melee_bonus 相乘 */
    MELEE_DAMAGE("melee_damage", "近战伤害", ValueType.FLAT, 0.0, Double.MAX_VALUE, 0.0, Category.ORIGINAL_RPG),
    /** 近战攻击百分比加成，乘 melee_damage */
    MELEE_BONUS("melee_bonus", "近战加成", ValueType.PERCENT, 0.0, Double.MAX_VALUE, 0.0, Category.ORIGINAL_RPG),

    /** 远程攻击固定加成（绝对值），仅对射弹生效（弓/弩/三叉戟投掷），与 projectile_bonus 相乘 */
    PROJECTILE_DAMAGE("projectile_damage", "射弹伤害", ValueType.FLAT, 0.0, Double.MAX_VALUE, 0.0, Category.ORIGINAL_RPG),
    /** 远程攻击百分比加成，乘 projectile_damage */
    PROJECTILE_BONUS("projectile_bonus", "射弹加成", ValueType.PERCENT, 0.0, Double.MAX_VALUE, 0.0, Category.ORIGINAL_RPG),

    /** 通用伤害固定加成（绝对值），同时加到近战和射弹，与 damage_bonus 相乘 */
    DAMAGE("damage", "伤害", ValueType.FLAT, 0.0, Double.MAX_VALUE, 0.0, Category.ORIGINAL_RPG),
    /** 通用伤害百分比加成，乘 damage */
    DAMAGE_BONUS("damage_bonus", "伤害加成", ValueType.PERCENT, 0.0, Double.MAX_VALUE, 0.0, Category.ORIGINAL_RPG),

    /** 暴击率（百分比），普通攻击触发暴击的几率（上限100%） */
    CRITICAL_CHANCE("critical_chance", "暴击率", ValueType.PERCENT, 0.0, 100.0, 0.0, Category.ORIGINAL_RPG),
    /** 暴击倍率（百分比），与暴击默认值叠加，ADD模式相加或MULTIPLY模式相乘 */
    CRITICAL_MULTIPLIER("critical_multiplier", "暴击倍率", ValueType.PERCENT, 0.0, Double.MAX_VALUE, 0.0, Category.ORIGINAL_RPG),

    /** 吸血概率（百分比），偷取/固定吸血/汲取三机制共用触发概率（上限100%） */
    LIFESTEAL_CHANCE("lifesteal_chance", "吸血概率", ValueType.PERCENT, 0.0, 100.0, 0.0, Category.ORIGINAL_RPG),
    /** 吸血倍率（百分比），影响偷取效率+汲取回血倍率 */
    LIFESTEAL_MULTIPLIER("lifesteal_multiplier", "吸血倍率", ValueType.PERCENT, 0.0, Double.MAX_VALUE, 0.0, Category.ORIGINAL_RPG),
    /** 固定吸血（绝对值），独立触发，恢复固定血量 */
    LIFESTEAL_FLAT("lifesteal_flat", "固定吸血", ValueType.FLAT, 0.0, Double.MAX_VALUE, 0.0, Category.ORIGINAL_RPG),
    /** 汲取（绝对值），独立触发，造成额外伤害并按倍率回血 */
    LIFESTEAL_DRAIN("lifesteal_drain", "汲取", ValueType.FLAT, 0.0, Double.MAX_VALUE, 0.0, Category.ORIGINAL_RPG),

    /** 攻击速度（百分比），影响手持武器的攻击间隔，基值4.0（次/秒），-80%~500% */
    ATTACK_SPEED("attack_speed", "攻击速度", ValueType.PERCENT, -80.0, 500.0, 0.0, Category.ORIGINAL_RPG),

    /** 低穿（百分比），无视目标 X% 护甲减免（上限100%） */
    LOW_PENETRATION("low_penetration", "低穿", ValueType.PERCENT, 0.0, 100.0, 0.0, Category.ORIGINAL_RPG),
    /** 高穿（绝对值），忽略 X 点护甲韧性值，超额部分转为额外伤害，不受护甲韧性影响 */
    HIGH_PENETRATION("high_penetration", "高穿", ValueType.FLAT, 0.0, Double.MAX_VALUE, 0.0, Category.ORIGINAL_RPG),
    /** 穿透效能（百分比），同时增幅低穿和高穿的最终值 */
    PENETRATION_EFFICIENCY("penetration_efficiency", "穿透效能", ValueType.PERCENT, 0.0, Double.MAX_VALUE, 0.0, Category.ORIGINAL_RPG),
    /** 护甲韧性（百分比），免疫低穿（上限100%） */
    ARMOR_TOUGHNESS("armor_toughness", "护甲韧性", ValueType.PERCENT, 0.0, 100.0, 0.0, Category.ORIGINAL_RPG),

    /** 破甲概率（百分比），攻击时触发破甲 */
    ARMOR_BREAK_CHANCE("armor_break_chance", "破甲概率", ValueType.PERCENT, 0.0, 100.0, 0.0, Category.ORIGINAL_RPG),
    /** 破甲深度-浅（百分比），浅标记减少护甲比例 */
    ARMOR_BREAK_SHALLOW("armor_break_shallow_pct", "破甲浅度", ValueType.PERCENT, 0.0, 100.0, 0.0, Category.ORIGINAL_RPG),
    /** 破甲深度-中（百分比），叠加到浅之上 */
    ARMOR_BREAK_MEDIUM("armor_break_medium_pct", "破甲中度", ValueType.PERCENT, 0.0, 100.0, 0.0, Category.ORIGINAL_RPG),
    /** 破甲深度-深（百分比），叠加到中之上 */
    ARMOR_BREAK_DEEP("armor_break_deep_pct", "破甲深度", ValueType.PERCENT, 0.0, 100.0, 0.0, Category.ORIGINAL_RPG),
    /** 破甲时间（tick），浅中深共用时长，20tick=1秒 */
    ARMOR_BREAK_TICKS("armor_break_ticks", "破甲时间", ValueType.FLAT, 0.0, Double.MAX_VALUE, 0.0, Category.ORIGINAL_RPG),

    /* ==================== 未实现 ==================== */

    /** 暴击修正（预留），用于特殊效果修正暴击触发 */
    CRITICAL_CORRECTION("critical_correction", "暴击修正", ValueType.PERCENT, -500.0, 500.0, 0.0, Category.ORIGINAL_RPG),

    /** 生命加成（绝对值），直接加到玩家最大生命值上 */
    HEALTH_BONUS("health_bonus", "生命加成", ValueType.FLAT, 0.0, 1000.0, 0.0, Category.ORIGINAL_RPG),
    /** 防御力（百分比减免），降低受到伤害 */
    DEFENSE("defense", "防御", ValueType.PERCENT, 0.0, 80.0, 0.0, Category.ORIGINAL_RPG),
    /** 移动速度（百分比），影响玩家行走速度 */
    MOVEMENT_SPEED("movement_speed", "移动速度", ValueType.PERCENT, -50.0, 300.0, 0.0, Category.ORIGINAL_RPG),
    /** 生命回复（绝对值），每秒回复的生命值 */
    HEALTH_REGEN("health_regen", "生命回复", ValueType.FLAT, 0.0, 100.0, 0.0, Category.ORIGINAL_RPG),
    /** 韧性（百分比），减免被控制时间 */
    TENACITY("tenacity", "韧性", ValueType.PERCENT, 0.0, 80.0, 0.0, Category.ORIGINAL_RPG),
    /** 闪避率（百分比），完全闪避攻击的几率 */
    DODGE("dodge", "闪避", ValueType.PERCENT, 0.0, 75.0, 0.0, Category.ORIGINAL_RPG),

    /* ==================== 枪械（预留） ==================== */

    /* ==================== 魔法（预留） ==================== */

    /* ==================== 修仙（预留） ==================== */
    ;

    /** 数值类型 */
    public enum ValueType { PERCENT, FLAT }

    /** 属性分类 */
    public enum Category { ORIGINAL_RPG, GUN, MAGIC, XIUXIAN }

    private final String key;
    private final String displayName;
    private final ValueType valueType;
    private final double minValue;
    private final double maxValue;
    private final double defaultValue;
    private final Category category;

    RpgAttribute(String key, String displayName, ValueType valueType,
                 double minValue, double maxValue, double defaultValue,
                 Category category) {
        this.key = key;
        this.displayName = displayName;
        this.valueType = valueType;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.defaultValue = defaultValue;
        this.category = category;
    }

    /** PDC / 配置文件中使用的键名 */
    public String getKey() { return key; }

    /** 中文显示名 */
    public String getDisplayName() { return displayName; }

    /** 百分比型还是绝对值型 */
    public ValueType getValueType() { return valueType; }

    /** 是否为百分比型（便捷方法） */
    public boolean isPercent() { return valueType == ValueType.PERCENT; }

    /** 最小值 */
    public double getMinValue() { return minValue; }

    /** 最大值 */
    public double getMaxValue() { return maxValue; }

    /** 默认值 */
    public double getDefaultValue() { return defaultValue; }

    /** 所属分类 */
    public Category getCategory() { return category; }

    /**
     * 根据 key 查找属性，找不到返回 null
     */
    public static RpgAttribute fromKey(String key) {
        for (RpgAttribute attr : values()) {
            if (attr.key.equals(key)) return attr;
        }
        return null;
    }

    /**
     * 修正值到合法范围
     */
    public double clamp(double value) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    /**
     * 格式化显示：百分比型加 "%"，绝对值不额外处理
     */
    public String format(double value) {
        if (isPercent()) {
            return (value == (long) value ? String.valueOf((long) value) : String.format("%.1f", value)) + "%";
        }
        return value == (long) value ? String.valueOf((long) value) : String.format("%.1f", value);
    }
}
