package io.github.liyughj.xH.command;

import io.github.liyughj.xH.XH;
import io.github.liyughj.xH.debug.DebugManager;
import io.github.liyughj.xH.debug.RestoreManager;
import io.github.liyughj.xH.enchantmentLevel.EnchantmentLevelConfig;
import io.github.liyughj.xH.enchantmentLevel.EnchantmentLevelDisplay;
import io.github.liyughj.xH.enchantmentLevel.SpecialEffects;
import io.github.liyughj.xH.gun.AmmoConfig;
import io.github.liyughj.xH.gun.GunSystemConfig;
import io.github.liyughj.xH.gun.GUI.GunWorkbenchGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * XH插件命令执行器 + Tab补全
 * 处理 /xh 命令及其子命令
 */
public class XHCommand implements CommandExecutor, TabCompleter {

    private final XH plugin;
    private GunWorkbenchGui workbenchGui;

    public XHCommand(XH plugin) {
        this.plugin = plugin;
    }

    /** 注册工作台GUI实例，用于 /xh bench 命令 */
    public void setWorkbenchGui(GunWorkbenchGui gui) {
        this.workbenchGui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
            case "rl":
                return handleReload(sender);
            case "lore":
                return handleLore(sender, args);
            case "give":
                return handleGive(sender, args);
            case "bench":
                return handleBench(sender);
            case "debug":
                return handleDebug(sender);
            case "restore":
                return handleRestore(sender);
            case "help":
                sendHelp(sender);
                return true;
            default:
                sender.sendMessage("§c未知命令，使用 /xh help 查看帮助");
                return true;
        }
    }

    /* ==================== Tab 补全 ==================== */

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 一级子命令
            String prefix = args[0].toLowerCase();
            for (String cmd : new String[]{"bench", "debug", "give", "help", "lore", "reload", "restore"}) {
                if (cmd.startsWith(prefix)) completions.add(cmd);
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            // 二级：gun / ammo / mag
            String prefix = args[1].toLowerCase();
            for (String t : new String[]{"gun", "ammo", "mag"}) {
                if (t.startsWith(prefix)) completions.add(t);
            }
            return completions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[2].toLowerCase();
            switch (args[1].toLowerCase()) {
                case "gun":
                    for (String id : GunSystemConfig.gun().getAllGunIds()) {
                        if (id.startsWith(prefix)) completions.add(id);
                    }
                    break;
                case "ammo":
                    AmmoConfig ammoCfg = GunSystemConfig.ammo();
                    if (ammoCfg != null) {
                        // 显示可用口径ID
                        for (String cid : ammoCfg.getCaliberIds()) {
                            if (cid.startsWith(prefix)) completions.add(cid);
                        }
                    }
                    break;
                case "mag":
                    for (String id : GunSystemConfig.gun().getAllMagazineIds()) {
                        if (id.startsWith(prefix)) completions.add(id);
                    }
                    break;
            }
            return completions;
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("ammo")) {
            // 已选口径，补全弹种
            String prefix = args[3].toLowerCase();
            String caliber = args[2].toLowerCase();
            AmmoConfig ammoCfg = GunSystemConfig.ammo();
            if (ammoCfg != null) {
                for (String aid : ammoCfg.getAvailableTypes(caliber)) {
                    if (aid.startsWith(prefix)) completions.add(aid);
                }
            }
            return completions;
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("ammo")) {
            completions.add("<数量>");
            return completions;
        }

        return completions;
    }

    /* ==================== /xh give ==================== */

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return true;
        }
        if (!player.hasPermission("xh.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }

        String type = args[1].toLowerCase();

        switch (type) {
            case "gun":
                if (args.length < 3) { sender.sendMessage("§e用法: /xh give gun <ID>"); return true; }
                return handleGiveGun(player, args[2].toLowerCase());
            case "ammo":
                if (args.length < 4) { sender.sendMessage("§e用法: /xh give ammo <口径> <弹种> [数量]"); return true; }
                int amount = 1;
                if (args.length >= 5) {
                    try { amount = Integer.parseInt(args[4]); } catch (NumberFormatException ignored) {}
                    if (amount < 1) amount = 1;
                    if (amount > 64) amount = 64;
                }
                return handleGiveAmmo(player, args[2].toLowerCase(), args[3].toLowerCase(), amount);
            case "mag":
                if (args.length < 3) { sender.sendMessage("§e用法: /xh give mag <ID>"); return true; }
                return handleGiveMag(player, args[2].toLowerCase());
            default:
                sender.sendMessage("§c类型错误: " + type + "，应为 gun/ammo/mag");
                return true;
        }
    }

    private boolean handleGiveGun(Player player, String gunId) {
        ItemStack item = GunSystemConfig.gun().createGunItem(gunId);
        if (item == null) {
            player.sendMessage("§c未知枪械ID: " + gunId);
            player.sendMessage("§7可用ID: " + String.join(", ", GunSystemConfig.gun().getAllGunIds()));
            return true;
        }
        player.getInventory().addItem(item);
        player.sendMessage("§a已给予枪械: §e" + gunId);
        return true;
    }

    private boolean handleGiveAmmo(Player player, String caliberId, String ammoTypeId, int amount) {
        AmmoConfig ammo = GunSystemConfig.ammo();
        if (ammo == null) {
            player.sendMessage("§c弹药系统未加载");
            return true;
        }
        if (!ammo.isTypeAvailableForCaliber(caliberId, ammoTypeId)) {
            player.sendMessage("§c弹种 §e" + ammoTypeId + " §c不适用于口径 §e" + caliberId);
            return true;
        }
        ItemStack item = ammo.createAmmoItemStack(caliberId, ammoTypeId);
        if (item == null) {
            player.sendMessage("§c未知弹种ID: " + ammoTypeId);
            return true;
        }
        item.setAmount(amount);
        player.getInventory().addItem(item);
        player.sendMessage("§a已给予弹药: §e" + caliberId + " " + ammoTypeId + " §7×" + amount);
        return true;
    }

    private boolean handleGiveMag(Player player, String magId) {
        ItemStack item = GunSystemConfig.gun().createMagazineItem(magId);
        if (item == null) {
            player.sendMessage("§c未知弹匣ID: " + magId);
            player.sendMessage("§7可用ID: " + String.join(", ", GunSystemConfig.gun().getAllMagazineIds()));
            return true;
        }
        player.getInventory().addItem(item);
        player.sendMessage("§a已给予弹匣: §e" + magId);
        return true;
    }

    /* ==================== /xh bench ==================== */

    private boolean handleBench(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return true;
        }
        if (workbenchGui == null) {
            sender.sendMessage("§c枪械工作台未初始化");
            return true;
        }
        workbenchGui.open(player);
        return true;
    }

    /* ==================== /xh debug ==================== */

    private boolean handleDebug(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return true;
        }
        if (!player.hasPermission("xh.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }
        DebugManager.toggle(player);
        return true;
    }

    /* ==================== /xh restore ==================== */

    private boolean handleRestore(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return true;
        }
        if (!player.hasPermission("xh.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }
        RestoreManager.tryRestore(player);
        return true;
    }

    /* ==================== /xh reload ==================== */

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("xh.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }

        sender.sendMessage("§a正在重载 XH 插件配置...");

        try {
            plugin.getEnchantingTableConfig().reload();
            plugin.getAnvilConfig().reload();

            EnchantmentLevelConfig levelConfig = EnchantmentLevelConfig.getInstance();
            if (levelConfig != null) levelConfig.reload();

            SpecialEffects effects = SpecialEffects.getInstance();
            if (effects != null) effects.reload();

            if (plugin.getLevelConfig() != null) plugin.getLevelConfig().reload();

            /* 重载枪械配置 */
            GunSystemConfig.gun().reload();
            AmmoConfig ammo = GunSystemConfig.ammo();
            if (ammo != null) ammo.reload();

            sender.sendMessage("§aXH 插件配置重载完成！");
            plugin.getLogger().info("配置已通过命令重载 - 操作者: " + sender.getName());
        } catch (Exception e) {
            sender.sendMessage("§c重载配置时发生错误: " + e.getMessage());
            plugin.getLogger().warning("重载配置时发生错误: " + e.getMessage());
        }

        return true;
    }

    /* ==================== /xh lore ==================== */

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

        if (args.length >= 2 && args[1].equalsIgnoreCase("xp")) {
            display.setXpMode(player, true);
            player.sendMessage("§a已切换到 §e经验显示模式 §a（显示附魔经验进度）");
        } else {
            display.setXpMode(player, false);
            player.sendMessage("§a已切换到 §e正常模式 §a（显示原版附魔）");
        }

        return true;
    }

    /* ==================== /xh help ==================== */

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6========== XH 插件帮助 ==========");
        sender.sendMessage("§e/xh give gun <ID> §7- 获取枪械");
        sender.sendMessage("§e/xh give ammo <口径> <弹种> [数量] §7- 获取弹药");
        sender.sendMessage("§e/xh give mag <ID> §7- 获取弹匣");
        sender.sendMessage("§e/xh bench §7- 打开枪械工作台");
        sender.sendMessage("§e/xh reload §7- 重载插件配置");
        sender.sendMessage("§e/xh debug §7- 切换调试模式（显示枪械属性/附魔/RPG效果）");
        sender.sendMessage("§e/xh restore §7- 修复手中被改名的RPG武器/工具");
        sender.sendMessage("§e/xh lore xp §7- 切换到经验显示模式");
        sender.sendMessage("§e/xh lore §7- 切换回正常附魔显示");
        sender.sendMessage("§e/xh help §7- 显示此帮助信息");
        sender.sendMessage("§6================================");

        if (sender.hasPermission("xh.admin")) {
            sender.sendMessage("§a你拥有 xh.admin 权限");
        }
    }
}
