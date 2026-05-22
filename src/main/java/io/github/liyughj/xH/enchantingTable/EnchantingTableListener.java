package io.github.liyughj.xH.enchantingTable;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

/**
 * 附魔台事件监听器
 * 监听玩家与附魔台的交互，阻止非满级附魔台的使用
 */
public class EnchantingTableListener implements Listener {

    /* 满级所需书架数量 */
    private final int requiredBookshelves;

    /**
     * 构造函数
     *
     * @param requiredBookshelves 满级所需书架数量
     */
    public EnchantingTableListener(int requiredBookshelves) {
        this.requiredBookshelves = requiredBookshelves;
    }

    /**
     * 监听玩家打开容器事件
     * 当玩家尝试打开附魔台时，检测是否为满级附魔台
     * 非满级附魔台静默阻止，无任何提示
     *
     * @param event 打开容器事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();

        /* 判断是否为附魔台界面 */
        if (inventory.getType() != InventoryType.ENCHANTING) {
            return;
        }

        /* 获取附魔台方块位置 */
        Location enchantingTableLoc = getEnchantingTableLocation(inventory);
        if (enchantingTableLoc == null) {
            return;
        }

        /* 检测是否为满级附魔台 */
        boolean isMaxLevel = EnchantingUtils.isMaxLevel(enchantingTableLoc, requiredBookshelves);

        /* 非满级附魔台：静默阻止打开，不发送任何提示 */
        if (!isMaxLevel) {
            event.setCancelled(true);
            /* 注意：此处不发送任何消息，完全静默处理 */
        }
        /* 满级附魔台：正常打开，不做任何干预 */
    }

    /**
     * 从附魔台界面获取附魔台方块位置
     *
     * @param inventory 附魔台界面
     * @return 附魔台方块位置，如果无法获取则返回null
     */
    private Location getEnchantingTableLocation(Inventory inventory) {
        /* 尝试从holder获取位置 */
        if (inventory.getHolder() instanceof org.bukkit.block.EnchantingTable) {
            org.bukkit.block.EnchantingTable enchantingTable =
                (org.bukkit.block.EnchantingTable) inventory.getHolder();
            return enchantingTable.getLocation();
        }

        /* 备用方案：通过查看器获取位置 */
        if (inventory.getViewers().isEmpty()) {
            return null;
        }

        /* 获取第一个查看者的位置，尝试找到附近的附魔台 */
        Location viewerLoc = inventory.getViewers().get(0).getLocation();
        return findNearestEnchantingTable(viewerLoc);
    }

    /**
     * 查找玩家附近的附魔台
     * 只在合理范围内搜索（玩家与附魔台交互的最大距离为6格）
     *
     * @param location 玩家位置
     * @return 最近的附魔台位置，如果找不到则返回null
     */
    private Location findNearestEnchantingTable(Location location) {
        /* 在玩家周围3格范围内搜索附魔台（确保找到的是玩家正在交互的附魔台） */
        int searchRadius = 3;
        Location nearestTable = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    Block block = location.clone().add(dx, dy, dz).getBlock();
                    if (block.getType() == Material.ENCHANTING_TABLE) {
                        /* 计算与玩家的距离 */
                        double distance = block.getLocation().distanceSquared(location);
                        /* 只接受在合理交互距离内的附魔台（6格以内） */
                        if (distance <= 36.0 && distance < nearestDistance) {
                            nearestDistance = distance;
                            nearestTable = block.getLocation();
                        }
                    }
                }
            }
        }

        return nearestTable;
    }
}
