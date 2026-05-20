package io.github.liyughj.xH.enchantingTable;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 附魔等级限制监听器
 * 监听附魔完成事件，强制将所有附魔等级设为 I级（1级）
 */
public class EnchantingLevelListener implements Listener {

    /**
     * 监听附魔物品事件
     * 当玩家通过附魔台完成附魔时，强制将所有附魔等级设为 I级
     *
     * @param event 附魔物品事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEnchantItem(EnchantItemEvent event) {
        /* 获取即将添加的附魔Map */
        Map<Enchantment, Integer> enchantsToAdd = event.getEnchantsToAdd();

        /* 如果附魔列表为空，直接返回 */
        if (enchantsToAdd.isEmpty()) {
            return;
        }

        /* 创建新的附魔Map，所有等级强制设为 I级（1级） */
        Map<Enchantment, Integer> levelOneEnchants = new HashMap<>();

        for (Enchantment enchantment : enchantsToAdd.keySet()) {
            /* 强制等级为 I级（1级） */
            levelOneEnchants.put(enchantment, 1);
        }

        /* 清除原附魔列表 */
        enchantsToAdd.clear();

        /* 添加 I级附魔 */
        enchantsToAdd.putAll(levelOneEnchants);

        /* 注意：此处不发送任何提示，静默处理 */
    }
}
