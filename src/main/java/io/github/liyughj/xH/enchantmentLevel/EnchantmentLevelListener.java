package io.github.liyughj.xH.enchantmentLevel;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

/**
 * 附魔经验获取事件监听器
 * 监听各种游戏事件，根据物品类型和附魔类型分配经验值
 */
public class EnchantmentLevelListener implements Listener {

    private final EnchantmentLevelManager manager;
    private final EnchantmentLevelConfig config;
    private final SpecialEffects effects;

    /**
     * 构造函数
     *
     * @param manager 经验管理器
     * @param config  配置实例
     * @param effects 特效实例
     */
    public EnchantmentLevelListener(EnchantmentLevelManager manager, EnchantmentLevelConfig config, SpecialEffects effects) {
        this.manager = manager;
        this.config = config;
        this.effects = effects;
    }

    /**
     * 工具附魔经验获取：挖掘方块
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!config.isEnabled() || !config.isToolSourceEnabled()) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isTool(tool)) return;
        if (!tool.hasItemMeta()) return;

        /* 获取经验值（基于方块硬度） */
        double hardness = event.getBlock().getType().getHardness();
        int expAmount = (int) Math.max(1, hardness * config.getBlockExpMultiplier());

        /* 检查工具上是否有附魔 */
        if (EnchantmentLevelData.getAllEnchantments(tool).isEmpty()) return;

        /* 初始化经验数据（如果尚未初始化） */
        if (config.isAutoInitialize() && !manager.hasExpData(tool)) {
            manager.initializeExp(tool);
        }

        /* 为每个附魔添加经验 */
        List<Enchantment> upgraded = manager.addExpToEnchantments(tool, expAmount);

        /* 播放升级特效 */
        if (!upgraded.isEmpty()) {
            playUpgradeEffects(player, tool, upgraded);
        }
    }

    /**
     * 近战武器附魔经验获取：攻击实体
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!config.isEnabled() || !config.isMeleeSourceEnabled()) return;

        /* 检查攻击者是否为玩家 */
        if (!(event.getDamager() instanceof Player player)) return;
        /* 检查目标是否为生物 */
        if (!(event.getEntity() instanceof LivingEntity)) return;

        /* 获取主手武器 */
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!isMeleeWeapon(weapon) || !weapon.hasItemMeta()) return;

        /* 获取经验值（基于伤害量） */
        int expAmount = (int) Math.max(1, event.getDamage() * config.getEntityExpMultiplier());

        /* 检查武器上是否有附魔 */
        if (EnchantmentLevelData.getAllEnchantments(weapon).isEmpty()) return;

        /* 自动初始化经验数据 */
        if (config.isAutoInitialize() && !manager.hasExpData(weapon)) {
            manager.initializeExp(weapon);
        }

        /* 为每个附魔添加经验 */
        List<Enchantment> upgraded = manager.addExpToEnchantments(weapon, expAmount);

        /* 播放升级特效 */
        if (!upgraded.isEmpty()) {
            playUpgradeEffects(player, weapon, upgraded);
        }
    }

    /**
     * 远程武器附魔经验获取：箭矢命中
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!config.isEnabled() || !config.isRangedSourceEnabled()) return;

        /* 检查是否为箭矢 */
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        /* 检查射击者是否为玩家 */
        if (!(arrow.getShooter() instanceof Player player)) return;
        /* 检查命中目标是否为生物 */
        if (!(event.getHitEntity() instanceof LivingEntity)) return;

        /* 获取主手武器 */
        ItemStack bow = player.getInventory().getItemInMainHand();
        if (!isRangedWeapon(bow)) {
            /* 副手检查 */
            bow = player.getInventory().getItemInOffHand();
            if (!isRangedWeapon(bow)) return;
        }
        if (!bow.hasItemMeta()) return;

        /* 获取经验值（基于箭矢伤害） */
        int expAmount = (int) Math.max(1, arrow.getDamage() * config.getEntityExpMultiplier());

        /* 检查武器上是否有附魔 */
        if (EnchantmentLevelData.getAllEnchantments(bow).isEmpty()) return;

        /* 自动初始化经验数据 */
        if (config.isAutoInitialize() && !manager.hasExpData(bow)) {
            manager.initializeExp(bow);
        }

        /* 为每个附魔添加经验 */
        List<Enchantment> upgraded = manager.addExpToEnchantments(bow, expAmount);

        /* 播放升级特效 */
        if (!upgraded.isEmpty()) {
            playUpgradeEffects(player, bow, upgraded);
        }
    }

    /**
     * 护甲附魔经验获取：受到伤害
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!config.isEnabled() || !config.isArmorSourceEnabled()) return;

        /* 检查受伤者是否为玩家 */
        if (!(event.getEntity() instanceof Player player)) return;

        /* 获取经验值（基于受伤量） */
        int expAmount = (int) Math.max(1, event.getDamage() * config.getDamageExpMultiplier());

        /* 遍历玩家穿戴的护甲 */
        PlayerInventory inv = player.getInventory();
        ItemStack[] armor = {inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()};

        for (ItemStack piece : armor) {
            /* 跳过空槽位或无Meta的物品 */
            if (piece == null || piece.getType().isAir() || !piece.hasItemMeta()) continue;

            /* 检查护甲上是否有附魔 */
            if (EnchantmentLevelData.getAllEnchantments(piece).isEmpty()) continue;

            /* 自动初始化经验数据 */
            if (config.isAutoInitialize() && !manager.hasExpData(piece)) {
                manager.initializeExp(piece);
            }

            /* 为每个附魔添加经验 */
            List<Enchantment> upgraded = manager.addExpToEnchantments(piece, expAmount);

            /* 播放升级特效 */
            if (!upgraded.isEmpty()) {
                playUpgradeEffects(player, piece, upgraded);
            }
        }
    }

    /**
     * 附魔台附魔时自动初始化经验数据
     * 注意：EnchantingLevelListener（优先级 HIGH）会在本监听器之前执行，
     * 将附魔强制设为 I 级，因此本监听器初始化时等级已为 1 级。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        if (!config.isEnabled() || !config.isAutoInitialize()) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) return;

        manager.initializeExp(item);
    }

    /**
     * 铁砧合并时自动初始化经验数据
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!config.isEnabled() || !config.isAutoInitialize()) return;

        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) return;

        /* 初始化结果物品的经验数据 */
        if (!manager.hasExpData(result)) {
            manager.initializeExp(result);
        }
    }

    /* ========== 物品类型判断 ========== */

    private boolean isTool(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String name = item.getType().name();
        return name.endsWith("_PICKAXE") || name.endsWith("_AXE") ||
               name.endsWith("_SHOVEL") || name.endsWith("_HOE") ||
               name.endsWith("SHEARS");
    }

    private boolean isMeleeWeapon(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String name = item.getType().name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") ||
               name.equals("TRIDENT") || name.equals("MACE");
    }

    private boolean isRangedWeapon(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return item.getType() == Material.BOW || item.getType() == Material.CROSSBOW ||
               item.getType() == Material.TRIDENT;
    }

    /* ========== 升级特效 ========== */

    private void playUpgradeEffects(Player player, ItemStack item, List<Enchantment> upgraded) {
        if (effects == null || !config.isEffectsEnabled()) return;

        Location loc = player.getLocation().add(0, 1, 0);
        World world = player.getWorld();

        for (Enchantment enchant : upgraded) {
            Particle particle = effects.getParticle(enchant);
            int count = effects.getCount(enchant);
            double offsetX = effects.getOffsetX(enchant);
            double offsetY = effects.getOffsetY(enchant);
            double offsetZ = effects.getOffsetZ(enchant);
            double speed = effects.getSpeed(enchant);

            world.spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, speed);
        }

        /* 升级音效 */
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
    }
}