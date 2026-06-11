package io.github.liyughj.xH.rpg.Attribute;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * RPG 属性区间值 —— 支持 min~max 范围随机取值。
 * <p>
 * PDC 存储规范：
 * <ul>
 *   <li>单值（兼容旧格式）：存为 "item.&lt;key&gt;" = double</li>
 *   <li>区间（新格式）：存为 "item.&lt;key&gt;_min" + "item.&lt;key&gt;_max" = double</li>
 * </ul>
 * <p>
 * 字符串格式：
 * <ul>
 *   <li>"20~40" → min=20, max=40</li>
 *   <li>"80%~120%" → min=80, max=120（百分比型，尾部 % 自动剥离）</li>
 *   <li>"50" → min=50, max=50（单值，不随机）</li>
 * </ul>
 */
public class AttributeRange {

    public static final AttributeRange ZERO = new AttributeRange(0.0, 0.0);

    private final double min;
    private final double max;

    public AttributeRange(double min, double max) {
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
    }

    /** 单值区间（min==max，roll() 永远返回该值） */
    public static AttributeRange of(double value) {
        return new AttributeRange(value, value);
    }

    public double getMin() { return min; }
    public double getMax() { return max; }

    /** 是否为单值（不随机） */
    public boolean isSingleValue() { return min == max; }

    /** 随机取一个值 */
    public double roll() {
        if (min == max) return min;
        return min + ThreadLocalRandom.current().nextDouble() * (max - min);
    }

    @Override
    public String toString() {
        if (min == max) return formatNum(min);
        return formatNum(min) + "~" + formatNum(max);
    }

    private static String formatNum(double v) {
        return v == (long) v ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    /* ==================== 字符串解析 ==================== */

    /**
     * 解析属性值字符串。
     * <ul>
     *   <li>"20~40" → [20.0, 40.0]</li>
     *   <li>"80%~120%" → [80.0, 120.0]</li>
     *   <li>"50" → [50.0, 50.0]</li>
     * </ul>
     *
     * @param raw 原始字符串
     * @param attr 所属属性（用于 clamp）
     * @return 解析后的区间，确保值在 attr 的合法范围内
     */
    public static AttributeRange parse(String raw, RpgAttribute attr) {
        if (raw == null || raw.trim().isEmpty()) {
            return new AttributeRange(attr.getDefaultValue(), attr.getDefaultValue());
        }

        String s = raw.trim();

        /* 剥离百分比符号 */
        if (s.endsWith("%")) {
            s = s.substring(0, s.length() - 1).trim();
        }

        try {
            if (s.contains("~")) {
                String[] parts = s.split("~", 2);
                double v1 = Double.parseDouble(parts[0].trim());
                double v2 = Double.parseDouble(parts[1].trim());
                return new AttributeRange(attr.clamp(v1), attr.clamp(v2));
            } else {
                double v = Double.parseDouble(s);
                return new AttributeRange(attr.clamp(v), attr.clamp(v));
            }
        } catch (NumberFormatException e) {
            return new AttributeRange(attr.getDefaultValue(), attr.getDefaultValue());
        }
    }

    /* ==================== PDC 读写 ==================== */

    /**
     * 从 PDC 读取区间（兼容单值和区间两种格式）。
     *
     * @param pdc       物品或玩家的 PDC
     * @param singleKey 单值键（兼容旧格式）
     * @param minKey    区间最小值键
     * @param maxKey    区间最大值键
     * @param attr      所属属性
     */
    public static AttributeRange fromPdc(PersistentDataContainer pdc,
                                          NamespacedKey singleKey,
                                          NamespacedKey minKey,
                                          NamespacedKey maxKey,
                                          RpgAttribute attr) {
        Double minVal = pdc.get(minKey, PersistentDataType.DOUBLE);
        Double maxVal = pdc.get(maxKey, PersistentDataType.DOUBLE);

        if (minVal != null && maxVal != null) {
            return new AttributeRange(attr.clamp(minVal), attr.clamp(maxVal));
        }

        /* 回退到旧单值格式 */
        Double singleVal = pdc.get(singleKey, PersistentDataType.DOUBLE);
        if (singleVal != null) {
            return new AttributeRange(attr.clamp(singleVal), attr.clamp(singleVal));
        }

        return new AttributeRange(attr.getDefaultValue(), attr.getDefaultValue());
    }

    /**
     * 写入区间到 PDC。单值写两个键（min==max），区间也写两个键。
     */
    public void writeToPdc(PersistentDataContainer pdc,
                           NamespacedKey singleKey,
                           NamespacedKey minKey,
                           NamespacedKey maxKey) {
        pdc.set(minKey, PersistentDataType.DOUBLE, min);
        pdc.set(maxKey, PersistentDataType.DOUBLE, max);
        /* 同时写单值键以兼容旧读法 */
        pdc.set(singleKey, PersistentDataType.DOUBLE, (min + max) / 2.0);
    }

    /**
     * 移除 PDC 中的区间数据。
     */
    public static void removeFromPdc(PersistentDataContainer pdc,
                                     NamespacedKey singleKey,
                                     NamespacedKey minKey,
                                     NamespacedKey maxKey) {
        pdc.remove(singleKey);
        pdc.remove(minKey);
        pdc.remove(maxKey);
    }
}
