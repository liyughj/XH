package io.github.liyughj.xH.debug;

import io.github.liyughj.xH.lore.LoreManager;
import io.github.liyughj.xH.rpg.Attribute.AttributeRange;
import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * RPG 武器/工具名称修复器。
 * <p>
 * 玩家在铁砧中给 RPG 武器/工具改名后，displayName 被修改，
 * 可能导致部分系统判定异常。本工具还原 displayName 并重新生成 lore。
 * <p>
 * 使用方式：
 * <ul>
 *   <li>OP 执行 /xh restore 修复手中武器/工具</li>
 *   <li>检测：枪械（gun_damage PDC）或 RPG 物品（任意 RPG 属性 PDC）</li>
 *   <li>还原：清除 displayName → 回退材质默认名，重新生成 lore</li>
 * </ul>
 */
public class RestoreManager {

    /**
     * 尝试修复玩家手持物品的名称。
     *
     * @param player 执行命令的玩家
     * @return true=已修复，false=不适用（不是RPG物品或未改名）
     */
    public static boolean tryRestore(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            player.sendMessage("§c手中无物品");
            return false;
        }
        if (!item.hasItemMeta()) {
            player.sendMessage("§c该物品无元数据，不是 RPG 物品");
            return false;
        }

        ItemMeta meta = item.getItemMeta();

        // 检测是否为枪械
        boolean isGun = isGunItem(item);

        // 检测是否为 RPG 武器/工具（有任意非默认 RPG 属性 PDC）
        boolean isRpg = false;
        if (!isGun) {
            isRpg = hasAnyRpgAttribute(item);
        }

        if (!isGun && !isRpg) {
            player.sendMessage("§c该物品不是 RPG 武器/工具");
            return false;
        }

        // 检查是否有自定义名称
        boolean hasCustomName = meta.hasDisplayName();
        if (!hasCustomName) {
            player.sendMessage("§e该物品未被改名，无需修复");
            return false;
        }

        // 还原：清除 displayName
        Component oldNameComp = meta.displayName();
        String oldName = oldNameComp != null ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(oldNameComp) : "无";
        meta.displayName(null);
        item.setItemMeta(meta);

        // 重新生成 lore
        if (isGun) {
            LoreManager.refreshGunLore(item);
        }

        player.sendMessage("§a已修复！原名: §7" + oldName + " §a→ 已还原为默认名称");
        return true;
    }

    /** 检测是否为枪械（GUN_DAMAGE 属性非默认值） */
    private static boolean isGunItem(ItemStack item) {
        AttributeRange gunDmg = AttributeStorage.getItemAttrRange(item, RpgAttribute.GUN_DAMAGE);
        return !(gunDmg.getMin() == RpgAttribute.GUN_DAMAGE.getDefaultValue()
            && gunDmg.getMax() == RpgAttribute.GUN_DAMAGE.getDefaultValue());
    }

    /** 检测是否有任意非默认 RPG 属性 */
    private static boolean hasAnyRpgAttribute(ItemStack item) {
        for (RpgAttribute attr : RpgAttribute.values()) {
            if (attr.getCategory() == RpgAttribute.Category.GUN) continue; // 枪械已单独检测
            AttributeRange range = AttributeStorage.getItemAttrRange(item, attr);
            if (range.getMin() != attr.getDefaultValue() || range.getMax() != attr.getDefaultValue()) {
                return true;
            }
        }
        return false;
    }
}
