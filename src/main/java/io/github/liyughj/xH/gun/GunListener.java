package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeRange;
import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import io.github.liyughj.xH.lore.LoreManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
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
import org.bukkit.event.player.PlayerToggleSprintEvent;
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
        MagazineManager.cancelReloadIfInterruptible(player);
        MalfunctionManager.clearJam(player);

        // 人体工学：收旧枪 + 切新枪延迟
        ItemStack oldWeapon = player.getInventory().getItem(event.getPreviousSlot());
        ItemStack newWeapon = player.getInventory().getItem(event.getNewSlot());

        // 切枪拦截：上一把枪的收枪冷却未结束
        if (isGun(newWeapon) && EquipManager.isHolsterBlocked(player)) {
            event.setCancelled(true);
            return;
        }

        if (isGun(oldWeapon)) {
            EquipManager.markHolster(player, oldWeapon);
            MobilityManager.restoreWeaponSpeed(player);
        }
        if (isGun(newWeapon)) {
            EquipManager.markEquip(player, newWeapon);
            MobilityManager.applyWeaponSpeed(player, newWeapon);
        } else {
            MobilityManager.restoreWeaponSpeed(player);
        }
    }

    /** 疾跑切换 → 记录延迟 + 移速修正 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSprintToggle(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        if (event.isSprinting()) {
            EquipManager.markSprintStart(player);
        } else {
            EquipManager.markSprintEnd(player);
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (isGun(weapon) && MobilityManager.isSprintBlocked(player)) {
            event.setCancelled(true);
            return;
        }
        // 刷新疾跑移速
        MobilityManager.refreshSprintSpeed(player);
    }

    /** 跳跃 → 修正跳跃高度 */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJump(org.bukkit.event.player.PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) return;
        Player player = event.getPlayer();
        double deltaY = event.getTo().getY() - event.getFrom().getY();
        if (deltaY <= 0.3) return; // 不是跳跃（正常移动/摔落）
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!isGun(weapon)) return;

        double mult = MobilityManager.getJumpMultiplier(player);
        if (mult == 1.0) return;

        // 跳跃高度修正：修正 Y 方向速度
        var vel = player.getVelocity();
        vel.setY(vel.getY() * mult);
        player.setVelocity(vel);
    }

    /* ==================== 丢弃物品 (Q键) → 换弹 ==================== */

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!isGun(weapon)) return;

        // 持枪时按Q始终阻止丢弃，改为触发换弹
        event.setCancelled(true);

        // 检查弹夹系统是否启用
        if (!GunSystemConfig.isSystemEnabled(player, "magazine")) return;

        // 开始换弹（内部会停全自动+退开镜）
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
                if (!MalfunctionManager.startJamClear(player)) return; // 已在排障中
                double clearTicks = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_MALFUNC_JAM_CLEAR_TICKS);
                player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.4f, 1.8f);
                player.sendActionBar(Component.text("排除中...", NamedTextColor.GREEN));
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        MalfunctionManager.finishJamClear(player);
                        player.sendActionBar(Component.text("卡壳已排除", NamedTextColor.GREEN));
                    }
                }.runTaskLater(plugin, Math.max(1, (long) clearTicks));
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
                            if (!player.isOnline()) return;
                            // 重新读取主手，避免闭包持有过期引用
                            ItemStack held = player.getInventory().getItemInMainHand();
                            if (!isGun(held)) return;
                            int magNow = MagazineManager.getAmmo(held);
                            if (magNow > 0) {
                                String boltAmmoType = MagazineManager.peekNextAmmoType(held);
                                MagazineManager.setAmmo(held, magNow - 1);
                                MagazineManager.popTopFromStack(held);
                                ChamberManager.setChamberLoaded(held, true);
                                ChamberManager.setChamberAmmoType(held, boltAmmoType);
                                LoreManager.refreshGunLore(held);
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
                        () -> true); // 喷火器由定时器自己处理
                }
                return;
            }
            if ("laser".equals(weaponType)) {
                boolean continuous = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_CONTINUOUS) >= 1.0;
                if (continuous) {
                    if (SpecialWeapons.startLaserContinuous(plugin, player, weapon)) {
                        FireModeManager.toggleAuto(player, weapon, plugin, () -> true);
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

    private boolean doShoot(Player player, ItemStack weapon) {

        /* ── 武器类型分发 ── */
        String weaponType = GunSystemConfig.gun().getWeaponType(weapon.getType());
        if (weaponType == null) weaponType = "normal";

        boolean needsOverheatCheck = !weaponType.equals("flamethrower") && !weaponType.equals("laser")
            && !weaponType.equals("crossbow");
        boolean needsStandardCheck = !weaponType.equals("flamethrower") && !weaponType.equals("laser");

        /* ── 切枪冷却检查 ── */
        if (EquipManager.isEquipBlocked(player)) return false;

        /* ── 疾跑→开火延迟 ── */
        int sprintDelay = EquipManager.getSprintToFireDelay(player, weapon);
        if (sprintDelay > 0) {
            player.sendActionBar(Component.text("疾跑惯性... " + sprintDelay + "tick", NamedTextColor.GRAY));
            return false;
        }

        /* ── 0. [弩冷却] 射击后装填冷却检查 ── */
        if ("crossbow".equals(weaponType) && SpecialWeapons.isCrossbowOnCooldown(player, weapon)) {
            player.sendActionBar(Component.text("装填中...", NamedTextColor.YELLOW));
            return false;
        }

        /* ── 1. [弹夹/枪膛] 弹量检查（最先） ── */
        if (needsStandardCheck && GunSystemConfig.isSystemEnabled(player, "magazine")) {
            if (ChamberManager.isEnabled(weapon)) {
                if (!ChamberManager.isAutoBolt(weapon)) {
                    // 手动拉栓：必须膛内有弹才能右键射击
                    if (!ChamberManager.isChamberLoaded(weapon)) {
                        player.sendActionBar(Component.text("需要拉栓", NamedTextColor.YELLOW));
                        return false;
                    }
                } else {
                    // 自动拉栓：膛内有弹或弹夹有弹即可
                    if (MagazineManager.isEmpty(weapon) && !ChamberManager.hasChamberRound(player, weapon)) {
                        playDryFire(player, weapon);
                        return false;
                    }
                }
            } else {
                if (MagazineManager.isEmpty(weapon)) {
                    playDryFire(player, weapon);
                    if (GunSystemConfig.isAutoReloadEnabled()
                        && AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_AUTO_RELOAD) >= 1.0) {
                        MagazineManager.startReload(player, weapon);
                    }
                    return false;
                }
            }
        }

        /* ── 2. [过热] 热量检查 ── */
        if (needsOverheatCheck && OverheatManager.addHeat(player, weapon)) return false;

        /* ── 3. [故障] 故障检查 ── */
        if (needsStandardCheck) {
            MalfunctionManager.MalfuncType malfunc = MalfunctionManager.checkAndTrigger(player, weapon);
            if (malfunc == MalfunctionManager.MalfuncType.JAM) return false;
            if (malfunc == MalfunctionManager.MalfuncType.MISFIRE) return false;
            // 炸膛：正常发射
        }

        /* ── 4. [耐久] 先检查再消耗弹药，避免子弹浪费 ── */
        if (needsStandardCheck) {
            if (DurabilityManager.shootLoss(player, weapon)) return false;
        }

        /* ── 5. [弹夹/枪膛] 消耗 ── */
        if (needsStandardCheck && GunSystemConfig.isSystemEnabled(player, "magazine")) {
            if (ChamberManager.isEnabled(weapon)) {
                ChamberManager.consumeChamber(player, weapon);
            } else {
                MagazineManager.consumeAmmo(player, weapon);
            }
            LoreManager.refreshGunLore(weapon); // 弹药变化后刷新lore显示
        }

        /* ── 压制AOE ── */
        if (needsStandardCheck) SuppressionManager.onShoot(player, weapon);

        /* ── 按武器类型发射 ── */
        switch (weaponType) {
            case "shotgun":
                SpecialWeapons.shootShotgun(plugin, player, weapon);
                playGunSound(player, weapon);
                RecoilManager.applyRecoil(player, weapon);
                return true;
            case "crossbow":
                SpecialWeapons.shootCrossbow(plugin, player, weapon);
                playGunSound(player, weapon);
                RecoilManager.applyRecoil(player, weapon);
                return true;
            case "grenade_launcher":
                SpecialWeapons.shootGrenade(plugin, player, weapon);
                playGunSound(player, weapon);
                RecoilManager.applyRecoil(player, weapon);
                return true;
            case "rocket_launcher":
                SpecialWeapons.shootRocket(plugin, player, weapon);
                playGunSound(player, weapon);
                RecoilManager.applyRecoil(player, weapon);
                return true;
            default:
                doShootNormal(player, weapon);
                return true;
        }
    }

    /** 空仓击发音效 */
    private void playDryFire(Player player, ItemStack weapon) {
        Sound sound = Sound.BLOCK_LEVER_CLICK; // 默认
        String configured = GunSystemConfig.gun().getDryFireSound(weapon.getType());
        if (configured != null) {
            try { sound = org.bukkit.Registry.SOUNDS.get(NamespacedKey.minecraft(configured.toLowerCase())); }
            catch (IllegalArgumentException ignored) { /* 无效Sound名，用默认 */ }
            if (sound == null) sound = Sound.BLOCK_LEVER_CLICK;
        }
        player.playSound(player.getLocation(), sound, 0.3f, 1.0f);
    }

    /** 枪声音效 —— 世界级播放，实现听声辨位和距离衰减 */
    private void playGunSound(Player player, ItemStack weapon) {
        float volume = 2.0f;
        float pitch = 2.0f;
        // 弹药消音
        AmmoConfig.AmmoTypeDef ammo = MagazineManager.getCurrentAmmoType(weapon);
        if (ammo != null && ammo.getEffectBool("silent")) {
            volume *= 0.3f; // 降低70%音量
        }
        // 枪械配置的射击音效
        Sound sound = Sound.ENTITY_GENERIC_EXPLODE;
        String configured = GunSystemConfig.gun().getShootSound(weapon.getType());
        if (configured != null) {
            Sound s = org.bukkit.Registry.SOUNDS.get(NamespacedKey.minecraft(configured.toLowerCase()));
            if (s != null) sound = s;
        }
        // world 级播放：附近玩家都能听到，自带距离衰减
        player.getWorld().playSound(player.getLocation(), sound, volume, pitch);
    }

    /** 普通枪械发射（射线模式） */
    private void doShootNormal(Player player, ItemStack weapon) {

        /* ── Hitscan 分支（兼容旧配置） ── */
        if (AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_BULLET_HITSCAN) >= 1.0) {
            doHitscan(player, weapon);
            return;
        }

        Vector baseDir = player.getEyeLocation().getDirection();
        Vector spreadDir = SpreadCalculator.applySpread(player, weapon, baseDir);

        /* 射线命中（替换 Arrow 实体子弹） */
        RayTraceManager.shootRayNormal(player, weapon, spreadDir);

        /* 后坐力 */
        RecoilManager.applyRecoil(player, weapon);

        /* 枪声 */
        playGunSound(player, weapon);
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
            // 基础伤害计算
            double baseDamage = 1.0;
            var dmgResult = io.github.liyughj.xH.rpg.Attribute.AttributeCalculator.calcFinalDamage(
                player, weapon, target,
                io.github.liyughj.xH.rpg.Attribute.AttributeCalculator.DamageType.GUN,
                baseDamage);
            double finalDmg = dmgResult.damage;
            io.github.liyughj.xH.gun.AmmoConfig.AmmoTypeDef ammo = MagazineManager.getCurrentAmmoType(weapon);
            if (ammo != null) finalDmg *= ammo.damageMult;

            // 部位伤害（与 RpgCombatListener.applyHitzone 逻辑一致）
            finalDmg = applyHitzoneHitscan(player, weapon, target, hitPoint, finalDmg);

            target.damage(finalDmg, player);
        }

        // 粒子线
        SpecialWeapons.drawLaserLine(eye, hitPoint,
            (int) AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_COLOR),
            AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_LASER_THICKNESS));

        RecoilManager.applyRecoil(player, weapon);
        playGunSound(player, weapon);
    }

    /**
     * Hitscan 部位伤害判定（射线的 hitPoint 代替 Projectile.getLocation()）。
     * 与 RpgCombatListener.applyHitzone 使用相同的阈值/概率/倍率数据源。
     */
    private double applyHitzoneHitscan(Player shooter, ItemStack weapon, LivingEntity target,
                                        Location hitPoint, double baseDamage) {
        if (weapon == null || !weapon.hasItemMeta()) return baseDamage;

        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();

        // 读取部位属性（区间随机）
        double headChance  = getWeaponRange(weapon, RpgAttribute.GUN_HEADSHOT_CHANCE);
        double headMult    = getWeaponRange(weapon, RpgAttribute.GUN_HEADSHOT_MULT);
        double upperChance = getWeaponRange(weapon, RpgAttribute.GUN_UPPER_CHANCE);
        double upperMult   = getWeaponRange(weapon, RpgAttribute.GUN_UPPER_MULT);
        double lowerChance = getWeaponRange(weapon, RpgAttribute.GUN_LOWER_CHANCE);
        double lowerMult   = getWeaponRange(weapon, RpgAttribute.GUN_LOWER_MULT);
        double legChance   = getWeaponRange(weapon, RpgAttribute.GUN_LEG_CHANCE);
        double legMult     = getWeaponRange(weapon, RpgAttribute.GUN_LEG_MULT);
        double headThresh  = getWeaponRange(weapon, RpgAttribute.GUN_HEADSHOT_THRESHOLD);
        double bodyThresh  = getWeaponRange(weapon, RpgAttribute.GUN_BODY_THRESHOLD);
        double legThresh   = getWeaponRange(weapon, RpgAttribute.GUN_LEG_THRESHOLD);

        // 命中位置 vs 目标眼部高度
        double feetY  = target.getLocation().getY();
        double eyeH   = target.getEyeHeight();
        double hitY   = hitPoint.getY();
        double ratio  = (hitY - feetY) / Math.max(0.01, eyeH);

        double chance;
        double mult;

        if (ratio >= headThresh / 100.0) {
            chance = headChance; mult = headMult / 100.0;
        } else if (ratio >= bodyThresh / 100.0) {
            chance = upperChance; mult = upperMult / 100.0;
        } else if (ratio >= legThresh / 100.0) {
            chance = lowerChance; mult = lowerMult / 100.0;
        } else {
            chance = legChance; mult = legMult / 100.0;
        }

        if (chance <= 0) return baseDamage;
        if (chance >= 100) return baseDamage * mult;

        return rng.nextDouble() * 100 < chance ? baseDamage * mult : baseDamage;
    }

    /** 从武器 PDC 读取属性的区间随机值 */
    private static double getWeaponRange(ItemStack weapon, RpgAttribute attr) {
        io.github.liyughj.xH.rpg.Attribute.AttributeRange range =
            io.github.liyughj.xH.rpg.Attribute.AttributeStorage.getItemAttrRange(weapon, attr);
        return !range.isSingleValue()
            ? range.getMin() + Math.random() * (range.getMax() - range.getMin())
            : range.getMin();
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
