package io.github.liyughj.xH.rpg.Attribute;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPG 属性实时监听器（攻击速度 A+C 方案）。
 * <p>
 * <b>A — 视觉同步：</b>
 * <ol>
 *   <li>监听切槽/换手，立即从新主手物品读取 attack_speed</li>
 *   <li>写入 Player ATTACK_SPEED 原版属性 → 攻击条实时变化</li>
 *   <li>切武器时只用新武器的值，不窜武器</li>
 *   <li>左右手都有武器 → 以主手（Event 中的 newSlot）为准</li>
 * </ol>
 * <p>
 * <b>C — 攻速 CD 保底：</b>
 * <ol>
 *   <li>记录每次攻击时间，计算理想 CD</li>
 *   <li>CD 未到 → 伤害 × (实际间隔 / 理想CD)，最低 20%</li>
 * </ol>
 */
public class AttributeListener implements Listener {

    /** 上次近战攻击时间戳（System.nanoTime()） */
    private final Map<UUID, Long> lastAttackNano = new ConcurrentHashMap<>();

    /** 上次攻击 CD 秒数（避免重复读物品） */
    private final Map<UUID, Double> lastCooldown = new ConcurrentHashMap<>();

    public AttributeListener() {}

    /* ==================== A: 视觉同步 ==================== */

    /**
     * 玩家切换物品栏槽位 → 立即同步新武器攻速。
     * 退出时也调用（newSlot = 当前槽位）。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSlotChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack weapon = player.getInventory().getItem(event.getNewSlot());
        applyAttackSpeed(player, weapon);
    }

    /**
     * 副手换手 → 主手不变，但为安全起见刷新一次（以主手为准）。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack weapon = player.getInventory().getItemInMainHand();
        applyAttackSpeed(player, weapon);
    }

    /**
     * 玩家退出清理。
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastAttackNano.remove(uuid);
        lastCooldown.remove(uuid);
    }

    /**
     * 将物品的 attack_speed 写入玩家原版 ATTACK_SPEED 属性。
     * weapon=null/无属性 → 重置为默认 4.0。
     */
    private void applyAttackSpeed(Player player, ItemStack weapon) {
        AttributeInstance attr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attr == null) return;

        double currentBase = attr.getBaseValue();

        double attackSpeedPct = 0.0;
        if (weapon != null && weapon.hasItemMeta()) {
            AttributeRange range = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.ATTACK_SPEED);
            /* 仅当物品上有该属性时才生效，否则回退默认 */
            if (range.getMin() != RpgAttribute.ATTACK_SPEED.getDefaultValue()
                || range.getMax() != RpgAttribute.ATTACK_SPEED.getDefaultValue()) {
                attackSpeedPct = range.roll();
            }
        }

        double newBase = 4.0 * (1.0 + attackSpeedPct / 100.0);
        newBase = Math.max(0.1, Math.min(1024.0, newBase));

        if (Math.abs(currentBase - newBase) > 0.001) {
            attr.setBaseValue(newBase);
        }
    }

    /* ==================== C: 攻速 CD 保底 ==================== */

    /**
     * NORMAL 优先级：检测攻击间隔，未到 CD 则削减伤害。
     * 在 RPG 伤害计算（LOW）之后、附魔效果（HIGH）之前，只影响近战。
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAttackCooldownCheck(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK
            && event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }

        ItemStack weapon = player.getInventory().getItemInMainHand();
        double attackSpeedPct = AttributeCalculator.calcAttackSpeedPercent(player, weapon);
        double idealCooldown = AttributeCalculator.getCooldownSeconds(attackSpeedPct);

        long now = System.nanoTime();
        UUID uuid = player.getUniqueId();

        Long lastNano = lastAttackNano.get(uuid);
        if (lastNano != null) {
            double elapsed = (now - lastNano) / 1_000_000_000.0;
            if (elapsed < idealCooldown) {
                /* CD 未到 → 伤害按比例削减，最低保留 20% */
                double ratio = Math.max(0.2, elapsed / idealCooldown);
                event.setDamage(event.getDamage() * ratio);
            }
        }

        lastAttackNano.put(uuid, now);
        lastCooldown.put(uuid, idealCooldown);
    }
}
