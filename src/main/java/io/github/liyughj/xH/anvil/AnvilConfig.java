package io.github.liyughj.xH.anvil;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * 铁砧配置管理类
 * 管理铁砧经验限制的相关配置，使用独立的 anvil.yml 文件
 */
public class AnvilConfig {

    /* 默认铁砧固定经验成本 */
    private static final int DEFAULT_FIXED_EXP_COST = 60;

    /* 配置文件名 */
    private static final String CONFIG_FILE_NAME = "anvil.yml";

    /* 配置键名 */
    private static final String CONFIG_KEY = "fixed-exp-cost";

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;
    private int fixedExpCost;

    /**
     * 构造函数
     *
     * @param plugin 插件主类实例
     */
    public AnvilConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE_NAME);
        loadConfig();
    }

    /**
     * 加载配置
     * 如果配置文件不存在，则创建默认配置
     */
    private void loadConfig() {
        /* 如果配置文件不存在，创建默认配置 */
        if (!configFile.exists()) {
            createDefaultConfig();
        }

        /* 加载配置文件 */
        this.config = YamlConfiguration.loadConfiguration(configFile);

        /* 读取配置值 */
        this.fixedExpCost = config.getInt(CONFIG_KEY, DEFAULT_FIXED_EXP_COST);

        /* 验证配置值有效性 */
        if (fixedExpCost < 0 || fixedExpCost > 1000) {
            plugin.getLogger().warning("配置文件 " + CONFIG_FILE_NAME + " 中的 " + CONFIG_KEY +
                " 值无效: " + fixedExpCost + "，使用默认值: " + DEFAULT_FIXED_EXP_COST);
            this.fixedExpCost = DEFAULT_FIXED_EXP_COST;
        }
    }

    /**
     * 创建默认配置文件
     */
    private void createDefaultConfig() {
        /* 确保数据文件夹存在 */
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        /* 创建默认配置 */
        YamlConfiguration defaultConfig = new YamlConfiguration();
        defaultConfig.set(CONFIG_KEY, DEFAULT_FIXED_EXP_COST);

        /* 添加注释 - 使用新的API避免弃用警告 */
        List<String> headerLines = Arrays.asList(
            "铁砧经验限制配置文件",
            "fixed-exp-cost: 铁砧固定经验成本（默认60）",
            "无论何种操作（修复/重命名/合并附魔），经验成本始终为此值"
        );
        defaultConfig.options().setHeader(headerLines);

        /* 保存配置文件 */
        try {
            defaultConfig.save(configFile);
            plugin.getLogger().info("已创建默认配置文件: " + CONFIG_FILE_NAME);
        } catch (Exception e) {
            plugin.getLogger().warning("创建配置文件失败: " + e.getMessage());
        }
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        loadConfig();
        plugin.getLogger().info("配置文件 " + CONFIG_FILE_NAME + " 已重新加载");
    }

    /**
     * 获取铁砧固定经验成本
     *
     * @return 固定经验成本
     */
    public int getFixedExpCost() {
        return fixedExpCost;
    }

    /**
     * 保存配置
     */
    public void save() {
        try {
            config.save(configFile);
        } catch (Exception e) {
            plugin.getLogger().warning("保存配置文件失败: " + e.getMessage());
        }
    }
}
