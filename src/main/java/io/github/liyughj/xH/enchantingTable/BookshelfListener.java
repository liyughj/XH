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
 * 监听书架的放置和破坏事件，触发附近附魔台状态更新
 * 注：实际阻止逻辑在 EnchantingTableListener 中处理（基于 InventoryOpenEvent 实时检测）
 * 当前类主要作为扩展点保留，以便未来添加缓存或预计算机制
 */
public class BookshelfListener implements Listener {

    /* 搜索附魔台的范围：书架变动时，在此范围内搜索附魔台 */
    private static final int SEARCH_RADIUS = 8;

    /**
     * 监听书架放置事件
     * 当前为扩展点，未来可添加缓存刷新机制
     *
     * @param event 方块放置事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.BOOKSHELF) {
            return;
        }
        /* 预留：未来可在此处触发附近附魔台缓存刷新 */
    }

    /**
     * 监听书架破坏事件
     * 当前为扩展点，未来可添加缓存刷新机制
     *
     * @param event 方块破坏事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.BOOKSHELF) {
            return;
        }
        /* 预留：未来可在此处触发附近附魔台缓存刷新 */
    }

    /**
     * 查找指定位置附近的所有附魔台（工具方法，供未来缓存机制使用）
     *
     * @param center 中心位置（书架位置）
     * @return 附近附魔台位置的集合
     */
    public static Set<Location> findNearbyEnchantingTables(Location center) {
        Set<Location> enchantingTables = new HashSet<>();

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
