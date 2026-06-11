package io.github.liyughj.xH.rpg.Attribute;

import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 致盲状态管理器 —— 管理实体上的致盲标记。
 *
 * <p><b>致盲效果：</b></p>
 * 被致盲者在攻击他人时，有效命中率 = 原始命中率 - 致盲效能。
 * <ul>
 *   <li>有效命中≥0 → 正常进行命中/闪避判定</li>
 *   <li>有效命中<0 → 先进行"致盲MISS"判定（roll ≤ abs(有效命中)）</li>
 *   &emsp;→ 命中则打空（与对方闪避无关，直接MISS）
 *   <li>对方闪避时，有效命中≤0则无法穿越闪避</li>
 * </ul>
 *
 * <p><b>多攻击者规则：</b></p>
 * 取效能最高 → 剩余时间最长 → 拒绝弱者。
 */
public final class BlindManager {

    /** 单个实体的致盲状态 */
    public static class Debuff {
        /** 施加者名称（用于聊天提示） */
        public final String breakerName;
        /** 致盲效能（0~100，降低对方命中%） */
        public final double efficiency;
        /** 过期时间戳（System.currentTimeMillis() + durationMs） */
        public final long expireMs;

        Debuff(String breakerName, double efficiency, long expireMs) {
            this.breakerName = breakerName;
            this.efficiency = Math.min(100.0, Math.max(0, efficiency));
            this.expireMs = expireMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireMs;
        }

        long remainingMs() {
            return Math.max(0, expireMs - System.currentTimeMillis());
        }
    }

    /** 实体 UUID → 致盲状态 */
    private static final Map<UUID, Debuff> debuffs = new ConcurrentHashMap<>();

    private BlindManager() {}

    /* ==================== 查询 ==================== */

    /**
     * 获取实体当前的致盲 Debuff（自动清理过期）。
     * 返回 null 表示无致盲。
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
     * 获取致盲效能（0 = 无致盲，20 = 降低对方20%命中）。
     */
    public static double getBlindEfficiency(LivingEntity entity) {
        Debuff d = getDebuff(entity);
        return d != null ? d.efficiency : 0.0;
    }

    /* ==================== 施加致盲 ==================== */

    /**
     * 对目标施加致盲。
     *
     * @param attacker       攻击者
     * @param target         被致盲目标
     * @param chancePct      触发概率（0~100）
     * @param efficiencyPct  致盲效能（0~100）
     * @param durationTicks  持续时间（tick）
     * @return 新 Debuff，未触发或被拒绝返回 null
     */
    public static Debuff apply(LivingEntity attacker, LivingEntity target,
                               double chancePct, double efficiencyPct, long durationTicks) {
        if (attacker == null || target == null) return null;
        if (efficiencyPct <= 0 || durationTicks <= 0) return null;

        /* 概率 roll */
        if (chancePct <= 0) return null;
        double roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100.0;
        if (roll > chancePct) return null;

        UUID targetUuid = target.getUniqueId();
        long durationMs = durationTicks * 50L;
        long expireMs = System.currentTimeMillis() + durationMs;
        String attackerName = attacker.getName();

        Debuff existing = getDebuff(target);

        if (existing == null) {
            Debuff d = new Debuff(attackerName, efficiencyPct, expireMs);
            debuffs.put(targetUuid, d);
            return d;
        }

        /* 已有致盲：效能更高覆盖，同效能取时间更长 */
        int cmp = Double.compare(efficiencyPct, existing.efficiency);
        if (cmp > 0 || (cmp == 0 && expireMs > existing.expireMs)) {
            Debuff d = new Debuff(attackerName, efficiencyPct, expireMs);
            debuffs.put(targetUuid, d);
            return d;
        }

        return null; // 被现有覆盖
    }

    /** 移除实体致盲 */
    public static void remove(LivingEntity entity) {
        if (entity != null) {
            debuffs.remove(entity.getUniqueId());
        }
    }

    public static void clear() {
        debuffs.clear();
    }
}
