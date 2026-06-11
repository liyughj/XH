package io.github.liyughj.xH.gun;

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

        // 重力
        boolean hasGravity = gravityLevel >= 2;
        arrow.setGravity(hasGravity);
        if (gravityLevel == 1) {
            // 弱重力通过自定义 tick 模拟
        }

        BulletMeta meta = new BulletMeta();
        meta.velocity = vel;
        meta.drag = Math.max(0, Math.min(drag, 0.5)); // 限制最大阻力
        meta.lifetime = lifetime;
        meta.trailInterval = trailInterval;
        meta.trailCounter = 0;
        // 弹药尾迹类型
        if (ammo != null && ammo.effects != null) {
            meta.ammoTrailType = ammo.effects.getString("trail", null);
            if (meta.ammoTrailType != null && meta.trailInterval <= 0) {
                meta.trailInterval = 2;
            }
        }
        meta.gravityLevel = gravityLevel;
        meta.falloffStart = falloffStart;
        meta.falloffEnd = falloffEnd;
        meta.minPercent = minPct;
        meta.shooter = player;
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

                // 弱重力（gravityLevel == 1）
                if (m.gravityLevel == 1) {
                    m.velocity.setY(m.velocity.getY() - 0.02);
                    arrow.setVelocity(m.velocity);
                }

                // 粒子尾迹
                m.trailCounter++;
                if (m.trailInterval > 0 && m.trailCounter >= m.trailInterval) {
                    m.trailCounter = 0;
                    Particle trailParticle = "flame".equals(m.ammoTrailType) ? Particle.FLAME : Particle.SMOKE;
                    int trailCount = "flame".equals(m.ammoTrailType) ? 2 : 1;
                    arrow.getWorld().spawnParticle(
                        trailParticle,
                        arrow.getLocation(),
                        trailCount, 0, 0, 0, 0
                    );
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return arrow;
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

    /** 移除子弹元数据 */
    public static void removeBullet(UUID arrowUuid) {
        activeBullets.remove(arrowUuid);
    }
}
