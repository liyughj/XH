package io.github.liyughj.xH.rpg.Attribute;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * RPG 战斗监听器 —— 负责将 RPG 属性计算接入实际伤害事件。
 * <p>
 * 事件流水线：
 * <ol>
 *   <li>ProjectileLaunchEvent NORMAL — 在弹射物 PDC 中存入发射者 UUID + 武器类型</li>
 *   <li>EntityDamageByEntityEvent LOW — RPG 伤害 + 暴击 + 吸血 + 穿透 + 破甲</li>
 *   <li>EntityDamageByEntityEvent NORMAL — 攻速 CD 保底（AttributeListener）</li>
 *   <li>EntityDamageByEntityEvent HIGH — 附魔效果（LevelEffectListener）</li>
 * </ol>
 */
public class RpgCombatListener implements Listener {

    private final JavaPlugin plugin;
    private static final String PDC_RPG_SHOOTER = "rpg_shooter_uuid";
    private static final String PDC_RPG_WEAPON_TYPE = "rpg_weapon_type";
    /** 枪械弹标记 key（与 GunListener 保持一致） */
    private static final String PDC_GUN_IS_GUN = "gun_is_gun";

    /** 击杀追踪：实体UUID → 最近一次攻击者信息 */
    private static final java.util.Map<java.util.UUID, KillTrack> killTracker =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static class KillTrack {
        final java.util.UUID attackerUuid;
        final org.bukkit.Material weaponMaterial;
        final boolean isGun;
        KillTrack(java.util.UUID attackerUuid, org.bukkit.Material weaponMaterial, boolean isGun) {
            this.attackerUuid = attackerUuid;
            this.weaponMaterial = weaponMaterial;
            this.isGun = isGun;
        }
    }

    public RpgCombatListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /* ==================== 弹射物：存入 RPG 数据 ==================== */

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        if (!(event.getEntity() instanceof Projectile proj)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || !weapon.hasItemMeta()) return;

        /* 存入发射者 UUID 和武器类型，命中时读取 */
        proj.getPersistentDataContainer().set(
            new NamespacedKey(plugin, PDC_RPG_SHOOTER),
            PersistentDataType.STRING,
            player.getUniqueId().toString()
        );
        proj.getPersistentDataContainer().set(
            new NamespacedKey(plugin, PDC_RPG_WEAPON_TYPE),
            PersistentDataType.STRING,
            weapon.getType().name()
        );
    }

    /* ==================== 特殊武器弹道命中方块 ==================== */

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player player)) return;

        // 榴弹命中方块
        if (arrow.getPersistentDataContainer().has(
            new NamespacedKey(plugin, "grenade_fuse"), PersistentDataType.INTEGER)) {
            event.setCancelled(true);
            io.github.liyughj.xH.gun.SpecialWeapons.explodeGrenade(arrow, arrow.getLocation(), player);
            return;
        }

        // 火箭弹命中方块
        if (arrow.getPersistentDataContainer().has(
            new NamespacedKey(plugin, "rocket_radius"), PersistentDataType.DOUBLE)) {
            event.setCancelled(true);
            io.github.liyughj.xH.gun.SpecialWeapons.explodeRocketOnHit(arrow, player);
        }

        // 穿透方块击穿（薄墙：玻璃板/铁栏杆/栅栏等）
        handleBlockBreak(arrow, player, event);

        // 跳弹
        handleRicochet(arrow, player, event);
    }

    /** 跳弹：子弹命中方块时以镜面反射反弹 */
    private void handleRicochet(org.bukkit.entity.Arrow arrow, Player player, ProjectileHitEvent event) {
        org.bukkit.block.Block hitBlock = event.getHitBlock();
        if (hitBlock == null) return;

        io.github.liyughj.xH.gun.BallisticsManager.BulletMeta meta =
            io.github.liyughj.xH.gun.BallisticsManager.getBulletMeta(arrow.getUniqueId());
        if (meta == null) return;

        // 玻璃穿透优先：玻璃方块直接破坏并穿过
         if (meta.isGlassPierce() && isGlassBlock(hitBlock.getType())) {
             event.setCancelled(true);
             hitBlock.breakNaturally();
             return;
         }

         // 跳弹检查
         if (!meta.canRicochet()) return;

         // 每次命中方块时概率判定
         if (meta.getRicochetChance() < 100.0 && Math.random() * 100.0 >= meta.getRicochetChance()) return;

         Vector v = arrow.getVelocity();
         Vector n = getHitNormal(event.getHitBlockFace());

         // 入射角校验：dot < 0 (入射), abs(dot) > cos(maxAngle) → 入射角过大不跳弹
         double incidenceDot = Math.abs(v.clone().normalize().dot(n));
         double maxAngleCos = Math.cos(Math.toRadians(getRicochetMaxAngle(arrow)));
         if (incidenceDot > maxAngleCos) return; // 入射角太大，不跳弹

         event.setCancelled(true);
         meta.consumeRicochet();

         Vector reflected = v.clone().subtract(n.clone().multiply(2 * v.dot(n)));
         reflected.multiply(0.6);

        org.bukkit.Location spawnLoc = arrow.getLocation().add(reflected.clone().normalize().multiply(0.3));
        org.bukkit.entity.Arrow newArrow = arrow.getWorld().spawn(spawnLoc, org.bukkit.entity.Arrow.class);
        newArrow.setVelocity(reflected);
        newArrow.setShooter(player);
        newArrow.setInvisible(true);
        newArrow.setCritical(false);
        newArrow.setGravity(arrow.hasGravity());
        copyGunPDC(arrow, newArrow);

        io.github.liyughj.xH.gun.BallisticsManager.removeBullet(arrow.getUniqueId());
        arrow.remove();
    }

    /** 获取方块面的法向量 */
    private Vector getHitNormal(org.bukkit.block.BlockFace face) {
        return switch (face) {
            case UP -> new Vector(0, 1, 0);
            case DOWN -> new Vector(0, -1, 0);
            case NORTH -> new Vector(0, 0, -1);
            case SOUTH -> new Vector(0, 0, 1);
            case EAST -> new Vector(1, 0, 0);
            case WEST -> new Vector(-1, 0, 0);
            default -> new Vector(0, 1, 0);
        };
    }

    /** 是否为玻璃类方块 */
    private boolean isGlassBlock(org.bukkit.Material mat) {
        String name = mat.name();
        return name.contains("GLASS") || name.contains("ICE") || name.equals("GLOWSTONE");
    }

    /** 读取弹道 meta 获取跳弹最大入射角，返回角度（度） */
    private double getRicochetMaxAngle(org.bukkit.entity.Arrow arrow) {
        // 从 PDC 读取开枪时记录的角度
        Double angle = arrow.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "ricochet_angle"), PersistentDataType.DOUBLE);
        return angle != null ? angle : 45.0; // 默认 45°
    }

    /** 处理穿透弹击穿薄墙 */
    private void handleBlockBreak(org.bukkit.entity.Arrow arrow, Player player, ProjectileHitEvent event) {
        if (!arrow.getPersistentDataContainer().has(
            new NamespacedKey("xh", "pen_block_break"), PersistentDataType.BYTE)) return;

        org.bukkit.block.Block hitBlock = event.getHitBlock();
        if (hitBlock == null) return;
        if (!isThinBlock(hitBlock.getType())) return;

        Integer penCount = arrow.getPersistentDataContainer().get(
            new NamespacedKey("xh", "pen_count"), PersistentDataType.INTEGER);
        int remaining = (penCount != null ? penCount : 0) - 1;

        event.setCancelled(true);
        // 破坏薄墙
        hitBlock.breakNaturally();
        arrow.remove();

        if (remaining >= 0) {
            // 在新位置生成继续飞行的箭矢
            org.bukkit.Location spawnLoc = hitBlock.getLocation().add(0.5, 0.5, 0.5);
            org.bukkit.entity.Arrow newArrow = arrow.getWorld().spawn(spawnLoc, org.bukkit.entity.Arrow.class);
            newArrow.setVelocity(arrow.getVelocity());
            newArrow.setShooter(player);
            newArrow.setPierceLevel(Math.max(0, remaining));
            newArrow.setInvisible(true);
            newArrow.setCritical(false);
            newArrow.setGravity(arrow.hasGravity());
            // 复制枪械弹PDC标记
            copyGunPDC(arrow, newArrow);
            // 更新剩余穿透计数
            newArrow.getPersistentDataContainer().set(
                new NamespacedKey("xh", "pen_block_break"), PersistentDataType.BYTE, (byte) 1);
            newArrow.getPersistentDataContainer().set(
                new NamespacedKey("xh", "pen_count"), PersistentDataType.INTEGER, remaining);
        }
    }

    /** 复制枪械相关的PDC标记到新箭矢 */
    private void copyGunPDC(org.bukkit.entity.Arrow from, org.bukkit.entity.Arrow to) {
        for (String key : new String[]{"gun_shooter_uuid", "gun_weapon_type", "gun_is_gun", "gun_shot_ammo", "shotgun_pellet"}) {
            NamespacedKey nk = new NamespacedKey(plugin, key);
            if (from.getPersistentDataContainer().has(nk, PersistentDataType.STRING)) {
                to.getPersistentDataContainer().set(nk, PersistentDataType.STRING,
                    from.getPersistentDataContainer().get(nk, PersistentDataType.STRING));
            }
        }
        // gun_is_gun as BYTE
        NamespacedKey gunKey = new NamespacedKey(plugin, "gun_is_gun");
        if (from.getPersistentDataContainer().has(gunKey, PersistentDataType.BYTE)) {
            to.getPersistentDataContainer().set(gunKey, PersistentDataType.BYTE, (byte) 1);
        }
        // shotgun_pellet as DOUBLE
        NamespacedKey pelletKey = new NamespacedKey(plugin, "shotgun_pellet");
        if (from.getPersistentDataContainer().has(pelletKey, PersistentDataType.DOUBLE)) {
            to.getPersistentDataContainer().set(pelletKey, PersistentDataType.DOUBLE,
                from.getPersistentDataContainer().get(pelletKey, PersistentDataType.DOUBLE));
        }
    }

    /** 判定是否为可击穿的薄墙方块 */
    private boolean isThinBlock(org.bukkit.Material mat) {
        String name = mat.name();
        return name.contains("PANE") || name.contains("_BARS") || name.contains("FENCE")
            || name.contains("WALL") || name.contains("CHAIN") || name.contains("BAMBOO")
            || name.contains("ROD") || name.contains("TRAPDOOR");
    }

    /* ==================== 伤害事件：RPG 计算入口 ==================== */

    /**
     * LOW 优先级：在附魔剥离之后、攻速 CD 之前，进行 RPG 伤害计算。
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        // 射线枪械伤害已由 RayTraceManager 完整处理，跳过重复计算
        if (target.hasMetadata("xh_raytrace")) return;

        DamageCause cause = event.getCause();

        if (cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK) {
            handleMelee(event, target);
        } else if (cause == DamageCause.PROJECTILE) {
            handleProjectile(event, target);
        }
    }

    /* ==================== 闪避 / 命中 / 致盲 ==================== */

    /**
     * 判定目标是否闪避了本次攻击（含致盲影响）。
     *
     * <p><b>判定流程：</b></p>
     * <ol>
     *   <li>读攻击方原始命中 hit + 读致盲效能 blindEff</li>
     *   <li>有效命中 = hit - blindEff</li>
     *   <li>有效命中<0 → roll abs(有效命中) → 中了则致盲MISS（打空）</li>
     *   <li>致盲MISS未触发 → 防御方闪避判定</li>
     *   <li>闪避成功 → 有效命中>0 则 roll 命中穿越判定</li>
     * </ol>
     */
    /** 供外部射线系统调用的闪避/命中/致盲判定 */
    public static boolean checkRayDodge(Player attacker, ItemStack attackWeapon, LivingEntity defender) {
        if (!(defender instanceof Player defPlayer)) return false;

        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();

        /* ——— 攻击方命中计算 ——— */
        double hitPct = 0.0;
        if (attackWeapon != null && attackWeapon.hasItemMeta()) {
            hitPct += AttributeStorage.getItemAttrRange(attackWeapon, RpgAttribute.HIT).roll();
        }
        hitPct += AttributeStorage.getPlayerAttrRange(attacker, RpgAttribute.HIT).roll();

        // 枪械默认命中100%（无HIT属性时），使闪避对枪械生效
        if (hitPct <= 0 && io.github.liyughj.xH.gun.GunListener.isGunStatic(attackWeapon)) {
            hitPct = 100.0;
        }
        hitPct = Math.min(100.0, hitPct);

        /* ——— 致盲削减 ——— */
        double blindEff = BlindManager.getBlindEfficiency(attacker);
        double effectiveHit = hitPct - blindEff;

        /* 致盲MISS：有效命中为负数 → roll 绝对值 → 打空了 */
        if (effectiveHit < 0) {
            double missChance = Math.abs(effectiveHit);
            missChance = Math.min(100.0, missChance);
            if (rng.nextDouble() * 100.0 <= missChance) return true;
            // 未触发MISS，effectiveHit 仍为负，后续命中穿越不可能成功
        }

        /* ——— 防御方闪避（与致盲无关的独立判定） ——— */
        double dodgePct = AttributeStorage.getPlayerAttrRange(defPlayer, RpgAttribute.DODGE).roll();
        for (ItemStack armor : defPlayer.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            dodgePct += AttributeStorage.getItemAttrRange(armor, RpgAttribute.DODGE).roll();
        }
        dodgePct = Math.min(100.0, dodgePct);
        if (dodgePct <= 0) return false;
        if (rng.nextDouble() * 100.0 > dodgePct) return false; // 未触发闪避

        /* 闪避成功 → 命中穿越判定（有效命中>0才可能成功） */
        if (effectiveHit > 0 && rng.nextDouble() * 100.0 <= effectiveHit) return false;
        return true; // 闪避生效
    }

    private boolean checkDodge(Player attacker, ItemStack attackWeapon, LivingEntity defender,
                               EntityDamageByEntityEvent event) {
        if (checkRayDodge(attacker, attackWeapon, defender)) {
            event.setDamage(0);
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    /* ==================== 近战处理 ==================== */

    private void handleMelee(EntityDamageByEntityEvent event, LivingEntity target) {
        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();

        /* 闪避/命中判定（在伤害计算之前，可取消事件） */
        if (checkDodge(player, weapon, target, event)) return;

        if (weapon == null) return;

        double baseDamage = event.getDamage();

        AttributeCalculator.DamageResult result = AttributeCalculator.calcFinalDamage(
            player, weapon, target,
            AttributeCalculator.DamageType.MELEE,
            baseDamage
        );

        /* 穿透 */
        AttributeCalculator.PenetrationResult pen = AttributeCalculator.calcPenetration(
            player, weapon, target, result.damage
        );

        event.setDamage(result.damage + pen.extraDamage);
        applyHeal(player, result.heal);

        /* 破甲 */
        applyArmorBreak(player, weapon, target, event);

        /* 致盲 */
        applyBlind(player, weapon, target, event);

        /* 枪械命中特效（减速/硬直/致盲） */
        applyGunHitEffects(player, weapon, target);
    }

    /* ==================== 射弹处理 ==================== */

    private void handleProjectile(EntityDamageByEntityEvent event, LivingEntity target) {
        if (!(event.getDamager() instanceof Projectile proj)) return;
        if (!(proj.getShooter() instanceof Player player)) return;

        /* 从 PDC 读取发射时的武器类型 */
        String shootWeaponType = proj.getPersistentDataContainer().get(
            new NamespacedKey(plugin, PDC_RPG_WEAPON_TYPE),
            PersistentDataType.STRING
        );

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null) return;

        /* 闪避/命中判定（在伤害计算之前，可取消事件） */
        if (checkDodge(player, weapon, target, event)) return;

        /* 如果主手武器类型与发射时不同，回退到发射时的武器类型查找 */
        if (shootWeaponType != null && weapon.getType().name().equals(shootWeaponType)) {
            // 主手武器未切换，正常使用
        } else {
            // 武器已切换，跳过 RPG 计算
            return;
        }

        double baseDamage = event.getDamage();

        /* 检测枪械弹 → 使用 GUN 类型计算 */
        boolean isGun = proj.getPersistentDataContainer().has(
            new NamespacedKey(plugin, PDC_GUN_IS_GUN),
            PersistentDataType.BYTE
        );

        /* ── 特殊武器拦截：榴弹/火箭弹 ── */
        if (isGun && proj instanceof Arrow arrow) {
            if (proj.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "grenade_fuse"), PersistentDataType.INTEGER)) {
                // 榴弹命中：引信=0/-1时撞击引爆
                Integer fuse = arrow.getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "grenade_fuse"), PersistentDataType.INTEGER);
                if (fuse != null && fuse <= 0) {
                    event.setCancelled(true);
                    io.github.liyughj.xH.gun.SpecialWeapons.explodeGrenade(arrow, arrow.getLocation(), player);
                    return;
                }
                // 弹跳榴弹：不爆炸，反弹（由Minecraft物理处理）
                Integer bounce = arrow.getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "grenade_bounce"), PersistentDataType.INTEGER);
                if (bounce != null && bounce > 0) {
                    // 弹跳中不爆炸
                    return;
                }
                // 否则正常穿透（等引信到时间）
            }
            if (proj.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "rocket_radius"), PersistentDataType.DOUBLE)) {
                event.setCancelled(true);
                io.github.liyughj.xH.gun.SpecialWeapons.explodeRocketOnHit(arrow, player);
                return;
            }
        }

        /* ── 弩箭：流血效果 ── */
        if (isGun && proj instanceof Arrow && target instanceof LivingEntity) {
            applyBleed(proj, target);
        }
        AttributeCalculator.DamageType calcType = isGun ? AttributeCalculator.DamageType.GUN
                                                         : AttributeCalculator.DamageType.PROJECTILE;

        AttributeCalculator.DamageResult result = AttributeCalculator.calcFinalDamage(
            player, weapon, target,
            calcType,
            baseDamage
        );

        double finalDamage = result.damage;

        /* ── 弹药伤害乘数 ── */
        if (isGun) {
            io.github.liyughj.xH.gun.AmmoConfig.AmmoTypeDef ammoDmg = getAmmoType(proj, weapon);
            if (ammoDmg != null) {
                finalDamage *= ammoDmg.damageMult;
                // 护甲穿透
                int armorIgnore = ammoDmg.getEffectInt("armor_ignore", 0);
                if (armorIgnore > 0 && target.getAttribute(org.bukkit.attribute.Attribute.ARMOR) != null) {
                    double armor = target.getAttribute(org.bukkit.attribute.Attribute.ARMOR).getValue();
                    double effectiveArmor = armor * (1.0 - armorIgnore / 100.0);
                    // MC 每点护甲 ≈ 4% 减伤，换算为伤害修正
                    double originalReduction = Math.min(0.8, armor * 0.04);
                    double effectiveReduction = Math.min(0.8, effectiveArmor * 0.04);
                    double mult = (1.0 - effectiveReduction) / Math.max(0.01, (1.0 - originalReduction));
                    mult = Math.min(5.0, Math.max(1.0, mult));
                    finalDamage *= mult;
                }
            }
        }

        /* ── 枪械子弹：穿透 + 弹药效果 ── */
        if (isGun) {
            // 穿透伤害衰减：命中次数>1时递减
            if (proj.getPersistentDataContainer().has(PDC_PENETRATION_HIT, PersistentDataType.INTEGER)) {
                Integer prevHits = proj.getPersistentDataContainer().get(PDC_PENETRATION_HIT, PersistentDataType.INTEGER);
                if (prevHits != null && prevHits > 0) {
                    int mode = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_FALLOFF_MODE);
                    double falloff = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_FALLOFF) / 100.0;
                    double minDmg = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_MIN_DAMAGE) / 100.0;
                    if (mode == 1) {
                        // 指数模式
                        finalDamage *= Math.pow(1.0 - falloff, prevHits);
                    } else {
                        // 线性模式
                        finalDamage *= Math.max(minDmg, 1.0 - falloff * prevHits);
                    }
                    if (finalDamage < 0) finalDamage = 0;
                }
            }
            // 霰弹弹丸伤害分配
            if (proj.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "shotgun_pellet"), PersistentDataType.DOUBLE)) {
                Double divider = proj.getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "shotgun_pellet"), PersistentDataType.DOUBLE);
                double pelletCount = getPelletCount(weapon);
                if (pelletCount > 0 && divider != null) {
                    if (divider <= 0) {
                        finalDamage = finalDamage / pelletCount;
                    } else {
                        finalDamage = finalDamage * divider / pelletCount;
                    }
                }
            }

            // 爆头/部位
            finalDamage = applyHitzone(player, weapon, target, proj, finalDamage);

            // 距离衰减（弹道系统）
            if (proj instanceof Arrow) {
                finalDamage *= io.github.liyughj.xH.gun.BallisticsManager.calcDistanceFalloff(player, (Arrow) proj, weapon);
            }

            // 穿透：标记已击穿实体，允许子弹继续飞行
            applyPenetration(player, weapon, target, proj, finalDamage);

            // 弹药特殊效果
            applyAmmoEffects(player, weapon, target);
        }

        event.setDamage(finalDamage);
        applyHeal(player, result.heal);

        /* 破甲 */
        applyArmorBreak(player, weapon, target, event);

        /* 致盲 */
        applyBlind(player, weapon, target, event);

        /* 枪械命中特效（减速/硬直/致盲） */
        applyGunHitEffects(player, weapon, target);
    }

    /* ==================== 破甲处理 ==================== */

    /**
     * 读取攻击方破甲属性，对目标施加破甲标记。
     * 同时应用目标已有破甲标记的伤害增幅。
     */
    /** 供外部射线系统调用：应用破甲效果，返回已有破甲的伤害倍率 */
    public static double applyRayArmorBreak(Player attacker, ItemStack weapon, LivingEntity target) {
        double multiplier = ArmorBreakManager.getDamageMultiplier(target);

        if (weapon == null || !weapon.hasItemMeta()) return multiplier;

        AttributeRange chanceRange = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.ARMOR_BREAK_CHANCE);
        AttributeRange shallowRange = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.ARMOR_BREAK_SHALLOW);
        AttributeRange mediumRange = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.ARMOR_BREAK_MEDIUM);
        AttributeRange deepRange = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.ARMOR_BREAK_DEEP);
        AttributeRange ticksRange = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.ARMOR_BREAK_TICKS);

        double chance = chanceRange.roll();
        double shallow = shallowRange.roll();
        double medium = mediumRange.roll();
        double deep = deepRange.roll();
        long ticks = (long) ticksRange.roll();

        if (chance <= 0 || ticks <= 0) return multiplier;

        ArmorBreakManager.Debuff before = ArmorBreakManager.getDebuff(target);
        ArmorBreakManager.Debuff after = ArmorBreakManager.apply(
            attacker, target, chance, shallow, medium, deep, ticks);

        if (after == null) return multiplier;

        boolean changed = before == null
            || before.level != after.level
            || before.totalPct != after.totalPct
            || !before.breakerUuid.equals(after.breakerUuid);

        if (changed) {
            String targetName = target instanceof Player tp ? tp.getName() : target.getType().name();
            attacker.sendMessage("§c你使 §e" + targetName + " §c破甲 → 当前程度: §e" + after.level.getDisplay());
            if (target instanceof Player tp)
                tp.sendMessage("§c" + after.breakerName + " §c使你破甲 → 当前程度: §e" + after.level.getDisplay());
        }
        return multiplier;
    }

    private void applyArmorBreak(Player attacker, ItemStack weapon, LivingEntity target,
                                 EntityDamageByEntityEvent event) {
        double multiplier = applyRayArmorBreak(attacker, weapon, target);
        if (multiplier > 1.0) event.setDamage(event.getDamage() * multiplier);
    }

    /* ==================== 击杀追踪 ==================== */

    /** MONITOR 优先级：记录所有伤害的最后攻击者，供 EntityDeathEvent 击杀连锁使用 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageTrackKiller(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        Player attacker = null;
        org.bukkit.Material weaponMat = null;
        boolean isGun = false;

        if (event.getDamager() instanceof Player p) {
            attacker = p;
            ItemStack weapon = p.getInventory().getItemInMainHand();
            if (weapon.getType().isItem()) weaponMat = weapon.getType();
            isGun = io.github.liyughj.xH.gun.GunListener.isGunStatic(weapon);
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
            String weaponType = proj.getPersistentDataContainer().get(
                new NamespacedKey(plugin, PDC_RPG_WEAPON_TYPE), PersistentDataType.STRING);
            if (weaponType != null) {
                weaponMat = org.bukkit.Material.getMaterial(weaponType);
            }
            if (weaponMat == null || !weaponMat.isItem()) {
                ItemStack mainHand = p.getInventory().getItemInMainHand();
                weaponMat = mainHand.getType().isItem() ? mainHand.getType() : null;
            }
            isGun = proj.getPersistentDataContainer().has(
                new NamespacedKey(plugin, PDC_GUN_IS_GUN), PersistentDataType.BYTE);
        }

        if (attacker != null && weaponMat != null) {
            killTracker.put(victim.getUniqueId(), new KillTrack(attacker.getUniqueId(), weaponMat, isGun));
        }
    }

    /* ==================== 实体死亡 ==================== */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        // 击杀连锁：枪械击杀时尝试激活buff
        KillTrack track = killTracker.remove(entity.getUniqueId());
        if (track != null && track.isGun) {
            Player killer = org.bukkit.Bukkit.getPlayer(track.attackerUuid);
            if (killer != null && killer.isOnline()) {
                // 在玩家背包中查找匹配的枪械物品
                ItemStack weapon = findMatchingWeapon(killer, track.weaponMaterial);
                if (weapon != null) {
                    io.github.liyughj.xH.gun.MagazineManager.tryApplyKillChainBuffs(killer, weapon);
                }
            }
        }
        ArmorBreakManager.remove(entity);
        BlindManager.remove(entity);
    }

    /** 在玩家主手/副手/背包中查找匹配 Material 的枪械 */
    private ItemStack findMatchingWeapon(Player player, org.bukkit.Material material) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main.getType() == material && io.github.liyughj.xH.gun.GunListener.isGunStatic(main)) return main;
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off.getType() == material && io.github.liyughj.xH.gun.GunListener.isGunStatic(off)) return off;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material && io.github.liyughj.xH.gun.GunListener.isGunStatic(item)) return item;
        }
        return null;
    }

    /* ==================== 致盲处理 ==================== */

    /**
     * 读取攻击方致盲属性，对目标施加致盲标记。
     */
    /** 供外部射线系统调用的致盲判定 */
    public static void applyRayBlind(Player attacker, ItemStack weapon, LivingEntity target) {
        if (weapon == null || !weapon.hasItemMeta()) return;

        AttributeRange chanceRange = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.BLIND_CHANCE);
        AttributeRange effRange = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.BLIND_EFFICIENCY);
        AttributeRange ticksRange = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.BLIND_TICKS);

        double chance = chanceRange.roll();
        double efficiency = effRange.roll();
        long ticks = (long) ticksRange.roll();

        if (chance <= 0 || efficiency <= 0 || ticks <= 0) return;

        BlindManager.Debuff before = BlindManager.getDebuff(target);
        BlindManager.Debuff after = BlindManager.apply(
            attacker, target, chance, efficiency, ticks);

        if (after == null) return;

        boolean changed = before == null
            || before.efficiency != after.efficiency
            || !before.breakerName.equals(after.breakerName);

        if (changed) {
            String targetName = target instanceof Player tp ? tp.getName() : target.getType().name();
            attacker.sendMessage("§b你致盲了 §e" + targetName + " §b→ 敌方命中 -" + (int) after.efficiency + "%");
            if (target instanceof Player tp)
                tp.sendMessage("§b" + after.breakerName + " §b致盲了你 → 你的命中 -" + (int) after.efficiency + "%");
        }
    }

    private void applyBlind(Player attacker, ItemStack weapon, LivingEntity target,
                            EntityDamageByEntityEvent event) {
        applyRayBlind(attacker, weapon, target);
    }

    /* ==================== 爆头/部位伤害 ==================== */

    /**
     * 根据弹射物落点计算命中部位，乘以对应倍率。
     *
     * @param weapon      枪械物品
     * @param target      被击中目标
     * @param proj        弹射物
     * @param baseDamage  RPG 计算后的原始伤害
     * @return 部位修正后的伤害
     */
    private double applyHitzone(Player shooter, ItemStack weapon, LivingEntity target,
                                Projectile proj, double baseDamage) {
        if (weapon == null || !weapon.hasItemMeta()) return baseDamage;

        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();

        /* —— 读取武器属性 —— */
        double headChance   = rollWeaponAttr(weapon, RpgAttribute.GUN_HEADSHOT_CHANCE);
        double headMult     = rollWeaponAttr(weapon, RpgAttribute.GUN_HEADSHOT_MULT);
        double upperChance  = rollWeaponAttr(weapon, RpgAttribute.GUN_UPPER_CHANCE);
        double upperMult    = rollWeaponAttr(weapon, RpgAttribute.GUN_UPPER_MULT);
        double lowerChance  = rollWeaponAttr(weapon, RpgAttribute.GUN_LOWER_CHANCE);
        double lowerMult    = rollWeaponAttr(weapon, RpgAttribute.GUN_LOWER_MULT);
        double legChance    = rollWeaponAttr(weapon, RpgAttribute.GUN_LEG_CHANCE);
        double legMult      = rollWeaponAttr(weapon, RpgAttribute.GUN_LEG_MULT);
        double headThresh   = rollWeaponAttr(weapon, RpgAttribute.GUN_HEADSHOT_THRESHOLD);
        double bodyThresh   = rollWeaponAttr(weapon, RpgAttribute.GUN_BODY_THRESHOLD);
        double legThresh    = rollWeaponAttr(weapon, RpgAttribute.GUN_LEG_THRESHOLD);

        // 弩箭爆头倍率覆写
        if (proj instanceof org.bukkit.entity.Arrow arrow) {
            Double cbowHeadMult = arrow.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "crossbow_headshot_mult"),
                PersistentDataType.DOUBLE);
            if (cbowHeadMult != null && cbowHeadMult > 0) {
                headMult = cbowHeadMult;
            }
        }

        /* —— 计算命中部位 —— */
        double feetY   = target.getLocation().getY();
        double eyeH    = target.getEyeHeight();
        double projY   = proj.getLocation().getY();
        double deltaY  = projY - feetY;
        double ratio   = deltaY / Math.max(0.01, eyeH); // [0, ~1.0]

        double chance;
        double mult;
        String zoneName;

        if (ratio >= headThresh / 100.0) {
            chance = headChance;   mult = headMult;   zoneName = "§c头部";
        } else if (ratio >= bodyThresh / 100.0) {
            chance = upperChance;  mult = upperMult;   zoneName = "§e上肢";
        } else if (ratio >= legThresh / 100.0) {
            chance = lowerChance;  mult = lowerMult;   zoneName = "§e下身";
        } else {
            chance = legChance;    mult = legMult;     zoneName = "§7腿部";
        }

        /* —— 概率判定 —— */
        if (chance <= 0 || chance >= 100) {
            // 100% 直接触发，0% 跳过
            if (chance <= 0) return baseDamage;
        } else {
            if (rng.nextDouble() * 100.0 > chance) return baseDamage;
        }

        double damage = baseDamage * mult / 100.0;
        shooter.sendMessage(zoneName + " §7×" + String.format("%.1f", mult / 100.0)
            + "  §f" + String.format("%.1f", damage));
        return damage;
    }

    /** 从武器读取属性区间值并 roll */
    private double rollWeaponAttr(ItemStack weapon, RpgAttribute attr) {
        AttributeRange range = AttributeStorage.getItemAttrRange(weapon, attr);
        return range.roll();
    }

    /** 获取霰弹枪弹丸数 */
    private double getPelletCount(ItemStack weapon) {
        return AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_SHOTGUN_PELLET_COUNT);
    }

    /* ==================== 枪械命中特效（减速/硬直/致盲） ==================== */

    /**
     * 读取枪械属性，对命中目标施加减速/硬直/额外致盲效果。
     */
    /** 供外部射线系统调用的枪械命中特效（减速/硬直/致盲） */
    public static void applyGunHitEffects(Player shooter, ItemStack weapon, LivingEntity target) {
        if (weapon == null || !weapon.hasItemMeta()) return;
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();

        // 命中减速
        double slowChance = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HIT_SLOW_CHANCE);
        if (slowChance > 0 && rng.nextDouble() * 100.0 < slowChance) {
            double slowAmount = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HIT_SLOW_AMOUNT);
            int slowTicks = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HIT_SLOW_TICKS);
            int amp = Math.min(6, Math.max(0, (int)(slowAmount / 15.0))); // 每15%一级
            target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SLOWNESS, slowTicks, amp, false, true, true));
        }

        // 命中硬直（stagger = setVelocity 击退）
        double staggerChance = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HIT_STAGGER_CHANCE);
        if (staggerChance > 0 && rng.nextDouble() * 100.0 < staggerChance) {
            double strength = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HIT_STAGGER_STRENGTH);
            var dir = shooter.getEyeLocation().getDirection().normalize();
            target.setVelocity(dir.multiply(strength * 0.5));
        }

        // 命中致盲（额外给目标致盲效果）
        double blindChance = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HIT_BLIND_CHANCE);
        if (blindChance > 0 && rng.nextDouble() * 100.0 < blindChance) {
            int blindTicks = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_HIT_BLIND_TICKS);
            // 用已有的 BlindManager 叠加
            // 简化：直接给短暂的 blindness 药水效果
            target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.BLINDNESS, blindTicks, 0, false, true, true));
        }
    }

    /* ==================== 吸血回血 ==================== */

    /** 供外部射线系统调用的吸血回血 */
    public static void applyHeal(Player player, double heal) {
        if (heal <= 0) return;
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        double newHealth = Math.min(maxHealth, player.getHealth() + heal);
        player.setHealth(newHealth);
    }

    /* ==================== 穿透系统 ==================== */

    private static final NamespacedKey PDC_PENETRATION_HIT = new NamespacedKey("xh", "penetration_hit_count");

    /**
     * 穿透处理：检测子弹是否还能继续穿透实体。
     * 在 Arrow PDC 上记录已穿透次数，超过 penetration_count 则移除 Arrow。
     */
    private void applyPenetration(Player shooter, ItemStack weapon, LivingEntity target,
                                  Projectile proj, double damage) {
        if (!(proj instanceof Arrow arrow)) return;
        if (!arrow.isValid() || arrow.isDead()) return;

        io.github.liyughj.xH.gun.AmmoConfig.AmmoTypeDef ammo = getAmmoType(proj, weapon);

        int penCount = (int) io.github.liyughj.xH.rpg.Attribute.AttributeStorage.getAttrValue(
            weapon, RpgAttribute.GUN_PENETRATION_COUNT);
        if (ammo != null) penCount += ammo.penetrationBonus;

        if (penCount <= 0) return; // 无穿透能力

        // 记录已穿透层数
        int alreadyHit = 0;
        if (arrow.getPersistentDataContainer().has(PDC_PENETRATION_HIT, PersistentDataType.INTEGER)) {
            alreadyHit = arrow.getPersistentDataContainer().get(PDC_PENETRATION_HIT, PersistentDataType.INTEGER);
        }
        alreadyHit++;

        arrow.getPersistentDataContainer().set(PDC_PENETRATION_HIT, PersistentDataType.INTEGER, alreadyHit);

        // 衰减模式: 0=线性(-固定%), 1=指数(每层×衰减系数)
        int falloffMode = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_FALLOFF_MODE);
        double falloff = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_FALLOFF) / 100.0;
        double minDmg = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_MIN_DAMAGE) / 100.0;

        if (falloffMode == 1) {
            // 指数：每次×(1-falloff%)
            double remaining = damage * Math.pow(1.0 - falloff, alreadyHit);
            if (remaining < damage * minDmg) remaining = damage * minDmg;
            // 箭头继续飞行，伤害由下一个命中事件重新计算
        }

        // 穿透粒子
        String particleStr = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_PARTICLE) >= 1
            ? "crit" : null;
        if (particleStr != null && particleStr.equals("crit")) {
            target.getWorld().spawnParticle(
                org.bukkit.Particle.CRIT, target.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0);
        }

        // 穿透音效
        double soundVal = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_SOUND);
        if (soundVal >= 1) {
            target.getWorld().playSound(target.getLocation(),
                org.bukkit.Sound.ENTITY_ARROW_HIT, 0.3f, 1.5f);
        }

        if (alreadyHit > penCount) {
            // 穿透层数用完，移除 Arrow
            arrow.remove();
        }
        // 否则箭矢继续飞行，可以命中下一个实体
    }

    /* ==================== 弹药特效 ==================== */

    private void applyAmmoEffects(Player shooter, ItemStack weapon, LivingEntity target) {
        io.github.liyughj.xH.gun.AmmoConfig.AmmoTypeDef ammo = io.github.liyughj.xH.gun.MagazineManager.getCurrentAmmoType(weapon);
        if (ammo == null || ammo.effects == null) return;

        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();

        // 点燃
        org.bukkit.configuration.ConfigurationSection igniteSec = ammo.effects.getConfigurationSection("ignite");
        if (igniteSec != null) {
            int chance = igniteSec.getInt("chance", 0);
            int ticks = igniteSec.getInt("ticks", 40);
            if (chance > 0 && rng.nextInt(100) < chance) {
                target.setFireTicks(Math.max(target.getFireTicks(), ticks));
            }
        }

        // 流血 (来自弹药如空尖弹)
        org.bukkit.configuration.ConfigurationSection bleedSec = ammo.effects.getConfigurationSection("bleed");
        if (bleedSec != null) {
            int chance = bleedSec.getInt("chance", 0);
            int damage = bleedSec.getInt("damage", 0);
            int ticks = bleedSec.getInt("ticks", 0);
            if (chance > 0 && damage > 0 && ticks > 0 && rng.nextInt(100) < chance) {
                new org.bukkit.scheduler.BukkitRunnable() {
                    int remaining = ticks;
                    @Override
                    public void run() {
                        if (!target.isValid() || target.isDead() || remaining <= 0) { cancel(); return; }
                        remaining--;
                        target.damage(damage);
                    }
                }.runTaskTimer(plugin, 0L, 2L);
            }
        }

        // 烟雾
        if (ammo.getEffectBool("smoke")) {
            target.getWorld().spawnParticle(
                org.bukkit.Particle.CAMPFIRE_COSY_SMOKE,
                target.getLocation().add(0, 1, 0),
                30, 2, 2, 2, 0.05);
        }

        // 致盲 (来自ammo如flash弹)
        org.bukkit.configuration.ConfigurationSection blindSec = ammo.effects.getConfigurationSection("blind");
        if (blindSec != null) {
            int radius = blindSec.getInt("radius", 0);
            int ticks = blindSec.getInt("ticks", 0);
            if (radius > 0 && ticks > 0) {
                BlindManager.apply(shooter, target, 100, 100, ticks);
                // 范围致盲
                for (org.bukkit.entity.Entity e : target.getWorld().getNearbyEntities(
                    target.getLocation(), radius, radius, radius)) {
                    if (e instanceof LivingEntity le && le != target && le != shooter) {
                        BlindManager.apply(shooter, le, 80, 60, ticks);
                    }
                }
            }
        }

        // 击退
        double knockback = ammo.getEffectDouble("knockback", 0);
        if (knockback > 0 && target != shooter) {
            org.bukkit.util.Vector dir = target.getLocation().toVector()
                .subtract(shooter.getLocation().toVector()).normalize();
            target.setVelocity(dir.multiply(knockback));
        }

        // 非致命
        if (ammo.getEffectBool("no_kill")) {
            if (target.getHealth() <= 1.0) {
                target.setHealth(1.0);
            }
        }

        // 火焰额外伤害 (fire_damage per tick)
        int fireDamage = ammo.getEffectInt("fire_damage", 0);
        if (fireDamage > 0 && target.getFireTicks() > 0) {
            target.damage(fireDamage, shooter);
        }

        // AOE 火焰区域
        if (ammo.getEffectBool("aoe_fire")) {
            Location loc = target.getLocation();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Location fireLoc = loc.clone().add(x, 0, z);
                    if (fireLoc.getBlock().isEmpty()) {
                        fireLoc.getBlock().setType(org.bukkit.Material.FIRE);
                    }
                }
            }
            // 延迟灭火
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    for (int x = -1; x <= 1; x++) {
                        for (int z = -1; z <= 1; z++) {
                            Location fireLoc = loc.clone().add(x, 0, z);
                            if (fireLoc.getBlock().getType() == org.bukkit.Material.FIRE) {
                                fireLoc.getBlock().setType(org.bukkit.Material.AIR);
                            }
                        }
                    }
                }
            }.runTaskLater(plugin, 60L);
        }

        // 爆炸
        if (ammo.getEffectBool("explosion")) {
            float power = (float) ammo.getEffectDouble("explosion_power", 2.0f);
            boolean destroyBlocks = ammo.getEffectBool("explosion_destroy");
            target.getWorld().createExplosion(target.getLocation(), power, false, destroyBlocks, shooter);

            // 破片
            int fragCount = ammo.getEffectInt("frag_count", 0);
            int fragDamage = ammo.getEffectInt("frag_damage", 0);
            if (fragCount > 0 && fragDamage > 0) {
                for (int i = 0; i < fragCount; i++) {
                    org.bukkit.entity.Arrow frag = target.getWorld().spawn(
                        target.getLocation().add(0, 1, 0), org.bukkit.entity.Arrow.class);
                    double angle = java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
                    double pitch = (java.util.concurrent.ThreadLocalRandom.current().nextDouble() - 0.5) * Math.PI;
                    frag.setVelocity(new org.bukkit.util.Vector(
                        Math.cos(angle) * Math.cos(pitch),
                        Math.sin(pitch),
                        Math.sin(angle) * Math.cos(pitch)
                    ).multiply(0.8));
                    frag.setShooter(shooter);
                    frag.setDamage(fragDamage / 10.0);
                    frag.setPierceLevel(0);
                    // 破片自动消失
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            if (frag.isValid()) frag.remove();
                        }
                    }.runTaskLater(plugin, 40L);
                }
            }
        }

        // 烟雾区域
        if (ammo.getEffectBool("smoke")) {
            Location loc = target.getLocation();
            for (int d = 0; d < 40; d++) {
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < 3; i++) {
                            loc.getWorld().spawnParticle(
                                org.bukkit.Particle.CAMPFIRE_COSY_SMOKE,
                                loc.clone().add(
                                    (Math.random() - 0.5) * 3,
                                    Math.random() * 2,
                                    (Math.random() - 0.5) * 3
                                ),
                                1, 0, 0, 0, 0.01);
                        }
                    }
                }.runTaskLater(plugin, d * 2L);
            }
        }
    }

    /** 获取当前发射弹种（Arrow PDC 优先 > 武器弹夹栈） */
    private io.github.liyughj.xH.gun.AmmoConfig.AmmoTypeDef getAmmoType(Projectile proj, ItemStack weapon) {
        // 1. Arrow PDC 上的发射弹种标记（doShootNormal 写入）
        if (proj != null) {
            String shotAmmo = proj.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "gun_shot_ammo"), PersistentDataType.STRING);
            if (shotAmmo != null && io.github.liyughj.xH.gun.GunSystemConfig.ammo() != null) {
                io.github.liyughj.xH.gun.AmmoConfig.AmmoTypeDef def =
                    io.github.liyughj.xH.gun.GunSystemConfig.ammo().getAmmoType(shotAmmo);
                if (def != null) return def;
            }
        }
        // 2. 武器弹夹栈顶/默认
        return io.github.liyughj.xH.gun.MagazineManager.getCurrentAmmoType(weapon);
    }

    /* ==================== 通用流血 DoT ==================== */

    private void applyBleed(org.bukkit.entity.Projectile proj, LivingEntity target) {
        Double bleedChance = proj.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "bleed_chance"), PersistentDataType.DOUBLE);
        if (bleedChance == null || bleedChance <= 0) return;

        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100 >= bleedChance) return;

        Double bleedDamage = proj.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "bleed_damage"), PersistentDataType.DOUBLE);
        Integer bleedTicks = proj.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "bleed_ticks"), PersistentDataType.INTEGER);

        if (bleedDamage == null || bleedTicks == null || bleedDamage <= 0 || bleedTicks <= 0) return;

        double dmgPerTick = bleedDamage;
        int ticks = bleedTicks;

        new org.bukkit.scheduler.BukkitRunnable() {
            int remaining = ticks;
            @Override
            public void run() {
                if (!target.isValid() || target.isDead() || remaining <= 0) {
                    cancel();
                    return;
                }
                remaining--;
                target.damage(dmgPerTick);
            }
        }.runTaskTimer(plugin, 0L, 2L); // 每2tick判定一次
    }
}
