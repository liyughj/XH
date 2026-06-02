package io.github.liyughj.xH.enchantmentLevel;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * 附魔经验客户端显示（ProtocolLib）
 * 通过 /xh lore xp 命令切换到经验显示模式
 * 拦截物品数据包，为附魔物品注入经验进度Lore
 */
public class EnchantmentLevelDisplay implements Listener {

    private final JavaPlugin plugin;
    private final EnchantmentLevelManager manager;
    private final EnchantmentLevelConfig config;
    private final ProtocolManager protocolManager;

    /* 开启了经验显示模式的玩家 */
    private final Set<UUID> xpModePlayers = new HashSet<>();

    /* 每个玩家对应的满级 [MAX] 随机色（每次开启 XP 模式时随机一个） */
    private final Map<UUID, TextColor> playerMaxColors = new HashMap<>();

    /**
     * 构造函数
     *
     * @param plugin  插件实例
     * @param manager 经验管理器
     * @param config  配置实例
     */
    public EnchantmentLevelDisplay(JavaPlugin plugin, EnchantmentLevelManager manager, EnchantmentLevelConfig config) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        registerPacketListener();
    }

    /**
     * 注册ProtocolLib数据包监听器
     */
    private void registerPacketListener() {
        protocolManager.addPacketListener(new PacketAdapter(plugin,
            PacketType.Play.Server.WINDOW_ITEMS,
            PacketType.Play.Server.SET_SLOT) {

            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                if (player == null) return;

                /* 只对开启了经验显示模式的玩家修改数据包 */
                if (!isXpMode(player)) return;

                PacketContainer packet = event.getPacket();
                PacketType type = event.getPacketType();

                if (type == PacketType.Play.Server.WINDOW_ITEMS) {
                    handleWindowItems(player, packet);
                } else if (type == PacketType.Play.Server.SET_SLOT) {
                    handleSetSlot(player, packet);
                }
            }
        });
    }

    /**
     * 处理 WINDOW_ITEMS 数据包
     */
    private void handleWindowItems(Player player, PacketContainer packet) {
        List<ItemStack> items = packet.getItemListModifier().read(0);
        List<ItemStack> modified = new ArrayList<>();
        TextColor maxColor = playerMaxColors.get(player.getUniqueId());

        for (int i = 0; i < items.size(); i++) {
            ItemStack item = items.get(i);
            ItemStack modifiedItem = modifyItemLore(item, maxColor);
            if (modifiedItem != null) {
                modified.add(modifiedItem);
            } else {
                /* 空槽位使用 Air */
                modified.add(item != null ? item : new ItemStack(Material.AIR));
            }
        }

        packet.getItemListModifier().write(0, modified);
    }

    /**
     * 处理 SET_SLOT 数据包
     */
    private void handleSetSlot(Player player, PacketContainer packet) {
        ItemStack item = packet.getItemModifier().read(0);
        TextColor maxColor = playerMaxColors.get(player.getUniqueId());
        ItemStack modified = modifyItemLore(item, maxColor);
        if (modified != null) {
            packet.getItemModifier().write(0, modified);
        }
    }

    /**
     * 修改物品Lore（注入附魔经验进度）
     *
     * @param item     原始物品
     * @param maxColor 玩家当前 [MAX] 颜色，可为 null
     * @return 修改后的物品（克隆），如果不需要修改则返回null
     */
    private ItemStack modifyItemLore(ItemStack item, TextColor maxColor) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }

        /* 检查物品是否有需要显示的附魔 */
        Set<Enchantment> enchants = EnchantmentLevelData.getAllEnchantments(item);
        if (enchants.isEmpty()) {
            return null;
        }

        /* 检查是否存在经验数据 */
        if (!manager.hasExpData(item)) {
            return null;
        }

        /* 克隆物品避免修改原始物品 */
        ItemStack cloned = item.clone();
        ItemMeta meta = cloned.getItemMeta();
        List<Component> originalLore = meta.lore();

        /* 获取附魔经验Lore */
        List<Component> expLore = manager.getDisplayLoreComponents(item, maxColor);
        if (expLore.isEmpty()) {
            return null;
        }

        /* 过滤掉已存在的经验进度行，避免重复 */
        List<Component> filteredOriginalLore = new ArrayList<>();
        if (originalLore != null) {
            for (Component line : originalLore) {
                if (!isExpLoreLine(line)) {
                    filteredOriginalLore.add(line);
                }
            }
        }

        /* 构建新Lore：附魔经验行在前，原始Lore在后 */
        List<Component> newLore = new ArrayList<>(expLore);
        newLore.addAll(filteredOriginalLore);

        meta.lore(newLore);

        /* 隐藏原版附魔显示 */
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        cloned.setItemMeta(meta);
        return cloned;
    }

    /**
     * 判断是否为经验进度Lore行
     * 通过强特征（经验条方块字符或 [MAX] 标记）来识别，避免误判普通描述中的 "x/y" 文本
     *
     * @param line Lore行
     * @return 是否为经验进度行
     */
    private boolean isExpLoreLine(Component line) {
        if (line == null) return false;
        String text = line.toString();
        /* 经验条方块字符 [MAX] 标记是经验Lore的强特征 */
        return text.contains("█") || text.contains("░") || text.contains("[MAX]");
    }

    /**
     * 设置玩家的经验显示模式
     *
     * @param player 玩家
     * @param on     是否开启
     */
    public void setXpMode(Player player, boolean on) {
        if (on) {
            xpModePlayers.add(player.getUniqueId());
            /* 每次开启 XP 模式时，随机生成一个 [MAX] 文字颜色，整局固定不变 */
            playerMaxColors.put(player.getUniqueId(), EnchantmentLevelManager.getRandomMaxColor());
        } else {
            xpModePlayers.remove(player.getUniqueId());
            playerMaxColors.remove(player.getUniqueId());
        }
        /* 延迟一 tick 刷新，确保模式切换完成后再发包 */
        Bukkit.getScheduler().runTaskLater(plugin, () -> refreshInventory(player), 1L);
    }

    /**
     * 查询玩家是否开启了经验显示模式
     *
     * @param player 玩家
     * @return 是否开启
     */
    public boolean isXpMode(Player player) {
        return xpModePlayers.contains(player.getUniqueId());
    }

    /**
     * 刷新玩家的库存显示
     * 使用 ProtocolLib 手动发送 WINDOW_ITEMS 数据包强制刷新
     */
    private void refreshInventory(Player player) {
        InventoryView view = player.getOpenInventory();
        if (view == null) return;

        int windowId = view.getTopInventory().getType() == InventoryType.CRAFTING ? 0 : view.hashCode();

        try {
            /* 构建物品列表 */
            List<ItemStack> items = new ArrayList<>();
            int slotCount = view.countSlots();
            TextColor maxColor = playerMaxColors.get(player.getUniqueId());

            for (int i = 0; i < slotCount; i++) {
                ItemStack item = view.getItem(i);
                if (item == null || item.getType().isAir()) {
                    items.add(new ItemStack(Material.AIR));
                } else {
                    /* 如果是经验模式，应用修改；否则发送原始物品 */
                    if (isXpMode(player)) {
                        ItemStack modified = modifyItemLore(item, maxColor);
                        items.add(modified != null ? modified : item);
                    } else {
                        /* 正常模式：发送干净的物品（移除HIDE_ENCHANTS） */
                        items.add(getCleanItem(item));
                    }
                }
            }

            /* 创建 WINDOW_ITEMS 数据包 */
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.WINDOW_ITEMS);
            packet.getIntegers().write(0, windowId);
            packet.getIntegers().write(1, 0); /* stateId */
            packet.getItemListModifier().write(0, items);
            packet.getItemModifier().write(0, new ItemStack(Material.AIR)); /* carriedItem */

            /* 发送数据包 */
            protocolManager.sendServerPacket(player, packet);

        } catch (Exception e) {
            plugin.getLogger().warning("刷新库存显示时发生错误: " + e.getMessage());
            /* 回退到原版方法 */
            player.updateInventory();
        }
    }

    /**
     * 获取干净的物品（移除经验Lore和HIDE_ENCHANTS）
     *
     * @param item 原始物品
     * @return 干净的物品
     */
    private ItemStack getCleanItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return item;
        }

        ItemStack clean = item.clone();
        ItemMeta meta = clean.getItemMeta();

        /* 移除 HIDE_ENCHANTS，让原版附魔显示 */
        meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);

        /* 获取原始Lore（不含经验进度） */
        List<Component> originalLore = meta.lore();
        if (originalLore != null) {
            /* 过滤掉经验进度行 */
            List<Component> filteredLore = new ArrayList<>();
            for (Component line : originalLore) {
                if (!isExpLoreLine(line)) {
                    filteredLore.add(line);
                }
            }
            if (filteredLore.isEmpty()) {
                meta.lore(null);
            } else {
                meta.lore(filteredLore);
            }
        }

        clean.setItemMeta(meta);
        return clean;
    }

    /* ========== Bukkit事件监听 ========== */

    /**
     * 玩家退出时清理追踪数据
     * 避免玩家重新登录时颜色混乱，并防止内存泄漏
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        xpModePlayers.remove(uuid);
        playerMaxColors.remove(uuid);
    }

    /**
     * 插件禁用时清理所有资源
     * 通过 shutdown() 方法在 onDisable 中显式调用
     */
    public void shutdown() {
        xpModePlayers.clear();
        playerMaxColors.clear();
        if (protocolManager != null) {
            protocolManager.removePacketListeners(plugin);
        }
    }
}
