package io.github.liyughj.xH.enchantmentLevel;

import io.github.liyughj.xH.enchantmentLevel.EnchantmentLevelConfig.ExpCategory;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 附魔经验获取事件监听器
 *
 * 设计哲学（Minecraft 原生）：
 *   用剑战斗 → 剑上的附魔成长
 *   用镐挖矿 → 镐上的附魔成长
 *   穿护甲受伤 → 护甲上的附魔成长
 *
 * 经验公式：
 *   工具: max(1, (toolBaseExp × blockRarity × globalMultiplier) + xpBonus)
 *   近战: max(1, (meleeBaseExp × entityRarity × dimensionMul × globalMultiplier) + xpBonus)
 *   远程: max(1, (rangedBaseExp × entityRarity × dimensionMul × globalMultiplier) + xpBonus)
 *   护甲: max(1, (armorBaseExp × globalMultiplier) + xpBonus)
 *
 * RPG 接口：
 *   PDC 跳过标记：物品 PDC 含对应 key → 本插件跳过该物品的 XP 发放
 *   XP Bonus：外部模块可通过 LevelConfig.setXpBonus() 注入额外经验
 *   优先级间隙：MONITOR 优先级，RPG 模块可在 HIGH/HIGHEST 插入
 */
public class EnchantmentLevelListener implements Listener {

    private final EnchantmentLevelManager manager;
    private final EnchantmentLevelConfig config;
    private final SpecialEffects effects;
    private final JavaPlugin plugin;

    public EnchantmentLevelListener(EnchantmentLevelManager manager, EnchantmentLevelConfig config, SpecialEffects effects, JavaPlugin plugin) {
        this.manager = manager;
        this.config = config;
        this.effects = effects;
        this.plugin = plugin;
    }

    /** 缓存 NamespacedKey，避免每次事件重建 */
    private final java.util.Map<String, NamespacedKey> nsKeyCache = new java.util.HashMap<>();
    private NamespacedKey nsKey(String key) {
        return nsKeyCache.computeIfAbsent(key, k -> new NamespacedKey(plugin, k));
    }

    /* ==================== 工具：挖掘方块 → TOOL 类别附魔 ==================== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!config.isEnabled() || !config.isToolSourceEnabled()) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isTool(tool) || !tool.hasItemMeta()) return;

        ItemMeta meta = tool.getItemMeta();

        /* RPG 模块接管：物品 PDC 有标记则跳过 */
        if (meta != null && meta.getPersistentDataContainer().has(
            nsKey(EnchantmentLevelData.PDC_XP_TOOL_RPG_MANAGED),
            PersistentDataType.BYTE)) return;

        if (EnchantmentLevelData.getAllEnchantments(tool).isEmpty()) return;

        if (config.isAutoInitialize() && !manager.hasExpData(tool)) {
            manager.initializeExp(tool);
        }

        /* 经验 = 基础值 × 方块稀有度 × 全局倍率 + RPG Bonus */
        Material block = event.getBlock().getType();
        double rarity = config.getBlockRarity(block);
        double base = config.getToolBaseExp() * rarity * config.getGlobalMultiplier();
        int expAmount = (int) Math.max(1, base + config.getXpBonus(ExpCategory.TOOL));

        List<Enchantment> upgraded = manager.addExpToEnchantments(tool, expAmount, ExpCategory.TOOL);

        if (!upgraded.isEmpty()) {
            playUpgradeEffects(player, tool, upgraded);
        }
    }

    /* ==================== 近战：攻击实体 → WEAPON 类别附魔 ==================== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!config.isEnabled() || !config.isMeleeSourceEnabled()) return;

        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!isMeleeWeapon(weapon) || !weapon.hasItemMeta()) return;

        ItemMeta meta = weapon.getItemMeta();

        /* RPG 模块接管：物品 PDC 有标记则跳过 */
        if (meta != null && meta.getPersistentDataContainer().has(
            nsKey(EnchantmentLevelData.PDC_XP_WEAPON_RPG_MANAGED),
            PersistentDataType.BYTE)) return;

        if (EnchantmentLevelData.getAllEnchantments(weapon).isEmpty()) return;

        if (config.isAutoInitialize() && !manager.hasExpData(weapon)) {
            manager.initializeExp(weapon);
        }

        /* 经验 = 基础值 × 生物稀有度 × 维度倍率 × 全局倍率 + RPG Bonus */
        double entityRarity = config.getEntityRarity(target.getType());
        double dimensionMul = config.getDimensionMultiplier(target.getWorld());
        double base = config.getMeleeBaseExp() * entityRarity * dimensionMul * config.getGlobalMultiplier();
        int expAmount = (int) Math.max(1, base + config.getXpBonus(ExpCategory.WEAPON));

        List<Enchantment> upgraded = manager.addExpToEnchantments(weapon, expAmount, ExpCategory.WEAPON);

        if (!upgraded.isEmpty()) {
            playUpgradeEffects(player, weapon, upgraded);
        }
    }

    /* ==================== 远程：箭矢命中 → BOW 类别附魔 ==================== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!config.isEnabled() || !config.isRangedSourceEnabled()) return;

        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player player)) return;
        if (!(event.getHitEntity() instanceof LivingEntity target)) return;

        ItemStack bow = player.getInventory().getItemInMainHand();
        if (!isRangedWeapon(bow)) {
            bow = player.getInventory().getItemInOffHand();
            if (!isRangedWeapon(bow)) return;
        }
        if (!bow.hasItemMeta()) return;

        ItemMeta meta = bow.getItemMeta();

        /* RPG 模块接管：物品 PDC 有标记则跳过 */
        if (meta != null && meta.getPersistentDataContainer().has(
            nsKey(EnchantmentLevelData.PDC_XP_BOW_RPG_MANAGED),
            PersistentDataType.BYTE)) return;

        if (EnchantmentLevelData.getAllEnchantments(bow).isEmpty()) return;

        if (config.isAutoInitialize() && !manager.hasExpData(bow)) {
            manager.initializeExp(bow);
        }

        /* 经验 = 基础值 × 生物稀有度 × 维度倍率 × 全局倍率 + RPG Bonus */
        double entityRarity = config.getEntityRarity(target.getType());
        double dimensionMul = config.getDimensionMultiplier(target.getWorld());
        double base = config.getRangedBaseExp() * entityRarity * dimensionMul * config.getGlobalMultiplier();
        int expAmount = (int) Math.max(1, base + config.getXpBonus(ExpCategory.BOW));

        List<Enchantment> upgraded = manager.addExpToEnchantments(bow, expAmount, ExpCategory.BOW);

        if (!upgraded.isEmpty()) {
            playUpgradeEffects(player, bow, upgraded);
        }
    }

    /* ==================== 护甲：受到伤害 → ARMOR 类别附魔 ==================== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!config.isEnabled() || !config.isArmorSourceEnabled()) return;

        if (!(event.getEntity() instanceof Player player)) return;

        double base = config.getArmorBaseExp() * config.getGlobalMultiplier();
        int expAmount = (int) Math.max(1, base + config.getXpBonus(ExpCategory.ARMOR));

        PlayerInventory inv = player.getInventory();
        ItemStack[] armor = {inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()};

        for (ItemStack piece : armor) {
            if (piece == null || piece.getType().isAir() || !piece.hasItemMeta()) continue;

            ItemMeta meta = piece.getItemMeta();

            /* RPG 模块接管：物品 PDC 有标记则跳过 */
            if (meta != null && meta.getPersistentDataContainer().has(
                nsKey(EnchantmentLevelData.PDC_XP_ARMOR_RPG_MANAGED),
                PersistentDataType.BYTE)) continue;

            if (EnchantmentLevelData.getAllEnchantments(piece).isEmpty()) continue;

            if (config.isAutoInitialize() && !manager.hasExpData(piece)) {
                manager.initializeExp(piece);
            }

            List<Enchantment> upgraded = manager.addExpToEnchantments(piece, expAmount, ExpCategory.ARMOR);

            if (!upgraded.isEmpty()) {
                playUpgradeEffects(player, piece, upgraded);
            }
        }
    }

    /* ==================== 初始化 ==================== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        if (!config.isEnabled() || !config.isAutoInitialize()) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) return;

        manager.initializeExp(item);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!config.isEnabled() || !config.isAutoInitialize()) return;

        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) return;

        if (!manager.hasExpData(result)) {
            manager.initializeExp(result);
        }
    }

    /* ==================== 物品类型判断 ==================== */

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
               name.endsWith("_SPEAR") ||
               name.equals("TRIDENT") || name.equals("MACE");
    }

    private boolean isRangedWeapon(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return item.getType() == Material.BOW || item.getType() == Material.CROSSBOW ||
               item.getType() == Material.TRIDENT;
    }

    /* ==================== 升级特效 ==================== */

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

        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
    }
}
