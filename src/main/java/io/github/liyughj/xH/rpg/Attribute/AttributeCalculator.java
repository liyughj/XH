package io.github.liyughj.xH.rpg.Attribute;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;

/**
 * RPG 属性合并计算器。
 * <p>
 * 职责：
 * <ol>
 *   <li>合并物品属性 + 玩家属性 → 最终运行时值</li>
 *   <li>对区间属性执行随机 roll（每次调用时）</li>
 *   <li>按公式计算近战/射弹最终伤害数值</li>
 * </ol>
 * <p>
 * 伤害计算公式：
 * <pre>
 *   近战 = melee_damage × (1 + melee_bonus%)  +  damage × (1 + damage_bonus%)
 *   射弹 = projectile_damage × (1 + projectile_bonus%)  +  damage × (1 + damage_bonus%)
 * </pre>
 * <p>
 * 集成到现有伤害管线（LevelEffectListener）：
 * <pre>
 *   // LOWEST 剥离原版后，HIGH 应用：
 *   double rpgBonus = AttributeCalculator.calcMeleeDamage(player, weapon);
 *   double enchantMultiplier = 1.0 + getEnchantDamagePercent();
 *   event.setDamage((baseDamage + rpgBonus) * enchantMultiplier);
 * </pre>
 * <p>
 * 扩展设计：预留 {@link DamageCalculator} 接口，未来枪械/魔法/修仙模块可注册自定义计算器。
 */
public class AttributeCalculator {

    /** 玩家属性源列表（由职业模块注册） */
    private static final java.util.List<ItemAttributeConfig.PlayerAttributeSource> playerSources = new java.util.ArrayList<>();

    /** 自定义伤害计算器列表（由外部模块注册） */
    private static final java.util.List<DamageCalculator> calculators = new java.util.ArrayList<>();

    /** 暴击默认值（全局，来自 rpg_items.yml，默认 50%） */
    private static double critDefault = 50.0;

    /** 暴击模式：ADD（加法）或 MULTIPLY（乘法），默认 ADD */
    public enum CritMode { ADD, MULTIPLY }
    private static CritMode critMode = CritMode.ADD;

    /** 伤害类型 */
    public enum DamageType { MELEE, PROJECTILE, GUN }

    /**
     * 自定义伤害计算器接口。
     * <p>
     * 外部模块（枪械/魔法/修仙）实现此接口并注册，
     * 本计算器会在标准 RPG 计算之后依次调用。
     */
    public interface DamageCalculator {
        /**
         * 在标准 RPG 伤害基础上叠加额外伤害。
         * @param ctx 计算上下文（含已算好的 rpg 伤害 + 原始基础伤害）
         * @return 叠加后的总伤害
         */
        double calculate(DamageContext ctx);
    }

    /** 伤害计算上下文 */
    public static class DamageContext {
        public final Player player;
        public final ItemStack weapon;
        public final DamageType type;
        /** 本次 roll 后的纯 RPG 伤害（不含附魔加成） */
        public final double rpgDamage;
        /** 原版基础伤害（附魔剥离后） */
        public final double baseDamage;

        public DamageContext(Player player, ItemStack weapon, DamageType type, double rpgDamage, double baseDamage) {
            this.player = player;
            this.weapon = weapon;
            this.type = type;
            this.rpgDamage = rpgDamage;
            this.baseDamage = baseDamage;
        }
    }

    /** 伤害计算结果（含吸血回复量） */
    public static class DamageResult {
        public final double damage;
        public final double heal;

        public DamageResult(double damage, double heal) {
            this.damage = Math.max(0, damage);
            this.heal = Math.max(0, heal);
        }
    }

    /* ==================== 注册方法 ==================== */

    /** 注册玩家属性源 */
    public static void registerPlayerSource(ItemAttributeConfig.PlayerAttributeSource source) {
        playerSources.add(source);
    }

    /** 注册自定义伤害计算器 */
    public static void registerCalculator(DamageCalculator calculator) {
        calculators.add(calculator);
    }

    /** 由 ItemAttributeConfig 设置暴击全局配置 */
    public static void setCritConfig(double critDefault, CritMode mode) {
        AttributeCalculator.critDefault = Math.max(0, critDefault);
        AttributeCalculator.critMode = mode;
    }

    public static double getCritDefault() { return critDefault; }
    public static CritMode getCritMode() { return critMode; }

    /** 吸血默认值（全局，来自 rpg_items.yml，默认 50%） */
    private static double lifestealDefault = 50.0;

    /** 吸血模式：ADD（加法）或 MULTIPLY（乘法），默认 ADD */
    public enum LifestealMode { ADD, MULTIPLY }
    private static LifestealMode lifestealMode = LifestealMode.ADD;

    /** 由 ItemAttributeConfig 设置吸血全局配置 */
    public static void setLifestealConfig(double lsDefault, LifestealMode mode) {
        AttributeCalculator.lifestealDefault = Math.max(0, lsDefault);
        AttributeCalculator.lifestealMode = mode;
    }

    public static double getLifestealDefault() { return lifestealDefault; }
    public static LifestealMode getLifestealMode() { return lifestealMode; }

    /* ==================== 主计算入口 ==================== */

    /**
     * 计算近战 RPG 伤害加成（roll 后的最终值）。
     * 只算 RPG 属性部分，不包含原版基础和附魔加成。
     *
     * @param player 攻击者
     * @param weapon 手持武器
     * @return RPG 近战伤害加成（≥0）
     */
    public static double calcMeleeDamage(Player player, ItemStack weapon) {
        Map<RpgAttribute, Double> merged = mergeAttrs(player, weapon);
        return compute(DamageType.MELEE, merged);
    }

    /**
     * 计算射弹 RPG 伤害加成（roll 后的最终值）。
     */
    public static double calcProjectileDamage(Player player, ItemStack weapon) {
        Map<RpgAttribute, Double> merged = mergeAttrs(player, weapon);
        return compute(DamageType.PROJECTILE, merged);
    }

    /**
     * 获取物品的攻击速度加成百分比（已 roll 区间，合并玩家+物品）。
     * 返回 0.0 表示默认攻速（4.0 次/秒），正数=更快，负数=更慢。
     */
    public static double calcAttackSpeedPercent(Player player, ItemStack weapon) {
        Map<RpgAttribute, Double> merged = mergeAttrs(player, weapon);
        return getAttr(merged, RpgAttribute.ATTACK_SPEED);
    }

    /**
     * 将攻击速度百分比转为原版 ATTACK_SPEED 属性值。
     * 基值 4.0（次/秒），pct=50% → 6.0，pct=-50% → 2.0。
     */
    public static double toVanillaAttackSpeed(double attackSpeedPercent) {
        return 4.0 * (1.0 + attackSpeedPercent / 100.0);
    }

    /**
     * 获取攻击冷却秒数（两次攻击之间的最小间隔）。
     */
    public static double getCooldownSeconds(double attackSpeedPercent) {
        double vanillaSpeed = toVanillaAttackSpeed(attackSpeedPercent);
        return 1.0 / Math.max(0.1, vanillaSpeed);
    }

    /* ==================== 穿透计算 ==================== */

    /** 穿透计算结果 */
    public static class PenetrationResult {
        /** 穿透后的额外伤害（高穿超额部分，已经过减免） */
        public final double extraDamage;
        /** 穿透后剩余的护甲减免百分比（0~1，供后续防御减免） */
        public final double remainingArmorPct;

        public PenetrationResult(double extraDamage, double remainingArmorPct) {
            this.extraDamage = Math.max(0, extraDamage);
            this.remainingArmorPct = Math.max(0, Math.min(1, remainingArmorPct));
        }
    }

    /**
     * 对被攻击者计算穿透。
     * <p>
     * 流程：
     * <ol>
     *   <li>读攻击方低穿/高穿/效能，合并玩家+物品</li>
     *   <li>读防御方四件护甲的护甲韧性（材质默认+物品armor_toughness）</li>
     *   <li>低穿受总韧性抵抗，高穿无视韧性</li>
     *   <li>高穿超额 = max(0, 最终高穿 - 总韧性)，转为额外伤害</li>
     * </ol>
     *
     * @param attacker       攻击者
     * @param attackWeapon   攻击者武器
     * @param defender       防御者
     * @param incomingDamage 经过暴击/吸血前的原始伤害（暴击后、吸血前的总伤害）
     * @return PenetrationResult（extraDamage=高穿超额伤害，remainingArmorPct=未被低穿的护甲减免比例）
     */
    public static PenetrationResult calcPenetration(Player attacker, ItemStack attackWeapon, LivingEntity defender, double incomingDamage) {
        /* --- 攻击方：读低穿/高穿/效能 --- */
        Map<RpgAttribute, Double> atkMerged = mergeAttrs(attacker, attackWeapon);

        double lowPen = getAttr(atkMerged, RpgAttribute.LOW_PENETRATION);
        double highPen = getAttr(atkMerged, RpgAttribute.HIGH_PENETRATION);
        double eff = getAttr(atkMerged, RpgAttribute.PENETRATION_EFFICIENCY);

        if (lowPen <= 0 && highPen <= 0) {
            return new PenetrationResult(0, 1.0); // 无穿透，护甲全额生效
        }

        /* 穿透效能增幅 */
        double lowPenFinal = lowPen * (1.0 + eff / 100.0);
        double highPenFinal = highPen * (1.0 + eff / 100.0);

        /* --- 防御方：累加四件护甲的护甲韧性 --- */
        double totalToughness = 0.0;
        if (defender instanceof Player defPlayer) {
            EquipmentSlot[] armorSlots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
            for (EquipmentSlot slot : armorSlots) {
                ItemStack armor = defPlayer.getInventory().getItem(slot);
                if (armor == null || !armor.hasItemMeta()) continue;

                /* 材质默认韧性 */
                double matToughness = ItemAttributeConfig.getArmorToughness(armor.getType());

                /* 物品上的 armor_toughness 属性 */
                double itemToughness = 0.0;
                if (armor.hasItemMeta()) {
                    AttributeRange range = AttributeStorage.getItemAttrRange(armor, RpgAttribute.ARMOR_TOUGHNESS);
                    itemToughness = range.roll();
                }

                totalToughness += matToughness + itemToughness;
            }
        }
        totalToughness = Math.min(100.0, totalToughness);

        /* --- 低穿计算：最终低穿受韧性抵抗 --- */
        double effectiveLowPen = Math.max(0, lowPenFinal - totalToughness);
        double remainingPct = 1.0 - effectiveLowPen / 100.0; // 未被穿透的护甲比例

        /* --- 高穿计算：无视韧性，超额转伤害 --- */
        double extraDamage = 0.0;
        if (highPenFinal > totalToughness) {
            extraDamage = highPenFinal - totalToughness;
            /* 高穿超额伤害需经过残余护甲减免 */
            if (remainingPct < 1.0) {
                extraDamage *= remainingPct;
            }
        }

        return new PenetrationResult(extraDamage, remainingPct);
    }

    /**
     * 全量计算（含自定义计算器 + 暴击 + 吸血），返回最终 RPG 伤害和回复量。
     * 供 LevelEffectListener 在 HIGH 优先级调用。
     *
     * @param player     攻击者
     * @param weapon     手持武器
     * @param target     被攻击的目标（汲取需要，可为 null）
     * @param type       近战/射弹
     * @param baseDamage 原版基础伤害（剥离附魔后）
     * @return DamageResult（damage=最终伤害，heal=吸血回复量）
     */
    public static DamageResult calcFinalDamage(Player player, ItemStack weapon, LivingEntity target, DamageType type, double baseDamage) {
        Map<RpgAttribute, Double> merged = mergeAttrs(player, weapon);
        double rpgDamage = compute(type, merged);

        /* 合并基础伤害 */
        double totalDamage = rpgDamage + baseDamage;

        /* 暴击判定 */
        totalDamage = applyCrit(player, weapon, totalDamage);

        /* 处理自定义计算器（枪械/魔法/修仙等模块） */
        if (!calculators.isEmpty()) {
            DamageContext ctx = new DamageContext(player, weapon, type, totalDamage - baseDamage, baseDamage);
            for (DamageCalculator calc : calculators) {
                totalDamage = calc.calculate(ctx) + baseDamage;
            }
        }

        /* 吸血判定（汲取可能造成额外伤害） */
        return applyLifesteal(player, weapon, target, totalDamage);
    }

    /* ==================== 内部：合并玩家+物品属性 ==================== */

    /**
     * 合并玩家属性 + 装备中所有物品属性。
     * <p>
     * 当前玩家属性来源为空（由职业模块未来注册），
     * 物品属性直接从物品 PDC 读取并 roll。
     */
    private static Map<RpgAttribute, Double> mergeAttrs(Player player, ItemStack weapon) {
        Map<RpgAttribute, Double> result = new java.util.LinkedHashMap<>();

        /* 1. 合并物品属性（主手武器） */
        mergeItemRanges(result, weapon);

        /* 2. 合并玩家属性（由注册的来源提供） */
        for (ItemAttributeConfig.PlayerAttributeSource src : playerSources) {
            Map<RpgAttribute, Double> playerAttrs = src.getAllAttributes(player);
            if (playerAttrs == null) continue;
            for (Map.Entry<RpgAttribute, Double> e : playerAttrs.entrySet()) {
                RpgAttribute attr = e.getKey();
                double val = attr.clamp(e.getValue());
                result.merge(attr, val, Double::sum);
            }
        }

        return result;
    }

    /**
     * 从物品读取区间属性并 roll，累加到结果 Map。
     */
    private static void mergeItemRanges(Map<RpgAttribute, Double> result, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;

        for (RpgAttribute attr : RpgAttribute.values()) {
            AttributeRange range = AttributeStorage.getItemAttrRange(item, attr);
            if (range.getMin() == attr.getDefaultValue() && range.getMax() == attr.getDefaultValue()) {
                continue; // 未设置，跳过
            }
            double rolled = range.roll();
            result.merge(attr, rolled, Double::sum);
        }
    }

    /* ==================== 内部：公式计算 ==================== */

    /**
     * 根据合并后的属性计算伤害。
     */
    private static double compute(DamageType type, Map<RpgAttribute, Double> attrs) {
        double damage = getAttr(attrs, RpgAttribute.DAMAGE);
        double damageBonus = getAttr(attrs, RpgAttribute.DAMAGE_BONUS);

        double specificDamage;
        double specificBonus;

        if (type == DamageType.MELEE) {
            specificDamage = getAttr(attrs, RpgAttribute.MELEE_DAMAGE);
            specificBonus = getAttr(attrs, RpgAttribute.MELEE_BONUS);
        } else if (type == DamageType.GUN) {
            specificDamage = getAttr(attrs, RpgAttribute.GUN_DAMAGE);
            specificBonus = getAttr(attrs, RpgAttribute.GUN_BONUS);

            // 特殊武器伤害覆写
            double crossbowDmg = getAttr(attrs, RpgAttribute.GUN_CROSSBOW_DAMAGE);
            if (crossbowDmg > 0) specificDamage = crossbowDmg;

            double grenadeDmg = getAttr(attrs, RpgAttribute.GUN_GRENADE_DAMAGE);
            if (grenadeDmg > 0) specificDamage = grenadeDmg;

            double rocketDmg = getAttr(attrs, RpgAttribute.GUN_ROCKET_DAMAGE);
            if (rocketDmg > 0) specificDamage = rocketDmg;

            double laserDmg = getAttr(attrs, RpgAttribute.GUN_LASER_DAMAGE);
            if (laserDmg > 0) specificDamage = laserDmg;

            double flameDmg = getAttr(attrs, RpgAttribute.GUN_FLAME_DAMAGE_PER_TICK);
            if (flameDmg > 0) specificDamage = flameDmg;
        } else {
            specificDamage = getAttr(attrs, RpgAttribute.PROJECTILE_DAMAGE);
            specificBonus = getAttr(attrs, RpgAttribute.PROJECTILE_BONUS);
        }

        /* 近战/射弹/枪械 = 专属×专属加成% + 通用×通用加成% */
        double result = specificDamage * (1.0 + specificBonus / 100.0)
                      + damage * (1.0 + damageBonus / 100.0);

        return Math.max(0.0, result);
    }

    /** 安全取属性值，不存在时返回该属性的默认值 */
    private static double getAttr(Map<RpgAttribute, Double> attrs, RpgAttribute attr) {
        Double val = attrs.get(attr);
        return val != null ? Math.max(0.0, val) : attr.getDefaultValue();
    }

    /* ==================== 暴击计算 ==================== */

    /**
     * 暴击判定并应用暴击伤害。
     * <p>
     * 公式：
     * <ul>
     *   <li>ADD 模式：最终暴击率 = critDefault + critical_multiplier（相加）</li>
     *   <li>MULTIPLY 模式：最终暴击率 = critDefault + critDefault × critical_multiplier（默认值乘倍率）</li>
     * </ul>
     * 暴击后：damage × (1 + 最终暴击率 / 100)
     *
     * @return 暴击后的伤害值（未触发则返回原值）
     */
    /** 供外部射线系统调用的暴击判定 */
    public static double applyCrit(Player player, ItemStack weapon, double totalDamage) {
        /* 从物品读取暴击概率并 roll */
        AttributeRange chanceRange = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.CRITICAL_CHANCE);
        double critChance = Math.max(0, chanceRange.roll());

        /* 合并玩家暴击概率（职业模块可注入） */
        for (ItemAttributeConfig.PlayerAttributeSource src : playerSources) {
            double playerChance = src.getAttribute(player, RpgAttribute.CRITICAL_CHANCE);
            critChance += Math.max(0, playerChance);
        }
        critChance = Math.min(100.0, critChance);

        if (critChance <= 0) return totalDamage;

        /* 暴击判定：roll 0~100，小于等于 critChance 即为暴击 */
        double roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100.0;
        if (roll > critChance) return totalDamage;

        /* 读取暴击倍率 */
        AttributeRange multRange = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.CRITICAL_MULTIPLIER);
        double critMult = Math.max(0, multRange.roll());

        for (ItemAttributeConfig.PlayerAttributeSource src : playerSources) {
            double playerMult = src.getAttribute(player, RpgAttribute.CRITICAL_MULTIPLIER);
            critMult += Math.max(0, playerMult);
        }

        /* 根据模式计算最终暴击加成 */
        double finalCritPct;
        if (critMode == CritMode.MULTIPLY) {
            /* 乘法：critDefault + critDefault × critMult% */
            finalCritPct = critDefault + critDefault * (critMult / 100.0);
        } else {
            /* 加法：critDefault + critMult */
            finalCritPct = critDefault + critMult;
        }

        double result = totalDamage * (1.0 + finalCritPct / 100.0);
        return Math.max(0.0, result);
    }

    /* ==================== 吸血计算 ==================== */

    /**
     * 吸血判定。三机制共享 lifesteal_chance，各自独立 roll：
     * <ol>
     *   <li><b>偷取</b>：回血 = 最终伤害 × 偷取率%（偷取率受倍率+模式影响）</li>
     *   <li><b>固定吸血</b>：回血 = lifesteal_flat（不受伤害/倍率影响）</li>
     *   <li><b>汲取</b>：额外伤害 = min(drain, 目标剩余血)，回血 = 实际额外 × (1 + 倍率%)</li>
     * </ol>
     *
     * @return DamageResult（damage=含汲取的最终伤害，heal=总吸血回复量）
     */
    /** 供外部射线系统调用的吸血判定 */
    public static DamageResult applyLifesteal(Player player, ItemStack weapon, LivingEntity target, double totalDamage) {
        if (totalDamage <= 0 || weapon == null || !weapon.hasItemMeta()) {
            return new DamageResult(totalDamage, 0);
        }

        /* 读取吸血概率并 roll */
        AttributeRange chanceRange = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.LIFESTEAL_CHANCE);
        double lsChance = Math.max(0, chanceRange.roll());

        for (ItemAttributeConfig.PlayerAttributeSource src : playerSources) {
            lsChance += Math.max(0, src.getAttribute(player, RpgAttribute.LIFESTEAL_CHANCE));
        }
        lsChance = Math.min(100.0, lsChance);

        if (lsChance <= 0) return new DamageResult(totalDamage, 0);

        double totalHeal = 0;
        double extraDamage = 0;

        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();

        /* 机制1：偷取 — 按最终伤害百分比回血 */
        if (rng.nextDouble() * 100.0 <= lsChance) {
            AttributeRange multRange = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.LIFESTEAL_MULTIPLIER);
            double lsMult = Math.max(0, multRange.roll());

            for (ItemAttributeConfig.PlayerAttributeSource src : playerSources) {
                lsMult += Math.max(0, src.getAttribute(player, RpgAttribute.LIFESTEAL_MULTIPLIER));
            }

            double stealPct;
            if (lifestealMode == LifestealMode.MULTIPLY) {
                stealPct = lifestealDefault + lifestealDefault * (lsMult / 100.0);
            } else {
                stealPct = lifestealDefault + lsMult;
            }

            totalHeal += totalDamage * (stealPct / 100.0);
        }

        /* 机制2：固定吸血 — 恢复固定血量，不受伤害/倍率影响 */
        if (rng.nextDouble() * 100.0 <= lsChance) {
            AttributeRange flatRange = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.LIFESTEAL_FLAT);
            double flatHeal = Math.max(0, flatRange.roll());

            for (ItemAttributeConfig.PlayerAttributeSource src : playerSources) {
                flatHeal += Math.max(0, src.getAttribute(player, RpgAttribute.LIFESTEAL_FLAT));
            }

            totalHeal += flatHeal;
        }

        /* 机制3：汲取 — 造成额外伤害并按倍率回血 */
        if (rng.nextDouble() * 100.0 <= lsChance) {
            AttributeRange drainRange = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.LIFESTEAL_DRAIN);
            double drainVal = Math.max(0, drainRange.roll());

            for (ItemAttributeConfig.PlayerAttributeSource src : playerSources) {
                drainVal += Math.max(0, src.getAttribute(player, RpgAttribute.LIFESTEAL_DRAIN));
            }

            if (drainVal > 0 && target != null) {
                double actualDrain = Math.min(drainVal, target.getHealth());

                /* 回血 = 实际额外伤害 × (1 + 倍率%) */
                AttributeRange multRange = AttributeStorage.getItemAttrRange(weapon, RpgAttribute.LIFESTEAL_MULTIPLIER);
                double lsMult = Math.max(0, multRange.roll());

                for (ItemAttributeConfig.PlayerAttributeSource src : playerSources) {
                    lsMult += Math.max(0, src.getAttribute(player, RpgAttribute.LIFESTEAL_MULTIPLIER));
                }

                double drainHeal = actualDrain * (1.0 + lsMult / 100.0);
                totalHeal += drainHeal;
                extraDamage = actualDrain;
            }
        }

        return new DamageResult(totalDamage + extraDamage, totalHeal);
    }
}
