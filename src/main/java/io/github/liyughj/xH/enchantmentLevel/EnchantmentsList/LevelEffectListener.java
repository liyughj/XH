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
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
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

        for (Enchantment enchant : WEAPON_DAMAGE_ENCHANTS) {
            int level = meta.getEnchantLevel(enchant);
            if (level <= 0) continue;

            String key = enchant.getKey().getKey();
            if (!levelConfig.hasDamageEffect(key)) continue;

            double percent = levelConfig.getDamagePercentPerLevel(key);
            if (percent > bestPercent || (percent == bestPercent && level > bestLevel)) {
                bestPercent = percent;
                bestLevel = level;
            }
        }

        if (bestLevel <= 0 || bestPercent <= 0.0) return;

        double multiplier = 1.0 + (bestPercent * bestLevel / 100.0);
        event.setDamage(event.getDamage() * multiplier);
    }

    private void applyFireAspect(ItemMeta meta, LivingEntity target) {
        int level = meta.getEnchantLevel(Enchantment.FIRE_ASPECT);
        if (level <= 0) return;

        String key = Enchantment.FIRE_ASPECT.getKey().getKey();
        if (!levelConfig.hasFireEffect(key)) return;

        int ticksPerLevel = levelConfig.getFireTicksPerLevel(key);
        target.setFireTicks(ticksPerLevel * level);
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
        double totalBlocks = blocksPerLevel * level;

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
        double range = levelConfig.getSweepRange(key);
        if (range <= 0) range = 3.0;

        /* 最终伤害（经锋利/护甲等全部计算后） */
        double finalDamage = event.getFinalDamage();

        /* 横扫伤害 = 最终伤害 × (横扫百分比 × 等级 / 100) */
        double sweepPercent = percentPerLevel * sweepLevel / 100.0;
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

        /* 计算保留概率 = min(每级概率 × 等级, 100%) */
        double chancePerLevel = levelConfig.getSilkTouchKeepChancePerLevel(key);
        double chance = Math.min(chancePerLevel * silkLevel / 100.0, 1.0);

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
        double baseProb = levelConfig.getFortuneBaseProbability(key);
        double decrement = levelConfig.getFortuneProbDecrement(key);

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

        double range = levelConfig.getChainRange(key);
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
        double saveProb = Math.min(saveChance * level / 100.0, 1.0);
        if (rand.nextDouble() < saveProb) {
            event.setDamage(0);
            return;
        }

        /* 第二判定：返还耐久
         * 返还量 = ceil(基础消耗 × 返还倍率 × 等级 / 100)
         * 默认：1级=50%, 2级=100%, ..., 10级=500% */
        double returnProb = Math.min(returnChance * level / 100.0, 1.0);
        if (rand.nextDouble() < returnProb) {
            int returnAmount = (int) Math.ceil(baseDamage * returnRate * level / 100.0);

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
            double rareBonusChance = rareChancePercentPerLevel * lootingLevel / 100.0;
            for (ItemStack drop : event.getDrops()) {
                if (random.nextDouble() < rareBonusChance && drop.getAmount() > 0) {
                    drop.setAmount(drop.getAmount() + 1);
                }
            }
        }

        if (maxDropPercentPerLevel > 0) {
            double maxDropBonus = maxDropPercentPerLevel * lootingLevel / 100.0;
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
