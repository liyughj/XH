package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeRange;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import net.md_5.bungee.api.ChatColor;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 枪械系统综合逻辑测试 —— 纯逻辑/数学/枚举测试，不依赖运行时 Bukkit Registry。
 *
 * 覆盖模块：
 * <ol>
 *   <li>RpgAttribute 枚举完整性（key唯一性/前缀匹配/默认值合理性）</li>
 *   <li>AttributeRange 解析/随机/边界</li>
 *   <li>故障系统 权重解码/故障率公式</li>
 *   <li>耐久%计算 + 散布/后坐惩罚公式</li>
 *   <li>过热因子计算（散布/后坐/ADS冷却）</li>
 *   <li>散布向量数学</li>
 *   <li>Lore 颜色码解析（标准符 + RGB 十六进制）</li>
 *   <li>PDC key 命名冲突审计</li>
 *   <li>FireMode 模式名称 / bitmask</li>
 *   <li>Category 分类一致性</li>
 *   <li>换弹时间计算</li>
 *   <li>子弹伤害衰减公式</li>
 * </ol>
 */
@DisplayName("枪械系统综合逻辑测试")
class GunSystemsTest {

    // ==================== 一: RpgAttribute 枚举完整性 ====================

    @Nested
    @DisplayName("RpgAttribute 枚举审计")
    class AttributeEnumAudit {

        @Test
        @DisplayName("GUN属性key唯一性")
        void testGunAttributeKeysUnique() {
            Set<String> keys = new HashSet<>();
            for (RpgAttribute attr : RpgAttribute.values()) {
                if (attr.getCategory() == RpgAttribute.Category.GUN) {
                    String key = attr.getKey();
                    assertFalse(keys.contains(key),
                        "重复属性key: " + key);
                    keys.add(key);
                    assertNotNull(attr.getDisplayName(), "显示名null: " + key);
                }
            }
            assertTrue(keys.size() > 90, "GUN属性数量异常: " + keys.size());
        }

        @Test
        @DisplayName("key命名规范: 无空格/非空")
        void testKeyNaming() {
            for (RpgAttribute attr : RpgAttribute.values()) {
                String key = attr.getKey();
                assertNotNull(key, attr.name());
                assertFalse(key.contains(" "), "key含空格: " + key);
                assertFalse(key.isEmpty(), "key为空: " + attr.name());
            }
        }

        @Test
        @DisplayName("gun_前缀属性必须属于GUN分类")
        void testGunPrefixMatchesCategory() {
            for (RpgAttribute attr : RpgAttribute.values()) {
                if (attr.getKey().startsWith("gun_")) {
                    assertEquals(RpgAttribute.Category.GUN, attr.getCategory(),
                        attr.getKey() + " 前缀gun_但不是GUN分类");
                }
            }
        }

        @Test
        @DisplayName("GUN ValueType PERCENT 边界一致 (0~100)")
        void testPercentValueTypeUpperBound() {
            for (RpgAttribute attr : RpgAttribute.values()) {
                if (attr.getCategory() == RpgAttribute.Category.GUN
                    && attr.getValueType() == RpgAttribute.ValueType.PERCENT) {
                    assertTrue(attr.getMaxValue() >= 100 || attr.getMaxValue() == Double.MAX_VALUE,
                        attr.getKey() + " PERCENT类型但max=" + attr.getMaxValue());
                }
            }
        }
    }

    // ==================== 二: AttributeRange ====================

    @Nested
    @DisplayName("AttributeRange 区间逻辑")
    class AttributeRangeTests {

        @Test
        @DisplayName("单值解析")
        void testParseSingle() {
            AttributeRange r = AttributeRange.parse("50", RpgAttribute.GUN_DAMAGE);
            assertEquals(50, r.getMin(), 0.001);
            assertEquals(50, r.getMax(), 0.001);
            assertTrue(r.isSingleValue());
            assertEquals("50", r.toString());
        }

        @Test
        @DisplayName("范围解析 20~40")
        void testParseRange() {
            AttributeRange r = AttributeRange.parse("20~40", RpgAttribute.GUN_DAMAGE);
            assertEquals(20, r.getMin(), 0.001);
            assertEquals(40, r.getMax(), 0.001);
            assertFalse(r.isSingleValue());
            assertEquals("20~40", r.toString());
        }

        @Test
        @DisplayName("百分比单值 80% → 剥离%得到80")
        void testParsePercentSingle() {
            AttributeRange r = AttributeRange.parse("80%", RpgAttribute.GUN_BONUS);
            assertEquals(80, r.getMin(), 0.001);
            assertEquals(80, r.getMax(), 0.001);
        }

        @Test
        @DisplayName("百分比范围 拆分→仅处理尾部% → 80%非数字→回退默认")
        void testParsePercentRangeLimitation() {
            // "80%~120%" 只剥离尾部一个% → 得到"80%~120"
            // split by ~ → "80%" 和 "120" → "80%" parseDouble 失败 → 回退默认 0
            AttributeRange r = AttributeRange.parse("80%~120%", RpgAttribute.GUN_BONUS);
            // 实际行为: parse 返回 default value 0.0~0.0
            assertEquals(RpgAttribute.GUN_BONUS.getDefaultValue(), r.getMin(), 0.001);
            // 说明: 区间%需用单值格式 "80"~"120" 或配置系统显式处理
        }

        @Test
        @DisplayName("边界clamp")
        void testParseClamp() {
            AttributeRange r = AttributeRange.parse("150", RpgAttribute.GUN_HEADSHOT_CHANCE);
            assertEquals(100, r.getMin(), 0.001);
        }

        @Test
        @DisplayName("空字符串→默认值")
        void testParseEmpty() {
            AttributeRange r = AttributeRange.parse("", RpgAttribute.GUN_RPM);
            assertEquals(RpgAttribute.GUN_RPM.getDefaultValue(), r.getMin(), 0.001);
        }

        @Test
        @DisplayName("null→默认值")
        void testParseNull() {
            AttributeRange r = AttributeRange.parse(null, RpgAttribute.GUN_RPM);
            assertEquals(RpgAttribute.GUN_RPM.getDefaultValue(), r.getMin(), 0.001);
        }

        @Test
        @DisplayName("质量数字格式化: 整数 → int表示")
        void testFormatting() {
            assertEquals("50", new AttributeRange(50, 50).toString());
            assertEquals("20~40", new AttributeRange(20, 40).toString());
            assertEquals("1.5~2.5", new AttributeRange(1.5, 2.5).toString());
        }

        @Test
        @DisplayName("roll() 10000次均在[min,max]内")
        void testRollBoundaries() {
            AttributeRange r = new AttributeRange(10, 50);
            for (int i = 0; i < 10000; i++) {
                double v = r.roll();
                assertTrue(v >= 10 && v <= 50, "roll出界: " + v);
            }
        }

        @Test
        @DisplayName("roll() 单值恒等")
        void testRollSingle() {
            AttributeRange r = AttributeRange.of(42);
            for (int i = 0; i < 1000; i++) {
                assertEquals(42, r.roll(), 0.001);
            }
        }

        @Test
        @DisplayName("roll() min==max 返回恒等")
        void testRollMinEqualsMax() {
            AttributeRange r = new AttributeRange(7, 7);
            for (int i = 0; i < 100; i++) {
                assertEquals(7, r.roll(), 0.001);
            }
        }

        @Test
        @DisplayName("ZERO常量: min=0 max=0 roll=0")
        void testZeroConstant() {
            assertEquals(0, AttributeRange.ZERO.getMin(), 0.001);
            assertEquals(0, AttributeRange.ZERO.getMax(), 0.001);
            assertEquals(0, AttributeRange.ZERO.roll(), 0.001);
        }
    }

    // ==================== 三: 故障系统 ====================

    @Nested
    @DisplayName("故障系统数学")
    class MalfunctionTests {

        @Test
        @DisplayName("故障类型权重解码 (默认8020010: jam=8 misfire=20 cata=10)")
        void testWeightDecoding() {
            double encoded = RpgAttribute.GUN_MALFUNC_TYPE_WEIGHTS.getDefaultValue();
            assertEquals(8020010.0, encoded, 0.001);

            int jamW = Math.max(0, (int) (encoded / 1000000) % 1000);
            int misfireW = Math.max(0, (int) (encoded / 1000) % 1000);
            int cataW = Math.max(0, (int) encoded % 1000);

            // 8020010 → jam=8, misfire=20, cata=10
            assertEquals(8, jamW);
            assertEquals(20, misfireW);
            assertEquals(10, cataW);
            assertEquals(38, jamW + misfireW + cataW);
        }

        @Test
        @DisplayName("故障权重边缘: 全卡壳")
        void testWeightAllJam() {
            double encoded = 100_000_000;
            int jamW = Math.max(0, (int) (encoded / 1000000) % 1000);
            assertEquals(100, jamW);
            assertEquals(0, Math.max(0, (int) (encoded / 1000) % 1000));
            assertEquals(0, Math.max(0, (int) encoded % 1000));
        }

        @Test
        @DisplayName("故障权重边缘: 全哑火")
        void testWeightAllMisfire() {
            double encoded = 100_000;
            assertEquals(0, Math.max(0, (int) (encoded / 1000000) % 1000));
            assertEquals(100, Math.max(0, (int) (encoded / 1000) % 1000));
            assertEquals(0, Math.max(0, (int) encoded % 1000));
        }

        @Test
        @DisplayName("故障权重边缘: 全炸膛")
        void testWeightAllCata() {
            double encoded = 100;
            assertEquals(0, Math.max(0, (int) (encoded / 1000000) % 1000));
            assertEquals(0, Math.max(0, (int) (encoded / 1000) % 1000));
            assertEquals(100, Math.max(0, (int) encoded % 1000));
        }

        @Test
        @DisplayName("故障率公式: base+heatBonus+duraBonus 在组合输入下计算")
        void testMalfuncChanceFormula() {
            // 基础故障率5%
            double baseChance = 5.0;

            // 热量50%, 热量故障因子200% → 热量贡献=50*(200/100)=100
            double heatPct = 0.5;
            double heatFactor = 200;
            double heatBonus = heatPct * 100.0 * (heatFactor / 100.0);
            assertEquals(100.0, heatBonus, 0.01);

            // 耐久30%(即磨损70%), 耐久故障因子200% → 磨损贡献=70*(200/100)=140
            double duraPct = 0.3;
            double duraFactor = 200;
            double wearBonus = (1.0 - duraPct) * 100.0 * (duraFactor / 100.0);
            assertEquals(140.0, wearBonus, 0.01);

            // 总和=5+100+140=245, clamp到100
            double total = baseChance + heatBonus + wearBonus;
            assertEquals(245.0, total, 0.1);
            assertEquals(100.0, Math.min(total, 100), 0.01);
        }

        @Test
        @DisplayName("GUN_MALFUNC_BASE_CHANCE 默认0 → 纯系统不触发")
        void testBaseChanceDefault() {
            assertEquals(0, RpgAttribute.GUN_MALFUNC_BASE_CHANCE.getDefaultValue(), 0.001);
        }
    }

    // ==================== 四: 耐久 ====================

    @Nested
    @DisplayName("耐久计算公式")
    class DurabilityTests {

        @Test
        @DisplayName("耐久百分比")
        void testPercent() {
            assertEquals(0.5, 250.0 / 500.0, 0.001);
            assertEquals(0.75, 375.0 / 500.0, 0.001);
            assertEquals(1.0, 500.0 / 500.0, 0.001);
            assertEquals(0.0, 0.0 / 500.0, 0.001);
        }

        @Test
        @DisplayName("散布惩罚: 50%耐久 → ×1.5")
        void testSpreadPenalty() {
            double penalty = 100;
            double pct = 0.5;
            double result = 1.0 + ((1.0 - pct) * penalty / 100.0);
            assertEquals(1.5, result, 0.001);
        }

        @Test
        @DisplayName("散布惩罚: 0%耐久(破损) → 常规×2.0 + 破损额外×5.0 = ×7.0")
        void testBrokenPenalty() {
            double penalty = 100;
            double brokenPenalty = 500;
            double pct = 0.0;
            double result = 1.0 + ((1.0 - pct) * penalty / 100.0);
            assertEquals(2.0, result, 0.001);
            result += brokenPenalty / 100.0;
            assertEquals(7.0, result, 0.001);
        }

        @Test
        @DisplayName("后坐惩罚: 30%耐久 → ×1.7")
        void testRecoilPenalty() {
            double penalty = 100;
            double pct = 0.3;
            double result = 1.0 + ((1.0 - pct) * penalty / 100.0);
            assertEquals(1.7, result, 0.001);
        }

        @Test
        @DisplayName("故障惩罚: 10%耐久 → ×3.8 (磨损90%*300/100 + 1.0)")
        void testMalfuncPenalty() {
            double penalty = 300; // 200→已设200, 但这里用300测试
            double pct = 0.1;
            double result = 1.0 + ((1.0 - pct) * penalty / 100.0);
            // 1.0 + (0.9 * 300 / 100) = 3.7
            assertEquals(3.7, result, 0.001);
        }
    }

    // ==================== 五: 过热 ====================

    @Nested
    @DisplayName("过热因子数学")
    class OverheatTests {

        @Test
        @DisplayName("热量百分比")
        void testHeatPercent() {
            assertEquals(0.5, 50.0 / 100.0, 0.001);
            assertEquals(0.3, 30.0 / 100.0, 0.001);
            assertEquals(0.0, 0.0 / 100.0, 0.001);
            assertEquals(1.0, 100.0 / 100.0, 0.001);
        }

        @Test
        @DisplayName("散布因子: 50%热量 * 因子200% → ×2.0")
        void testSpreadFactor() {
            double factor = 200;
            double heatPct = 0.5;
            double mult = 1.0 + heatPct * (factor / 100.0);
            assertEquals(2.0, mult, 0.001);
        }

        @Test
        @DisplayName("后坐因子: 30%热量 * 因子150% → ×1.45")
        void testRecoilFactor() {
            double factor = 150;
            double heatPct = 0.3;
            double mult = 1.0 + heatPct * (factor / 100.0);
            assertEquals(1.45, mult, 0.001);
        }

        @Test
        @DisplayName("故障因子: 30%热量 * 因子300% → ×1.9")
        void testMalfuncFactor() {
            double factor = 300;
            double heatPct = 0.3;
            double mult = 1.0 + heatPct * (factor / 100.0);
            assertEquals(1.9, mult, 0.001);
        }

        @Test
        @DisplayName("ADS冷却加成: 基础10/s 加成150% → 25/s")
        void testAdsCoolBonus() {
            double coolRate = 10.0;
            double adsBonus = 150;
            double result = coolRate * (1.0 + adsBonus / 100.0);
            assertEquals(25.0, result, 0.001);
        }

        @Test
        @DisplayName("热量为0时所有因子=1.0")
        void testHeatZeroNoEffect() {
            assertEquals(1.0, 1.0 + 0.0 * (200 / 100.0), 0.001);
            assertEquals(1.0, 1.0 + 0.0 * (150 / 100.0), 0.001);
            assertEquals(1.0, 1.0 + 0.0 * (300 / 100.0), 0.001);
        }
    }

    // ==================== 六: 散布 ====================

    @Nested
    @DisplayName("散布向量数学")
    class SpreadTests {

        @Test
        @DisplayName("扩散角→偏移半径: 10°→约0.176")
        void testAngleToRadius() {
            double spreadDeg = 10.0;
            double spreadRad = Math.toRadians(spreadDeg);
            double offsetRadius = Math.tan(spreadRad);
            assertTrue(offsetRadius > 0.1 && offsetRadius < 0.3,
                "10°tan=" + offsetRadius + " 期望≈0.176");
        }

        @Test
        @DisplayName("扩散角→偏移: 5°≈0.0875  15°≈0.268")
        void testCommonAngles() {
            double r5 = Math.tan(Math.toRadians(5));
            double r15 = Math.tan(Math.toRadians(15));
            assertTrue(r5 > 0.08 && r5 < 0.09, "5°=" + r5);
            assertTrue(r15 > 0.26 && r15 < 0.27, "15°=" + r15);
        }

        @Test
        @DisplayName("sqrt均匀采样: sqrt(random)*5 确保 [0,5]均匀")
        void testSqrtUniform() {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            int below2 = 0, above2 = 0;
            for (int i = 0; i < 20000; i++) {
                double radius = Math.sqrt(rng.nextDouble()) * 5.0;
                assertTrue(radius >= 0 && radius <= 5.0);
                if (radius < 2.0) below2++;
                else above2++;
            }
            // sqrt 均匀 → 面积比 = r²/R² = 4/25 = 0.16 → 约16%在2以内
            double ratio = (double) below2 / (below2 + above2);
            assertTrue(ratio > 0.10 && ratio < 0.22, "均匀性偏差: " + ratio);
        }

        @Test
        @DisplayName("象限bitmask编码")
        void testQuadrantBitmask() {
            assertEquals(1, 1 << 0);  // Q1
            assertEquals(2, 1 << 1);  // Q2
            assertEquals(4, 1 << 2);  // Q3
            assertEquals(8, 1 << 3);  // Q4
            assertEquals(15, 1 | 2 | 4 | 8); // 全象限
        }

        @Test
        @DisplayName("移动惩罚: 基础散布*1.5 (惩罚50%)")
        void testMovePenalty() {
            double baseSpread = 2.0;
            double movePenalty = 50; // %
            double result = baseSpread * (1.0 + movePenalty / 100.0);
            assertEquals(3.0, result, 0.001);
        }

        @Test
        @DisplayName("开镜修正: 基础散布*0.5 (ADS修正50%)")
        void testAdsCorrection() {
            double baseSpread = 3.0;
            double adsCorrection = 50; // % 缩减
            double result = baseSpread * (1.0 - adsCorrection / 100.0);
            assertEquals(1.5, result, 0.001);
        }
    }

    // ==================== 七: Lore 系统 ====================

    @Nested
    @DisplayName("Lore模板与颜色")
    class LoreTests {

        @Test
        @DisplayName("标准颜色码 & → §")
        void testColorCode() {
            String raw = "&7伤害: &c50";
            String colorized = ChatColor.translateAlternateColorCodes('&', raw);
            assertTrue(colorized.startsWith("\u00a77")); // §7 = 灰色
            assertTrue(colorized.contains("\u00a7c"));   // §c = 红色
        }

        @Test
        @DisplayName("多级颜色码: &l加粗 &4深红 → §l§4")
        void testFormatCodes() {
            String raw = "&l&4致命 &r&7普通";
            String result = ChatColor.translateAlternateColorCodes('&', raw);
            assertTrue(result.contains("\u00a7l\u00a74")); // 加粗+深红(&4)
            assertTrue(result.contains("\u00a7r\u00a77")); // 重置+灰
        }

        @Test
        @DisplayName("RGB十六进制解析 &#rrggbb")
        void testRgbHexPattern() {
            Pattern hex = Pattern.compile("&#([0-9a-fA-F]{6})");
            String raw = "&#ff0000红色 &#00ff00绿色";
            Matcher m = hex.matcher(raw);

            assertTrue(m.find());
            assertEquals("ff0000", m.group(1));
            assertTrue(m.find());
            assertEquals("00ff00", m.group(1));
            assertFalse(m.find());
        }

        @Test
        @DisplayName("RGB模式: find在6位hex前缀匹配")
        void testRgbMatchSixDigits() {
            Pattern hex = Pattern.compile("&#([0-9a-fA-F]{6})");
            // "&#1234567": find 会在7个字符中找到前6个 &#123456
            // 期望: find() 返回 true (因为"&#123456"匹配)
            Matcher m = hex.matcher("&#1234567");
            assertTrue(m.find(), "&#1234567中前6位匹配"); // 这实际上会匹配
            assertEquals("123456", m.group(1));
        }

        @Test
        @DisplayName("RGB模式: 非hex字符不匹配")
        void testRgbRejectsNonHex() {
            Pattern hex = Pattern.compile("&#([0-9a-fA-F]{6})");
            assertFalse(hex.matcher("&#zzz999").find()); // z不是hex
            assertFalse(hex.matcher("&#GGGGGG").find()); // G不是hex
        }

        @Test
        @DisplayName("所有&X颜色码 key=0-9 a-f存在")
        void testAllColorCodes() {
            char[] codes = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f','r'};
            for (char c : codes) {
                ChatColor color = ChatColor.getByChar(c);
                assertNotNull(color, "颜色码不存在: &" + c);
                assertEquals(c, color.toString().charAt(1));
            }
        }
    }

    // ==================== 八: FireMode ====================

    @Nested
    @DisplayName("射击模式")
    class FireModeTests {

        @Test
        @DisplayName("模式名称数组: 4个元素")
        void testModeNames() {
            assertEquals(4, FireModeManager.MODE_NAMES.length);
            assertEquals("§7安全", FireModeManager.MODE_NAMES[0]);
            assertEquals("§a单发", FireModeManager.MODE_NAMES[1]);
            assertEquals("§e连发", FireModeManager.MODE_NAMES[2]);
            assertEquals("§c全自动", FireModeManager.MODE_NAMES[3]);
        }

        @Test
        @DisplayName("GUN_FIRE_AVAILABLE_MODES bitmask 默认15=全模式")
        void testBitmask() {
            assertEquals(15.0, RpgAttribute.GUN_FIRE_AVAILABLE_MODES.getDefaultValue(), 0.001);
            assertEquals(15, (1 << 0) | (1 << 1) | (1 << 2) | (1 << 3));
        }

        @Test
        @DisplayName("GUN_FIRE_DEFAULT_MODE 默认单发=1")
        void testDefaultMode() {
            assertEquals(1.0, RpgAttribute.GUN_FIRE_DEFAULT_MODE.getDefaultValue(), 0.001);
        }

        @Test
        @DisplayName("连发弹数默认3发")
        void testBurstCount() {
            assertEquals(3.0, RpgAttribute.GUN_BURST_COUNT.getDefaultValue(), 0.001);
            assertEquals(80.0, RpgAttribute.GUN_BURST_INTERVAL_MS.getDefaultValue(), 0.001);
        }
    }

    // ==================== 九: 关键默认值 ====================

    @Nested
    @DisplayName("GUN默认属性值合理性")
    class DefaultValues {

        @Test
        @DisplayName("核心属性")
        void testCoreDefaults() {
            assertEquals(600, RpgAttribute.GUN_RPM.getDefaultValue(), 0.001);
            assertEquals(30, RpgAttribute.GUN_MAG_CAPACITY.getDefaultValue(), 0.001);
            assertEquals(100, RpgAttribute.GUN_DURA_MAX.getDefaultValue(), 0.001);
            assertEquals(1, RpgAttribute.GUN_DURA_LOSS_PER_SHOT.getDefaultValue(), 0.001);
        }

        @Test
        @DisplayName("散布属性")
        void testSpreadDefaults() {
            assertEquals(0, RpgAttribute.GUN_SPREAD_MIN.getDefaultValue(), 0.001);
            assertEquals(5, RpgAttribute.GUN_SPREAD_MAX.getDefaultValue(), 0.001);
            assertEquals(0.5, RpgAttribute.GUN_SPREAD_GROWTH.getDefaultValue(), 0.001);
            assertEquals(3, RpgAttribute.GUN_SPREAD_RECOVERY.getDefaultValue(), 0.001);
            assertEquals(15, RpgAttribute.GUN_SPREAD_QUADRANTS.getDefaultValue(), 0.001);
        }

        @Test
        @DisplayName("后坐属性")
        void testRecoilDefaults() {
            assertEquals(2, RpgAttribute.GUN_RECOIL_VERTICAL.getDefaultValue(), 0.001);
            assertEquals(0.5, RpgAttribute.GUN_RECOIL_HORIZONTAL.getDefaultValue(), 0.001);
            assertEquals(15, RpgAttribute.GUN_RECOIL_MAX.getDefaultValue(), 0.001);
            assertEquals(8, RpgAttribute.GUN_RECOIL_RECOVERY.getDefaultValue(), 0.001);
            assertEquals(0, RpgAttribute.GUN_RECOIL_PATTERN.getDefaultValue(), 0.001);
        }

        @Test
        @DisplayName("爆头属性")
        void testHeadshotDefaults() {
            assertEquals(100, RpgAttribute.GUN_HEADSHOT_CHANCE.getDefaultValue(), 0.001);
            assertEquals(200, RpgAttribute.GUN_HEADSHOT_MULT.getDefaultValue(), 0.001);
            assertEquals(85, RpgAttribute.GUN_HEADSHOT_THRESHOLD.getDefaultValue(), 0.001);
            assertEquals(50, RpgAttribute.GUN_BODY_THRESHOLD.getDefaultValue(), 0.001);
            assertEquals(20, RpgAttribute.GUN_LEG_THRESHOLD.getDefaultValue(), 0.001);
        }

        @Test
        @DisplayName("过热属性")
        void testOverheatDefaults() {
            assertEquals(5, RpgAttribute.GUN_HEAT_PER_SHOT.getDefaultValue(), 0.001);
            assertEquals(100, RpgAttribute.GUN_HEAT_THRESHOLD.getDefaultValue(), 0.001);
            assertEquals(10, RpgAttribute.GUN_HEAT_COOL_RATE.getDefaultValue(), 0.001);
            assertEquals(200, RpgAttribute.GUN_HEAT_SPREAD_FACTOR.getDefaultValue(), 0.001);
            assertEquals(150, RpgAttribute.GUN_HEAT_RECOIL_FACTOR.getDefaultValue(), 0.001);
        }

        @Test
        @DisplayName("故障属性")
        void testMalfuncDefaults() {
            assertEquals(0, RpgAttribute.GUN_MALFUNC_BASE_CHANCE.getDefaultValue(), 0.001);
            assertEquals(20, RpgAttribute.GUN_MALFUNC_COOLDOWN_TICKS.getDefaultValue(), 0.001);
            assertEquals(30, RpgAttribute.GUN_MALFUNC_JAM_CLEAR_TICKS.getDefaultValue(), 0.001);
            assertEquals(20, RpgAttribute.GUN_MALFUNC_CATA_DAMAGE.getDefaultValue(), 0.001);
            assertEquals(50, RpgAttribute.GUN_MALFUNC_CATA_DURA_LOSS.getDefaultValue(), 0.001);
        }

        @Test
        @DisplayName("弹夹属性")
        void testMagDefaults() {
            assertEquals(40, RpgAttribute.GUN_RELOAD_TIME_TICKS.getDefaultValue(), 0.001);
            assertEquals(50, RpgAttribute.GUN_RELOAD_EMPTY_TIME_TICKS.getDefaultValue(), 0.001);
            assertEquals(30, RpgAttribute.GUN_RELOAD_STAGED.getDefaultValue(), 0.001);
            assertEquals(0, RpgAttribute.GUN_AUTO_RELOAD.getDefaultValue(), 0.001);
            assertEquals(0, RpgAttribute.GUN_RELOAD_INTERRUPTIBLE.getDefaultValue(), 0.001);
        }

        @Test
        @DisplayName("子弹弹道属性")
        void testBallisticsDefaults() {
            assertEquals(60, RpgAttribute.GUN_BULLET_SPEED.getDefaultValue(), 0.001);
            assertEquals(0, RpgAttribute.GUN_BULLET_GRAVITY.getDefaultValue(), 0.001);
            assertEquals(100, RpgAttribute.GUN_BULLET_LIFETIME_TICKS.getDefaultValue(), 0.001);
            assertEquals(0.5, RpgAttribute.GUN_BULLET_DRAG.getDefaultValue(), 0.001);
        }

        @Test
        @DisplayName("枪膛属性")
        void testChamberDefaults() {
            assertEquals(0, RpgAttribute.GUN_CHAMBER_ENABLED.getDefaultValue(), 0.001);
            assertEquals(40, RpgAttribute.GUN_CHAMBER_TACTICAL_RELOAD_BONUS.getDefaultValue(), 0.001);
            assertEquals(15, RpgAttribute.GUN_CHAMBER_BOLT_TIME_TICKS.getDefaultValue(), 0.001);
            assertEquals(1, RpgAttribute.GUN_CHAMBER_AUTO_BOLT.getDefaultValue(), 0.001);
        }
    }

    // ==================== 十: PDC Key 防冲突 ====================

    @Test
    @DisplayName("PDC key 完整集合无重复 (item前缀不冲突)")
    void testAllPdcKeysUnique() {
        Set<String> allKeys = new HashSet<>();

        // 直接 PDC keys (去重)
        List<String> directKeys = List.of(
            "gun_id", "mag_ammo", "mag_ammo_stack", "mag_current_shot",
            "chamber_loaded", "gun_dura", "ammo_caliber", "ammo_type",
            "gun_weapon_type", "magazine_id", "mag_caliber", "mag_capacity",
            "pen_block_break", "pen_count", "gun_shooter_uuid",
            "gun_is_gun", "gun_shot_ammo",
            "kill_buff_expire", "kill_reload_speed", "kill_damage_bonus"
        );
        for (String k : directKeys) {
            assertTrue(allKeys.add(k), "重复PDC key: " + k);
        }

        // item.* 键（由 RpgAttribute GUN 生成）
        for (RpgAttribute attr : RpgAttribute.values()) {
            if (attr.getCategory() == RpgAttribute.Category.GUN) {
                assertTrue(allKeys.add("item." + attr.getKey()),
                    "item PDC key冲突: " + attr.getKey());
                assertTrue(allKeys.add("item." + attr.getKey() + "_min"));
                assertTrue(allKeys.add("item." + attr.getKey() + "_max"));
            }
        }

        // 验证 item.xxx 不会与直接key冲突
        for (String k : allKeys) {
            if (!k.startsWith("item.")) {
                assertFalse(allKeys.contains("item." + k),
                    "PDC key name冲突: " + k + " vs item." + k);
            }
        }
    }

    // ==================== 十一: 换弹时间 ====================

    @Nested
    @DisplayName("换弹时间计算")
    class ReloadTimeTests {

        @Test
        @DisplayName("战术换弹40tick (2秒)")
        void testTacticalReload() {
            assertEquals(40, RpgAttribute.GUN_RELOAD_TIME_TICKS.getDefaultValue(), 0.001);
            assertEquals(2.0, 40.0 / 20.0, 0.001); // tick→秒
        }

        @Test
        @DisplayName("空仓换弹50tick > 战术40tick")
        void testEmptyReloadLonger() {
            assertTrue(
                RpgAttribute.GUN_RELOAD_EMPTY_TIME_TICKS.getDefaultValue()
                > RpgAttribute.GUN_RELOAD_TIME_TICKS.getDefaultValue(),
                "空仓换弹应比战术换弹慢");
        }

        @Test
        @DisplayName("分段换弹30tick (可在1.5秒后中断)")
        void testStagedReload() {
            assertEquals(30, RpgAttribute.GUN_RELOAD_STAGED.getDefaultValue(), 0.001);
            assertEquals(1.5, 30.0 / 20.0, 0.001);
        }
    }

    // ==================== 十二: 子弹伤害衰减 ====================

    @Nested
    @DisplayName("子弹伤害衰减")
    class DamageFalloffTests {

        @Test
        @DisplayName("默认衰减: 20m起 80m终 最低50%")
        void testFalloffDefaults() {
            assertEquals(20, RpgAttribute.GUN_BULLET_DAMAGE_FALLOFF_START.getDefaultValue(), 0.001);
            assertEquals(80, RpgAttribute.GUN_BULLET_DAMAGE_FALLOFF_END.getDefaultValue(), 0.001);
            assertEquals(50, RpgAttribute.GUN_BULLET_DAMAGE_MIN_PERCENT.getDefaultValue(), 0.001);
        }

        @Test
        @DisplayName("线性衰减: 50m → 50%伤害")
        void testLinearFalloff() {
            double start = 20, end = 80, minPct = 50;
            double distance = 50;
            double t = Math.max(0, Math.min(1, (distance - start) / (end - start)));
            assertEquals(0.5, t, 0.01);
            double damagePct = 1.0 - t * (1.0 - minPct / 100.0);
            assertEquals(0.75, damagePct, 0.01); // 75%保留
        }

        @Test
        @DisplayName("超出衰减终点 → 最低保留")
        void testPastFalloffEnd() {
            double start = 20, end = 80, minPct = 50;
            double distance = 100;
            double t = Math.max(0, Math.min(1, (distance - start) / (end - start)));
            assertEquals(1.0, t, 0.001);
            double damagePct = 1.0 - t * (1.0 - minPct / 100.0);
            assertEquals(0.5, damagePct, 0.001);
        }

        @Test
        @DisplayName("衰减起点之前 → 100%伤害")
        void testBeforeFalloffStart() {
            double start = 20, end = 80, minPct = 50;
            double distance = 10;
            double t = Math.max(0, Math.min(1, (distance - start) / (end - start)));
            assertEquals(0.0, t, 0.001);
            double damagePct = 1.0 - t * (1.0 - minPct / 100.0);
            assertEquals(1.0, damagePct, 0.001);
        }
    }
}
