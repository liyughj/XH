package io.github.liyughj.xH;

import io.github.liyughj.xH.anvil.AnvilConfig;
import io.github.liyughj.xH.anvil.AnvilListener;
import io.github.liyughj.xH.anvil.AnvilPacketListener;
import io.github.liyughj.xH.command.XHCommand;
import io.github.liyughj.xH.enchantmentLevel.*;
import io.github.liyughj.xH.enchantingTable.BookshelfListener;
import io.github.liyughj.xH.enchantingTable.EnchantingItemListener;
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

    /* 铁砧配置管理器 */
    private AnvilConfig anvilConfig;

    /* 附魔经验显示管理器 */
    private EnchantmentLevelDisplay enchantmentLevelDisplay;

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

        /* 注册附魔物品限制监听器 */
        /* 监听物品放入附魔台事件，限制只有普通书才能显示附魔选项 */
        getServer().getPluginManager().registerEvents(
            new EnchantingItemListener(),
            this
        );

        /* 初始化铁砧配置（使用 anvil.yml） */
        this.anvilConfig = new AnvilConfig(this);

        /* 注册铁砧经验限制监听器 */
        /* 监听铁砧准备事件，强制经验成本为固定值 */
        getServer().getPluginManager().registerEvents(
            new AnvilListener(anvilConfig),
            this
        );

        /* 注册铁砧数据包监听器（ProtocolLib） */
        /* 修复铁砧"过于昂贵"显示问题，允许超过40级的经验成本 */
        new AnvilPacketListener(this, anvilConfig.getMaxRepairCost());

        /* ========== 附魔升级系统 ========== */

        /* 初始化附魔升级配置（使用 enchantmentLevel.yml） */
        EnchantmentLevelConfig levelConfig = EnchantmentLevelConfig.getInstance(this);
        if (levelConfig.isEnabled()) {
            /* 初始化升级特效配置（使用 specialEffects.yml） */
            SpecialEffects specialEffects = SpecialEffects.getInstance(this);

            /* 创建经验管理器 */
            EnchantmentLevelManager levelManager = new EnchantmentLevelManager(levelConfig);

            /* 注册附魔升级事件监听器 */
            getServer().getPluginManager().registerEvents(
                new EnchantmentLevelListener(levelManager, levelConfig, specialEffects),
                this
            );

            /* 注册附魔经验显示（含ProtocolLib数据包监听器） */
            this.enchantmentLevelDisplay = new EnchantmentLevelDisplay(this, levelManager, levelConfig);
            getServer().getPluginManager().registerEvents(enchantmentLevelDisplay, this);

            getLogger().info("附魔升级系统已启用");
        } else {
            getLogger().info("附魔升级系统已禁用（配置中 enabled=false）");
        }

        /* 注册命令执行器 */
        getCommand("xh").setExecutor(new XHCommand(this));

        getLogger().info("XH插件已启用！");
        getLogger().info("满级附魔台所需书架数量：" + enchantingTableConfig.getRequiredBookshelves());
        getLogger().info("附魔等级限制：强制 I级");
        getLogger().info("附魔物品限制：仅普通书可附魔");
        getLogger().info("铁砧经验成本：固定 " + anvilConfig.getFixedExpCost() + " 级（低于40级，不会显示过于昂贵）");
        getLogger().info("使用 /xh reload 命令可以重载配置");
    }

    @Override
    public void onDisable() {
        /* 清理附魔经验显示资源（内存清理、数据包监听器注销） */
        if (enchantmentLevelDisplay != null) {
            enchantmentLevelDisplay.shutdown();
        }
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

    /**
     * 获取铁砧配置管理器
     *
     * @return 配置管理器实例
     */
    public AnvilConfig getAnvilConfig() {
        return anvilConfig;
    }

    /**
     * 获取附魔经验显示管理器
     *
     * @return 显示管理器实例
     */
    public EnchantmentLevelDisplay getEnchantmentLevelDisplay() {
        return enchantmentLevelDisplay;
    }
}
