package io.github.liyughj.xH.enchantingTable;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 附魔台配置管理类
 * 管理满级附魔台的相关配置
 */
public class EnchantingTableConfig {

    /* 默认满级所需书架数量 */
    private static final int DEFAULT_REQUIRED_BOOKSHELVES = 15;

    /* 配置键名 */
    private static final String CONFIG_PATH = "enchanting-table.required-bookshelves";

    private final JavaPlugin plugin;
    private int requiredBookshelves;

    /**
     * 构造函数
     *
     * @param plugin 插件主类实例
     */
    public EnchantingTableConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * 加载配置
     */
    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();

        /* 设置默认值 */
        config.addDefault(CONFIG_PATH, DEFAULT_REQUIRED_BOOKSHELVES);

        /* 保存默认配置 */
        config.options().copyDefaults(true);
        plugin.saveConfig();

        /* 读取配置值 */
        this.requiredBookshelves = config.getInt(CONFIG_PATH, DEFAULT_REQUIRED_BOOKSHELVES);

        /* 验证配置值有效性 */
        if (requiredBookshelves < 0 || requiredBookshelves > 30) {
            plugin.getLogger().warning("配置项 " + CONFIG_PATH + " 的值无效: " + requiredBookshelves +
                "，使用默认值: " + DEFAULT_REQUIRED_BOOKSHELVES);
            this.requiredBookshelves = DEFAULT_REQUIRED_BOOKSHELVES;
        }
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        plugin.reloadConfig();
        loadConfig();
    }

    /**
     * 获取满级所需书架数量
     *
     * @return 所需书架数量
     */
    public int getRequiredBookshelves() {
        return requiredBookshelves;
    }
}
