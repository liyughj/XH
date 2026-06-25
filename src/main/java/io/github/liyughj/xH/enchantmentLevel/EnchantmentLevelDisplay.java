package io.github.liyughj.xH.enchantmentLevel;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import io.github.liyughj.xH.enchantmentLevel.EnchantmentsList.LevelConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
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
    private final LevelConfig levelConfig;
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
     */
    public EnchantmentLevelDisplay(JavaPlugin plugin, EnchantmentLevelManager manager, LevelConfig levelConfig) {
        this.plugin = plugin;
        this.manager = manager;
        this.levelConfig = levelConfig;
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
     * 修改物品Lore（注入附魔经验进度 + 效果汇总）。
     * <p>
     * 统一过滤 + 重建策略：
     *  extractCleanLore() 用 isInjectedLine() 剥离所有已注入行，
     *  再追加最新 XP 行 + 效果汇总行，杜绝叠层残留。
     */
    private ItemStack modifyItemLore(ItemStack item, TextColor maxColor) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;

        Set<Enchantment> enchants = EnchantmentLevelData.getAllEnchantments(item);
        if (enchants.isEmpty()) return null;
        if (!manager.hasExpData(item)) return null;

        ItemStack cloned = item.clone();
        ItemMeta meta = cloned.getItemMeta();

        List<Component> cleanLore = extractCleanLore(meta);

        List<Component> expLore = manager.getDisplayLoreComponents(item, maxColor);
        if (expLore.isEmpty()) return null;

        List<Component> newLore = new ArrayList<>(expLore);
        newLore.addAll(cleanLore);
        newLore.addAll(getEffectSummaryLines(item));

        meta.lore(newLore);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        cloned.setItemMeta(meta);
        return cloned;
    }

    /**
     * 从 ItemMeta 中提取干净 Lore（去掉经验进度行 + 效果汇总行）。
     */
    private List<Component> extractCleanLore(ItemMeta meta) {
        List<Component> lore = meta.lore();
        if (lore == null) return new ArrayList<>();
        List<Component> clean = new ArrayList<>();
        for (Component line : lore) {
            if (!isInjectedLine(line)) {
                clean.add(line);
            }
        }
        return clean;
    }

    /**
     * 判断该行是否由本系统注入（经验条 / 效果汇总 / 分隔线）。
     * 检查三大特征：
     *   █ ░ [MAX]  → 经验进度条
     *   →           → 效果汇总行（含标题，标题包含 →）
     */
    private boolean isInjectedLine(Component line) {
        if (line == null) return false;
        String text = line.toString();
        return text.contains("→") || text.contains("█") || text.contains("░") || text.contains("[MAX]");
    }

    /**
     * 设置玩家的经验显示模式
     *
     * @param player 玩家
     * @param on     是否开启
     */
    public void setXpMode(Player player, boolean on) {
        UUID uuid = player.getUniqueId();
        if (on) {
            xpModePlayers.add(uuid);
            playerMaxColors.put(uuid, EnchantmentLevelManager.getRandomMaxColor());
        } else {
            xpModePlayers.remove(uuid);
            playerMaxColors.remove(uuid);
        }
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
     * 获取干净的物品（移除所有注入行和 HIDE_ENCHANTS）
     */
    private ItemStack getCleanItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return item;

        ItemStack clean = item.clone();
        ItemMeta meta = clean.getItemMeta();
        meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);

        List<Component> cleanLore = extractCleanLore(meta);
        meta.lore(cleanLore.isEmpty() ? null : cleanLore);

        clean.setItemMeta(meta);
        return clean;
    }

    /* ========== 附魔效果汇总 ========== */

    private static final TextColor SUMMARY_HEADER_COLOR = TextColor.color(0xAAAAAA);
    private static final TextColor SUMMARY_LABEL_COLOR = TextColor.color(0x888888);
    private static final TextColor SUMMARY_VALUE_COLOR = TextColor.color(0x55FF55);
    private static final Component SUMMARY_HEADER = Component.text()
        .append(Component.text("── 附魔效果 → ──", SUMMARY_HEADER_COLOR))
        .decoration(TextDecoration.ITALIC, false)
        .build();

    /**
     * 生成物品所有附魔的效果汇总行
     */
    private List<Component> getEffectSummaryLines(ItemStack item) {
        List<Component> lines = new ArrayList<>();
        if (item == null || !item.hasItemMeta()) return lines;

        ItemMeta meta = item.getItemMeta();
        Map<Enchantment, Integer> enchantLevels;
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            enchantLevels = storageMeta.getStoredEnchants();
        } else {
            enchantLevels = meta.getEnchants();
        }
        if (enchantLevels.isEmpty()) return lines;

        /* 按原版注册顺序排序 */
        List<Map.Entry<Enchantment, Integer>> sorted = new ArrayList<>(enchantLevels.entrySet());
        sorted.sort(Comparator.comparingInt(e -> manager.getEnchantmentOrder(e.getKey())));

        boolean hasAny = false;
        for (Map.Entry<Enchantment, Integer> entry : sorted) {
            Component line = buildEffectLine(entry.getKey(), entry.getValue());
            if (line != null) {
                if (!hasAny) {
                    lines.add(SUMMARY_HEADER);
                    hasAny = true;
                }
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * 为单个附魔生成效果描述行，无效果返回 null
     */
    private Component buildEffectLine(Enchantment enchant, int level) {
        String key = enchant.getKey().getKey();
        Component name = enchant.displayName(level)
            .decoration(TextDecoration.ITALIC, false).color(SUMMARY_LABEL_COLOR);
        String desc = null;

        switch (key) {
            /* ---- 武器伤害 ---- */
            case "sharpness", "smite", "bane_of_arthropods", "impaling", "riptide",
                 "loyalty", "channeling":
                double wpct = levelConfig.getDamagePercentPerLevel(key);
                if (wpct > 0) desc = "+" + fmt(wpct * level) + "% 伤害";
                break;

            /* ---- 致密 ---- */
            case "density":
                double dPct = levelConfig.getDensityDamagePercentPerBlock(key);
                double dMax = levelConfig.getDensityMaxMultiplierPercent(key);
                if (dPct > 0) desc = "+" + fmt(dPct) + "%/格 伤害, 上限" + fmt(dMax) + "%";
                break;

            /* ---- 火焰附加 & 火矢 ---- */
            case "fire_aspect":
            case "flame":
                int ft = levelConfig.getFireTicksPerLevel(key) * level;
                if (ft > 0) desc = ft + " tick 燃烧";
                break;

            /* ---- 击退 & 冲击 ---- */
            case "knockback":
            case "punch":
                double kb = levelConfig.getKnockbackBlocksPerLevel(key) * level;
                if (kb > 0) desc = "+" + fmt(kb) + " 格击退";
                break;

            /* ---- 横扫之刃 ---- */
            case "sweeping_edge":
                double swDmg = levelConfig.getSweepDamagePercentPerLevel(key) * level;
                double swRng = levelConfig.getSweepRange(key);
                if (swDmg > 0 && swRng > 0)
                    desc = "+" + fmt(swDmg) + "% 横扫伤害, " + fmt(swRng) + " 格范围";
                break;

            /* ---- 抢夺 ---- */
            case "looting":
                double ld = levelConfig.getLootingMaxDropPercentPerLevel(key) * level;
                double lr = levelConfig.getLootingRareChancePercentPerLevel(key) * level;
                if (ld > 0 || lr > 0)
                    desc = "+" + fmt(ld) + "% 额外掉落, +" + fmt(lr) + "% 稀有";
                break;

            /* ---- 保护系 ---- */
            case "protection", "fire_protection", "blast_protection",
                 "projectile_protection":
                double ppct = levelConfig.getProtectionPercentPerLevel(key) * level;
                if (ppct > 0) desc = fmt(ppct) + "% 伤害减免";
                break;

            /* ---- 摔落保护 ---- */
            case "feather_falling":
                double ff = levelConfig.getProtectionPercentPerLevel(key) * level;
                if (ff > 0) desc = fmt(ff) + "% 摔落减免";
                break;

            /* ---- 荆棘 ---- */
            case "thorns":
                double tc = levelConfig.getThornsChancePerLevel(key) * level;
                double td = levelConfig.getThornsDamagePerLevel(key) * level;
                if (tc > 0 && td > 0)
                    desc = fmt(tc) + "% 概率" + fmt(td) + "% 反伤";
                break;

            /* ---- 水下呼吸 ---- */
            case "respiration":
                double rs = levelConfig.getRespirationSecondsPerLevel(key) * level;
                if (rs > 0) desc = "+" + fmt(rs) + "s 水中呼吸";
                break;

            /* ---- 水下速掘 ---- */
            case "aqua_affinity":
                double aq = levelConfig.getAquaAffinityChainPercentPerLevel(key) * level;
                if (aq > 0) desc = fmt(aq) + "% 链挖掘";
                break;

            /* ---- 深海探索者 ---- */
            case "depth_strider":
                double ds = levelConfig.getDamagePercentPerLevel(key) * level;
                if (ds > 0) desc = "+" + fmt(ds) + "% 水下伤害";
                break;

            /* ---- 灵魂疾行 ---- */
            case "soul_speed":
                double ss = levelConfig.getDamagePercentPerLevel(key) * level;
                if (ss > 0) desc = "+" + fmt(ss) + "% 灵魂沙伤害";
                break;

            /* ---- 迅捷潜行 ---- */
            case "swift_sneak":
                double sn = levelConfig.getDamagePercentPerLevel(key) * level;
                if (sn > 0) desc = "+" + fmt(sn) + "% 潜行伤害";
                break;

            /* ---- 冰霜行者 ---- */
            case "frost_walker":
                double fr = levelConfig.getFrostWalkerBaseRadius(key)
                    + levelConfig.getFrostWalkerRadiusPerLevel(key) * level;
                double fm = levelConfig.getFrostWalkerMeltBaseSeconds(key)
                    + levelConfig.getFrostWalkerMeltSecondsPerLevel(key) * level;
                if (fr > 0 || fm > 0) desc = "半径" + fmt(fr) + ", " + fmt(fm) + "s 滞留";
                break;

            /* ---- 力量 & 无限 ---- */
            case "power":
            case "infinity":
                double bpct = levelConfig.getDamagePercentPerLevel(key) * level;
                if (bpct > 0) desc = "+" + fmt(bpct) + "% 箭矢伤害";
                break;

            /* ---- 多重射击 ---- */
            case "multishot":
                double ma = levelConfig.getMultishotExtraArrowsPerLevel(key) * level;
                if (ma > 0) desc = "+" + fmt(ma) + " 额外箭矢";
                break;

            /* ---- 快速装填 & 穿透 ---- */
            case "quick_charge":
            case "piercing":
                double cpct = levelConfig.getDamagePercentPerLevel(key) * level;
                if (cpct > 0) desc = "+" + fmt(cpct) + "% 弩伤害";
                break;

            /* ---- 海之眷顾 ---- */
            case "luck_of_the_sea":
                double lfb = levelConfig.getFortuneBaseProbability(key);
                double lfd = levelConfig.getFortuneProbDecrement(key);
                if (lfb > 0) desc = fmt(lfb) + "%/" + fmt(lfd) + "% 级联概率";
                break;

            /* ---- 饵钓 ---- */
            case "lure":
                double lu = levelConfig.getLureChancePerLevel(key) * level;
                if (lu > 0) desc = "+" + fmt(lu) + "% 额外一钩";
                break;

            /* ---- 效率 ---- */
            case "efficiency":
                double cr = levelConfig.getChainRange(key);
                if (cr > 0) desc = fmt(cr) + " 格链挖掘";
                break;

            /* ---- 精准采集 ---- */
            case "silk_touch":
                double sc = levelConfig.getSilkTouchKeepChancePerLevel(key) * level;
                if (sc > 0) desc = fmt(sc) + "% 保留方块";
                break;

            /* ---- 时运 ---- */
            case "fortune":
                double fb = levelConfig.getFortuneBaseProbability(key);
                double fd = levelConfig.getFortuneProbDecrement(key);
                if (fb > 0) desc = fmt(fb) + "%/" + fmt(fd) + "% 级联概率";
                break;

            /* ---- 耐久 ---- */
            case "unbreaking":
                double us = levelConfig.getUnbreakingSaveChancePerLevel(key) * level;
                double ur = levelConfig.getUnbreakingReturnChancePerLevel(key) * level;
                double urr = levelConfig.getUnbreakingReturnRatePerLevel(key);
                if (us > 0 || ur > 0)
                    desc = fmt(us) + "% 不耗/" + fmt(ur) + "% 返还(" + fmt(urr) + "%)";
                break;

            /* ---- 经验修补 ---- */
            case "mending":
                double md = levelConfig.getMendingDurabilityPerXp(key) * level;
                if (md > 0) desc = fmt(md) + " 耐久/XP";
                break;

            /* ---- 破甲 ---- */
            case "breach":
                double bp = levelConfig.getBreachArmorBypassPercentPerLevel(key) * level;
                if (bp > 0) desc = fmt(bp) + "% 护甲穿透";
                break;

            /* ---- 风爆 ---- */
            case "wind_burst":
                double wh = levelConfig.getWindBurstHeightPerLevel(key) * level;
                double ws = levelConfig.getWindBurstSlowFallTicksPerLevel(key) * level / 20.0;
                if (wh > 0 && ws > 0)
                    desc = "+" + fmt(wh) + " 格弹飞, " + fmt(ws) + "s 缓降";
                break;

            /* ---- 突进 ---- */
            case "lunge":
                int sa = levelConfig.getLungeSpeedAmplifier(key) * level;
                double dt = levelConfig.getLungeDurationTicksPerLevel(key) * level / 20.0;
                if (dt > 0)
                    desc = "速度 " + EnchantmentLevelManager.toRoman(sa + 1) + ", " + fmt(dt) + "s 持续";
                break;
        }

        if (desc == null) return null;

        return Component.text()
            .append(name)
            .append(Component.text(" → ", SUMMARY_HEADER_COLOR))
            .append(Component.text(desc, SUMMARY_VALUE_COLOR))
            .decoration(TextDecoration.ITALIC, false)
            .build();
    }

    /** 格式化数值：整数值省略小数点 */
    private static String fmt(double v) {
        return v == (long) v ? String.valueOf((long) v) : String.format("%.1f", v);
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
