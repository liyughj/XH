package io.github.liyughj.xH.rpg.Attribute;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 物品 RPG 属性模板配置（rpg_items.yml）。
 * <p>
 * 按 Material 定义物品的默认属性模板，支持单值和区间。
 * <pre>
 *   items:
 *     DIAMOND_SWORD:
 *       melee_damage: 20~40        # 区间：每次攻击随机取 20~40
 *       melee_bonus: 80%~120%      # 区间百分比
 *       damage: 10~20
 *       damage_bonus: 50%
 *       critical_chance: 15
 *     BOW:
 *       projectile_damage: 30~50
 *       projectile_bonus: 60%~100%
 * </pre>
 */
public class ItemAttributeConfig {

    private static final String CONFIG_FILE_NAME = "rpg_items.yml";

    private static final boolean DEFAULT_ENABLED = true;

    private final JavaPlugin plugin;
    private FileConfiguration config;

    private boolean enabled;

    /** Material → 属性区间映射 */
    private final Map<Material, Map<RpgAttribute, AttributeRange>> materialTemplates = new EnumMap<>(Material.class);

    /** 材质默认护甲韧性（Material → 百分比，如 DIAMOND_CHESTPLATE → 5.0 表示 5%） */
    private static final Map<Material, Double> armorToughnessDefaults = new EnumMap<>(Material.class);

    /** 护甲韧性扩展源列表（由外部模块注册） */
    private static final List<ArmorToughnessSource> toughnessSources = new ArrayList<>();

    /** 护甲韧性扩展接口（预留，方便后续添加自定义材质韧性） */
    public interface ArmorToughnessSource {
        /** 返回材质的基础韧性百分比，返回 null 表示不干预 */
        Double getToughness(Material material);
    }

    /** 注册护甲韧性源 */
    public static void registerToughnessSource(ArmorToughnessSource source) {
        toughnessSources.add(source);
    }

    /**
     * 获取指定材质的护甲韧性百分比。
     * 顺序：扩展源 → 配置文件材质默认 → 0。
     */
    public static double getArmorToughness(Material material) {
        for (ArmorToughnessSource src : toughnessSources) {
            Double t = src.getToughness(material);
            if (t != null) return Math.max(0, Math.min(100, t));
        }
        return armorToughnessDefaults.getOrDefault(material, 0.0);
    }

    public ItemAttributeConfig(JavaPlugin plugin) {
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

        /* 暴击全局配置 */
        double critDefaultVal = config.getDouble("crit-default", 50.0);
        String critModeStr = config.getString("crit-mode", "ADD");
        AttributeCalculator.CritMode mode;
        try {
            mode = AttributeCalculator.CritMode.valueOf(critModeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("无效的 crit-mode: " + critModeStr + "，已回退为 ADD");
            mode = AttributeCalculator.CritMode.ADD;
        }
        AttributeCalculator.setCritConfig(critDefaultVal, mode);

        /* 吸血全局配置 */
        double lsDefaultVal = config.getDouble("lifesteal-default", 50.0);
        String lsModeStr = config.getString("lifesteal-mode", "ADD");
        AttributeCalculator.LifestealMode lsMode;
        try {
            lsMode = AttributeCalculator.LifestealMode.valueOf(lsModeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("无效的 lifesteal-mode: " + lsModeStr + "，已回退为 ADD");
            lsMode = AttributeCalculator.LifestealMode.ADD;
        }
        AttributeCalculator.setLifestealConfig(lsDefaultVal, lsMode);

        /* 护甲韧性材质默认配置 */
        armorToughnessDefaults.clear();
        ConfigurationSection toughnessSection = config.getConfigurationSection("armor-toughness");
        if (toughnessSection != null) {
            for (String key : toughnessSection.getKeys(false)) {
                Material mat = Material.getMaterial(key);
                if (mat == null) {
                    plugin.getLogger().warning(CONFIG_FILE_NAME + ": armor-toughness 下无效材质 \"" + key + "\"");
                    continue;
                }
                double val = toughnessSection.getDouble(key);
                armorToughnessDefaults.put(mat, Math.max(0, Math.min(100, val)));
            }
        }
        if (armorToughnessDefaults.isEmpty()) {
            /* 默认值 */
            armorToughnessDefaults.put(Material.DIAMOND_HELMET, 5.0);
            armorToughnessDefaults.put(Material.DIAMOND_CHESTPLATE, 5.0);
            armorToughnessDefaults.put(Material.DIAMOND_LEGGINGS, 5.0);
            armorToughnessDefaults.put(Material.DIAMOND_BOOTS, 5.0);
            armorToughnessDefaults.put(Material.NETHERITE_HELMET, 10.0);
            armorToughnessDefaults.put(Material.NETHERITE_CHESTPLATE, 10.0);
            armorToughnessDefaults.put(Material.NETHERITE_LEGGINGS, 10.0);
            armorToughnessDefaults.put(Material.NETHERITE_BOOTS, 10.0);
            armorToughnessDefaults.put(Material.IRON_HELMET, 2.0);
            armorToughnessDefaults.put(Material.IRON_CHESTPLATE, 2.0);
            armorToughnessDefaults.put(Material.IRON_LEGGINGS, 2.0);
            armorToughnessDefaults.put(Material.IRON_BOOTS, 2.0);
        }

        materialTemplates.clear();
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String matName : itemsSection.getKeys(false)) {
                Material material = Material.getMaterial(matName);
                if (material == null) {
                    plugin.getLogger().warning(CONFIG_FILE_NAME + ": 无效的 Material \"" + matName + "\"");
                    continue;
                }
                ConfigurationSection attrSection = itemsSection.getConfigurationSection(matName);
                if (attrSection == null) continue;

                Map<RpgAttribute, AttributeRange> attrs = new LinkedHashMap<>();
                for (String attrKey : attrSection.getKeys(false)) {
                    RpgAttribute attr = RpgAttribute.fromKey(attrKey);
                    if (attr == null) {
                        plugin.getLogger().warning(CONFIG_FILE_NAME + ": 未知属性 \"" + attrKey + "\" (物品 " + matName + ")");
                        continue;
                    }
                    /* 解析：尝试字符串区间格式 "20~40"，也兼容纯数字 */
                    AttributeRange range = parseConfigValue(attrSection, attrKey, attr);
                    attrs.put(attr, range);
                }
                if (!attrs.isEmpty()) {
                    materialTemplates.put(material, attrs);
                }
            }
        }

        plugin.getLogger().info("已加载 " + materialTemplates.size() + " 个物品属性模板");
    }

    /**
     * 解析配置中的属性值。优先尝试字符串区间格式，fallback 到纯数字。
     */
    private AttributeRange parseConfigValue(ConfigurationSection section, String key, RpgAttribute attr) {
        /* 先尝试作为字符串解析（支持 "20~40"、"80%~120%"） */
        Object raw = section.get(key);
        if (raw instanceof String) {
            return AttributeRange.parse((String) raw, attr);
        }
        /* 纯数字回退 */
        if (raw instanceof Number) {
            double v = ((Number) raw).doubleValue();
            return AttributeRange.of(attr.clamp(v));
        }
        return AttributeRange.of(attr.getDefaultValue());
    }

    private void createDefaultConfig(File file) {
        FileConfiguration dc = new YamlConfiguration();

        dc.options().setHeader(Arrays.asList(
            "RPG 物品属性模板配置",
            "",
            "按 Material 定义物品的默认 RPG 属性。",
            "支持单值或区间（min~max），每次攻击时在区间内随机取值。",
            "",
            "=== 暴击全局配置 ===",
            "  crit-default: 默认暴击额外伤害百分比（叠加 critical_multiplier 使用）",
            "  crit-mode: 暴击倍率计算模式，ADD=加法 MULTIPLY=乘法",
            "    ADD:      最终暴击 = crit-default + critical_multiplier",
            "    MULTIPLY: 最终暴击 = crit-default + crit-default × critical_multiplier",
            "",
            "=== 吸血全局配置 ===",
            "  lifesteal-default: 默认偷取百分比（叠加 lifesteal_multiplier 使用）",
            "  lifesteal-mode: 吸血倍率计算模式，ADD=加法 MULTIPLY=乘法",
            "    ADD:      偷取率 = lifesteal-default + lifesteal_multiplier",
            "    MULTIPLY: 偷取率 = lifesteal-default + lifesteal-default × lifesteal_multiplier",
            "  三机制共享 lifesteal_chance，各自独立 roll：",
            "    偷取:     回血 = 最终伤害 × 偷取率%（受倍率影响）",
            "    固定吸血:  回血 = lifesteal_flat（不受伤害/倍率影响）",
            "    汲取:     额外伤害 = min(drain, 目标剩余血) × (1+倍率%)，回血同值",
            "",
            "=== 护甲韧性配置 ===",
            "  按护甲材质配置默认韧性百分比，每件护甲独立生效。",
            "  物品上的 armor_toughness 属性与材质默认值叠加：",
            "    最终韧性 = 材质默认值 + 物品 armor_toughness%",
            "  韧性免疫对应百分比的低穿（高穿无视韧性）。",
            "  示例：",
            "    armor-toughness:",
            "      DIAMOND_CHESTPLATE: 5    # 钻石胸甲默认 5% 韧性",
            "      NETHERITE_HELMET: 10     # 下界合金头盔默认 10% 韧性",
            "      IRON_BOOTS: 2            # 铁靴默认 2% 韧性",
            "  可通过 ArmorToughnessSource 接口扩展自定义材质。",
            "",
            "=== 属性 key 列表（见 RpgAttribute 枚举） ===",
            "",
            "  伤害类（6个）：",
            "    melee_damage       - 近战伤害（绝对值），仅近战生效",
            "    melee_bonus        - 近战加成（%），乘 melee_damage",
            "    projectile_damage  - 射弹伤害（绝对值），仅远程生效",
            "    projectile_bonus   - 射弹加成（%），乘 projectile_damage",
            "    damage             - 伤害（绝对值），同时加到近战+远程",
            "    damage_bonus       - 伤害加成（%），乘 damage",
            "",
            "  暴击类：",
            "    critical_chance    - 暴击率（%）",
            "    critical_multiplier- 暴击倍率（%），与 crit-default 叠加",
            "    critical_correction- 暴击修正（%）预留，暂未实现",
            "",
            "  吸血类（新增）：",
            "    lifesteal_chance   - 吸血概率（%），三机制共用",
            "    lifesteal_multiplier- 吸血倍率（%），影响偷取+汲取",
            "    lifesteal_flat     - 固定吸血（绝对值），恢复固定血量",
            "    lifesteal_drain    - 汲取（绝对值），额外伤害+回血",
            "",
            "  穿透类：",
            "    low_penetration      - 低穿（%），无视护甲减免%",
            "    high_penetration     - 高穿（绝对值），忽略护甲韧性点",
            "    penetration_efficiency - 穿透效能（%），增幅高低穿",
            "    armor_toughness      - 护甲韧性（%），抵抗低穿",
            "",
            "  生存类：",
            "    health_bonus       - 生命加成（绝对值）",
            "    defense            - 防御（%）",
            "    attack_speed       - 攻击速度（%）",
            "    movement_speed     - 移动速度（%）",
            "    health_regen       - 生命回复（绝对值）",
            "    tenacity           - 韧性（%）",
            "    dodge              - 闪避（%）",
            "",
            "=== 伤害计算公式（每次攻击） ===",
            "  近战 = melee_damage×(1+melee_bonus%) + damage×(1+damage_bonus%)",
            "  射弹 = projectile_damage×(1+projectile_bonus%) + damage×(1+damage_bonus%)",
            "  暴击 = (近战/射弹 + 基础) × (1 + 最终暴击率)",
            "  附魔加成在此基础上叠加",
            "",
            "=== 写法说明 ===",
            "  单值:      melee_damage: 20         → 每次都是 20",
            "  区间:      melee_damage: 20~40      → 每次随机 20~40",
            "  百分比单值: melee_bonus: 80%        → 每次都是 80%",
            "  百分比区间: melee_bonus: 80%~120%   → 每次随机 80%~120%",
            ""
        ));

        dc.set("enabled", DEFAULT_ENABLED);
        dc.set("crit-default", 50.0);
        dc.set("crit-mode", "ADD");
        dc.set("lifesteal-default", 50.0);
        dc.set("lifesteal-mode", "ADD");

        /* 护甲韧性材质默认配置 */
        dc.set("armor-toughness.DIAMOND_HELMET", 5);
        dc.set("armor-toughness.DIAMOND_CHESTPLATE", 5);
        dc.set("armor-toughness.DIAMOND_LEGGINGS", 5);
        dc.set("armor-toughness.DIAMOND_BOOTS", 5);
        dc.set("armor-toughness.NETHERITE_HELMET", 10);
        dc.set("armor-toughness.NETHERITE_CHESTPLATE", 10);
        dc.set("armor-toughness.NETHERITE_LEGGINGS", 10);
        dc.set("armor-toughness.NETHERITE_BOOTS", 10);
        dc.set("armor-toughness.IRON_HELMET", 2);
        dc.set("armor-toughness.IRON_CHESTPLATE", 2);
        dc.set("armor-toughness.IRON_LEGGINGS", 2);
        dc.set("armor-toughness.IRON_BOOTS", 2);

        /* 预设示例 */
        dc.set("items.DIAMOND_SWORD.melee_damage", "10~20");
        dc.set("items.DIAMOND_SWORD.melee_bonus", "50%~100%");
        dc.set("items.DIAMOND_SWORD.damage", 5);
        dc.set("items.DIAMOND_SWORD.damage_bonus", "50%");
        dc.set("items.DIAMOND_SWORD.critical_chance", 10);
        dc.set("items.DIAMOND_SWORD.critical_multiplier", "10%~30%");
        dc.set("items.DIAMOND_SWORD.lifesteal_chance", "5~10");
        dc.set("items.DIAMOND_SWORD.lifesteal_multiplier", "10%~20%");
        dc.set("items.DIAMOND_SWORD.lifesteal_flat", "2~4");

        dc.set("items.NETHERITE_SWORD.melee_damage", "20~40");
        dc.set("items.NETHERITE_SWORD.melee_bonus", "80%~120%");
        dc.set("items.NETHERITE_SWORD.damage", "10~20");
        dc.set("items.NETHERITE_SWORD.damage_bonus", "80%~100%");
        dc.set("items.NETHERITE_SWORD.critical_chance", "15~20");
        dc.set("items.NETHERITE_SWORD.critical_multiplier", "20%~40%");
        dc.set("items.NETHERITE_SWORD.lifesteal_chance", "10~15");
        dc.set("items.NETHERITE_SWORD.lifesteal_multiplier", "20%~30%");
        dc.set("items.NETHERITE_SWORD.lifesteal_drain", "10~20");
        dc.set("items.NETHERITE_SWORD.health_bonus", 2);

        dc.set("items.BOW.projectile_damage", "20~30");
        dc.set("items.BOW.projectile_bonus", "50%~80%");
        dc.set("items.BOW.damage", "5~10");

        dc.set("items.CROSSBOW.projectile_damage", "30~50");
        dc.set("items.CROSSBOW.projectile_bonus", "60%~100%");
        dc.set("items.CROSSBOW.damage", "10~15");

        try {
            dc.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("无法创建默认 rpg_items.yml: " + e.getMessage());
        }
    }

    /* ==================== 查询方法 ==================== */

    public boolean isEnabled() { return enabled; }

    /**
     * 获取指定 Material 的属性区间模板。
     * 返回空 Map 表示无配置。
     */
    public Map<RpgAttribute, AttributeRange> getAttributeRanges(Material material) {
        Map<RpgAttribute, AttributeRange> template = materialTemplates.get(material);
        return template != null ? Collections.unmodifiableMap(template) : Collections.emptyMap();
    }

    /**
     * 检查某个 Material 是否有配置。
     */
    public boolean hasAttributes(Material material) {
        return materialTemplates.containsKey(material);
    }

    /**
     * 获取所有已配置的 Material。
     */
    public Set<Material> getConfiguredMaterials() {
        return Collections.unmodifiableSet(materialTemplates.keySet());
    }

    /** 重载配置 */
    public void reload() { loadConfig(); }

    /** 获取原始 FileConfiguration */
    public FileConfiguration getConfig() { return config; }

    /* ==================== 预留：职业/玩家属性源接口 ==================== */

    /**
     * 玩家属性来源接口（预留）。
     * <p>
     * 未来职业模块实现此接口，提供玩家自身的属性值。
     * 例如：战士职业 +100%近战加成、+5生命；法师职业 +200%法术强度。
     * <p>
     * <b>当前未使用，由职业模块实现。</b>
     */
    public interface PlayerAttributeSource {
        double getAttribute(org.bukkit.entity.Player player, RpgAttribute attr);
        Map<RpgAttribute, Double> getAllAttributes(org.bukkit.entity.Player player);
    }
}
