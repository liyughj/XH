package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import io.github.liyughj.xH.lore.LoreManager;
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
        boolean interruptible = true; // 换弹是否可被切槽打断
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

    /** 获取当前发射弹种（从 consumeAmmo 暂存的 KEY_CURRENT_SHOT 读取） */
    public static String getCurrentShotType(ItemStack weapon) {
        return getStringPDC(weapon, KEY_CURRENT_SHOT);
    }

    /** 设置当前发射弹种标记（ChamberManager 消耗膛弹时调用） */
    public static void setCurrentShotType(ItemStack weapon, String ammoType) {
        setStringPDC(weapon, KEY_CURRENT_SHOT, ammoType);
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

    /** 获取下发射击应使用的弹种定义（栈顶 > 枪械默认，跳过 KEY_CURRENT_SHOT 避免读到旧值）。
     *  供弹药消费前使用（耐久/过热检查等），不依赖 KEY_CURRENT_SHOT 暂存。 */
    public static AmmoConfig.AmmoTypeDef peekNextAmmoTypeDef(ItemStack weapon) {
        if (GunSystemConfig.ammo() == null) return null;
        String stackType = peekNextAmmoType(weapon);
        if (stackType != null) {
            AmmoConfig.AmmoTypeDef def = GunSystemConfig.ammo().getAmmoType(stackType);
            if (def != null) return def;
        }
        if (GunSystemConfig.gun() != null) {
            String defaultAmmo = GunSystemConfig.gun().getDefaultAmmo(weapon.getType());
            if (defaultAmmo != null) {
                AmmoConfig.AmmoTypeDef def = GunSystemConfig.ammo().getAmmoType(defaultAmmo);
                if (def != null) return def;
            }
        }
        return null;
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

    /** 向弹夹栈顶压入 count 发指定弹种（后压入的先发射，LIFO） */
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
    static String popTopFromStack(ItemStack weapon) {
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

    /**
     * 截断弹夹栈至指定数量，保留栈顶（后装入=先发射）的 count 发。
     * 用于弹夹交换时原弹夹弹药数 > 武器容量的防御性处理。
     * 栈格式：左=底(先装填), 右=顶(后装填=先发射)
     */
    private static String trimStackToCount(String stackData, int count) {
        if (stackData == null || stackData.isEmpty() || count <= 0) return null;
        String[] parts = stackData.split(",");
        if (parts.length <= count) return stackData;
        // 保留末尾（栈顶）count 个
        StringBuilder sb = new StringBuilder();
        int start = parts.length - count;
        for (int i = start; i < parts.length; i++) {
            if (sb.length() > 0) sb.append(',');
            sb.append(parts[i].trim());
        }
        return sb.toString();
    }

    private static String getStackPDC(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY_MAG_STACK, PersistentDataType.STRING);
    }

    private static void setStackPDC(ItemStack item, String value) {
        if (item == null) return;
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta()
            : org.bukkit.Bukkit.getItemFactory().getItemMeta(item.getType());
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

    /** 开始换弹。返回 true = 换弹已启动，false = 无需换弹/弹夹满/无匹配弹药/换弹中 */
    public static boolean startReload(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "magazine")) return false;
        if (isReloading(player)) {
            player.sendActionBar(Component.text("正在换弹中...", NamedTextColor.YELLOW));
            return false;
        }

        // 换弹期间强制退出开镜、停止全自动
        AdsManager.forceExit(player);
        FireModeManager.stopAuto(player);

        int current = getAmmo(weapon);
        int cap = getCapacity(weapon);

        // 弹夹满
        if (current >= cap) {
            player.sendActionBar(Component.text("弹夹已满 (" + current + "/" + cap + ")", NamedTextColor.GREEN));
            return false;
        }

        // 检查口径匹配的弹药/弹夹物品
        if (GunSystemConfig.isSystemEnabled(player, "ammo")) {
            String caliber = GunSystemConfig.gun().getCaliber(weapon.getType());
            if (caliber != null && !hasMatchingAmmo(player, caliber) && !hasMatchingMagazine(player, caliber)) {
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

        // 读取换弹可中断标记
        boolean interruptible = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_RELOAD_INTERRUPTIBLE) >= 1.0;

        ReloadState state = getReloadState(player);
        state.reloading = true;
        state.originalWalkSpeed = player.getWalkSpeed();
        state.interruptible = interruptible;
        player.setWalkSpeed(state.originalWalkSpeed * 0.8f);
        cancelReloadTask(player);

        final double totalTicks = reloadTicks * getKillChainReloadFactor(weapon);
        final int finalStaged = (int) staged;

        // 解析换弹音效
        final Sound reloadSound = resolveReloadSound(weapon);

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
                    player.playSound(player.getLocation(), reloadSound, 0.5f, 1.8f);
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
        int magProvided = 0; // 从弹夹物品获得的弹药数
        String magazineStackData = null; // 弹夹交换时保留原始栈
        boolean hasCaliber = false;

        // 优先弹夹交换（消耗整个弹夹物品），其次散装弹药
        if (GunSystemConfig.isSystemEnabled(player, "ammo")) {
            String caliber = GunSystemConfig.gun().getCaliber(weapon.getType());
            if (caliber != null) {
                hasCaliber = true;
                MagAmmoResult magResult = consumeFromMagazine(player, caliber);
                if (magResult != null) {
                    loadedAmmoType = magResult.ammoType;
                    magProvided = magResult.count;
                    magazineStackData = magResult.stackData;
                }
                if (loadedAmmoType == null) {
                    loadedAmmoType = consumeAmmoItem(player, caliber);
                    if (loadedAmmoType != null) magProvided = 1;
                }
            }
        }

        // 若枪械有口径配置但没拿到弹药 → 换弹失败
        boolean ammoEnabled = GunSystemConfig.isSystemEnabled(player, "ammo");
        if (ammoEnabled && hasCaliber && magProvided == 0 && loadedAmmoType == null) {
            ReloadState state = reloadStates.get(player.getUniqueId());
            if (state != null) {
                state.reloading = false;
                state.reloadTaskId = -1;
                state.stagedAt = 0;
                player.setWalkSpeed(state.originalWalkSpeed);
            }
            player.sendActionBar(Component.text("弹药不足", NamedTextColor.RED));
            return;
        }

        // 若没拿到弹种，回退到枪械默认弹种
        if (loadedAmmoType == null && GunSystemConfig.ammo() != null && GunSystemConfig.gun() != null) {
            loadedAmmoType = GunSystemConfig.gun().getDefaultAmmo(weapon.getType());
        }

        int cap = getCapacity(weapon);
        int fillCount;

        if (magProvided > 0) {
            fillCount = Math.min(magProvided, cap);
        } else {
            // 弹药系统禁用时：补满
            fillCount = cap;
        }

        // 枪膛启用且非空仓换弹 → 弹夹容量-1
        if (!wasEmpty && ChamberManager.isEnabled(weapon) && fillCount == cap) {
            fillCount = cap - 1;
            if (fillCount < 1) fillCount = cap;
        }

        setAmmo(weapon, fillCount);

        // 清空旧弹夹栈，装入新弹药
        clearAmmoStack(weapon);
        if (loadedAmmoType != null && fillCount > 0) {
            if (magazineStackData != null && !magazineStackData.isEmpty()) {
                // 弹夹交换：复制弹夹栈并截断至 fillCount（避免原弹夹弹药数 > 武器容量时 stack/ammo 不同步）
                String trimmedStack = trimStackToCount(magazineStackData, fillCount);
                setStackPDC(weapon, trimmedStack);
            } else {
                // 散装弹药：压入统一弹种
                pushAmmoToStack(weapon, loadedAmmoType, fillCount);
            }
        }

        // 换弹完成后自动上膛（枪膛系统）
        ChamberManager.afterReload(weapon);

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
        LoreManager.refreshGunLore(weapon);
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

    /** 仅在换弹可中断时才打断（切槽时调用） */
    public static void cancelReloadIfInterruptible(Player player) {
        ReloadState state = reloadStates.get(player.getUniqueId());
        if (state == null || !state.reloading) return;
        if (state.interruptible) {
            cancelReload(player);
        }
    }

    private static void cancelReloadTask(Player player) {
        ReloadState state = reloadStates.get(player.getUniqueId());
        if (state != null && state.reloadTaskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(state.reloadTaskId);
            state.reloadTaskId = -1;
        }
    }

    /** 解析换弹音效：配置优先，无效则用默认 */
    private static Sound resolveReloadSound(ItemStack weapon) {
        if (GunSystemConfig.gun() == null) return Sound.BLOCK_LEVER_CLICK;
        String configured = GunSystemConfig.gun().getReloadSound(weapon.getType());
        if (configured != null) {
            Sound s = org.bukkit.Registry.SOUNDS.get(
                NamespacedKey.minecraft(configured.toLowerCase()));
            if (s != null) return s;
        }
        return Sound.BLOCK_LEVER_CLICK;
    }

    /** 移除玩家换弹状态 */
    public static void remove(Player player) {
        cancelReloadTask(player);
        reloadStates.remove(player.getUniqueId());
    }

    /* ==================== 击杀连锁 ==================== */

    /** 读取武器 PDC 中的击杀连锁换弹加速，过期则清除并返回 1.0 */
    private static double getKillChainReloadFactor(ItemStack weapon) {
        if (!weapon.hasItemMeta()) return 1.0;
        var pdc = weapon.getItemMeta().getPersistentDataContainer();
        Long expire = pdc.get(new NamespacedKey("xh", "kill_buff_expire"), PersistentDataType.LONG);
        if (expire == null || System.currentTimeMillis() > expire) {
            // 过期清理
            if (expire != null) clearKillChainPDC(weapon);
            return 1.0;
        }
        Double speed = pdc.get(new NamespacedKey("xh", "kill_reload_speed"), PersistentDataType.DOUBLE);
        return speed != null ? (1.0 - speed / 100.0) : 1.0;
    }

    /** 清除武器上的击杀连锁 PDC */
    private static void clearKillChainPDC(ItemStack weapon) {
        var meta = weapon.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        pdc.remove(new NamespacedKey("xh", "kill_reload_speed"));
        pdc.remove(new NamespacedKey("xh", "kill_damage_bonus"));
        pdc.remove(new NamespacedKey("xh", "kill_buff_expire"));
        weapon.setItemMeta(meta);
    }

    /** 获取击杀连锁伤害加成乘数（≥1.0），过期自动清除 */
    public static double getKillChainDamageFactor(ItemStack weapon) {
        if (weapon == null || !weapon.hasItemMeta()) return 1.0;
        var pdc = weapon.getItemMeta().getPersistentDataContainer();
        Long expire = pdc.get(new NamespacedKey("xh", "kill_buff_expire"), PersistentDataType.LONG);
        if (expire == null || System.currentTimeMillis() > expire) {
            if (expire != null) clearKillChainPDC(weapon);
            return 1.0;
        }
        Double bonus = pdc.get(new NamespacedKey("xh", "kill_damage_bonus"), PersistentDataType.DOUBLE);
        return bonus != null ? (1.0 + bonus / 100.0) : 1.0;
    }

    /** 击杀时尝试激活连锁buff：roll触发概率 → 写入武器PDC + 回血。返回true=已激活 */
    public static boolean tryApplyKillChainBuffs(Player player, ItemStack weapon) {
        if (weapon == null || !weapon.hasItemMeta() || GunSystemConfig.ammo() == null) return false;

        double triggerChance = io.github.liyughj.xH.rpg.Attribute.AttributeStorage.getAttrValue(weapon,
            io.github.liyughj.xH.rpg.Attribute.RpgAttribute.GUN_ON_KILL_TRIGGER_CHANCE);
        if (triggerChance <= 0) return false;
        if (Math.random() * 100.0 > triggerChance) return false;

        double reloadSpeed = io.github.liyughj.xH.rpg.Attribute.AttributeStorage.getAttrValue(weapon,
            io.github.liyughj.xH.rpg.Attribute.RpgAttribute.GUN_ON_KILL_RELOAD_SPEED);
        double damageBonus = io.github.liyughj.xH.rpg.Attribute.AttributeStorage.getAttrValue(weapon,
            io.github.liyughj.xH.rpg.Attribute.RpgAttribute.GUN_ON_KILL_DAMAGE_BONUS);
        double heal = io.github.liyughj.xH.rpg.Attribute.AttributeStorage.getAttrValue(weapon,
            io.github.liyughj.xH.rpg.Attribute.RpgAttribute.GUN_ON_KILL_HEAL);
        long buffTicks = (long) io.github.liyughj.xH.rpg.Attribute.AttributeStorage.getAttrValue(weapon,
            io.github.liyughj.xH.rpg.Attribute.RpgAttribute.GUN_ON_KILL_BUFF_TICKS);

        if (reloadSpeed <= 0 && damageBonus <= 0 && heal <= 0) return false;

        long expireMs = Math.max(1, buffTicks) * 50 + System.currentTimeMillis();

        var meta = weapon.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        if (reloadSpeed > 0)
            pdc.set(new NamespacedKey("xh", "kill_reload_speed"), PersistentDataType.DOUBLE, reloadSpeed);
        if (damageBonus > 0)
            pdc.set(new NamespacedKey("xh", "kill_damage_bonus"), PersistentDataType.DOUBLE, damageBonus);
        pdc.set(new NamespacedKey("xh", "kill_buff_expire"), PersistentDataType.LONG, expireMs);
        weapon.setItemMeta(meta);

        // 回血
        if (heal > 0) {
            double maxHp = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            player.setHealth(Math.min(player.getHealth() + heal, maxHp));
        }

        player.sendActionBar(net.kyori.adventure.text.Component.text(
            "§e☠ 击杀连锁激活! §7换弹+" + (int) reloadSpeed + "% 伤害+" + (int) damageBonus + "%",
            net.kyori.adventure.text.format.NamedTextColor.GOLD));
        LoreManager.refreshGunLore(weapon);
        return true;
    }

    /* ==================== 弹药背包检查 ==================== */

    private static final NamespacedKey KEY_GUN_ID = new NamespacedKey("xh", "gun_id");
    private static final NamespacedKey KEY_MAGAZINE_ID = new NamespacedKey("xh", "magazine_id");

    /** 扫描背包中有无匹配口径的弹药物品（排除枪械和弹夹） */
    private static boolean hasMatchingAmmo(Player player, String caliber) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            var pdc = item.getItemMeta().getPersistentDataContainer();
            if (pdc.has(KEY_GUN_ID, PersistentDataType.STRING)) continue;      // 排除枪械
            if (pdc.has(KEY_MAGAZINE_ID, PersistentDataType.STRING)) continue; // 排除弹夹
            String itemCaliber = pdc.get(new NamespacedKey("xh", "ammo_caliber"), PersistentDataType.STRING);
            if (caliber.equals(itemCaliber)) return true;
        }
        return false;
    }

    /** 消耗背包中第一个匹配口径的弹药，返回被消耗物品的弹种ID（排除枪械和弹夹） */
    private static String consumeAmmoItem(Player player, String caliber) {
        NamespacedKey ammoTypeKey = new NamespacedKey("xh", "ammo_type");
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            var pdc = item.getItemMeta().getPersistentDataContainer();
            if (pdc.has(KEY_GUN_ID, PersistentDataType.STRING)) continue;      // 排除枪械
            if (pdc.has(KEY_MAGAZINE_ID, PersistentDataType.STRING)) continue; // 排除弹夹
            String itemCaliber = pdc.get(new NamespacedKey("xh", "ammo_caliber"), PersistentDataType.STRING);
            if (caliber.equals(itemCaliber)) {
                String ammoType = pdc.get(ammoTypeKey, PersistentDataType.STRING);
                item.setAmount(item.getAmount() - 1);
                return ammoType;
            }
        }
        return null;
    }

    /** 扫描背包中有无匹配口径且非空的弹夹物品 */
    private static boolean hasMatchingMagazine(Player player, String caliber) {
        NamespacedKey magCaliberKey = new NamespacedKey("xh", "mag_caliber");
        NamespacedKey magAmmoKey = new NamespacedKey("xh", "mag_ammo");
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            var pdc = item.getItemMeta().getPersistentDataContainer();
            if (!pdc.has(KEY_MAGAZINE_ID, PersistentDataType.STRING)) continue;
            String magCaliber = pdc.get(magCaliberKey, PersistentDataType.STRING);
            if (!caliber.equals(magCaliber)) continue;
            Integer ammo = pdc.get(magAmmoKey, PersistentDataType.INTEGER);
            if (ammo != null && ammo > 0) return true;
        }
        return false;
    }

    /** 从弹夹物品消耗全部弹药，返回弹种和数量 */
    private record MagAmmoResult(String ammoType, int count, String stackData) {}

    /** 从弹夹物品中消耗全部子弹，返回弹种ID和消耗数量 */
    /** 弹夹交换：消耗背包中一个满/非空弹夹物品，返回其弹药数据（同时移除该物品） */
    private static MagAmmoResult consumeFromMagazine(Player player, String caliber) {
        NamespacedKey magCaliberKey = new NamespacedKey("xh", "mag_caliber");
        NamespacedKey magAmmoKey = new NamespacedKey("xh", "mag_ammo");
        NamespacedKey magStackKey = new NamespacedKey("xh", "mag_ammo_stack");
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            var pdc = item.getItemMeta().getPersistentDataContainer();
            if (!pdc.has(KEY_MAGAZINE_ID, PersistentDataType.STRING)) continue;
            String magCaliber = pdc.get(magCaliberKey, PersistentDataType.STRING);
            if (!caliber.equals(magCaliber)) continue;
            Integer ammo = pdc.get(magAmmoKey, PersistentDataType.INTEGER);
            if (ammo == null || ammo <= 0) continue;

            // 读弹夹栈，取栈顶弹种
            String rawStack = pdc.get(magStackKey, PersistentDataType.STRING);
            String ammoType = null;
            if (rawStack != null && !rawStack.isEmpty()) {
                String[] parts = rawStack.split(",");
                ammoType = parts[parts.length - 1].trim();
            }

            // 消耗弹夹物品（整组移除，不是清空 PDC）
            item.setAmount(item.getAmount() - 1);

            if (ammoType == null || ammoType.isEmpty()) {
                if (GunSystemConfig.ammo() != null) {
                    ammoType = GunSystemConfig.ammo().getDefaultAmmoType(caliber);
                }
            }
            return new MagAmmoResult(ammoType, ammo, rawStack);
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
