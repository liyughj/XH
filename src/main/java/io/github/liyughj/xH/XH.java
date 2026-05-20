package io.github.liyughj.xH;

import io.github.liyughj.xH.enchantingTable.BookshelfListener;
import io.github.liyughj.xH.enchantingTable.EnchantingLevelListener;
import io.github.liyughj.xH.enchantingTable.EnchantingTableConfig;
import io.github.liyughj.xH.enchantingTable.EnchantingTableListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * XH插件主类
 * 管理插件生命周期和组件注册
 */
public final class XH extends JavaPlugin {

    /* 附魔台配置管理器 */
    private EnchantingTableConfig enchantingTableConfig;

    @Override
    public void onEnable() {
        /* 初始化附魔台配置（使用 enchantingTable.yml） */
        this.enchantingTableConfig = new EnchantingTableConfig(this);

        /* 注册附魔台事件监听器 */
        /* 监听玩家打开附魔台事件，阻止非满级附魔台的使用 */
        getServer().getPluginManager().registerEvents(
            new EnchantingTableListener(enchantingTableConfig.getRequiredBookshelves()),
            this
        );

        /* 注册书架事件监听器 */
        /* 监听书架放置/破坏事件，用于触发附近附魔台的重新检测 */
        getServer().getPluginManager().registerEvents(
            new BookshelfListener(),
            this
        );

        /* 注册附魔等级限制监听器 */
        /* 监听附魔完成事件，强制将所有附魔等级设为 I级 */
        getServer().getPluginManager().registerEvents(
            new EnchantingLevelListener(),
            this
        );

        getLogger().info("XH插件已启用！");
        getLogger().info("满级附魔台所需书架数量：" + enchantingTableConfig.getRequiredBookshelves());
        getLogger().info("附魔等级限制：强制 I级");
    }

    @Override
    public void onDisable() {
        getLogger().info("XH插件已禁用！");
    }

    /**
     * 获取附魔台配置管理器
     *
     * @return 配置管理器实例
     */
    public EnchantingTableConfig getEnchantingTableConfig() {
        return enchantingTableConfig;
    }
}
