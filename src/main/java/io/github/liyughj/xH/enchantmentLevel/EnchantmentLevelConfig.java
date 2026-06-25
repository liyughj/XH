package io.github.liyughj.xH.enchantmentLevel;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 附魔升级系统配置管理类
 * 管理等级公式、经验来源、稀有度倍率、维度倍率、显示格式等配置
 * 使用独立的 enchantmentLevel.yml 文件
 */
public class EnchantmentLevelConfig {

    /* ==================== 默认值 ==================== */

    private static final boolean DEFAULT_ENABLED = true;
    private static final String DEFAULT_FORMULA = "EXPONENTIAL";
    private static final int DEFAULT_BASE_EXP = 100;
    private static final double DEFAULT_MULTIPLIER = 2.0;
    private static final int DEFAULT_INCREMENT = 50;
    private static final int DEFAULT_EXP_BAR_LENGTH = 10;
    private static final boolean DEFAULT_AUTO_INIT = true;
    private static final boolean DEFAULT_EFFECTS_ENABLED = true;

    /* 经验来源默认值 */
    private static final double DEFAULT_GLOBAL_MULTIPLIER = 1.0;
    private static final int DEFAULT_TOOL_BASE_EXP = 1;
    private static final int DEFAULT_MELEE_BASE_EXP = 3;
    private static final int DEFAULT_RANGED_BASE_EXP = 3;
    private static final int DEFAULT_ARMOR_BASE_EXP = 1;

    /* 区块稀有度默认值（高于1.0才需配置） */
    private static final Map<Material, Double> DEFAULT_BLOCK_RARITY = new LinkedHashMap<>();
    /* 实体稀有度默认值（高于1.0才需配置） */
    private static final Map<EntityType, Double> DEFAULT_ENTITY_RARITY = new LinkedHashMap<>();
    /* 维度倍率默认值 */
    private static final Map<String, Double> DEFAULT_DIMENSION_MULTIPLIERS = new LinkedHashMap<>();

    static {
        /* 方块稀有度：越稀有越珍贵，基值 × 倍率 */
        DEFAULT_BLOCK_RARITY.put(Material.COAL_ORE, 2.0);
        DEFAULT_BLOCK_RARITY.put(Material.DEEPSLATE_COAL_ORE, 2.0);
        DEFAULT_BLOCK_RARITY.put(Material.COPPER_ORE, 3.0);
        DEFAULT_BLOCK_RARITY.put(Material.DEEPSLATE_COPPER_ORE, 3.0);
        DEFAULT_BLOCK_RARITY.put(Material.IRON_ORE, 3.0);
        DEFAULT_BLOCK_RARITY.put(Material.DEEPSLATE_IRON_ORE, 3.0);
        DEFAULT_BLOCK_RARITY.put(Material.LAPIS_ORE, 3.0);
        DEFAULT_BLOCK_RARITY.put(Material.DEEPSLATE_LAPIS_ORE, 3.0);
        DEFAULT_BLOCK_RARITY.put(Material.REDSTONE_ORE, 4.0);
        DEFAULT_BLOCK_RARITY.put(Material.DEEPSLATE_REDSTONE_ORE, 4.0);
        DEFAULT_BLOCK_RARITY.put(Material.GOLD_ORE, 4.0);
        DEFAULT_BLOCK_RARITY.put(Material.DEEPSLATE_GOLD_ORE, 4.0);
        DEFAULT_BLOCK_RARITY.put(Material.NETHER_GOLD_ORE, 3.0);
        DEFAULT_BLOCK_RARITY.put(Material.NETHER_QUARTZ_ORE, 2.0);
        DEFAULT_BLOCK_RARITY.put(Material.DIAMOND_ORE, 6.0);
        DEFAULT_BLOCK_RARITY.put(Material.DEEPSLATE_DIAMOND_ORE, 6.0);
        DEFAULT_BLOCK_RARITY.put(Material.EMERALD_ORE, 6.0);
        DEFAULT_BLOCK_RARITY.put(Material.DEEPSLATE_EMERALD_ORE, 6.0);
        DEFAULT_BLOCK_RARITY.put(Material.ANCIENT_DEBRIS, 10.0);

        /* 生物稀有度：越强越稀有越珍贵 */
        DEFAULT_ENTITY_RARITY.put(EntityType.ENDERMAN, 1.67);
        DEFAULT_ENTITY_RARITY.put(EntityType.WITCH, 1.5);
        DEFAULT_ENTITY_RARITY.put(EntityType.SLIME, 1.33);
        DEFAULT_ENTITY_RARITY.put(EntityType.MAGMA_CUBE, 1.33);
        DEFAULT_ENTITY_RARITY.put(EntityType.BLAZE, 2.0);
        DEFAULT_ENTITY_RARITY.put(EntityType.GHAST, 2.0);
        DEFAULT_ENTITY_RARITY.put(EntityType.WITHER_SKELETON, 2.5);
        DEFAULT_ENTITY_RARITY.put(EntityType.GUARDIAN, 2.0);
        DEFAULT_ENTITY_RARITY.put(EntityType.ELDER_GUARDIAN, 3.5);
        DEFAULT_ENTITY_RARITY.put(EntityType.SHULKER, 2.0);
        DEFAULT_ENTITY_RARITY.put(EntityType.PHANTOM, 2.0);
        DEFAULT_ENTITY_RARITY.put(EntityType.DROWNED, 1.33);
        DEFAULT_ENTITY_RARITY.put(EntityType.HUSK, 1.33);
        DEFAULT_ENTITY_RARITY.put(EntityType.STRAY, 1.33);
        DEFAULT_ENTITY_RARITY.put(EntityType.CREEPER, 1.5);
        DEFAULT_ENTITY_RARITY.put(EntityType.HOGLIN, 2.0);
        DEFAULT_ENTITY_RARITY.put(EntityType.ZOGLIN, 2.0);
        DEFAULT_ENTITY_RARITY.put(EntityType.PIGLIN, 1.33);
        DEFAULT_ENTITY_RARITY.put(EntityType.PIGLIN_BRUTE, 3.0);
        DEFAULT_ENTITY_RARITY.put(EntityType.ZOMBIFIED_PIGLIN, 1.33);
        DEFAULT_ENTITY_RARITY.put(EntityType.EVOKER, 3.5);
        DEFAULT_ENTITY_RARITY.put(EntityType.VINDICATOR, 2.5);
        DEFAULT_ENTITY_RARITY.put(EntityType.PILLAGER, 1.5);
        DEFAULT_ENTITY_RARITY.put(EntityType.RAVAGER, 3.5);
        DEFAULT_ENTITY_RARITY.put(EntityType.WARDEN, 7.0);
        DEFAULT_ENTITY_RARITY.put(EntityType.WITHER, 10.0);
        DEFAULT_ENTITY_RARITY.put(EntityType.ENDER_DRAGON, 15.0);

        /* 维度倍率：越危险的维度越激励探索 */
        DEFAULT_DIMENSION_MULTIPLIERS.put("minecraft:overworld", 1.0);
        DEFAULT_DIMENSION_MULTIPLIERS.put("minecraft:the_nether", 1.5);
        DEFAULT_DIMENSION_MULTIPLIERS.put("minecraft:the_end", 2.0);
    }

    /* 默认经验条渐变色 */
    private static final List<String> DEFAULT_BAR_GRADIENT = Arrays.asList(
        "#FF5555", "#FFAA00", "#FFFF55", "#55FF55"
    );

    private static final String DEFAULT_BAR_EMPTY_COLOR = "#333333";

    private static final List<String[]> DEFAULT_TEXT_COLOR_TIERS = Arrays.asList(
        new String[]{"0.0", "#FF5555"},
        new String[]{"0.34", "#FFAA00"},
        new String[]{"0.66", "#FFFF55"},
        new String[]{"1.0", "#55FF55"}
    );

    /* ==================== 附魔类别定义 ==================== */

    /**
     * 附魔经验成长类别
     * 每个附魔属于一种类别，只有对应行为才给经验
     */
    public enum ExpCategory {
        /** 近战武器 — 攻击实体时获得经验 */
        WEAPON,
        /** 工具 — 挖掘方块时获得经验 */
        TOOL,
        /** 护甲 — 受到伤害时获得经验 */
        ARMOR,
        /** 弓箭/弩 — 远程命中时获得经验 */
        BOW,
        /** 三叉戟 — 近战/投掷命中时获得经验 */
        TRIDENT,
        /** 通用 — 任何使用方式都获得经验（如耐久） */
        UNIVERSAL
    }

    /* ==================== 配置键名 ==================== */

    private static final String CONFIG_FILE_NAME = "enchantmentLevel.yml";

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LEVEL_FORMULA = "level-formula";
    private static final String KEY_FORMULA = "formula";
    private static final String KEY_BASE_EXP = "base-exp";
    private static final String KEY_MULTIPLIER = "multiplier";
    private static final String KEY_INCREMENT = "increment";
    private static final String KEY_MAX_LEVELS = "max-levels";
    private static final String KEY_EXP_SOURCES = "exp-sources";
    private static final String KEY_GLOBAL_MULTIPLIER = "global-multiplier";
    private static final String KEY_SOURCE_ENABLED = "enabled";
    private static final String KEY_BASE_EXP_PER_ACTION = "base-exp-per-action";
    private static final String KEY_BLOCK_RARITY = "block-rarity-multipliers";
    private static final String KEY_ENTITY_RARITY = "entity-rarity-multipliers";
    private static final String KEY_DIMENSION_MULTIPLIER = "dimension-multipliers";
    private static final String KEY_DISPLAY = "display";
    private static final String KEY_FORMAT = "format";
    private static final String KEY_EXP_BAR_LENGTH = "exp-bar-length";
    private static final String KEY_AUTO_INITIALIZE = "auto-initialize";
    private static final String KEY_EXP_BAR_COLORS = "exp-bar-colors";
    private static final String KEY_BAR_GRADIENT = "bar-gradient";
    private static final String KEY_BAR_EMPTY_COLOR = "bar-empty-color";
    private static final String KEY_TEXT_COLOR_TIERS = "text-color-tiers";
    private static final String KEY_THRESHOLD = "threshold";
    private static final String KEY_COLOR = "color";
    private static final String KEY_UPGRADE_EFFECTS = "upgrade-effects";
    private static final String KEY_EFFECTS_ENABLED = "enabled";

    private static final String KEY_TOOL = "tool";
    private static final String KEY_MELEE = "melee-weapon";
    private static final String KEY_RANGED = "ranged-weapon";
    private static final String KEY_ARMOR = "armor";

    /* ==================== 字段 ==================== */

    private static EnchantmentLevelConfig instance;
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    /* 通用 */
    private boolean enabled;
    private double globalMultiplier;

    /* 升级公式 */
    private String formula;
    private int baseExp;
    private double multiplier;
    private int increment;
    private final Map<String, Integer> maxLevels = new HashMap<>();

    /* 经验来源开关 */
    private boolean toolSourceEnabled;
    private boolean meleeSourceEnabled;
    private boolean rangedSourceEnabled;
    private boolean armorSourceEnabled;

    /* 每动作基础经验值 */
    private int toolBaseExp;
    private int meleeBaseExp;
    private int rangedBaseExp;
    private int armorBaseExp;

    /* 稀有度倍率表 */
    private final Map<Material, Double> blockRarity = new LinkedHashMap<>();
    private final Map<EntityType, Double> entityRarity = new LinkedHashMap<>();
    private final Map<String, Double> dimensionMultipliers = new LinkedHashMap<>();

    /* XP Bonus 覆盖（RPG 接口：外部模块注入的额外经验，不影响配置文件） */
    private final ConcurrentHashMap<ExpCategory, Double> xpBonuses = new ConcurrentHashMap<>();

    /* 显示 */
    private int expBarLength;
    private List<TextColor> barGradientColors;
    private TextColor emptyBarColor;
    private final List<Map.Entry<Double, TextColor>> textColorTiers = new ArrayList<>();
    private boolean autoInitialize;
    private boolean effectsEnabled;

    /* ==================== 单例 ==================== */

    private EnchantmentLevelConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE_NAME);
        loadConfig();
    }

    public static synchronized EnchantmentLevelConfig getInstance(JavaPlugin plugin) {
        if (instance == null) {
            instance = new EnchantmentLevelConfig(plugin);
        }
        return instance;
    }

    public static EnchantmentLevelConfig getInstance() {
        return instance;
    }

    /* ==================== 加载 ==================== */

    public void reload() {
        loadConfig();
        plugin.getLogger().info("配置文件 " + CONFIG_FILE_NAME + " 已重新加载");
    }

    private void loadConfig() {
        if (!configFile.exists()) {
            createDefaultConfig();
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);
        loadAll();
        validate();
    }

    private void createDefaultConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        YamlConfiguration dc = new YamlConfiguration();

        /* 通用 */
        dc.set(KEY_ENABLED, DEFAULT_ENABLED);

        /* 满级配置（按类别分组，方便查阅；删除或注释可恢复原版上限） */
        dc.set(KEY_MAX_LEVELS + ".sharpness", 5);
        dc.set(KEY_MAX_LEVELS + ".smite", 5);
        dc.set(KEY_MAX_LEVELS + ".bane_of_arthropods", 5);
        dc.set(KEY_MAX_LEVELS + ".sweeping_edge", 3);
        dc.set(KEY_MAX_LEVELS + ".fire_aspect", 2);
        dc.set(KEY_MAX_LEVELS + ".knockback", 2);
        dc.set(KEY_MAX_LEVELS + ".looting", 3);
        dc.set(KEY_MAX_LEVELS + ".efficiency", 5);
        dc.set(KEY_MAX_LEVELS + ".silk_touch", 1);
        dc.set(KEY_MAX_LEVELS + ".fortune", 3);
        dc.set(KEY_MAX_LEVELS + ".unbreaking", 3);
        dc.set(KEY_MAX_LEVELS + ".mending", 1);
        dc.set(KEY_MAX_LEVELS + ".protection", 4);
        dc.set(KEY_MAX_LEVELS + ".fire_protection", 4);
        dc.set(KEY_MAX_LEVELS + ".blast_protection", 4);
        dc.set(KEY_MAX_LEVELS + ".projectile_protection", 4);
        dc.set(KEY_MAX_LEVELS + ".feather_falling", 4);
        dc.set(KEY_MAX_LEVELS + ".thorns", 3);
        dc.set(KEY_MAX_LEVELS + ".respiration", 3);
        dc.set(KEY_MAX_LEVELS + ".aqua_affinity", 1);
        dc.set(KEY_MAX_LEVELS + ".depth_strider", 3);
        dc.set(KEY_MAX_LEVELS + ".frost_walker", 2);
        dc.set(KEY_MAX_LEVELS + ".soul_speed", 3);
        dc.set(KEY_MAX_LEVELS + ".swift_sneak", 3);
        dc.set(KEY_MAX_LEVELS + ".power", 5);
        dc.set(KEY_MAX_LEVELS + ".punch", 2);
        dc.set(KEY_MAX_LEVELS + ".flame", 1);
        dc.set(KEY_MAX_LEVELS + ".infinity", 1);
        dc.set(KEY_MAX_LEVELS + ".multishot", 1);
        dc.set(KEY_MAX_LEVELS + ".quick_charge", 3);
        dc.set(KEY_MAX_LEVELS + ".piercing", 4);
        dc.set(KEY_MAX_LEVELS + ".channeling", 1);
        dc.set(KEY_MAX_LEVELS + ".impaling", 5);
        dc.set(KEY_MAX_LEVELS + ".loyalty", 3);
        dc.set(KEY_MAX_LEVELS + ".riptide", 3);
        dc.set(KEY_MAX_LEVELS + ".luck_of_the_sea", 3);
        dc.set(KEY_MAX_LEVELS + ".lure", 3);
        dc.set(KEY_MAX_LEVELS + ".density", 5);
        dc.set(KEY_MAX_LEVELS + ".breach", 4);
        dc.set(KEY_MAX_LEVELS + ".wind_burst", 3);
        dc.set(KEY_MAX_LEVELS + ".lunge", 3);
        dc.set(KEY_MAX_LEVELS + ".binding_curse", 1);
        dc.set(KEY_MAX_LEVELS + ".vanishing_curse", 1);

        /* 升级公式 */
        dc.set(KEY_LEVEL_FORMULA + "." + KEY_FORMULA, DEFAULT_FORMULA);
        dc.set(KEY_LEVEL_FORMULA + "." + KEY_BASE_EXP, DEFAULT_BASE_EXP);
        dc.set(KEY_LEVEL_FORMULA + "." + KEY_MULTIPLIER, DEFAULT_MULTIPLIER);
        dc.set(KEY_LEVEL_FORMULA + "." + KEY_INCREMENT, DEFAULT_INCREMENT);

        /* 经验来源 */
        dc.set(KEY_EXP_SOURCES + "." + KEY_GLOBAL_MULTIPLIER, DEFAULT_GLOBAL_MULTIPLIER);

        dc.set(KEY_EXP_SOURCES + "." + KEY_TOOL + "." + KEY_SOURCE_ENABLED, true);
        dc.set(KEY_EXP_SOURCES + "." + KEY_TOOL + "." + KEY_BASE_EXP_PER_ACTION, DEFAULT_TOOL_BASE_EXP);
        for (Map.Entry<Material, Double> e : DEFAULT_BLOCK_RARITY.entrySet()) {
            dc.set(KEY_EXP_SOURCES + "." + KEY_TOOL + "." + KEY_BLOCK_RARITY + "." + e.getKey().name(), e.getValue());
        }

        dc.set(KEY_EXP_SOURCES + "." + KEY_MELEE + "." + KEY_SOURCE_ENABLED, true);
        dc.set(KEY_EXP_SOURCES + "." + KEY_MELEE + "." + KEY_BASE_EXP_PER_ACTION, DEFAULT_MELEE_BASE_EXP);
        for (Map.Entry<EntityType, Double> e : DEFAULT_ENTITY_RARITY.entrySet()) {
            dc.set(KEY_EXP_SOURCES + "." + KEY_MELEE + "." + KEY_ENTITY_RARITY + "." + e.getKey().name(), e.getValue());
        }
        for (Map.Entry<String, Double> e : DEFAULT_DIMENSION_MULTIPLIERS.entrySet()) {
            dc.set(KEY_EXP_SOURCES + "." + KEY_MELEE + "." + KEY_DIMENSION_MULTIPLIER + "." + e.getKey(), e.getValue());
        }

        dc.set(KEY_EXP_SOURCES + "." + KEY_RANGED + "." + KEY_SOURCE_ENABLED, true);
        dc.set(KEY_EXP_SOURCES + "." + KEY_RANGED + "." + KEY_BASE_EXP_PER_ACTION, DEFAULT_RANGED_BASE_EXP);
        for (Map.Entry<EntityType, Double> e : DEFAULT_ENTITY_RARITY.entrySet()) {
            dc.set(KEY_EXP_SOURCES + "." + KEY_RANGED + "." + KEY_ENTITY_RARITY + "." + e.getKey().name(), e.getValue());
        }
        for (Map.Entry<String, Double> e : DEFAULT_DIMENSION_MULTIPLIERS.entrySet()) {
            dc.set(KEY_EXP_SOURCES + "." + KEY_RANGED + "." + KEY_DIMENSION_MULTIPLIER + "." + e.getKey(), e.getValue());
        }

        dc.set(KEY_EXP_SOURCES + "." + KEY_ARMOR + "." + KEY_SOURCE_ENABLED, true);
        dc.set(KEY_EXP_SOURCES + "." + KEY_ARMOR + "." + KEY_BASE_EXP_PER_ACTION, DEFAULT_ARMOR_BASE_EXP);

        /* 显示 */
        dc.set(KEY_DISPLAY + "." + KEY_FORMAT + "." + KEY_EXP_BAR_LENGTH, DEFAULT_EXP_BAR_LENGTH);
        dc.set(KEY_DISPLAY + "." + KEY_AUTO_INITIALIZE, DEFAULT_AUTO_INIT);
        dc.set(KEY_DISPLAY + "." + KEY_EXP_BAR_COLORS + "." + KEY_BAR_EMPTY_COLOR, DEFAULT_BAR_EMPTY_COLOR);
        dc.set(KEY_DISPLAY + "." + KEY_EXP_BAR_COLORS + "." + KEY_BAR_GRADIENT, DEFAULT_BAR_GRADIENT);

        List<String> tierThresholds = new ArrayList<>();
        List<String> tierColors = new ArrayList<>();
        for (String[] tier : DEFAULT_TEXT_COLOR_TIERS) {
            tierThresholds.add(tier[0]);
            tierColors.add(tier[1]);
        }
        dc.set(KEY_DISPLAY + "." + KEY_EXP_BAR_COLORS + "." + KEY_TEXT_COLOR_TIERS + "." + KEY_THRESHOLD, tierThresholds);
        dc.set(KEY_DISPLAY + "." + KEY_EXP_BAR_COLORS + "." + KEY_TEXT_COLOR_TIERS + "." + KEY_COLOR, tierColors);

        /* 特效 */
        dc.set(KEY_UPGRADE_EFFECTS + "." + KEY_EFFECTS_ENABLED, DEFAULT_EFFECTS_ENABLED);

        dc.options().setHeader(Arrays.asList(
            "附魔升级系统配置文件",
            "",
            "=== 设计理念（Minecraft 原生哲学） ===",
            "  用剑战斗→剑上的附魔成长；用镐挖矿→镐上的附魔成长。",
            "  每个附魔有固定的\"成长类别\"，只有对应用法才加经验。",
            "",
            "=== 附魔类别（共42个，含1.21+锤类） ===",
            "  WEAPON:   锋利/亡灵杀手/节肢杀手/横扫之刃/火焰附加/击退/抢夺",
            "  TOOL:     效率/精准采集/时运",
            "  ARMOR:    保护/火焰保护/爆炸保护/弹射物保护/摔落保护",
            "            荆棘/水下呼吸/水下速掘",
            "            深海探索者/冰霜行者/灵魂疾行/迅捷潜行",
            "  BOW:      力量/冲击/火矢/无限",
            "  CROSSBOW: 多重射击/快速装填/穿透",
            "  TRIDENT:  穿刺/忠诚/激流/引雷",
            "  FISHING:  海之眷顾/饵钓",
            "  MACE:     致密/破甲/风爆",
            "  SPEAR:    突进",
            "  UNIVERSAL: 耐久/经验修补",
            "  CURSE:    绑定诅咒/消失诅咒",
            "",
            "=== 最高等级 ===",
            "  max-levels 下每个附魔的默认值=原版上限（如 sharpness: 5）",
            "  修改数值可突破原版上限（最高 10），删除或注释则回退原版",
            "  自定义附魔效果见 Level.yml",
            "",
            "  稀有度系统:",
            "    挖掘更稀有的方块→更多经验（钻石矿 > 铁矿 > 石头）",
            "    击杀更强的生物→更多经验（末影龙 > 凋零骷髅 > 僵尸）",
            "    维度递进→更危险的维度成长更快（末地 ×2 > 下界 ×1.5 > 主世界 ×1）",
            "",
            "=== 指令 ===",
            "  /xh lore xp  - 切换到经验显示模式",
            "  /xh lore     - 切换回正常显示",
            "  /xh reload   - 重载所有配置"
        ));

        try {
            dc.save(configFile);
            plugin.getLogger().info("已创建默认配置文件: " + CONFIG_FILE_NAME);
        } catch (Exception e) {
            plugin.getLogger().warning("创建配置文件失败: " + e.getMessage());
        }
    }

    private void loadAll() {
        loadGeneral();
        loadLevelFormula();
        loadExpSources();
        loadDisplay();
        loadUpgradeEffects();
    }

    private void loadGeneral() {
        this.enabled = config.getBoolean(KEY_ENABLED, DEFAULT_ENABLED);
        this.autoInitialize = config.getBoolean(KEY_DISPLAY + "." + KEY_AUTO_INITIALIZE, DEFAULT_AUTO_INIT);
    }

    private void loadLevelFormula() {
        String path = KEY_LEVEL_FORMULA;
        this.formula = config.getString(path + "." + KEY_FORMULA, DEFAULT_FORMULA);
        this.baseExp = config.getInt(path + "." + KEY_BASE_EXP, DEFAULT_BASE_EXP);
        this.multiplier = config.getDouble(path + "." + KEY_MULTIPLIER, DEFAULT_MULTIPLIER);
        this.increment = config.getInt(path + "." + KEY_INCREMENT, DEFAULT_INCREMENT);

        this.maxLevels.clear();
        if (config.contains(KEY_MAX_LEVELS)) {
            for (String key : config.getConfigurationSection(KEY_MAX_LEVELS).getKeys(false)) {
                maxLevels.put(key.toUpperCase(), config.getInt(KEY_MAX_LEVELS + "." + key, 0));
            }
        }
    }

    private void loadExpSources() {
        String path = KEY_EXP_SOURCES;
        this.globalMultiplier = config.getDouble(path + "." + KEY_GLOBAL_MULTIPLIER, DEFAULT_GLOBAL_MULTIPLIER);

        /* 工具 */
        this.toolSourceEnabled = config.getBoolean(path + "." + KEY_TOOL + "." + KEY_SOURCE_ENABLED, true);
        this.toolBaseExp = config.getInt(path + "." + KEY_TOOL + "." + KEY_BASE_EXP_PER_ACTION, DEFAULT_TOOL_BASE_EXP);
        loadBlockRarity(path + "." + KEY_TOOL + "." + KEY_BLOCK_RARITY);

        /* 近战 */
        this.meleeSourceEnabled = config.getBoolean(path + "." + KEY_MELEE + "." + KEY_SOURCE_ENABLED, true);
        this.meleeBaseExp = config.getInt(path + "." + KEY_MELEE + "." + KEY_BASE_EXP_PER_ACTION, DEFAULT_MELEE_BASE_EXP);
        loadEntityRarity(path + "." + KEY_MELEE + "." + KEY_ENTITY_RARITY);
        loadDimensionMultipliers(path + "." + KEY_MELEE + "." + KEY_DIMENSION_MULTIPLIER);

        /* 远程 */
        this.rangedSourceEnabled = config.getBoolean(path + "." + KEY_RANGED + "." + KEY_SOURCE_ENABLED, true);
        this.rangedBaseExp = config.getInt(path + "." + KEY_RANGED + "." + KEY_BASE_EXP_PER_ACTION, DEFAULT_RANGED_BASE_EXP);

        /* 护甲 */
        this.armorSourceEnabled = config.getBoolean(path + "." + KEY_ARMOR + "." + KEY_SOURCE_ENABLED, true);
        this.armorBaseExp = config.getInt(path + "." + KEY_ARMOR + "." + KEY_BASE_EXP_PER_ACTION, DEFAULT_ARMOR_BASE_EXP);
    }

    private void loadBlockRarity(String path) {
        this.blockRarity.clear();
        this.blockRarity.putAll(DEFAULT_BLOCK_RARITY);
        if (config.contains(path)) {
            for (String key : config.getConfigurationSection(path).getKeys(false)) {
                try {
                    Material mat = Material.valueOf(key.toUpperCase());
                    double val = config.getDouble(path + "." + key, 1.0);
                    blockRarity.put(mat, val);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void loadEntityRarity(String path) {
        this.entityRarity.clear();
        this.entityRarity.putAll(DEFAULT_ENTITY_RARITY);
        if (config.contains(path)) {
            for (String key : config.getConfigurationSection(path).getKeys(false)) {
                try {
                    EntityType type = EntityType.valueOf(key.toUpperCase());
                    double val = config.getDouble(path + "." + key, 1.0);
                    entityRarity.put(type, val);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void loadDimensionMultipliers(String path) {
        this.dimensionMultipliers.clear();
        this.dimensionMultipliers.putAll(DEFAULT_DIMENSION_MULTIPLIERS);
        if (config.contains(path)) {
            for (String key : config.getConfigurationSection(path).getKeys(false)) {
                double val = config.getDouble(path + "." + key, 1.0);
                dimensionMultipliers.put(key, val);
            }
        }
    }

    private void loadDisplay() {
        String path = KEY_DISPLAY;
        this.expBarLength = config.getInt(path + "." + KEY_FORMAT + "." + KEY_EXP_BAR_LENGTH, DEFAULT_EXP_BAR_LENGTH);
        loadBarColors();
    }

    private void loadBarColors() {
        String path = KEY_DISPLAY + "." + KEY_EXP_BAR_COLORS;

        String emptyColorStr = config.getString(path + "." + KEY_BAR_EMPTY_COLOR, DEFAULT_BAR_EMPTY_COLOR);
        this.emptyBarColor = parseColor(emptyColorStr);
        if (this.emptyBarColor == null) {
            this.emptyBarColor = parseColor(DEFAULT_BAR_EMPTY_COLOR);
        }

        this.barGradientColors = new ArrayList<>();
        List<String> gradientList = config.getStringList(path + "." + KEY_BAR_GRADIENT);
        if (gradientList.isEmpty()) gradientList = DEFAULT_BAR_GRADIENT;
        for (String colorStr : gradientList) {
            TextColor color = parseColor(colorStr);
            if (color != null) this.barGradientColors.add(color);
        }
        if (this.barGradientColors.isEmpty()) {
            this.barGradientColors.add(parseColor("#55FF55"));
        }

        this.textColorTiers.clear();
        List<String> thresholds = config.getStringList(path + "." + KEY_TEXT_COLOR_TIERS + "." + KEY_THRESHOLD);
        List<String> colors = config.getStringList(path + "." + KEY_TEXT_COLOR_TIERS + "." + KEY_COLOR);
        int count = Math.min(thresholds.size(), colors.size());
        for (int i = 0; i < count; i++) {
            try {
                double threshold = Double.parseDouble(thresholds.get(i));
                TextColor color = parseColor(colors.get(i));
                if (color != null && threshold >= 0.0 && threshold <= 1.0) {
                    this.textColorTiers.add(new AbstractMap.SimpleEntry<>(threshold, color));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (this.textColorTiers.isEmpty()) {
            for (String[] tier : DEFAULT_TEXT_COLOR_TIERS) {
                this.textColorTiers.add(new AbstractMap.SimpleEntry<>(
                    Double.parseDouble(tier[0]), parseColor(tier[1])));
            }
        } else {
            this.textColorTiers.sort(Comparator.comparingDouble(Map.Entry::getKey));
        }
    }

    private void loadUpgradeEffects() {
        this.effectsEnabled = config.getBoolean(KEY_UPGRADE_EFFECTS + "." + KEY_EFFECTS_ENABLED, DEFAULT_EFFECTS_ENABLED);
    }

    /* ==================== 验证 ==================== */

    private void validate() {
        if (baseExp < 1) { baseExp = DEFAULT_BASE_EXP; }
        if (!formula.equalsIgnoreCase("EXPONENTIAL") && !formula.equalsIgnoreCase("LINEAR")) {
            formula = DEFAULT_FORMULA;
        }
        if (expBarLength < 1 || expBarLength > 50) { expBarLength = DEFAULT_EXP_BAR_LENGTH; }
    }

    /* ==================== 颜色工具 ==================== */

    private TextColor parseColor(String colorStr) {
        if (colorStr == null) return null;
        String s = colorStr.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            return TextColor.color(Integer.parseInt(s, 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public TextColor getTextColorForProgress(double progress) {
        if (textColorTiers.isEmpty()) return TextColor.color(0x888888);
        TextColor result = textColorTiers.get(0).getValue();
        for (Map.Entry<Double, TextColor> entry : textColorTiers) {
            if (progress >= entry.getKey()) result = entry.getValue();
            else break;
        }
        return result;
    }

    /* ==================== Getter ==================== */

    public boolean isEnabled() { return enabled; }

    public String getFormula() { return formula; }

    public boolean isExponential() { return "EXPONENTIAL".equalsIgnoreCase(formula); }

    public int getBaseExp() { return baseExp; }

    public double getMultiplier() { return multiplier; }

    public int getIncrement() { return increment; }

    public int getMaxLevel(Enchantment enchant) {
        return getMaxLevel(enchantKey(enchant), enchant.getMaxLevel());
    }

    public int getMaxLevel(String key, int vanillaDefault) {
        Integer configMax = maxLevels.get(key.toUpperCase());
        if (configMax != null) {
            return configMax == 0 ? vanillaDefault : configMax;
        }
        return vanillaDefault;
    }

    /* 经验来源 */
    public double getGlobalMultiplier() { return globalMultiplier; }

    public boolean isToolSourceEnabled() { return toolSourceEnabled; }
    public boolean isMeleeSourceEnabled() { return meleeSourceEnabled; }
    public boolean isRangedSourceEnabled() { return rangedSourceEnabled; }
    public boolean isArmorSourceEnabled() { return armorSourceEnabled; }

    public int getToolBaseExp() { return toolBaseExp; }
    public int getMeleeBaseExp() { return meleeBaseExp; }
    public int getRangedBaseExp() { return rangedBaseExp; }
    public int getArmorBaseExp() { return armorBaseExp; }

    /** 获取方块的稀有度倍率（未配置返回 1.0） */
    public double getBlockRarity(Material material) {
        return blockRarity.getOrDefault(material, 1.0);
    }

    /** 获取实体的稀有度倍率（未配置返回 1.0） */
    public double getEntityRarity(EntityType type) {
        return entityRarity.getOrDefault(type, 1.0);
    }

    /** 获取维度倍率（未配置返回 1.0） */
    public double getDimensionMultiplier(World world) {
        if (world == null) return 1.0;
        String key = world.getKey().asString();
        return dimensionMultipliers.getOrDefault(key, 1.0);
    }

    /* ==================== RPG 接口：XP Bonus ==================== */

    /**
     * 设置指定行为类别的额外 XP（不修改配置文件，存内存中）
     * 例如给 TOOL 类别注入 +10 XP，每次挖矿都会多加 10 经验
     *
     * @param category 行为类别（TOOL/WEAPON/BOW/ARMOR）
     * @param bonus    额外经验值（可为负数）
     */
    public void setXpBonus(ExpCategory category, double bonus) {
        xpBonuses.put(category, bonus);
    }

    /**
     * 获取指定行为类别的 XP Bonus
     */
    public double getXpBonus(ExpCategory category) {
        return xpBonuses.getOrDefault(category, 0.0);
    }

    /**
     * 清除指定类别的 XP Bonus
     */
    public void clearXpBonus(ExpCategory category) {
        xpBonuses.remove(category);
    }

    /**
     * 清除所有 XP Bonus
     */
    public void clearAllXpBonuses() {
        xpBonuses.clear();
    }

    /* 显示 */
    public int getExpBarLength() { return expBarLength; }
    public List<TextColor> getBarGradientColors() { return barGradientColors; }
    public TextColor getEmptyBarColor() { return emptyBarColor; }
    public boolean isAutoInitialize() { return autoInitialize; }
    public boolean isEffectsEnabled() { return effectsEnabled; }

    /* ==================== 工具方法 ==================== */

    public static String enchantKey(Enchantment enchant) {
        return enchant.getKey().getKey().toUpperCase();
    }

    /**
     * 获取附魔所属的成长类别
     * 只有对应行为才会给这个附魔经验
     */
    public static ExpCategory getCategory(Enchantment enchant) {
        String key = enchantKey(enchant);
        return switch (key) {
            /* 近战武器 */
            case "SHARPNESS", "SMITE", "BANE_OF_ARTHROPODS",
                 "SWEEPING_EDGE", "FIRE_ASPECT", "KNOCKBACK", "LOOTING",
                 "LUNGE" -> ExpCategory.WEAPON;

            /* 工具 */
            case "EFFICIENCY", "SILK_TOUCH", "FORTUNE" -> ExpCategory.TOOL;

            /* 护甲 */
            case "PROTECTION", "FIRE_PROTECTION", "BLAST_PROTECTION", "PROJECTILE_PROTECTION",
                 "FEATHER_FALLING", "RESPIRATION", "AQUA_AFFINITY", "DEPTH_STRIDER",
                 "FROST_WALKER", "SOUL_SPEED", "SWIFT_SNEAK", "THORNS" -> ExpCategory.ARMOR;

            /* 弓/弩 */
            case "POWER", "PUNCH", "FLAME", "INFINITY",
                 "PIERCING", "QUICK_CHARGE", "MULTISHOT" -> ExpCategory.BOW;

            /* 三叉戟 */
            case "IMPALING", "LOYALTY", "RIPTIDE", "CHANNELING" -> ExpCategory.TRIDENT;

            /* 通用 */
            case "UNBREAKING", "MENDING" -> ExpCategory.UNIVERSAL;

            default -> ExpCategory.UNIVERSAL;
        };
    }

    /**
     * 判断该附魔是否应通过指定行为类别获得经验
     * @param enchant   附魔
     * @param action    行为类别（事件来源）
     */
    public static boolean matchesCategory(Enchantment enchant, ExpCategory action) {
        ExpCategory category = getCategory(enchant);
        if (category == ExpCategory.UNIVERSAL) return true;
        return category == action;
    }
}
