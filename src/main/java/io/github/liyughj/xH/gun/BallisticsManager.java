package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeRange;
import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import org.bukkit.Particle;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弹道系统管理器。
 * 管理 Arrow 弹道物理：速度、重力、空气阻力、距离伤害衰减、粒子尾迹。
 */
public class BallisticsManager {

    private static JavaPlugin plugin;

    public static void init(JavaPlugin p) {
        plugin = p;
    }

    /** Arrow UUID → BulletMeta */
    private static final Map<UUID, BulletMeta> activeBullets = new ConcurrentHashMap<>();

    public static class BulletMeta {
        Vector velocity;            // 当前速度（blocks/tick）
        double drag;                // 空气阻力系数（0~1，每tick速度乘以 1-drag）
        int lifetime;               // 剩余生命tick
        int trailInterval;          // 粒子间隔
        int trailCounter;           // 粒子计数器
        int gravityLevel;           // 0=none 1=weak 2=normal
        double falloffStart;        // 衰减开始距离
        double falloffEnd;          // 衰减结束距离
        double minPercent;         // 最低伤害保留%
        Player shooter;            // 射击者
        double baseDamage;         // 基础伤害（用于距离衰减计算）
        Object extra;              // 特殊武器附加数据
        String ammoTrailType;      // 弹药尾迹类型: null/"flame"/"smoke"
        int ricochetCount;         // 剩余跳弹次数
        double ricochetChance;     // 每次命中方块的跳弹概率（%）
        double waterSpeedMult;     // 水中弹速乘数
        boolean glassPierce;       // 穿透玻璃
        boolean wasInWater;        // 上一tick是否在水中

        public boolean canRicochet() { return ricochetCount > 0; }
        public void consumeRicochet() { ricochetCount--; }
        public boolean isGlassPierce() { return glassPierce; }
        public double getRicochetChance() { return ricochetChance; }
        /** 跳弹后更新当前速度向量 */
        public void setVelocity(Vector v) { this.velocity = v; }
    }

    /**
     * 使用 ballistics 系统发射 Arrow。
     * 覆盖 doShoot 中的默认 Arrow 创建逻辑。
     */
    public static Arrow launchBullet(Player player, ItemStack weapon, Vector spreadDir) {
        if (!GunSystemConfig.isSystemEnabled(player, "ballistics")) {
            // 降级为默认
            Arrow arrow = player.launchProjectile(Arrow.class);
            arrow.setVelocity(spreadDir);
            arrow.setInvisible(true);
            arrow.setCritical(false);
            arrow.setGravity(false);
            arrow.setSilent(true);
            arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
            // 穿透等级
            int penCount = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_COUNT);
            if (penCount > 0) arrow.setPierceLevel(penCount);
            return arrow;
        }

        double speed = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_SPEED);
        int gravityLevel = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_GRAVITY);
        int lifetime = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_LIFETIME_TICKS);
        double drag = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_DRAG) / 100.0;
        int trailInterval = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_TRAIL_INTERVAL);
        double falloffStart = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_DAMAGE_FALLOFF_START);
        double falloffEnd = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_DAMAGE_FALLOFF_END);
        double minPct = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_DAMAGE_MIN_PERCENT);

        // 弹种修正
        AmmoConfig.AmmoTypeDef ammo = MagazineManager.getCurrentAmmoType(weapon);
        if (ammo != null) {
            speed *= ammo.bulletSpeedMult;
            gravityLevel = (int) Math.round(gravityLevel * ammo.bulletGravityMult);
            drag *= ammo.bulletDragMult;
        }

        Arrow arrow = player.launchProjectile(Arrow.class);
        Vector vel = spreadDir.clone().multiply(speed / 20.0);
        arrow.setVelocity(vel);
        arrow.setInvisible(true);
        arrow.setCritical(false);
        arrow.setSilent(true);
        arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);

        // 重力
        boolean hasGravity = gravityLevel >= 2;
        arrow.setGravity(hasGravity);
        if (gravityLevel == 1) {
            // 弱重力通过自定义 tick 模拟
        }

        // 穿透等级（让MC允许箭矢穿透多个实体）
        int penCount = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_COUNT);
        if (ammo != null) penCount += ammo.penetrationBonus;
        if (penCount > 0) {
            arrow.setPierceLevel(penCount);
        }
        // 方块击穿标记 (RpgCombatListener 读取)
        boolean blockBreak = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_PENETRATION_BLOCK_BREAK) >= 1.0;
        if (blockBreak) {
            arrow.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey("xh", "pen_block_break"),
                org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            arrow.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey("xh", "pen_count"),
                org.bukkit.persistence.PersistentDataType.INTEGER, penCount);
        }

        BulletMeta meta = new BulletMeta();
        meta.velocity = vel;
        meta.drag = Math.max(0, Math.min(drag, 0.5)); // 限制最大阻力
        // 基础伤害（区间随机取一值，供距离衰减计算）
        AttributeRange dmgRange = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.GUN_DAMAGE);
        meta.baseDamage = (dmgRange != null) ? dmgRange.roll() : 5.0;
        meta.lifetime = lifetime;
        meta.trailInterval = trailInterval;
        meta.trailCounter = 0;
        // 尾迹：弹药优先 > 枪械默认
        if (ammo != null && ammo.effects != null) {
            meta.ammoTrailType = ammo.effects.getString("trail", null);
        }
        if (meta.ammoTrailType == null) {
            int trailCode = (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_TRAIL);
            // 0=off 1=smoke 2=flame 3=crit 4=end_rod
            switch (trailCode) {
                case 1: meta.ammoTrailType = "smoke"; break;
                case 2: meta.ammoTrailType = "flame"; break;
                case 3: meta.ammoTrailType = "crit"; break;
                case 4: meta.ammoTrailType = "end_rod"; break;
                default: meta.ammoTrailType = null;
            }
        }
        if (meta.ammoTrailType != null && meta.trailInterval <= 0) {
            meta.trailInterval = 2;
        }
        meta.gravityLevel = gravityLevel;
        meta.falloffStart = falloffStart;
        meta.falloffEnd = falloffEnd;
        meta.minPercent = minPct;
        meta.shooter = player;

        // 跳弹属性（概率在每次命中方块时判定，此处记录参数）
        meta.ricochetChance = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_RICOCHET_CHANCE);
        if (meta.ricochetChance > 0) {
            meta.ricochetCount = 1; // 最多跳弹1次
            double ricochetAngle = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_RICOCHET_ANGLE);
            arrow.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey("xh", "ricochet_angle"),
                org.bukkit.persistence.PersistentDataType.DOUBLE, ricochetAngle);
        }
        // 水中弹速
        meta.waterSpeedMult = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_WATER_SPEED) / 100.0;
        if (meta.waterSpeedMult <= 0) meta.waterSpeedMult = 1.0;
        // 玻璃穿透
        meta.glassPierce = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_GLASS_PIERCE) >= 1.0;

        activeBullets.put(arrow.getUniqueId(), meta);

        // 弹道 tick
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isDead()) {
                    activeBullets.remove(arrow.getUniqueId());
                    cancel();
                    return;
                }

                BulletMeta m = activeBullets.get(arrow.getUniqueId());
                if (m == null) {
                    cancel();
                    return;
                }

                m.lifetime--;
                if (m.lifetime <= 0) {
                    arrow.remove();
                    activeBullets.remove(arrow.getUniqueId());
                    cancel();
                    return;
                }

                // 空气阻力
                if (m.drag > 0) {
                    m.velocity.multiply(1.0 - m.drag);
                    arrow.setVelocity(m.velocity);
                }

                // 水中弹速修正
                boolean nowInWater = arrow.getLocation().getBlock().isLiquid();
                if (nowInWater && m.waterSpeedMult != 1.0) {
                    if (!m.wasInWater) {
                        m.velocity.multiply(m.waterSpeedMult);
                        arrow.setVelocity(m.velocity);
                    }
                }
                m.wasInWater = nowInWater;

                // 弱重力（gravityLevel == 1）
                if (m.gravityLevel == 1) {
                    m.velocity.setY(m.velocity.getY() - 0.02);
                    arrow.setVelocity(m.velocity);
                }

                // 粒子尾迹
                m.trailCounter++;
                if (m.trailInterval > 0 && m.trailCounter >= m.trailInterval) {
                    m.trailCounter = 0;
                    renderTrailParticle(arrow, m);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return arrow;
    }

    /** 渲染尾迹粒子 */
    private static void renderTrailParticle(Arrow arrow, BulletMeta m) {
        if (m.ammoTrailType == null) return;
        Particle p;
        int count;
        switch (m.ammoTrailType) {
            case "flame":   p = Particle.FLAME; count = 2; break;
            case "crit":    p = Particle.CRIT; count = 1; break;
            case "end_rod": p = Particle.END_ROD; count = 1; break;
            default:        p = Particle.SMOKE; count = 1; break; // "smoke"
        }
        arrow.getWorld().spawnParticle(p, arrow.getLocation(), count, 0, 0, 0, 0);
    }

    /** 计算距离衰减后的伤害系数 */
    public static double calcDistanceFalloff(Player shooter, Arrow arrow, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(shooter, "ballistics")) return 1.0;

        double distance = shooter.getLocation().distance(arrow.getLocation());
        double falloffStart = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_DAMAGE_FALLOFF_START);
        double falloffEnd = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_DAMAGE_FALLOFF_END);
        double minPct = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_DAMAGE_MIN_PERCENT) / 100.0;

        if (distance <= falloffStart) return 1.0;
        if (distance >= falloffEnd) return minPct;

        double t = (distance - falloffStart) / (falloffEnd - falloffStart);
        return 1.0 - t * (1.0 - minPct);
    }

    /** 获取子弹元数据 */
    public static BulletMeta getBulletMeta(UUID arrowUuid) {
        return activeBullets.get(arrowUuid);
    }

    /** 注册子弹元数据（跳弹时由新 Arrow 继承使用） */
    public static void registerBullet(UUID arrowUuid, BulletMeta meta) {
        activeBullets.put(arrowUuid, meta);
    }

    /** 移除子弹元数据 */
    public static void removeBullet(UUID arrowUuid) {
        activeBullets.remove(arrowUuid);
    }
}
