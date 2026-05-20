package io.github.liyughj.xH.anvil;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.view.AnvilView;

/**
 * 铁砧经验限制监听器
 * 监听铁砧准备事件，强制铁砧使用经验为固定值（60级）
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
            /* 从配置获取固定经验成本，默认60级 */
            int fixedCost = anvilConfig.getFixedExpCost();

            /* 无论原成本是多少，强制设为配置值 */
            anvilView.setRepairCost(fixedCost);

            /* 注意：此处不发送任何提示消息给玩家，静默处理 */
            /* setRepairCost 会同时修改界面显示和实际消耗的经验 */
        }
    }
}
