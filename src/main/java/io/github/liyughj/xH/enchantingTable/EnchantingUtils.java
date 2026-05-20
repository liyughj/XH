package io.github.liyughj.xH.enchantingTable;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * 附魔台工具类
 * 提供书架数量检测和满级判断功能
 */
public class EnchantingUtils {

    /* 满级所需书架数量，默认15（原版标准） */
    private static final int DEFAULT_REQUIRED_BOOKSHELVES = 15;

    /* 检测范围：以附魔台为中心，X/Z方向各延伸7格（15x15区域） */
    private static final int CHECK_RADIUS = 7;

    /**
     * 计算附魔台周围的有效书架数量
     * 使用原版附魔台的检测逻辑
     *
     * @param enchantingTable 附魔台位置
     * @return 有效书架数量（最大15）
     */
    public static int countBookshelves(Location enchantingTable) {
        int count = 0;
        Block tableBlock = enchantingTable.getBlock();

        /* 遍历以附魔台为中心的15x15区域，Y轴检测当前层和上一层 */
        for (int dx = -CHECK_RADIUS; dx <= CHECK_RADIUS; dx++) {
            for (int dz = -CHECK_RADIUS; dz <= CHECK_RADIUS; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    Location checkLoc = enchantingTable.clone().add(dx, dy, dz);
                    Block checkBlock = checkLoc.getBlock();

                    /* 检查是否为书架方块 */
                    if (checkBlock.getType() == Material.BOOKSHELF) {
                        /* 检查书架和附魔台之间是否有阻挡（空气或透明方块不算阻挡） */
                        if (!isBlocked(tableBlock, checkBlock)) {
                            count++;
                            /* 原版最大有效书架数为15，超过不再计算 */
                            if (count >= DEFAULT_REQUIRED_BOOKSHELVES) {
                                return DEFAULT_REQUIRED_BOOKSHELVES;
                            }
                        }
                    }
                }
            }
        }

        return count;
    }

    /**
     * 判断附魔台是否为满级
     *
     * @param enchantingTable 附魔台位置
     * @param requiredCount 所需书架数量
     * @return 是否满级
     */
    public static boolean isMaxLevel(Location enchantingTable, int requiredCount) {
        return countBookshelves(enchantingTable) >= requiredCount;
    }

    /**
     * 判断附魔台是否为满级（使用默认15个）
     *
     * @param enchantingTable 附魔台位置
     * @return 是否满级
     */
    public static boolean isMaxLevel(Location enchantingTable) {
        return isMaxLevel(enchantingTable, DEFAULT_REQUIRED_BOOKSHELVES);
    }

    /**
     * 检查两个方块之间是否有阻挡
     * 原版逻辑：书架和附魔台之间不能有实体方块阻挡
     *
     * @param from 起始方块（附魔台）
     * @param to 目标方块（书架）
     * @return 是否有阻挡
     */
    private static boolean isBlocked(Block from, Block to) {
        /* 获取两个方块中心点的位置 */
        Location fromLoc = from.getLocation().add(0.5, 0.5, 0.5);
        Location toLoc = to.getLocation().add(0.5, 0.5, 0.5);

        /* 计算方向向量 */
        double dx = toLoc.getX() - fromLoc.getX();
        double dy = toLoc.getY() - fromLoc.getY();
        double dz = toLoc.getZ() - fromLoc.getZ();

        /* 归一化 */
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance == 0) return false;

        dx /= distance;
        dy /= distance;
        dz /= distance;

        /* 从附魔台向书架方向射线检测，检查中间是否有实体方块 */
        double step = 0.5;
        for (double d = step; d < distance; d += step) {
            int checkX = (int) Math.floor(fromLoc.getX() + dx * d);
            int checkY = (int) Math.floor(fromLoc.getY() + dy * d);
            int checkZ = (int) Math.floor(fromLoc.getZ() + dz * d);

            Block checkBlock = from.getWorld().getBlockAt(checkX, checkY, checkZ);

            /* 如果中间有实体方块阻挡，则该书架无效 */
            if (checkBlock.getType().isSolid() &&
                !checkBlock.equals(from) &&
                !checkBlock.equals(to)) {
                return true;
            }
        }

        return false;
    }
}
