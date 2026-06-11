package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弹夹系统管理器。
 * 管理弹夹容量、当前子弹数、换弹流程。
 * 通过 PDC 存储当前弹夹子弹数（mag_ammo）。
 */
public class MagazineManager {

    private static final NamespacedKey KEY_MAG_AMMO = new NamespacedKey("xh", "mag_ammo");
    /** 弹夹内子弹栈（逗号分隔，左=先装弹=底, 右=后装弹=顶=下次发射） */
    private static final NamespacedKey KEY_MAG_STACK = new NamespacedKey("xh", "mag_ammo_stack");
    /** 当前发射使用的弹种（消费前peek暂存，管道下游读取） */
    private static final NamespacedKey KEY_CURRENT_SHOT = new NamespacedKey("xh", "mag_current_shot");
    private static JavaPlugin plugin;

    public static class ReloadState {
        int reloadTaskId = -1;
        boolean reloading;
        int stagedAt;
        float originalWalkSpeed = 0.2f;
    }

    private static final Map<UUID, ReloadState> reloadStates = new ConcurrentHashMap<>();

    public static void init(JavaPlugin p) {
        plugin = p;
    }

    /* ==================== 弹夹操作 ==================== */

    /** 获取当前弹夹子弹数 */
    public static int getAmmo(ItemStack weapon) {
        Integer val = getPDC(weapon, KEY_MAG_AMMO);
        return val != null ? val : getCapacity(weapon);
    }

    /** 设置弹夹子弹数 */
    public static void setAmmo(ItemStack weapon, int ammo) {
        setPDC(weapon, KEY_MAG_AMMO, ammo);
    }

    /** 获取弹夹容量 */
    public static int getCapacity(ItemStack weapon) {
        double cap = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_MAG_CAPACITY);
        return (int) Math.max(0, cap);
    }

    /** 消耗一颗子弹，返回剩余弹数 */
    public static int consumeAmmo(Player player, ItemStack weapon) {
        int current = getAmmo(weapon);
        if (current <= 0) return 0;
        // 弹出前暂存当前弹种，供下游管道读取
        String top = peekNextAmmoType(weapon);
        setStringPDC(weapon, KEY_CURRENT_SHOT, top);
        int next = current - 1;
        setAmmo(weapon, next);
        popTopFromStack(weapon);
        return next;
    }

    /** 清除当前发射弹种标记（每个射击周期结束后调用） */
    public static void clearCurrentShot(ItemStack weapon) {
        setStringPDC(weapon, KEY_CURRENT_SHOT, null);
    }

    /** 消耗一颗子弹并返回发射的弹种ID */
    public static String consumeAmmoPopType(Player player, ItemStack weapon) {
        int current = getAmmo(weapon);
        if (current <= 0) return null;
        int next = current - 1;
        setAmmo(weapon, next);
        return popTopFromStack(weapon);
    }

    /* ─── 弹夹子弹栈（倒序发射：后压入的优先射出） ─── */

    /** 获取当前弹夹栈顶弹种（不消耗），null=栈空或无弹药系统 */
    public static String peekNextAmmoType(ItemStack weapon) {
        String stack = getStackPDC(weapon);
        if (stack == null || stack.isEmpty()) return null;
        int lastComma = stack.lastIndexOf(',');
        return lastComma >= 0 ? stack.substring(lastComma + 1) : stack;
    }

    /** 取当前发射应使用的弹种定义（本次射击标记 > 栈顶 > 枪械默认 > null） */
    public static AmmoConfig.AmmoTypeDef getCurrentAmmoType(ItemStack weapon) {
        if (GunSystemConfig.ammo() == null) return null;
        // 0. 本次射击标记（consumAmmo 弹出前暂存）
        String currentShot = getStringPDC(weapon, KEY_CURRENT_SHOT);
        if (currentShot != null && !currentShot.isEmpty()) {
            AmmoConfig.AmmoTypeDef def = GunSystemConfig.ammo().getAmmoType(currentShot);
            if (def != null) return def;
        }
        // 1. 弹夹栈顶（未消耗时有效）
        String stackType = peekNextAmmoType(weapon);
        if (stackType != null) {
            AmmoConfig.AmmoTypeDef def = GunSystemConfig.ammo().getAmmoType(stackType);
            if (def != null) return def;
        }
        // 2. 枪械默认
        if (GunSystemConfig.gun() != null) {
            String defaultAmmo = GunSystemConfig.gun().getDefaultAmmo(weapon.getType());
            if (defaultAmmo != null) {
                AmmoConfig.AmmoTypeDef def = GunSystemConfig.ammo().getAmmoType(defaultAmmo);
                if (def != null) return def;
            }
        }
        return null;
    }

    /** 向弹夹栈底压入 count 发指定弹种 */
    public static void pushAmmoToStack(ItemStack weapon, String ammoType, int count) {
        if (ammoType == null || count <= 0) return;
        StringBuilder sb = new StringBuilder();
        String existing = getStackPDC(weapon);
        if (existing != null && !existing.isEmpty()) {
            sb.append(existing).append(',');
        }
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(',');
            sb.append(ammoType);
        }
        setStackPDC(weapon, sb.toString());
    }

    /** 清空弹夹栈 */
    public static void clearAmmoStack(ItemStack weapon) {
        setStackPDC(weapon, null);
    }

    /** 获取栈内所有弹种列表（从左=底 到 右=顶） */
    public static String getAmmoStackRaw(ItemStack weapon) {
        return getStackPDC(weapon);
    }

    /** 从栈顶弹出一发，返回弹种ID */
    private static String popTopFromStack(ItemStack weapon) {
        String stack = getStackPDC(weapon);
        if (stack == null || stack.isEmpty()) return null;
        int lastComma = stack.lastIndexOf(',');
        String popped;
        String remaining;
        if (lastComma >= 0) {
            popped = stack.substring(lastComma + 1);
            remaining = stack.substring(0, lastComma);
        } else {
            popped = stack;
            remaining = null;
        }
        setStackPDC(weapon, remaining);
        return popped;
    }

    private static String getStackPDC(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY_MAG_STACK, PersistentDataType.STRING);
    }

    private static void setStackPDC(ItemStack item, String value) {
        if (item == null) return;
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta == null) return;
        if (value == null || value.isEmpty()) {
            meta.getPersistentDataContainer().remove(KEY_MAG_STACK);
        } else {
            meta.getPersistentDataContainer().set(KEY_MAG_STACK, PersistentDataType.STRING, value);
        }
        item.setItemMeta(meta);
    }

    /** 子弹是否归零 */
    public static boolean isEmpty(ItemStack weapon) {
        return getAmmo(weapon) <= 0;
    }

    /** 填充弹夹至满 */
    public static void fill(ItemStack weapon) {
        setAmmo(weapon, getCapacity(weapon));
    }

    /** 弹夹百分比 */
    public static double getAmmoPercent(ItemStack weapon) {
        int cap = getCapacity(weapon);
        if (cap <= 0) return 1.0;
        return (double) getAmmo(weapon) / cap;
    }

    /* ==================== 换弹 ==================== */

    /** 获取或创建换弹状态 */
    public static ReloadState getReloadState(Player player) {
        return reloadStates.computeIfAbsent(player.getUniqueId(), k -> new ReloadState());
    }

    /** 是否正在换弹 */
    public static boolean isReloading(Player player) {
        ReloadState state = reloadStates.get(player.getUniqueId());
        return state != null && state.reloading;
    }

    /** 开始换弹。返回 true = 换弹已启动，false = 无需换弹/弹夹满/无匹配弹药 */
    public static boolean startReload(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "magazine")) return false;
        if (isReloading(player)) return false;

        int current = getAmmo(weapon);
        int cap = getCapacity(weapon);

        // 弹夹满
        if (current >= cap) return false;

        // 检查口径匹配的弹药物品
        if (GunSystemConfig.isSystemEnabled(player, "ammo")) {
            String caliber = GunSystemConfig.gun().getCaliber(weapon.getType());
            if (caliber != null && !hasMatchingAmmo(player, caliber)) {
                player.sendActionBar(Component.text(
                    "弹药不足 (需要 " + caliber + ")", NamedTextColor.RED));
                return false;
            }
        }

        boolean isEmpty = current <= 0;
        double reloadTicks = isEmpty
            ? AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_RELOAD_EMPTY_TIME_TICKS)
            : AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_RELOAD_TIME_TICKS);

        // 枪膛战术换弹加成
        if (!isEmpty && ChamberManager.isEnabled(weapon)) {
            double bonus = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_CHAMBER_TACTICAL_RELOAD_BONUS);
            reloadTicks *= (1.0 - bonus / 100.0);
        }

        // 分段换弹
        double staged = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_RELOAD_STAGED);

        ReloadState state = getReloadState(player);
        state.reloading = true;
        state.originalWalkSpeed = player.getWalkSpeed();
        player.setWalkSpeed(state.originalWalkSpeed * 0.8f);
        cancelReloadTask(player);

        final double totalTicks = reloadTicks;
        final int finalStaged = (int) staged;

        state.reloadTaskId = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (state != reloadStates.get(player.getUniqueId())) {
                    cancel();
                    return;
                }
                tick++;

                // 分段就绪
                if (finalStaged > 0 && tick >= finalStaged && state.stagedAt == 0) {
                    state.stagedAt = tick;
                }

                // 换弹被打断
                if (tick < totalTicks && !isReloading(player)) {
                    // 若已过分段点，视为换弹成功
                    if (state.stagedAt > 0) {
                        finishReload(player, weapon, isEmpty);
                    } else {
                        // 换弹失败，回退
                        state.reloading = false;
                        state.reloadTaskId = -1;
                        state.stagedAt = 0;
                        player.setWalkSpeed(state.originalWalkSpeed);
                        player.sendActionBar(Component.text("换弹中断", NamedTextColor.GRAY));
                    }
                    cancel();
                    return;
                }

                // 播放音效（开始/完成）
                if (tick == 1) {
                    player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.5f, 1.8f);
                }

                if (tick >= totalTicks) {
                    finishReload(player, weapon, isEmpty);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId();

        return true;
    }

    private static void finishReload(Player player, ItemStack weapon, boolean wasEmpty) {
        String loadedAmmoType = null;

        // 检查背包弹药
        if (GunSystemConfig.isSystemEnabled(player, "ammo")) {
            String caliber = GunSystemConfig.gun().getCaliber(weapon.getType());
            if (caliber != null) {
                loadedAmmoType = consumeAmmoItem(player, caliber);
            }
        }

        // 若没拿到弹种，回退到枪械默认弹种
        if (loadedAmmoType == null && GunSystemConfig.ammo() != null && GunSystemConfig.gun() != null) {
            loadedAmmoType = GunSystemConfig.gun().getDefaultAmmo(weapon.getType());
        }

        int cap = getCapacity(weapon);
        int fillCount = cap;

        // 枪膛启用且非空仓换弹 → 弹夹容量-1（因为膛内还有1发）
        if (!wasEmpty && ChamberManager.isEnabled(weapon)) {
            fillCount = cap - 1;
            if (fillCount < 1) fillCount = cap;
        }

        setAmmo(weapon, fillCount);

        // 将装入的弹种压入弹夹栈底（倒序：后装的先发射）
        if (loadedAmmoType != null && fillCount > 0) {
            pushAmmoToStack(weapon, loadedAmmoType, fillCount);
        }

        ReloadState state = reloadStates.get(player.getUniqueId());
        if (state != null) {
            state.reloading = false;
            state.reloadTaskId = -1;
            state.stagedAt = 0;
            player.setWalkSpeed(state.originalWalkSpeed);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.4f, 1.2f);
        player.sendActionBar(Component.text(
            "装填完成 (" + fillCount + "/" + cap + ")", NamedTextColor.GREEN));
    }

    /** 打断当前换弹 */
    public static void cancelReload(Player player) {
        cancelReloadTask(player);
        ReloadState state = reloadStates.get(player.getUniqueId());
        if (state != null) {
            state.reloading = false;
            state.stagedAt = 0;
            player.setWalkSpeed(state.originalWalkSpeed);
        }
    }

    private static void cancelReloadTask(Player player) {
        ReloadState state = reloadStates.get(player.getUniqueId());
        if (state != null && state.reloadTaskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(state.reloadTaskId);
            state.reloadTaskId = -1;
        }
    }

    /** 移除玩家换弹状态 */
    public static void remove(Player player) {
        cancelReloadTask(player);
        reloadStates.remove(player.getUniqueId());
    }

    /* ==================== 弹药背包检查 ==================== */

    /** 扫描背包中有无匹配口径的弹药物品 */
    private static boolean hasMatchingAmmo(Player player, String caliber) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            String itemCaliber = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey("xh", "ammo_caliber"), PersistentDataType.STRING);
            if (caliber.equals(itemCaliber)) return true;
        }
        return false;
    }

    /** 消耗背包中第一个匹配口径的弹药，返回被消耗物品的弹种ID */
    private static String consumeAmmoItem(Player player, String caliber) {
        NamespacedKey ammoTypeKey = new NamespacedKey("xh", "ammo_type");
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            String itemCaliber = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey("xh", "ammo_caliber"), PersistentDataType.STRING);
            if (caliber.equals(itemCaliber)) {
                String ammoType = item.getItemMeta().getPersistentDataContainer()
                    .get(ammoTypeKey, PersistentDataType.STRING);
                item.setAmount(item.getAmount() - 1);
                return ammoType;
            }
        }
        return null;
    }

    /* ==================== PDC 工具 ==================== */

    private static Integer getPDC(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
    }

    private static void setPDC(ItemStack item, NamespacedKey key, int value) {
        if (item == null) return;
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta == null) {
            meta = org.bukkit.Bukkit.getItemFactory().getItemMeta(item.getType());
            if (meta == null) return;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
        item.setItemMeta(meta);
    }

    private static String getStringPDC(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private static void setStringPDC(ItemStack item, NamespacedKey key, String value) {
        if (item == null) return;
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta == null) {
            meta = org.bukkit.Bukkit.getItemFactory().getItemMeta(item.getType());
            if (meta == null) return;
        }
        if (value == null || value.isEmpty()) {
            meta.getPersistentDataContainer().remove(key);
        } else {
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        }
        item.setItemMeta(meta);
    }
}
