package io.github.liyughj.xH.lore;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

/**
 * Lore 模板配置管理器 —— 负责加载/管理 lore.yml。
 *
 * <h3>配置文件结构</h3>
 * <pre>{@code
 * # 每个模块下:
 * #   order: [属性key列表]   → 控制lore行的顺序
 * #   属性key: "模板文本"     → 占位符 {value} 会被替换为属性格式化值
 * #   header: "顶部文本"      → 模块lore的头部
 * #   footer: "底部文本"      → 模块lore的尾部
 *
 * # 支持特殊标签: {rpg} RPG字体前缀, {reset} 格式化重置
 * # 颜色代码: &0-&f, &k-o, &#rrggbb
 *
 * gun:
 *   header: "&6═══ 枪械属性 ═══"
 *   order:
 *     - gun_damage
 *     - gun_fire_rate
 *     - gun_recoil
 *   gun_damage: "&7伤害: &c{value}"
 *   gun_fire_rate: "&7射速: &e{value} RPM"
 *   ...
 * }</pre>
 */
public final class LoreConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private boolean enabled;

    /* category名 → LoreModuleDef */
    private final Map<String, LoreModuleDef> modules = new LinkedHashMap<>();

    // --- 单例 ---
    private static LoreConfig instance;

    public LoreConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        instance = this;
        loadConfig();
    }

    public static boolean hasInstance() { return instance != null; }
    public static LoreConfig instance() { return instance; }

    /* ==================== 加载 ==================== */

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "lore.yml");
        if (!file.exists()) {
            createDefaultConfig(file);
        }
        config = YamlConfiguration.loadConfiguration(file);
        enabled = config.getBoolean("enabled", true);

        // 检查默认值并合并缺失key
        InputStream defStream = plugin.getResource("lore.yml");
        if (defStream != null) {
            FileConfiguration defCfg = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defStream, StandardCharsets.UTF_8));
            boolean updated = false;
            for (String cat : new String[]{"gun", "weapon", "enchantment", "magic", "xiuxian", "magazine", "ammo"}) {
                if (!config.contains(cat)) {
                    config.set(cat, defCfg.getConfigurationSection(cat));
                    updated = true;
                }
            }
            if (updated) {
                try { config.save(file); } catch (IOException ignored) {}
            }
        }

        modules.clear();
        for (String category : new String[]{"gun", "weapon", "enchantment", "magic", "xiuxian", "magazine", "ammo"}) {
            ConfigurationSection cs = config.getConfigurationSection(category);
            if (cs == null) {
                modules.put(category, new LoreModuleDef(category));
                continue;
            }
            LoreModuleDef def = new LoreModuleDef(category);
            def.header  = cs.getString("header", "");
            def.footer  = cs.getString("footer", "");
            def.order   = cs.getStringList("order");
            // 读取每个属性的显示模板
            for (String key : cs.getKeys(false)) {
                if (key.equals("header") || key.equals("footer") || key.equals("order")) continue;
                def.templates.put(key, cs.getString(key));
            }
            modules.put(category, def);
        }
        plugin.getLogger().info("[Lore] 已加载 " + modules.size() + " 个模块模板");
    }

    /* ==================== 查询 ==================== */

    public boolean isEnabled() { return enabled; }

    /** 获取指定分类的模块定义，不存在返回空模块 */
    public LoreModuleDef getModule(String category) {
        return modules.getOrDefault(category, new LoreModuleDef(category));
    }

    /** 获取所有已注册分类 */
    public Set<String> getCategories() {
        return Collections.unmodifiableSet(modules.keySet());
    }

    public void reload() { loadConfig(); }

    /* ==================== 默认配置 ==================== */

    private void createDefaultConfig(File file) {
        FileConfiguration dc = new YamlConfiguration();

        dc.set("enabled", true);

        // --- 枪械 ---
        dc.set("gun.header", "&6═══ 枪械属性 ═══");
        dc.set("gun.order", Arrays.asList(
            // 弹药状态（动态读取PDC）
            "_mag_ammo", "_chamber_status", "_chamber_ammo", "_gun_dura",
            // 核心面板
            "gun_damage", "gun_rpm", "gun_mag_capacity",
            // 散布
            "gun_spread_min", "gun_spread_max", "gun_spread_growth", "gun_spread_recovery",
            // 后坐
            "gun_recoil_vertical", "gun_recoil_horizontal", "gun_recoil_growth",
            // 弹道
            "gun_bullet_speed", "gun_range", "gun_penetration_count",
            // 换弹
            "gun_reload_ticks", "gun_reload_empty_ticks",
            // 枪膛
            "gun_chamber_tactical_reload_bonus", "gun_chamber_bolt_ticks", "gun_chamber_auto_bolt",
            // 爆头
            "gun_headshot_chance", "gun_headshot_multiplier",
            // 过热
            "gun_heat_max", "gun_heat_per_shot", "gun_heat_cool_rate",
            "gun_heat_overheat_trigger", "gun_heat_malfunc_trigger", "gun_heat_dura_loss_max",
            // 故障
            "gun_malfunc_base_chance",
            // 人体工学
            "gun_equip_ticks", "gun_holster_ticks", "gun_sprint_to_fire_ticks",
            "gun_weapon_swap_speed", "gun_ads_in_ticks", "gun_ads_out_ticks",
            // 机动
            "gun_move_speed", "gun_sprint_speed", "gun_ads_move_speed", "gun_jump_height",
            // 开镜
            "gun_ads_sway_amount", "gun_ads_scope_type", "gun_ads_night_vision",
            "gun_ads_breath_max", "gun_ads_breath_drain", "gun_ads_breath_regen",
            // 命中特效
            "gun_hit_slow_chance", "gun_hit_slow_amount", "gun_hit_stagger_chance", "gun_hit_blind_chance",
            // 击杀连锁
            "gun_on_kill_trigger_chance", "gun_on_kill_heal", "gun_on_kill_reload_speed", "gun_on_kill_damage_bonus",
            // 弹道高级
            "gun_bullet_ricochet_chance", "gun_bullet_water_speed", "gun_bullet_glass_pierce",
            // 压制
            "gun_suppress_radius", "gun_suppress_amount",
            // 耐久（仅消耗，当前/最大由 _gun_dura 状态行替代）
            "item_dura_loss_per_use"
        ));
        // --- 弹药状态（动态） ---
        dc.set("gun._mag_ammo",        "&7弹药: &f{mag_ammo}&7/&f{mag_capacity}");
        dc.set("gun._chamber_status",  "&7枪膛状态: {chamber_status}");
        dc.set("gun._chamber_ammo",    "&7膛内弹种: &e{chamber_ammo}");
        dc.set("gun._gun_dura",        "&7耐久: &f{gun_dura}&7/&f{gun_dura_max}");
        // --- 核心面板 ---
        dc.set("gun.gun_damage",         "&7伤害:       &c{value}");
        dc.set("gun.gun_rpm",            "&7射速:       &e{value} RPM");
        dc.set("gun.gun_mag_capacity",    "&7弹匣:       &d{value}发");
        // --- 散布 ---
        dc.set("gun.gun_spread_min",     "&7最小散布:   &b{value}°");
        dc.set("gun.gun_spread_max",     "&7最大散布:   &b{value}°");
        dc.set("gun.gun_spread_growth",  "&7散布增长:   &b+{value}°/发");
        dc.set("gun.gun_spread_recovery","&7散布恢复:   &b{value}°/秒");
        // --- 后坐 ---
        dc.set("gun.gun_recoil_vertical","&7垂直后坐:   &a{value}°");
        dc.set("gun.gun_recoil_horizontal","&7水平后坐:  &a±{value}°");
        dc.set("gun.gun_recoil_growth",  "&7后坐增长:   &a+{value}°/发");
        // --- 弹道 ---
        dc.set("gun.gun_bullet_speed",   "&7弹速:       &6{value} m/s");
        dc.set("gun.gun_range",          "&7射程:       &6{value}格");
        dc.set("gun.gun_penetration_count","&7穿透:       &a{value}体");
        // --- 换弹 ---
        dc.set("gun.gun_reload_ticks","&7换弹:       &f{value}tick");
        dc.set("gun.gun_reload_empty_ticks","&7空仓换弹: &f{value}tick");
        // --- 枪膛 ---
        dc.set("gun.gun_chamber_tactical_reload_bonus","&7战术换弹加成: &a+{value}");
        dc.set("gun.gun_chamber_bolt_ticks","&7拉栓时间: &7{value}tick");
        dc.set("gun.gun_chamber_auto_bolt","&7自动拉栓: &a{value}");
        // --- 爆头 ---
        dc.set("gun.gun_headshot_chance", "&7爆头率:     &e{value}");
        dc.set("gun.gun_headshot_multiplier",   "&7爆头倍率:   &e{value}");
        // --- 过热 ---
        dc.set("gun.gun_heat_max",       "&7最大热量:   &c{value}");
        dc.set("gun.gun_heat_per_shot",  "&7单发热量:   &c{value}");
        dc.set("gun.gun_heat_cool_rate",  "&7冷却速率:   &b{value}/秒");
        dc.set("gun.gun_heat_overheat_trigger","&7过热触发:   &e{value}");
        dc.set("gun.gun_heat_malfunc_trigger","&7故障触发:   &e{value}");
        dc.set("gun.gun_heat_dura_loss_max","&7热量损耐:   &7{value}");
        // --- 故障 ---
        dc.set("gun.gun_malfunc_base_chance","&7故障率:    &c{value}");
        // --- 人体工学 ---
        dc.set("gun.gun_equip_ticks","&7切枪耗时:   &7{value}tick");
        dc.set("gun.gun_holster_ticks","&7收枪耗时:  &7{value}tick");
        dc.set("gun.gun_sprint_to_fire_ticks","&7疾跑→开火: &7{value}tick");
        dc.set("gun.gun_weapon_swap_speed","&7切枪速度:   &7{value}");
        dc.set("gun.gun_ads_in_ticks","&7开镜渐入:   &7{value}tick");
        dc.set("gun.gun_ads_out_ticks","&7关镜渐出:   &7{value}tick");
        // --- 机动 ---
        dc.set("gun.gun_move_speed",       "&7持枪移速:   &b{value}");
        dc.set("gun.gun_sprint_speed",     "&7持枪疾跑:   &b{value}");
        dc.set("gun.gun_ads_move_speed",   "&7开镜移速:   &b{value}");
        dc.set("gun.gun_jump_height",      "&7持枪跳跃:   &b{value}");
        // --- 开镜 ---
        dc.set("gun.gun_ads_sway_amount",  "&7开镜晃动:   &e{value}");
        dc.set("gun.gun_ads_scope_type",   "&7瞄具类型:   &d{value}");
        dc.set("gun.gun_ads_night_vision", "&7开镜夜视:   &5{value}");
        dc.set("gun.gun_ads_breath_max",   "&7呼吸上限:   &b{value}");
        dc.set("gun.gun_ads_breath_drain", "&7屏息消耗:   &c{value}/tick");
        dc.set("gun.gun_ads_breath_regen", "&7呼吸恢复:   &a{value}/tick");
        // --- 命中特效 ---
        dc.set("gun.gun_hit_slow_chance",  "&7命中减速:   &d{value}");
        dc.set("gun.gun_hit_slow_amount",  "&7减速幅度:   &d{value}");
        dc.set("gun.gun_hit_stagger_chance","&7命中硬直:   &e{value}");
        dc.set("gun.gun_hit_blind_chance",  "&7命中致盲:   &5{value}");
        // --- 击杀连锁 ---
        dc.set("gun.gun_on_kill_trigger_chance","&7击杀触发概率: &e{value}");
        dc.set("gun.gun_on_kill_heal",     "&7击杀回血:   &c+{value}");
        dc.set("gun.gun_on_kill_reload_speed","&7击杀换弹:  &a+{value}");
        dc.set("gun.gun_on_kill_damage_bonus","&7击杀增伤:  &c+{value}");
        // --- 弹道高级 ---
        dc.set("gun.gun_bullet_ricochet_chance","&7跳弹率:   &e{value}");
        dc.set("gun.gun_bullet_water_speed","&7水中弹速:   &b{value}");
        dc.set("gun.gun_bullet_glass_pierce","&7玻璃穿透:   &a{value}");
        // --- 压制 ---
        dc.set("gun.gun_suppress_radius",   "&7压制范围:   &e{value}格");
        dc.set("gun.gun_suppress_amount",   "&7压制强度:   &c{value}");
        // --- 耐久 ---
        dc.set("gun.item_dura_loss_per_use","&7耐久消耗:   &7{value}/次");
        // gun_dura_max 已整合到状态行 _gun_dura 中，不再单独显示

        // --- 武器 / RPG ---
        dc.set("weapon.header", "&c═══ 武器属性 ═══");
        dc.set("weapon.order", Arrays.asList(
            // 伤害
            "melee_damage", "melee_bonus", "projectile_damage", "projectile_bonus",
            "damage", "damage_bonus",
            // 暴击
            "critical_chance", "critical_multiplier",
            // 吸血
            "lifesteal_chance", "lifesteal_multiplier", "lifesteal_flat", "lifesteal_drain",
            // 攻速
            "attack_speed",
            // 穿透
            "low_penetration", "high_penetration", "penetration_efficiency",
            "armor_toughness",
            // 破甲
            "armor_break_chance", "armor_break_shallow_pct", "armor_break_medium_pct",
            "armor_break_deep_pct", "armor_break_ticks",
            // 闪避/命中
            "dodge", "hit",
            // 致盲
            "blind_chance", "blind_efficiency", "blind_ticks",
            // 基础属性
            "health_bonus", "defense", "movement_speed", "health_regen", "tenacity"
        ));
        // --- 伤害 ---
        dc.set("weapon.melee_damage",       "&7近战伤害:   &c+{value}");
        dc.set("weapon.melee_bonus",        "&7近战加成:   &c{value}");
        dc.set("weapon.projectile_damage",  "&7射弹伤害:   &c+{value}");
        dc.set("weapon.projectile_bonus",   "&7射弹加成:   &c{value}");
        dc.set("weapon.damage",             "&7通用伤害:   &c+{value}");
        dc.set("weapon.damage_bonus",       "&7伤害加成:   &c{value}");
        // --- 暴击 ---
        dc.set("weapon.critical_chance",    "&7暴击率:     &e{value}");
        dc.set("weapon.critical_multiplier","&7暴击倍率:   &e{value}");
        // --- 吸血 ---
        dc.set("weapon.lifesteal_chance",   "&7吸血概率:   &d{value}");
        dc.set("weapon.lifesteal_multiplier","&7吸血倍率:   &d{value}");
        dc.set("weapon.lifesteal_flat",     "&7固定吸血:   &d+{value}");
        dc.set("weapon.lifesteal_drain",    "&7汲取伤害:   &d+{value}");
        // --- 攻速 ---
        dc.set("weapon.attack_speed",       "&7攻击速度:   &b{value}");
        // --- 穿透 ---
        dc.set("weapon.low_penetration",    "&7低穿(无视): &a{value}");
        dc.set("weapon.high_penetration",   "&7高穿(破韧): &a+{value}");
        dc.set("weapon.penetration_efficiency","&7穿透效能:  &a{value}");
        dc.set("weapon.armor_toughness",    "&7护甲韧性:   &6{value}");
        // --- 破甲 ---
        dc.set("weapon.armor_break_chance", "&7破甲概率:   &c{value}");
        dc.set("weapon.armor_break_shallow_pct","&7破甲浅度:  &7{value}");
        dc.set("weapon.armor_break_medium_pct","&7破甲中度:   &7{value}");
        dc.set("weapon.armor_break_deep_pct","&7破甲深度:   &c{value}");
        dc.set("weapon.armor_break_ticks",  "&7破甲时间:   &f{value}tick");
        // --- 闪避/命中 ---
        dc.set("weapon.dodge",              "&7闪避率:     &b{value}");
        dc.set("weapon.hit",                "&7命中率:     &e{value}");
        // --- 致盲 ---
        dc.set("weapon.blind_chance",       "&7致盲概率:   &5{value}");
        dc.set("weapon.blind_efficiency",   "&7致盲效能:   &5{value}");
        dc.set("weapon.blind_ticks",        "&7致盲时间:   &f{value}tick");
        // --- 基础属性 ---
        dc.set("weapon.health_bonus",       "&7生命加成:   &c+{value}");
        dc.set("weapon.defense",            "&7防御力:     &a{value}");
        dc.set("weapon.movement_speed",     "&7移动速度:   &b{value}");
        dc.set("weapon.health_regen",       "&7生命回复:   &c+{value}/秒");
        dc.set("weapon.tenacity",           "&7韧性:       &6{value}");

        // --- 附魔 ---
        dc.set("enchantment.order", new ArrayList<String>());
        dc.set("enchantment.header", "&5═══ 附魔属性 ═══");
        // 留空，由用户在配置中自行定义

        // --- 魔法 ---
        dc.set("magic.order", new ArrayList<String>());
        dc.set("magic.header", "&9═══ 魔法属性 ═══");

        // --- 修仙 ---
        dc.set("xiuxian.order", new ArrayList<String>());
        dc.set("xiuxian.header", "&d═══ 修仙属性 ═══");

        // --- 弹匣 ---
        dc.set("magazine.header", "&3═══ 弹匣信息 ═══");
        dc.set("magazine.order", Arrays.asList("caliber", "capacity", "usage"));
        dc.set("magazine.caliber", "&7口径: &b{caliber}");
        dc.set("magazine.capacity", "&7容量: &b{capacity}发");
        dc.set("magazine.usage",   "&7右键打开 → 装弹");

        // --- 弹药 ---
        dc.set("ammo.header", "&e═══ 弹种信息 ═══");
        dc.set("ammo.order", Arrays.asList("caliber", "type", "damage", "penetration", "spread", "recoil", "speed"));
        dc.set("ammo.caliber",     "&7口径: &b{caliber}");
        dc.set("ammo.type",        "&7弹种: &e{ammo_type}");
        dc.set("ammo.damage",      "&7伤害: &c{damage_mult}%");
        dc.set("ammo.penetration", "&7穿透: &a{penetration_bonus}");
        dc.set("ammo.spread",      "&7散布: &b{spread_mult}%");
        dc.set("ammo.recoil",      "&7后坐: &d{recoil_mult}%");
        dc.set("ammo.speed",       "&7弹速: &e{speed_mult}%");

        try { dc.save(file); }
        catch (IOException e) { plugin.getLogger().log(Level.WARNING, "无法创建 lore.yml", e); }
    }

    /* ==================== 数据类 ==================== */

    /** 单个分类的Lore模板定义 */
    public static class LoreModuleDef {
        public final String category;
        public String header = "";
        public String footer = "";
        /** 属性显示顺序（key列表） */
        public List<String> order = new ArrayList<>();
        /** key → 显示模板（如 "&7后坐力: &c{value}"） */
        public final Map<String, String> templates = new LinkedHashMap<>();

        public LoreModuleDef(String category) { this.category = category; }

        /** 获取某个key的模板，未定义返回 null */
        public String getTemplate(String key) {
            return templates.get(key);
        }
    }
}
