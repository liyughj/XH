package io.github.liyughj.xH.enchantingTable;

import org.bukkit.Material;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 附魔物品限制监听器
 * 监听物品放入附魔台事件，限制只有普通书（BOOK）才能显示附魔选项
 */
public class EnchantingItemListener implements Listener {

    /**
     * 监听准备附魔物品事件
     * 当玩家将物品放入附魔位置时，判断物品类型
     * 只有普通书（BOOK）才显示附魔选项，其他物品不显示任何选项
     *
     * @param event 准备附魔物品事件
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemEnchant(PrepareItemEnchantEvent event) {
        /* 获取附魔位置的物品 */
        ItemStack item = event.getItem();

        /* 如果物品为空或空气，直接返回 */
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        /* 判断物品是否为普通书（BOOK） */
        if (item.getType() != Material.BOOK) {
            /* 非普通书：将所有附魔选项设为null，不显示任何附魔选项 */
            /* 这样附魔台界面将显示为空，玩家无法点击附魔 */
            EnchantmentOffer[] offers = event.getOffers();
            if (offers != null) {
                for (int i = 0; i < offers.length; i++) {
                    offers[i] = null;
                }
            }

            /* 注意：此处不发送任何提示消息给玩家，静默处理 */
        }
        /* 普通书（BOOK）：不做任何干预，正常显示附魔选项 */
    }
}
