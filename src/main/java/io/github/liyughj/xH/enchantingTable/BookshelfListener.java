package io.github.liyughj.xH.enchantingTable;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * 书架事件监听器
 * 监听书架的放置和破坏事件，用于触发附近附魔台的重新检测
 * 注：当前版本仅做监控，实际阻止逻辑在EnchantingTableListener中处理
 */
public class BookshelfListener implements Listener {

    /* 搜索附魔台的范围：书架变动时，在此范围内搜索附魔台 */
    private static final int SEARCH_RADIUS = 15;

    /**
     * 监听书架放置事件
     *
     * @param event 方块放置事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        /* 只处理书架方块 */
        if (event.getBlock().getType() != Material.BOOKSHELF) {
            return;
        }

        /* 获取放置的书架位置 */
        Location bookshelfLoc = event.getBlock().getLocation();

        /* 查找附近的所有附魔台 */
        Set<Location> enchantingTables = findNearbyEnchantingTables(bookshelfLoc);

        /* 触发附魔台更新（当前版本主要用于未来扩展缓存机制） */
        for (Location tableLoc : enchantingTables) {
            /* 重新计算该附魔台的书架数量 */
            int count = EnchantingUtils.countBookshelves(tableLoc);
            /* 此处可以添加缓存更新逻辑 */
        }
    }

    /**
     * 监听书架破坏事件
     *
     * @param event 方块破坏事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        /* 只处理书架方块 */
        if (event.getBlock().getType() != Material.BOOKSHELF) {
            return;
        }

        /* 获取被破坏的书架位置 */
        Location bookshelfLoc = event.getBlock().getLocation();

        /* 查找附近的所有附魔台 */
        Set<Location> enchantingTables = findNearbyEnchantingTables(bookshelfLoc);

        /* 触发附魔台更新（当前版本主要用于未来扩展缓存机制） */
        for (Location tableLoc : enchantingTables) {
            /* 重新计算该附魔台的书架数量 */
            int count = EnchantingUtils.countBookshelves(tableLoc);
            /* 此处可以添加缓存更新逻辑 */
        }
    }

    /**
     * 查找指定位置附近的所有附魔台
     *
     * @param center 中心位置（书架位置）
     * @return 附近附魔台位置的集合
     */
    private Set<Location> findNearbyEnchantingTables(Location center) {
        Set<Location> enchantingTables = new HashSet<>();

        /* 在15格范围内搜索附魔台 */
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    Block block = center.clone().add(dx, dy, dz).getBlock();
                    if (block.getType() == Material.ENCHANTING_TABLE) {
                        enchantingTables.add(block.getLocation());
                    }
                }
            }
        }

        return enchantingTables;
    }
}
