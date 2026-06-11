package io.github.liyughj.xH.gun;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 枪械定时任务 —— 每 5 tick 恢复扩散、后坐力、冷却 + 特效维护。
 */
public final class GunTickTask {

    private GunTickTask() {}

    /** 启动定时任务，每 5 tick 执行一次 */
    public static void start(JavaPlugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                SpreadManager.tickRecovery();
                RecoilManager.tickRecovery();

                // 所有在线玩家的持枪维护
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ItemStack weapon = player.getInventory().getItemInMainHand();

                    // 过热冷却（无论持枪与否都降温，持枪时用枪的属性，否则默认值）
                    OverheatManager.doCool(player, GunListener.isGunStatic(weapon) ? weapon : null);

                    if (!GunListener.isGunStatic(weapon)) continue;

                    // 过热冒烟粒子
                    if (GunSystemConfig.isSystemEnabled(player, "overheat")) {
                        double smokeThreshold = io.github.liyughj.xH.rpg.Attribute.AttributeStorage
                            .getAttrValue(weapon, io.github.liyughj.xH.rpg.Attribute.RpgAttribute.GUN_HEAT_SMOKE_THRESHOLD);
                        double heatPct = OverheatManager.getHeatPercent(player);
                        if (smokeThreshold > 0 && heatPct >= smokeThreshold) {
                            player.getWorld().spawnParticle(
                                Particle.SMOKE,
                                player.getEyeLocation().add(player.getLocation().getDirection().multiply(1.2)),
                                1, 0.02, 0.02, 0.02, 0);
                        }
                    }

                    // 燃料/能量恢复（喷火器/激光未开火时）
                    String wt = GunSystemConfig.gun().getWeaponType(weapon.getType());
                    if ("flamethrower".equals(wt) && !SpecialWeapons.isFlameActive(player)) {
                        SpecialWeapons.regenerateFlameFuel(player);
                    }
                    if ("laser".equals(wt) && !SpecialWeapons.isLaserActive(player)) {
                        SpecialWeapons.regenerateLaserEnergy(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }
}
