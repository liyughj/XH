package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.debug.DebugManager;
import io.github.liyughj.xH.rpg.Attribute.AttributeCalculator;
import io.github.liyughj.xH.rpg.Attribute.AttributeRange;
import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import io.github.liyughj.xH.rpg.Attribute.RpgCombatListener;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 射线命中管理器 —— 替代 Arrow 实体子弹的即时命中+步进模拟系统。
 *
 * <h3>武器类型→方法映射</h3>
 * <table>
 *   <tr><td>普通枪/霰弹</td><td>{@link #shootRayNormal}</td><td>直接射线+跳弹+水中弹速</td></tr>
 *   <tr><td>弩</td><td>{@link #shootRayGravity}</td><td>步进射线+重力抛物线</td></tr>
 *   <tr><td>榴弹</td><td>{@link #shootGrenadeRay}</td><td>步进射线+重力+弹跳+引信</td></tr>
 *   <tr><td>火箭筒</td><td>{@link #shootRocketRay}</td><td>步进射线+追踪+遥控引爆</td></tr>
 * </table>
 */
public final class RayTraceManager {

    private static JavaPlugin plugin;
    private static final double STEP = 0.3;

    private RayTraceManager() {}
    public static void init(JavaPlugin p) {
        plugin = p;
        // 注册玩家离线清理
        p.getServer().getPluginManager().registerEvents(new QuitListener(), p);
    }

    private static class QuitListener implements Listener {
        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            remoteRockets.remove(event.getPlayer().getUniqueId());
        }
    }

    // ==================== API：普通射线（普通枪/霰弹） ====================

    public static List<LivingEntity> shootRayNormal(Player shooter, ItemStack weapon, Vector direction) {
        return shootRayNormal(shooter, weapon, direction, false, 0, null);
    }

    public static List<LivingEntity> shootRayNormal(Player shooter, ItemStack weapon, Vector direction,
                                                     boolean shotgunPellet, int pelletIndex, Double shotgunDivider) {
        List<LivingEntity> hitEntities = new ArrayList<>();

        // ── 射程：霰弹用独立弹丸速度，否则用通用弹速 ──
        double speed;
        if (shotgunPellet) {
            speed = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_SHOTGUN_PELLET_SPEED);
        } else {
            speed = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_SPEED);
        }
        if (speed <= 0) speed = 60;
        int lifetime = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_LIFETIME_TICKS);
        if (lifetime <= 0) lifetime = 60;
        AmmoConfig.AmmoTypeDef ammo = MagazineManager.getCurrentAmmoType(weapon);
        if (ammo != null) speed *= ammo.bulletSpeedMult;
        double maxRange = (speed / 20.0) * lifetime;
        if (maxRange <= 0) maxRange = 60;

        // ── 水中弹速：水方块前的射程消耗 ──
        double waterSpeedPct = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_WATER_SPEED) / 100.0;
        if (waterSpeedPct <= 0) waterSpeedPct = 0.4;

        // ── 穿透 ──
        int penCount = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_COUNT);
        if (ammo != null) penCount += ammo.penetrationBonus;
        int penFalloffMode = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_FALLOFF_MODE);
        double penFalloff = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_FALLOFF) / 100.0;
        double penMinDmg = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_MIN_DAMAGE) / 100.0;
        boolean penParticle = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_PARTICLE) >= 1.0;
        boolean penSound = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_SOUND) >= 1.0;
        boolean glassPierce = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_GLASS_PIERCE) >= 1.0;
        boolean blockBreak = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_BLOCK_BREAK) >= 1.0;

        // ── 跳弹 ──
        double ricochetChance = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_RICOCHET_CHANCE) / 100.0;
        double ricochetMaxAngle = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_RICOCHET_ANGLE);

        // ── 枪线粒子 ──
        int trailType = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_TRAIL);
        int trailInterval = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_TRAIL_INTERVAL);
        if (trailInterval <= 0) trailInterval = 2;

        // ── 收集薄墙 ──
        Set<Material> penetrable = new HashSet<>();
        if (glassPierce) penetrable.addAll(THIN_BLOCKS);
        if (blockBreak) penetrable.addAll(THIN_BLOCKS);

        Vector dir = direction.clone().normalize();
        Location eye = shooter.getEyeLocation();
        World world = shooter.getWorld();

        // ── 主射线：处理方块跳弹/薄墙 ──
        RayTraceResult blockResult = world.rayTraceBlocks(eye, dir, maxRange,
            FluidCollisionMode.NEVER, true);
        double blockDist = maxRange;
        Location blockHitPoint = null;
        Vector currentDir = dir.clone();

        if (blockResult != null && blockResult.getHitBlock() != null) {
            Material blockType = blockResult.getHitBlock().getType();
            if (penetrable.contains(blockType)) {
                if (blockBreak) blockResult.getHitBlock().breakNaturally();
                Location past = blockResult.getHitPosition().toLocation(world)
                    .add(currentDir.clone().multiply(0.3));
                blockResult = world.rayTraceBlocks(past, currentDir,
                    maxRange - blockResult.getHitPosition().distance(eye.toVector()),
                    FluidCollisionMode.NEVER, true);
                blockDist = blockResult != null
                    ? blockResult.getHitPosition().distance(eye.toVector()) : maxRange;
            } else if (ricochetChance > 0) {
                // 跳弹判定
                Vector hitPos = blockResult.getHitPosition();
                Vector normal = getBlockNormal(blockResult.getHitBlock(), hitPos);
                double incidenceAngle = Math.toDegrees(Math.acos(
                    Math.abs(currentDir.clone().multiply(-1).normalize().dot(normal))));
                if (incidenceAngle <= ricochetMaxAngle
                    && ThreadLocalRandom.current().nextDouble() < ricochetChance) {
                    // 反射方向
                    Vector reflect = currentDir.clone().subtract(
                        normal.clone().multiply(2 * currentDir.dot(normal))).normalize();
                    Location past = blockResult.getHitPosition().toLocation(world)
                        .add(reflect.clone().multiply(0.3));
                    double distSoFar = hitPos.distance(eye.toVector());
                    double remaining = maxRange - distSoFar;
                    blockResult = world.rayTraceBlocks(past, reflect, remaining,
                        FluidCollisionMode.NEVER, true);
                    currentDir = reflect;
                    blockDist = blockResult != null
                        ? blockResult.getHitPosition().distance(eye.toVector()) : maxRange;
                    world.spawnParticle(Particle.CRIT, blockResult != null
                        ? blockResult.getHitPosition().toLocation(world) : past,
                        5, 0.1, 0.1, 0.1, 0);
                    world.playSound(hitPos.toLocation(world),
                        Sound.ENTITY_ARROW_SHOOT, 0.2f, 2.0f);
                } else {
                    blockDist = blockResult.getHitPosition().distance(eye.toVector());
                    blockHitPoint = blockResult.getHitPosition().toLocation(world);
                }
            } else {
                blockDist = blockResult.getHitPosition().distance(eye.toVector());
                blockHitPoint = blockResult.getHitPosition().toLocation(world);
            }
        }

        // ── 水中弹速：穿水时直接缩短有效距离，避免修改循环边界变量 ──
        double waterPenaltyAccum = 0;
        double checkStep = 0.5;
        for (double d = 0; d < blockDist; d += checkStep) {
            Location ck = eye.clone().add(currentDir.clone().multiply(d));
            if (ck.getBlock().isLiquid()) {
                waterPenaltyAccum += checkStep * (1.0 / waterSpeedPct - 1.0);
            }
        }
        blockDist -= waterPenaltyAccum;
        if (blockDist <= 0) blockDist = checkStep;

        // ── 实体收集 ──
        record EntityHit(LivingEntity entity, Location hitLoc, double distance) {}
        List<EntityHit> hits = new ArrayList<>();
        for (org.bukkit.entity.Entity e : world.getNearbyEntities(eye, maxRange, maxRange, maxRange)) {
            if (!(e instanceof LivingEntity le) || le == shooter) continue;
            if (!le.isValid() || le.isDead()) continue;
            BoundingBox bb = le.getBoundingBox();
            RayTraceResult rtr = bb.rayTrace(eye.toVector(), currentDir, blockDist);
            if (rtr == null) continue;
            Vector intersection = rtr.getHitPosition();
            double dist = intersection.distance(eye.toVector());
            if (dist > blockDist) continue;
            hits.add(new EntityHit(le, intersection.toLocation(world), dist));
        }
        hits.sort(Comparator.comparingDouble(h -> h.distance));

        // ── 穿射伤害 ──
        int hitCount = 0;
        Location lastHit = blockHitPoint != null ? blockHitPoint
            : eye.clone().add(currentDir.clone().multiply(blockDist));

        for (EntityHit hit : hits) {
            if (hitCount > penCount) break;
            lastHit = hit.hitLoc;

            // 穿透粒子+音效
            if (hitCount > 0 && penParticle) {
                world.spawnParticle(Particle.CRIT, hit.hitLoc, 5, 0.1, 0.1, 0.1, 0);
            }
            if (hitCount > 0 && penSound) {
                world.playSound(hit.hitLoc, Sound.ENTITY_ARROW_HIT, 0.4f, 1.5f);
            }

            double baseDamage = rollGunDamage(weapon);
            double finalDamage = baseDamage;
            if (ammo != null) finalDamage *= ammo.damageMult;

            double pelletCount = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_SHOTGUN_PELLET_COUNT);
            if (shotgunPellet && pelletCount > 0) {
                if (shotgunDivider != null && shotgunDivider <= 0)
                    finalDamage /= pelletCount;
                else if (shotgunDivider != null)
                    finalDamage = finalDamage * shotgunDivider / pelletCount;
            }

            if (hitCount > 0 && penFalloff > 0) {
                if (penFalloffMode == 1)
                    finalDamage *= Math.pow(1.0 - penFalloff, hitCount);
                else
                    finalDamage *= Math.max(penMinDmg, 1.0 - penFalloff * hitCount);
            }

            double fs = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_DAMAGE_FALLOFF_START);
            double fe = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_DAMAGE_FALLOFF_END);
            double mp = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_DAMAGE_MIN_PERCENT) / 100.0;
            if (hit.distance > fs && fe > fs) {
                double t = Math.min(1.0, (hit.distance - fs) / (fe - fs));
                finalDamage *= (1.0 - t * (1.0 - mp));
            }

            // ── RPG：暴击 ──
            double beforeCrit = finalDamage;
            finalDamage = AttributeCalculator.applyCrit(shooter, weapon, finalDamage);
            if (finalDamage > beforeCrit) {
                DebugManager.debugCrit(shooter,
                    AttributeStorage.getAttrValue(weapon, RpgAttribute.CRITICAL_CHANCE),
                    AttributeStorage.getAttrValue(weapon, RpgAttribute.CRITICAL_MULTIPLIER),
                    beforeCrit, finalDamage);
            }

            // ── RPG：穿透（低穿/高穿）──
            AttributeCalculator.PenetrationResult penResult =
                AttributeCalculator.calcPenetration(shooter, weapon, hit.entity, finalDamage);
            finalDamage += penResult.extraDamage;
            if (penResult.extraDamage > 0 || penResult.remainingArmorPct < 1.0) {
                DebugManager.debugPenetration(shooter,
                    AttributeStorage.getAttrValue(weapon, RpgAttribute.LOW_PENETRATION),
                    AttributeStorage.getAttrValue(weapon, RpgAttribute.HIGH_PENETRATION),
                    AttributeStorage.getAttrValue(weapon, RpgAttribute.PENETRATION_EFFICIENCY),
                    0, // toughness is internal to calcPenetration
                    penResult.extraDamage, penResult.remainingArmorPct);
            }

            // ── RPG：破甲（已有破甲伤害倍率）──
            double abMult = RpgCombatListener.applyRayArmorBreak(shooter, weapon, hit.entity);
            if (abMult > 1.0) {
                DebugManager.debugArmorBreak(shooter,
                    AttributeStorage.getAttrValue(weapon, RpgAttribute.ARMOR_BREAK_CHANCE),
                    abMult, "触发");
                finalDamage *= abMult;
            }

            // ── 部位伤害 ──
            finalDamage = applyHitzone(shooter, weapon, hit.entity, hit.hitLoc, finalDamage);

            // ── 弹药护甲穿透 ──
            if (ammo != null) {
                int armorIgnore = ammo.getEffectInt("armor_ignore", 0);
                if (armorIgnore > 0 && hit.entity.getAttribute(org.bukkit.attribute.Attribute.ARMOR) != null) {
                    double armor = hit.entity.getAttribute(org.bukkit.attribute.Attribute.ARMOR).getValue();
                    double ea = armor * (1.0 - armorIgnore / 100.0);
                    double oR = Math.min(0.8, armor * 0.04);
                    double eR = Math.min(0.8, ea * 0.04);
                    double mult = (1.0 - eR) / Math.max(0.01, (1.0 - oR));
                    finalDamage *= Math.min(5.0, Math.max(1.0, mult));
                }
            }

            if (finalDamage <= 0) { hitCount++; continue; }

            // ── 击杀连锁伤害加成 ──
            finalDamage *= MagazineManager.getKillChainDamageFactor(weapon);

            // ── RPG：闪避/命中/致盲 ──
            if (RpgCombatListener.checkRayDodge(shooter, weapon, hit.entity)) {
                hitCount++;
                continue;
            }

            // ── 伤害 ──
            hit.entity.setMetadata("xh_raytrace", new FixedMetadataValue(plugin, true));
            DamageSource dmgSource = DamageSource.builder(DamageType.ARROW)
                .withCausingEntity(shooter).withDirectEntity(shooter).build();
            hit.entity.damage(finalDamage, dmgSource);
            hit.entity.removeMetadata("xh_raytrace", plugin);

            // ── RPG：吸血 ──
            double beforeLs = finalDamage;
            AttributeCalculator.DamageResult lsResult =
                AttributeCalculator.applyLifesteal(shooter, weapon, hit.entity, finalDamage);
            if (lsResult.heal > 0) {
                RpgCombatListener.applyHeal(shooter, lsResult.heal);
                DebugManager.debugLifesteal(shooter,
                    AttributeStorage.getAttrValue(weapon, RpgAttribute.LIFESTEAL_CHANCE),
                    lsResult.heal, 0, lsResult.damage - beforeLs,
                    lsResult.heal, lsResult.damage - beforeLs);
            }

            // ── RPG：致盲 ──
            RpgCombatListener.applyRayBlind(shooter, weapon, hit.entity);

            // ── RPG：命中特效（减速/硬直/致盲）──
            RpgCombatListener.applyGunHitEffects(shooter, weapon, hit.entity);

            applyAmmoEffects(shooter, weapon, hit.entity);
            hitEntities.add(hit.entity);
            hitCount++;
        }

        // 方块命中特效
        if (blockHitPoint != null && hits.isEmpty()) {
            if (blockResult != null && blockResult.getHitBlock() != null)
                world.spawnParticle(Particle.BLOCK, blockHitPoint, 8,
                    0.05, 0.05, 0.05, 0.1, blockResult.getHitBlock().getBlockData());
            world.playSound(blockHitPoint, Sound.BLOCK_STONE_HIT, 0.3f, 1.0f);
        }

        drawRayTrail(eye, lastHit, trailType, trailInterval);
        return hitEntities;
    }

    // ==================== API：弩 (重力射线) ====================

    public static List<LivingEntity> shootRayGravity(Player shooter, ItemStack weapon, Vector direction) {
        double speed = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_SPEED);
        if (speed <= 0) speed = 60;
        int lifetime = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_LIFETIME_TICKS);
        if (lifetime <= 0) lifetime = 60;
        AmmoConfig.AmmoTypeDef ammo = MagazineManager.getCurrentAmmoType(weapon);
        if (ammo != null) speed *= ammo.bulletSpeedMult;
        double maxRange = (speed / 20.0) * lifetime;
        if (maxRange <= 0) maxRange = 60;

        int gravLevel = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_CROSSBOW_GRAVITY);
        int trailType = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_TRAIL);
        int trailInterval = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_TRAIL_INTERVAL);
        if (trailInterval <= 0) trailInterval = 3;

        double gravityPerStep = 0;
        if (gravLevel == 2) gravityPerStep = 0.005;
        else if (gravLevel == 1) gravityPerStep = 0.002;

        // 弩专用伤害（优先 GUN_CROSSBOW_DAMAGE，回退 GUN_DAMAGE）
        AttributeRange crossbowDmgRange = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.GUN_CROSSBOW_DAMAGE);
        double crossbowDmg = crossbowDmgRange != null && crossbowDmgRange.getMax() > RpgAttribute.GUN_CROSSBOW_DAMAGE.getDefaultValue()
            ? crossbowDmgRange.roll() : 0;

        SteppedResult result = steppedRay(shooter, weapon, direction, maxRange, gravityPerStep,
            false, 0, 0, false, 0, trailType, trailInterval, ammo, false, false,
            crossbowDmg);
        return result.hitEntities;
    }

    // ==================== API：榴弹 (重力+弹跳+引信) ====================

    public static List<LivingEntity> shootGrenadeRay(Player shooter, ItemStack weapon, Vector direction) {
        double speed = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_SPEED);
        if (speed <= 0) speed = 40;
        int lifetime = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_LIFETIME_TICKS);
        if (lifetime <= 0) lifetime = 60;
        AmmoConfig.AmmoTypeDef ammo = MagazineManager.getCurrentAmmoType(weapon);
        if (ammo != null) speed *= ammo.bulletSpeedMult;
        double maxRange = (speed / 20.0) * lifetime;
        if (maxRange <= 0) maxRange = 80;

        double rawRadius = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_GRENADE_RADIUS);
        final double radius = rawRadius > 0 ? rawRadius : 4;
        double rawDamage = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_GRENADE_DAMAGE);
        final double damage = rawDamage > 0 ? rawDamage : 60;
        final double selfFactor = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_GRENADE_SELF_DAMAGE_FACTOR) / 100.0;
        final boolean destroyBlocks = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_GRENADE_DESTROY_BLOCKS) >= 1.0;
        double rawKnockback = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_GRENADE_KNOCKBACK);
        final double knockback = rawKnockback > 0 ? rawKnockback : 1.5;
        int bounceCount = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_GRENADE_BOUNCE);
        int fuseTicks = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_GRENADE_FUSE_TICKS);

        double gravityPerStep = 0.008; // 榴弹重力比弩重

        int trailType = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_TRAIL);
        int trailInterval = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_TRAIL_INTERVAL);
        if (trailInterval <= 0) trailInterval = 3;

        SteppedResult result = steppedRay(shooter, weapon, direction, maxRange, gravityPerStep,
            false, 0, 0, true, bounceCount, trailType, trailInterval, ammo, false, false,
            0);

        Location hitPoint = result.finalLocation;
        if (fuseTicks > 0) {
            // 引信延迟爆炸
            final Location expPoint = hitPoint.clone();
            new org.bukkit.scheduler.BukkitRunnable() {
                int remaining = fuseTicks;
                @Override
                public void run() {
                    World w = expPoint.getWorld();
                    if (w == null) { cancel(); return; } // 世界已被卸载
                    if (remaining <= 0) {
                        SpecialWeapons.explodeGrenadeAt(shooter, expPoint, radius, damage,
                            selfFactor, knockback, destroyBlocks);
                        cancel();
                        return;
                    }
                    w.spawnParticle(Particle.SMOKE, expPoint, 1, 0.05, 0.05, 0.05, 0);
                    remaining--;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        } else {
            SpecialWeapons.explodeGrenadeAt(shooter, hitPoint, radius, damage,
                selfFactor, knockback, destroyBlocks);
        }

        return result.hitEntities;
    }

    // ==================== API：火箭筒 (追踪+遥控引爆) ====================

    /** 遥控引爆：Player → 火箭弹参数 */
    private static final Map<UUID, RocketRemote> remoteRockets = new HashMap<>();

    private static class RocketRemote {
        final Player shooter; final Location hitPoint;
        final double radius, damage, selfFactor; final boolean destroyBlocks;
        RocketRemote(Player s, Location p, double r, double d, double sf, boolean db) {
            shooter = s; hitPoint = p; radius = r; damage = d; selfFactor = sf; destroyBlocks = db;
        }
    }

    public static List<LivingEntity> shootRocketRay(Player shooter, ItemStack weapon, Vector direction) {
        double speed = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_SPEED);
        if (speed <= 0) speed = 30;
        int lifetime = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_LIFETIME_TICKS);
        if (lifetime <= 0) lifetime = 60;
        AmmoConfig.AmmoTypeDef ammo = MagazineManager.getCurrentAmmoType(weapon);
        if (ammo != null) speed *= ammo.bulletSpeedMult;
        double maxRange = (speed / 20.0) * lifetime;
        if (maxRange <= 0) maxRange = 90;

        boolean homing = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_ROCKET_HOMING) >= 1.0;
        double homingStrength = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_ROCKET_HOMING_STRENGTH) / 100.0;
        double homingRange = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_ROCKET_HOMING_RANGE);
        if (homingRange <= 0) homingRange = 30;

        double radius = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_ROCKET_RADIUS);
        if (radius <= 0) radius = 6;
        double damage = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_ROCKET_DAMAGE);
        if (damage <= 0) damage = 80;
        double selfFactor = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_ROCKET_SELF_DAMAGE_FACTOR) / 100.0;
        boolean destroyBlocks = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_ROCKET_DESTROY_BLOCKS) >= 1.0;
        boolean remote = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_ROCKET_REMOTE) >= 1.0;

        int trailType = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_TRAIL);
        int trailInterval = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_TRAIL_INTERVAL);
        if (trailInterval <= 0) trailInterval = 2;

        SteppedResult result = steppedRay(shooter, weapon, direction, maxRange, 0,
            homing, homingStrength, homingRange, false, 0, trailType, trailInterval, ammo,
            true, true, 0);

        Location hitPoint = result.finalLocation;

        if (remote) {
            remoteRockets.put(shooter.getUniqueId(),
                new RocketRemote(shooter, hitPoint, radius, damage, selfFactor, destroyBlocks));
            shooter.sendActionBar(net.kyori.adventure.text.Component.text(
                "火箭弹待命 (右击引爆)", net.kyori.adventure.text.format.NamedTextColor.GOLD));
        } else {
            SpecialWeapons.explodeRocketAt(shooter, hitPoint, radius, damage, selfFactor, destroyBlocks);
        }

        return result.hitEntities;
    }

    /** 遥控引爆 */
    public static boolean remoteDetonate(Player player) {
        RocketRemote remote = remoteRockets.remove(player.getUniqueId());
        if (remote == null) return false;
        SpecialWeapons.explodeRocketAt(remote.shooter, remote.hitPoint,
            remote.radius, remote.damage, remote.selfFactor, remote.destroyBlocks);
        return true;
    }

    /** 玩家切武器/离线时清除遥控火箭 */
    public static boolean hasRemoteRocket(UUID uuid) {
        return remoteRockets.containsKey(uuid);
    }

    /** 清理玩家遥控火箭（PlayerQuit/PlayerDeath 时调用） */
    public static void cleanupRemote(UUID uuid) {
        remoteRockets.remove(uuid);
    }

    // ==================== 步进射线核心 ====================

    private static class SteppedResult {
        final List<LivingEntity> hitEntities;
        final Location finalLocation;
        SteppedResult(List<LivingEntity> e, Location l) { hitEntities = e; finalLocation = l; }
    }

    /**
     * 步进射线：逐段前进，支持重力、追踪、弹跳。
     *
     * @param gravityPerStep 每步Y轴下坠量 (0=直线)
     * @param homing 是否追踪
     * @param homingStrength 追踪修正强度
     * @param homingRange 追踪检测范围
     * @param bounce 是否弹跳(榴弹)
     * @param maxBounces 最大弹跳次数
     */
    /**
     * @param isRocket 火箭用火焰粒子尾迹
     * @param noPenetrate 禁用穿透（榴弹/火箭直击不穿透）
     */
    private static SteppedResult steppedRay(Player shooter, ItemStack weapon, Vector direction,
                                             double maxRange, double gravityPerStep,
                                             boolean homing, double homingStrength, double homingRange,
                                             boolean bounce, int maxBounces,
                                             int trailType, int trailInterval,
                                             AmmoConfig.AmmoTypeDef ammo,
                                             boolean isRocket, boolean noPenetrate,
                                             double baseDamageOverride) {
        List<LivingEntity> hitEntities = new ArrayList<>();
        World world = shooter.getWorld();
        Location pos = shooter.getEyeLocation().clone();
        Vector dir = direction.clone().normalize();
        double traveled = 0;
        int trailCounter = 0;
        int bouncesDone = 0;

        // 穿透（重力射线也支持穿透，但榴弹/火箭不走穿透）
        int penCount = noPenetrate ? 0
            : (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_COUNT);
        if (ammo != null && !noPenetrate) penCount += ammo.penetrationBonus;
        int penFalloffMode = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_FALLOFF_MODE);
        double penFalloff = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_FALLOFF) / 100.0;
        double penMinDmg = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_MIN_DAMAGE) / 100.0;
        boolean penParticle = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_PARTICLE) >= 1.0;
        boolean penSound = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_SOUND) >= 1.0;
        boolean glassPierce = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_GLASS_PIERCE) >= 1.0;
        boolean blockBreak = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_BLOCK_BREAK) >= 1.0;
        double waterSpeedPct = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_WATER_SPEED) / 100.0;
        if (waterSpeedPct <= 0) waterSpeedPct = 0.4;

        Set<Material> penetrable = new HashSet<>();
        if (glassPierce) penetrable.addAll(THIN_BLOCKS);
        if (blockBreak) penetrable.addAll(THIN_BLOCKS);

        int hitCount = 0;
        Set<UUID> hitEntitiesThisShot = new HashSet<>();
        boolean entityStop = false; // 爆炸武器命中实体后立即停止

        while (traveled < maxRange && !entityStop) {
            // ── 水中弹速 ──
            double step = STEP;
            if (pos.getBlock().isLiquid()) step *= waterSpeedPct;

            // ── 追踪修正 ──
            if (homing) {
                LivingEntity target = findNearestTarget(pos, homingRange, shooter);
                if (target != null) {
                    Vector toTarget = target.getEyeLocation().toVector()
                        .subtract(pos.toVector()).normalize();
                    dir = dir.add(toTarget.multiply(homingStrength * 0.1)).normalize();
                }
            }

            // ── 重力 ──
            if (gravityPerStep > 0) {
                dir = dir.add(new Vector(0, -gravityPerStep, 0)).normalize();
            }

            Location next = pos.clone().add(dir.clone().multiply(step));
            Block block = next.getBlock();

            // ── 方块碰撞 ──
            if (block.getType().isSolid() && !block.isLiquid()) {
                if (penetrable.contains(block.getType())) {
                    if (blockBreak) block.breakNaturally();
                    pos = next.clone().add(dir.clone().multiply(0.3));
                    traveled += step;
                } else if (bounce && bouncesDone < maxBounces) {
                    // 弹跳：反射
                    Vector normal = getBlockNormal(block, next.toVector());
                    dir = dir.clone().subtract(
                        normal.clone().multiply(2 * dir.dot(normal))).normalize();
                    pos = next.clone().add(dir.clone().multiply(0.3));
                    traveled += step;
                    bouncesDone++;
                    world.spawnParticle(Particle.CRIT, next, 5, 0.1, 0.1, 0.1, 0);
                    world.playSound(next, Sound.BLOCK_STONE_HIT, 0.3f, 1.5f);
                } else {
                    // 停止
                    pos = next;
                    traveled += step;
                    break;
                }
            } else {
                pos = next;
                traveled += step;
            }

            // ── 实体命中检测 ──
            for (org.bukkit.entity.Entity e : world.getNearbyEntities(pos, 0.5, 0.5, 0.5)) {
                if (!(e instanceof LivingEntity le) || le == shooter) continue;
                if (!le.isValid() || le.isDead()) continue;
                if (hitEntitiesThisShot.contains(le.getUniqueId())) continue;
                if (!le.getBoundingBox().contains(pos.toVector())) continue;

                if (hitCount > penCount) break;

                if (hitCount > 0 && penParticle)
                    world.spawnParticle(Particle.CRIT, pos, 5, 0.1, 0.1, 0.1, 0);
                if (hitCount > 0 && penSound)
                    world.playSound(pos, Sound.ENTITY_ARROW_HIT, 0.4f, 1.5f);

                double baseDamage = baseDamageOverride > 0 ? baseDamageOverride : rollGunDamage(weapon);
                double finalDamage = baseDamage;
                if (ammo != null) finalDamage *= ammo.damageMult;

                if (hitCount > 0 && penFalloff > 0) {
                    if (penFalloffMode == 1)
                        finalDamage *= Math.pow(1.0 - penFalloff, hitCount);
                    else
                        finalDamage *= Math.max(penMinDmg, 1.0 - penFalloff * hitCount);
                }

                double fs = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_DAMAGE_FALLOFF_START);
                double fe = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_DAMAGE_FALLOFF_END);
                double mp = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_DAMAGE_MIN_PERCENT) / 100.0;
                double dist = pos.distance(shooter.getEyeLocation());
                if (dist > fs && fe > fs) {
                    double t = Math.min(1.0, (dist - fs) / (fe - fs));
                    finalDamage *= (1.0 - t * (1.0 - mp));
                }

                // ── RPG：暴击 ──
                finalDamage = AttributeCalculator.applyCrit(shooter, weapon, finalDamage);

                // ── RPG：穿透（低穿/高穿）──
                AttributeCalculator.PenetrationResult penResult =
                    AttributeCalculator.calcPenetration(shooter, weapon, le, finalDamage);
                finalDamage += penResult.extraDamage;

                // ── RPG：破甲（已有破甲伤害倍率）──
                double abMult = RpgCombatListener.applyRayArmorBreak(shooter, weapon, le);
                if (abMult > 1.0) finalDamage *= abMult;

                // ── 部位伤害 ──
                finalDamage = applyHitzone(shooter, weapon, le, pos, finalDamage);

                if (ammo != null) {
                    int armorIgnore = ammo.getEffectInt("armor_ignore", 0);
                    if (armorIgnore > 0 && le.getAttribute(org.bukkit.attribute.Attribute.ARMOR) != null) {
                        double armor = le.getAttribute(org.bukkit.attribute.Attribute.ARMOR).getValue();
                        double ea = armor * (1.0 - armorIgnore / 100.0);
                        double oR = Math.min(0.8, armor * 0.04);
                        double eR = Math.min(0.8, ea * 0.04);
                        double mult = (1.0 - eR) / Math.max(0.01, (1.0 - oR));
                        finalDamage *= Math.min(5.0, Math.max(1.0, mult));
                    }
                }

                if (finalDamage > 0) {
                    // ── RPG：闪避/命中/致盲 ──
                    if (RpgCombatListener.checkRayDodge(shooter, weapon, le)) {
                        hitEntitiesThisShot.add(le.getUniqueId());
                        hitCount++;
                        continue;
                    }

                    // ── 伤害 ──
                    le.setMetadata("xh_raytrace", new FixedMetadataValue(plugin, true));
                    DamageSource dmgSource = DamageSource.builder(DamageType.ARROW)
                        .withCausingEntity(shooter).withDirectEntity(shooter).build();
                    le.damage(finalDamage, dmgSource);
                    le.removeMetadata("xh_raytrace", plugin);

                    // ── RPG：吸血 ──
                    AttributeCalculator.DamageResult lsResult =
                        AttributeCalculator.applyLifesteal(shooter, weapon, le, finalDamage);
                    if (lsResult.heal > 0) RpgCombatListener.applyHeal(shooter, lsResult.heal);

                    // ── RPG：致盲 ──
                    RpgCombatListener.applyRayBlind(shooter, weapon, le);

                    // ── RPG：命中特效（减速/硬直/致盲）──
                    RpgCombatListener.applyGunHitEffects(shooter, weapon, le);

                    applyAmmoEffects(shooter, weapon, le);
                    hitEntities.add(le);
                    if (noPenetrate) entityStop = true; // 爆炸武器命中后停止
                }
                hitEntitiesThisShot.add(le.getUniqueId());
                hitCount++;
            }

            // ── 枪线粒子 ──
            trailCounter++;
            if (trailCounter >= trailInterval) {
                trailCounter = 0;
                spawnTrailParticle(trailType, pos, isRocket);
            }
        }

        return new SteppedResult(hitEntities, pos);
    }

    // ==================== 辅助 ====================

    private static LivingEntity findNearestTarget(Location center, double range, Player shooter) {
        LivingEntity nearest = null;
        double nearestDist = range;
        for (org.bukkit.entity.Entity e : center.getWorld().getNearbyEntities(center, range, range, range)) {
            if (!(e instanceof LivingEntity le) || le == shooter || !le.isValid() || le.isDead()) continue;
            double dist = center.distance(le.getLocation());
            if (dist < nearestDist) { nearestDist = dist; nearest = le; }
        }
        return nearest;
    }

    /** 方块法线（简化：6轴法线） */
    private static Vector getBlockNormal(Block block, Vector hitPos) {
        Location loc = block.getLocation();
        double bx = hitPos.getX() - loc.getX() - 0.5;
        double by = hitPos.getY() - loc.getY() - 0.5;
        double bz = hitPos.getZ() - loc.getZ() - 0.5;
        double ax = Math.abs(bx), ay = Math.abs(by), az = Math.abs(bz);
        if (ax >= ay && ax >= az) return new Vector(bx > 0 ? 1 : -1, 0, 0);
        if (ay >= ax && ay >= az) return new Vector(0, by > 0 ? 1 : -1, 0);
        return new Vector(0, 0, bz > 0 ? 1 : -1);
    }

    // ── 枪线粒子 ──

    private static void drawRayTrail(Location from, Location to, int trailType, int trailInterval) {
        World world = from.getWorld();
        Vector dir = to.toVector().subtract(from.toVector());
        double length = dir.length();
        dir.normalize();
        int counter = 0;
        for (double d = 0; d < length; d += STEP) {
            counter++;
            if (counter >= trailInterval) {
                counter = 0;
                spawnTrailParticle(trailType, from.clone().add(dir.clone().multiply(d)), false);
            }
        }
        // 终点火花
        world.spawnParticle(Particle.CRIT, to, 3, 0.05, 0.05, 0.05, 0);
    }

    private static void spawnTrailParticle(int trailType, Location loc, boolean isRocket) {
        World w = loc.getWorld();
        switch (trailType) {
            case 1:  w.spawnParticle(Particle.SMOKE, loc, 1, 0, 0, 0, 0); break;
            case 2:  w.spawnParticle(Particle.FLAME, loc, 1, 0, 0, 0, 0.01); break;
            case 3:  w.spawnParticle(Particle.CRIT, loc, 1, 0, 0, 0, 0); break;
            case 4:  w.spawnParticle(Particle.END_ROD, loc, 1, 0, 0, 0, 0); break;
            default:
                if (isRocket) {
                    w.spawnParticle(Particle.FLAME, loc, 1, 0, 0, 0, 0.01);
                    w.spawnParticle(Particle.SMOKE, loc, 1, 0, 0, 0, 0);
                } else {
                    w.spawnParticle(Particle.CRIT, loc, 1, 0, 0, 0, 0);
                }
        }
    }

    // ── 伤害 ──

    private static double applyHitzone(Player shooter, ItemStack weapon, LivingEntity target,
                                        Location hitLoc, double baseDamage) {
        if (weapon == null || !weapon.hasItemMeta()) return baseDamage;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        double headChance  = rollAttr(weapon, RpgAttribute.GUN_HEADSHOT_CHANCE);
        double headMult    = rollAttr(weapon, RpgAttribute.GUN_HEADSHOT_MULT);
        double upperChance = rollAttr(weapon, RpgAttribute.GUN_UPPER_CHANCE);
        double upperMult   = rollAttr(weapon, RpgAttribute.GUN_UPPER_MULT);
        double lowerChance = rollAttr(weapon, RpgAttribute.GUN_LOWER_CHANCE);
        double lowerMult   = rollAttr(weapon, RpgAttribute.GUN_LOWER_MULT);
        double legChance   = rollAttr(weapon, RpgAttribute.GUN_LEG_CHANCE);
        double legMult     = rollAttr(weapon, RpgAttribute.GUN_LEG_MULT);
        double headThresh  = rollAttr(weapon, RpgAttribute.GUN_HEADSHOT_THRESHOLD);
        double bodyThresh  = rollAttr(weapon, RpgAttribute.GUN_BODY_THRESHOLD);
        double legThresh   = rollAttr(weapon, RpgAttribute.GUN_LEG_THRESHOLD);

        double feetY = target.getLocation().getY();
        double eyeH  = target.getEyeHeight();
        double hitY  = hitLoc.getY();
        double ratio = (hitY - feetY) / Math.max(0.01, eyeH);

        double chance, mult;
        if (ratio >= headThresh / 100.0)      { chance = headChance;  mult = headMult; }
        else if (ratio >= bodyThresh / 100.0) { chance = upperChance; mult = upperMult; }
        else if (ratio >= legThresh / 100.0)  { chance = lowerChance; mult = lowerMult; }
        else                                   { chance = legChance;   mult = legMult; }

        if (chance <= 0) return baseDamage;
        if (chance >= 100) return baseDamage * mult / 100.0;
        return rng.nextDouble() * 100 < chance ? baseDamage * mult / 100.0 : baseDamage;
    }

    // ── 弹药效果 ──

    private static void applyAmmoEffects(Player shooter, ItemStack weapon, LivingEntity target) {
        AmmoConfig.AmmoTypeDef ammo = MagazineManager.getCurrentAmmoType(weapon);
        if (ammo == null || ammo.effects == null) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        org.bukkit.configuration.ConfigurationSection igniteSec = ammo.effects.getConfigurationSection("ignite");
        if (igniteSec != null) {
            int chance = igniteSec.getInt("chance", 0);
            int ticks = igniteSec.getInt("ticks", 40);
            if (chance > 0 && rng.nextInt(100) < chance)
                target.setFireTicks(Math.max(target.getFireTicks(), ticks));
        }

        org.bukkit.configuration.ConfigurationSection bleedSec = ammo.effects.getConfigurationSection("bleed");
        if (bleedSec != null) {
            int chance = bleedSec.getInt("chance", 0);
            int damage = bleedSec.getInt("damage", 0);
            int ticks = bleedSec.getInt("ticks", 0);
            if (chance > 0 && damage > 0 && ticks > 0 && rng.nextInt(100) < chance) {
                new org.bukkit.scheduler.BukkitRunnable() {
                    int remaining = ticks;
                    @Override public void run() {
                        if (!target.isValid() || target.isDead() || remaining <= 0) { cancel(); return; }
                        remaining--;
                        target.damage(damage);
                    }
                }.runTaskTimer(plugin, 0L, 2L);
            }
        }

        if (ammo.getEffectBool("smoke"))
            target.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                target.getLocation().add(0, 1, 0), 30, 2, 2, 2, 0.05);

        org.bukkit.configuration.ConfigurationSection blindSec = ammo.effects.getConfigurationSection("blind");
        if (blindSec != null) {
            int radius = blindSec.getInt("radius", 0);
            int ticks = blindSec.getInt("ticks", 0);
            if (radius > 0 && ticks > 0) {
                io.github.liyughj.xH.rpg.Attribute.BlindManager.apply(shooter, target, 100, 100, ticks);
                for (org.bukkit.entity.Entity e : target.getWorld().getNearbyEntities(
                    target.getLocation(), radius, radius, radius)) {
                    if (e instanceof LivingEntity le && le != target && le != shooter)
                        io.github.liyughj.xH.rpg.Attribute.BlindManager.apply(shooter, le, 80, 60, ticks);
                }
            }
        }

        double knockback = ammo.getEffectDouble("knockback", 0);
        if (knockback > 0 && target != shooter) {
            Vector kbDir = target.getLocation().toVector()
                .subtract(shooter.getLocation().toVector()).normalize();
            target.setVelocity(kbDir.multiply(knockback));
        }

        if (ammo.getEffectBool("no_kill") && target.getHealth() <= 1.0) target.setHealth(1.0);

        int fireDamage = ammo.getEffectInt("fire_damage", 0);
        if (fireDamage > 0 && target.getFireTicks() > 0) target.damage(fireDamage);

        if (ammo.getEffectBool("aoe_fire")) {
            Location loc = target.getLocation();
            for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++) {
                    Location fLoc = loc.clone().add(x, 0, z);
                    if (fLoc.getBlock().isEmpty()) fLoc.getBlock().setType(Material.FIRE);
                }
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override public void run() {
                    for (int x = -1; x <= 1; x++)
                        for (int z = -1; z <= 1; z++) {
                            Location fLoc = loc.clone().add(x, 0, z);
                            if (fLoc.getBlock().getType() == Material.FIRE)
                                fLoc.getBlock().setType(Material.AIR);
                        }
                }
            }.runTaskLater(plugin, 60L);
        }

        if (ammo.getEffectBool("explosion"))
            target.getWorld().createExplosion(target.getLocation(), 2.0f, false, false, shooter);
    }

    // ── 工具 ──

    private static double rollGunDamage(ItemStack weapon) {
        AttributeRange range = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.GUN_DAMAGE);
        return range != null ? range.roll() : 5.0;
    }

    private static double rollAttr(ItemStack weapon, RpgAttribute attr) {
        AttributeRange range = AttributeStorage.getItemAttrRange(weapon, attr);
        return range.roll();
    }

    private static final Set<Material> THIN_BLOCKS = Set.of(
        Material.GLASS_PANE, Material.WHITE_STAINED_GLASS_PANE,
        Material.ORANGE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE,
        Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE,
        Material.LIME_STAINED_GLASS_PANE, Material.PINK_STAINED_GLASS_PANE,
        Material.GRAY_STAINED_GLASS_PANE, Material.LIGHT_GRAY_STAINED_GLASS_PANE,
        Material.CYAN_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE,
        Material.BLUE_STAINED_GLASS_PANE, Material.BROWN_STAINED_GLASS_PANE,
        Material.GREEN_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE,
        Material.BLACK_STAINED_GLASS_PANE,
        Material.IRON_BARS, Material.IRON_TRAPDOOR,
        Material.OAK_FENCE, Material.SPRUCE_FENCE, Material.BIRCH_FENCE,
        Material.JUNGLE_FENCE, Material.ACACIA_FENCE, Material.DARK_OAK_FENCE,
        Material.MANGROVE_FENCE, Material.CHERRY_FENCE, Material.BAMBOO_FENCE,
        Material.CRIMSON_FENCE, Material.WARPED_FENCE,
        Material.NETHER_BRICK_FENCE
    );
}
