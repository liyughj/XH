package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeRange;
import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 散布计算器 —— 将扩散角 + 象限 + 偏向度 + 散布模式映射为随机弹道方向。
 *
 * <h3>坐标映射</h3>
 * 玩家视角 → 右手坐标系：右=右向量（X），上=上向量（Y），前=视线方向（Z）。
 * 直角坐标系中 X=右(+)/左(-), Y=上(+)/下(-):
 *   Q1: X≥0, Y≥0  (右上)
 *   Q2: X<0, Y≥0  (左上)
 *   Q3: X<0, Y<0  (左下)
 *   Q4: X≥0, Y<0  (右下)
 *
 * <h3>偏向度</h3>
 * biasX=100% → 越靠近 X 轴角度概率越高（平射）；0% → 越靠近 Y 轴（竖射）。
 * 实现：随机角度的概率由幂函数加权。
 */
public final class SpreadCalculator {

    private SpreadCalculator() {}

    /**
     * 计算随机弹道方向。
     *
     * @param player      玩家
     * @param weapon      枪械物品
     * @param baseDir     玩家准星方向（未经散布的原始方向）
     * @return 经散布偏移后的弹道方向（单位向量）
     */
    public static Vector applySpread(Player player, ItemStack weapon, Vector baseDir) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        /* —— 从 SpreadManager 消费扩散角 —— */
        double spreadDeg = SpreadManager.consumeSpread(player, weapon);

        /* —— 过热/耐久/弹药修正 —— */
        spreadDeg *= io.github.liyughj.xH.specialEvent.HeatSystem.getSpreadMultiplier(player, weapon);
        spreadDeg *= io.github.liyughj.xH.specialEvent.DurabilitySystem.getSpreadPenalty(player, weapon);
        // 弹药修正
        AmmoConfig.AmmoTypeDef ammo = MagazineManager.getCurrentAmmoType(weapon);
        if (ammo != null) spreadDeg *= ammo.spreadMult;

        // 开镜晃动修正（屏息时降低）
        spreadDeg *= AdsManager.getSwayMultiplier(player, weapon);

        if (spreadDeg <= 0.0) return baseDir.clone(); // 绝对精准

        /* —— 读取象限/偏向/模式 —— */
        int quadrantMask = (int) rollOrDefault(weapon, RpgAttribute.GUN_SPREAD_QUADRANTS);
        double biasX = rollOrDefault(weapon, RpgAttribute.GUN_SPREAD_BIAS_X) / 100.0;
        int pattern = (int) rollOrDefault(weapon, RpgAttribute.GUN_SPREAD_PATTERN);

        /* —— 构建局部坐标系 —— */
        Vector dir = baseDir.clone().normalize();
        // 找一个与 dir 正交的 right 向量
        Vector worldUp = new Vector(0, 1, 0);
        // 避免 dir 正好平行于 worldUp
        if (Math.abs(dir.dot(worldUp)) > 0.999) {
            worldUp = new Vector(1, 0, 0);
        }
        Vector right = dir.clone().crossProduct(worldUp).normalize();
        Vector up = right.clone().crossProduct(dir).normalize();

        /* —— 将扩散角转为偏移半径 —— */
        double spreadRad = Math.toRadians(spreadDeg);
        double offsetRadius = Math.tan(spreadRad); // 单位距离上的偏移长度

        /* —— 生成随机角度 —— */
        double angleDeg = rng.nextDouble() * 360.0; // [0, 360)
        double radius;

        /* 模式处理 */
        switch (pattern) {
            case 0: // 圆形：均匀角度 + 均匀半径
                angleDeg = pickAngleInQuadrants(rng, angleDeg, quadrantMask, biasX);
                radius = Math.sqrt(rng.nextDouble()) * offsetRadius; // sqrt 保证圆内均匀
                break;

            case 1: // 水平椭圆：X 轴拉伸
                angleDeg = pickAngleInQuadrants(rng, angleDeg, quadrantMask, biasX);
                radius = Math.sqrt(rng.nextDouble()) * offsetRadius;
                // 拉伸：水平方向(x)半径 = offsetRadius, 竖直方向(y)半径 = offsetRadius * 0.3
                // 用椭圆的参数方程
                double angleRadH = Math.toRadians(angleDeg);
                double rx = offsetRadius;
                double ry = offsetRadius * 0.3;
                double dx = Math.cos(angleRadH) * rx * Math.sqrt(rng.nextDouble());
                double dy = Math.sin(angleRadH) * ry * Math.sqrt(rng.nextDouble());
                Vector hOffset = right.clone().multiply(dx).add(up.clone().multiply(dy));
                return dir.clone().add(hOffset).normalize();

            case 2: // 竖直椭圆：Y 轴拉伸
                angleDeg = pickAngleInQuadrants(rng, angleDeg, quadrantMask, biasX);
                double angleRadV = Math.toRadians(angleDeg);
                double rxV = offsetRadius * 0.3;
                double ryV = offsetRadius;
                double dxV = Math.cos(angleRadV) * rxV * Math.sqrt(rng.nextDouble());
                double dyV = Math.sin(angleRadV) * ryV * Math.sqrt(rng.nextDouble());
                Vector vOffset = right.clone().multiply(dxV).add(up.clone().multiply(dyV));
                return dir.clone().add(vOffset).normalize();

            case 3: // 十字：仅 ±X 或 ±Y 方向
                angleDeg = pickAngleInQuadrants(rng, angleDeg, quadrantMask, biasX);
                // 将角度 snap 到最近的轴
                double snap = Math.round(angleDeg / 90.0) * 90.0;
                double snapRad = Math.toRadians(snap);
                radius = Math.sqrt(rng.nextDouble()) * offsetRadius;
                double dxC = Math.cos(snapRad) * radius;
                double dyC = Math.sin(snapRad) * radius;
                Vector cOffset = right.clone().multiply(dxC).add(up.clone().multiply(dyC));
                return dir.clone().add(cOffset).normalize();

            default: // 同圆形
                angleDeg = pickAngleInQuadrants(rng, angleDeg, quadrantMask, biasX);
                radius = Math.sqrt(rng.nextDouble()) * offsetRadius;
        }

        /* 圆形：生成最终偏移 */
        double rad = Math.toRadians(angleDeg);
        Vector offset = right.clone().multiply(Math.cos(rad) * radius)
            .add(up.clone().multiply(Math.sin(rad) * radius));
        return dir.clone().add(offset).normalize();
    }

    /* ==================== 象限过滤 ==================== */

    /**
     * 在允许的象限内随机一个角度（度，[0,360)），并应用 biasX 偏向。
     *
     * biasX=50% → 均匀分布。
     * biasX=100% → 随机角向最近的 X 轴端点靠拢（水平射）。
     * biasX=0% → 随机角向最近的 Y 轴端点靠拢（竖直射）。
     */
    private static double pickAngleInQuadrants(ThreadLocalRandom rng, double rawAngle,
                                               int quadrantMask, double biasX) {
        java.util.List<double[]> segments = buildSegments(quadrantMask);
        if (segments.isEmpty()) return rawAngle;

        double[] seg = segments.get(rng.nextInt(segments.size()));
        double lo = seg[0], hi = seg[1];

        if (biasX == 0.5) return lo + rng.nextDouble() * (hi - lo);

        // 确定每一端到 X/Y 轴的距离，选更近X 还是更近Y 的那个端点作为偏向目标
        double loDistX = minDistToXAxis(lo);
        double loDistY = minDistToYAxis(lo);
        double hiDistX = minDistToXAxis(hi);
        double hiDistY = minDistToYAxis(hi);

        boolean loNearX = (loDistX <= loDistY);
        boolean hiNearX = (hiDistX <= hiDistY);

        double biasTarget; // [0,1] 归一化偏向目标（0=lo, 1=hi）
        if (biasX > 0.5) {
            // 偏向X轴：选择该段两端中更靠近X轴的那端
            biasTarget = loNearX ? 0.0 : 1.0;
            // 如果两端都靠近X轴，选最近的
            if (loNearX && hiNearX) biasTarget = (loDistX <= hiDistX) ? 0.0 : 1.0;
        } else {
            // 偏向Y轴
            biasTarget = loNearX ? 1.0 : 0.0; // loNearX means lo far from Y
            if (!loNearX && !hiNearX) biasTarget = (loDistY <= hiDistY) ? 0.0 : 1.0;
        }

        double power = 1.0 + Math.abs(biasX - 0.5) * 6.0;
        double u = rng.nextDouble();
        double t;
        if (u < biasTarget) {
            t = biasTarget - biasTarget * Math.pow(1.0 - u / biasTarget, power);
        } else {
            t = biasTarget + (1.0 - biasTarget) * Math.pow((u - biasTarget) / (1.0 - biasTarget), power);
        }
        return lo + (hi - lo) * t;
    }

    /** 角度到最近的 X 轴（0°/180°/360°）的度数距离 */
    private static double minDistToXAxis(double deg) {
        deg = deg % 360;
        if (deg < 0) deg += 360;
        double dist0 = Math.min(deg, 360.0 - deg);       // 到 0°(360°) 的距离
        double dist180 = Math.abs(deg - 180.0);
        return Math.min(dist0, dist180);
    }

    /** 角度到最近的 Y 轴（90°/270°）的度数距离 */
    private static double minDistToYAxis(double deg) {
        deg = deg % 360;
        if (deg < 0) deg += 360;
        double dist90 = Math.abs(deg - 90.0);
        double dist270 = Math.abs(deg - 270.0);
        return Math.min(dist90, dist270);
    }

    /**
     * 根据象限 bitmask 构建角度段列表。
     * bit0=Q1[0°,90°), bit1=Q2[90°,180°), bit2=Q3[180°,270°), bit3=Q4[270°,360°)
     */
    private static java.util.List<double[]> buildSegments(int mask) {
        java.util.List<double[]> list = new java.util.ArrayList<>();
        if ((mask & 1) != 0) list.add(new double[]{0.0, 90.0});    // Q1
        if ((mask & 2) != 0) list.add(new double[]{90.0, 180.0});  // Q2
        if ((mask & 4) != 0) list.add(new double[]{180.0, 270.0}); // Q3
        if ((mask & 8) != 0) list.add(new double[]{270.0, 360.0}); // Q4
        return list;
    }

    /* ==================== 工具方法 ==================== */

    private static double rollOrDefault(ItemStack item, RpgAttribute attr) {
        AttributeRange range = AttributeStorage.getItemAttrRange(item, attr);
        if (range.getMin() == attr.getDefaultValue() && range.getMax() == attr.getDefaultValue()) {
            return attr.getDefaultValue();
        }
        return range.roll();
    }
}
