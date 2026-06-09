package io.github.liyughj.xH.command;

import io.github.liyughj.xH.XH;
import io.github.liyughj.xH.enchantmentLevel.EnchantmentLevelConfig;
import io.github.liyughj.xH.enchantmentLevel.EnchantmentLevelDisplay;
import io.github.liyughj.xH.enchantmentLevel.SpecialEffects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * XH插件命令执行器
 * 处理 /xh 命令及其子命令
 */
public class XHCommand implements CommandExecutor {

    private final XH plugin;

    /**
     * 构造函数
     *
     * @param plugin 插件主类实例
     */
    public XHCommand(XH plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        /* 无参数时显示帮助信息 */
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        /* 处理子命令 */
        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
            case "rl":
                return handleReload(sender);
            case "lore":
                return handleLore(sender, args);
            case "help":
                sendHelp(sender);
                return true;
            default:
                sender.sendMessage("§c未知命令，使用 /xh help 查看帮助");
                return true;
        }
    }

    /**
     * 处理重载命令
     *
     * @param sender 命令发送者
     * @return 命令是否执行成功
     */
    private boolean handleReload(CommandSender sender) {
        /* 检查权限 */
        if (!sender.hasPermission("xh.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }

        sender.sendMessage("§a正在重载 XH 插件配置...");

        try {
            /* 重载附魔台配置 */
            plugin.getEnchantingTableConfig().reload();

            /* 重载铁砧配置 */
            plugin.getAnvilConfig().reload();

            /* 重载附魔升级配置 */
            EnchantmentLevelConfig levelConfig = EnchantmentLevelConfig.getInstance();
            if (levelConfig != null) {
                levelConfig.reload();
            }

            /* 重载特效配置 */
            SpecialEffects effects = SpecialEffects.getInstance();
            if (effects != null) {
                effects.reload();
            }

            /* 重载附魔等级效果配置 */
            if (plugin.getLevelConfig() != null) {
                plugin.getLevelConfig().reload();
            }

            sender.sendMessage("§aXH 插件配置重载完成！");
            plugin.getLogger().info("配置已通过命令重载 - 操作者: " + sender.getName());
        } catch (Exception e) {
            sender.sendMessage("§c重载配置时发生错误: " + e.getMessage());
            plugin.getLogger().warning("重载配置时发生错误: " + e.getMessage());
        }

        return true;
    }

    /**
     * 处理附魔经验显示切换命令
     * /xh lore xp → 切换到经验显示模式
     * /xh lore    → 切换回正常附魔显示
     *
     * @param sender 命令发送者
     * @param args   命令参数
     * @return 命令是否执行成功
     */
    private boolean handleLore(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return true;
        }

        EnchantmentLevelDisplay display = plugin.getEnchantmentLevelDisplay();
        if (display == null) {
            sender.sendMessage("§c附魔升级系统未启用");
            return true;
        }

        /* /xh lore xp → 开启经验显示模式 */
        if (args.length >= 2 && args[1].equalsIgnoreCase("xp")) {
            display.setXpMode(player, true);
            player.sendMessage("§a已切换到 §e经验显示模式 §a（显示附魔经验进度）");
        } else {
            /* /xh lore → 关闭经验显示模式，恢复正常附魔显示 */
            display.setXpMode(player, false);
            player.sendMessage("§a已切换到 §e正常模式 §a（显示原版附魔）");
        }

        return true;
    }

    /**
     * 发送帮助信息
     *
     * @param sender 命令发送者
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6========== XH 插件帮助 ==========");
        sender.sendMessage("§e/xh reload §7- 重载插件配置");
        sender.sendMessage("§e/xh lore xp §7- 切换到经验显示模式");
        sender.sendMessage("§e/xh lore §7- 切换回正常附魔显示");
        sender.sendMessage("§e/xh help §7- 显示此帮助信息");
        sender.sendMessage("§6================================");

        /* 如果有权限，显示权限信息 */
        if (sender.hasPermission("xh.admin")) {
            sender.sendMessage("§a你拥有 xh.admin 权限");
        }
    }
}
