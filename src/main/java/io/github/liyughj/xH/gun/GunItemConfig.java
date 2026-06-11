package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeRange;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 枪械物品属性配置（gun.yml）。
 *
 * <pre>
 *   enabled: true
 *   items:
 *     IRON_HOE:                    # 视为手枪
 *       gun_damage: 15~25
 *       gun_bonus: "30~50%"
 *       gun_rpm: 300
 *       gun_spread_min: 0.5
 *       ...
 *     DIAMOND_HOE:                 # 视为狙击枪
 *       gun_damage: 50~80
 *       gun_rpm: 60
 *       ...
 * </pre>
 *
 * 所有枪械属性（gun_*、gun_spread_*、gun_recoil_*）均可在此配置。
 */
public class GunItemConfig {

    private static final String CONFIG_FILE_NAME = "gun.yml";
    private static final boolean DEFAULT_ENABLED = true;

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private boolean enabled;

    /** Material → 属性区间映射 */
    private final Map<Material, Map<RpgAttribute, AttributeRange>> materialTemplates = new EnumMap<>(Material.class);

    /** Material → 口径ID (String) */
    private final Map<Material, String> caliberMap = new EnumMap<>(Material.class);
    /** Material → 武器类型ID (String: shotgun/crossbow/flamethrower/grenade_launcher/rocket_launcher/laser) */
    private final Map<Material, String> weaponTypeMap = new EnumMap<>(Material.class);
    /** Material → 默认弹种ID (String) */
    private final Map<Material, String> defaultAmmoMap = new EnumMap<>(Material.class);

    /** 全局系统开关配置 */
    private ConfigurationSection systemsSection;

    public GunItemConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE_NAME);

        if (!configFile.exists()) {
            plugin.saveResource(CONFIG_FILE_NAME, false);
            if (!configFile.exists()) {
                createDefaultConfig(configFile);
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        enabled = config.getBoolean("enabled", DEFAULT_ENABLED);

        systemsSection = config.getConfigurationSection("systems");
        if (systemsSection == null) systemsSection = config.createSection("systems");

        materialTemplates.clear();
        caliberMap.clear();
        weaponTypeMap.clear();
        defaultAmmoMap.clear();

        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String matName : itemsSection.getKeys(false)) {
                Material material = Material.getMaterial(matName);
                if (material == null) {
                    plugin.getLogger().warning(CONFIG_FILE_NAME + ": 无效 Material \"" + matName + "\"");
                    continue;
                }
                ConfigurationSection attrSection = itemsSection.getConfigurationSection(matName);
                if (attrSection == null) continue;

                // 读字符串属性
                String caliber = attrSection.getString("caliber");
                if (caliber != null && !caliber.isEmpty()) caliberMap.put(material, caliber);

                String weaponType = attrSection.getString("gun_weapon_type");
                if (weaponType != null && !weaponType.isEmpty()) weaponTypeMap.put(material, weaponType);

                String defaultAmmo = attrSection.getString("default_ammo");
                if (defaultAmmo != null && !defaultAmmo.isEmpty()) defaultAmmoMap.put(material, defaultAmmo);

                Map<RpgAttribute, AttributeRange> attrs = new LinkedHashMap<>();
                for (String attrKey : attrSection.getKeys(false)) {
                    // 跳过字符串kv
                    if (attrKey.equals("caliber") || attrKey.equals("gun_weapon_type") || attrKey.equals("default_ammo")) continue;
                    RpgAttribute attr = RpgAttribute.fromKey(attrKey);
                    if (attr == null) {
                        plugin.getLogger().warning(CONFIG_FILE_NAME + ": 未知属性 \"" + attrKey + "\" (物品 " + matName + ")");
                        continue;
                    }
                    AttributeRange range = parseConfigValue(attrSection, attrKey, attr);
                    attrs.put(attr, range);
                }
                if (!attrs.isEmpty()) {
                    materialTemplates.put(material, attrs);
                }
            }
        }

        plugin.getLogger().info("[枪械] 已加载 " + materialTemplates.size() + " 个枪械配置模板");
    }

    // --- 解析配置用 ----------
    private AttributeRange parseConfigValue(ConfigurationSection section, String key, RpgAttribute attr) {
        Object raw = section.get(key);
        if (raw instanceof String) {
            return AttributeRange.parse((String) raw, attr);
        }
        if (raw instanceof Number) {
            double v = ((Number) raw).doubleValue();
            return AttributeRange.of(attr.clamp(v));
        }
        return AttributeRange.of(attr.getDefaultValue());
    }

    // ---------- 默认配置 ~
    private void createDefaultConfig(File file) {
        FileConfiguration dc = new YamlConfiguration();
        dc.options().setHeader(Arrays.asList(
            "枪械配置（gun.yml）",
            "",
            "按 Material 定义枪械的所有属性。",
            "物品 PDC 上有值时优先使用 PDC，否则使用此配置。",
            "支持单值或区间（min~max）。",
            "",
            "========================================================================",
            "                          全 部 枪 械 属 性",
            "========================================================================",
            "",
            "--- 基础属性 ---",
            "  gun_damage             枪械伤害（绝对值）",
            "  gun_bonus              枪械加成（%）",
            "  gun_rpm                射速（每分钟发数，RPM）",
            "  caliber                口径ID（如 9mm / 5.56mm / 12gauge，字符串）",
            "  gun_weapon_type        武器类型ID（shotgun/crossbow/flamethrower/... 字符串）",
            "  default_ammo           默认弹种ID（如 fmj / hp / ap，字符串）",
            "",
            "--- 散布系统 ---",
            "  gun_spread_min           最小扩散角（度）",
            "  gun_spread_max           最大扩散角（度）",
            "  gun_spread_growth        单发扩散增长（度）",
            "  gun_spread_recovery      扩散恢复（度/秒）",
            "  gun_spread_reset_delay   恢复延迟（tick）",
            "  gun_spread_firstshot_bonus  首发精度加成（%）",
            "  gun_spread_firstshot_count  首发弹数",
            "  gun_spread_quadrants     射击象限 bitmask（1=Q1 2=Q2 4=Q3 8=Q4 15=全）",
            "  gun_spread_bias_x        X轴偏向度（%  100=平射 0=竖射 50=均匀）",
            "  gun_spread_move          移动惩罚（%）",
            "  gun_spread_jump          跳跃惩罚（%）",
            "  gun_spread_crouch        蹲下修正（%）",
            "  gun_spread_ads           开镜修正（%）",
            "  gun_spread_pattern       散布模式（0=圆 1=水平椭圆 2=竖直椭圆 3=十字）",
            "",
            "--- 后坐力系统 ---",
            "  gun_recoil_vertical       垂直后坐（度/发）",
            "  gun_recoil_horizontal     水平后坐（度/发）",
            "  gun_recoil_growth         后坐增长（度）",
            "  gun_recoil_max            后坐上限（度）",
            "  gun_recoil_recovery       后坐恢复（度/秒）",
            "  gun_recoil_reset_delay    恢复延迟（tick）",
            "  gun_recoil_firstshot_bonus  首发减免（%）",
            "  gun_recoil_firstshot_count  首发弹数",
            "  gun_recoil_horizontal_bias  水平偏向（%  0=全左 50=均匀 100=全右）",
            "  gun_recoil_crouch         蹲下修正（%）",
            "  gun_recoil_ads            开镜修正（%）",
            "  gun_recoil_pattern        后坐模式（0=直线 1=锯齿 2=S线 3=倒T）",
            "  gun_recoil_view_kick      视角震动（度）",
            "",
            "--- 爆头/部位伤害 ---",
            "  gun_headshot_chance       爆头概率（%）",
            "  gun_headshot_mult         爆头倍率（%）",
            "  gun_upper_chance          上肢概率（%）",
            "  gun_upper_mult            上肢倍率（%）",
            "  gun_lower_chance          下身概率（%）",
            "  gun_lower_mult            下身倍率（%）",
            "  gun_leg_chance            腿部概率（%）",
            "  gun_leg_mult              腿部倍率（%）",
            "  gun_headshot_threshold    爆头阈值（%）",
            "  gun_body_threshold        上身阈值（%）",
            "  gun_leg_threshold         腿部阈值（%）",
            "",
            "--- 射击模式 ---",
            "  gun_fire_default_mode      默认模式（0=安全 1=单发 2=连发 3=全自动）",
            "  gun_fire_available_modes   可用模式 bitmask（bit0=安全 bit1=单发 bit2=连发 bit3=全自动  15=全有）",
            "  gun_burst_count            连发弹数（每次点击发射 N 发）",
            "  gun_burst_interval_ms      连发间隔（ms）",
            "  gun_auto_trigger_delay_ms  全自动首发延迟（ms  0=立即）",
            "",
            "--- 过热系统 ---",
            "  gun_heat_per_shot               单发热量",
            "  gun_heat_threshold              过热阈值",
            "  gun_heat_cool_rate              冷却速率（/秒）",
            "  gun_heat_overheat_penalty_ticks 过热惩罚持续（tick）",
            "  gun_heat_spread_factor          过热散布因子（%）",
            "  gun_heat_recoil_factor          过热后坐因子（%）",
            "  gun_heat_malfunction_factor     过热故障因子（%）",
            "  gun_heat_ads_cool_bonus         开镜冷却加成（%）",
            "  gun_heat_smoke_threshold        冒烟热量阈值（%）",
            "",
            "--- 故障系统 ---",
            "  gun_malfunc_base_chance     基础故障率（%）",
            "  gun_malfunc_heat_factor     热量故障因子（%）",
            "  gun_malfunc_dura_factor     耐久故障因子（%）",
            "  gun_malfunc_jam_clear_ticks 卡壳排除时间（tick）",
            "  gun_malfunc_cata_damage     炸膛伤害",
            "  gun_malfunc_cata_dura_loss  炸膛耐久损失",
            "  gun_malfunc_cooldown_ticks  故障冷却（tick）",
            "",
            "--- 弹夹系统 ---",
            "  gun_mag_capacity            弹夹容量",
            "  gun_reload_time_ticks       换弹时间（tick）",
            "  gun_reload_empty_time_ticks 空仓换弹时间（tick）",
            "  gun_reload_staged           分段换弹（tick）",
            "  gun_auto_reload             自动换弹（1=启用）",
            "  gun_reload_interruptible    换弹可中断（1=允许）",
            "",
            "--- 枪膛系统 ---",
            "  gun_chamber_enabled                 枪膛开关（1=启用）",
            "  gun_chamber_tactical_reload_bonus   战术换弹加成（%）",
            "  gun_chamber_bolt_time_ticks         拉栓时间（tick）",
            "  gun_chamber_auto_bolt               自动拉栓（1=启用）",
            "",
            "--- 耐久度系统 ---",
            "  gun_dura_max                      耐久上限",
            "  gun_dura_loss_per_shot            单发耐久损耗",
            "  gun_dura_spread_penalty           耐久散布惩罚（%）",
            "  gun_dura_recoil_penalty           耐久后坐惩罚（%）",
            "  gun_dura_malfunc_penalty          耐久故障惩罚（%）",
            "  gun_dura_warning_threshold        耐久警告阈值（%）",
            "  gun_dura_broken_spread_penalty    破损散布惩罚（%）",
            "  gun_dura_broken_repairable        破损可修复（1=可修复）",
            "",
            "--- 穿透系统 ---",
            "  gun_penetration_count          穿透层数",
            "  gun_penetration_falloff        穿透衰减（%）",
            "  gun_penetration_falloff_mode   衰减模式（0=固定 1=平方 2=指数）",
            "  gun_penetration_min_damage     穿透最低伤害（%）",
            "  gun_penetration_block_break    方块击穿（1=可击穿）",
            "",
            "--- 弹道系统 ---",
            "  gun_bullet_speed                  子弹速度",
            "  gun_bullet_gravity                重力等级（0=直线 1=弱 2=标准）",
            "  gun_bullet_lifetime_ticks         生存时间（tick）",
            "  gun_bullet_drag                   空气阻力（%）",
            "  gun_bullet_damage_falloff_start   伤害衰减起始距离",
            "  gun_bullet_damage_falloff_end     伤害衰减终止距离",
            "  gun_bullet_damage_min_percent     最低伤害保留（%）",
            "  gun_bullet_hitscan                Hitscan（1=射线 0=Projectile）",
            "  gun_bullet_trail_interval         粒子间隔（tick）",
            "",
            "--- 特殊武器：霰弹枪 ---",
            "  gun_shotgun_pellet_count      弹丸数量",
            "  gun_shotgun_spread_mode       散布模式（0=圆 1=中心 2=环形 3=水平线）",
            "  gun_shotgun_damage_divider     伤害系数（%）",
            "  gun_shotgun_pellet_speed       弹丸速度",
            "",
            "--- 特殊武器：弩 ---",
            "  gun_crossbow_damage          弩伤害",
            "  gun_crossbow_reload_ticks    弩装填（tick）",
            "  gun_crossbow_gravity         弩重力等级",
            "  gun_crossbow_bleed_chance    流血概率（%）",
            "  gun_crossbow_bleed_damage    流血伤害",
            "  gun_crossbow_bleed_ticks     流血持续（tick）",
            "  gun_crossbow_headshot_mult   弩爆头倍率（%）",
            "",
            "--- 特殊武器：喷火器 ---",
            "  gun_flame_damage_per_tick    火焰伤害",
            "  gun_flame_damage_interval    伤害间隔（tick）",
            "  gun_flame_range              喷射距离",
            "  gun_flame_spread_angle       扩散角度",
            "  gun_flame_ignite_ticks       点燃持续（tick）",
            "  gun_flame_fuel_max           燃料上限",
            "  gun_flame_fuel_per_tick      燃料消耗/tick",
            "  gun_flame_fuel_regen         燃料恢复/秒",
            "  gun_flame_particle_density   粒子密度",
            "",
            "--- 特殊武器：榴弹发射器 ---",
            "  gun_grenade_damage              榴弹伤害",
            "  gun_grenade_radius              爆炸半径",
            "  gun_grenade_fuse_ticks          引信时间（tick）",
            "  gun_grenade_bounce              弹跳次数",
            "  gun_grenade_destroy_blocks      破坏方块（1=破坏）",
            "  gun_grenade_knockback           爆炸击退",
            "  gun_grenade_self_damage_factor  自伤系数（%）",
            "",
            "--- 特殊武器：火箭筒 ---",
            "  gun_rocket_damage               火箭伤害",
            "  gun_rocket_radius               爆炸半径",
            "  gun_rocket_velocity             飞行速度",
            "  gun_rocket_homing               追踪导弹（1=启用）",
            "  gun_rocket_homing_strength      追踪强度（%）",
            "  gun_rocket_homing_range         追踪范围",
            "  gun_rocket_remote               遥控引爆（1=启用）",
            "  gun_rocket_self_damage_factor   自伤系数（%）",
            "  gun_rocket_destroy_blocks       破坏方块（1=破坏）",
            "",
            "--- 特殊武器：激光枪 ---",
            "  gun_laser_damage             激光伤害",
            "  gun_laser_range              激光射程",
            "  gun_laser_continuous         持续模式（1=按住持续）",
            "  gun_laser_energy_max         能量上限",
            "  gun_laser_energy_per_shot    能量消耗/发",
            "  gun_laser_energy_regen       能量恢复/秒",
            "  gun_laser_color              RGB颜色（如 0xFF0000=红）",
            "  gun_laser_thickness          激光粗细",
            "  gun_laser_pierce             穿透实体（1=可穿透）",
            "",
            "========================================================================",
            "                          属 性 扩 展 链",
            "========================================================================",
            "",
            "  读取优先级（高→低）：",
            "    1. 物品 PDC（指令/铁砧赋予）",
            "    2. GunAttributeProvider（配件/符文/附魔模块）",
            "    3. 本文件 gun.yml 材质模板",
            "    4. 属性默认值（0）",
            "",
            "  实现 GunAttributeProvider 接口并 register() 即可注入配件等效果。",
            "",
            "========================================================================",
            "                          枪 械 预 设",
            "========================================================================",
            "",
            "  如需自定义，复制任意预设段并修改 Material 名称即可。",
            "  未配置的属性自动使用 RpgAttribute 中定义的默认值（多为0）。"
        ));

        dc.set("enabled", DEFAULT_ENABLED);

        // ===== 手枪（IRON_HOE）=====
        dc.set("items.IRON_HOE.gun_damage", "15~25");
        dc.set("items.IRON_HOE.gun_bonus", "30~50%");
        dc.set("items.IRON_HOE.gun_rpm", 300);
        /* 散布 */
        dc.set("items.IRON_HOE.gun_spread_min", 0.5);
        dc.set("items.IRON_HOE.gun_spread_max", 5.0);
        dc.set("items.IRON_HOE.gun_spread_growth", 0.3);
        dc.set("items.IRON_HOE.gun_spread_recovery", 3.0);
        dc.set("items.IRON_HOE.gun_spread_reset_delay", 0);
        dc.set("items.IRON_HOE.gun_spread_firstshot_bonus", 100);
        dc.set("items.IRON_HOE.gun_spread_firstshot_count", 1);
        dc.set("items.IRON_HOE.gun_spread_quadrants", 15);
        dc.set("items.IRON_HOE.gun_spread_bias_x", 50);
        dc.set("items.IRON_HOE.gun_spread_move", 0);
        dc.set("items.IRON_HOE.gun_spread_jump", 80);
        dc.set("items.IRON_HOE.gun_spread_crouch", 30);
        dc.set("items.IRON_HOE.gun_spread_ads", 50);
        dc.set("items.IRON_HOE.gun_spread_pattern", 0);
        /* 后坐 */
        dc.set("items.IRON_HOE.gun_recoil_vertical", 1.5);
        dc.set("items.IRON_HOE.gun_recoil_horizontal", 0.3);
        dc.set("items.IRON_HOE.gun_recoil_growth", 0.05);
        dc.set("items.IRON_HOE.gun_recoil_max", 10.0);
        dc.set("items.IRON_HOE.gun_recoil_recovery", 8.0);
        dc.set("items.IRON_HOE.gun_recoil_reset_delay", 2);
        dc.set("items.IRON_HOE.gun_recoil_firstshot_bonus", 100);
        dc.set("items.IRON_HOE.gun_recoil_firstshot_count", 1);
        dc.set("items.IRON_HOE.gun_recoil_horizontal_bias", 50);
        dc.set("items.IRON_HOE.gun_recoil_crouch", 30);
        dc.set("items.IRON_HOE.gun_recoil_ads", 50);
        dc.set("items.IRON_HOE.gun_recoil_pattern", 1);
        dc.set("items.IRON_HOE.gun_recoil_view_kick", 0.5);
        /* 爆头/部位 */
        dc.set("items.IRON_HOE.gun_headshot_chance", 100);
        dc.set("items.IRON_HOE.gun_headshot_mult", 200);
        dc.set("items.IRON_HOE.gun_upper_chance", 100);
        dc.set("items.IRON_HOE.gun_upper_mult", 100);
        dc.set("items.IRON_HOE.gun_lower_chance", 100);
        dc.set("items.IRON_HOE.gun_lower_mult", 100);
        dc.set("items.IRON_HOE.gun_leg_chance", 100);
        dc.set("items.IRON_HOE.gun_leg_mult", 70);
        dc.set("items.IRON_HOE.gun_headshot_threshold", 85);
        dc.set("items.IRON_HOE.gun_body_threshold", 50);
        dc.set("items.IRON_HOE.gun_leg_threshold", 20);
        /* 射击模式 */
        dc.set("items.IRON_HOE.gun_fire_default_mode", 1);
        dc.set("items.IRON_HOE.gun_fire_available_modes", 2);
        dc.set("items.IRON_HOE.gun_burst_count", 3);
        dc.set("items.IRON_HOE.gun_burst_interval_ms", 80);
        dc.set("items.IRON_HOE.gun_auto_trigger_delay_ms", 0);

        // ===== 步枪（DIAMOND_HOE）=====
        dc.set("items.DIAMOND_HOE.gun_damage", "30~45");
        dc.set("items.DIAMOND_HOE.gun_bonus", "50~80%");
        dc.set("items.DIAMOND_HOE.gun_rpm", 600);
        /* 散布 */
        dc.set("items.DIAMOND_HOE.gun_spread_min", 0.8);
        dc.set("items.DIAMOND_HOE.gun_spread_max", 7.0);
        dc.set("items.DIAMOND_HOE.gun_spread_growth", 0.4);
        dc.set("items.DIAMOND_HOE.gun_spread_recovery", 4.0);
        dc.set("items.DIAMOND_HOE.gun_spread_reset_delay", 0);
        dc.set("items.DIAMOND_HOE.gun_spread_firstshot_bonus", 80);
        dc.set("items.DIAMOND_HOE.gun_spread_firstshot_count", 2);
        dc.set("items.DIAMOND_HOE.gun_spread_quadrants", 15);
        dc.set("items.DIAMOND_HOE.gun_spread_bias_x", 50);
        dc.set("items.DIAMOND_HOE.gun_spread_move", 20);
        dc.set("items.DIAMOND_HOE.gun_spread_jump", 80);
        dc.set("items.DIAMOND_HOE.gun_spread_crouch", 30);
        dc.set("items.DIAMOND_HOE.gun_spread_ads", 50);
        dc.set("items.DIAMOND_HOE.gun_spread_pattern", 0);
        /* 后坐 */
        dc.set("items.DIAMOND_HOE.gun_recoil_vertical", 2.0);
        dc.set("items.DIAMOND_HOE.gun_recoil_horizontal", 0.5);
        dc.set("items.DIAMOND_HOE.gun_recoil_growth", 0.1);
        dc.set("items.DIAMOND_HOE.gun_recoil_max", 15.0);
        dc.set("items.DIAMOND_HOE.gun_recoil_recovery", 8.0);
        dc.set("items.DIAMOND_HOE.gun_recoil_reset_delay", 2);
        dc.set("items.DIAMOND_HOE.gun_recoil_firstshot_bonus", 100);
        dc.set("items.DIAMOND_HOE.gun_recoil_firstshot_count", 1);
        dc.set("items.DIAMOND_HOE.gun_recoil_horizontal_bias", 50);
        dc.set("items.DIAMOND_HOE.gun_recoil_crouch", 30);
        dc.set("items.DIAMOND_HOE.gun_recoil_ads", 50);
        dc.set("items.DIAMOND_HOE.gun_recoil_pattern", 1);
        dc.set("items.DIAMOND_HOE.gun_recoil_view_kick", 1.0);
        /* 爆头/部位 */
        dc.set("items.DIAMOND_HOE.gun_headshot_chance", 100);
        dc.set("items.DIAMOND_HOE.gun_headshot_mult", 200);
        dc.set("items.DIAMOND_HOE.gun_upper_chance", 100);
        dc.set("items.DIAMOND_HOE.gun_upper_mult", 100);
        dc.set("items.DIAMOND_HOE.gun_lower_chance", 100);
        dc.set("items.DIAMOND_HOE.gun_lower_mult", 100);
        dc.set("items.DIAMOND_HOE.gun_leg_chance", 100);
        dc.set("items.DIAMOND_HOE.gun_leg_mult", 70);
        dc.set("items.DIAMOND_HOE.gun_headshot_threshold", 85);
        dc.set("items.DIAMOND_HOE.gun_body_threshold", 50);
        dc.set("items.DIAMOND_HOE.gun_leg_threshold", 20);
        /* 射击模式（步枪：全自动+连发+单发，无安全） */
        dc.set("items.DIAMOND_HOE.gun_fire_default_mode", 3);
        dc.set("items.DIAMOND_HOE.gun_fire_available_modes", 14);
        dc.set("items.DIAMOND_HOE.gun_burst_count", 3);
        dc.set("items.DIAMOND_HOE.gun_burst_interval_ms", 40);
        dc.set("items.DIAMOND_HOE.gun_auto_trigger_delay_ms", 0);

        // ===== 狙击枪（NETHERITE_HOE）=====
        dc.set("items.NETHERITE_HOE.gun_damage", "60~90");
        dc.set("items.NETHERITE_HOE.gun_bonus", "20~30%");
        dc.set("items.NETHERITE_HOE.gun_rpm", 60);
        /* 散布 */
        dc.set("items.NETHERITE_HOE.gun_spread_min", 0.05);
        dc.set("items.NETHERITE_HOE.gun_spread_max", 3.0);
        dc.set("items.NETHERITE_HOE.gun_spread_growth", 1.5);
        dc.set("items.NETHERITE_HOE.gun_spread_recovery", 2.0);
        dc.set("items.NETHERITE_HOE.gun_spread_reset_delay", 10);
        dc.set("items.NETHERITE_HOE.gun_spread_firstshot_bonus", 100);
        dc.set("items.NETHERITE_HOE.gun_spread_firstshot_count", 2);
        dc.set("items.NETHERITE_HOE.gun_spread_quadrants", 15);
        dc.set("items.NETHERITE_HOE.gun_spread_bias_x", 50);
        dc.set("items.NETHERITE_HOE.gun_spread_move", 100);
        dc.set("items.NETHERITE_HOE.gun_spread_jump", 150);
        dc.set("items.NETHERITE_HOE.gun_spread_crouch", 50);
        dc.set("items.NETHERITE_HOE.gun_spread_ads", 80);
        dc.set("items.NETHERITE_HOE.gun_spread_pattern", 2);
        /* 后坐 */
        dc.set("items.NETHERITE_HOE.gun_recoil_vertical", 4.0);
        dc.set("items.NETHERITE_HOE.gun_recoil_horizontal", 1.5);
        dc.set("items.NETHERITE_HOE.gun_recoil_growth", 0.3);
        dc.set("items.NETHERITE_HOE.gun_recoil_max", 25.0);
        dc.set("items.NETHERITE_HOE.gun_recoil_recovery", 5.0);
        dc.set("items.NETHERITE_HOE.gun_recoil_reset_delay", 5);
        dc.set("items.NETHERITE_HOE.gun_recoil_firstshot_bonus", 100);
        dc.set("items.NETHERITE_HOE.gun_recoil_firstshot_count", 1);
        dc.set("items.NETHERITE_HOE.gun_recoil_horizontal_bias", 50);
        dc.set("items.NETHERITE_HOE.gun_recoil_crouch", 40);
        dc.set("items.NETHERITE_HOE.gun_recoil_ads", 60);
        dc.set("items.NETHERITE_HOE.gun_recoil_pattern", 0);
        dc.set("items.NETHERITE_HOE.gun_recoil_view_kick", 1.5);
        /* 爆头/部位（狙击：爆头率60%，3倍伤害） */
        dc.set("items.NETHERITE_HOE.gun_headshot_chance", 60);
        dc.set("items.NETHERITE_HOE.gun_headshot_mult", 300);
        dc.set("items.NETHERITE_HOE.gun_upper_chance", 100);
        dc.set("items.NETHERITE_HOE.gun_upper_mult", 120);
        dc.set("items.NETHERITE_HOE.gun_lower_chance", 100);
        dc.set("items.NETHERITE_HOE.gun_lower_mult", 100);
        dc.set("items.NETHERITE_HOE.gun_leg_chance", 40);
        dc.set("items.NETHERITE_HOE.gun_leg_mult", 50);
        dc.set("items.NETHERITE_HOE.gun_headshot_threshold", 85);
        dc.set("items.NETHERITE_HOE.gun_body_threshold", 50);
        dc.set("items.NETHERITE_HOE.gun_leg_threshold", 20);
        /* 射击模式（狙击枪：仅单发） */
        dc.set("items.NETHERITE_HOE.gun_fire_default_mode", 1);
        dc.set("items.NETHERITE_HOE.gun_fire_available_modes", 2);
        dc.set("items.NETHERITE_HOE.gun_burst_count", 0);
        dc.set("items.NETHERITE_HOE.gun_burst_interval_ms", 80);
        dc.set("items.NETHERITE_HOE.gun_auto_trigger_delay_ms", 0);

        /* ---- 口径与武器类型 ---- */
        dc.set("items.IRON_HOE.caliber", "9mm");
        dc.set("items.IRON_HOE.default_ammo", "fmj");
        dc.set("items.DIAMOND_HOE.caliber", "5.56mm");
        dc.set("items.DIAMOND_HOE.default_ammo", "fmj");
        dc.set("items.NETHERITE_HOE.caliber", ".338lapua");
        dc.set("items.NETHERITE_HOE.default_ammo", "ap");

        /* ---- 弹夹系统 ---- */
        dc.set("items.IRON_HOE.gun_mag_capacity", 15);
        dc.set("items.IRON_HOE.gun_reload_time_ticks", 30);
        dc.set("items.IRON_HOE.gun_reload_empty_time_ticks", 40);
        dc.set("items.IRON_HOE.gun_auto_reload", 0);
        dc.set("items.DIAMOND_HOE.gun_mag_capacity", 30);
        dc.set("items.DIAMOND_HOE.gun_reload_time_ticks", 50);
        dc.set("items.DIAMOND_HOE.gun_reload_empty_time_ticks", 60);
        dc.set("items.DIAMOND_HOE.gun_auto_reload", 1);
        dc.set("items.NETHERITE_HOE.gun_mag_capacity", 5);
        dc.set("items.NETHERITE_HOE.gun_reload_time_ticks", 70);
        dc.set("items.NETHERITE_HOE.gun_reload_empty_time_ticks", 80);
        dc.set("items.NETHERITE_HOE.gun_auto_reload", 0);
        /* 枪膛 (狙击枪启用) */
        dc.set("items.NETHERITE_HOE.gun_chamber_enabled", 1);
        dc.set("items.NETHERITE_HOE.gun_chamber_bolt_time_ticks", 20);

        /* ---- 弹道系统 ---- */
        dc.set("items.IRON_HOE.gun_bullet_speed", 60);
        dc.set("items.IRON_HOE.gun_bullet_gravity", 0);
        dc.set("items.IRON_HOE.gun_bullet_lifetime_ticks", 80);
        dc.set("items.IRON_HOE.gun_bullet_drag", 0.5);
        dc.set("items.IRON_HOE.gun_bullet_damage_falloff_start", 15);
        dc.set("items.IRON_HOE.gun_bullet_damage_falloff_end", 50);
        dc.set("items.IRON_HOE.gun_bullet_damage_min_percent", 40);
        dc.set("items.DIAMOND_HOE.gun_bullet_speed", 80);
        dc.set("items.DIAMOND_HOE.gun_bullet_gravity", 1);
        dc.set("items.DIAMOND_HOE.gun_bullet_lifetime_ticks", 120);
        dc.set("items.DIAMOND_HOE.gun_bullet_drag", 0.8);
        dc.set("items.DIAMOND_HOE.gun_bullet_damage_falloff_start", 20);
        dc.set("items.DIAMOND_HOE.gun_bullet_damage_falloff_end", 80);
        dc.set("items.DIAMOND_HOE.gun_bullet_damage_min_percent", 40);
        dc.set("items.NETHERITE_HOE.gun_bullet_speed", 120);
        dc.set("items.NETHERITE_HOE.gun_bullet_gravity", 1);
        dc.set("items.NETHERITE_HOE.gun_bullet_lifetime_ticks", 200);
        dc.set("items.NETHERITE_HOE.gun_bullet_drag", 0.3);
        dc.set("items.NETHERITE_HOE.gun_bullet_damage_falloff_start", 30);
        dc.set("items.NETHERITE_HOE.gun_bullet_damage_falloff_end", 120);
        dc.set("items.NETHERITE_HOE.gun_bullet_damage_min_percent", 60);

        /* ---- 穿透系统 ---- */
        dc.set("items.NETHERITE_HOE.gun_penetration_count", 2);
        dc.set("items.NETHERITE_HOE.gun_penetration_falloff", 25);
        dc.set("items.DIAMOND_HOE.gun_penetration_count", 1);
        dc.set("items.DIAMOND_HOE.gun_penetration_falloff", 35);

        /* ---- 耐久系统 ---- */
        dc.set("items.IRON_HOE.gun_dura_max", 500);
        dc.set("items.IRON_HOE.gun_dura_loss_per_shot", 1);
        dc.set("items.DIAMOND_HOE.gun_dura_max", 600);
        dc.set("items.DIAMOND_HOE.gun_dura_loss_per_shot", 1);
        dc.set("items.NETHERITE_HOE.gun_dura_max", 300);
        dc.set("items.NETHERITE_HOE.gun_dura_loss_per_shot", 2);

        /* ==================== 全局系统开关 ==================== */
        dc.set("systems.overheat.enabled", false);
        dc.set("systems.overheat.global_cool_rate", 10.0);
        dc.set("systems.malfunction.enabled", false);
        dc.set("systems.malfunction.global_cooldown_ticks", 40);
        dc.set("systems.magazine.enabled", true);
        dc.set("systems.magazine.global_reload_input", "R");   // R键换弹
        dc.set("systems.magazine.auto_reload", true);
        dc.set("systems.chamber.enabled", false);
        dc.set("systems.ammo.enabled", false);
        dc.set("systems.durability.enabled", false);
        dc.set("systems.durability.global_repair_material", "IRON_INGOT");
        dc.set("systems.durability.global_repair_per_material", 5);
        dc.set("systems.durability.global_repair_cost_mode", "flat");
        dc.set("systems.durability.global_repair_cost", 1);
        dc.set("systems.penetration.enabled", false);
        dc.set("systems.ballistics.enabled", true);
        dc.set("systems.special_weapons.enabled", false);

        try {
            dc.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("无法创建默认 gun.yml: " + e.getMessage());
        }
    }

    /* ==================== 查询方法 ==================== */

    public boolean isEnabled() { return enabled; }

    /** 获取指定 Material 上某个属性的区间模板。不存在返回 null。 */
    public AttributeRange getAttrRange(Material material, RpgAttribute attr) {
        Map<RpgAttribute, AttributeRange> map = materialTemplates.get(material);
        if (map == null) return null;
        return map.get(attr);
    }

    /** 获取指定 Material 的全部配置属性。空 Map 表示无配置。 */
    public Map<RpgAttribute, AttributeRange> getAttributeRanges(Material material) {
        Map<RpgAttribute, AttributeRange> template = materialTemplates.get(material);
        return template != null ? Collections.unmodifiableMap(template) : Collections.emptyMap();
    }

    /** 某 Material 是否有配置 */
    public boolean hasMaterial(Material material) {
        return materialTemplates.containsKey(material);
    }

    public Set<Material> getConfiguredMaterials() {
        return Collections.unmodifiableSet(materialTemplates.keySet());
    }

    /** 获取枪械绑定的口径ID，可能为null */
    public String getCaliber(Material material) { return caliberMap.get(material); }

    /** 获取枪械的武器类型ID，可能为null（null=普通枪械） */
    public String getWeaponType(Material material) { return weaponTypeMap.get(material); }

    /** 获取枪械的默认弹种ID，可能为null */
    public String getDefaultAmmo(Material material) { return defaultAmmoMap.get(material); }

    /** 获取全局系统配置节点 */
    public ConfigurationSection getSystemsSection() { return systemsSection; }

    /** 获取某个系统是否启用 */
    public boolean isSystemEnabled(String systemName) {
        if (systemsSection == null) return false;
        return systemsSection.getBoolean(systemName + ".enabled", false);
    }

    public void reload() { loadConfig(); }
}
