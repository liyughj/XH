package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.lore.LoreConfig;
import io.github.liyughj.xH.lore.LoreManager;
import io.github.liyughj.xH.rpg.Attribute.AttributeRange;
import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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
    /** Material → 空仓击发音效 (Bukkit Sound 名称) */
    private final Map<Material, String> dryFireSoundMap = new EnumMap<>(Material.class);
    /** Material → 换弹音效 (Bukkit Sound 名称) */
    private final Map<Material, String> reloadSoundMap = new EnumMap<>(Material.class);
    /** Material → 射击音效 (Bukkit Sound 名称，如 ENTITY_GENERIC_EXPLODE) */
    private final Map<Material, String> shootSoundMap = new EnumMap<>(Material.class);
    /** gun_id → Material 反向映射 */
    private final Map<String, Material> gunIdToMaterial = new LinkedHashMap<>();
    /** Material → gun_id */
    private final Map<Material, String> materialToGunId = new EnumMap<>(Material.class);
    /** 弹匣配置 ID → MagazineDef */
    private final Map<String, MagazineDef> magazineMap = new LinkedHashMap<>();

    /** 全局系统开关配置 */
    private ConfigurationSection systemsSection;

    public GunItemConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE_NAME);

        if (!configFile.exists()) {
            createDefaultConfig(configFile);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        enabled = config.getBoolean("enabled", DEFAULT_ENABLED);

        systemsSection = config.getConfigurationSection("systems");
        if (systemsSection == null) systemsSection = config.createSection("systems");

        materialTemplates.clear();
        caliberMap.clear();
        weaponTypeMap.clear();
        defaultAmmoMap.clear();
        dryFireSoundMap.clear();
        reloadSoundMap.clear();
        shootSoundMap.clear();
        gunIdToMaterial.clear();
        materialToGunId.clear();

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
                if (caliber != null && !caliber.isEmpty()) caliberMap.put(material, AmmoConfig.normalizeCaliberId(caliber));

                String weaponType = attrSection.getString("gun_weapon_type");
                if (weaponType != null && !weaponType.isEmpty()) weaponTypeMap.put(material, weaponType);

                String defaultAmmo = attrSection.getString("default_ammo");
                if (defaultAmmo != null && !defaultAmmo.isEmpty()) defaultAmmoMap.put(material, defaultAmmo);

                String dryFireSound = attrSection.getString("gun_dry_fire_sound");
                if (dryFireSound != null && !dryFireSound.isEmpty()) dryFireSoundMap.put(material, dryFireSound);

                String reloadSound = attrSection.getString("gun_reload_sound");
                if (reloadSound != null && !reloadSound.isEmpty()) reloadSoundMap.put(material, reloadSound);

                String shootSound = attrSection.getString("gun_shoot_sound");
                if (shootSound != null && !shootSound.isEmpty()) shootSoundMap.put(material, shootSound);

                // gun_id 标识
                String gunId = attrSection.getString("gun_id");
                if (gunId != null && !gunId.isEmpty()) {
                    gunIdToMaterial.put(gunId.toLowerCase(), material);
                    materialToGunId.put(material, gunId.toLowerCase());
                }

                Map<RpgAttribute, AttributeRange> attrs = new LinkedHashMap<>();
                for (String attrKey : attrSection.getKeys(false)) {
                    // 跳过字符串kv
                    if (attrKey.equals("caliber") || attrKey.equals("gun_weapon_type") || attrKey.equals("default_ammo")
                        || attrKey.equals("gun_dry_fire_sound") || attrKey.equals("gun_reload_sound")
                        || attrKey.equals("gun_shoot_sound") || attrKey.equals("gun_id")) continue;
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

        // 弹匣配置
        magazineMap.clear();
        ConfigurationSection magSection = config.getConfigurationSection("magazines");
        if (magSection != null) {
            for (String magId : magSection.getKeys(false)) {
                ConfigurationSection ms = magSection.getConfigurationSection(magId);
                if (ms == null) continue;
                MagazineDef def = new MagazineDef();
                def.id = magId;
                def.displayName = ms.getString("display_name", magId);
                def.caliber = AmmoConfig.normalizeCaliberId(ms.getString("caliber", ""));
                def.capacity = ms.getInt("capacity", 30);
                def.itemMaterial = ms.getString("item_material", "IRON_INGOT");
                def.itemCustomModelData = ms.getInt("item_custom_model_data", 0);
                magazineMap.put(magId, def);
            }
        }
        plugin.getLogger().info("[枪械] 已加载 " + magazineMap.size() + " 个弹匣配置");
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
            "  gun_id                   枪械ID（字符串，/xh give gun <id> 使用）",
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
            "  gun_dry_fire_sound          空仓击发音效（Bukkit Sound枚举名 如BLOCK_LEVER_CLICK）",
            "  gun_reload_sound            换弹音效（Bukkit Sound枚举名 如BLOCK_PISTON_EXTEND）",
            "  gun_shoot_sound             射击音效（Bukkit Sound枚举名 如ENTITY_GENERIC_EXPLODE）",
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
            "  bleed_chance                 流血概率（%，通用）",
            "  bleed_damage                 流血伤害（HP/tick，通用）",
            "  bleed_ticks                  流血持续（tick，通用）",
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
            "--- 人体工学 ---",
            "  gun_equip_time_ticks         切枪掏出耗时（tick）",
            "  gun_holster_time_ticks       收枪耗时（tick）",
            "  gun_sprint_to_fire_ticks     疾跑→可开火延迟（tick）",
            "  gun_ads_in_time_ticks        开镜渐入时间（tick）",
            "  gun_ads_out_time_ticks       关镜渐出时间（tick）",
            "  gun_weapon_swap_speed        切枪速度（100=正常）",
            "",
            "--- 机动性 ---",
            "  gun_move_speed               持枪移速（100=正常）",
            "  gun_sprint_speed             持枪疾跑速度",
            "  gun_ads_move_speed           开镜移速",
            "  gun_jump_height              持枪跳跃高度",
            "  gun_can_sprint               允许疾跑（1=允许 0=禁止）",
            "",
            "--- 开镜高级属性 ---",
            "  gun_ads_sensitivity          开镜灵敏度",
            "  gun_ads_sway_amount          开镜晃动（%越大散布越大）",
             "  gun_ads_breath_max           呼吸值上限（默认100）",
             "  gun_ads_breath_drain         屏息消耗/tick",
             "  gun_ads_breath_regen         呼吸恢复/tick",
             "  gun_ads_breath_threshold     屏息最低阈值（%），低于%×max自动停屏息",
             "  gun_ads_night_vision         开镜夜视（1=开启）",
            "  gun_ads_scope_type           瞄具类型（0=机瞄 1=红点 2=全息 3=四倍 4=高倍 5=热成像）",
            "",
            "--- 命中特效 ---",
            "  gun_hit_slow_chance          命中减速概率（%）",
            "  gun_hit_slow_amount          减速幅度（%）",
            "  gun_hit_slow_ticks           减速持续（tick）",
            "  gun_hit_stagger_chance       命中硬直概率（%）",
            "  gun_hit_stagger_strength     硬直击退力度",
            "  gun_hit_blind_chance         命中致盲概率（%）",
            "  gun_hit_blind_ticks          致盲持续（tick）",
            "",
            "--- 击杀连锁 ---",
            "  gun_on_kill_trigger_chance   击杀触发概率（%）",
            "  gun_on_kill_reload_speed     击杀后换弹加速（%）",
            "  gun_on_kill_damage_bonus     击杀后伤害加成（%）",
            "  gun_on_kill_heal             击杀回复生命",
            "  gun_on_kill_buff_ticks       击杀buff持续（tick）",
            "",
            "--- 弹道高级特性 ---",
            "  gun_bullet_ricochet_chance   跳弹概率（%）",
            "  gun_bullet_ricochet_angle    跳弹触发最大入射角（度）",
            "  gun_bullet_water_speed       水中弹速（100=正常）",
            "  gun_bullet_glass_pierce      玻璃穿透（1=穿透）",
            "",
            "--- 视觉/音效 ---",
            "  gun_muzzle_flash_intensity   枪口火焰强度（1-5）",
            "  gun_muzzle_flash_color       焰色（RGB整数）",
            "  gun_shell_eject              抛壳（1=开启）",
            "  gun_shell_material           弹壳材质（Material名称）",
            "  gun_hit_marker_type          命中标记（0=默认 1=十字 2=圆圈 3=菱形）",
            "  gun_hit_marker_kill          击杀标记（0=默认 1=特殊音效 2=粒子）",
            "  gun_inspect_ticks            检视时长（tick）",
            "",
            "--- 压制系统 ---",
            "  gun_suppress_radius          压制范围（格）",
            "  gun_suppress_amount          压制强度（%），被压制方散布/后坐增幅",
            "  gun_suppress_duration_ticks  压制持续（tick）",
            "",
            "--- 耐久补充 ---",
            "  gun_dura_repair_cost         修理成本（材料数量）",
            "  gun_dura_repair_material     修理材料ID（Material名称）",
            "",
            "--- 配件槽位 ---",
            "  gun_attachment_slots         可用槽位bitmask（muzzle=1 optic=2 grip=4 mag=8 stock=16 laser=32 trigger=64）",
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
        dc.set("items.IRON_HOE.gun_id", "pistol");
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
        dc.set("items.DIAMOND_HOE.gun_id", "rifle");
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
        dc.set("items.NETHERITE_HOE.gun_id", "sniper");
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
        dc.set("items.IRON_HOE.gun_dry_fire_sound", "BLOCK_LEVER_CLICK");
        dc.set("items.IRON_HOE.gun_reload_sound", "BLOCK_PISTON_EXTEND");
        dc.set("items.DIAMOND_HOE.caliber", "5_56mm");
        dc.set("items.DIAMOND_HOE.default_ammo", "fmj");
        dc.set("items.DIAMOND_HOE.gun_dry_fire_sound", "BLOCK_LEVER_CLICK");
        dc.set("items.DIAMOND_HOE.gun_reload_sound", "BLOCK_PISTON_EXTEND");
        dc.set("items.NETHERITE_HOE.caliber", "338lapua");
        dc.set("items.NETHERITE_HOE.default_ammo", "ap");
        dc.set("items.NETHERITE_HOE.gun_dry_fire_sound", "BLOCK_LEVER_CLICK");
        dc.set("items.NETHERITE_HOE.gun_reload_sound", "BLOCK_PISTON_EXTEND");

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

        /* ---- 呼吸/屏息 (开镜时自动屏息) ---- */
        dc.set("items.IRON_HOE.gun_ads_breath_max", 100);
        dc.set("items.IRON_HOE.gun_ads_breath_drain", 0.3);
        dc.set("items.IRON_HOE.gun_ads_breath_regen", 0.8);
        dc.set("items.IRON_HOE.gun_ads_breath_threshold", 30);
        dc.set("items.DIAMOND_HOE.gun_ads_breath_max", 100);
        dc.set("items.DIAMOND_HOE.gun_ads_breath_drain", 0.4);
        dc.set("items.DIAMOND_HOE.gun_ads_breath_regen", 0.6);
        dc.set("items.DIAMOND_HOE.gun_ads_breath_threshold", 35);
        dc.set("items.NETHERITE_HOE.gun_ads_breath_max", 100);
        dc.set("items.NETHERITE_HOE.gun_ads_breath_drain", 0.5);
        dc.set("items.NETHERITE_HOE.gun_ads_breath_regen", 0.5);
        dc.set("items.NETHERITE_HOE.gun_ads_breath_threshold", 40);

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

        /* ---- 人体工学 ---- */
        dc.set("items.IRON_HOE.gun_equip_time_ticks", 6);
        dc.set("items.IRON_HOE.gun_holster_time_ticks", 4);
        dc.set("items.IRON_HOE.gun_sprint_to_fire_ticks", 8);
        dc.set("items.IRON_HOE.gun_ads_in_time_ticks", 5);
        dc.set("items.IRON_HOE.gun_ads_out_time_ticks", 3);
        dc.set("items.IRON_HOE.gun_weapon_swap_speed", 100);
        dc.set("items.DIAMOND_HOE.gun_equip_time_ticks", 8);
        dc.set("items.DIAMOND_HOE.gun_holster_time_ticks", 6);
        dc.set("items.DIAMOND_HOE.gun_sprint_to_fire_ticks", 10);
        dc.set("items.DIAMOND_HOE.gun_ads_in_time_ticks", 7);
        dc.set("items.DIAMOND_HOE.gun_ads_out_time_ticks", 4);
        dc.set("items.DIAMOND_HOE.gun_weapon_swap_speed", 85);
        dc.set("items.NETHERITE_HOE.gun_equip_time_ticks", 12);
        dc.set("items.NETHERITE_HOE.gun_holster_time_ticks", 8);
        dc.set("items.NETHERITE_HOE.gun_sprint_to_fire_ticks", 14);
        dc.set("items.NETHERITE_HOE.gun_ads_in_time_ticks", 10);
        dc.set("items.NETHERITE_HOE.gun_ads_out_time_ticks", 6);
        dc.set("items.NETHERITE_HOE.gun_weapon_swap_speed", 70);

        /* ---- 机动性 ---- */
        dc.set("items.IRON_HOE.gun_move_speed", 90);
        dc.set("items.IRON_HOE.gun_sprint_speed", 100);
        dc.set("items.IRON_HOE.gun_ads_move_speed", 60);
        dc.set("items.IRON_HOE.gun_jump_height", 100);
        dc.set("items.IRON_HOE.gun_can_sprint", 1);
        dc.set("items.DIAMOND_HOE.gun_move_speed", 85);
        dc.set("items.DIAMOND_HOE.gun_sprint_speed", 100);
        dc.set("items.DIAMOND_HOE.gun_ads_move_speed", 50);
        dc.set("items.DIAMOND_HOE.gun_jump_height", 100);
        dc.set("items.DIAMOND_HOE.gun_can_sprint", 1);
        dc.set("items.NETHERITE_HOE.gun_move_speed", 65);
        dc.set("items.NETHERITE_HOE.gun_sprint_speed", 90);
        dc.set("items.NETHERITE_HOE.gun_ads_move_speed", 35);
        dc.set("items.NETHERITE_HOE.gun_jump_height", 85);
        dc.set("items.NETHERITE_HOE.gun_can_sprint", 0);

        /* ---- 开镜高级 ---- */
        dc.set("items.IRON_HOE.gun_ads_sensitivity", 70);
        dc.set("items.IRON_HOE.gun_ads_sway_amount", 1.5);
        dc.set("items.IRON_HOE.gun_ads_night_vision", 0);
        dc.set("items.IRON_HOE.gun_ads_scope_type", 1);
        dc.set("items.DIAMOND_HOE.gun_ads_sensitivity", 60);
        dc.set("items.DIAMOND_HOE.gun_ads_sway_amount", 2.5);
        dc.set("items.DIAMOND_HOE.gun_ads_night_vision", 0);
        dc.set("items.DIAMOND_HOE.gun_ads_scope_type", 1);
        dc.set("items.NETHERITE_HOE.gun_ads_sensitivity", 40);
        dc.set("items.NETHERITE_HOE.gun_ads_sway_amount", 5.0);
        dc.set("items.NETHERITE_HOE.gun_ads_night_vision", 0);
        dc.set("items.NETHERITE_HOE.gun_ads_scope_type", 4);

        /* ---- 命中特效 ---- */
        dc.set("items.NETHERITE_HOE.gun_hit_slow_chance", 50);
        dc.set("items.NETHERITE_HOE.gun_hit_slow_amount", 40);
        dc.set("items.NETHERITE_HOE.gun_hit_slow_ticks", 40);
        dc.set("items.NETHERITE_HOE.gun_hit_stagger_chance", 30);
        dc.set("items.NETHERITE_HOE.gun_hit_stagger_strength", 1.5);
        dc.set("items.NETHERITE_HOE.gun_hit_blind_chance", 10);
        dc.set("items.NETHERITE_HOE.gun_hit_blind_ticks", 40);

        /* ---- 击杀连锁 ---- */
        dc.set("items.NETHERITE_HOE.gun_on_kill_trigger_chance", 100);
        dc.set("items.NETHERITE_HOE.gun_on_kill_reload_speed", 30);
        dc.set("items.NETHERITE_HOE.gun_on_kill_damage_bonus", 20);
        dc.set("items.NETHERITE_HOE.gun_on_kill_heal", 2);
        dc.set("items.NETHERITE_HOE.gun_on_kill_buff_ticks", 120);

        /* ---- 弹道高级 ---- */
        dc.set("items.NETHERITE_HOE.gun_bullet_ricochet_chance", 10);
        dc.set("items.NETHERITE_HOE.gun_bullet_ricochet_angle", 60);
        dc.set("items.NETHERITE_HOE.gun_bullet_water_speed", 70);
        dc.set("items.NETHERITE_HOE.gun_bullet_glass_pierce", 1);

        /* ---- 视觉/音效 ---- */
        dc.set("items.IRON_HOE.gun_muzzle_flash_intensity", 2);
        dc.set("items.IRON_HOE.gun_muzzle_flash_color", 16744448);
        dc.set("items.IRON_HOE.gun_shell_eject", 1);
        dc.set("items.IRON_HOE.gun_shell_material", "GOLD_NUGGET");
        dc.set("items.DIAMOND_HOE.gun_muzzle_flash_intensity", 3);
        dc.set("items.DIAMOND_HOE.gun_muzzle_flash_color", 16744448);
        dc.set("items.DIAMOND_HOE.gun_shell_eject", 1);
        dc.set("items.DIAMOND_HOE.gun_shell_material", "GOLD_NUGGET");
        dc.set("items.NETHERITE_HOE.gun_muzzle_flash_intensity", 5);
        dc.set("items.NETHERITE_HOE.gun_muzzle_flash_color", 16744448);
        dc.set("items.NETHERITE_HOE.gun_shell_eject", 1);
        dc.set("items.NETHERITE_HOE.gun_shell_material", "IRON_NUGGET");

        /* ---- 压制系统 ---- */
        dc.set("items.NETHERITE_HOE.gun_suppress_radius", 15);
        dc.set("items.NETHERITE_HOE.gun_suppress_amount", 40);
        dc.set("items.NETHERITE_HOE.gun_suppress_duration_ticks", 60);

        /* ---- 耐久补充 ---- */
        dc.set("items.NETHERITE_HOE.gun_dura_repair_cost", 3);
        dc.set("items.NETHERITE_HOE.gun_dura_repair_material", "IRON_INGOT");

        /* ---- 配件槽位 ---- */
        dc.set("items.IRON_HOE.gun_attachment_slots", 11);    // muzzle(1)+optic(2)+mag(8) = 11
        dc.set("items.DIAMOND_HOE.gun_attachment_slots", 63); // 6 slots
        dc.set("items.NETHERITE_HOE.gun_attachment_slots", 127); // all 7 slots

        /* ==================== 全局系统开关 ==================== */
        dc.set("systems.overheat.enabled", true);
        dc.set("systems.overheat.global_cool_rate", 10.0);
        dc.set("systems.malfunction.enabled", true);
        dc.set("systems.malfunction.global_cooldown_ticks", 40);
        dc.set("systems.magazine.enabled", true);
        dc.set("systems.magazine.global_reload_input", "Q");   // Q键换弹（丢弃物品键）
        dc.set("systems.magazine.auto_reload", true);
        dc.set("systems.chamber.enabled", true);
        dc.set("systems.ammo.enabled", true);
        dc.set("systems.durability.enabled", true);
        dc.set("systems.durability.global_repair_material", "IRON_INGOT");
        dc.set("systems.durability.global_repair_per_material", 5);
        dc.set("systems.durability.global_repair_cost_mode", "flat");
        dc.set("systems.durability.global_repair_cost", 1);
        dc.set("systems.penetration.enabled", true);
        dc.set("systems.ballistics.enabled", true);
        dc.set("systems.special_weapons.enabled", true);

        /* ==================== 弹匣预设 ==================== */
        dc.set("magazines.glock_17.display_name", "Glock 17 弹匣");
        dc.set("magazines.glock_17.caliber", "9mm");
        dc.set("magazines.glock_17.capacity", 17);
        dc.set("magazines.glock_17.item_material", "IRON_INGOT");
        dc.set("magazines.stanag_30.display_name", "STANAG 30发 弹匣");
        dc.set("magazines.stanag_30.caliber", "5_56mm");
        dc.set("magazines.stanag_30.capacity", 30);
        dc.set("magazines.stanag_30.item_material", "IRON_INGOT");
        dc.set("magazines.ai_5.display_name", "AI 5发 弹匣");
        dc.set("magazines.ai_5.caliber", "338lapua");
        dc.set("magazines.ai_5.capacity", 5);
        dc.set("magazines.ai_5.item_material", "IRON_INGOT");

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

    /** 获取枪械的空仓击发音效（Bukkit Sound 名称），null=使用默认 */
    public String getDryFireSound(Material material) { return dryFireSoundMap.get(material); }

    /** 获取枪械的换弹音效（Bukkit Sound 名称），null=使用默认 */
    public String getReloadSound(Material material) { return reloadSoundMap.get(material); }

    /** 获取枪械的射击音效（Bukkit Sound 名称），null=使用默认 ENTITY_GENERIC_EXPLODE */
    public String getShootSound(Material material) { return shootSoundMap.get(material); }

    /** gun_id → Material */
    public Material getMaterialByGunId(String gunId) { return gunIdToMaterial.get(gunId.toLowerCase()); }
    /** Material → gun_id */
    public String getGunId(Material material) { return materialToGunId.get(material); }
    /** 获取所有已注册的 gun_id */
    public Set<String> getAllGunIds() { return Collections.unmodifiableSet(gunIdToMaterial.keySet()); }

    /** 弹匣配置 */
    public MagazineDef getMagazineDef(String magId) { return magazineMap.get(magId); }
    /** 全部弹匣ID */
    public Set<String> getAllMagazineIds() { return Collections.unmodifiableSet(magazineMap.keySet()); }

    /** 根据 mag_id 创建弹匣 ItemStack */
    public ItemStack createMagazineItem(String magId) {
        MagazineDef def = magazineMap.get(magId);
        if (def == null) return null;
        Material mat = Material.getMaterial(def.itemMaterial);
        if (mat == null) mat = Material.IRON_INGOT;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text("§b" + def.displayName));
        // PDC
        meta.getPersistentDataContainer().set(new NamespacedKey("xh", "magazine_id"), PersistentDataType.STRING, magId);
        meta.getPersistentDataContainer().set(new NamespacedKey("xh", "mag_caliber"), PersistentDataType.STRING, def.caliber);
        meta.getPersistentDataContainer().set(new NamespacedKey("xh", "mag_capacity"), PersistentDataType.INTEGER, def.capacity);

        // 用默认弹种填满弹夹
        int fillCount = 0;
        if (GunSystemConfig.ammo() != null) {
            String defaultType = GunSystemConfig.ammo().getDefaultAmmoType(def.caliber);
            if (defaultType != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < def.capacity; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(defaultType);
                }
                meta.getPersistentDataContainer().set(new NamespacedKey("xh", "mag_ammo_stack"), PersistentDataType.STRING, sb.toString());
                fillCount = def.capacity;
            }
        }
        meta.getPersistentDataContainer().set(new NamespacedKey("xh", "mag_ammo"), PersistentDataType.INTEGER, fillCount);
        if (def.itemCustomModelData > 0) meta.setCustomModelData(def.itemCustomModelData);

        item.setItemMeta(meta);

        // 初始化耐久
        double maxDura = AttributeStorage.getAttrValue(item, RpgAttribute.GUN_DURA_MAX);
        if (maxDura > 0) {
            DurabilityManager.setDurability(item, maxDura);
        }

        // 应用 LoreManager 模板生成 lore
        if (LoreConfig.hasInstance() && LoreConfig.instance().isEnabled()) {
            List<Component> loreLines = LoreManager.buildMagazineLore(def.caliber, def.capacity);
            if (!loreLines.isEmpty()) {
                ItemMeta updatedMeta = item.getItemMeta();
                updatedMeta.lore(loreLines);
                item.setItemMeta(updatedMeta);
            }
        }
        return item;
    }

    /** 根据 gun_id 创建枪械 ItemStack，属性从 gun.yml 写入 PDC */
    public ItemStack createGunItem(String gunId) {
        Material mat = gunIdToMaterial.get(gunId.toLowerCase());
        if (mat == null) return null;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // gun_id PDC
        meta.getPersistentDataContainer().set(new NamespacedKey("xh", "gun_id"), PersistentDataType.STRING, gunId.toLowerCase());

        // 写入 gun.yml 全部属性到 PDC
        Map<RpgAttribute, AttributeRange> attrs = materialTemplates.get(mat);
        if (attrs != null) {
            for (Map.Entry<RpgAttribute, AttributeRange> e : attrs.entrySet()) {
                AttributeRange range = e.getValue();
                if (range.getMin() != e.getKey().getDefaultValue() || range.getMax() != e.getKey().getDefaultValue()) {
                    meta.getPersistentDataContainer().set(
                        new NamespacedKey("xh", "item." + e.getKey().getKey() + "_min"),
                        PersistentDataType.DOUBLE, range.getMin());
                    meta.getPersistentDataContainer().set(
                        new NamespacedKey("xh", "item." + e.getKey().getKey() + "_max"),
                        PersistentDataType.DOUBLE, range.getMax());
                }
            }
        }
        // 字符串属性
        String caliber = caliberMap.get(mat);
        if (caliber != null) meta.getPersistentDataContainer().set(new NamespacedKey("xh", "ammo_caliber"), PersistentDataType.STRING, caliber);
        String weaponType = weaponTypeMap.get(mat);
        if (weaponType != null) meta.getPersistentDataContainer().set(new NamespacedKey("xh", "gun_weapon_type"), PersistentDataType.STRING, weaponType);
        String defaultAmmo = defaultAmmoMap.get(mat);
        if (defaultAmmo != null) meta.getPersistentDataContainer().set(new NamespacedKey("xh", "ammo_type"), PersistentDataType.STRING, defaultAmmo);

        // 显示名
        String displayName = gunIdToMaterial.containsKey(gunId.toLowerCase()) ? "§6" + gunId : mat.name();
        meta.displayName(Component.text(displayName));

        item.setItemMeta(meta);

        // 初始化弹匣容量和弹夹栈（必须在 lore 生成之前）
        double cap = AttributeStorage.getAttrValue(item, RpgAttribute.GUN_MAG_CAPACITY);
        int capInt = (int) cap;
        if (capInt > 0) {
            MagazineManager.setAmmo(item, capInt);
            // 用默认弹种填充弹夹栈
            String defaultAmmoType = defaultAmmoMap.get(mat);
            if (defaultAmmoType != null) {
                MagazineManager.pushAmmoToStack(item, defaultAmmoType, capInt);
            }
            // 如果枪膛启用，自动上膛一发
            if (ChamberManager.isEnabled(item)) {
                ChamberManager.afterReload(item);
            }
        }

        // 应用 LoreManager 模板生成 lore（在弹夹/枪膛初始化之后）
        if (LoreConfig.hasInstance() && LoreConfig.instance().isEnabled()) {
            List<Component> loreLines = LoreManager.buildGunLore(item);
            if (!loreLines.isEmpty()) {
                ItemMeta updatedMeta = item.getItemMeta();
                updatedMeta.lore(loreLines);
                item.setItemMeta(updatedMeta);
            }
        }

        return item;
    }

    /** 获取全局系统配置节点 */
    public ConfigurationSection getSystemsSection() { return systemsSection; }

    /** 获取某个系统是否启用 */
    public boolean isSystemEnabled(String systemName) {
        if (systemsSection == null) return false;
        return systemsSection.getBoolean(systemName + ".enabled", false);
    }

    public void reload() { loadConfig(); }

    /** 弹匣模板定义 */
    public static class MagazineDef {
        public String id;
        public String displayName;
        public String caliber;
        public int capacity;
        public String itemMaterial;
        public int itemCustomModelData;
    }
}
