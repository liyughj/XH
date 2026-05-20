package io.github.liyughj.xH.enchantingTable;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * 附魔台配置管理类
 * 管理满级附魔台的相关配置，使用独立的 enchantingTable.yml 文件
 */
public class EnchantingTableConfig {

    /* 默认满级所需书架数量 */
    private static final int DEFAULT_REQUIRED_BOOKSHELVES = 15;

    /* 配置文件名 */
    private static final String CONFIG_FILE_NAME = "enchantingTable.yml";

    /* 配置键名 */
    private static final String CONFIG_KEY = "required-bookshelves";

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;
    private int requiredBookshelves;

    /**
     * 构造函数
     *
     * @param plugin 插件主类实例
     */
    public EnchantingTableConfig(JavaPlugin plugin) {
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
        this.requiredBookshelves = config.getInt(CONFIG_KEY, DEFAULT_REQUIRED_BOOKSHELVES);

        /* 验证配置值有效性 */
        if (requiredBookshelves < 0 || requiredBookshelves > 30) {
            plugin.getLogger().warning("配置文件 " + CONFIG_FILE_NAME + " 中的 " + CONFIG_KEY +
                " 值无效: " + requiredBookshelves + "，使用默认值: " + DEFAULT_REQUIRED_BOOKSHELVES);
            this.requiredBookshelves = DEFAULT_REQUIRED_BOOKSHELVES;
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
        defaultConfig.set(CONFIG_KEY, DEFAULT_REQUIRED_BOOKSHELVES);

        /* 添加注释 */
        defaultConfig.options().header("满级附魔台配置文件\n" +
            "required-bookshelves: 满级所需书架数量（默认15）\n" +
            "附魔等级强制为 I级，无需配置");

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
     * 获取满级所需书架数量
     *
     * @return 所需书架数量
     */
    public int getRequiredBookshelves() {
        return requiredBookshelves;
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
