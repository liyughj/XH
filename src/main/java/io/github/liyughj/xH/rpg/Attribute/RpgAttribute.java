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

    /** 闪避率（百分比），完全闪避本次攻击 */
    DODGE("dodge", "闪避", ValueType.PERCENT, 0.0, 100.0, 0.0, Category.ORIGINAL_RPG),
    /** 命中率（百分比），对方闪避后我方再次判定，roll ≤ 命中 → 无视闪避 */
    HIT("hit", "命中", ValueType.PERCENT, 0.0, 100.0, 0.0, Category.ORIGINAL_RPG),

    /** 致盲概率（百分比），攻击时触发致盲 */
    BLIND_CHANCE("blind_chance", "致盲概率", ValueType.PERCENT, 0.0, 100.0, 0.0, Category.ORIGINAL_RPG),
    /** 致盲效能（百分比），降低目标命中率%，最高100% */
    BLIND_EFFICIENCY("blind_efficiency", "致盲效能", ValueType.PERCENT, 0.0, 100.0, 0.0, Category.ORIGINAL_RPG),
    /** 致盲时间（tick），致盲效果持续时间，20tick=1秒 */
    BLIND_TICKS("blind_ticks", "致盲时间", ValueType.FLAT, 0.0, Double.MAX_VALUE, 0.0, Category.ORIGINAL_RPG),

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

    /* ==================== 枪械 ==================== */

    /** 枪械伤害（绝对值），枪械基础伤害，与 gun_bonus 相乘 */
    GUN_DAMAGE("gun_damage", "枪械伤害", ValueType.FLAT, 0.0, Double.MAX_VALUE, 0.0, Category.GUN),
    /** 枪械加成（百分比），乘 gun_damage */
    GUN_BONUS("gun_bonus", "枪械加成", ValueType.PERCENT, 0.0, Double.MAX_VALUE, 0.0, Category.GUN),
    /** 射速（绝对值），RPM每分钟发数，如 600=10发/秒，射击间隔=60000/RPM ms */
    GUN_RPM("gun_rpm", "射速", ValueType.FLAT, 1.0, Double.MAX_VALUE, 600.0, Category.GUN),

    /* --- 散布系统 --- */
    /** 最小扩散（绝对值），最精准状态下的扩散角（度） */
    GUN_SPREAD_MIN("gun_spread_min", "最小扩散", ValueType.FLAT, 0.0, 360.0, 0.0, Category.GUN),
    /** 最大扩散（绝对值），扩散累加的硬上限（度） */
    GUN_SPREAD_MAX("gun_spread_max", "最大扩散", ValueType.FLAT, 0.0, 360.0, 5.0, Category.GUN),
    /** 扩散增长（绝对值），每次射击后扩散角增加量（度） */
    GUN_SPREAD_GROWTH("gun_spread_growth", "扩散增长", ValueType.FLAT, 0.0, 360.0, 0.5, Category.GUN),
    /** 扩散恢复（绝对值），停火后每秒回落量（度/秒） */
    GUN_SPREAD_RECOVERY("gun_spread_recovery", "扩散恢复", ValueType.FLAT, 0.0, 360.0, 3.0, Category.GUN),
    /** 恢复延迟（tick），停火后等待X tick才开始恢复，20tick=1秒 */
    GUN_SPREAD_RESET_DELAY("gun_spread_reset_delay", "恢复延迟", ValueType.FLAT, 0.0, Double.MAX_VALUE, 0.0, Category.GUN),
    /** 首发精度（百分比），100=直接压到最小扩散（上限100%） */
    GUN_SPREAD_FIRSTSHOT_BONUS("gun_spread_firstshot_bonus", "首发精度", ValueType.PERCENT, 0.0, 100.0, 100.0, Category.GUN),
    /** 首发弹数（绝对值），恢复后前N发享受精度加成，0=不启用 */
    GUN_SPREAD_FIRSTSHOT_COUNT("gun_spread_firstshot_count", "首发弹数", ValueType.FLAT, 0.0, Double.MAX_VALUE, 1.0, Category.GUN),
    /** 射击象限（绝对值），bitmask编码，bit0=Q1 bit1=Q2 bit2=Q3 bit3=Q4，15=全象限 */
    GUN_SPREAD_QUADRANTS("gun_spread_quadrants", "射击象限", ValueType.FLAT, 1.0, 15.0, 15.0, Category.GUN),
    /** 偏向X轴（百分比），100=偏水平，0=偏垂直，50=均匀 */
    GUN_SPREAD_BIAS_X("gun_spread_bias_x", "偏向X轴", ValueType.PERCENT, 0.0, 100.0, 50.0, Category.GUN),
    /** 移动惩罚（百分比），移动时扩散倍率增量，如50=移动时扩散×1.5 */
    GUN_SPREAD_MOVE("gun_spread_move", "移动惩罚", ValueType.PERCENT, 0.0, Double.MAX_VALUE, 0.0, Category.GUN),
    /** 跳跃惩罚（百分比），跳跃/空中扩散倍率增量 */
    GUN_SPREAD_JUMP("gun_spread_jump", "跳跃惩罚", ValueType.PERCENT, 0.0, Double.MAX_VALUE, 80.0, Category.GUN),
    /** 蹲下修正（百分比），蹲下时扩散缩减比例（上限100%） */
    GUN_SPREAD_CROUCH("gun_spread_crouch", "蹲下修正", ValueType.PERCENT, 0.0, 100.0, 30.0, Category.GUN),
    /** 开镜修正（百分比），开镜时扩散缩减比例（上限100%） */
    GUN_SPREAD_ADS("gun_spread_ads", "开镜修正", ValueType.PERCENT, 0.0, 100.0, 50.0, Category.GUN),
    /** 散布模式（绝对值），0=圆形 1=水平椭圆 2=竖直椭圆 3=十字 */
    GUN_SPREAD_PATTERN("gun_spread_pattern", "散布模式", ValueType.FLAT, 0.0, 3.0, 0.0, Category.GUN),

    /* --- 后坐力系统 --- */
    /** 垂直后坐（绝对值），每发准星上跳度数（度/发） */
    GUN_RECOIL_VERTICAL("gun_recoil_vertical", "垂直后坐", ValueType.FLAT, 0.0, 45.0, 2.0, Category.GUN),
    /** 水平后坐（绝对值），每发左右随机±此值（度/发） */
    GUN_RECOIL_HORIZONTAL("gun_recoil_horizontal", "水平后坐", ValueType.FLAT, 0.0, 45.0, 0.5, Category.GUN),
    /** 后坐增长（绝对值），连射每发额外累加到垂直后坐上（度） */
    GUN_RECOIL_GROWTH("gun_recoil_growth", "后坐增长", ValueType.FLAT, 0.0, 45.0, 0.1, Category.GUN),
    /** 后坐上限（绝对值），单次连射累计上跳硬上限（度） */
    GUN_RECOIL_MAX("gun_recoil_max", "后坐上限", ValueType.FLAT, 0.0, 90.0, 15.0, Category.GUN),
    /** 后坐恢复（绝对值），停火每秒回落量（度/秒） */
    GUN_RECOIL_RECOVERY("gun_recoil_recovery", "后坐恢复", ValueType.FLAT, 0.0, 90.0, 8.0, Category.GUN),
    /** 后坐恢复延迟（tick），停火后等X tick开始恢复，20tick=1秒 */
    GUN_RECOIL_RESET_DELAY("gun_recoil_reset_delay", "后坐恢复延迟", ValueType.FLAT, 0.0, Double.MAX_VALUE, 2.0, Category.GUN),
    /** 后坐首发减免（百分比），首发后坐力乘(1-bonus/100)，100=首发无后坐（上限100%） */
    GUN_RECOIL_FIRSTSHOT_BONUS("gun_recoil_firstshot_bonus", "后坐首发减免", ValueType.PERCENT, 0.0, 100.0, 100.0, Category.GUN),
    /** 后坐首发弹数（绝对值），恢复后前N发享受减免 */
    GUN_RECOIL_FIRSTSHOT_COUNT("gun_recoil_firstshot_count", "后坐首发弹数", ValueType.FLAT, 0.0, Double.MAX_VALUE, 1.0, Category.GUN),
    /** 水平偏向（百分比），50=均匀，0=全左，100=全右 */
    GUN_RECOIL_HORIZONTAL_BIAS("gun_recoil_horizontal_bias", "水平偏向", ValueType.PERCENT, 0.0, 100.0, 50.0, Category.GUN),
    /** 后坐蹲下修正（百分比），蹲下时后坐缩减比例（上限100%） */
    GUN_RECOIL_CROUCH("gun_recoil_crouch", "后坐蹲下修正", ValueType.PERCENT, 0.0, 100.0, 30.0, Category.GUN),
    /** 后坐开镜修正（百分比），开镜时后坐缩减比例（上限100%）（预留） */
    GUN_RECOIL_ADS("gun_recoil_ads", "后坐开镜修正", ValueType.PERCENT, 0.0, 100.0, 50.0, Category.GUN),
    /** 后坐模式（绝对值），0=直线 1=锯齿 2=S线 3=倒T */
    GUN_RECOIL_PATTERN("gun_recoil_pattern", "后坐模式", ValueType.FLAT, 0.0, 3.0, 0.0, Category.GUN),
    /** 视角震动（绝对值），额外随机抖动量，影响瞄准方向偏移（度） */
    GUN_RECOIL_VIEW_KICK("gun_recoil_view_kick", "视角震动", ValueType.FLAT, 0.0, 45.0, 0.0, Category.GUN),

    /* --- 爆头/部位伤害 --- */
    /** 爆头概率（百分比），命中头部时触发爆头的概率（上限100%） */
    GUN_HEADSHOT_CHANCE("gun_headshot_chance", "爆头概率", ValueType.PERCENT, 0.0, 100.0, 100.0, Category.GUN),
    /** 爆头倍率（百分比），触发爆头时伤害倍率 */
    GUN_HEADSHOT_MULT("gun_headshot_multiplier", "爆头倍率", ValueType.PERCENT, 0.0, Double.MAX_VALUE, 200.0, Category.GUN),
    /** 上肢概率（百分比），命中躯体时触发额外伤害的概率（上限100%） */
    GUN_UPPER_CHANCE("gun_upper_chance", "上肢概率", ValueType.PERCENT, 0.0, 100.0, 100.0, Category.GUN),
    /** 上肢倍率（百分比），触发时伤害倍率 */
    GUN_UPPER_MULT("gun_upper_multiplier", "上肢倍率", ValueType.PERCENT, 0.0, Double.MAX_VALUE, 100.0, Category.GUN),
    /** 下身概率（百分比），命中腹部时触发额外伤害的概率（上限100%） */
    GUN_LOWER_CHANCE("gun_lower_chance", "下身概率", ValueType.PERCENT, 0.0, 100.0, 100.0, Category.GUN),
    /** 下身倍率（百分比），触发时伤害倍率 */
    GUN_LOWER_MULT("gun_lower_multiplier", "下身倍率", ValueType.PERCENT, 0.0, Double.MAX_VALUE, 100.0, Category.GUN),
    /** 腿部概率（百分比），命中腿部时触发额外伤害的概率（上限100%） */
    GUN_LEG_CHANCE("gun_leg_chance", "腿部概率", ValueType.PERCENT, 0.0, 100.0, 100.0, Category.GUN),
    /** 腿部倍率（百分比），触发时伤害倍率 */
    GUN_LEG_MULT("gun_leg_multiplier", "腿部倍率", ValueType.PERCENT, 0.0, Double.MAX_VALUE, 70.0, Category.GUN),
    /** 爆头阈值（百分比），弹落点≥眼高×此%→头部（上限100%） */
    GUN_HEADSHOT_THRESHOLD("gun_headshot_threshold", "爆头阈值", ValueType.PERCENT, 0.0, 100.0, 85.0, Category.GUN),
    /** 上身阈值（百分比），弹落点≥眼高×此%→上肢/躯干（上限100%） */
    GUN_BODY_THRESHOLD("gun_body_threshold", "上身阈值", ValueType.PERCENT, 0.0, 100.0, 50.0, Category.GUN),
    /** 腿部阈值（百分比），弹落点<眼高×此%→腿部（上限100%） */
    GUN_LEG_THRESHOLD("gun_leg_threshold", "腿部阈值", ValueType.PERCENT, 0.0, 100.0, 20.0, Category.GUN),

    /* --- 射击模式 --- */
    /** 默认模式（绝对值），0=安全 1=单发 2=连发 3=全自动 */
    GUN_FIRE_DEFAULT_MODE("gun_fire_default_mode", "默认模式", ValueType.FLAT, 0.0, 3.0, 1.0, Category.GUN),
    /** 可用模式（绝对值），bitmask编码，bit0=安全 bit1=单发 bit2=连发 bit3=全自动 */
    GUN_FIRE_AVAILABLE_MODES("gun_fire_available_modes", "可用模式", ValueType.FLAT, 0.0, 15.0, 15.0, Category.GUN),
    /** 连发弹数（绝对值），每次点击连续发射 N 发 */
    GUN_BURST_COUNT("gun_burst_count", "连发弹数", ValueType.FLAT, 0.0, Double.MAX_VALUE, 3.0, Category.GUN),
    /** 连发间隔（绝对值），连发内每发间隔（ms） */
    GUN_BURST_INTERVAL_MS("gun_burst_interval_ms", "连发间隔", ValueType.FLAT, 0.0, Double.MAX_VALUE, 80.0, Category.GUN),
    /** 自动首发延迟（绝对值），全自动首次射击延迟（ms），0=立即 */
    GUN_AUTO_TRIGGER_DELAY_MS("gun_auto_trigger_delay_ms", "自动首发延迟", ValueType.FLAT, 0.0, Double.MAX_VALUE, 0.0, Category.GUN),

    /* --- 过热系统 --- */
    /** 单发热量（绝对值），每发射击增加的热量值 */
    GUN_HEAT_PER_SHOT("gun_heat_per_shot", "单发热量", ValueType.FLAT, 0.0, Double.MAX_VALUE, 5.0, Category.GUN),
    /** 冷却速率（绝对值），每秒降低的热量值（/秒） */
    GUN_HEAT_COOL_RATE("gun_heat_cool_rate", "冷却速率", ValueType.FLAT, 0.0, Double.MAX_VALUE, 10.0, Category.GUN),
    /** 过热惩罚（tick），过热后强制冷却时间，20tick=1秒 */
    GUN_HEAT_OVERHEAT_PENALTY_TICKS("gun_heat_overheat_penalty_ticks", "过热惩罚", ValueType.FLAT, 0.0, Double.MAX_VALUE, 40.0, Category.GUN),
    /** 过热散布因子（百分比），热量每1%增加的散布% */
    GUN_HEAT_SPREAD_FACTOR("gun_heat_spread_factor", "过热散布因子", ValueType.PERCENT, 0.0, 500.0, 200.0, Category.GUN),
    /** 过热后坐因子（百分比），热量每1%增加的后坐% */
    GUN_HEAT_RECOIL_FACTOR("gun_heat_recoil_factor", "过热后坐因子", ValueType.PERCENT, 0.0, 500.0, 150.0, Category.GUN),
    /** 过热故障因子（百分比），热量每1%增加的故障率% */
    GUN_HEAT_MALFUNCTION_FACTOR("gun_heat_malfunction_factor", "过热故障因子", ValueType.PERCENT, 0.0, 500.0, 300.0, Category.GUN),
    /** 开镜冷却加成（百分比），开镜时冷却速率的倍率 */
    GUN_HEAT_ADS_COOL_BONUS("gun_heat_ads_cool_bonus", "开镜冷却加成", ValueType.PERCENT, 0.0, 500.0, 150.0, Category.GUN),
    /** 冒烟阈值（百分比），热量%≥此值时枪口冒烟粒子（上限100%） */
    GUN_HEAT_SMOKE_THRESHOLD("gun_heat_smoke_threshold", "冒烟阈值", ValueType.PERCENT, 0.0, 100.0, 60.0, Category.GUN),
    /** 最大热量（绝对值），枪械可积累的热量上限 */
    GUN_HEAT_MAX("gun_heat_max", "最大热量", ValueType.FLAT, 1.0, Double.MAX_VALUE, 100.0, Category.GUN),
    /** 过热触发（百分比），热量%≥此值触发过热（上限100%） */
    GUN_HEAT_OVERHEAT_TRIGGER("gun_heat_overheat_trigger", "过热触发", ValueType.PERCENT, 0.0, 100.0, 80.0, Category.GUN),
    /** 故障触发（百分比），热量%≥此值才可能触发故障（上限100%） */
    GUN_HEAT_MALFUNC_TRIGGER("gun_heat_malfunc_trigger", "故障触发", ValueType.PERCENT, 0.0, 100.0, 50.0, Category.GUN),
    /** 热量耐久损耗上限（绝对值），热量100%时每发额外消耗的耐久值 */
    GUN_HEAT_DURA_LOSS_MAX("gun_heat_dura_loss_max", "热量耐久损耗上限", ValueType.FLAT, 0.0, Double.MAX_VALUE, 0.0, Category.GUN),

    /* --- 故障系统 --- */
    /** 基础故障率（百分比），每发子弹的基准概率（上限100%） */
    GUN_MALFUNC_BASE_CHANCE("gun_malfunc_base_chance", "基础故障率", ValueType.PERCENT, 0.0, 100.0, 0.0, Category.GUN),
    /** 卡壳排除（tick），左键排障耗时，20tick=1秒 */
    GUN_MALFUNC_JAM_CLEAR_TICKS("gun_malfunc_jam_clear_ticks", "卡壳排除", ValueType.FLAT, 0.0, Double.MAX_VALUE, 30.0, Category.GUN),
    /** 炸膛伤害（绝对值），灾难性故障对持枪者造成的伤害 */
    GUN_MALFUNC_CATA_DAMAGE("gun_malfunc_cata_damage", "炸膛伤害", ValueType.FLAT, 0.0, Double.MAX_VALUE, 20.0, Category.GUN),
    /** 炸膛耐久损失（绝对值），灾难性故障直接扣除的耐久值 */
    GUN_MALFUNC_CATA_DURA_LOSS("gun_malfunc_cata_dura_loss", "炸膛耐久损失", ValueType.FLAT, 0.0, Double.MAX_VALUE, 50.0, Category.GUN),
    /** 故障冷却（tick），一次故障后再次检测的最小间隔，20tick=1秒 */
    GUN_MALFUNC_COOLDOWN_TICKS("gun_malfunc_cooldown_ticks", "故障冷却", ValueType.FLAT, 0.0, Double.MAX_VALUE, 20.0, Category.GUN),
    /** 故障类型权重（绝对值），编码: jam*1000000+misfire*1000+cata */
    GUN_MALFUNC_TYPE_WEIGHTS("gun_malfunc_type_weights", "故障类型权重", ValueType.FLAT, 0.0, 1000.0, 8020010.0, Category.GUN),

    /* --- 弹夹系统 --- */
    /** 弹夹容量（绝对值），一个弹夹的最大装填数 */
    GUN_MAG_CAPACITY("gun_mag_capacity", "弹夹容量", ValueType.FLAT, 0.0, Double.MAX_VALUE, 30.0, Category.GUN),
    /** 换弹时间（tick），战术换弹耗时，20tick=1秒 */
    GUN_RELOAD_TIME_TICKS("gun_reload_ticks", "换弹时间", ValueType.FLAT, 0.0, Double.MAX_VALUE, 40.0, Category.GUN),
    /** 空仓换弹（tick），弹夹为零时的换弹耗时，20tick=1秒 */
    GUN_RELOAD_EMPTY_TIME_TICKS("gun_reload_empty_ticks", "空仓换弹", ValueType.FLAT, 0.0, Double.MAX_VALUE, 50.0, Category.GUN),
    /** 分段换弹（tick），换弹可在此时间点后中断并完成，20tick=1秒 */
    GUN_RELOAD_STAGED("gun_reload_staged", "分段换弹", ValueType.FLAT, 0.0, Double.MAX_VALUE, 30.0, Category.GUN),
    /** 自动换弹（绝对值），1=启用 0=禁用 */
    GUN_AUTO_RELOAD("gun_auto_reload", "自动换弹", ValueType.FLAT, 0.0, 1.0, 0.0, Category.GUN),
    /** 换弹可中断（绝对值），1=允许切换物品中断换弹 0=不允许 */
    GUN_RELOAD_INTERRUPTIBLE("gun_reload_interruptible", "换弹可中断", ValueType.FLAT, 0.0, 1.0, 0.0, Category.GUN),

    /* --- 枪膛系统 --- */
    /** 枪膛开关（绝对值），1=启用（有1发膛内弹） 0=禁用 */
    GUN_CHAMBER_ENABLED("gun_chamber_enabled", "枪膛开关", ValueType.FLAT, 0.0, 1.0, 0.0, Category.GUN),
    /** 战术换弹加成（百分比），非空仓换弹时间缩减比例（上限100%） */
    GUN_CHAMBER_TACTICAL_RELOAD_BONUS("gun_chamber_tactical_reload_bonus", "战术换弹加成", ValueType.PERCENT, 0.0, 100.0, 40.0, Category.GUN),
    /** 拉栓时间（tick），空仓换弹后额外拉栓耗时，20tick=1秒 */
    GUN_CHAMBER_BOLT_TIME_TICKS("gun_chamber_bolt_ticks", "拉栓时间", ValueType.FLAT, 0.0, Double.MAX_VALUE, 15.0, Category.GUN),
    /** 自动拉栓（绝对值），1=换弹后自动拉栓 0=需手动 */
    GUN_CHAMBER_AUTO_BOLT("gun_chamber_auto_bolt", "自动拉栓", ValueType.FLAT, 0.0, 1.0, 1.0, Category.GUN),

    /* --- 耐久系统（通用） --- */
    /** 最大耐久（绝对值），物品可承受的耐久上限 */
    ITEM_DURA_MAX("item_dura_max", "最大耐久", ValueType.FLAT, 0.0, Double.MAX_VALUE, 100.0, Category.ORIGINAL_RPG),
    /** 耐久消耗（绝对值），每次使用消耗的耐久值 */
    ITEM_DURA_LOSS_PER_USE("item_dura_loss_per_use", "耐久消耗", ValueType.FLAT, 0.0, Double.MAX_VALUE, 1.0, Category.ORIGINAL_RPG),
    /** 耐久消耗系数（百分比），>100%多消耗 <100%少消耗，默认100%=1.0 */
    ITEM_DURA_CONSUMPTION_COEFFICIENT("item_dura_consumption_coefficient", "耐久消耗系数", ValueType.PERCENT, 0.0, 1000.0, 100.0, Category.ORIGINAL_RPG),
    /** 耐久阀（百分比），当前耐久%低于此值开始增加负面概率（上限100%） */
    ITEM_DURA_THRESHOLD("item_dura_threshold", "耐久阀", ValueType.PERCENT, 0.0, 100.0, 30.0, Category.ORIGINAL_RPG),
    /** 耐久阀惩罚因子（百分比），每低于阀值1%增加的概率% */
    ITEM_DURA_THRESHOLD_PENALTY_FACTOR("item_dura_threshold_penalty_factor", "耐久阀惩罚因子", ValueType.PERCENT, 0.0, 1000.0, 200.0, Category.ORIGINAL_RPG),
    /** 修复系数因子（百分比），每次维修增加的消耗系数% */
    ITEM_DURA_REPAIR_COEFFICIENT_FACTOR("item_dura_repair_coefficient_factor", "修复系数因子", ValueType.PERCENT, 0.0, 1000.0, 0.0, Category.ORIGINAL_RPG),
    /** 耐久警告阈值（百分比），低于此值时ActionBar警告（上限100%） */
    ITEM_DURA_WARNING_THRESHOLD("item_dura_warning_threshold", "耐久警告阈值", ValueType.PERCENT, 0.0, 100.0, 20.0, Category.ORIGINAL_RPG),
    /** 耐久散布惩罚（百分比），每少1%耐久增加的散布% */
    ITEM_DURA_SPREAD_PENALTY("item_dura_spread_penalty", "耐久散布惩罚", ValueType.PERCENT, 0.0, 500.0, 100.0, Category.ORIGINAL_RPG),
    /** 耐久后坐惩罚（百分比），每少1%耐久增加的后坐% */
    ITEM_DURA_RECOIL_PENALTY("item_dura_recoil_penalty", "耐久后坐惩罚", ValueType.PERCENT, 0.0, 500.0, 100.0, Category.ORIGINAL_RPG),
    /** 破损散布惩罚（百分比），破损时固定散布扩大百分比 */
    ITEM_DURA_BROKEN_SPREAD_PENALTY("item_dura_broken_spread_penalty", "破损散布惩罚", ValueType.PERCENT, 0.0, 500.0, 500.0, Category.ORIGINAL_RPG),
    /** 破损可修复（绝对值），1=可修复 0=永久报废 */
    ITEM_DURA_BROKEN_REPAIRABLE("item_dura_broken_repairable", "破损可修复", ValueType.FLAT, 0.0, 1.0, 1.0, Category.ORIGINAL_RPG),
    /** 修理成本（绝对值），修复所需材料数量 */
    ITEM_DURA_REPAIR_COST("item_dura_repair_cost", "修理成本", ValueType.FLAT, 0.0, 64.0, 8.0, Category.ORIGINAL_RPG),
    /** 修理材料（绝对值），修复所需材料的Minecraft ID编码 */
    ITEM_DURA_REPAIR_MATERIAL("item_dura_repair_material", "修理材料", ValueType.FLAT, 0.0, 12.0, 0.0, Category.ORIGINAL_RPG),

    /* --- 穿透系统 --- */
    /** 穿透层数（绝对值），可穿透的最大实体数 */
    GUN_PENETRATION_COUNT("gun_penetration_count", "穿透层数", ValueType.FLAT, 0.0, Double.MAX_VALUE, 0.0, Category.GUN),
    /** 穿透衰减（百分比），每穿透一层降低的伤害百分比（上限100%） */
    GUN_PENETRATION_FALLOFF("gun_penetration_falloff", "穿透衰减", ValueType.PERCENT, 0.0, 100.0, 30.0, Category.GUN),
    /** 衰减模式（绝对值），0=固定% 1=平方递减 2=指数递减 */
    GUN_PENETRATION_FALLOFF_MODE("gun_penetration_falloff_mode", "衰减模式", ValueType.FLAT, 0.0, 2.0, 0.0, Category.GUN),
    /** 穿透最低伤害（百分比），穿透至极限时最低保留%（上限100%） */
    GUN_PENETRATION_MIN_DAMAGE("gun_penetration_min_damage", "穿透最低伤害", ValueType.PERCENT, 0.0, 100.0, 10.0, Category.GUN),
    /** 方块击穿（绝对值），1=可以穿透方块 0=不穿透方块 */
    GUN_PENETRATION_BLOCK_BREAK("gun_penetration_block_break", "方块击穿", ValueType.FLAT, 0.0, 1.0, 0.0, Category.GUN),
    /** 穿透粒子（绝对值），1=启用 0=禁用 */
    GUN_PENETRATION_PARTICLE("gun_penetration_particle", "穿透粒子", ValueType.FLAT, 0.0, 1.0, 0.0, Category.GUN),
    /** 穿透音效（绝对值），1=启用 0=禁用 */
    GUN_PENETRATION_SOUND("gun_penetration_sound", "穿透音效", ValueType.FLAT, 0.0, 1.0, 0.0, Category.GUN),

    /* --- 弹道系统 --- */
    /** 子弹速度（绝对值），箭矢初速（m/s） */
    GUN_BULLET_SPEED("gun_bullet_speed", "子弹速度", ValueType.FLAT, 0.0, Double.MAX_VALUE, 60.0, Category.GUN),
    /** 子弹重力（绝对值），0=直线(hitscan) 1=弱重力 2=标准重力 */
    GUN_BULLET_GRAVITY("gun_bullet_gravity", "子弹重力", ValueType.FLAT, 0.0, 2.0, 0.0, Category.GUN),
    /** 子弹生存时间（tick），超时自动移除，20tick=1秒 */
    GUN_BULLET_LIFETIME_TICKS("gun_bullet_lifetime_ticks", "子弹生存时间", ValueType.FLAT, 0.0, Double.MAX_VALUE, 100.0, Category.GUN),
    /** 空气阻力（百分比），每tick速度乘以(1-drag/100)（上限100%） */
    GUN_BULLET_DRAG("gun_bullet_drag", "空气阻力", ValueType.PERCENT, 0.0, 100.0, 0.5, Category.GUN),
    /** 伤害衰减起始（绝对值），小于此距离无衰减（格） */
    GUN_BULLET_DAMAGE_FALLOFF_START("gun_bullet_damage_falloff_start", "伤害衰减起始", ValueType.FLAT, 0.0, Double.MAX_VALUE, 20.0, Category.GUN),
    /** 伤害衰减终止（绝对值），大于此距离衰减至最低（格） */
    GUN_BULLET_DAMAGE_FALLOFF_END("gun_bullet_damage_falloff_end", "伤害衰减终止", ValueType.FLAT, 0.0, Double.MAX_VALUE, 80.0, Category.GUN),
    /** 最低伤害保留（百分比），距离衰减的保底伤害比例（上限100%） */
    GUN_BULLET_DAMAGE_MIN_PERCENT("gun_bullet_damage_min_percent", "最低伤害保留", ValueType.PERCENT, 0.0, 100.0, 50.0, Category.GUN),
    /** 射线模式（绝对值），1=hitscan射线检测 0=Projectile实体 */
    GUN_BULLET_HITSCAN("gun_bullet_hitscan", "射线模式", ValueType.FLAT, 0.0, 1.0, 0.0, Category.GUN),
    /** 弹道尾迹（绝对值），编码: 0=off 1=smoke 2=flame 3=crit 4=end_rod */
    GUN_BULLET_TRAIL("gun_bullet_trail", "弹道尾迹", ValueType.FLAT, 0.0, 4.0, 0.0, Category.GUN),
    /** 粒子间隔（tick），每N tick产生一个尾迹粒子，20tick=1秒 */
    GUN_BULLET_TRAIL_INTERVAL("gun_bullet_trail_interval", "粒子间隔", ValueType.FLAT, 0.0, Double.MAX_VALUE, 3.0, Category.GUN),

    /* --- 特殊武器类型 --- */
    /* 霰弹枪 */
    /** 弹丸数量（绝对值），每次射击发射的弹丸数 */
    GUN_SHOTGUN_PELLET_COUNT("gun_shotgun_pellet_count", "弹丸数量", ValueType.FLAT, 1.0, Double.MAX_VALUE, 8.0, Category.GUN),
    /** 霰弹散布模式（绝对值），0=均匀圆 1=中心偏重 2=环形 3=水平线 */
    GUN_SHOTGUN_SPREAD_MODE("gun_shotgun_spread_mode", "霰弹散布模式", ValueType.FLAT, 0.0, 3.0, 0.0, Category.GUN),
    /** 霰弹伤害系数（百分比），基础伤害×此系数再÷弹丸数=每粒弹丸伤害 */
    GUN_SHOTGUN_DAMAGE_DIVIDER("gun_shotgun_damage_divider", "霰弹伤害系数", ValueType.PERCENT, 0.0, 500.0, 100.0, Category.GUN),
    /** 弹丸速度（绝对值），霰弹丸的飞行速度（m/s） */
    GUN_SHOTGUN_PELLET_SPEED("gun_shotgun_pellet_speed", "弹丸速度", ValueType.FLAT, 0.0, Double.MAX_VALUE, 50.0, Category.GUN),

    /* 弩 */
    /** 弩伤害（绝对值），弩的基础伤害值 */
    GUN_CROSSBOW_DAMAGE("gun_crossbow_damage", "弩伤害", ValueType.FLAT, 0.0, Double.MAX_VALUE, 40.0, Category.GUN),
    /** 弩装填（tick），弩的装填时间，20tick=1秒 */
    GUN_CROSSBOW_RELOAD_TICKS("gun_crossbow_reload_ticks", "弩装填", ValueType.FLAT, 0.0, Double.MAX_VALUE, 60.0, Category.GUN),
    /** 弩重力（绝对值），0=直线 1=弱 2=标准 */
    GUN_CROSSBOW_GRAVITY("gun_crossbow_gravity", "弩重力", ValueType.FLAT, 0.0, 2.0, 2.0, Category.GUN),

    /* ── 通用流血 ── */
    /** 流血概率（百分比），命中后触发流血DoT的概率（上限100%） */
    BLEED_CHANCE("bleed_chance", "流血概率", ValueType.PERCENT, 0.0, 100.0, 0.0, Category.ORIGINAL_RPG),
    /** 流血伤害（绝对值），每tick扣除的HP值（HP/tick） */
    BLEED_DAMAGE("bleed_damage", "流血伤害", ValueType.FLAT, 0.0, Double.MAX_VALUE, 0.0, Category.ORIGINAL_RPG),
    /** 流血持续（tick），DoT持续时间，20tick=1秒 */
    BLEED_TICKS("bleed_ticks", "流血持续", ValueType.FLAT, 0.0, Double.MAX_VALUE, 0.0, Category.ORIGINAL_RPG),
    /** 弩爆头倍率（百分比），覆盖通用爆头倍率 [射线系统使用GUN_HEADSHOT_MULT] */
    GUN_CROSSBOW_HEADSHOT_MULT("gun_crossbow_headshot_multiplier", "弩爆头倍率", ValueType.PERCENT, 0.0, 1000.0, 250.0, Category.GUN),

    /* 喷火器 */
    /** 火焰伤害（绝对值），每次伤害判定的伤害值（HP/次） */
    GUN_FLAME_DAMAGE_PER_TICK("gun_flame_damage_per_tick", "火焰伤害", ValueType.FLAT, 0.0, Double.MAX_VALUE, 3.0, Category.GUN),
    /** 火焰伤害间隔（tick），伤害判定间隔，20tick=1秒 */
    GUN_FLAME_DAMAGE_INTERVAL("gun_flame_damage_interval", "火焰伤害间隔", ValueType.FLAT, 1.0, Double.MAX_VALUE, 4.0, Category.GUN),
    /** 火焰射程（绝对值），喷射最大距离（格） */
    GUN_FLAME_RANGE("gun_flame_range", "火焰射程", ValueType.FLAT, 1.0, Double.MAX_VALUE, 8.0, Category.GUN),
    /** 火焰扩散角（绝对值），火焰喷射的锥形角度（度） */
    GUN_FLAME_SPREAD_ANGLE("gun_flame_spread_angle", "火焰扩散角", ValueType.FLAT, 0.0, 90.0, 15.0, Category.GUN),
    /** 点燃持续（tick），命中后目标着火时间，20tick=1秒 */
    GUN_FLAME_IGNITE_TICKS("gun_flame_ignite_ticks", "点燃持续", ValueType.FLAT, 0.0, Double.MAX_VALUE, 40.0, Category.GUN),
    /** 燃料上限（绝对值），燃料可积累的最大值 */
    GUN_FLAME_FUEL_MAX("gun_flame_fuel_max", "燃料上限", ValueType.FLAT, 0.0, Double.MAX_VALUE, 100.0, Category.GUN),
    /** 燃料消耗（绝对值），喷射时每tick消耗（/tick） */
    GUN_FLAME_FUEL_PER_TICK("gun_flame_fuel_per_tick", "燃料消耗", ValueType.FLAT, 0.0, Double.MAX_VALUE, 2.0, Category.GUN),
    /** 燃料恢复（绝对值），不喷射时每秒恢复（/秒） */
    GUN_FLAME_FUEL_REGEN("gun_flame_fuel_regen", "燃料恢复", ValueType.FLAT, 0.0, Double.MAX_VALUE, 5.0, Category.GUN),
    /** 火焰粒子密度（绝对值），火焰粒子的生成频率 */
    GUN_FLAME_PARTICLE_DENSITY("gun_flame_particle_density", "火焰粒子密度", ValueType.FLAT, 1.0, 10.0, 3.0, Category.GUN),

    /* 榴弹发射器 */
    /** 榴弹伤害（绝对值），爆炸中心伤害值 */
    GUN_GRENADE_DAMAGE("gun_grenade_damage", "榴弹伤害", ValueType.FLAT, 0.0, Double.MAX_VALUE, 60.0, Category.GUN),
    /** 爆炸半径（绝对值），爆炸影响范围（格） */
    GUN_GRENADE_RADIUS("gun_grenade_radius", "爆炸半径", ValueType.FLAT, 0.0, Double.MAX_VALUE, 4.0, Category.GUN),
    /** 引信时间（tick），发射后延迟爆炸，20tick=1秒 */
    GUN_GRENADE_FUSE_TICKS("gun_grenade_fuse_ticks", "引信时间", ValueType.FLAT, 0.0, Double.MAX_VALUE, 40.0, Category.GUN),
    /** 弹跳次数（绝对值），碰墙反弹次数 */
    GUN_GRENADE_BOUNCE("gun_grenade_bounce", "弹跳次数", ValueType.FLAT, 0.0, 10.0, 2.0, Category.GUN),
    /** 破坏方块（绝对值），1=爆炸可破坏方块 0=不破坏 */
    GUN_GRENADE_DESTROY_BLOCKS("gun_grenade_destroy_blocks", "破坏方块", ValueType.FLAT, 0.0, 1.0, 0.0, Category.GUN),
    /** 榴弹击退（绝对值），爆炸击退强度 */
    GUN_GRENADE_KNOCKBACK("gun_grenade_knockback", "榴弹击退", ValueType.FLAT, 0.0, 5.0, 1.5, Category.GUN),
    /** 榴弹自伤（百分比），爆炸对自身的伤害比例（上限100%） */
    GUN_GRENADE_SELF_DAMAGE_FACTOR("gun_grenade_self_damage_factor", "榴弹自伤", ValueType.PERCENT, 0.0, 100.0, 50.0, Category.GUN),

    /* 火箭筒 */
    /** 火箭伤害（绝对值），爆炸中心伤害值 */
    GUN_ROCKET_DAMAGE("gun_rocket_damage", "火箭伤害", ValueType.FLAT, 0.0, Double.MAX_VALUE, 80.0, Category.GUN),
    /** 火箭半径（绝对值），爆炸影响范围（格） */
    GUN_ROCKET_RADIUS("gun_rocket_radius", "火箭半径", ValueType.FLAT, 0.0, Double.MAX_VALUE, 6.0, Category.GUN),
    /** 火箭速度（绝对值），火箭弹飞行速度（m/s）[射线系统使用GUN_BULLET_SPEED] */
    GUN_ROCKET_VELOCITY("gun_rocket_velocity", "火箭速度", ValueType.FLAT, 0.0, Double.MAX_VALUE, 30.0, Category.GUN),
    /** 追踪导弹（绝对值），1=启用追踪 0=禁用 */
    GUN_ROCKET_HOMING("gun_rocket_homing", "追踪导弹", ValueType.FLAT, 0.0, 1.0, 0.0, Category.GUN),
    /** 追踪强度（百分比），修正火箭弹朝向目标的力度 */
    GUN_ROCKET_HOMING_STRENGTH("gun_rocket_homing_strength", "追踪强度", ValueType.PERCENT, 0.0, 200.0, 50.0, Category.GUN),
    /** 追踪范围（绝对值），超过此距离不追踪（格） */
    GUN_ROCKET_HOMING_RANGE("gun_rocket_homing_range", "追踪范围", ValueType.FLAT, 0.0, Double.MAX_VALUE, 30.0, Category.GUN),
    /** 遥控引爆（绝对值），1=右击引爆当前飞行火箭弹 0=自动爆炸 */
    GUN_ROCKET_REMOTE("gun_rocket_remote", "遥控引爆", ValueType.FLAT, 0.0, 1.0, 0.0, Category.GUN),
    /** 火箭自伤（百分比），爆炸对自身的伤害比例（上限100%） */
    GUN_ROCKET_SELF_DAMAGE_FACTOR("gun_rocket_self_damage_factor", "火箭自伤", ValueType.PERCENT, 0.0, 100.0, 50.0, Category.GUN),
    /** 火箭破坏方块（绝对值），1=爆炸可破坏方块 0=不破坏 */
    GUN_ROCKET_DESTROY_BLOCKS("gun_rocket_destroy_blocks", "火箭破坏方块", ValueType.FLAT, 0.0, 1.0, 1.0, Category.GUN),

    /* 激光枪 */
    /** 激光伤害（绝对值），激光每发伤害值 */
    GUN_LASER_DAMAGE("gun_laser_damage", "激光伤害", ValueType.FLAT, 0.0, Double.MAX_VALUE, 10.0, Category.GUN),
    /** 激光射程（绝对值），激光最大距离（格） */
    GUN_LASER_RANGE("gun_laser_range", "激光射程", ValueType.FLAT, 1.0, Double.MAX_VALUE, 40.0, Category.GUN),
    /** 持续激光（绝对值），1=按住持续 0=单脉冲 */
    GUN_LASER_CONTINUOUS("gun_laser_continuous", "持续激光", ValueType.FLAT, 0.0, 1.0, 0.0, Category.GUN),
    /** 激光能量上限（绝对值），能量可积累的最大值 */
    GUN_LASER_ENERGY_MAX("gun_laser_energy_max", "激光能量上限", ValueType.FLAT, 0.0, Double.MAX_VALUE, 100.0, Category.GUN),
    /** 激光能量消耗（绝对值），每发消耗的能量值（/发） */
    GUN_LASER_ENERGY_PER_SHOT("gun_laser_energy_per_shot", "激光能量消耗", ValueType.FLAT, 0.0, Double.MAX_VALUE, 10.0, Category.GUN),
    /** 激光能量恢复（绝对值），每秒恢复的能量值（/秒） */
    GUN_LASER_ENERGY_REGEN("gun_laser_energy_regen", "激光能量恢复", ValueType.FLAT, 0.0, Double.MAX_VALUE, 8.0, Category.GUN),
    /** 激光颜色（绝对值），RGB整数编码，如 0xFF0000=红色 */
    GUN_LASER_COLOR("gun_laser_color", "激光颜色", ValueType.FLAT, 0.0, 16777215.0, 16711680.0, Category.GUN),
    /** 激光粗细（绝对值），粒子束宽度 */
    GUN_LASER_THICKNESS("gun_laser_thickness", "激光粗细", ValueType.FLAT, 1.0, 10.0, 1.0, Category.GUN),
    /** 激光穿透（绝对值），1=可穿透实体（不受gun_penetration影响） 0=正常 */
    GUN_LASER_PIERCE("gun_laser_pierce", "激光穿透", ValueType.FLAT, 0.0, 1.0, 1.0, Category.GUN),

    /* ── 人体工学 ── */
    /** 切枪耗时（tick），掏出武器耗时，20tick=1秒 */
    GUN_EQUIP_TIME_TICKS("gun_equip_ticks", "切枪耗时", ValueType.FLAT, 0.0, Double.MAX_VALUE, 10.0, Category.GUN),
    /** 收枪耗时（tick），切换为其他物品的延迟，20tick=1秒 */
    GUN_HOLSTER_TIME_TICKS("gun_holster_ticks", "收枪耗时", ValueType.FLAT, 0.0, Double.MAX_VALUE, 5.0, Category.GUN),
    /** 疾跑开火延迟（tick），冲刺后多久才能射击，20tick=1秒 */
    GUN_SPRINT_TO_FIRE_TICKS("gun_sprint_to_fire_ticks", "疾跑开火延迟", ValueType.FLAT, 0.0, Double.MAX_VALUE, 6.0, Category.GUN),
    /** 开镜渐入（tick），0=瞬间进入，20tick=1秒 */
    GUN_ADS_IN_TIME_TICKS("gun_ads_in_ticks", "开镜渐入", ValueType.FLAT, 0.0, 60.0, 5.0, Category.GUN),
    /** 关镜渐出（tick），0=瞬间退出，20tick=1秒 */
    GUN_ADS_OUT_TIME_TICKS("gun_ads_out_ticks", "关镜渐出", ValueType.FLAT, 0.0, 60.0, 3.0, Category.GUN),
    /** 切枪速度（百分比），切换到此枪的速度倍率，100=正常 50=慢一倍 */
    GUN_WEAPON_SWAP_SPEED("gun_weapon_swap_speed", "切枪速度", ValueType.PERCENT, 10.0, 200.0, 100.0, Category.GUN),

    /* ── 机动性 ── */
    /** 持枪移速（百分比），100=正常 80=慢20% */
    GUN_MOVE_SPEED("gun_move_speed", "持枪移速", ValueType.PERCENT, 10.0, 150.0, 100.0, Category.GUN),
    /** 持枪疾跑（百分比），100=正常 */
    GUN_SPRINT_SPEED("gun_sprint_speed", "持枪疾跑", ValueType.PERCENT, 10.0, 150.0, 100.0, Category.GUN),
    /** 开镜移速（百分比），开镜时移速倍率 */
    GUN_ADS_MOVE_SPEED("gun_ads_move_speed", "开镜移速", ValueType.PERCENT, 5.0, 100.0, 50.0, Category.GUN),
    /** 持枪跳跃（百分比），跳跃高度修正，100=正常 */
    GUN_JUMP_HEIGHT("gun_jump_height", "持枪跳跃", ValueType.PERCENT, 10.0, 200.0, 100.0, Category.GUN),
    /** 允许疾跑（绝对值），1=允许持枪疾跑 0=禁止 */
    GUN_CAN_SPRINT("gun_can_sprint", "允许疾跑", ValueType.FLAT, 0.0, 1.0, 1.0, Category.GUN),

    /* ── 开镜高级属性 ── */
    /** 开镜晃动（绝对值），值越大开镜时子弹散布越大，0=无晃动 */
    GUN_ADS_SWAY_AMOUNT("gun_ads_sway_amount", "开镜晃动", ValueType.FLAT, 0.0, 10.0, 1.0, Category.GUN),
    /** 呼吸值上限（绝对值），开镜时屏息消耗至此值×阈值%停止，100=默认 */
    GUN_ADS_BREATH_MAX("gun_ads_breath_max", "呼吸值上限", ValueType.FLAT, 10.0, 500.0, 100.0, Category.GUN),
    /** 屏息消耗（绝对值），开镜屏息时每tick扣减呼吸值（/tick） */
    GUN_ADS_BREATH_DRAIN("gun_ads_breath_drain", "屏息消耗", ValueType.FLAT, 0.0, 10.0, 0.3, Category.GUN),
    /** 呼吸恢复（绝对值），不屏息时每tick恢复（/tick） */
    GUN_ADS_BREATH_REGEN("gun_ads_breath_regen", "呼吸恢复", ValueType.FLAT, 0.0, 10.0, 0.8, Category.GUN),
    /** 屏息阈值（百分比），呼吸值低于此%×max时自动停止屏息（上限100%） */
    GUN_ADS_BREATH_THRESHOLD("gun_ads_breath_threshold", "屏息阈值", ValueType.PERCENT, 0.0, 100.0, 30.0, Category.GUN),
    /** 开镜夜视（绝对值），1=开镜时给 night_vision 效果 0=无 */
    GUN_ADS_NIGHT_VISION("gun_ads_night_vision", "开镜夜视", ValueType.FLAT, 0.0, 1.0, 0.0, Category.GUN),
    /** 瞄具类型（绝对值），0=无/机瞄 1=红点 2=全息 3=四倍 4=高倍 5=热成像(Glowing) */
    GUN_ADS_SCOPE_TYPE("gun_ads_scope_type", "瞄具类型", ValueType.FLAT, 0.0, 5.0, 0.0, Category.GUN),

    /* ── 命中特效 ── */
    /** 命中减速率（百分比），命中触发减速的概率（上限100%） */
    GUN_HIT_SLOW_CHANCE("gun_hit_slow_chance", "命中减速率", ValueType.PERCENT, 0.0, 100.0, 0.0, Category.GUN),
    /** 减速幅度（百分比），减目标移速的百分比（上限100%） */
    GUN_HIT_SLOW_AMOUNT("gun_hit_slow_amount", "减速幅度", ValueType.PERCENT, 0.0, 100.0, 30.0, Category.GUN),
    /** 减速持续（tick），减速效果时长，20tick=1秒 */
    GUN_HIT_SLOW_TICKS("gun_hit_slow_ticks", "减速持续", ValueType.FLAT, 0.0, Double.MAX_VALUE, 40.0, Category.GUN),
    /** 硬直概率（百分比），命中触发硬直的概率（上限100%） */
    GUN_HIT_STAGGER_CHANCE("gun_hit_stagger_chance", "硬直概率", ValueType.PERCENT, 0.0, 100.0, 0.0, Category.GUN),
    /** 硬直力度（绝对值），硬直击退力度，0=无击退 */
    GUN_HIT_STAGGER_STRENGTH("gun_hit_stagger_strength", "硬直力度", ValueType.FLAT, 0.0, 5.0, 1.0, Category.GUN),
    /** 致盲概率（百分比），命中给目标 blindness 效果的概率（上限100%） */
    GUN_HIT_BLIND_CHANCE("gun_hit_blind_chance", "致盲概率", ValueType.PERCENT, 0.0, 100.0, 0.0, Category.GUN),
    /** 致盲持续（tick），blindness 效果时长，20tick=1秒 */
    GUN_HIT_BLIND_TICKS("gun_hit_blind_ticks", "致盲持续", ValueType.FLAT, 0.0, Double.MAX_VALUE, 60.0, Category.GUN),

    /* ── 击杀连锁 ── */
    /** 击杀触发概率（百分比），击杀时roll判定是否激活连锁buff（上限100%） */
    GUN_ON_KILL_TRIGGER_CHANCE("gun_on_kill_trigger_chance", "击杀触发概率", ValueType.PERCENT, 0.0, 100.0, 100.0, Category.GUN),
    /** 击杀换弹加速（百分比），击杀后换弹速度加成，持续 N tick */
    GUN_ON_KILL_RELOAD_SPEED("gun_on_kill_reload_speed", "击杀换弹加速", ValueType.PERCENT, 0.0, 500.0, 0.0, Category.GUN),
    /** 击杀伤害加成（百分比），击杀后伤害提升比例 */
    GUN_ON_KILL_DAMAGE_BONUS("gun_on_kill_damage_bonus", "击杀伤害加成", ValueType.PERCENT, 0.0, 500.0, 0.0, Category.GUN),
    /** 击杀回复（绝对值），击杀时回复的生命值 */
    GUN_ON_KILL_HEAL("gun_on_kill_heal", "击杀回复", ValueType.FLAT, 0.0, Double.MAX_VALUE, 0.0, Category.GUN),
    /** 击杀buff持续（tick），击杀连锁buff持续时间，20tick=1秒 */
    GUN_ON_KILL_BUFF_TICKS("gun_on_kill_buff_ticks", "击杀buff持续", ValueType.FLAT, 0.0, Double.MAX_VALUE, 100.0, Category.GUN),

    /* ── 弹道高级特性 ── */
    /** 跳弹概率（百分比），入射角≤阈值时触发（上限100%） */
    GUN_BULLET_RICOCHET_CHANCE("gun_bullet_ricochet_chance", "跳弹概率", ValueType.PERCENT, 0.0, 100.0, 0.0, Category.GUN),
    /** 跳弹角度（绝对值），弹道与面法线夹角≤此值才可能跳弹（度） */
    GUN_BULLET_RICOCHET_ANGLE("gun_bullet_ricochet_angle", "跳弹角度", ValueType.FLAT, 0.0, 90.0, 30.0, Category.GUN),
    /** 水中弹速（百分比），水中子弹飞行速度系数，100=正常水速 */
    GUN_BULLET_WATER_SPEED("gun_bullet_water_speed", "水中弹速", ValueType.PERCENT, 1.0, 100.0, 40.0, Category.GUN),
    /** 玻璃穿透（绝对值），1=穿透玻璃类方块不消失 0=击碎停止 */
    GUN_BULLET_GLASS_PIERCE("gun_bullet_glass_pierce", "玻璃穿透", ValueType.FLAT, 0.0, 1.0, 0.0, Category.GUN),

    /* ── 视觉/音效 ── */
    /** 枪口火焰（绝对值），强度1-5，0=关闭 [未实现] */
    GUN_MUZZLE_FLASH_INTENSITY("gun_muzzle_flash_intensity", "枪口火焰", ValueType.FLAT, 0.0, 5.0, 1.0, Category.GUN),
    /** 焰色（绝对值），RGB整数编码，如 0xFF6600=橙焰 [未实现] */
    GUN_MUZZLE_FLASH_COLOR("gun_muzzle_flash_color", "焰色", ValueType.FLAT, 0.0, 16777215.0, 16744448.0, Category.GUN),
    /** 抛壳（绝对值），1=射击时抛弹壳 0=关 [未实现] */
    GUN_SHELL_EJECT("gun_shell_eject", "抛壳", ValueType.FLAT, 0.0, 1.0, 1.0, Category.GUN),
    /** 弹壳材质（绝对值），Material 名称编码，如 GOLD_NUGGET/IRON_NUGGET [未实现] */
    GUN_SHELL_MATERIAL("gun_shell_material", "弹壳材质", ValueType.FLAT, 0.0, 10.0, 0.0, Category.GUN),
    /** 命中标记（绝对值），0=默认(红) 1=十字 2=圆圈 3=菱形 */
    GUN_HIT_MARKER_TYPE("gun_hit_marker_type", "命中标记", ValueType.FLAT, 0.0, 3.0, 0.0, Category.GUN),
    /** 击杀标记（绝对值），0=默认 1=特殊击杀音效 2=击杀粒子 */
    GUN_HIT_MARKER_KILL("gun_hit_marker_kill", "击杀标记", ValueType.FLAT, 0.0, 2.0, 0.0, Category.GUN),
    /* ── 压制系统 ── */
    /** 压制范围（绝对值），AOE 检测半径（格） */
    GUN_SUPPRESS_RADIUS("gun_suppress_radius", "压制范围", ValueType.FLAT, 0.0, 50.0, 10.0, Category.GUN),
    /** 压制强度（百分比），被压制者散布/后坐增加比例 */
    GUN_SUPPRESS_AMOUNT("gun_suppress_amount", "压制强度", ValueType.PERCENT, 0.0, 500.0, 50.0, Category.GUN),
    /** 压制持续（tick），压制效果时长，20tick=1秒 */
    GUN_SUPPRESS_DURATION_TICKS("gun_suppress_duration_ticks", "压制持续", ValueType.FLAT, 0.0, Double.MAX_VALUE, 80.0, Category.GUN),
    /* ── 耐久补充 ── */
    /** 修理所需材料数量 */
    GUN_REPAIR_MATERIALS("gun_repair_materials", "修理所需材料", ValueType.FLAT, 0.0, 100.0, 1.0, Category.GUN),
    /* ── 配件槽位 ── */
    /** 配件槽位（绝对值），bitmask编码，bit0=muzzle bit1=optic bit2=grip bit3=mag bit4=stock bit5=laser bit6=trigger */
    GUN_ATTACHMENT_SLOTS("gun_attachment_slots", "配件槽位", ValueType.FLAT, 0.0, 127.0, 127.0, Category.GUN),

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
