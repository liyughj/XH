package io.github.liyughj.xH.debug;

import io.github.liyughj.xH.rpg.Attribute.AttributeRange;
import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 调试管理器 —— /xh debug 开关，输出到控制台 + 聊天栏。
 * <p>
 * 使用方式：
 * <ul>
 *   <li>玩家执行 /xh debug 切换开关（OP 权限）</li>
 *   <li>枪械射击时输出当前武器所有 GUN 属性值</li>
 *   <li>附魔触发时输出附魔效果详情</li>
 *   <li>RPG 属性生效时输出暴击/吸血/穿透/破甲等判定结果</li>
 * </ul>
 */
public class DebugManager {

    private static final Set<UUID> enabled = new HashSet<>();
    private static final String PREFIX = "§b[Debug] §f";
    private static final String CONSOLE_PREFIX = "[XH Debug] ";

    /** 切换玩家调试状态 */
    public static boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (enabled.contains(uuid)) {
            enabled.remove(uuid);
            player.sendMessage(PREFIX + "§c调试模式已关闭");
            log(player, "调试模式已关闭");
            return false;
        } else {
            enabled.add(uuid);
            player.sendMessage(PREFIX + "§a调试模式已开启");
            log(player, "调试模式已开启");
            return true;
        }
    }

    /** 检查玩家是否开启了调试 */
    public static boolean isEnabled(Player player) {
        return enabled.contains(player.getUniqueId());
    }

    /** 调试输出：同时发到玩家聊天栏和控制台 */
    public static void debug(Player player, String msg) {
        if (!isEnabled(player)) return;
        player.sendMessage(PREFIX + msg);
        log(player, stripColor(msg));
    }

    /** 调试输出（仅控制台，不发聊天栏） */
    public static void debugConsole(Player player, String msg) {
        if (!isEnabled(player)) return;
        log(player, stripColor(msg));
    }

    /** 控制台日志 */
    private static void log(Player player, String msg) {
        Bukkit.getConsoleSender().sendMessage(CONSOLE_PREFIX + "[" + player.getName() + "] " + msg);
    }

    /** 去除颜色代码 */
    private static String stripColor(String msg) {
        return msg.replaceAll("§[0-9a-fk-or]", "");
    }

    /* ==================== 枪械属性输出 ==================== */

    /**
     * 输出当前枪械上所有 GUN 类别属性的配置区间/值（不重新roll，避免与实际伤害不一致）。
     * 区间属性显示 min~max，固定值属性显示原值。
     * 在射击时调用。
     */
    public static void debugGunAttributes(Player player, ItemStack weapon, String weaponType) {
        if (!isEnabled(player)) return;

        StringBuilder sb = new StringBuilder();
        sb.append("§6===== 枪械属性 §e[").append(weaponType).append("] §6=====");

        for (RpgAttribute attr : RpgAttribute.values()) {
            if (attr.getCategory() != RpgAttribute.Category.GUN) continue;
            AttributeRange range = AttributeStorage.getItemAttrRange(weapon, attr);
            double min = range.getMin();
            double max = range.getMax();

            // 跳过默认值（min==max==默认，说明未被配置）
            if (min == attr.getDefaultValue() && max == attr.getDefaultValue()) continue;

            String label = attr.getDisplayName();
            String valueStr;
            if (min != max) {
                // 区间属性：显示 min~max
                valueStr = attr.isPercent()
                    ? String.format("§e%.1f%%~%.1f%%", min, max)
                    : String.format("§e%.1f~%.1f", min, max);
            } else {
                // 固定值
                valueStr = attr.isPercent()
                    ? String.format("§f%.1f%%", min)
                    : String.format("§f%.1f", min);
            }
            sb.append("\n§7  ").append(label).append(": ").append(valueStr);
        }

        // 额外：弹夹状态
        int magAmmo = io.github.liyughj.xH.gun.MagazineManager.getAmmo(weapon);
        int magCap = io.github.liyughj.xH.gun.MagazineManager.getCapacity(weapon);
        sb.append("\n§7  弹夹子弹: §f").append(magAmmo).append("/").append(magCap);

        // 枪膛状态
        boolean chamberEnabled = io.github.liyughj.xH.gun.ChamberManager.isEnabled(weapon);
        if (chamberEnabled) {
            boolean loaded = io.github.liyughj.xH.gun.ChamberManager.isChamberLoaded(weapon);
            sb.append("\n§7  枪膛装填: §f").append(loaded ? "§a有弹" : "§c空");
        }

        player.sendMessage(sb.toString());
        log(player, stripColor(sb.toString()));
    }

    /* ==================== 附魔效果输出 ==================== */

    /**
     * 输出附魔伤害加成信息
     */
    public static void debugEnchantDamage(Player player, String enchantName, int level, double percent, double multiplier) {
        if (!isEnabled(player)) return;
        String msg = String.format("§d[附魔] §e%s §7Lv.%d §f→ 伤害倍率 §e+%.1f%% §7(×%.2f)",
            enchantName, level, percent * level, multiplier);
        debug(player, msg);
    }

    /**
     * 输出附魔火焰效果
     */
    public static void debugEnchantFire(Player player, String enchantName, int level, int ticks) {
        if (!isEnabled(player)) return;
        String msg = String.format("§d[附魔] §e%s §7Lv.%d §f→ 点燃 §c%d ticks",
            enchantName, level, ticks);
        debug(player, msg);
    }

    /**
     * 输出附魔击退效果
     */
    public static void debugEnchantKnockback(Player player, String enchantName, int level, double strength) {
        if (!isEnabled(player)) return;
        String msg = String.format("§d[附魔] §e%s §7Lv.%d §f→ 击退 §e%.1f 格",
            enchantName, level, strength);
        debug(player, msg);
    }

    /**
     * 输出附魔通用效果
     */
    public static void debugEnchantEffect(Player player, String enchantName, int level, String effect) {
        if (!isEnabled(player)) return;
        String msg = String.format("§d[附魔] §e%s §7Lv.%d §f→ %s",
            enchantName, level, effect);
        debug(player, msg);
    }

    /* ==================== RPG 属性效果输出 ==================== */

    /**
     * 输出暴击判定结果
     * @param critChanceMin 暴击率区间下界
     * @param critChanceMax 暴击率区间上界
     * @param critMultMin 暴击倍率区间下界
     * @param critMultMax 暴击倍率区间上界
     */
    public static void debugCrit(Player player,
            double critChanceMin, double critChanceMax,
            double critMultMin, double critMultMax,
            double before, double after) {
        if (!isEnabled(player)) return;
        String chanceStr = critChanceMin != critChanceMax
            ? String.format("%.1f%%~%.1f%%", critChanceMin, critChanceMax)
            : String.format("%.1f%%", critChanceMin);
        String multStr = critMultMin != critMultMax
            ? String.format("%.1f%%~%.1f%%", critMultMin, critMultMax)
            : String.format("%.1f%%", critMultMin);
        String msg = String.format("§c[RPG暴击] §7概率区间=§e%s §7倍率区间=§e%s §7伤害: §f%.1f → §c%.1f",
            chanceStr, multStr, before, after);
        debug(player, msg);
    }

    /**
     * 输出吸血判定结果
     */
    public static void debugLifesteal(Player player, double chanceMin, double chanceMax, double steal, double flat, double drain, double totalHeal, double extraDmg) {
        if (!isEnabled(player)) return;
        StringBuilder sb = new StringBuilder();
        String chanceStr = chanceMin != chanceMax
            ? String.format("%.1f%%~%.1f%%", chanceMin, chanceMax)
            : String.format("%.1f%%", chanceMin);
        sb.append(String.format("§a[RPG吸血] §7概率区间=§e%s", chanceStr));
        if (steal > 0) sb.append(String.format(" §7偷取=§e%.1f", steal));
        if (flat > 0) sb.append(String.format(" §7固定=§e%.1f", flat));
        if (drain > 0) sb.append(String.format(" §7汲取=§e%.1f", drain));
        sb.append(String.format(" §7总回复=§a%.1f", totalHeal));
        if (extraDmg > 0) sb.append(String.format(" §7额外伤害=§c%.1f", extraDmg));
        debug(player, sb.toString());
    }

    /**
     * 输出穿透判定结果
     */
    public static void debugPenetration(Player player, double lowPenMin, double lowPenMax,
            double highPenMin, double highPenMax, double effMin, double effMax,
            double extraDmg, double remainingPct) {
        if (!isEnabled(player)) return;
        String lowStr = lowPenMin != lowPenMax ? String.format("%.1f%%~%.1f%%", lowPenMin, lowPenMax) : String.format("%.1f%%", lowPenMin);
        String highStr = highPenMin != highPenMax ? String.format("%.1f%%~%.1f%%", highPenMin, highPenMax) : String.format("%.1f%%", highPenMin);
        String effStr = effMin != effMax ? String.format("%.1f%%~%.1f%%", effMin, effMax) : String.format("%.1f%%", effMin);
        String msg = String.format("§9[RPG穿透] §7低穿=§e%s §7高穿=§e%s §7效能=§e%s §7额外伤害=§c%.1f §7残余减免=§e%.1f%%",
            lowStr, highStr, effStr, extraDmg, remainingPct * 100);
        debug(player, msg);
    }

    /**
     * 输出破甲判定结果
     */
    public static void debugArmorBreak(Player player, double chanceMin, double chanceMax, double mult, String level) {
        if (!isEnabled(player)) return;
        String chanceStr = chanceMin != chanceMax
            ? String.format("%.1f%%~%.1f%%", chanceMin, chanceMax)
            : String.format("%.1f%%", chanceMin);
        String msg = String.format("§6[RPG破甲] §7概率区间=§e%s §7倍率=§e%.2f §7程度=§c%s",
            chanceStr, mult, level);
        debug(player, msg);
    }

    /**
     * 输出命中特效（减速/硬直/致盲）
     */
    public static void debugHitEffect(Player player, String effect, double chance, String detail) {
        if (!isEnabled(player)) return;
        String msg = String.format("§e[RPG命中] §f%s §7概率=§e%.1f%% §7→ %s",
            effect, chance, detail);
        debug(player, msg);
    }

    /**
     * 输出流血效果
     */
    public static void debugBleed(Player player, double damage, int ticks) {
        if (!isEnabled(player)) return;
        String msg = String.format("§4[RPG流血] §7伤害=§c%.1f/tick §7持续=§e%d ticks",
            damage, ticks);
        debug(player, msg);
    }

    /**
     * 输出闪避/致盲判定
     */
    public static void debugDodge(Player player, String target, double chance, boolean dodged) {
        if (!isEnabled(player)) return;
        String msg = String.format("§8[RPG闪避] §7目标=§f%s §7概率=§e%.1f%% §7结果=§e%s",
            target, chance, dodged ? "§c闪避成功" : "§a命中");
        debug(player, msg);
    }
}
