package io.github.liyughj.xH.command;

import io.github.liyughj.xH.XH;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

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

            sender.sendMessage("§aXH 插件配置重载完成！");
            plugin.getLogger().info("配置已通过命令重载 - 操作者: " + sender.getName());
        } catch (Exception e) {
            sender.sendMessage("§c重载配置时发生错误: " + e.getMessage());
            plugin.getLogger().warning("重载配置时发生错误: " + e.getMessage());
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
        sender.sendMessage("§e/xh help §7- 显示此帮助信息");
        sender.sendMessage("§6================================");

        /* 如果有权限，显示权限信息 */
        if (sender.hasPermission("xh.admin")) {
            sender.sendMessage("§a你拥有 xh.admin 权限");
        }
    }
}
