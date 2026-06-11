package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 耐久度系统管理器。
 * 枪械耐久存储在 PDC 中（gun_dura），射击时消耗。
 * 耐久越低散布/后坐/故障率越大。耐久归零枪械破损。
 */
public class DurabilityManager {

    private static final NamespacedKey KEY_DURA = new NamespacedKey("xh", "gun_dura");

    /** 获取当前耐久 */
    public static double getDurability(ItemStack weapon) {
        Double val = getPDC(weapon, KEY_DURA);
        if (val != null) return val;
        // 初始化为最大值
        double max = getMaxDurability(weapon);
        setDurability(weapon, max);
        return max;
    }

    /** 设置当前耐久 */
    public static void setDurability(ItemStack weapon, double dura) {
        setPDC(weapon, KEY_DURA, dura);
    }

    /** 获取最大耐久 */
    public static double getMaxDurability(ItemStack weapon) {
        return AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_DURA_MAX);
    }

    /** 耐久百分比（0~1） */
    public static double getDurabilityPercent(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "durability")) return 1.0;
        double max = getMaxDurability(weapon);
        if (max <= 0) return 1.0;
        return Math.max(0, getDurability(weapon) / max);
    }

    /** 射击时扣除耐久。返回 true = 破损 */
    public static boolean loseDurability(Player player, ItemStack weapon, double loss) {
        if (!GunSystemConfig.isSystemEnabled(player, "durability")) return false;

        double current = getDurability(weapon);
        double newVal = current - loss;
        setDurability(weapon, newVal);

        double max = getMaxDurability(weapon);
        double pct = Math.max(0, newVal / max);

        // 警告
        double warnThreshold = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_DURA_WARNING_THRESHOLD) / 100.0;
        if (pct <= warnThreshold && pct > 0) {
            player.sendActionBar(Component.text(
                "武器耐久低! (" + (int)(pct * 100) + "%)", NamedTextColor.YELLOW));
        }

        if (newVal <= 0) {
            // 破损
            player.sendActionBar(Component.text(
                "武器破损!", NamedTextColor.DARK_RED));
            return true;
        }
        return false;
    }

    /** 射击时扣除耐久（使用枪械配置的损耗值） */
    public static boolean shootLoss(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "durability")) return false;
        double loss = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_DURA_LOSS_PER_SHOT);
        // 弹药耐久修正
        AmmoConfig.AmmoTypeDef ammo = MagazineManager.getCurrentAmmoType(weapon);
        if (ammo != null) loss *= ammo.durabilityMult;
        return loseDurability(player, weapon, loss);
    }

    /** 获取散布惩罚倍率 */
    public static double getSpreadPenalty(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "durability")) return 1.0;

        double pct = getDurabilityPercent(player, weapon);
        if (pct >= 1.0) return 1.0;

        double max = getMaxDurability(weapon);
        if (max <= 0) return 1.0;

        // 破损时叠加额外惩罚
        double penalty = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_DURA_SPREAD_PENALTY);
        double result = 1.0 + ((1.0 - pct) * penalty / 100.0);
        if (pct <= 0) {
            double brokenPenalty = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_DURA_BROKEN_SPREAD_PENALTY);
            result += brokenPenalty / 100.0;
        }
        return result;
    }

    /** 获取后坐惩罚倍率 */
    public static double getRecoilPenalty(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "durability")) return 1.0;
        double pct = getDurabilityPercent(player, weapon);
        if (pct >= 1.0) return 1.0;
        double penalty = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_DURA_RECOIL_PENALTY);
        return 1.0 + ((1.0 - pct) * penalty / 100.0);
    }

    /** 修复耐久 */
    public static void repair(ItemStack weapon, double amount) {
        double max = getMaxDurability(weapon);
        double current = getDurability(weapon);
        setDurability(weapon, Math.min(max, current + amount));
    }

    /** 是否已破损 */
    public static boolean isBroken(ItemStack weapon) {
        return getDurability(weapon) <= 0;
    }

    /** 是否可修复 */
    public static boolean isRepairable(ItemStack weapon) {
        return AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_DURA_BROKEN_REPAIRABLE) >= 1.0;
    }

    /* PDC */
    private static Double getPDC(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
    }

    private static void setPDC(ItemStack item, NamespacedKey key, double value) {
        if (item == null) return;
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta()
            : org.bukkit.Bukkit.getItemFactory().getItemMeta(item.getType());
        if (meta == null) return;
        meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, value);
        item.setItemMeta(meta);
    }
}
