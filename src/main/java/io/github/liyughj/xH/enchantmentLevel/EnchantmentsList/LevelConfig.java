package io.github.liyughj.xH.enchantmentLevel.EnchantmentsList;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 附魔等级效果配置管理类
 * 管理各附魔每级的实际效果参数，使用独立的 Level.yml 文件
 */
public class LevelConfig {

    /* 配置文件名 */
    private static final String CONFIG_FILE_NAME = "Level.yml";

    /* 默认值 */
    private static final double DEFAULT_DAMAGE_PERCENT = 10.0;
    private static final double DEFAULT_SWEEP_PERCENT = 10.0;
    private static final double DEFAULT_SWEEP_RANGE = 3.0;       // 横扫范围（格）
    private static final int DEFAULT_FIRE_TICKS = 80;           // 4秒 = 80 tick
    private static final double DEFAULT_KNOCKBACK_BLOCKS = 1.0; // 1格
    private static final double DEFAULT_LOOTING_MAX_DROP_PERCENT = 10.0;
    private static final double DEFAULT_LOOTING_RARE_CHANCE_PERCENT = 1.0;
    private static final double DEFAULT_CHAIN_RANGE = 5.0;      // 连锁挖掘搜索范围（格）
    private static final double DEFAULT_SILK_TOUCH_CHANCE = 10.0;   // 精准采集每级10%概率不破坏
    private static final double DEFAULT_FORTUNE_BASE_PROB = 50.0;   // 时运1级触发概率（%）
    private static final double DEFAULT_FORTUNE_DECREMENT = 5.0;    // 时运每高1级概率削减（%）
    private static final double DEFAULT_UNBREAKING_SAVE = 7.5;      // 每级不消耗耐久概率（%）
    private static final double DEFAULT_UNBREAKING_RETURN = 2.5;    // 每级返还耐久概率（%）
    private static final double DEFAULT_UNBREAKING_RETURN_RATE = 50.0; // 每级返还耐久倍率（%）
    private static final double DEFAULT_MENDING_DURABILITY_PER_XP = 1.0; // 每级每经验修复耐久点数
    private static final double DEFAULT_PROTECTION_PERCENT = 2.0;    // 每级伤害减免百分比（%）
    private static final double DEFAULT_THORNS_CHANCE = 2.0;          // 每级荆棘触发概率（%）
    private static final double DEFAULT_THORNS_DAMAGE = 2.0;          // 每级荆棘反伤百分比（%）
    private static final double DEFAULT_RESPIRATION_SECONDS = 3.0;    // 每级水下呼吸额外秒数
    private static final double DEFAULT_AQUA_AFFINITY_CHAIN_PERCENT = 5.0; // 每级恢复链挖掘效能（%）

    /* 配置键名 */
    private static final String KEY_DAMAGE_PERCENT = "damage-percent-per-level";
    private static final String KEY_SWEEP_PERCENT = "sweep-damage-percent-per-level";
    private static final String KEY_SWEEP_RANGE = "sweep-range";
    private static final String KEY_FIRE_TICKS = "fire-ticks-per-level";
    private static final String KEY_KNOCKBACK_BLOCKS = "knockback-blocks-per-level";
    private static final String KEY_LOOTING_MAX_DROP = "looting-max-drop-percent-per-level";
    private static final String KEY_LOOTING_RARE_CHANCE = "looting-rare-chance-percent-per-level";
    private static final String KEY_CHAIN_RANGE = "chain-range";
    private static final String KEY_SILK_TOUCH_CHANCE = "keep-block-chance-per-level";
    private static final String KEY_FORTUNE_BASE_PROB = "fortune-base-probability";
    private static final String KEY_FORTUNE_DECREMENT = "fortune-probability-decrement";
    private static final String KEY_UNBREAKING_SAVE = "save-chance-per-level";
    private static final String KEY_UNBREAKING_RETURN = "return-chance-per-level";
    private static final String KEY_UNBREAKING_RETURN_RATE = "return-rate-per-level";
    private static final String KEY_MENDING_DURABILITY = "mending-durability-per-xp";
    private static final String KEY_PROTECTION_PERCENT = "protection-percent-per-level";
    private static final String KEY_THORNS_CHANCE = "thorns-chance-per-level";
    private static final String KEY_THORNS_DAMAGE = "thorns-damage-per-level";
    private static final String KEY_RESPIRATION_SECONDS = "respiration-seconds-per-level";
    private static final String KEY_AQUA_AFFINITY_CHAIN_PERCENT = "aqua-affinity-chain-percent-per-level";

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    /* 附魔 key（大写） -> 效果参数 */
    private final Map<String, EnchantEffect> effects = new HashMap<>();

    /*
     * ==================== 附魔加成覆盖接口 ====================
     * 外部模块（如赛季加成、公会加成等）可以在不修改配置文件的情况下，
     * 在附魔系统内部叠加额外加成。bonus 是固定值，直接加在最终结果上。
     *
     * 例如：锋利配置 per-level=10%，外部注入 +5% bonus
     *      锋利 X = 100%（per-level × 10）+ 5%（bonus）= 105%
     *
     * 用法：
     *   LevelConfig lc = XH.getInstance().getLevelConfig();
     *   lc.setBonusDamagePercent("sharpness", 5.0);  // sharpness 最终+5%
     */
    private final ConcurrentHashMap<String, Double> bonusOverrides = new ConcurrentHashMap<>();

    private static String bonusKey(String enchantKey, String field) {
        return enchantKey.toUpperCase() + "." + field;
    }

    /** 伤害加成 bonus（锋利/亡灵/节肢） */
    public void setBonusDamagePercent(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "damage"), bonus);
    }

    /** 横扫伤害加成 bonus */
    public void setBonusSweepPercent(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "sweep"), bonus);
    }

    /** 横扫范围 bonus（格） */
    public void setBonusSweepRange(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "sweep_range"), bonus);
    }

    /** 火焰燃烧 bonus（tick/级） */
    public void setBonusFireTicks(String enchantKey, int bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "fire"), (double) bonus);
    }

    /** 击退距离 bonus（格/级） */
    public void setBonusKnockbackBlocks(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "knockback"), bonus);
    }

    /** 抢夺最大掉落 bonus（%/级） */
    public void setBonusLootingMaxDrop(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "looting_max_drop"), bonus);
    }

    /** 抢夺稀有概率 bonus（%/级） */
    public void setBonusLootingRareChance(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "looting_rare"), bonus);
    }

    /** 连锁挖掘范围 bonus（格） */
    public void setBonusChainRange(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "chain_range"), bonus);
    }

    /** 精准采集保留概率 bonus（%/级） */
    public void setBonusSilkTouchChance(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "silk_touch"), bonus);
    }

    /** 时运基础概率 bonus（%） */
    public void setBonusFortuneBaseProb(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "fortune_base"), bonus);
    }

    /** 时运概率削减 bonus（%）*/
    public void setBonusFortuneDecrement(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "fortune_decrement"), bonus);
    }

    /** 耐久不消耗概率 bonus（%/级） */
    public void setBonusUnbreakingSave(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "unbreaking_save"), bonus);
    }

    /** 耐久返还概率 bonus（%/级） */
    public void setBonusUnbreakingReturn(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "unbreaking_return"), bonus);
    }

    /** 耐久返还倍率 bonus（%/级） */
    public void setBonusUnbreakingReturnRate(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "unbreaking_return_rate"), bonus);
    }

    /** 经验修补耐久倍率 bonus（耐久/经验） */
    public void setBonusMendingDurability(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "mending"), bonus);
    }

    /** 保护伤害减免 bonus（%/全套） */
    public void setBonusProtectionPercent(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "protection"), bonus);
    }

    /** 火焰保护燃烧时间减免 bonus（%/全套） */
    public void setBonusFireTickPercent(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "fire_tick"), bonus);
    }

    /** 爆炸保护击退减免 bonus（%/全套） */
    public void setBonusBlastKnockbackPercent(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "blast_knockback"), bonus);
    }

    /** 弹射物保护击退减免 bonus（%/全套） */
    public void setBonusProjectileKnockbackPercent(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "projectile_knockback"), bonus);
    }

    /** 荆棘触发概率 bonus（%/全套） */
    public void setBonusThornsChance(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "thorns_chance"), bonus);
    }

    /** 荆棘反伤百分比 bonus（%/全套） */
    public void setBonusThornsDamage(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "thorns_damage"), bonus);
    }

    /** 水下呼吸时间 bonus（秒） */
    public void setBonusRespirationSeconds(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "respiration"), bonus);
    }

    /** 水下速掘链挖掘效能 bonus（%/全套） */
    public void setBonusAquaAffinityChainPercent(String enchantKey, double bonus) {
        bonusOverrides.put(bonusKey(enchantKey, "aqua_affinity"), bonus);
    }

    /** 清除指定附魔的所有 bonus */
    public void clearBonuses(String enchantKey) {
        String prefix = enchantKey.toUpperCase() + ".";
        bonusOverrides.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** 清除全部 bonus */
    public void clearAllBonuses() {
        bonusOverrides.clear();
    }

    private double getBonus(String enchantKey, String field) {
        Double v = bonusOverrides.get(bonusKey(enchantKey, field));
        return v != null ? v : 0.0;
    }

    /* ==================== 原 Getter（含 bonus 叠加） ==================== */

    /**
     * 附魔效果参数记录
     */
    public static class EnchantEffect {
        public final String enchantKey;
        public final double damagePercentPerLevel;
        public final double sweepDamagePercentPerLevel;
        public final double sweepRange;
        public final int fireTicksPerLevel;
        public final double knockbackBlocksPerLevel;
        public final double lootingMaxDropPercentPerLevel;
        public final double lootingRareChancePercentPerLevel;
        public final double chainRange;
        public final double silkTouchKeepChancePerLevel;
        public final double fortuneBaseProbability;
        public final double fortuneProbDecrement;
        public final double unbreakingSaveChancePerLevel;
        public final double unbreakingReturnChancePerLevel;
        public final double unbreakingReturnRatePerLevel;
        public final double mendingDurabilityPerXp;
        public final double protectionPercentPerLevel;
        public final double thornsChancePerLevel;
        public final double thornsDamagePerLevel;
        public final double respirationSecondsPerLevel;
        public final double aquaAffinityChainPercentPerLevel;

        public EnchantEffect(String enchantKey) {
            this.enchantKey = enchantKey;
            this.damagePercentPerLevel = 0;
            this.sweepDamagePercentPerLevel = 0;
            this.sweepRange = 0;
            this.fireTicksPerLevel = 0;
            this.knockbackBlocksPerLevel = 0;
            this.lootingMaxDropPercentPerLevel = 0;
            this.lootingRareChancePercentPerLevel = 0;
            this.chainRange = 0;
            this.silkTouchKeepChancePerLevel = 0;
            this.fortuneBaseProbability = 0;
            this.fortuneProbDecrement = 0;
            this.unbreakingSaveChancePerLevel = 0;
            this.unbreakingReturnChancePerLevel = 0;
            this.unbreakingReturnRatePerLevel = 0;
            this.mendingDurabilityPerXp = 0;
            this.protectionPercentPerLevel = 0;
            this.thornsChancePerLevel = 0;
            this.thornsDamagePerLevel = 0;
            this.respirationSecondsPerLevel = 0;
            this.aquaAffinityChainPercentPerLevel = 0;
        }

        public EnchantEffect(String enchantKey, double damagePercentPerLevel, double sweepDamagePercentPerLevel,
                             double sweepRange, int fireTicksPerLevel, double knockbackBlocksPerLevel,
                             double lootingMaxDropPercentPerLevel, double lootingRareChancePercentPerLevel,
                             double chainRange, double silkTouchKeepChancePerLevel,
                             double fortuneBaseProbability, double fortuneProbDecrement,
                              double unbreakingSaveChance, double unbreakingReturnChance,
                               double unbreakingReturnRate, double mendingDurabilityPerXp,
                               double protectionPercentPerLevel,
                               double thornsChancePerLevel, double thornsDamagePerLevel,
                               double respirationSecondsPerLevel,
                               double aquaAffinityChainPercentPerLevel) {
            this.enchantKey = enchantKey;
            this.damagePercentPerLevel = damagePercentPerLevel;
            this.sweepDamagePercentPerLevel = sweepDamagePercentPerLevel;
            this.sweepRange = sweepRange;
            this.fireTicksPerLevel = fireTicksPerLevel;
            this.knockbackBlocksPerLevel = knockbackBlocksPerLevel;
            this.lootingMaxDropPercentPerLevel = lootingMaxDropPercentPerLevel;
            this.lootingRareChancePercentPerLevel = lootingRareChancePercentPerLevel;
            this.chainRange = chainRange;
            this.silkTouchKeepChancePerLevel = silkTouchKeepChancePerLevel;
            this.fortuneBaseProbability = fortuneBaseProbability;
            this.fortuneProbDecrement = fortuneProbDecrement;
            this.unbreakingSaveChancePerLevel = unbreakingSaveChance;
            this.unbreakingReturnChancePerLevel = unbreakingReturnChance;
            this.unbreakingReturnRatePerLevel = unbreakingReturnRate;
            this.mendingDurabilityPerXp = mendingDurabilityPerXp;
            this.protectionPercentPerLevel = protectionPercentPerLevel;
            this.thornsChancePerLevel = thornsChancePerLevel;
            this.thornsDamagePerLevel = thornsDamagePerLevel;
            this.respirationSecondsPerLevel = respirationSecondsPerLevel;
            this.aquaAffinityChainPercentPerLevel = aquaAffinityChainPercentPerLevel;
        }
    }

    /**
     * 构造函数
     */
    public LevelConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE_NAME);
        loadConfig();
    }

    private void loadConfig() {
        if (!configFile.exists()) {
            createDefaultConfig();
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);
        loadAll();
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);
        loadAll();
        plugin.getLogger().info("配置文件 " + CONFIG_FILE_NAME + " 已重新加载");
    }

    private void createDefaultConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        YamlConfiguration cfg = new YamlConfiguration();

        /* 锋利/亡灵杀手/节肢杀手：每级+10%伤害 */
        cfg.set("sharpness." + KEY_DAMAGE_PERCENT, DEFAULT_DAMAGE_PERCENT);
        cfg.set("smite." + KEY_DAMAGE_PERCENT, DEFAULT_DAMAGE_PERCENT);
        cfg.set("bane_of_arthropods." + KEY_DAMAGE_PERCENT, DEFAULT_DAMAGE_PERCENT);

        /* 横扫之刃：每级+10%横扫伤害，范围3格 */
        cfg.set("sweeping_edge." + KEY_SWEEP_PERCENT, DEFAULT_SWEEP_PERCENT);
        cfg.set("sweeping_edge." + KEY_SWEEP_RANGE, DEFAULT_SWEEP_RANGE);

        /* 火焰附加：每级4秒燃烧 */
        cfg.set("fire_aspect." + KEY_FIRE_TICKS, DEFAULT_FIRE_TICKS);

        /* 击退：每级1格 */
        cfg.set("knockback." + KEY_KNOCKBACK_BLOCKS, DEFAULT_KNOCKBACK_BLOCKS);

        /* 抢夺：最大掉落+10%，稀有概率+1% */
        cfg.set("looting." + KEY_LOOTING_MAX_DROP, DEFAULT_LOOTING_MAX_DROP_PERCENT);
        cfg.set("looting." + KEY_LOOTING_RARE_CHANCE, DEFAULT_LOOTING_RARE_CHANCE_PERCENT);

        /* 效率：连锁挖掘范围5格（每级额外破坏1个同类型方块） */
        cfg.set("efficiency." + KEY_CHAIN_RANGE, DEFAULT_CHAIN_RANGE);

        /* 精准采集：每级10%概率不破坏矿物原矿但掉落方块 */
        cfg.set("silk_touch." + KEY_SILK_TOUCH_CHANCE, DEFAULT_SILK_TOUCH_CHANCE);

        /* 时运：级联概率掉率系统 */
        cfg.set("fortune." + KEY_FORTUNE_BASE_PROB, DEFAULT_FORTUNE_BASE_PROB);
        cfg.set("fortune." + KEY_FORTUNE_DECREMENT, DEFAULT_FORTUNE_DECREMENT);

        /* 耐久：每级7.5%概率不消耗 / 2.5%概率返还耐久 */
        cfg.set("unbreaking." + KEY_UNBREAKING_SAVE, DEFAULT_UNBREAKING_SAVE);
        cfg.set("unbreaking." + KEY_UNBREAKING_RETURN, DEFAULT_UNBREAKING_RETURN);
        cfg.set("unbreaking." + KEY_UNBREAKING_RETURN_RATE, DEFAULT_UNBREAKING_RETURN_RATE);

        /* 经验修补：每经验修复 = 等级 × key-multiplier 点耐久 */
        cfg.set("mending." + KEY_MENDING_DURABILITY, DEFAULT_MENDING_DURABILITY_PER_XP);

        /* 保护类：每级 2% 伤害减免（全套保护 X = 80%） */
        cfg.set("protection." + KEY_PROTECTION_PERCENT, DEFAULT_PROTECTION_PERCENT);
        cfg.set("fire_protection." + KEY_PROTECTION_PERCENT, DEFAULT_PROTECTION_PERCENT);
        cfg.set("blast_protection." + KEY_PROTECTION_PERCENT, DEFAULT_PROTECTION_PERCENT);
        cfg.set("projectile_protection." + KEY_PROTECTION_PERCENT, DEFAULT_PROTECTION_PERCENT);
        cfg.set("feather_falling." + KEY_PROTECTION_PERCENT, DEFAULT_PROTECTION_PERCENT);

        /* 荆棘：每级 2% 触发概率 / 2% 反伤（全套荆棘 X = 80%/80%） */
        cfg.set("thorns." + KEY_THORNS_CHANCE, DEFAULT_THORNS_CHANCE);
        cfg.set("thorns." + KEY_THORNS_DAMAGE, DEFAULT_THORNS_DAMAGE);

        /* 水下呼吸：每级 +3s 水下呼吸时间（满级 X = 30s） */
        cfg.set("respiration." + KEY_RESPIRATION_SECONDS, DEFAULT_RESPIRATION_SECONDS);

        /* 水下速掘：每级恢复 5% 链挖掘效能（满级 X = 50%） */
        cfg.set("aqua_affinity." + KEY_AQUA_AFFINITY_CHAIN_PERCENT, DEFAULT_AQUA_AFFINITY_CHAIN_PERCENT);

        List<String> headerLines = Arrays.asList(
            "附魔等级效果配置文件",
            "格式：",
            "  <附魔key>:",
            "    damage-percent-per-level: <每级伤害百分比>",
            "    sweep-damage-percent-per-level: <每级横扫伤害百分比>",
            "    sweep-range: <横扫范围（格），默认3.0>",
            "    fire-ticks-per-level: <每级燃烧tick数>",
            "    knockback-blocks-per-level: <每级击退格数>",
            "    looting-max-drop-percent-per-level: <每级最大掉落增加百分比>",
            "    looting-rare-chance-percent-per-level: <每级稀有掉落概率增加百分比>",
            "    chain-range: <连锁挖掘搜索同类型方块范围（格），默认5.0>",
            "    keep-block-chance-per-level: <精准采集每级不破坏方块概率（%），默认10.0>",
            "    fortune-base-probability: <时运1级触发概率（%），默认50.0>",
            "    fortune-probability-decrement: <时运每高1级概率削减（%），默认5.0>",
            "    save-chance-per-level: <耐久每级不消耗概率（%），默认7.5>",
            "    return-chance-per-level: <耐久每级返还概率（%），默认2.5>",
            "    return-rate-per-level: <耐久每级返还倍率（%），默认50.0>",
            "    mending-durability-per-xp: <每等级每经验修复耐久点数，默认1.0>",
            "    protection-percent-per-level: <每级伤害减免（%），默认2.0，全套保护X=80%>",
            "    thorns-chance-per-level: <每级荆棘触发概率（%），默认2.0>",
            "    thorns-damage-per-level: <每级荆棘反伤百分比（%），默认2.0>",
            "    respiration-seconds-per-level: <每级水下呼吸额外秒数，默认3.0，满级X=30s>",
            "    aqua-affinity-chain-percent-per-level: <每级恢复链挖掘效能（%），默认5.0，满级X=50%>"
        );
        cfg.options().setHeader(headerLines);

        try {
            cfg.save(configFile);
            plugin.getLogger().info("已创建默认配置文件: " + CONFIG_FILE_NAME);
        } catch (Exception e) {
            plugin.getLogger().warning("创建配置文件失败: " + e.getMessage());
        }
    }

    private void loadAll() {
        effects.clear();

        for (String key : config.getKeys(false)) {
            String normalizedKey = key.toUpperCase();
            EnchantEffect existing = effects.get(normalizedKey);
            EnchantEffect effect = existing != null ? existing : new EnchantEffect(key);

            double damagePercent = config.getDouble(key + "." + KEY_DAMAGE_PERCENT,
                effect.damagePercentPerLevel);
            double sweepPercent = config.getDouble(key + "." + KEY_SWEEP_PERCENT,
                effect.sweepDamagePercentPerLevel);
            double sweepRange = config.getDouble(key + "." + KEY_SWEEP_RANGE,
                effect.sweepRange);
            int fireTicks = config.getInt(key + "." + KEY_FIRE_TICKS,
                effect.fireTicksPerLevel);
            double knockbackBlocks = config.getDouble(key + "." + KEY_KNOCKBACK_BLOCKS,
                effect.knockbackBlocksPerLevel);
            double lootingMaxDrop = config.getDouble(key + "." + KEY_LOOTING_MAX_DROP,
                effect.lootingMaxDropPercentPerLevel);
            double lootingRareChance = config.getDouble(key + "." + KEY_LOOTING_RARE_CHANCE,
                effect.lootingRareChancePercentPerLevel);
            double chainRange = config.getDouble(key + "." + KEY_CHAIN_RANGE,
                effect.chainRange);
            double silkTouchChance = config.getDouble(key + "." + KEY_SILK_TOUCH_CHANCE,
                effect.silkTouchKeepChancePerLevel);
            double fortuneBaseProb = config.getDouble(key + "." + KEY_FORTUNE_BASE_PROB,
                effect.fortuneBaseProbability);
            double fortuneDecrement = config.getDouble(key + "." + KEY_FORTUNE_DECREMENT,
                effect.fortuneProbDecrement);
            double unbreakingSave = config.getDouble(key + "." + KEY_UNBREAKING_SAVE,
                effect.unbreakingSaveChancePerLevel);
            double unbreakingReturn = config.getDouble(key + "." + KEY_UNBREAKING_RETURN,
                effect.unbreakingReturnChancePerLevel);
            double unbreakingReturnRate = config.getDouble(key + "." + KEY_UNBREAKING_RETURN_RATE,
                effect.unbreakingReturnRatePerLevel);
            double mendingDurability = config.getDouble(key + "." + KEY_MENDING_DURABILITY,
                effect.mendingDurabilityPerXp);
            double protectionPercent = config.getDouble(key + "." + KEY_PROTECTION_PERCENT,
                effect.protectionPercentPerLevel);
            double thornsChance = config.getDouble(key + "." + KEY_THORNS_CHANCE,
                effect.thornsChancePerLevel);
            double thornsDamage = config.getDouble(key + "." + KEY_THORNS_DAMAGE,
                effect.thornsDamagePerLevel);
            double respirationSeconds = config.getDouble(key + "." + KEY_RESPIRATION_SECONDS,
                effect.respirationSecondsPerLevel);
            double aquaAffinityChainPercent = config.getDouble(key + "." + KEY_AQUA_AFFINITY_CHAIN_PERCENT,
                effect.aquaAffinityChainPercentPerLevel);

            effects.put(normalizedKey, new EnchantEffect(key,
                damagePercent, sweepPercent, sweepRange, fireTicks, knockbackBlocks,
                lootingMaxDrop, lootingRareChance, chainRange, silkTouchChance,
                fortuneBaseProb, fortuneDecrement,
                unbreakingSave, unbreakingReturn, unbreakingReturnRate, mendingDurability,
                protectionPercent, thornsChance, thornsDamage, respirationSeconds,
                aquaAffinityChainPercent));
        }
    }

    /* ========== Getter（配置原始值，不含 bonus） ========== */

    public double getDamagePercentPerLevel(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.damagePercentPerLevel : 0.0;
    }

    public boolean hasDamageEffect(String enchantKey) {
        return getDamagePercentPerLevel(enchantKey) > 0.0;
    }

    public double getSweepDamagePercentPerLevel(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.sweepDamagePercentPerLevel : 0.0;
    }

    public boolean hasSweepEffect(String enchantKey) {
        return getSweepDamagePercentPerLevel(enchantKey) > 0.0;
    }

    public double getSweepRange(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.sweepRange : 0.0;
    }

    public int getFireTicksPerLevel(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.fireTicksPerLevel : 0;
    }

    public boolean hasFireEffect(String enchantKey) {
        return getFireTicksPerLevel(enchantKey) > 0;
    }

    public double getKnockbackBlocksPerLevel(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.knockbackBlocksPerLevel : 0.0;
    }

    public boolean hasKnockbackEffect(String enchantKey) {
        return getKnockbackBlocksPerLevel(enchantKey) > 0.0;
    }

    public double getLootingMaxDropPercentPerLevel(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.lootingMaxDropPercentPerLevel : 0.0;
    }

    public double getLootingRareChancePercentPerLevel(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.lootingRareChancePercentPerLevel : 0.0;
    }

    public boolean hasLootingEffect(String enchantKey) {
        return getLootingMaxDropPercentPerLevel(enchantKey) > 0.0
            || getLootingRareChancePercentPerLevel(enchantKey) > 0.0;
    }

    public double getChainRange(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.chainRange : 0.0;
    }

    public boolean hasChainEffect(String enchantKey) {
        return getChainRange(enchantKey) > 0.0;
    }

    public double getSilkTouchKeepChancePerLevel(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.silkTouchKeepChancePerLevel : 0.0;
    }

    public boolean hasSilkTouchEffect(String enchantKey) {
        return getSilkTouchKeepChancePerLevel(enchantKey) > 0.0;
    }

    public double getFortuneBaseProbability(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.fortuneBaseProbability : DEFAULT_FORTUNE_BASE_PROB;
    }

    public double getFortuneProbDecrement(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.fortuneProbDecrement : DEFAULT_FORTUNE_DECREMENT;
    }

    public boolean hasFortuneEffect(String enchantKey) {
        return effects.containsKey(enchantKey.toUpperCase());
    }

    public double getUnbreakingSaveChancePerLevel(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.unbreakingSaveChancePerLevel : DEFAULT_UNBREAKING_SAVE;
    }

    public double getUnbreakingReturnChancePerLevel(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.unbreakingReturnChancePerLevel : DEFAULT_UNBREAKING_RETURN;
    }

    public double getUnbreakingReturnRatePerLevel(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.unbreakingReturnRatePerLevel : DEFAULT_UNBREAKING_RETURN_RATE;
    }

    public boolean hasUnbreakingEffect(String enchantKey) {
        return effects.containsKey(enchantKey.toUpperCase());
    }

    public double getMendingDurabilityPerXp(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.mendingDurabilityPerXp : DEFAULT_MENDING_DURABILITY_PER_XP;
    }

    public boolean hasMendingEffect(String enchantKey) {
        return effects.containsKey(enchantKey.toUpperCase())
            && getMendingDurabilityPerXp(enchantKey) > 0.0;
    }

    public double getProtectionPercentPerLevel(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.protectionPercentPerLevel : 0.0;
    }

    public boolean hasProtectionEffect(String enchantKey) {
        return getProtectionPercentPerLevel(enchantKey) > 0.0;
    }

    public double getThornsChancePerLevel(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.thornsChancePerLevel : 0.0;
    }

    public double getThornsDamagePerLevel(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.thornsDamagePerLevel : 0.0;
    }

    public boolean hasThornsEffect(String enchantKey) {
        return getThornsChancePerLevel(enchantKey) > 0.0;
    }

    public double getRespirationSecondsPerLevel(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.respirationSecondsPerLevel : 0.0;
    }

    public boolean hasRespirationEffect(String enchantKey) {
        return getRespirationSecondsPerLevel(enchantKey) > 0.0;
    }

    public double getAquaAffinityChainPercentPerLevel(String enchantKey) {
        EnchantEffect e = effects.get(enchantKey.toUpperCase());
        return e != null ? e.aquaAffinityChainPercentPerLevel : 0.0;
    }

    public boolean hasAquaAffinityChainEffect(String enchantKey) {
        return getAquaAffinityChainPercentPerLevel(enchantKey) > 0.0;
    }

    /* ========== Bonus Getter（外部注入的固定加成值） ========== */

    public double getDamagePercentBonus(String enchantKey) {
        return getBonus(enchantKey, "damage");
    }

    public double getSweepPercentBonus(String enchantKey) {
        return getBonus(enchantKey, "sweep");
    }

    public double getSweepRangeBonus(String enchantKey) {
        return getBonus(enchantKey, "sweep_range");
    }

    public int getFireTicksBonus(String enchantKey) {
        return (int) getBonus(enchantKey, "fire");
    }

    public double getKnockbackBlocksBonus(String enchantKey) {
        return getBonus(enchantKey, "knockback");
    }

    public double getLootingMaxDropBonus(String enchantKey) {
        return getBonus(enchantKey, "looting_max_drop");
    }

    public double getLootingRareChanceBonus(String enchantKey) {
        return getBonus(enchantKey, "looting_rare");
    }

    public double getChainRangeBonus(String enchantKey) {
        return getBonus(enchantKey, "chain_range");
    }

    public double getSilkTouchChanceBonus(String enchantKey) {
        return getBonus(enchantKey, "silk_touch");
    }

    public double getFortuneBaseProbBonus(String enchantKey) {
        return getBonus(enchantKey, "fortune_base");
    }

    public double getFortuneDecrementBonus(String enchantKey) {
        return getBonus(enchantKey, "fortune_decrement");
    }

    public double getUnbreakingSaveBonus(String enchantKey) {
        return getBonus(enchantKey, "unbreaking_save");
    }

    public double getUnbreakingReturnBonus(String enchantKey) {
        return getBonus(enchantKey, "unbreaking_return");
    }

    public double getUnbreakingReturnRateBonus(String enchantKey) {
        return getBonus(enchantKey, "unbreaking_return_rate");
    }

    public double getMendingDurabilityBonus(String enchantKey) {
        return getBonus(enchantKey, "mending");
    }

    public double getProtectionPercentBonus(String enchantKey) {
        return getBonus(enchantKey, "protection");
    }

    public double getFireTickPercentBonus(String enchantKey) {
        return getBonus(enchantKey, "fire_tick");
    }

    public double getBlastKnockbackPercentBonus(String enchantKey) {
        return getBonus(enchantKey, "blast_knockback");
    }

    public double getProjectileKnockbackPercentBonus(String enchantKey) {
        return getBonus(enchantKey, "projectile_knockback");
    }

    public double getThornsChanceBonus(String enchantKey) {
        return getBonus(enchantKey, "thorns_chance");
    }

    public double getThornsDamageBonus(String enchantKey) {
        return getBonus(enchantKey, "thorns_damage");
    }

    public double getRespirationSecondsBonus(String enchantKey) {
        return getBonus(enchantKey, "respiration");
    }

    public double getAquaAffinityChainPercentBonus(String enchantKey) {
        return getBonus(enchantKey, "aqua_affinity");
    }
}
