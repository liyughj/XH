package io.github.liyughj.xH.enchantmentLevel;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * 附魔升级系统配置管理类
 * 管理等级公式、经验来源、显示格式等配置，使用独立的 enchantmentLevel.yml 文件
 */
public class EnchantmentLevelConfig {

    /* 默认值 */
    private static final boolean DEFAULT_ENABLED = true;
    private static final String DEFAULT_FORMULA = "EXPONENTIAL";
    private static final int DEFAULT_BASE_EXP = 100;
    private static final double DEFAULT_MULTIPLIER = 2.0;
    private static final int DEFAULT_INCREMENT = 50;
    private static final int DEFAULT_ABSOLUTE_MAX = 100;
    private static final boolean DEFAULT_RESPECT_VANILLA_MAX = false;
    private static final int DEFAULT_EXP_BAR_LENGTH = 10;
    private static final boolean DEFAULT_SHOW_ON_SHIFT = true;
    private static final boolean DEFAULT_AUTO_INIT = true;
    private static final double DEFAULT_MULTIPLIER_VAL = 1.0;
    private static final boolean DEFAULT_EFFECTS_ENABLED = true;

    /* 配置文件名 */
    private static final String CONFIG_FILE_NAME = "enchantmentLevel.yml";

    /* 配置键名 */
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LEVEL_FORMULA = "level-formula";
    private static final String KEY_FORMULA = "formula";
    private static final String KEY_BASE_EXP = "base-exp";
    private static final String KEY_MULTIPLIER = "multiplier";
    private static final String KEY_INCREMENT = "increment";
    private static final String KEY_MAX_LEVELS = "max-levels";
    private static final String KEY_EXP_SOURCES = "exp-sources";
    private static final String KEY_SOURCE_ENABLED = "enabled";
    private static final String KEY_BLOCK_MULTIPLIER = "block-exp-multiplier";
    private static final String KEY_ENTITY_MULTIPLIER = "entity-exp-multiplier";
    private static final String KEY_DAMAGE_MULTIPLIER = "damage-exp-multiplier";
    private static final String KEY_DISPLAY = "display";
    private static final String KEY_FORMAT = "format";
    private static final String KEY_SHOW_ON_SHIFT = "show-when-shift-only";
    private static final String KEY_EXP_BAR_LENGTH = "exp-bar-length";
    private static final String KEY_AUTO_INITIALIZE = "auto-initialize";
    private static final String KEY_UPGRADE_EFFECTS = "upgrade-effects";
    private static final String KEY_EFFECTS_ENABLED = "enabled";

    /* 经验来源子键 */
    private static final String KEY_TOOL = "tool";
    private static final String KEY_MELEE = "melee-weapon";
    private static final String KEY_RANGED = "ranged-weapon";
    private static final String KEY_ARMOR = "armor";

    private static EnchantmentLevelConfig instance;
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    /* 通用设置 */
    private boolean enabled;

    /* 升级公式 */
    private String formula;
    private int baseExp;
    private double multiplier;
    private int increment;
    /* 附魔最高等级配置：附魔名 -> 等级（0表示原版） */
    private final Map<String, Integer> maxLevels = new HashMap<>();

    /* 经验来源 */
    private boolean toolSourceEnabled;
    private double blockExpMultiplier;
    private boolean meleeSourceEnabled;
    private double entityExpMultiplier;
    private boolean rangedSourceEnabled;
    private boolean armorSourceEnabled;
    private double damageExpMultiplier;

    /* 显示设置 */
    private boolean showOnShiftOnly;
    private int expBarLength;

    /* 其他 */
    private boolean autoInitialize;

    /* 升级特效 */
    private boolean effectsEnabled;

    /**
     * 私有构造函数（单例模式）
     *
     * @param plugin 插件主类实例
     */
    private EnchantmentLevelConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE_NAME);
        loadConfig();
    }

    /**
     * 获取单例实例
     *
     * @param plugin 插件主类实例
     * @return 配置实例
     */
    public static EnchantmentLevelConfig getInstance(JavaPlugin plugin) {
        if (instance == null) {
            instance = new EnchantmentLevelConfig(plugin);
        }
        return instance;
    }

    /**
     * 获取单例实例
     *
     * @return 配置实例
     */
    public static EnchantmentLevelConfig getInstance() {
        return instance;
    }

    /**
     * 加载配置
     * 如果配置文件不存在，则创建默认配置
     */
    private void loadConfig() {
        if (!configFile.exists()) {
            createDefaultConfig();
        }

        this.config = YamlConfiguration.loadConfiguration(configFile);
        loadAll();
        validate();
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        loadConfig();
        plugin.getLogger().info("配置文件 " + CONFIG_FILE_NAME + " 已重新加载");
    }

    /**
     * 创建默认配置文件
     */
    private void createDefaultConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        YamlConfiguration defaultConfig = new YamlConfiguration();

        /* 通用设置 */
        defaultConfig.set(KEY_ENABLED, DEFAULT_ENABLED);

        /* 升级公式 */
        defaultConfig.set(KEY_LEVEL_FORMULA + "." + KEY_FORMULA, DEFAULT_FORMULA);
        defaultConfig.set(KEY_LEVEL_FORMULA + "." + KEY_BASE_EXP, DEFAULT_BASE_EXP);
        defaultConfig.set(KEY_LEVEL_FORMULA + "." + KEY_MULTIPLIER, DEFAULT_MULTIPLIER);
        defaultConfig.set(KEY_LEVEL_FORMULA + "." + KEY_INCREMENT, DEFAULT_INCREMENT);
        /* 附魔最高等级设置 - 0表示使用原版最高等级，>0表示自定义等级
         * 默认不写入任何附魔配置，所有附魔都默认使用原版最高等级
         * 如需自定义，手动添加，例如：
         * max-levels:
         *   EFFICIENCY: 10    # 效率最高10级
         *   SHARPNESS: 0      # 锋利使用原版最高（V级）
         */

        /* 经验来源 */
        defaultConfig.set(KEY_EXP_SOURCES + "." + KEY_TOOL + "." + KEY_SOURCE_ENABLED, true);
        defaultConfig.set(KEY_EXP_SOURCES + "." + KEY_TOOL + "." + KEY_BLOCK_MULTIPLIER, DEFAULT_MULTIPLIER_VAL);
        defaultConfig.set(KEY_EXP_SOURCES + "." + KEY_MELEE + "." + KEY_SOURCE_ENABLED, true);
        defaultConfig.set(KEY_EXP_SOURCES + "." + KEY_MELEE + "." + KEY_ENTITY_MULTIPLIER, DEFAULT_MULTIPLIER_VAL);
        defaultConfig.set(KEY_EXP_SOURCES + "." + KEY_RANGED + "." + KEY_SOURCE_ENABLED, true);
        defaultConfig.set(KEY_EXP_SOURCES + "." + KEY_RANGED + "." + KEY_ENTITY_MULTIPLIER, DEFAULT_MULTIPLIER_VAL);
        defaultConfig.set(KEY_EXP_SOURCES + "." + KEY_ARMOR + "." + KEY_SOURCE_ENABLED, true);
        defaultConfig.set(KEY_EXP_SOURCES + "." + KEY_ARMOR + "." + KEY_DAMAGE_MULTIPLIER, DEFAULT_MULTIPLIER_VAL);

        /* 显示设置 */
        defaultConfig.set(KEY_DISPLAY + "." + KEY_FORMAT + "." + KEY_EXP_BAR_LENGTH, DEFAULT_EXP_BAR_LENGTH);
        defaultConfig.set(KEY_DISPLAY + "." + KEY_AUTO_INITIALIZE, DEFAULT_AUTO_INIT);

        /* 升级特效 */
        defaultConfig.set(KEY_UPGRADE_EFFECTS + "." + KEY_EFFECTS_ENABLED, DEFAULT_EFFECTS_ENABLED);

        /* 添加文件头注释 */
        List<String> headerLines = Arrays.asList(
            "附魔升级系统配置文件",
            "enabled: 是否启用附魔升级系统（默认true）",
            "level-formula: 升级公式设置",
            "  formula: EXPONENTIAL（指数）或 LINEAR（线性）",
            "  base-exp: 第一级升级所需经验",
            "  multiplier: 指数公式倍率",
            "  increment: 线性公式增量",
            "max-levels: 各附魔最高等级设置（可选）",
            "  格式: 附魔英文名: 等级",
            "  0表示使用原版最高等级，>0表示自定义等级",
            "  未配置的附魔默认使用原版最高等级",
            "  示例: EFFICIENCY: 10（效率最高10级）",
            "exp-sources: 经验来源设置",
            "  tool: 工具附魔（挖掘方块）",
            "  melee-weapon: 近战武器附魔（攻击实体）",
            "  ranged-weapon: 远程武器附魔（箭矢命中）",
            "  armor: 护甲附魔（受到伤害）",
            "display: 显示设置",
            "  format.exp-bar-length: 经验条长度",
            "  auto-initialize: 获得附魔时自动初始化经验数据",
            "upgrade-effects: 升级特效设置",
            "",
            "指令切换显示模式:",
            "  /xh lore xp  - 切换到经验显示模式（显示附魔经验进度）",
            "  /xh lore     - 切换回正常模式（显示原版附魔）"
        );
        defaultConfig.options().setHeader(headerLines);

        try {
            defaultConfig.save(configFile);
            plugin.getLogger().info("已创建默认配置文件: " + CONFIG_FILE_NAME);
        } catch (Exception e) {
            plugin.getLogger().warning("创建配置文件失败: " + e.getMessage());
        }
    }

    /* ========== 读取配置 ========== */

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

        /* 加载每个附魔的最高等级配置 */
        this.maxLevels.clear();
        if (config.contains(KEY_MAX_LEVELS)) {
            for (String key : config.getConfigurationSection(KEY_MAX_LEVELS).getKeys(false)) {
                int maxLevel = config.getInt(KEY_MAX_LEVELS + "." + key, 0);
                maxLevels.put(key.toUpperCase(), maxLevel);
            }
        }
    }

    private void loadExpSources() {
        String path = KEY_EXP_SOURCES;
        this.toolSourceEnabled = config.getBoolean(path + "." + KEY_TOOL + "." + KEY_SOURCE_ENABLED, true);
        this.blockExpMultiplier = config.getDouble(path + "." + KEY_TOOL + "." + KEY_BLOCK_MULTIPLIER, DEFAULT_MULTIPLIER_VAL);
        this.meleeSourceEnabled = config.getBoolean(path + "." + KEY_MELEE + "." + KEY_SOURCE_ENABLED, true);
        this.entityExpMultiplier = config.getDouble(path + "." + KEY_MELEE + "." + KEY_ENTITY_MULTIPLIER, DEFAULT_MULTIPLIER_VAL);
        this.rangedSourceEnabled = config.getBoolean(path + "." + KEY_RANGED + "." + KEY_SOURCE_ENABLED, true);
        this.armorSourceEnabled = config.getBoolean(path + "." + KEY_ARMOR + "." + KEY_SOURCE_ENABLED, true);
        this.damageExpMultiplier = config.getDouble(path + "." + KEY_ARMOR + "." + KEY_DAMAGE_MULTIPLIER, DEFAULT_MULTIPLIER_VAL);
    }

    private void loadDisplay() {
        String path = KEY_DISPLAY;
        this.showOnShiftOnly = config.getBoolean(path + "." + KEY_FORMAT + "." + KEY_SHOW_ON_SHIFT, DEFAULT_SHOW_ON_SHIFT);
        this.expBarLength = config.getInt(path + "." + KEY_FORMAT + "." + KEY_EXP_BAR_LENGTH, DEFAULT_EXP_BAR_LENGTH);
    }

    private void loadUpgradeEffects() {
        this.effectsEnabled = config.getBoolean(KEY_UPGRADE_EFFECTS + "." + KEY_EFFECTS_ENABLED, DEFAULT_EFFECTS_ENABLED);
    }

    /* ========== 验证 ========== */

    private void validate() {
        if (baseExp < 1) {
            plugin.getLogger().warning("base-exp 值无效: " + baseExp + "，使用默认值: " + DEFAULT_BASE_EXP);
            this.baseExp = DEFAULT_BASE_EXP;
        }
        if (!formula.equalsIgnoreCase("EXPONENTIAL") && !formula.equalsIgnoreCase("LINEAR")) {
            plugin.getLogger().warning("formula 值无效: " + formula + "，使用默认值: " + DEFAULT_FORMULA);
            this.formula = DEFAULT_FORMULA;
        }
        if (expBarLength < 1 || expBarLength > 50) {
            plugin.getLogger().warning("exp-bar-length 值无效: " + expBarLength + "，使用默认值: " + DEFAULT_EXP_BAR_LENGTH);
            this.expBarLength = DEFAULT_EXP_BAR_LENGTH;
        }
    }

    /* ========== Getter ========== */

    public boolean isEnabled() {
        return enabled;
    }

    public String getFormula() {
        return formula;
    }

    public boolean isExponential() {
        return "EXPONENTIAL".equalsIgnoreCase(formula);
    }

    public int getBaseExp() {
        return baseExp;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public int getIncrement() {
        return increment;
    }

    /**
     * 获取指定附魔的最高等级
     * @param enchant 附魔类型
     * @return 最高等级（0表示使用原版最高等级）
     */
    public int getMaxLevel(Enchantment enchant) {
        String key = enchantKey(enchant);
        Integer configMax = maxLevels.get(key);
        if (configMax != null) {
            /* 0表示使用原版最高等级 */
            if (configMax == 0) {
                return enchant.getMaxLevel();
            }
            return configMax;
        }
        /* 未配置则使用原版最高等级 */
        return enchant.getMaxLevel();
    }

    public boolean isAutoInitialize() {
        return autoInitialize;
    }

    public boolean isToolSourceEnabled() {
        return toolSourceEnabled;
    }

    public double getBlockExpMultiplier() {
        return blockExpMultiplier;
    }

    public boolean isMeleeSourceEnabled() {
        return meleeSourceEnabled;
    }

    public double getEntityExpMultiplier() {
        return entityExpMultiplier;
    }

    public boolean isRangedSourceEnabled() {
        return rangedSourceEnabled;
    }

    public boolean isArmorSourceEnabled() {
        return armorSourceEnabled;
    }

    public double getDamageExpMultiplier() {
        return damageExpMultiplier;
    }

    public boolean isShowOnShiftOnly() {
        return showOnShiftOnly;
    }

    public int getExpBarLength() {
        return expBarLength;
    }

    public boolean isEffectsEnabled() {
        return effectsEnabled;
    }

    /* ========== 工具方法 ========== */

    /**
     * 将附魔转换为配置键名（大写，无命名空间）
     *
     * @param enchant 附魔
     * @return 配置键名
     */
    public static String enchantKey(Enchantment enchant) {
        return enchant.getKey().getKey().toUpperCase();
    }
}