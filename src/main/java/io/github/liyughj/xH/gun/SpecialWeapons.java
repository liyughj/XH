package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 特殊武器发射逻辑。
 * 由 GunListener.doShoot 根据 gun_weapon_type 分发到此。
 */
public final class SpecialWeapons {

    private SpecialWeapons() {}

    // ───────────────── 霰弹枪 ─────────────────

    /** 发射霰弹：N次射线散布 */
    public static void shootShotgun(JavaPlugin plugin, Player player, ItemStack weapon) {
        Vector baseDir = player.getEyeLocation().getDirection();
        int pelletCount = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_SHOTGUN_PELLET_COUNT);
        if (pelletCount <= 0) pelletCount = 8;

        int spreadMode = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_SHOTGUN_SPREAD_MODE);
        double divider = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_SHOTGUN_DAMAGE_DIVIDER) / 100.0;

        double spreadAngle = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_SPREAD_MIN);
        if (spreadAngle <= 0) spreadAngle = 3;

        for (int i = 0; i < pelletCount; i++) {
            Vector pelletDir = applyShotgunSpread(player, weapon, baseDir, i, pelletCount, spreadMode, spreadAngle);
            RayTraceManager.shootRayNormal(player, weapon, pelletDir, true, i, divider);
        }

        // 后坐和音效已由 GunListener.doShoot 统一处理
    }

    private static Vector applyShotgunSpread(Player player, ItemStack weapon, Vector baseDir,
                                              int index, int total, int mode, double angleDeg) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double spreadRad = Math.toRadians(angleDeg);
        double theta;
        double phi;

        switch (mode) {
            case 1: // 中心偏重
                if (index == 0) return baseDir.clone();
                // fall through
            case 0: // 均匀圆
            default:
                theta = spreadRad * Math.sqrt(rng.nextDouble());
                phi = rng.nextDouble() * 2 * Math.PI;
                break;
            case 2: // 环形
                theta = spreadRad;
                phi = (2 * Math.PI * index) / total;
                break;
            case 3: // 水平线
                theta = spreadRad * Math.sqrt(rng.nextDouble());
                phi = (rng.nextBoolean() ? 0 : Math.PI);
                break;
        }

        return rotateVector(baseDir, theta, phi);
    }

    // ───────────────── 弩 ─────────────────

    /** 发射弩箭：射线模式 + 流血 */
    public static void shootCrossbow(JavaPlugin plugin, Player player, ItemStack weapon) {
        Vector baseDir = player.getEyeLocation().getDirection();
        Vector spreadDir = SpreadCalculator.applySpread(player, weapon, baseDir);

        /* 重力射线命中 */
        List<LivingEntity> hits = RayTraceManager.shootRayGravity(player, weapon, spreadDir);

        /* 弩箭流血 */
        double bleedChance = AttributeStorage.getAttrValue(weapon, RpgAttribute.BLEED_CHANCE);
        if (bleedChance > 0) {
            double bleedDamage = AttributeStorage.getAttrValue(weapon, RpgAttribute.BLEED_DAMAGE);
            int bleedTicks = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.BLEED_TICKS);
            for (LivingEntity target : hits) {
                if (!target.isValid() || target.isDead()) continue;
                if (ThreadLocalRandom.current().nextDouble() * 100 >= bleedChance) continue;
                new org.bukkit.scheduler.BukkitRunnable() {
                    int remaining = bleedTicks;
                    @Override
                    public void run() {
                        if (!target.isValid() || target.isDead() || remaining <= 0) { cancel(); return; }
                        target.damage(bleedDamage / 20.0); // 每秒伤害
                        remaining--;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }
        }

        // 后坐和音效已由 GunListener.doShoot 统一处理

        // 弩装填冷却
        double reloadTicks = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_CROSSBOW_RELOAD_TICKS);
        if (reloadTicks > 0) {
            crossbowCooldowns.put(player.getUniqueId(),
                player.getWorld().getGameTime() + (long) reloadTicks);
        }
    }

    /** 弩是否在装填冷却中 */
    public static boolean isCrossbowOnCooldown(Player player, ItemStack weapon) {
        Long endTick = crossbowCooldowns.get(player.getUniqueId());
        if (endTick == null) return false;
        if (player.getWorld().getGameTime() >= endTick) {
            crossbowCooldowns.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    /** 弩装填冷却 Map */
    private static final Map<UUID, Long> crossbowCooldowns = new HashMap<>();

    // ───────────────── 喷火器 ─────────────────

    private static final Map<UUID, FlamethrowerTask> flameTasks = new HashMap<>();

    private static class FlamethrowerTask {
        int taskId;
        double fuel;
        double fuelMax;
        double fuelRegen;   // 每秒恢复量
        boolean active;
    }

    /** 开始/持续喷火（全自动 toggle 调用） */
    public static boolean startFlame(JavaPlugin plugin, Player player, ItemStack weapon) {
        UUID uid = player.getUniqueId();
        if (flameTasks.containsKey(uid)) return false; // 已喷射中

        double fuelMax = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_FLAME_FUEL_MAX);
        double fuelPerTick = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_FLAME_FUEL_PER_TICK);
        double fuelRegen = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_FLAME_FUEL_REGEN);
        double damagePerTick = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_FLAME_DAMAGE_PER_TICK);
        int interval = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_FLAME_DAMAGE_INTERVAL);
        double range = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_FLAME_RANGE);
        double spreadAngle = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_FLAME_SPREAD_ANGLE);
        int igniteTicks = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_FLAME_IGNITE_TICKS);
        int particleDensity = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_FLAME_PARTICLE_DENSITY);
        if (range <= 0) range = 5;
        if (interval <= 0) interval = 4;

        FlamethrowerTask ft = new FlamethrowerTask();
        ft.fuel = fuelMax;
        ft.fuelMax = fuelMax;
        ft.fuelRegen = fuelRegen;
        ft.active = true;

        final double fRange = range;
        final double fSpreadAngle = spreadAngle;
        final double fDamagePerTick = damagePerTick;
        final int fIgniteTicks = igniteTicks;
        final int fParticleDensity = particleDensity;
        final int fInterval = interval;
        final double fFuelPerTick = fuelPerTick;

        ft.taskId = new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!ft.active || !player.isOnline() || !player.isValid()) {
                    stopFlame(player);
                    cancel();
                    return;
                }
                ItemStack current = player.getInventory().getItemInMainHand();
                if (!GunListener.isGunStatic(current)) {
                    stopFlame(player);
                    cancel();
                    return;
                }

                tick++;

                // 燃料
                ft.fuel -= fFuelPerTick;
                if (ft.fuel <= 0) {
                    stopFlame(player);
                    player.sendActionBar(Component.text("燃料耗尽!", NamedTextColor.RED));
                    cancel();
                    return;
                }

                // 伤害判定
                if (tick % fInterval == 0) {
                    doFlameDamage(player, weapon, fRange, fSpreadAngle, fDamagePerTick, fIgniteTicks);
                }

                // 粒子
                if (fParticleDensity > 0) {
                    Location loc = player.getEyeLocation();
                    Vector dir = loc.getDirection();
                    for (int i = 0; i < fParticleDensity; i++) {
                        double dist = ThreadLocalRandom.current().nextDouble(fRange);
                        double angleOff = (ThreadLocalRandom.current().nextDouble() - 0.5) * fSpreadAngle;
                        Vector pDir = rotateVector2D(dir, Math.toRadians(angleOff));
                        Location pLoc = loc.clone().add(pDir.clone().multiply(dist + 1));
                        player.getWorld().spawnParticle(Particle.FLAME, pLoc, 1, 0, 0, 0, 0);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId();

        flameTasks.put(uid, ft);
        player.sendActionBar(Component.text("喷火器 开启", NamedTextColor.GOLD));
        return true;
    }

    /** 停止喷火 */
    public static void stopFlame(Player player) {
        FlamethrowerTask ft = flameTasks.remove(player.getUniqueId());
        if (ft != null) {
            ft.active = false;
            plugin().getServer().getScheduler().cancelTask(ft.taskId);
        }
    }

    /** 是否喷火中 */
    public static boolean isFlameActive(Player player) {
        FlamethrowerTask ft = flameTasks.get(player.getUniqueId());
        return ft != null && ft.active;
    }

    private static void doFlameDamage(Player player, ItemStack weapon,
                                       double range, double angleDeg, double damage, int igniteTicks) {
        Location eye = player.getEyeLocation();
        Vector baseDir = eye.getDirection();
        World world = player.getWorld();

        // 扇形检测：取前方范围内的实体
        for (Entity entity : world.getNearbyEntities(eye, range, range, range)) {
            if (!(entity instanceof LivingEntity target) || target == player) continue;
            if (!target.isValid() || target.isDead()) continue;

            Location targetLoc = target.getLocation().add(0, target.getHeight() / 2, 0);
            Vector toTarget = targetLoc.toVector().subtract(eye.toVector());
            double dist = toTarget.length();
            if (dist > range) continue;

            toTarget.normalize();
            double dot = baseDir.dot(toTarget);
            double angleRad = Math.toRadians(angleDeg);
            if (dot < Math.cos(angleRad / 2)) continue;

            // 伤害
            target.damage(damage, player);
            target.setFireTicks(Math.max(target.getFireTicks(), igniteTicks));
        }
    }

    // ───────────────── 榴弹发射器 ─────────────────

    /** 发射榴弹：步进射线（重力+弹跳+引信） → 爆炸 */
    public static void shootGrenade(JavaPlugin plugin, Player player, ItemStack weapon) {
        Vector baseDir = player.getEyeLocation().getDirection();
        Vector spreadDir = SpreadCalculator.applySpread(player, weapon, baseDir);
        RayTraceManager.shootGrenadeRay(player, weapon, spreadDir);
    }

    /** 榴弹爆炸（Arrow版，兼容旧调用） */
    public static void explodeGrenade(Arrow arrow, Location center, Player shooter) {
        double radius = 4, damage = 60, selfFactor = 0.5, knockback = 1.5;
        boolean destroyBlocks = false;

        Double r = getArrowPDC(arrow, "grenade_radius", Double.class);
        Double d = getArrowPDC(arrow, "grenade_damage", Double.class);
        Double sf = getArrowPDC(arrow, "grenade_self_factor", Double.class);
        Byte db = getArrowPDC(arrow, "grenade_destroy", Byte.class);
        Double kb = getArrowPDC(arrow, "grenade_knockback", Double.class);
        if (r != null) radius = r;
        if (d != null) damage = d;
        if (sf != null) selfFactor = sf;
        if (db != null) destroyBlocks = db == 1;
        if (kb != null) knockback = kb;

        explodeGrenadeAt(shooter, center, radius, damage, selfFactor, knockback, destroyBlocks);
        arrow.remove();
    }

    /** 榴弹爆炸（参数版，射线模式用） */
    public static void explodeGrenadeAt(Player shooter, Location center,
                                         double radius, double damage, double selfFactor,
                                         double knockback, boolean destroyBlocks) {

        // 粒子+音效
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 10, 1, 1, 1, 0.5);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);

        // 伤害周围实体
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || !target.isValid() || target.isDead()) continue;
            double dist = target.getLocation().distance(center);
            if (dist > radius) continue;
            double falloff = 1.0 - (dist / radius);
            double finalDamage = damage * falloff;

            if (target == shooter) {
                finalDamage *= selfFactor;
            }

            if (finalDamage > 0) {
                target.damage(finalDamage, shooter);
                // 击退（避免目标与爆炸中心重合时零向量 normalize 产生 NaN）
                Vector kbDir = target.getLocation().toVector().subtract(center.toVector());
                if (kbDir.lengthSquared() > 1.0E-6) {
                    kbDir.normalize();
                    target.setVelocity(kbDir.multiply(knockback * falloff));
                }
            }
        }

        // 破坏方块
        if (destroyBlocks) {
            center.getWorld().createExplosion(center, (float) radius / 2, true, true, shooter);
        }
    }

    // ───────────────── 火箭筒 ─────────────────

    /** 发射火箭弹：步进射线（追踪+遥控引爆） → 爆炸 */
    public static void shootRocket(JavaPlugin plugin, Player player, ItemStack weapon) {
        Vector baseDir = player.getEyeLocation().getDirection();
        Vector spreadDir = SpreadCalculator.applySpread(player, weapon, baseDir);
        RayTraceManager.shootRocketRay(player, weapon, spreadDir);
    }

    /** 遥控引爆：委托给 RayTraceManager */
    public static boolean remoteDetonate(Player player) {
        return RayTraceManager.remoteDetonate(player);
    }

    /** 火箭弹命中/遥控引爆（Arrow版兼容，读PDC→explodeRocketAt） */
    public static void explodeRocketOnHit(Arrow arrow, Player shooter) {
        Double r = getArrowPDC(arrow, "rocket_radius", Double.class);
        Double d = getArrowPDC(arrow, "rocket_damage", Double.class);
        Double sf = getArrowPDC(arrow, "rocket_self_factor", Double.class);
        Byte db = getArrowPDC(arrow, "rocket_destroy", Byte.class);
        double radius = r != null ? r : 6;
        double damage = d != null ? d : 80;
        double selfFactor = sf != null ? sf : 0.5;
        boolean destroyBlocks = db != null && db == 1;
        Location loc = arrow.getLocation();
        arrow.remove();
        explodeRocketAt(shooter, loc, radius, damage, selfFactor, destroyBlocks);
    }

    /** 火箭弹爆炸（参数版，射线模式用） */
    public static void explodeRocketAt(Player shooter, Location center,
                                        double radius, double damage, double selfFactor,
                                        boolean destroyBlocks) {
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 15, 2, 2, 2, 1);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);

        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || !target.isValid() || target.isDead()) continue;
            double dist = target.getLocation().distance(center);
            if (dist > radius) continue;
            double falloff = 1.0 - (dist / radius);
            double finalDamage = damage * falloff;

            if (target == shooter) finalDamage *= selfFactor;

            if (finalDamage > 0) {
                target.damage(finalDamage, shooter);
                Vector kbDir = target.getLocation().toVector().subtract(center.toVector());
                if (kbDir.lengthSquared() > 1.0E-6) {
                    kbDir.normalize();
                    target.setVelocity(kbDir.multiply(2.0 * falloff));
                }
            }
        }

        if (destroyBlocks) {
            center.getWorld().createExplosion(center, (float) radius, true, true, shooter);
        }
    }

    // ───────────────── 激光枪 ─────────────────

    private static final Map<UUID, LaserTask> laserTasks = new HashMap<>();

    private static class LaserTask {
        int taskId;
        double energy;
        double energyMax;
        double energyRegen;  // 每秒恢复量
        boolean active;
    }

    /** 发射激光（点射/首次按下） */
    public static boolean shootLaser(JavaPlugin plugin, Player player, ItemStack weapon) {
        double range = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_RANGE);
        if (range <= 0) range = 40;
        double damage = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_DAMAGE);
        double energyPerShot = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_ENERGY_PER_SHOT);
        double energyMax = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_ENERGY_MAX);
        double energyRegen = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_ENERGY_REGEN);
        int colorInt = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_COLOR);
        double thickness = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_THICKNESS);
        boolean pierce = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_PIERCE) >= 1.0;

        // 能量检查
        LaserTask lt = laserTasks.computeIfAbsent(player.getUniqueId(), k -> {
            LaserTask t = new LaserTask();
            t.energy = energyMax;
            t.energyMax = energyMax;
            t.energyRegen = energyRegen;
            return t;
        });
        if (lt.energy < energyPerShot) {
            player.sendActionBar(Component.text("能量不足!", NamedTextColor.RED));
            return false;
        }
        lt.energy -= energyPerShot;

        // Hitscan 射线
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        World world = player.getWorld();

        RayTraceResult result = world.rayTraceEntities(eye, dir, range,
            entity -> entity instanceof LivingEntity && entity != player && ((LivingEntity) entity).isValid());
        Location hitPoint = eye.clone().add(dir.clone().multiply(range));

        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            hitPoint = result.getHitPosition().toLocation(world);
            target.damage(damage, player);

            // 穿透
            if (pierce) {
                // 继续射线查找下一个
                Vector newDir = dir.clone();
                Location newEye = hitPoint.clone().add(newDir.clone().multiply(0.5));
                RayTraceResult result2 = world.rayTraceEntities(newEye, newDir, range - hitPoint.distance(eye),
                    entity -> entity instanceof LivingEntity && entity != player && entity != target && ((LivingEntity) entity).isValid());
                if (result2 != null && result2.getHitEntity() instanceof LivingEntity target2) {
                    target2.damage(damage * 0.7, player);
                }
            }
        }

        // 激光粒子线
        drawLaserLine(eye, hitPoint, colorInt, thickness);

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.3f, 2.0f);
        return true;
    }

    /** 开始持续激光 */
    public static boolean startLaserContinuous(JavaPlugin plugin, Player player, ItemStack weapon) {
        UUID uid = player.getUniqueId();
        if (laserTasks.containsKey(uid) && laserTasks.get(uid).active) return false;

        double range = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_RANGE);
        double damage = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_DAMAGE);
        double energyPerShot = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_ENERGY_PER_SHOT);
        double energyMax = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_ENERGY_MAX);
        double energyRegen = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_ENERGY_REGEN);
        int colorInt = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_COLOR);
        double thickness = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_THICKNESS);

        LaserTask lt = laserTasks.computeIfAbsent(uid, k -> {
            LaserTask t = new LaserTask();
            t.energy = energyMax;
            t.energyMax = energyMax;
            t.energyRegen = energyRegen;
            return t;
        });
        lt.active = true;

        final double lRange = range;
        final double lDamage = damage;
        final double lEnergyPerShot = energyPerShot;
        final int lColorInt = colorInt;
        final double lThickness = thickness;

        lt.taskId = new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!lt.active || !player.isOnline() || !player.isValid()) {
                    stopLaser(player);
                    cancel();
                    return;
                }
                ItemStack current = player.getInventory().getItemInMainHand();
                if (!GunListener.isGunStatic(current)) {
                    stopLaser(player);
                    cancel();
                    return;
                }

                tick++;
                // 持续激光每4tick判一次
                if (tick % 4 != 0) return;

                if (lt.energy < lEnergyPerShot) {
                    stopLaser(player);
                    player.sendActionBar(Component.text("能量耗尽!", NamedTextColor.RED));
                    cancel();
                    return;
                }
                lt.energy -= lEnergyPerShot;

                Location eye = player.getEyeLocation();
                Vector dir = eye.getDirection();
                World world = player.getWorld();

                RayTraceResult result = world.rayTraceEntities(eye, dir, lRange,
                    entity -> entity instanceof LivingEntity && entity != player && ((LivingEntity) entity).isValid());
                Location hitPoint = eye.clone().add(dir.clone().multiply(lRange));

                if (result != null && result.getHitEntity() instanceof LivingEntity target) {
                    hitPoint = result.getHitPosition().toLocation(world);
                    target.damage(lDamage * 0.5, player);
                }

                drawLaserLine(eye, hitPoint, lColorInt, lThickness);
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId();

        player.sendActionBar(Component.text("激光 开启", NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    /** 停止激光 */
    public static void stopLaser(Player player) {
        LaserTask lt = laserTasks.remove(player.getUniqueId());
        if (lt != null) {
            lt.active = false;
            plugin().getServer().getScheduler().cancelTask(lt.taskId);
        }
    }

    /** 是否激光中 */
    public static boolean isLaserActive(Player player) {
        LaserTask lt = laserTasks.get(player.getUniqueId());
        return lt != null && lt.active;
    }

    /* ─── 燃料/能量恢复（由 GunTickTask 每 5 tick 调用） ─── */

    /** 喷火器燃料恢复（未开火时） */
    public static void regenerateFlameFuel(Player player) {
        FlamethrowerTask ft = flameTasks.get(player.getUniqueId());
        if (ft == null || ft.active) return; // 开火中不恢复
        if (ft.fuel >= ft.fuelMax) return;
        ft.fuel = Math.min(ft.fuelMax, ft.fuel + ft.fuelRegen / 4.0); // 每5tick=0.25s, 分4次每秒
    }

    /** 激光能量恢复（未开火时） */
    public static void regenerateLaserEnergy(Player player) {
        LaserTask lt = laserTasks.get(player.getUniqueId());
        if (lt == null || lt.active) return;
        if (lt.energy >= lt.energyMax) return;
        lt.energy = Math.min(lt.energyMax, lt.energy + lt.energyRegen / 4.0);
    }

    // ───────────────── 工具方法 ─────────────────

    private static JavaPlugin pluginInstance;
    public static void init(JavaPlugin plugin) { pluginInstance = plugin; }
    static JavaPlugin plugin() { return pluginInstance; }

    private static Vector rotateVector(Vector dir, double theta, double phi) {
        Vector axisX = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
        if (axisX.lengthSquared() < 0.001) axisX = new Vector(1, 0, 0);
        Vector axisY = dir.clone().crossProduct(axisX).normalize();

        return dir.clone()
            .add(axisX.multiply(Math.sin(theta) * Math.cos(phi)))
            .add(axisY.multiply(Math.sin(theta) * Math.sin(phi)))
            .normalize();
    }

    /** 2D平面旋转（用于喷火器扇形） */
    private static Vector rotateVector2D(Vector dir, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vector(
            dir.getX() * cos - dir.getZ() * sin,
            dir.getY(),
            dir.getX() * sin + dir.getZ() * cos
        ).normalize();
    }

    @SuppressWarnings("unchecked")
    private static <T> T getArrowPDC(Arrow arrow, String key, Class<T> type) {
        if (!arrow.isValid()) return null;
        NamespacedKey nk = new NamespacedKey("xh", key);
        if (type == Double.class) return (T) arrow.getPersistentDataContainer().get(nk, PersistentDataType.DOUBLE);
        if (type == Integer.class) return (T) arrow.getPersistentDataContainer().get(nk, PersistentDataType.INTEGER);
        if (type == Byte.class) return (T) arrow.getPersistentDataContainer().get(nk, PersistentDataType.BYTE);
        return null;
    }

    public static void drawLaserLine(Location from, Location to, int color, double thickness) {
        Vector dir = to.toVector().subtract(from.toVector());
        double length = dir.length();
        dir.normalize();
        World world = from.getWorld();

        // RGB拆解
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        for (double d = 0; d < length; d += 0.3) {
            Location point = from.clone().add(dir.clone().multiply(d));
            world.spawnParticle(Particle.DUST, point, 1, r, g, b, 1);
        }
    }
}
