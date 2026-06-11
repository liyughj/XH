package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeRange;
import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * 枪械监听器 —— 射击 + 模式切换（F 键）。
 */
public class GunListener implements Listener {

    private final JavaPlugin plugin;

    public static final String PDC_GUN_SHOOTER = "gun_shooter_uuid";
    public static final String PDC_GUN_WEAPON = "gun_weapon_type";
    public static final String PDC_GUN_IS_GUN = "gun_is_gun";

    public GunListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /* ==================== F 键切换模式 ==================== */

    @EventHandler(priority = EventPriority.NORMAL)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!isGun(weapon)) return;

        event.setCancelled(true); // 阻止原版副手交换

        // 退出开镜
        AdsManager.forceExit(player);

        String modeName = FireModeManager.cycleMode(player, weapon, plugin);
        if (modeName != null) {
            player.sendActionBar(Component.text("射击模式: " + modeName, NamedTextColor.AQUA));
        }
    }

    /* ==================== 切槽 → 停止全自动 + 退出开镜 + 清理换弹/卡壳 ==================== */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSlotChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        FireModeManager.stopAuto(player);
        AdsManager.forceExit(player);
        MagazineManager.cancelReload(player);
        MalfunctionManager.clearJam(player);
    }

    /* ==================== 丢弃物品 (Q键) → 换弹 ==================== */

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!isGun(weapon)) return;

        // 检查弹夹系统是否启用
        if (!GunSystemConfig.isSystemEnabled(player, "magazine")) return;

        event.setCancelled(true); // 阻止丢弃

        // 退出全自动
        FireModeManager.stopAuto(player);

        // 开始换弹
        MagazineManager.startReload(player, weapon);
    }

    /* ==================== 射击 + 开镜 ==================== */

    @EventHandler(priority = EventPriority.NORMAL)
    public void onGunShoot(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        /* 只处理主手 */
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!isGun(weapon)) return;

        Action action = event.getAction();

        /* 阻止原版交互 */
        event.setCancelled(true);

        /* 只响应点击 */
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
            && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        /* —— 左键：卡壳排除优先，否则开镜 Toggle —— */
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            // 卡壳优先：左键排除
            if (MalfunctionManager.isJamActive(player)) {
                MalfunctionManager.clearJam(player);
                double clearTicks = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_MALFUNC_JAM_CLEAR_TICKS);
                if (clearTicks > 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.4f, 1.8f);
                    player.sendActionBar(Component.text("排除中...", NamedTextColor.GREEN));
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            player.sendActionBar(Component.text("卡壳已排除", NamedTextColor.GREEN));
                        }
                    }.runTaskLater(plugin, (long) clearTicks);
                }
                return;
            }

            // 手动拉栓（栓动狙击：auto_bolt=0，膛内无弹，弹夹有弹）
            if (ChamberManager.isEnabled(weapon) && !ChamberManager.isAutoBolt(weapon)
                && !ChamberManager.isChamberLoaded(weapon)) {
                int magAmmo = MagazineManager.getAmmo(weapon);
                if (magAmmo > 0) {
                    int boltTicks = ChamberManager.getBoltTime(weapon);
                    player.playSound(player.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.5f, 1.0f);
                    player.sendActionBar(Component.text("拉栓中...", NamedTextColor.YELLOW));
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            int magNow = MagazineManager.getAmmo(weapon);
                            if (magNow > 0) {
                                MagazineManager.setAmmo(weapon, magNow - 1);
                                ChamberManager.setChamberLoaded(weapon, true);
                                player.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.5f, 1.2f);
                                player.sendActionBar(Component.text("装填完成", NamedTextColor.GREEN));
                            }
                        }
                    }.runTaskLater(plugin, boltTicks);
                }
                return;
            }

            // 先停止全自动（开镜时自动射击也停）
            FireModeManager.stopAuto(player);
            boolean nowActive = AdsManager.toggle(player, weapon);
            if (nowActive) {
                player.sendActionBar(Component.text(
                    "开镜 " + String.format("%.1f", AdsManager.getMagnification(player)) + "x",
                    NamedTextColor.GREEN));
            } else {
                player.sendActionBar(Component.empty());
            }
            return;
        }

        /* —— 右键：射击 —— */

        /* 全自动模式 → Toggle 开/关 */
        if (FireModeManager.isAutoActive(player) || FireModeManager.getMode(player) == 3) {
            if (FireModeManager.isAutoActive(player)) {
                // 停止特殊武器
                String wt = GunSystemConfig.gun().getWeaponType(weapon.getType());
                if ("flamethrower".equals(wt)) SpecialWeapons.stopFlame(player);
                if ("laser".equals(wt)) SpecialWeapons.stopLaser(player);
                FireModeManager.stopAuto(player);
                return;
            }

            // 喷火器/激光有专用 toggle
            String weaponType = GunSystemConfig.gun().getWeaponType(weapon.getType());
            if ("flamethrower".equals(weaponType)) {
                if (SpecialWeapons.startFlame(plugin, player, weapon)) {
                    FireModeManager.toggleAuto(player, weapon, plugin,
                        () -> {}); // 喷火器不触发doShoot，由定时器自己处理
                }
                return;
            }
            if ("laser".equals(weaponType)) {
                boolean continuous = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_CONTINUOUS) >= 1.0;
                if (continuous) {
                    if (SpecialWeapons.startLaserContinuous(plugin, player, weapon)) {
                        FireModeManager.toggleAuto(player, weapon, plugin, () -> {});
                    }
                } else {
                    SpecialWeapons.shootLaser(plugin, player, weapon);
                }
                return;
            }

            boolean activated = FireModeManager.toggleAuto(player, weapon, plugin,
                () -> doShoot(player, weapon));
            if (activated) {
                player.sendActionBar(Component.text("全自动 开启", NamedTextColor.RED));
            }
            return;
        }

        // 单发 / 连发
        FireModeManager.fire(player, weapon, plugin,
            () -> doShoot(player, weapon));
    }

    /* ==================== 实际发射 ==================== */

    private void doShoot(Player player, ItemStack weapon) {

        /* ── 武器类型分发 ── */
        String weaponType = GunSystemConfig.gun().getWeaponType(weapon.getType());
        if (weaponType == null) weaponType = "normal";

        boolean needsOverheatCheck = !weaponType.equals("flamethrower") && !weaponType.equals("laser")
            && !weaponType.equals("crossbow");
        boolean needsStandardCheck = !weaponType.equals("flamethrower") && !weaponType.equals("laser");

        /* ── 1. [弹夹/枪膛] 弹量检查（最先） ── */
        if (needsStandardCheck && GunSystemConfig.isSystemEnabled(player, "magazine")) {
            if (ChamberManager.isEnabled(weapon)) {
                // 枪膛启用：检查弹夹+枪膛是否都为0
                if (MagazineManager.isEmpty(weapon) && !ChamberManager.hasChamberRound(player, weapon)) {
                    playDryFire(player, weapon);
                    return;
                }
            } else {
                if (MagazineManager.isEmpty(weapon)) {
                    playDryFire(player, weapon);
                    // 自动换弹（全局开关 + 枪械属性）
                    if (GunSystemConfig.isAutoReloadEnabled()
                        && AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_AUTO_RELOAD) >= 1.0) {
                        MagazineManager.startReload(player, weapon);
                    }
                    return;
                }
            }
        }

        /* ── 2. [过热] 热量检查 ── */
        if (needsOverheatCheck && OverheatManager.addHeat(player, weapon)) return;

        /* ── 3. [故障] 故障检查 ── */
        if (needsStandardCheck) {
            MalfunctionManager.MalfuncType malfunc = MalfunctionManager.checkAndTrigger(player, weapon);
            if (malfunc == MalfunctionManager.MalfuncType.JAM) return;
            if (malfunc == MalfunctionManager.MalfuncType.MISFIRE) {
                return; // 哑火：不消耗弹夹
            }
            // 炸膛：正常发射，后续处理在 checkAndTrigger 内完成
        }

        /* ── 11/12. [弹夹/枪膛] 消耗 ── */
        if (needsStandardCheck && GunSystemConfig.isSystemEnabled(player, "magazine")) {
            if (ChamberManager.isEnabled(weapon)) {
                ChamberManager.consumeChamber(player, weapon);
            } else {
                MagazineManager.consumeAmmo(player, weapon);
            }
        }

        /* ── 10. [耐久] ── */
        if (needsStandardCheck) {
            if (DurabilityManager.shootLoss(player, weapon)) return;
        }

        /* ── 按武器类型发射 ── */
        switch (weaponType) {
            case "shotgun":
                SpecialWeapons.shootShotgun(plugin, player, weapon);
                return;
            case "crossbow":
                SpecialWeapons.shootCrossbow(plugin, player, weapon);
                return;
            case "grenade_launcher":
                SpecialWeapons.shootGrenade(plugin, player, weapon);
                return;
            case "rocket_launcher":
                SpecialWeapons.shootRocket(plugin, player, weapon);
                return;
            default:
                doShootNormal(player, weapon);
        }
    }

    /** 空仓击发音效 */
    private void playDryFire(Player player, ItemStack weapon) {
        // 播放空仓音效（默认 click）
        player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.3f, 1.0f);
    }

    /** 普通枪械发射（原有逻辑） */
    private void doShootNormal(Player player, ItemStack weapon) {

        /* ── Hitscan 分支 ── */
        if (AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_HITSCAN) >= 1.0) {
            doHitscan(player, weapon);
            return;
        }

        Vector baseDir = player.getEyeLocation().getDirection();
        Vector spreadDir = SpreadCalculator.applySpread(player, weapon, baseDir);

        Arrow arrow = BallisticsManager.launchBullet(player, weapon, spreadDir);

        /* PDC 标记 */
        arrow.getPersistentDataContainer().set(
            new NamespacedKey(plugin, PDC_GUN_SHOOTER),
            PersistentDataType.STRING,
            player.getUniqueId().toString()
        );
        arrow.getPersistentDataContainer().set(
            new NamespacedKey(plugin, PDC_GUN_WEAPON),
            PersistentDataType.STRING,
            weapon.getType().name()
        );
        arrow.getPersistentDataContainer().set(
            new NamespacedKey(plugin, PDC_GUN_IS_GUN),
            PersistentDataType.BYTE,
            (byte) 1
        );
        // 当前发射弹种（命中时 RpgCombatListener 读取）
        String shotAmmo = MagazineManager.peekNextAmmoType(weapon);
        if (shotAmmo != null) {
            arrow.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "gun_shot_ammo"),
                PersistentDataType.STRING,
                shotAmmo
            );
        }

        /* 后坐力 */
        RecoilManager.applyRecoil(player, weapon);

        /* 枪声 */
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.3f, 2.0f);
    }

    /** Hitscan 即时命中（无弹丸飞行时间） */
    private void doHitscan(Player player, ItemStack weapon) {
        double range = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_RANGE);
        if (range <= 0) range = 60;

        Vector dir = SpreadCalculator.applySpread(player, weapon,
            player.getEyeLocation().getDirection());
        Location eye = player.getEyeLocation();
        World world = player.getWorld();

        var result = world.rayTraceEntities(eye, dir, range,
            entity -> entity instanceof LivingEntity && entity != player && ((LivingEntity) entity).isValid());
        Location hitPoint = eye.clone().add(dir.clone().multiply(range));

        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            hitPoint = result.getHitPosition().toLocation(world);
            // 通过 AttributeCalculator 计算伤害再 apply
            double baseDamage = 1.0;
            var dmgResult = io.github.liyughj.xH.rpg.Attribute.AttributeCalculator.calcFinalDamage(
                player, weapon, target,
                io.github.liyughj.xH.rpg.Attribute.AttributeCalculator.DamageType.GUN,
                baseDamage);
            double finalDmg = dmgResult.damage;
            io.github.liyughj.xH.gun.AmmoConfig.AmmoTypeDef ammo = null;
            if (GunSystemConfig.ammo() != null && GunSystemConfig.gun() != null) {
                String da = GunSystemConfig.gun().getDefaultAmmo(weapon.getType());
                if (da != null) ammo = GunSystemConfig.ammo().getAmmoType(da);
            }
            if (ammo != null) finalDmg *= ammo.damageMult;
            target.damage(finalDmg, player);
        }

        // 粒子线
        SpecialWeapons.drawLaserLine(eye, hitPoint,
            (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_COLOR),
            AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_THICKNESS));

        RecoilManager.applyRecoil(player, weapon);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.3f, 2.0f);
    }

    /* ==================== 工具 ==================== */

    public static boolean isGunStatic(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        AttributeRange gunDmg = AttributeStorage.getItemAttrRange(item, RpgAttribute.GUN_DAMAGE);
        return !(gunDmg.getMin() == RpgAttribute.GUN_DAMAGE.getDefaultValue()
            && gunDmg.getMax() == RpgAttribute.GUN_DAMAGE.getDefaultValue());
    }

    private boolean isGun(ItemStack item) {
        return isGunStatic(item);
    }

    public static boolean isGunProjectile(JavaPlugin plugin, org.bukkit.entity.Projectile proj) {
        return proj.getPersistentDataContainer().has(
            new NamespacedKey(plugin, PDC_GUN_IS_GUN),
            PersistentDataType.BYTE
        );
    }
}
