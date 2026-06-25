package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeStorage;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 故障系统管理器。
 * 每发射击有概率触发故障（卡壳/哑火/炸膛）。
 * 故障率 = 基础故障率 + 热量×热量因子 + (1-耐久率)×耐久因子
 */
public class MalfunctionManager {

    /** 故障类型 */
    public enum MalfuncType {
        JAM,       // 卡壳: 短时间内无法射击
        MISFIRE,   // 哑火: 本发无伤害
        CATASTROPHIC // 炸膛: 伤害持枪者 + 耐久暴跌
    }

    private static final Map<UUID, MalfunctionState> states = new ConcurrentHashMap<>();

    public static class MalfunctionState {
        boolean jamActive;       // 是否卡壳中
        boolean jamClearing;     // 是否正在排除卡壳（拒绝重复左键）
        long lastMalfuncTick;    // 上次故障的世界tick（冷却用）
    }

    private static MalfunctionState getState(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), k -> new MalfunctionState());
    }

    /** 是否正在排除卡壳（排障动作进行中，仍禁止射击） */
    public static boolean isJamClearing(Player player) {
        MalfunctionState state = states.get(player.getUniqueId());
        return state != null && state.jamClearing;
    }

    /**
     * 射击前调用。返回 null = 正常射击，否则返回故障类型。
     * 卡壳期间持续返回 JAM 直到清除。
     */
    public static MalfuncType checkAndTrigger(Player player, ItemStack weapon) {
        if (!GunSystemConfig.isSystemEnabled(player, "malfunction")) return null;

        MalfunctionState state = getState(player);

        // 卡壳持续 / 排障中
        if (state.jamActive || state.jamClearing) return MalfuncType.JAM;

        // 故障冷却
        long now = player.getWorld().getGameTime();
        double cooldownTicks = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_MALFUNC_COOLDOWN_TICKS);
        if (state.lastMalfuncTick > 0 && (now - state.lastMalfuncTick) < cooldownTicks) return null;

        // 热量门控：热量%未达到故障触发阈值则不roll故障
        if (!io.github.liyughj.xH.specialEvent.HeatSystem.canMalfunction(player, weapon)) return null;

        // 计算故障率
        double chance = calcMalfuncChance(player, weapon);
        if (Math.random() * 100 >= chance) return null;

        // 触发故障
        state.lastMalfuncTick = now;
        MalfuncType type = rollMalfuncType(weapon);

        switch (type) {
            case JAM:
                state.jamActive = true;
                player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 0.4f, 1.5f);
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "卡壳! 左键排除", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                break;

            case MISFIRE:
                player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 0.3f, 2.0f);
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "哑火!", net.kyori.adventure.text.format.NamedTextColor.GRAY));
                break;

            case CATASTROPHIC:
                double cataDamage = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_MALFUNC_CATA_DAMAGE);
                player.damage(cataDamage);
                // 耐久损失在 DurabilitySystem 中处理
                double duraLoss = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_MALFUNC_CATA_DURA_LOSS);
                io.github.liyughj.xH.specialEvent.DurabilitySystem.loseDurability(player, weapon, duraLoss);
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.5f);
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "炸膛!", net.kyori.adventure.text.format.NamedTextColor.DARK_RED));
                break;
        }
        return type;
    }

    /** 计算故障率（%） */
    private static double calcMalfuncChance(Player player, ItemStack weapon) {
        double chance = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_MALFUNC_BASE_CHANCE);

        // 全局基础故障率
        double globalBase = 0;
        if (GunSystemConfig.getSystemSection("malfunction") != null) {
            globalBase = GunSystemConfig.getSystemSection("malfunction").getDouble("global_base_chance", 0);
        }
        chance += globalBase;

        // 热量加成
        chance += io.github.liyughj.xH.specialEvent.HeatSystem.getMalfunctionBonus(player, weapon);

        // 耐久加成（仅耐久阀系统）
        chance += io.github.liyughj.xH.specialEvent.DurabilitySystem.getDurabilityMalfunctionBonus(player, weapon);

        return Math.min(chance, 100);
    }

    /** 随机决定故障类型，从属性 gun_malfunc_type_weights 读取权重（编码格式） */
    private static MalfuncType rollMalfuncType(ItemStack weapon) {
        double encoded = AttributeStorage.getAttrValue(weapon, RpgAttribute.GUN_MALFUNC_TYPE_WEIGHTS);
        // 编码: jam*1000000 + misfire*1000 + cata
        int jamW = Math.max(0, (int) (encoded / 1000000) % 1000);
        int misfireW = Math.max(0, (int) (encoded / 1000) % 1000);
        int cataW = Math.max(0, (int) encoded % 1000);

        int total = jamW + misfireW + cataW;
        if (total <= 0) { jamW = 70; misfireW = 20; cataW = 10; total = 100; }

        double roll = Math.random() * total;
        if (roll < jamW) return MalfuncType.JAM;
        if (roll < jamW + misfireW) return MalfuncType.MISFIRE;
        return MalfuncType.CATASTROPHIC;
    }

    /** 清除玩家状态 */
    public static void remove(Player player) {
        states.remove(player.getUniqueId());
    }

    /** 手动清除卡壳（例如切换武器或特殊动作） */
    public static void clearJam(Player player) {
        MalfunctionState state = states.get(player.getUniqueId());
        if (state != null) {
            state.jamActive = false;
            state.jamClearing = false;
        }
    }

    /** 开始排障动作（左键），返回 true=成功进入排障状态 */
    public static boolean startJamClear(Player player) {
        MalfunctionState state = states.get(player.getUniqueId());
        if (state == null || !state.jamActive || state.jamClearing) return false;
        state.jamClearing = true;
        return true;
    }

    /** 排障完成，清除卡壳标记 */
    public static void finishJamClear(Player player) {
        MalfunctionState state = states.get(player.getUniqueId());
        if (state != null) {
            state.jamActive = false;
            state.jamClearing = false;
        }
    }

    /** 查询是否卡壳中 */
    public static boolean isJamActive(Player player) {
        MalfunctionState state = states.get(player.getUniqueId());
        return state != null && state.jamActive;
    }
}
