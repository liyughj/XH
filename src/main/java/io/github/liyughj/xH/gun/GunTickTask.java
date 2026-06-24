package io.github.liyughj.xH.gun;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 枪械定时任务 —— 每 5 tick 恢复扩散、后坐力、冷却、呼吸值、热成像、压制 + 特效维护。
 */
public final class GunTickTask {

    private GunTickTask() {}

    public static void start(JavaPlugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                SpreadManager.tickRecovery();
                RecoilManager.tickRecovery();
                SuppressionManager.tickAll();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    AdsManager.tick(player);
                    AdsManager.thermalGlowTick(player);

                    ItemStack weapon = player.getInventory().getItemInMainHand();

                    io.github.liyughj.xH.specialEvent.HeatSystem.doCool(player, GunListener.isGunStatic(weapon) ? weapon : null);

                    if (!GunListener.isGunStatic(weapon)) continue;

                    // 疾跑禁用强制执行（兜底：登录时已在持枪不触发 onSlotChange）
                    if (MobilityManager.isSprintBlocked(player) && player.isSprinting()) {
                        player.setSprinting(false);
                    }

                    if (GunSystemConfig.isSystemEnabled(player, "overheat")) {
                        double smokeThreshold = io.github.liyughj.xH.rpg.Attribute.AttributeStorage
                            .getAttrValue(weapon, io.github.liyughj.xH.rpg.Attribute.RpgAttribute.GUN_HEAT_SMOKE_THRESHOLD);
                        double heatPct = io.github.liyughj.xH.specialEvent.HeatSystem.getHeatPercent(player, weapon);
                        if (smokeThreshold > 0 && heatPct * 100 >= smokeThreshold) {
                            player.getWorld().spawnParticle(
                                Particle.SMOKE,
                                player.getEyeLocation().add(player.getLocation().getDirection().multiply(1.2)),
                                1, 0.02, 0.02, 0.02, 0);
                        }
                    }

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
