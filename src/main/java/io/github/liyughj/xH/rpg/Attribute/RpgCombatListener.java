package io.github.liyughj.xH.rpg.Attribute;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

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

    /* ==================== 伤害事件：RPG 计算入口 ==================== */

    /**
     * LOW 优先级：在附魔剥离之后、攻速 CD 之前，进行 RPG 伤害计算。
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        DamageCause cause = event.getCause();

        if (cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK) {
            handleMelee(event, target);
        } else if (cause == DamageCause.PROJECTILE) {
            handleProjectile(event, target);
        }
    }

    /* ==================== 近战处理 ==================== */

    private void handleMelee(EntityDamageByEntityEvent event, LivingEntity target) {
        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
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

        /* 如果主手武器类型与发射时不同，回退到发射时的武器类型查找 */
        if (shootWeaponType != null && weapon.getType().name().equals(shootWeaponType)) {
            // 主手武器未切换，正常使用
        } else {
            // 武器已切换，跳过 RPG 计算
            return;
        }

        double baseDamage = event.getDamage();

        AttributeCalculator.DamageResult result = AttributeCalculator.calcFinalDamage(
            player, weapon, target,
            AttributeCalculator.DamageType.PROJECTILE,
            baseDamage
        );

        /* 弹射物不应用穿透（穿透仅近战生效） */
        event.setDamage(result.damage);
        applyHeal(player, result.heal);

        /* 破甲 */
        applyArmorBreak(player, weapon, target, event);
    }

    /* ==================== 破甲处理 ==================== */

    /**
     * 读取攻击方破甲属性，对目标施加破甲标记。
     * 同时应用目标已有破甲标记的伤害增幅。
     */
    private void applyArmorBreak(Player attacker, ItemStack weapon, LivingEntity target,
                                 EntityDamageByEntityEvent event) {
        /* 先应用目标已有的破甲效果（被破甲的目标受更多伤害） */
        double multiplier = ArmorBreakManager.getDamageMultiplier(target);
        if (multiplier > 1.0) {
            event.setDamage(event.getDamage() * multiplier);
        }

        if (weapon == null || !weapon.hasItemMeta()) return;

        /* 读取攻击方破甲属性 */
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

        if (chance <= 0 || ticks <= 0) return;

        ArmorBreakManager.Debuff before = ArmorBreakManager.getDebuff(target);

        ArmorBreakManager.Debuff after = ArmorBreakManager.apply(
            attacker, target, chance, shallow, medium, deep, ticks
        );

        /* 仅当破甲状态发生变化时发送提示 */
        if (after == null) return;

        boolean changed;
        if (before == null) {
            changed = true; // 新施加
        } else {
            changed = before.level != after.level
                      || before.totalPct != after.totalPct
                      || !before.breakerUuid.equals(after.breakerUuid);
        }

        if (changed) {
            String targetName = target instanceof Player tp ? tp.getName() : target.getType().name();
            String degree = after.level.getDisplay();
            String breakerName = after.breakerName;

            attacker.sendMessage("§c你使 §e" + targetName + " §c破甲 → 当前程度: §e" + degree);
            if (target instanceof Player tp) {
                tp.sendMessage("§c" + breakerName + " §c使你破甲 → 当前程度: §e" + degree);
            }
        }
    }

    /* ==================== 实体死亡清理 ==================== */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        ArmorBreakManager.remove(entity);
    }

    /* ==================== 吸血回血 ==================== */

    private void applyHeal(Player player, double heal) {
        if (heal <= 0) return;
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        double newHealth = Math.min(maxHealth, player.getHealth() + heal);
        player.setHealth(newHealth);
    }
}
