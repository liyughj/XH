package io.github.liyughj.xH.enchantmentLevel;

import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * 升级特效配置管理类
 * 管理升级粒子特效的相关配置，使用独立的 specialEffects.yml 文件
 */
public class SpecialEffects {

    /* 默认值 */
    private static final String DEFAULT_PARTICLE = "ENCHANTED_HIT";
    private static final int DEFAULT_COUNT = 30;
    private static final double DEFAULT_OFFSET_X = 0.5;
    private static final double DEFAULT_OFFSET_Y = 0.5;
    private static final double DEFAULT_OFFSET_Z = 0.5;
    private static final double DEFAULT_SPEED = 0.1;

    /* 配置文件名 */
    private static final String CONFIG_FILE_NAME = "specialEffects.yml";

    /* 配置键名 */
    private static final String KEY_DEFAULT = "default";
    private static final String KEY_PARTICLE = "particle";
    private static final String KEY_COUNT = "count";
    private static final String KEY_OFFSET_X = "offset-x";
    private static final String KEY_OFFSET_Y = "offset-y";
    private static final String KEY_OFFSET_Z = "offset-z";
    private static final String KEY_SPEED = "speed";
    private static final String KEY_CUSTOM_EFFECTS = "custom-effects";

    private static SpecialEffects instance;
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    private Particle defaultParticle;
    private int defaultCount;
    private double defaultOffsetX;
    private double defaultOffsetY;
    private double defaultOffsetZ;
    private double defaultSpeed;

    private final Map<String, Particle> customParticles = new HashMap<>();
    private final Map<String, Integer> customCounts = new HashMap<>();
    private final Map<String, Double> customOffsetsX = new HashMap<>();
    private final Map<String, Double> customOffsetsY = new HashMap<>();
    private final Map<String, Double> customOffsetsZ = new HashMap<>();
    private final Map<String, Double> customSpeeds = new HashMap<>();

    /**
     * 私有构造函数（单例模式）
     *
     * @param plugin 插件主类实例
     */
    private SpecialEffects(JavaPlugin plugin) {
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
    public static SpecialEffects getInstance(JavaPlugin plugin) {
        if (instance == null) {
            instance = new SpecialEffects(plugin);
        }
        return instance;
    }

    /**
     * 获取单例实例
     *
     * @return 配置实例
     */
    public static SpecialEffects getInstance() {
        return instance;
    }

    /**
     * 加载配置
     */
    private void loadConfig() {
        if (!configFile.exists()) {
            createDefaultConfig();
        }

        this.config = YamlConfiguration.loadConfiguration(configFile);
        loadAll();
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), CONFIG_FILE_NAME);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        loadAll();
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

        defaultConfig.set(KEY_DEFAULT + "." + KEY_PARTICLE, DEFAULT_PARTICLE);
        defaultConfig.set(KEY_DEFAULT + "." + KEY_COUNT, DEFAULT_COUNT);
        defaultConfig.set(KEY_DEFAULT + "." + KEY_OFFSET_X, DEFAULT_OFFSET_X);
        defaultConfig.set(KEY_DEFAULT + "." + KEY_OFFSET_Y, DEFAULT_OFFSET_Y);
        defaultConfig.set(KEY_DEFAULT + "." + KEY_OFFSET_Z, DEFAULT_OFFSET_Z);
        defaultConfig.set(KEY_DEFAULT + "." + KEY_SPEED, DEFAULT_SPEED);

        defaultConfig.set(KEY_CUSTOM_EFFECTS + ".SHARPNESS." + KEY_PARTICLE, "ENCHANTED_HIT");
        defaultConfig.set(KEY_CUSTOM_EFFECTS + ".SHARPNESS." + KEY_COUNT, 50);
        defaultConfig.set(KEY_CUSTOM_EFFECTS + ".PROTECTION." + KEY_PARTICLE, "HAPPY_VILLAGER");
        defaultConfig.set(KEY_CUSTOM_EFFECTS + ".EFFICIENCY." + KEY_PARTICLE, "END_ROD");

        List<String> headerLines = Arrays.asList(
            "升级特效配置文件",
            "default: 默认粒子特效设置（所有未单独配置的附魔使用此设置）",
            "  particle: 粒子类型（如 ENCHANTED_HIT、HAPPY_VILLAGER、END_ROD 等）",
            "  count: 粒子数量",
            "  offset-x/y/z: 粒子扩散范围",
            "  speed: 粒子速度",
            "custom-effects: 自定义附魔特效设置（按附魔名单独配置）",
            "  可用粒子类型参考: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Particle.html"
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
        loadDefaults();
        loadCustomEffects();
    }

    private void loadDefaults() {
        String particleName = config.getString(KEY_DEFAULT + "." + KEY_PARTICLE, DEFAULT_PARTICLE);
        defaultParticle = parseParticle(particleName);
        defaultCount = config.getInt(KEY_DEFAULT + "." + KEY_COUNT, DEFAULT_COUNT);
        defaultOffsetX = config.getDouble(KEY_DEFAULT + "." + KEY_OFFSET_X, DEFAULT_OFFSET_X);
        defaultOffsetY = config.getDouble(KEY_DEFAULT + "." + KEY_OFFSET_Y, DEFAULT_OFFSET_Y);
        defaultOffsetZ = config.getDouble(KEY_DEFAULT + "." + KEY_OFFSET_Z, DEFAULT_OFFSET_Z);
        defaultSpeed = config.getDouble(KEY_DEFAULT + "." + KEY_SPEED, DEFAULT_SPEED);
    }

    private void loadCustomEffects() {
        customParticles.clear();
        customCounts.clear();
        customOffsetsX.clear();
        customOffsetsY.clear();
        customOffsetsZ.clear();
        customSpeeds.clear();

        if (config.contains(KEY_CUSTOM_EFFECTS)) {
            for (String key : config.getConfigurationSection(KEY_CUSTOM_EFFECTS).getKeys(false)) {
                String normalizedKey = key.toUpperCase();
                String path = KEY_CUSTOM_EFFECTS + "." + key;
                String particleName = config.getString(path + "." + KEY_PARTICLE);
                if (particleName != null && !particleName.isEmpty()) {
                    customParticles.put(normalizedKey, parseParticle(particleName));
                }
                if (config.contains(path + "." + KEY_COUNT)) {
                    customCounts.put(normalizedKey, config.getInt(path + "." + KEY_COUNT));
                }
                if (config.contains(path + "." + KEY_OFFSET_X)) {
                    customOffsetsX.put(normalizedKey, config.getDouble(path + "." + KEY_OFFSET_X));
                }
                if (config.contains(path + "." + KEY_OFFSET_Y)) {
                    customOffsetsY.put(normalizedKey, config.getDouble(path + "." + KEY_OFFSET_Y));
                }
                if (config.contains(path + "." + KEY_OFFSET_Z)) {
                    customOffsetsZ.put(normalizedKey, config.getDouble(path + "." + KEY_OFFSET_Z));
                }
                if (config.contains(path + "." + KEY_SPEED)) {
                    customSpeeds.put(normalizedKey, config.getDouble(path + "." + KEY_SPEED));
                }
            }
        }
    }

    /* ========== 工具方法 ========== */

    private Particle parseParticle(String name) {
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("特效配置文件中的粒子类型无效: " + name + "，使用默认值: " + DEFAULT_PARTICLE);
            return Particle.ENCHANTED_HIT;
        }
    }

    /* ========== Getter 方法 ========== */

    public Particle getParticle(Enchantment enchant) {
        String key = EnchantmentLevelConfig.enchantKey(enchant);
        return customParticles.getOrDefault(key, defaultParticle);
    }

    public int getCount(Enchantment enchant) {
        String key = EnchantmentLevelConfig.enchantKey(enchant);
        return customCounts.getOrDefault(key, defaultCount);
    }

    public double getOffsetX(Enchantment enchant) {
        String key = EnchantmentLevelConfig.enchantKey(enchant);
        return customOffsetsX.getOrDefault(key, defaultOffsetX);
    }

    public double getOffsetY(Enchantment enchant) {
        String key = EnchantmentLevelConfig.enchantKey(enchant);
        return customOffsetsY.getOrDefault(key, defaultOffsetY);
    }

    public double getOffsetZ(Enchantment enchant) {
        String key = EnchantmentLevelConfig.enchantKey(enchant);
        return customOffsetsZ.getOrDefault(key, defaultOffsetZ);
    }

    public double getSpeed(Enchantment enchant) {
        String key = EnchantmentLevelConfig.enchantKey(enchant);
        return customSpeeds.getOrDefault(key, defaultSpeed);
    }
}