package io.github.liyughj.xH.anvil;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;


import java.util.HashSet;
import java.util.Map;


/**
 * 铁砧经验限制监听器
 * 监听铁砧准备事件和点击事件，强制铁砧使用经验为固定值（默认30级）
 */
public class AnvilListener implements Listener {

    private final AnvilConfig anvilConfig;

    /**
     * 构造函数
     *
     * @param anvilConfig 铁砧配置管理类实例
     */
    public AnvilListener(AnvilConfig anvilConfig) {
        this.anvilConfig = anvilConfig;
    }

    /**
     * 监听铁砧准备事件
     * 当玩家将物品放入铁砧时，强制设置经验成本为固定值
     * 并处理附魔合并规则
     *
     * @param event 铁砧准备事件
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        /* 获取铁砧视图对象（新API，替代AnvilInventory的过时方法） */
        AnvilView anvilView = event.getView();

        /* 获取当前修复成本（原计算值） */
        int originalCost = anvilView.getRepairCost();

        /* 只有存在有效操作时才修改（避免干扰空铁砧） */
        if (originalCost > 0) {
            /* 从配置获取固定经验成本，默认30级 */
            int fixedCost = anvilConfig.getFixedExpCost();

            /* 无论原成本是多少，强制设为配置值 */
            anvilView.setRepairCost(fixedCost);

            /* 注意：此处不发送任何提示消息给玩家，静默处理 */
            /* setRepairCost 会同时修改界面显示和实际消耗的经验 */
            /* 30级低于40级限制，不会显示"过于昂贵" */
        }

        /* 处理附魔合并 */
        handleEnchantMerge(event);
    }

    /**
     * 处理附魔合并
     *
     * @param event 铁砧准备事件
     */
    private void handleEnchantMerge(PrepareAnvilEvent event) {
        AnvilView anvilView = event.getView();

        /* 获取铁砧中的物品 */
        ItemStack firstItem = anvilView.getItem(0);
        ItemStack secondItem = anvilView.getItem(1);

        /* 检查物品是否有效 */
        if (firstItem == null || firstItem.getType().isAir() ||
            secondItem == null || secondItem.getType().isAir()) {
            return;
        }

        /* 使用 AnvilEnchantMerger 合并附魔 */
        Map<Enchantment, Integer> mergedEnchants = AnvilEnchantMerger.mergeEnchantments(firstItem, secondItem);

        /* 如果没有附魔需要合并，直接返回 */
        if (mergedEnchants.isEmpty()) {
            return;
        }

        /* 过滤掉不能应用到第一个物品的附魔 */
        Map<Enchantment, Integer> filteredEnchants = AnvilEnchantMerger.filterCompatibleEnchants(firstItem, mergedEnchants);

        /* 如果过滤后没有附魔，直接返回 */
        if (filteredEnchants.isEmpty()) {
            return;
        }

        /* 创建结果物品 */
        ItemStack result = createResultItem(firstItem, filteredEnchants);

        /* 设置事件结果 */
        if (result != null) {
            event.setResult(result);

            /* 设置修复成本 */
            int fixedCost = anvilConfig.getFixedExpCost();
            anvilView.setRepairCost(fixedCost);
        }
    }

    /**
     * 创建结果物品
     *
     * @param firstItem         第一个物品（基础物品）
     * @param mergedEnchants    合并后的附魔
     * @return 结果物品
     */
    private ItemStack createResultItem(ItemStack firstItem, Map<Enchantment, Integer> mergedEnchants) {
        /* 克隆第一个物品作为结果 */
        ItemStack result = firstItem.clone();

        /* 获取物品的 ItemMeta */
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return null;
        }

        /* 根据物品类型应用附魔 */
        if (meta instanceof EnchantmentStorageMeta) {
            /* 附魔书：使用 EnchantmentStorageMeta */
            EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) meta;

            /* 清除所有现有存储附魔 */
            for (Enchantment enchant : new HashSet<>(bookMeta.getStoredEnchants().keySet())) {
                bookMeta.removeStoredEnchant(enchant);
            }

            /* 添加新的存储附魔 */
            for (Map.Entry<Enchantment, Integer> entry : mergedEnchants.entrySet()) {
                bookMeta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
            }

            result.setItemMeta(bookMeta);
        } else {
            /* 普通物品：使用普通 ItemMeta */
            /* 清除所有现有附魔 */
            for (Enchantment enchant : new HashSet<>(meta.getEnchants().keySet())) {
                meta.removeEnchant(enchant);
            }

            /* 添加新附魔 */
            for (Map.Entry<Enchantment, Integer> entry : mergedEnchants.entrySet()) {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
            }

            result.setItemMeta(meta);
        }

        return result;
    }

    /**
     * 监听铁砧点击事件
     * 在玩家点击结果槽位时再次确保经验成本为固定值
     * 防止服务器后续计算覆盖设置的成本
     *
     * @param event 库存点击事件
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        /* 只处理铁砧界面 */
        if (event.getInventory().getType() != InventoryType.ANVIL) {
            return;
        }

        /* 只处理结果槽位的点击（槽位索引2是结果槽） */
        if (event.getRawSlot() != 2) {
            return;
        }

        /* 确保点击者是人类玩家 */
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        /* 获取铁砧视图 */
        if (!(event.getView() instanceof AnvilView)) {
            return;
        }

        AnvilView anvilView = (AnvilView) event.getView();

        /* 获取当前修复成本 */
        int currentCost = anvilView.getRepairCost();

        /* 只有在存在有效成本时才修改 */
        if (currentCost > 0) {
            int fixedCost = anvilConfig.getFixedExpCost();

            /* 如果成本不是固定值，则重新设置 */
            if (currentCost != fixedCost) {
                anvilView.setRepairCost(fixedCost);
            }
        }
    }
}
