package io.github.liyughj.xH.anvil;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

/**
 * 铁砧数据包监听器
 * 使用 ProtocolLib 修复铁砧"过于昂贵"显示问题
 */
public class AnvilPacketListener {

    private final int maxRepairCost;

    /**
     * 构造函数
     * @param plugin 插件实例
     * @param maxRepairCost 最大修复成本（用于修复"过于昂贵"显示）
     */
    public AnvilPacketListener(Plugin plugin, int maxRepairCost) {
        this.maxRepairCost = maxRepairCost;
        registerPacketListener(plugin);
    }

    /**
     * 注册 ProtocolLib 数据包监听器
     * @param plugin 插件实例
     */
    private void registerPacketListener(Plugin plugin) {
        /* 获取 ProtocolLib 的 ProtocolManager */
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

        /* 注册数据包监听器，监听服务器发送给客户端的 Window Data 数据包 */
        protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                PacketType.Play.Server.WINDOW_DATA
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                /* 获取数据包对象 */
                PacketContainer packet = event.getPacket();

                /* 获取玩家 */
                Player player = event.getPlayer();
                if (player == null) {
                    return;
                }

                /* 获取玩家当前打开的界面 */
                InventoryView view = player.getOpenInventory();
                if (view == null) {
                    return;
                }

                /* 判断是否为铁砧界面 */
                if (view.getType() != InventoryType.ANVIL) {
                    return;
                }

                /* 获取 Property ID（整数数组的第2个元素，索引为1） */
                int propertyId = packet.getIntegers().read(1);

                /* Property ID=0 表示最大经验成本（Maximum Repair Cost） */
                if (propertyId == 0) {
                    /* 修改最大经验成本为配置值 */
                    /* 这样即使实际成本超过40级，也不会显示"过于昂贵" */
                    packet.getIntegers().write(2, maxRepairCost);
                }
            }
        });
    }
}
