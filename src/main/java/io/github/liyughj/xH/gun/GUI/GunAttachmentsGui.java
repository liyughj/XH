package io.github.liyughj.xH.gun.GUI;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 枪械配件 GUI 接口 —— 配件系统扩展契约。
 *
 * <p>配件模块实现此接口后：</p>
 * <ol>
 *   <li>注册为 Listener 处理自身 GUI 点击事件</li>
 *   <li>通过 {@link #onRightClickWeapon(Player, ItemStack, int)} 打开配件界面</li>
 *   <li>通过 {@link io.github.liyughj.xH.gun.GunAttributeProvider} 将配件加成注入属性链</li>
 * </ol>
 *
 * <h3>典型 GUI 布局（54 槽）</h3>
 * <pre>
 *  Row 0-1 (0-17):   已安装配件显示
 *  Row 2   (18-26):  分隔栏
 *  Row 3-4 (27-44):  背包中可用配件
 *  Row 5   (45-53):  操作按钮（拆卸/卸下全部/关闭）
 * </pre>
 *
 * <h3>配件 PDC 规范</h3>
 * <pre>
 *  attachment_id    — 配件ID (String)
 *  attachment_slot  — 槽位类型: muzzle/optic/grip/magazine/stock/laser/trigger (String)
 *  attachment_data  — 自定义数据 (String, JSON可扩展)
 * </pre>
 *
 * <h3>实现示例</h3>
 * <pre>
 * public class MuzzleAttachmentGui implements Listener, GunAttachmentsGui {
 *     &#64;Override
 *     public void open(Player player, ItemStack weapon, int heldSlot) {
 *         BaseGuiHolder holder = new BaseGuiHolder(player.getUniqueId(), weapon, heldSlot);
 *         holder.guiType = "attachments_muzzle";
 *         Inventory inv = Bukkit.createInventory(holder, 54, Component.text("枪口配件"));
 *         holder.inventory = inv;
 *         // 渲染...
 *         player.openInventory(inv);
 *     }
 * }
 * </pre>
 */
public interface GunAttachmentsGui {

    /* ──── 标准槽位枚举 ──── */

    /** 配件槽位类型 */
    enum AttachmentSlot {
        MUZZLE("枪口", "消音器/制退器/消焰器"),
        OPTIC("瞄具", "红点/全息/ACOG/高倍镜"),
        GRIP("握把", "垂直/直角/轻型"),
        MAGAZINE("弹夹", "扩容/快速/弹鼓"),
        STOCK("枪托", "轻型/重型/折叠"),
        LASER("镭射", "激光/红外"),
        TRIGGER("扳机", "轻型/竞技/双动");

        public final String displayName;
        public final String description;

        AttachmentSlot(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    /* ──── 核心方法 ──── */

    /**
     * 打开指定槽位的配件 GUI。
     *
     * @param player     玩家
     * @param weapon     枪械物品（PDC 修改即保存）
     * @param heldSlot   快捷栏位置
     */
    void open(Player player, ItemStack weapon, int heldSlot);

    /**
     * 处理配件槽位点击（安装/拆卸）。
     * 实现者在 onInventoryClick 中调用。
     *
     * @param event   点击事件
     * @param holder  GUI Holder
     * @param slot    点击的槽位
     * @return true = 已处理，应 return
     */
    boolean handleClick(InventoryClickEvent event, BaseGuiHolder holder, int slot);

    /**
     * 获取当前 GUI 处理的对象物品上已安装的配件列表。
     *
     * @param weapon 枪械物品
     * @return {slotType → attachmentId}
     */
    java.util.Map<String, String> getInstalled(ItemStack weapon);

    /**
     * 安装配件。将配件 PDC 写入枪械物品。
     *
     * @param weapon       枪械
     * @param slotType     槽位
     * @param attachmentId 配件ID
     */
    void install(ItemStack weapon, String slotType, String attachmentId);

    /**
     * 拆卸配件。从枪械移除配件，返还给玩家。
     *
     * @param player  玩家（用于返还物品）
     * @param weapon  枪械
     * @param slotType 槽位
     * @return 拆卸的配件ID，null=无配件
     */
    String uninstall(Player player, ItemStack weapon, String slotType);
}
