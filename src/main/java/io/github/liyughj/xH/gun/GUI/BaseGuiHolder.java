package io.github.liyughj.xH.gun.GUI;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * 枪械 GUI 通用 InventoryHolder 基类。
 *
 * <p>所有枪械相关 GUI 共用此 Holder，通过 {@code guiType} 字段区分具体 GUI。
 * 事件处理时用 {@link GuiUtils#isGunGuiHolder(Inventory)} 统一拦截。</p>
 *
 * <h3>子 GUI 使用方式</h3>
 * <pre>
 * // 打开时：
 * BaseGuiHolder holder = new BaseGuiHolder(playerUUID, sourceItem, heldSlot);
 * holder.guiType = "attachments";  // 自定义标识
 * Inventory inv = Bukkit.createInventory(holder, 54, Component.text("配件"));
 * holder.inventory = inv;
 *
 * // 事件中判断：
 * void onClick(InventoryClickEvent e) {
 *     BaseGuiHolder h = GuiUtils.getHolder(e.getInventory());
 *     if (h == null) return;
 *     if (!"attachments".equals(h.guiType)) return;
 *     // 处理配件 GUI 点击...
 * }
 * </pre>
 */
@SuppressWarnings("deprecation") // InventoryHolder 接口本身是合理的
public class BaseGuiHolder implements InventoryHolder {

    /** 打开 GUI 的玩家 UUID */
    public final UUID playerId;

    /** 被操作的源物品（弹夹/枪械等），直接修改其 PDC 即为保存 */
    public final ItemStack sourceItem;

    /** 源物品所在快捷栏位置（0-8），-1=非快捷栏 */
    public final int heldSlot;

    /** 自定义 GUI 类型标识，区分不同 GUI */
    public String guiType;

    /** 自定义附加数据槽，用于 GUI 间传递 */
    public Object extraData;

    public Inventory inventory;

    public BaseGuiHolder(UUID playerId, ItemStack sourceItem, int heldSlot) {
        this.playerId = playerId;
        this.sourceItem = sourceItem;
        this.heldSlot = heldSlot;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
