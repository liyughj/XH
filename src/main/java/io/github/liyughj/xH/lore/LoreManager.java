package io.github.liyughj.xH.lore;

import io.github.liyughj.xH.rpg.Attribute.AttributeRange;
import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lore 渲染引擎 —— 将配置模板 + 物品属性解析为 Component 列表。
 *
 * <h3>RpgAttribute 占位符</h3>
 * <ul>
 *   <li>{@code {value}}    → 属性格式化值（如 "1.5%", "30"）</li>
 *   <li>{@code {label}}    → 属性显示名（如 "暴击率"）</li>
 *   <li>{@code {attr_key}} → 替换为该属性对应的显示模板（在 lore.yml 中定义）</li>
 * </ul>
 *
 * <h3>自定义占位符（弹匣/弹药等无 RpgAttribute 物品）</h3>
 * <ul>
 *   <li>{@code {caliber}} / {@code {capacity}} / {@code {ammo_type}} 等</li>
 *   <li>由 {@link #buildRawLore(String, Map)} 的 placeholders Map 提供值</li>
 * </ul>
 *
 * <h3>特殊标签</h3>
 * <ul>
 *   <li>{@code {rpg}}   → RPG 字体起始</li>
 *   <li>{@code {reset}} → 格式重置</li>
 * </ul>
 *
 * <h3>颜色支持</h3>
 * 支持 {@code &0-&f, &k-&o} 标准颜色码，以及 {@code &#rrggbb} RGB 颜色。
 */
public final class LoreManager {

    private static LoreConfig config;

    /** RPG 字体前缀 */
    public static final String RPG_PREFIX = "\u00a75\u00a7o";

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([\\w.]+)}");

    private LoreManager() {}

    public static void init(LoreConfig cfg) {
        config = cfg;
    }

    /* ==================== 公开 API ==================== */

    /**
     * 为枪械物品生成 lore 行列表（从 gun 分类模板）。
     * 若 lore.yml 中未启用，返回空列表。
     */
    public static List<Component> buildGunLore(ItemStack weapon) {
        return buildModuleLore(weapon, "gun");
    }

    /**
     * 为RPG武器/道具生成 lore 行列表。
     */
    public static List<Component> buildWeaponLore(ItemStack item) {
        return buildModuleLore(item, "weapon");
    }

    /**
     * 为附魔物品生成附加 lore 行列表（在现有附魔经验行之后追加）。
     */
    public static List<Component> buildEnchantmentLore(ItemStack item) {
        return buildModuleLore(item, "enchantment");
    }

    /**
     * 为魔法道具生成 lore 行列表。
     */
    public static List<Component> buildMagicLore(ItemStack item) {
        return buildModuleLore(item, "magic");
    }

    /**
     * 为修仙道具生成 lore 行列表。
     */
    public static List<Component> buildXiuxianLore(ItemStack item) {
        return buildModuleLore(item, "xiuxian");
    }

    /**
     * 使用自定义占位符 Map 渲染 lore。适用于弹匣/弹药等无 RpgAttribute 的物品。
     *
     * @param category     lore.yml 中的分类名（如 "magazine", "ammo"）
     * @param placeholders key→value 映射，key 对应模板中的 {key}
     */
    public static List<Component> buildRawLore(String category, Map<String, String> placeholders) {
        if (config == null || !config.isEnabled()) return new ArrayList<>();
        LoreConfig.LoreModuleDef module = config.getModule(category);
        return buildRaw(module, placeholders);
    }

    /**
     * 弹匣物品 lore。
     */
    public static List<Component> buildMagazineLore(String caliber, int capacity) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put("caliber", caliber);
        map.put("capacity", String.valueOf(capacity));
        return buildRawLore("magazine", map);
    }

    /**
     * 弹药物品 lore。
     */
    public static List<Component> buildAmmoLore(String caliber, String ammoType,
                                                double damageMult, int penBonus,
                                                double spreadMult, double recoilMult,
                                                double speedMult) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put("caliber", caliber);
        map.put("ammo_type", ammoType);
        map.put("damage_mult", String.format("%.0f", damageMult * 100));
        map.put("penetration_bonus", penBonus > 0 ? "+" + penBonus : String.valueOf(penBonus));
        map.put("spread_mult", String.format("%.0f", spreadMult * 100));
        map.put("recoil_mult", String.format("%.0f", recoilMult * 100));
        map.put("speed_mult", String.format("%.0f", speedMult * 100));
        return buildRawLore("ammo", map);
    }

    /**
     * 按指定分类渲染物品 lore。
     */
    public static List<Component> buildModuleLore(ItemStack item, String category) {
        if (config == null || !config.isEnabled()) return new ArrayList<>();
        LoreConfig.LoreModuleDef module = config.getModule(category);
        return buildLore(item, module);
    }

    /**
     * 根据模板渲染单行文本（适用于 GUI / 自定义场景）。
     * 支持 {value} 占位符和 & 颜色码。
     *
     * @param template 模板文本（如 "&7后坐力: &c{value}"）
     * @param attr     要替换的属性（用于 {value}/{label}）
     * @param item     物品引用（用于读取 {attr_key} 引用的其他属性）
     */
    public static Component renderLine(String template, RpgAttribute attr, ItemStack item) {
        if (template == null || template.isEmpty()) return Component.empty();
        String resolved = resolvePlaceholders(template, attr, item);
        return deserialize(resolved).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * 渲染纯文本（无属性绑定，仅处理 & 颜色码）。
     */
    public static Component renderText(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    /* ==================== 内部构建 ==================== */

    private static List<Component> buildLore(ItemStack item, LoreConfig.LoreModuleDef module) {
        List<Component> lines = new ArrayList<>();
        if (item == null || module == null || module.order.isEmpty()) return lines;

        // header
        if (!module.header.isEmpty()) {
            lines.add(renderText(module.header));
        }

        for (String attrKey : module.order) {
            RpgAttribute attr = RpgAttribute.fromKey(attrKey);
            if (attr == null) continue;

            // 检查物品是否有此属性（兼容 min/max 区间 PDC 格式）
            AttributeRange range = AttributeStorage.getItemAttrRange(item, attr);
            if (range.getMin() == attr.getDefaultValue() && range.getMax() == attr.getDefaultValue())
                continue;

            String template = module.getTemplate(attrKey);
            if (template == null || template.isEmpty()) {
                // 无自定义模板，使用默认 "{label}: {value}"
                template = "&7" + attr.getDisplayName() + ": &f{value}";
            }

            Component line = renderLine(template, attr, item);
            lines.add(line);
        }

        // footer
        if (!module.footer.isEmpty()) {
            lines.add(Component.empty());
            lines.add(renderText(module.footer));
        }

        return lines;
    }

    /** 使用自定义占位符渲染 lore 行 */
    private static List<Component> buildRaw(LoreConfig.LoreModuleDef module, Map<String, String> placeholders) {
        List<Component> lines = new ArrayList<>();
        if (module == null || module.order.isEmpty()) return lines;

        if (!module.header.isEmpty()) {
            lines.add(renderText(module.header));
        }

        for (String key : module.order) {
            String template = module.getTemplate(key);
            if (template == null || template.isEmpty()) continue;
            String resolved = resolveRaw(template, placeholders);
            lines.add(deserialize(resolved).decoration(TextDecoration.ITALIC, false));
        }

        if (!module.footer.isEmpty()) {
            lines.add(Component.empty());
            lines.add(renderText(module.footer));
        }
        return lines;
    }

    /** 解析自定义占位符（非 RpgAttribute 绑定） */
    private static String resolveRaw(String template, Map<String, String> placeholders) {
        StringBuffer sb = new StringBuffer();
        Matcher m = PLACEHOLDER_PATTERN.matcher(template);
        while (m.find()) {
            String ph = m.group(1);
            String replacement;
            if ("rpg".equals(ph)) {
                replacement = RPG_PREFIX;
            } else if ("reset".equals(ph)) {
                replacement = "\u00a7r";
            } else {
                replacement = placeholders.getOrDefault(ph, "?" + ph);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return colorize(sb.toString());
    }

    /* ==================== 内部构建 (RpgAttribute) ==================== */

    /**
     * 解析模板中的所有占位符。
     * {value} → 属性格式化值
     * {label} → 属性显示名
     * {any_attr_key} → 解析为对应的显示模板（二次解析）
     */
    private static String resolvePlaceholders(String template, RpgAttribute currentAttr, ItemStack item) {
        if (template == null) return "";

        // 先解析 {any_key} 占位符（交叉引用）
        StringBuffer sb = new StringBuffer();
        Matcher m = PLACEHOLDER_PATTERN.matcher(template);
        while (m.find()) {
            String placeholder = m.group(1);
            String replacement;
            if ("value".equals(placeholder)) {
                replacement = formatAttrValue(item, currentAttr);
            } else if ("label".equals(placeholder)) {
                replacement = currentAttr.getDisplayName();
            } else if ("rpg".equals(placeholder)) {
                replacement = RPG_PREFIX;
            } else if ("reset".equals(placeholder)) {
                replacement = "\u00a7r";
            } else {
                // 尝试作为属性key解析：先从lore.yml模板取值，否则用format
                replacement = resolveAttrKey(placeholder, item);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);

        return colorize(sb.toString());
    }

    /** 解析一个属性key占位符 → 拿到它的显示模板后再次解析 */
    private static String resolveAttrKey(String attrKey, ItemStack item) {
        RpgAttribute attr = RpgAttribute.fromKey(attrKey);
        if (attr == null) return "&c?" + attrKey;

        // 是否有自定义模板？
        String template = config != null ? config.getModule(attr.getCategory().name().toLowerCase()).getTemplate(attrKey) : null;
        if (template == null || template.isEmpty()) {
            template = "&7" + attr.getDisplayName() + ": &f{value}";
        }
        return resolvePlaceholders(template, attr, item);
    }

    /** 读取物品属性值并格式化 */
    private static String formatAttrValue(ItemStack item, RpgAttribute attr) {
        AttributeRange range = AttributeStorage.getItemAttrRange(item, attr);
        if (range == null) return "0";

        double val = (range.getMin() + range.getMax()) / 2.0; // 区间取中值
        if (attr.isPercent()) {
            long rounded = Math.round(val);
            return rounded == val ? String.valueOf(rounded) + "%" : String.format("%.1f%%", val);
        }
        // FLAT
        long rounded = Math.round(val);
        return rounded == val ? String.valueOf(rounded) : String.format("%.1f", val);
    }

    /* ==================== 颜色处理 ==================== */

    /** 将 & 颜色码转换为 §，支持 &#rrggbb RGB 格式 */
    private static String colorize(String text) {
        if (text == null) return "";
        // &#rrggbb → §x§r§r§g§g§b§b
        Matcher hex = HEX_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (hex.find()) {
            String hexCode = hex.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hexCode.toCharArray()) {
                replacement.append('§').append(c);
            }
            hex.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
        }
        hex.appendTail(sb);

        // &0-&f, &k-&o 标准码
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    /** 带颜色码的文本 → Adventure Component */
    private static Component deserialize(String colored) {
        return LegacyComponentSerializer.legacySection().deserialize(colored);
    }
}
