package io.github.liyughj.xH.rpg.Attribute;

import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 破甲状态管理器 —— 管理实体上的破甲标记。
 *
 * <p><b>状态机：</b></p>
 * <pre>
 *   无标记 → 浅（首次触发）
 *   浅   → 中（再触发，相同攻击者升级）
 *   中   → 深（再触发，相同攻击者升级）
 *   深   → 深（再触发，仅刷新时间）
 * </pre>
 *
 * <p><b>多攻击者规则：</b></p>
 * 取程度最深 → 百分比最高 → 剩余时间最长 → 拒绝弱者覆盖。
 *
 * <p><b>护甲变动自动响应：</b></p>
 * 破甲百分比在每次伤害计算时实时应用，不缓存护甲值，自然覆盖换装场景。
 */
public final class ArmorBreakManager {

    /** 破甲等级（顺序：浅 < 中 < 深） */
    public enum Level {
        SHALLOW("浅"),
        MEDIUM("中"),
        DEEP("深");

        private final String display;

        Level(String display) {
            this.display = display;
        }

        public String getDisplay() { return display; }

        public static Level fromOrdinal(int ord) {
            Level[] values = values();
            if (ord >= 0 && ord < values.length) return values[ord];
            return null;
        }
    }

    /** 单个实体的破甲状态 */
    public static class Debuff {
        /** 施加者的 UUID */
        public final UUID breakerUuid;
        /** 施加者名称（用于聊天提示） */
        public final String breakerName;
        /** 当前深度等级 */
        public final Level level;
        /** 总破甲百分比（已累加浅+中+深） */
        public final double totalPct;
        /** 过期时间戳（System.currentTimeMillis() + durationMs） */
        public final long expireMs;

        Debuff(UUID breakerUuid, String breakerName, Level level, double totalPct, long expireMs) {
            this.breakerUuid = breakerUuid;
            this.breakerName = breakerName;
            this.level = level;
            this.totalPct = Math.min(100.0, totalPct);
            this.expireMs = expireMs;
        }

        /** 是否已过期 */
        boolean isExpired() {
            return System.currentTimeMillis() > expireMs;
        }

        /** 剩余时间（毫秒） */
        long remainingMs() {
            return Math.max(0, expireMs - System.currentTimeMillis());
        }

        /** 深度序数比较 */
        int depthOrd() { return level.ordinal(); }
    }

    /** 实体 UUID → 破甲状态 */
    private static final Map<UUID, Debuff> debuffs = new ConcurrentHashMap<>();

    private ArmorBreakManager() { /* 工具类 */ }

    /* ==================== 查询 ==================== */

    /**
     * 获取实体当前的破甲 Debuff（自动清理过期）。
     * 返回 null 表示无破甲状态。
     */
    public static Debuff getDebuff(LivingEntity entity) {
        if (entity == null) return null;
        Debuff d = debuffs.get(entity.getUniqueId());
        if (d != null && d.isExpired()) {
            debuffs.remove(entity.getUniqueId());
            return null;
        }
        return d;
    }

    /**
     * 获取实体当前的破甲百分比（0 = 无破甲）。
     */
    public static double getBreakPct(LivingEntity entity) {
        Debuff d = getDebuff(entity);
        return d != null ? d.totalPct : 0.0;
    }

    /**
     * 获取伤害倍率（破甲导致目标受伤加重）。
     * 返回 1.0 表示无影响，1.5 表示多受 50% 伤害。
     */
    public static double getDamageMultiplier(LivingEntity target) {
        double pct = getBreakPct(target);
        return 1.0 + pct / 100.0;
    }

    /* ==================== 应用破甲 ==================== */

    /**
     * 对目标施加破甲。
     *
     * @param attacker      攻击者
     * @param target        被攻击目标
     * @param chancePct     破甲触发概率（0~100）
     * @param shallowPct    浅百分比
     * @param mediumPct     中百分比（累加到浅之上）
     * @param deepPct       深百分比（累加到中之上）
     * @param durationTicks 持续时间（tick）
     * @return 施加后的 Debuff，如果未触发/被拒绝则返回 null
     */
    public static Debuff apply(LivingEntity attacker, LivingEntity target,
                               double chancePct,
                               double shallowPct, double mediumPct, double deepPct,
                               long durationTicks) {
        if (attacker == null || target == null) return null;

        /* 概率 roll */
        if (chancePct <= 0) return null;
        double roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100.0;
        if (roll > chancePct) return null;

        UUID targetUuid = target.getUniqueId();
        long durationMs = durationTicks * 50L; // 1 tick = 50ms
        long expireMs = System.currentTimeMillis() + durationMs;

        Debuff existing = getDebuff(target);
        UUID attackerUuid = attacker.getUniqueId();
        String attackerName = attacker.getName();

        if (existing == null) {
            /* 首次破甲：直接施加浅标记 */
            Debuff d = new Debuff(attackerUuid, attackerName, Level.SHALLOW, shallowPct, expireMs);
            debuffs.put(targetUuid, d);
            return d;
        }

        /* 已有破甲，判断是否升级/覆盖 */
        boolean sameBreaker = existing.breakerUuid.equals(attackerUuid);

        if (sameBreaker) {
            /* 同一攻击者：升级或刷新 */
            return applySameBreaker(targetUuid, attackerUuid, attackerName, existing,
                                    shallowPct, mediumPct, deepPct, expireMs);
        } else {
            /* 不同攻击者：强者覆盖弱者 */
            return applyDifferentBreaker(targetUuid, attackerUuid, attackerName, existing,
                                         shallowPct, mediumPct, deepPct, expireMs);
        }
    }

    /** 同一攻击者：浅→中→深→刷新 */
    private static Debuff applySameBreaker(UUID targetUuid, UUID breakerUuid, String breakerName,
                                           Debuff existing,
                                           double shallowPct, double mediumPct, double deepPct,
                                           long expireMs) {
        Level newLevel;
        double newPct;

        switch (existing.level) {
            case SHALLOW:
                newLevel = Level.MEDIUM;
                newPct = shallowPct + mediumPct; // 浅 + 中
                break;
            case MEDIUM:
                newLevel = Level.DEEP;
                newPct = shallowPct + mediumPct + deepPct; // 浅 + 中 + 深
                break;
            case DEEP:
                newLevel = Level.DEEP;
                newPct = existing.totalPct; // 维持不变
                break;
            default:
                return null;
        }

        Debuff d = new Debuff(breakerUuid, breakerName, newLevel, newPct, expireMs);
        debuffs.put(targetUuid, d);
        return d;
    }

    /** 不同攻击者：深度 > 百分比 > 余时，强者覆盖弱者 */
    private static Debuff applyDifferentBreaker(UUID targetUuid, UUID breakerUuid, String breakerName,
                                                Debuff existing,
                                                double shallowPct, double mediumPct, double deepPct,
                                                long expireMs) {
        /* 计算新破甲在浅等级的初始百分比 */
        double newPct = shallowPct;
        Level newLevel = Level.SHALLOW;

        /* 判断新破甲是否比现有更强 */
        int depthCompare = Integer.compare(newLevel.ordinal(), existing.level.ordinal());
        if (depthCompare > 0) {
            // 新破甲深度更高 → 覆盖
        } else if (depthCompare == 0) {
            int pctCompare = Double.compare(newPct, existing.totalPct);
            if (pctCompare > 0) {
                // 同深度但百分比更高 → 覆盖
            } else if (pctCompare == 0) {
                if (expireMs > existing.expireMs) {
                    // 同百分比但时间更长 → 覆盖
                } else {
                    return null; // 被现有覆盖，不更新
                }
            } else {
                return null; // 百分比更低，不更新
            }
        } else {
            return null; // 深度更低，不更新
        }

        Debuff d = new Debuff(breakerUuid, breakerName, newLevel, newPct, expireMs);
        debuffs.put(targetUuid, d);
        return d;
    }

    /** 移除实体破甲 */
    public static void remove(LivingEntity entity) {
        if (entity != null) {
            debuffs.remove(entity.getUniqueId());
        }
    }

    /** 清理内部 Map（插件卸载时） */
    public static void clear() {
        debuffs.clear();
    }
}
