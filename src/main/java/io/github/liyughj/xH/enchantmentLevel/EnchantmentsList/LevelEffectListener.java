package io.github.liyughj.xH.enchantmentLevel.EnchantmentsList;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 附魔等级效果监听器
 * 根据 Level.yml 配置，完全替代原版附魔效果
 *
 * 伤害处理流水线：
 *   1. LOWEST → 剥离原版附魔效果 / 取消原版横扫攻击事件
 *   2. LOW ~ NORMAL → RPG 模块计算基础伤害
 *   3. HIGH → 应用自定义武器伤害/火焰/击退
 *   4. HIGHEST → 护甲计算
 *   5. MONITOR → 横扫之刃范围伤害（在所有计算完成后）
 *
 * 已实现的附魔（配置了 Level.yml 则完全替代原版）：
 * - 锋利/亡灵杀手/节肢杀手：每级 +% 伤害（默认10%）
 * - 横扫之刃：最终伤害 × 横扫百分比 × 等级 → 对周围生物造成范围伤害（默认10%/级，范围3格）
 * - 火焰附加：每级 N tick 燃烧（默认80tick=4秒）
 * - 击退：每级 N 格击退（默认1格）
 * - 抢夺：每级 +% 掉落 / +% 稀有概率（默认10%/1%，与战利品表叠加）
 */
public class LevelEffectListener implements Listener {

    private static final Enchantment[] WEAPON_DAMAGE_ENCHANTS = {
        Enchantment.SHARPNESS,
        Enchantment.SMITE,
        Enchantment.BANE_OF_ARTHROPODS
    };

    private final LevelConfig levelConfig;
    private final JavaPlugin plugin;

    /** 连锁挖掘重入防护：正在连锁破坏中的方块集合 */
    private final Set<Block> chainMiningBlocks = new HashSet<>();

    /** 连锁挖掘进行中标志（阻止精准采集在连锁破坏的方块上触发保留效果） */
    private final ThreadLocal<Boolean> inChainMining = ThreadLocal.withInitial(() -> false);

    /** 经验修补重入防护（防止 player.giveExp() 递归触发 PlayerExpChangeEvent） */
    private final ThreadLocal<Boolean> inMendingProcess = ThreadLocal.withInitial(() -> false);

    /** 荆棘反伤递归防护（防止反伤互相触发无限递归） */
    private final ThreadLocal<Boolean> inThornsReflection = ThreadLocal.withInitial(() -> false);

    /** 水下呼吸分数累积（用于精确控制空气消耗率） */
    private final Map<UUID, Double> respirationFractionalRefund = new HashMap<>();

    public LevelEffectListener(JavaPlugin plugin, LevelConfig levelConfig) {
        this.plugin = plugin;
        this.levelConfig = levelConfig;
    }

    /* ==================== 第一步：剥离原版附魔效果 ==================== */

    /**
     * LOWEST 优先级：剥离原版伤害加成/燃烧，取消原版横扫攻击事件
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void stripVanillaEffects(EntityDamageByEntityEvent event) {
        /* 取消原版横扫攻击事件（由本插件完全接管） */
        if (event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) {
            stripVanillaSweep(event);
            return;
        }

        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || !weapon.hasItemMeta()) return;

        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return;

        stripVanillaWeaponDamage(event, meta);
        stripVanillaFireAspect(meta, target);
    }

    /**
     * LOWEST 优先级：剥离原版保护附魔的 EPF 计算
     * 当 Player 是受伤害方时，零化 MAGIC modifier，
     * 防止原版 EPF 与自定义保护重复减免。
     * <p>
     * 注：Paper 1.21.4 中 MAGIC modifier 没有非弃用替代 API，
     * 这是唯一能零化原版保护 EPF 的方式。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void stripVanillaProtection(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        int totalLevel = sumProtectionLevels(player, event.getCause());
        if (totalLevel <= 0) return;

        /* 零化原版保护 EPF，由自定义系统接管 */
        event.setDamage(EntityDamageEvent.DamageModifier.MAGIC, 0.0);
    }

    /**
     * 取消原版横扫攻击事件
     * 原版横扫之刃会生成独立的 ENTITY_SWEEP_ATTACK 事件，
     * 我们完全取消它，改为在 MONITOR 阶段自行计算横扫伤害
     */
    private void stripVanillaSweep(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || !weapon.hasItemMeta()) return;

        int level = weapon.getItemMeta().getEnchantLevel(Enchantment.SWEEPING_EDGE);
        if (level <= 0) return;

        String key = Enchantment.SWEEPING_EDGE.getKey().getKey();
        if (!levelConfig.hasSweepEffect(key)) return;

        /* 取消原版横扫伤害，由我们在 MONITOR 中重新计算 */
        event.setDamage(0);
        event.setCancelled(true);
    }

    private void stripVanillaWeaponDamage(EntityDamageByEntityEvent event, ItemMeta meta) {
        double vanillaBonus = 0.0;

        for (Enchantment enchant : WEAPON_DAMAGE_ENCHANTS) {
            int level = meta.getEnchantLevel(enchant);
            if (level <= 0) continue;

            String key = enchant.getKey().getKey();
            if (!levelConfig.hasDamageEffect(key)) continue;

            if (enchant == Enchantment.SHARPNESS) {
                vanillaBonus += 0.5 * level + 0.5;
            } else {
                vanillaBonus += 2.5 * level;
            }
        }

        if (vanillaBonus > 0) {
            event.setDamage(Math.max(0, event.getDamage() - vanillaBonus));
        }
    }

    private void stripVanillaFireAspect(ItemMeta meta, LivingEntity target) {
        int level = meta.getEnchantLevel(Enchantment.FIRE_ASPECT);
        if (level <= 0) return;

        String key = Enchantment.FIRE_ASPECT.getKey().getKey();
        if (!levelConfig.hasFireEffect(key)) return;

        target.setFireTicks(0);
    }

    /* ==================== 第二步：应用自定义附魔效果 ==================== */

    /**
     * HIGH 优先级：RPG 之后、护甲之前，应用武器伤害/火焰/击退
     * 不处理横扫攻击事件（已在 LOWEST 取消）
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void applyCustomEffects(EntityDamageByEntityEvent event) {
        /* 跳过横扫事件 */
        if (event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) return;

        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || !weapon.hasItemMeta()) return;

        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return;

        applyWeaponDamageBonus(event, meta);
        applyFireAspect(meta, target);
        applyKnockback(meta, player, target);
    }

    private void applyWeaponDamageBonus(EntityDamageByEntityEvent event, ItemMeta meta) {
        double bestPercent = 0.0;
        int bestLevel = 0;
        String bestKey = null;

        for (Enchantment enchant : WEAPON_DAMAGE_ENCHANTS) {
            int level = meta.getEnchantLevel(enchant);
            if (level <= 0) continue;

            String key = enchant.getKey().getKey();
            if (!levelConfig.hasDamageEffect(key)) continue;

            double percent = levelConfig.getDamagePercentPerLevel(key);
            if (percent > bestPercent || (percent == bestPercent && level > bestLevel)) {
                bestPercent = percent;
                bestLevel = level;
                bestKey = key;
            }
        }

        if (bestLevel <= 0 || bestPercent <= 0.0) return;

        double multiplier = 1.0 + (bestPercent * bestLevel / 100.0)
            + levelConfig.getDamagePercentBonus(bestKey) / 100.0;
        event.setDamage(event.getDamage() * multiplier);
    }

    private void applyFireAspect(ItemMeta meta, LivingEntity target) {
        int level = meta.getEnchantLevel(Enchantment.FIRE_ASPECT);
        if (level <= 0) return;

        String key = Enchantment.FIRE_ASPECT.getKey().getKey();
        if (!levelConfig.hasFireEffect(key)) return;

        int ticksPerLevel = levelConfig.getFireTicksPerLevel(key);
        target.setFireTicks(ticksPerLevel * level
            + levelConfig.getFireTicksBonus(key));
    }

    private void applyKnockback(ItemMeta meta, Player player, LivingEntity target) {
        int level = meta.getEnchantLevel(Enchantment.KNOCKBACK);
        if (level <= 0) return;

        /* RPG 模块接管：物品有标记则跳过 */
        if (meta.getPersistentDataContainer().has(
            new NamespacedKey(plugin, PDC_KNOCKBACK_RPG_MANAGED),
            PersistentDataType.BYTE)) return;

        String key = Enchantment.KNOCKBACK.getKey().getKey();
        if (!levelConfig.hasKnockbackEffect(key)) return;

        double blocksPerLevel = levelConfig.getKnockbackBlocksPerLevel(key);
        double totalBlocks = blocksPerLevel * level
            + levelConfig.getKnockbackBlocksBonus(key);

        Vector direction = target.getLocation().toVector()
            .subtract(player.getLocation().toVector())
            .setY(0)
            .normalize();

        double velocityStrength = totalBlocks * 0.6;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!target.isValid() || target.isDead()) return;
            target.setVelocity(target.getVelocity()
                .add(direction.multiply(velocityStrength))
                .setY(Math.min(target.getVelocity().getY() + 0.4, 0.4)));
        });
    }

    /* ==================== 第二步B：保护附魔减免 ==================== */

    /**
     * HIGHEST 优先级：在武器伤害加成（HIGH）之后、原版护甲前，
     * 应用自定义保护附魔减免。
     * <p>
     * 流水线：伤害 → 武器附魔加成(HIGH) → 保护附魔减免(HIGHEST) → 原版护甲 → RPG
     * <p>
     * 保护附魔效果：每级 2% 减免（默认），全套保护 X 配戴 4 件 = 80%
     * <p>
     * 支持的保护类型：
     * <ul>
     *   <li>protection：所有伤害类型（泛用）</li>
     *   <li>fire_protection：火焰/岩浆伤害</li>
     *   <li>blast_protection：爆炸伤害</li>
     *   <li>projectile_protection：弹射物伤害</li>
     *   <li>feather_falling：摔落伤害</li>
     * </ul>
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void applyProtectionReduction(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        double totalPercent = computeProtectionReduction(player, event.getCause());
        if (totalPercent <= 0.0) return;

        /* 减免百分比上限 100%（最小伤害 0） */
        if (totalPercent > 100.0) totalPercent = 100.0;

        double factor = 1.0 - totalPercent / 100.0;
        double currentDamage = event.getDamage();

        /* MAGIC 已在 LOWEST 归零，直接对当前伤害值（= 武器加成后的基础伤害）应用减免 */
        event.setDamage(currentDamage * factor);
    }

    /**
     * 计算玩家所有护甲中针对指定伤害类型的保护减免总百分比。
     * 每件护甲同时计算专有保护和泛用保护，取较高者。
     */
    private double computeProtectionReduction(Player player, DamageCause cause) {
        double totalPercent = 0.0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            ItemMeta meta = armor.getItemMeta();

            /* RPG 接管：有标记则跳该件 */
            if (meta.getPersistentDataContainer().has(
                new NamespacedKey(plugin, PDC_PROTECTION_RPG_MANAGED),
                PersistentDataType.BYTE)) continue;

            /* 同时计算专有保护和泛用保护，取较高者 */
            double specific = getSpecificProtection(meta, cause);
            double generic = getProtectionPercent(meta);
            totalPercent += Math.max(specific, generic);
        }

        return totalPercent;
    }

    /**
     * 获取物品上泛用保护（protection）的减免百分比
     */
    private double getProtectionPercent(ItemMeta meta) {
        int level = meta.getEnchantLevel(Enchantment.PROTECTION);
        if (level <= 0) return 0.0;
        String key = Enchantment.PROTECTION.getKey().getKey();
        if (!levelConfig.hasProtectionEffect(key)) return 0.0;
        return levelConfig.getProtectionPercentPerLevel(key) * level
            + levelConfig.getProtectionPercentBonus(key);
    }

    /**
     * 根据伤害类型获取对应的专有保护附魔减免
     */
    private double getSpecificProtection(ItemMeta meta, DamageCause cause) {
        Enchantment specific = damageCauseToProtectionEnchant(cause);
        if (specific == null) return 0.0;

        int level = meta.getEnchantLevel(specific);
        if (level <= 0) return 0.0;
        String key = specific.getKey().getKey();
        if (!levelConfig.hasProtectionEffect(key)) return 0.0;
        return levelConfig.getProtectionPercentPerLevel(key) * level
            + levelConfig.getProtectionPercentBonus(key);
    }

    /**
     * 伤害类型 → 对应的保护附魔
     */
    private Enchantment damageCauseToProtectionEnchant(DamageCause cause) {
        if (cause == null) return null;
        switch (cause) {
            case FIRE:
            case FIRE_TICK:
            case LAVA:
            case HOT_FLOOR:
                return Enchantment.FIRE_PROTECTION;
            case BLOCK_EXPLOSION:
            case ENTITY_EXPLOSION:
                return Enchantment.BLAST_PROTECTION;
            case PROJECTILE:
                return Enchantment.PROJECTILE_PROTECTION;
            case FALL:
                return Enchantment.FEATHER_FALLING;
            default:
                return null; // 无专有保护 → 回退泛用 protection
        }
    }

    /**
     * 统计玩家身上保护附魔的等级总和（用于判断是否需要剥离原版 EPF）。
     * 每件取专有保护与泛用保护的较高等级。
     */
    private int sumProtectionLevels(Player player, DamageCause cause) {
        int total = 0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            ItemMeta meta = armor.getItemMeta();

            if (meta.getPersistentDataContainer().has(
                new NamespacedKey(plugin, PDC_PROTECTION_RPG_MANAGED),
                PersistentDataType.BYTE)) continue;

            int specificLevel = 0;
            Enchantment specific = damageCauseToProtectionEnchant(cause);
            if (specific != null) {
                int level = meta.getEnchantLevel(specific);
                if (level > 0 && levelConfig.hasProtectionEffect(specific.getKey().getKey())) {
                    specificLevel = level;
                }
            }

            int genericLevel = 0;
            int protLevel = meta.getEnchantLevel(Enchantment.PROTECTION);
            if (protLevel > 0 && levelConfig.hasProtectionEffect(Enchantment.PROTECTION.getKey().getKey())) {
                genericLevel = protLevel;
            }

            total += Math.max(specificLevel, genericLevel);
        }

        return total;
    }

    /* ==================== 第二步C：火焰保护燃烧时间减免 ==================== */

    /**
     * HIGHEST 优先级：在实体被点燃时，根据火焰保护附魔减免燃烧时间。
     * <p>
     * 燃烧时间减免 = Σ 每件护甲（等级 × 2% + bonus），上限 100%
     * 减免后的燃烧时间最小为 1 tick。
     * <p>
     * 公式：newDuration = max(1, originalDuration × (1 - totalPercent / 100))
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void applyFireTickReduction(EntityCombustEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        float duration = event.getDuration();
        if (duration <= 1.0f) return;

        double totalPercent = 0.0;
        String fireKey = Enchantment.FIRE_PROTECTION.getKey().getKey();
        String protKey = Enchantment.PROTECTION.getKey().getKey();

        if (!levelConfig.hasProtectionEffect(fireKey) && !levelConfig.hasProtectionEffect(protKey)) return;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            ItemMeta meta = armor.getItemMeta();

            if (meta.getPersistentDataContainer().has(
                new NamespacedKey(plugin, PDC_FIRE_TICK_RPG_MANAGED),
                PersistentDataType.BYTE)) continue;

            /* 同时计算火焰保护和泛用保护，取较高者 */
            double specific = 0.0;
            int fireLevel = meta.getEnchantLevel(Enchantment.FIRE_PROTECTION);
            if (fireLevel > 0 && levelConfig.hasProtectionEffect(fireKey)) {
                specific = levelConfig.getProtectionPercentPerLevel(fireKey) * fireLevel
                    + levelConfig.getFireTickPercentBonus(fireKey);
            }

            double generic = 0.0;
            int protLevel = meta.getEnchantLevel(Enchantment.PROTECTION);
            if (protLevel > 0 && levelConfig.hasProtectionEffect(protKey)) {
                generic = levelConfig.getProtectionPercentPerLevel(protKey) * protLevel
                    + levelConfig.getFireTickPercentBonus(protKey);
            }

            totalPercent += Math.max(specific, generic);
        }

        if (totalPercent <= 0.0) return;
        if (totalPercent > 100.0) totalPercent = 100.0;

        float newDuration = duration * (float)(1.0 - totalPercent / 100.0);
        if (newDuration < 1.0f) newDuration = 1.0f;

        event.setDuration(newDuration);
    }

    /* ==================== 第二步D：爆炸保护击退减免 ==================== */

    /**
     * MONITOR 优先级：爆炸伤害发生后，根据爆炸保护减免击退速度。
     * <p>
     * 击退减免 = Σ 每件护甲 max(专有保护%, 泛用保护%)，上限 100%
     * 每件：等级 × protection-percent-per-level + blast_knockback bonus
     * <p>
     * 使用 1 tick 延迟确保爆炸击退已由服务端应用后再缩减。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void applyBlastKnockbackReduction(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        DamageCause cause = event.getCause();
        if (cause != DamageCause.BLOCK_EXPLOSION && cause != DamageCause.ENTITY_EXPLOSION) return;

        double totalPercent = computeBlastKnockbackReduction(player);
        if (totalPercent <= 0.0) return;
        if (totalPercent > 100.0) totalPercent = 100.0;

        double factor = 1.0 - totalPercent / 100.0;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isValid() || player.isDead()) return;
            player.setVelocity(player.getVelocity().multiply(factor));
        });
    }

    /**
     * 计算爆炸击退减免总百分比。每件取专有保护与泛用保护的较高者。
     */
    private double computeBlastKnockbackReduction(Player player) {
        String blastKey = Enchantment.BLAST_PROTECTION.getKey().getKey();
        String protKey = Enchantment.PROTECTION.getKey().getKey();

        if (!levelConfig.hasProtectionEffect(blastKey)
            && !levelConfig.hasProtectionEffect(protKey)) return 0.0;

        double totalPercent = 0.0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            ItemMeta meta = armor.getItemMeta();

            if (meta.getPersistentDataContainer().has(
                new NamespacedKey(plugin, PDC_BLAST_KNOCKBACK_RPG_MANAGED),
                PersistentDataType.BYTE)) continue;

            double specific = 0.0;
            int blastLevel = meta.getEnchantLevel(Enchantment.BLAST_PROTECTION);
            if (blastLevel > 0 && levelConfig.hasProtectionEffect(blastKey)) {
                specific = levelConfig.getProtectionPercentPerLevel(blastKey) * blastLevel
                    + levelConfig.getBlastKnockbackPercentBonus(blastKey);
            }

            double generic = 0.0;
            int protLevel = meta.getEnchantLevel(Enchantment.PROTECTION);
            if (protLevel > 0 && levelConfig.hasProtectionEffect(protKey)) {
                generic = levelConfig.getProtectionPercentPerLevel(protKey) * protLevel
                    + levelConfig.getBlastKnockbackPercentBonus(protKey);
            }

            totalPercent += Math.max(specific, generic);
        }

        return totalPercent;
    }

    /* ==================== 第二步E：弹射物保护击退减免 ==================== */

    /**
     * MONITOR 优先级：弹射物伤害发生后，根据弹射物保护减免击退速度。
     * <p>
     * 击退减免 = Σ 每件护甲 max(专有保护%, 泛用保护%)，上限 100%
     * 每件：等级 × protection-percent-per-level + projectile_knockback bonus
     * <p>
     * 使用 1 tick 延迟确保弹射物击退已由服务端应用后再缩减。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void applyProjectileKnockbackReduction(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (event.getCause() != DamageCause.PROJECTILE) return;

        double totalPercent = computeProjectileKnockbackReduction(player);
        if (totalPercent <= 0.0) return;
        if (totalPercent > 100.0) totalPercent = 100.0;

        double factor = 1.0 - totalPercent / 100.0;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isValid() || player.isDead()) return;
            player.setVelocity(player.getVelocity().multiply(factor));
        });
    }

    /**
     * 计算弹射物击退减免总百分比。每件取专有保护与泛用保护的较高者。
     */
    private double computeProjectileKnockbackReduction(Player player) {
        String projectileKey = Enchantment.PROJECTILE_PROTECTION.getKey().getKey();
        String protKey = Enchantment.PROTECTION.getKey().getKey();

        if (!levelConfig.hasProtectionEffect(projectileKey)
            && !levelConfig.hasProtectionEffect(protKey)) return 0.0;

        double totalPercent = 0.0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            ItemMeta meta = armor.getItemMeta();

            if (meta.getPersistentDataContainer().has(
                new NamespacedKey(plugin, PDC_PROJECTILE_KNOCKBACK_RPG_MANAGED),
                PersistentDataType.BYTE)) continue;

            double specific = 0.0;
            int projectileLevel = meta.getEnchantLevel(Enchantment.PROJECTILE_PROTECTION);
            if (projectileLevel > 0 && levelConfig.hasProtectionEffect(projectileKey)) {
                specific = levelConfig.getProtectionPercentPerLevel(projectileKey) * projectileLevel
                    + levelConfig.getProjectileKnockbackPercentBonus(projectileKey);
            }

            double generic = 0.0;
            int protLevel = meta.getEnchantLevel(Enchantment.PROTECTION);
            if (protLevel > 0 && levelConfig.hasProtectionEffect(protKey)) {
                generic = levelConfig.getProtectionPercentPerLevel(protKey) * protLevel
                    + levelConfig.getProjectileKnockbackPercentBonus(protKey);
            }

            totalPercent += Math.max(specific, generic);
        }

        return totalPercent;
    }

    /* ==================== 第二步F：荆棘反伤 ==================== */

    /**
     * MONITOR 优先级：在玩家受到攻击者伤害后（所有减免计算完成），
     * 概率性触发荆棘反伤。
     * <p>
     * 反伤公式：最终伤害 × 反伤百分比（%/100）
     * <p>
     * 触发概率 = Σ 每件护甲（等级 × thorns-chance-per-level + bonus chance），上限 100%
     * 反伤百分比 = Σ 每件护甲（等级 × thorns-damage-per-level + bonus damage），作为反伤伤害比例
     * <p>
     * 反伤通过 damage() 施加给攻击者（独立事件，支持 RPG 模块触发），
     * 使用防递归标志防止荆棘对荆棘的无限反射。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void applyThornsReflection(EntityDamageByEntityEvent event) {
        /* 递归防护 */
        if (inThornsReflection.get()) return;

        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;
        if (attacker.isDead()) return;

        String key = Enchantment.THORNS.getKey().getKey();
        if (!levelConfig.hasThornsEffect(key)) return;

        double totalChance = 0.0;
        double totalDamagePercent = 0.0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            ItemMeta meta = armor.getItemMeta();

            if (meta.getPersistentDataContainer().has(
                new NamespacedKey(plugin, PDC_THORNS_RPG_MANAGED),
                PersistentDataType.BYTE)) continue;

            int level = meta.getEnchantLevel(Enchantment.THORNS);
            if (level <= 0) continue;

            totalChance += levelConfig.getThornsChancePerLevel(key) * level
                + levelConfig.getThornsChanceBonus(key);
            totalDamagePercent += levelConfig.getThornsDamagePerLevel(key) * level
                + levelConfig.getThornsDamageBonus(key);
        }

        if (totalChance <= 0.0 || totalDamagePercent <= 0.0) return;
        if (totalChance > 100.0) totalChance = 100.0;

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        if (rand.nextDouble() * 100.0 >= totalChance) return;

        /* 反伤伤害 = 玩家最终承受的伤害 × 反伤比例 */
        double finalDamage = event.getFinalDamage();
        double reflectDamage = finalDamage * totalDamagePercent / 100.0;
        if (reflectDamage <= 0.0) return;

        /* 通过 damage() 施加反伤（触发独立伤害事件链） */
        inThornsReflection.set(true);
        try {
            attacker.damage(reflectDamage, player);
        } finally {
            inThornsReflection.set(false);
        }
    }

    /* ==================== 第二步G：水下呼吸 ==================== */

    /**
     * HIGHEST 优先级：在水下空气减少时，根据水下呼吸附魔降低消耗率。
     * <p>
     * 有效空气总量 = 300 + 等级 × seconds-per-level × 20 tick
     * 空气消耗率 = 300 / 有效总量（原速的百分比）
     * <p>
     * 默认：每级 3 秒 → 等级 X = 60 秒有效时间（原版 15s × 4）
     * <p>
     * 使用分数累积器精确控制每 tick 的消耗量。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void applyRespiration(EntityAirChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        int newAir = event.getAmount();
        int currentAir = player.getRemainingAir();
        if (newAir >= currentAir) return; // 空气增加时不干预

        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null || !helmet.hasItemMeta()) return;

        ItemMeta meta = helmet.getItemMeta();
        if (meta.getPersistentDataContainer().has(
            new NamespacedKey(plugin, PDC_RESPIRATION_RPG_MANAGED),
            PersistentDataType.BYTE)) return;

        int level = meta.getEnchantLevel(Enchantment.RESPIRATION);
        if (level <= 0) return;

        String key = Enchantment.RESPIRATION.getKey().getKey();
        if (!levelConfig.hasRespirationEffect(key)) return;

        double secondsPerLevel = levelConfig.getRespirationSecondsPerLevel(key)
            + levelConfig.getRespirationSecondsBonus(key);

        /* 有效总 tick 数：原版 300 + 水下呼吸额外时间 */
        double effectiveTotal = 300.0 + level * secondsPerLevel * 20.0;
        double drainFactor = 300.0 / effectiveTotal; // 每次应消耗的实际比例

        int decrease = currentAir - newAir; // >0
        double fractionalDrain = decrease * drainFactor
            + respirationFractionalRefund.getOrDefault(player.getUniqueId(), 0.0);
        int actualDrain = (int) fractionalDrain;
        respirationFractionalRefund.put(player.getUniqueId(), fractionalDrain - actualDrain);

        event.setAmount(Math.max(0, currentAir - actualDrain));
    }

    /* ==================== 第三步：横扫之刃范围伤害 ==================== */

    /**
     * MONITOR 优先级：在所有计算（含护甲）完成之后，对周围生物造成横扫伤害
     *
     * 横扫伤害 = 最终伤害 × (横扫百分比 × 横扫等级 / 100)
     * 对攻击目标周围 sweepRange 格内的所有其他 LivingEntity 造成伤害
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void applySweepingDamage(EntityDamageByEntityEvent event) {
        /* 跳过横扫事件和玩家互伤 */
        if (event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) return;

        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || !weapon.hasItemMeta()) return;

        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return;

        int sweepLevel = meta.getEnchantLevel(Enchantment.SWEEPING_EDGE);
        if (sweepLevel <= 0) return;

        String key = Enchantment.SWEEPING_EDGE.getKey().getKey();
        if (!levelConfig.hasSweepEffect(key)) return;

        double percentPerLevel = levelConfig.getSweepDamagePercentPerLevel(key);
        double range = levelConfig.getSweepRange(key)
            + levelConfig.getSweepRangeBonus(key);
        if (range <= 0) range = 3.0;

        /* 最终伤害（经锋利/护甲等全部计算后） */
        double finalDamage = event.getFinalDamage();

        /* 横扫伤害 = 最终伤害 × (横扫百分比 × 等级 / 100 + bonus/100) */
        double sweepPercent = percentPerLevel * sweepLevel / 100.0
            + levelConfig.getSweepPercentBonus(key) / 100.0;
        double sweepDamage = finalDamage * sweepPercent;

        if (sweepDamage <= 0) return;

        /* 对周围生物造成横扫伤害 */
        for (LivingEntity nearby : target.getLocation().getNearbyLivingEntities(range)) {
            /* 跳过攻击目标本身和玩家 */
            if (nearby == target || nearby == player) continue;
            /* 跳过无敌或已死亡的实体 */
            if (nearby.isDead() || nearby.isInvulnerable()) continue;

            nearby.damage(sweepDamage, player);
        }
    }

    /* ==================== 第三步：精准采集 & 连锁挖掘 ==================== */

    /** 会被视为液体的方块类型 */
    private static final Set<Material> LIQUIDS = EnumSet.of(
        Material.WATER, Material.LAVA
    );

    /** 矿物原矿类型（精准采集"不破坏但掉落"仅对这些生效） */
    private static final Set<Material> ORES = EnumSet.of(
        Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
        Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
        Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
        Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
        Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
        Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
        Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
        Material.ANCIENT_DEBRIS
    );

    /** 方块被玩家放置的标记（在所在区块的 PDC 中） */
    private static final String PDC_PLACED_PREFIX = "placed_";

    /** 方块被精准采集保留的标记（下次挖掘必定掉落） */
    private static final String PDC_SILK_KEPT_PREFIX = "silk_kept_";

    /* ---------- 玩家放置方块追踪 ---------- */

    /**
     * 追踪玩家放置的方块（用于区分自然生成 vs 玩家放置的矿物）
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!ORES.contains(block.getType())) return;

        int cx = block.getX() & 15;
        int cy = block.getY();
        int cz = block.getZ() & 15;
        String pdcKey = PDC_PLACED_PREFIX + cx + "_" + cy + "_" + cz;

        block.getChunk().getPersistentDataContainer().set(
            new NamespacedKey(plugin, pdcKey),
            PersistentDataType.BYTE, (byte) 1
        );
    }

    /* ---------- 精准采集"不破坏但掉落" ---------- */

    /**
     * LOW 优先级：精准采集对矿物原矿的特殊效果（在方块被破坏前拦截，早于时运）
     * <p>
     * 每级精准采集有 keep-block-chance-per-level% 概率：
     * - 不破坏方块，但掉落精准采集产物（矿石自身）
     * - 时运级联概率系统影响掉落数量
     * - 被打上标记的方块，下次挖掘必定破坏（不会再次保留）
     * <p>
     * 仅对自然生成的矿物原矿有效。玩家放置的矿物使用原版精准采集效果。
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSilkTouchMine(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material blockType = block.getType();

        /* 只对矿物原矿生效 */
        if (!ORES.contains(blockType)) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || !tool.hasItemMeta()) return;

        ItemMeta meta = tool.getItemMeta();
        if (meta == null) return;

        int silkLevel = meta.getEnchantLevel(Enchantment.SILK_TOUCH);
        if (silkLevel <= 0) return;

        /* 连锁挖掘触发的破坏不触发精准采集保留 */
        if (inChainMining.get()) return;

        /* RPG 模块接管：工具 PDC 有标记则跳过 */
        if (meta.getPersistentDataContainer().has(
            new NamespacedKey(plugin, PDC_SILK_TOUCH_RPG_MANAGED),
            PersistentDataType.BYTE)) return;

        String key = Enchantment.SILK_TOUCH.getKey().getKey();
        if (!levelConfig.hasSilkTouchEffect(key)) return;

        /* 玩家放置的矿物 → 原版行为（正常破坏） */
        int cx = block.getX() & 15;
        int cy = block.getY();
        int cz = block.getZ() & 15;
        String placedPdcKey = PDC_PLACED_PREFIX + cx + "_" + cy + "_" + cz;

        if (isBlockPdcMarked(block, placedPdcKey)) {
            /* 正常破坏，清除放置标记 */
            removeBlockPdcMark(block, placedPdcKey);
            return;
        }

        /* 检查是否已被精准采集保留过（标记方块） */
        String keptPdcKey = PDC_SILK_KEPT_PREFIX + cx + "_" + cy + "_" + cz;
        boolean isKept = isBlockPdcMarked(block, keptPdcKey);

        if (isKept) {
            /* 已标记 → 必定破坏，清除标记，正常掉落 */
            removeBlockPdcMark(block, keptPdcKey);
            return;
        }

        /* 计算保留概率 = min(每级概率 × 等级 / 100 + bonus/100, 100%) */
        double chancePerLevel = levelConfig.getSilkTouchKeepChancePerLevel(key);
        double chance = Math.min(chancePerLevel * silkLevel / 100.0
            + levelConfig.getSilkTouchChanceBonus(key) / 100.0, 1.0);

        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            /* 未触发，正常破坏 */
            return;
        }

        /* ===== 触发"不破坏但掉落"效果 ===== */

        /* 取消原版破坏事件，方块将保留 */
        event.setCancelled(true);

        /* 标记此方块（下次必定破坏） */
        markBlockPdc(block, keptPdcKey);

        /* 掉落精准采集产物（矿石自身），受时运级联概率影响 */
        int effectiveFortune = getEffectiveFortuneLevel(meta, Enchantment.FORTUNE);
        int dropAmount = Math.max(1, effectiveFortune);
        ItemStack baseDrop = new ItemStack(blockType, dropAmount);
        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().dropItemNaturally(dropLoc, baseDrop);

        /* 工具耐久消耗 */
        if (!player.getGameMode().name().equals("CREATIVE")) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (mainHand.getType().getMaxDurability() > 0) {
                ItemMeta dm = mainHand.getItemMeta();
                if (dm instanceof org.bukkit.inventory.meta.Damageable damageable) {
                    int newDamage = damageable.getDamage() + 1;
                    if (newDamage > mainHand.getType().getMaxDurability()) {
                        mainHand.setAmount(0);
                    } else {
                        damageable.setDamage(newDamage);
                        mainHand.setItemMeta(dm);
                    }
                }
            }
        }
    }

    /* ---------- 时运级联概率掉落系统 ---------- */

    /**
     * NORMAL 优先级：时运完全替代原版掉落计算
     * <p>
     * 级联概率机制：
     * 从最高时运等级开始向下检查，每级有独立触发概率
     * 概率公式：baseProb - decrement × (level - 1)
     * 默认：1级=50%, 2级=45%, ..., 10级=5%
     * 首次命中即以其等级作为掉落倍率，不再继续检查
     * <p>
     * 掉落 = 原始掉落数量 × 命中等级
     * 兼容效率连锁挖掘 & 精准采集不破坏掉落
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFortuneBreak(BlockBreakEvent event) {
        /* 不处理精准采集已取消的事件（方块被保留） */
        Block block = event.getBlock();

        /* 跳过已被破坏的空气方块（连锁挖掘等副作用） */
        if (block.getType() == Material.AIR) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || !tool.hasItemMeta()) return;

        ItemMeta meta = tool.getItemMeta();
        if (meta == null) return;

        int fortuneLevel = meta.getEnchantLevel(Enchantment.FORTUNE);
        if (fortuneLevel <= 0) return;

        /* RPG 模块接管：工具 PDC 有标记则跳过 */
        if (meta.getPersistentDataContainer().has(
            new NamespacedKey(plugin, PDC_FORTUNE_RPG_MANAGED),
            PersistentDataType.BYTE)) return;

        String key = Enchantment.FORTUNE.getKey().getKey();
        if (!levelConfig.hasFortuneEffect(key)) return;

        /* 计算级联命中等级 */
        int effectiveLevel = getEffectiveFortuneLevel(meta, Enchantment.FORTUNE);
        if (effectiveLevel <= 1) return; // 1倍 = 原版，无需处理

        /* 计算工具剥离时运后的基础掉落 */
        ItemStack toolNoFortune = tool.clone();
        ItemMeta metaNoFortune = toolNoFortune.getItemMeta();
        metaNoFortune.removeEnchant(Enchantment.FORTUNE);
        toolNoFortune.setItemMeta(metaNoFortune);

        Collection<ItemStack> baseDrops = block.getDrops(toolNoFortune, player);
        List<ItemStack> newDrops = new ArrayList<>();

        for (ItemStack base : baseDrops) {
            if (base.getType() == Material.AIR || base.getAmount() <= 0) continue;

            int newAmount = base.getAmount() * effectiveLevel;
            newAmount = Math.min(newAmount, base.getMaxStackSize() * 64); // 安全上限

            ItemStack multiplied = base.clone();
            multiplied.setAmount(Math.min(newAmount, base.getMaxStackSize()));
            newDrops.add(multiplied);

            /* 如果超出单格上限，拆分为多个物品 */
            int remaining = newAmount - base.getMaxStackSize();
            while (remaining > 0) {
                ItemStack extra = base.clone();
                extra.setAmount(Math.min(remaining, base.getMaxStackSize()));
                newDrops.add(extra);
                remaining -= base.getMaxStackSize();
            }
        }

        if (!newDrops.isEmpty()) {
            /* 清除原版掉落的 exp orb（通过设置 drops 替代） */
            // event.setExpToDrop(0);  // 经验不受时运影响，保留原版
            event.getBlock().getWorld().dropItemNaturally(
                block.getLocation().add(0.5, 0.5, 0.5),
                newDrops.remove(0)
            );
            for (ItemStack drop : newDrops) {
                event.getBlock().getWorld().dropItemNaturally(
                    block.getLocation().add(0.5, 0.5, 0.5),
                    drop
                );
            }
            event.setDropItems(false);
        }
    }

    /**
     * 级联概率计算：从最高等级向下检查，返回命中等级
     * 概率公式：baseProb% - decrement% × (level - 1)
     * 默认：1级=50%, 2级=45%, 3级=40%, ..., 10级=5%
     * 全部未命中则返回 1（原版基础掉落）
     */
    private int getEffectiveFortuneLevel(ItemMeta meta, Enchantment enchant) {
        int level = meta.getEnchantLevel(enchant);
        if (level <= 0) return 1;

        String key = enchant.getKey().getKey();
        double baseProb = levelConfig.getFortuneBaseProbability(key)
            + levelConfig.getFortuneBaseProbBonus(key);
        double decrement = levelConfig.getFortuneProbDecrement(key)
            + levelConfig.getFortuneDecrementBonus(key);

        ThreadLocalRandom rand = ThreadLocalRandom.current();

        /* 从最高等级向下测试 */
        for (int checkLevel = level; checkLevel >= 1; checkLevel--) {
            double probability = (baseProb - decrement * (checkLevel - 1)) / 100.0;
            if (probability <= 0) continue;
            if (rand.nextDouble() < probability) {
                return checkLevel;
            }
        }

        /* 全部未命中，默认 1 倍 */
        return 1;
    }

    /* ---------- PDC 工具方法 ---------- */

    private boolean isBlockPdcMarked(Block block, String pdcKey) {
        return block.getChunk().getPersistentDataContainer().has(
            new NamespacedKey(plugin, pdcKey),
            PersistentDataType.BYTE
        );
    }

    private void markBlockPdc(Block block, String pdcKey) {
        block.getChunk().getPersistentDataContainer().set(
            new NamespacedKey(plugin, pdcKey),
            PersistentDataType.BYTE, (byte) 1
        );
    }

    private void removeBlockPdcMark(Block block, String pdcKey) {
        block.getChunk().getPersistentDataContainer().remove(
            new NamespacedKey(plugin, pdcKey)
        );
    }

    /**
     * MONITOR 优先级：方块已被破坏后，连锁挖掘周围同类型方块
     * <p>
     * 每级效率附魔额外破坏 1 个同类型方块（最多10级=10个额外方块）。
     * 保留原版挖掘速度不变，仅在原版基础上添加连锁效果。
     * <p>
     * 防刷物品措施：
     * <ul>
     *   <li>主方块无论旁边是否有液体都会正常破坏</li>
     *   <li>额外方块检查6面是否有液体相邻，有则跳过</li>
     *   <li>只破坏同 Material 的方块</li>
     *   <li>按距离排序取最近的 N 个</li>
     *   <li>重入防护：连锁破坏的方块不会再触发连锁</li>
     *   <li>检查额外方块是否已被其他方式修改（已变为空气）则跳过</li>
     * </ul>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block brokenBlock = event.getBlock();

        /* 重入防护：由连锁挖掘触发的破坏不再次连锁 */
        synchronized (chainMiningBlocks) {
            if (chainMiningBlocks.remove(brokenBlock)) {
                return;
            }
        }

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || !tool.hasItemMeta()) return;

        ItemMeta meta = tool.getItemMeta();
        if (meta == null) return;

        int efficiencyLevel = meta.getEnchantLevel(Enchantment.EFFICIENCY);
        if (efficiencyLevel <= 0) return;

        /* RPG 模块接管：工具 PDC 有标记则跳过 */
        if (meta.getPersistentDataContainer().has(
            new NamespacedKey(plugin, PDC_EFFICIENCY_RPG_MANAGED),
            PersistentDataType.BYTE)) return;

        String key = Enchantment.EFFICIENCY.getKey().getKey();
        if (!levelConfig.hasChainEffect(key)) return;

        double range = levelConfig.getChainRange(key)
            + levelConfig.getChainRangeBonus(key);
        if (range <= 0) range = 5.0;

        Material targetMaterial = brokenBlock.getType();

        /* BFS 搜索同类型且安全的方块，按距离排序 */
        List<Block> candidates = new ArrayList<>();
        Queue<Block> queue = new ArrayDeque<>();
        Set<Block> visited = new HashSet<>();

        queue.add(brokenBlock);
        visited.add(brokenBlock);

        while (!queue.isEmpty()) {
            Block current = queue.poll();

            for (BlockFace face : DIRECTIONS) {
                Block neighbor = current.getRelative(face);
                if (!visited.add(neighbor)) continue;
                if (brokenBlock.getLocation().distanceSquared(neighbor.getLocation()) > range * range) continue;

                if (neighbor.getType() == targetMaterial) {
                    if (isSafeToChain(neighbor)) {
                        candidates.add(neighbor);
                    }
                    queue.add(neighbor);
                }
            }
        }

        /* 按距离排序，取最近的 N 个（N = 效率等级，最多10级 = 10个额外方块） */
        candidates.sort((a, b) -> Double.compare(
            brokenBlock.getLocation().distanceSquared(a.getLocation()),
            brokenBlock.getLocation().distanceSquared(b.getLocation())
        ));

        int maxExtra = Math.min(efficiencyLevel, 10);

        /* 水下速掘：玩家在水中时，根据头盔水下速掘等级缩链挖掘数量 */
        if (maxExtra > 1 && player.getRemainingAir() < player.getMaximumAir()) {
            ItemStack helmet = player.getInventory().getHelmet();
            if (helmet != null && helmet.hasItemMeta()) {
                ItemMeta hMeta = helmet.getItemMeta();
                if (!hMeta.getPersistentDataContainer().has(
                    new NamespacedKey(plugin, PDC_AQUA_AFFINITY_RPG_MANAGED),
                    PersistentDataType.BYTE)) {
                    int aquaLevel = hMeta.getEnchantLevel(Enchantment.AQUA_AFFINITY);
                    String aquaKey = Enchantment.AQUA_AFFINITY.getKey().getKey();
                    if (aquaLevel > 0 && levelConfig.hasAquaAffinityChainEffect(aquaKey)) {
                        double percentPerLevel = levelConfig.getAquaAffinityChainPercentPerLevel(aquaKey);
                        double bonus = levelConfig.getAquaAffinityChainPercentBonus(aquaKey);
                        double chainPercent = aquaLevel * percentPerLevel + bonus;
                        if (chainPercent > 100.0) chainPercent = 100.0;
                        maxExtra = Math.max(1, (int)Math.ceil(maxExtra * chainPercent / 100.0));
                    }
                }
            }
        }

        inChainMining.set(true);
        try {
            for (int i = 0; i < Math.min(candidates.size(), maxExtra); i++) {
                Block extra = candidates.get(i);

                /* 再次确认方块未被其他方式修改 */
                if (extra.getType() != targetMaterial) continue;

                /* 标记为连锁挖掘中，防止递归触发 */
                synchronized (chainMiningBlocks) {
                    chainMiningBlocks.add(extra);
                }

                /* 清除可能存在的精准采集保留标记（连锁破坏时不会触发保留逻辑） */
                int ecx = extra.getX() & 15;
                int ecy = extra.getY();
                int ecz = extra.getZ() & 15;
                removeBlockPdcMark(extra, PDC_SILK_KEPT_PREFIX + ecx + "_" + ecy + "_" + ecz);

                /* 使用 breakNaturally 触发正常掉落（附魔工具的效果会自动应用） */
                extra.breakNaturally(tool);
            }
        } finally {
            inChainMining.set(false);
        }
    }

    /**
     * 检查方块是否安全可连锁挖掘
     * 排除条件：6面相邻有液体
     */
    private boolean isSafeToChain(Block block) {
        for (BlockFace face : DIRECTIONS) {
            Material neighborType = block.getRelative(face).getType();
            if (LIQUIDS.contains(neighborType)) {
                return false;
            }
        }
        return true;
    }

    /** 6个主方向 */
    private static final BlockFace[] DIRECTIONS = {
        BlockFace.UP, BlockFace.DOWN,
        BlockFace.NORTH, BlockFace.SOUTH,
        BlockFace.EAST, BlockFace.WEST
    };

    /* ==================== 耐久（不消耗/返还） ==================== */

    /**
     * RPG 模块接管耐久管理的 PDC 标记键
     * 如果物品有此标记，本耐久系统将跳过，由 RPG 模块自行处理
     */
    private static final String PDC_DURABILITY_RPG_MANAGED = "durability_rpg_managed";

    /* ---- RPG 模块接管标记（其他附魔） ---- */
    /**
     * 物品 PDC 中设置此键 → 击退系统跳过，RPG 接管
     */
    private static final String PDC_KNOCKBACK_RPG_MANAGED = "knockback_rpg_managed";
    /**
     * 物品 PDC 中设置此键 → 精准采集系统跳过，RPG 接管
     */
    private static final String PDC_SILK_TOUCH_RPG_MANAGED = "silk_touch_rpg_managed";
    /**
     * 物品 PDC 中设置此键 → 时运系统跳过，RPG 接管
     */
    private static final String PDC_FORTUNE_RPG_MANAGED = "fortune_rpg_managed";
    /**
     * 物品 PDC 中设置此键 → 效率连锁挖掘跳过，RPG 接管
     */
    private static final String PDC_EFFICIENCY_RPG_MANAGED = "efficiency_rpg_managed";
    /**
     * 物品 PDC 中设置此键 → 抢夺系统跳过，RPG 接管
     */
    private static final String PDC_LOOTING_RPG_MANAGED = "looting_rpg_managed";
    /**
     * 物品 PDC 中设置此键 → 经验修补系统跳过，RPG 接管
     */
    private static final String PDC_MENDING_RPG_MANAGED = "mending_rpg_managed";
    /**
     * 物品 PDC 中设置此键 → 保护附魔系统跳过，RPG 接管
     */
    private static final String PDC_PROTECTION_RPG_MANAGED = "protection_rpg_managed";
    /**
     * 物品 PDC 中设置此键 → 火焰燃烧时间减免跳过该件，RPG 接管
     */
    private static final String PDC_FIRE_TICK_RPG_MANAGED = "fire_tick_rpg_managed";
    /**
     * 物品 PDC 中设置此键 → 爆炸击退减免跳过该件，RPG 接管
     */
    private static final String PDC_BLAST_KNOCKBACK_RPG_MANAGED = "blast_knockback_rpg_managed";
    /**
     * 物品 PDC 中设置此键 → 弹射物击退减免跳过该件，RPG 接管
     */
    private static final String PDC_PROJECTILE_KNOCKBACK_RPG_MANAGED = "projectile_knockback_rpg_managed";
    /**
     * 物品 PDC 中设置此键 → 荆棘反伤跳过该件，RPG 接管
     */
    private static final String PDC_THORNS_RPG_MANAGED = "thorns_rpg_managed";
    /**
     * 物品 PDC 中设置此键 → 水下呼吸跳过该头盔，RPG 接管
     */
    private static final String PDC_RESPIRATION_RPG_MANAGED = "respiration_rpg_managed";
    /**
     * 物品 PDC 中设置此键 → 水下速掘链挖掘限制跳过该头盔，RPG 接管
     */
    private static final String PDC_AQUA_AFFINITY_RPG_MANAGED = "aqua_affinity_rpg_managed";

    /**
     * LOWEST 优先级：耐久附魔效果
     * <p>
     * 优先剥离原版耐久，使用自定义概率系统：
     * <ol>
     *   <li>不消耗判定：每级 save-chance-per-level%（默认7.5%）→ 成功则事件伤害归零</li>
     *   <li>返还判定：每级 return-chance-per-level%（默认2.5%）→ 成功则计算返还量</li>
     *   <li>都失败 → 正常消耗耐久</li>
     * </ol>
     * <p>
     * 返还耐久公式：ceil(原始耐久消耗 × 返还倍率 × 等级 / 100)
     * 默认：1级返还50%，2级100%，...，10级500%
     * <p>
     * RPG 窗口：物品 PDC 中有 {@code durability_rpg_managed} 标记时跳过，
     * 由 RPG 模块负责处理。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (!item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();

        /* RPG 模块接管窗口：由 RPG 模块在更高优先级处理 */
        if (meta.getPersistentDataContainer().has(
            new NamespacedKey(plugin, PDC_DURABILITY_RPG_MANAGED),
            PersistentDataType.BYTE)) {
            return;
        }

        int level = meta.getEnchantLevel(Enchantment.UNBREAKING);
        if (level <= 0) return;

        String key = Enchantment.UNBREAKING.getKey().getKey();
        if (!levelConfig.hasUnbreakingEffect(key)) return;

        double saveChance = levelConfig.getUnbreakingSaveChancePerLevel(key);
        double returnChance = levelConfig.getUnbreakingReturnChancePerLevel(key);
        double returnRate = levelConfig.getUnbreakingReturnRatePerLevel(key);

        /* 完全剥离原版耐久，从基础消耗重新计算
         * PlayerItemDamageEvent 触发时原版耐久已处理完毕，
         * 工具每次使用固定消耗 1 耐久（基础值） */
        int baseDamage = 1;
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        /* 第一判定：不消耗耐久 */
        double saveProb = Math.min(saveChance * level / 100.0
            + levelConfig.getUnbreakingSaveBonus(key) / 100.0, 1.0);
        if (rand.nextDouble() < saveProb) {
            event.setDamage(0);
            return;
        }

        /* 第二判定：返还耐久
         * 返还量 = ceil(基础消耗 × (返还倍率+bonus) × 等级 / 100)
         * 默认：1级=50%, 2级=100%, ..., 10级=500% */
        double returnProb = Math.min(returnChance * level / 100.0
            + levelConfig.getUnbreakingReturnBonus(key) / 100.0, 1.0);
        if (rand.nextDouble() < returnProb) {
            int returnAmount = (int) Math.ceil(baseDamage
                * (returnRate + levelConfig.getUnbreakingReturnRateBonus(key))
                * level / 100.0);

            /* 取消原版耐久消耗，手动计算净效果 */
            event.setDamage(0);

            if (meta instanceof org.bukkit.inventory.meta.Damageable dm) {
                int currentDamage = dm.getDamage();
                int netChange = baseDamage - returnAmount;

                if (netChange <= -currentDamage) {
                    /* 返还全部损伤 → 完全修复 */
                    dm.setDamage(0);
                } else if (currentDamage + netChange > item.getType().getMaxDurability()) {
                    /* 超出最大耐久 → 工具损坏 */
                    item.setAmount(0);
                    return;
                } else {
                    dm.setDamage(currentDamage + netChange);
                }
                item.setItemMeta(dm);
            }
            return;
        }

        /* 第三（默认）：正常消耗基础耐久（剥离原版） */
        event.setDamage(baseDamage);
    }

    /* ==================== 经验修补 ==================== */

    /**
     * LOWEST 优先级：拦截经验获取，使用自定义 Mending 公式修复物品
     * <p>
     * 修复量 = max(1, 等级 × 倍率) 每经验（默认 1 级 = 1 耐久/经验，V 级 = 5）
     * mending-durability-per-xp 每级每经验修复耐久点数
     * bonus 直接加到最终修复量上
     * <p>
     * RPG 窗口：物品 PDC 中有 {@code mending_rpg_managed} 标记时跳过，
     * 由 RPG 模块负责处理。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        /* 重入防护：由 giveExp() 触发的递归直接跳过 */
        if (inMendingProcess.get()) return;

        int totalXp = event.getAmount();
        if (totalXp <= 0) return;

        Player player = event.getPlayer();

        /* 收集有自定义 Mending 的物品（手持 + 护甲） */
        List<ItemStack> mendingItems = new ArrayList<>();
        addMendingItem(mendingItems, player.getInventory().getItemInMainHand());
        addMendingItem(mendingItems, player.getInventory().getItemInOffHand());
        addMendingItem(mendingItems, player.getInventory().getHelmet());
        addMendingItem(mendingItems, player.getInventory().getChestplate());
        addMendingItem(mendingItems, player.getInventory().getLeggings());
        addMendingItem(mendingItems, player.getInventory().getBoots());

        if (mendingItems.isEmpty()) return;

        int remainingXp = totalXp;
        boolean anyRepaired = false;

        for (ItemStack item : mendingItems) {
            if (remainingXp <= 0) break;

            ItemMeta itemMeta = item.getItemMeta();
            if (!(itemMeta instanceof org.bukkit.inventory.meta.Damageable dm)) continue;

            int currentDamage = dm.getDamage();
            if (currentDamage <= 0) continue;

            int level = itemMeta.getEnchantLevel(Enchantment.MENDING);
            String key = Enchantment.MENDING.getKey().getKey();

            double multiplier = levelConfig.getMendingDurabilityPerXp(key);
            double bonus = levelConfig.getMendingDurabilityBonus(key);
            double durabilityPerXp = Math.max(1, multiplier * level + bonus);

            /* XP 需求量 = ceil(当前损伤 / 每经验修复量) */
            int xpNeeded = (int) Math.ceil(currentDamage / durabilityPerXp);
            int xpToUse = Math.min(remainingXp, xpNeeded);

            int repairAmount = (int) (xpToUse * durabilityPerXp);
            int newDamage = Math.max(0, currentDamage - repairAmount);
            dm.setDamage(newDamage);
            item.setItemMeta(dm);

            remainingXp -= xpToUse;
            anyRepaired = true;
        }

        if (anyRepaired) {
            /* 已处理，取消原版 XP 获取（同时阻止原版 Mending） */
            event.setAmount(0);
            /* 返还剩余 XP */
            if (remainingXp > 0) {
                inMendingProcess.set(true);
                try {
                    player.giveExp(remainingXp);
                } finally {
                    inMendingProcess.set(false);
                }
            }
        }
    }

    /**
     * 将物品添加到 Mending 列表（检查 PDC 标记 + 附魔）
     */
    private void addMendingItem(List<ItemStack> list, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();

        /* RPG 模块接管：有标记则跳过 */
        if (meta.getPersistentDataContainer().has(
            new NamespacedKey(plugin, PDC_MENDING_RPG_MANAGED),
            PersistentDataType.BYTE)) return;

        if (meta.getEnchantLevel(Enchantment.MENDING) > 0
            && levelConfig.hasMendingEffect(Enchantment.MENDING.getKey().getKey())) {
            list.add(item);
        }
    }

    /* ==================== 抢夺（掉落物） ==================== */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (weapon == null || !weapon.hasItemMeta()) return;

        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return;

        int lootingLevel = meta.getEnchantLevel(Enchantment.LOOTING);
        if (lootingLevel <= 0) return;

        /* RPG 模块接管：武器 PDC 有标记则跳过 */
        if (meta.getPersistentDataContainer().has(
            new NamespacedKey(plugin, PDC_LOOTING_RPG_MANAGED),
            PersistentDataType.BYTE)) return;

        String key = Enchantment.LOOTING.getKey().getKey();
        if (!levelConfig.hasLootingEffect(key)) return;

        double maxDropPercentPerLevel = levelConfig.getLootingMaxDropPercentPerLevel(key);
        double rareChancePercentPerLevel = levelConfig.getLootingRareChancePercentPerLevel(key);

        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (rareChancePercentPerLevel > 0) {
            double rareBonusChance = rareChancePercentPerLevel * lootingLevel / 100.0
                + levelConfig.getLootingRareChanceBonus(key) / 100.0;
            for (ItemStack drop : event.getDrops()) {
                if (random.nextDouble() < rareBonusChance && drop.getAmount() > 0) {
                    drop.setAmount(drop.getAmount() + 1);
                }
            }
        }

        if (maxDropPercentPerLevel > 0) {
            double maxDropBonus = maxDropPercentPerLevel * lootingLevel / 100.0
                + levelConfig.getLootingMaxDropBonus(key) / 100.0;
            for (ItemStack drop : event.getDrops()) {
                if (drop.getAmount() > 0 && drop.getMaxStackSize() > drop.getAmount()) {
                    int extra = (int) Math.round(drop.getAmount() * maxDropBonus);
                    if (extra > 0) {
                        int newAmount = Math.min(drop.getAmount() + extra, drop.getMaxStackSize());
                        drop.setAmount(newAmount);
                    }
                }
            }
        }
    }
}
