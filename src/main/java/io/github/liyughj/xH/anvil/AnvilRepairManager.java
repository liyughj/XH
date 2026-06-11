package io.github.liyughj.xH.anvil;

import io.github.liyughj.xH.gun.DurabilityManager;
import io.github.liyughj.xH.gun.GunListener;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * 铁砧枪械修复管理器。
 * 从 anvil.yml 读取修复材料、费用等配置。
 * 枪械修复消耗材料 + 经验等级。
 */
public class AnvilRepairManager {

    private static FileConfiguration config;
    private static JavaPlugin plugin;
    private static boolean enabled;

    /** 材料ID → RepairConfig */
    private static final Map<String, RepairMaterialConfig> repairMaterials = new LinkedHashMap<>();

    public static class RepairMaterialConfig {
        public String materialId;     // Material name
        public int repairPerItem;     // 每个材料修复的耐久值
        public String costMode;       // "flat" / "formula"
        public int flatCost;          // 固定经验消耗（level）
        public String formula;        // 经验消耗公式
        public boolean perfectRepair; // 是否完美修复
    }

    public static void init(JavaPlugin p) {
        plugin = p;
        loadConfig();
    }

    private static void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "anvil.yml");
        if (!configFile.exists()) {
            createDefaultConfig(configFile);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        enabled = config.getBoolean("enabled", true);

        repairMaterials.clear();
        ConfigurationSection materialsSection = config.getConfigurationSection("repair_materials");
        if (materialsSection != null) {
            for (String key : materialsSection.getKeys(false)) {
                ConfigurationSection cs = materialsSection.getConfigurationSection(key);
                if (cs == null) continue;
                RepairMaterialConfig mat = new RepairMaterialConfig();
                mat.materialId = cs.getString("material", key);
                mat.repairPerItem = cs.getInt("repair_per_item", 10);
                mat.costMode = cs.getString("cost_mode", "flat");
                mat.flatCost = cs.getInt("flat_cost", 1);
                mat.formula = cs.getString("formula", null);
                mat.perfectRepair = cs.getBoolean("perfect_repair", false);
                repairMaterials.put(key, mat);
            }
        }

        plugin.getLogger().info("[铁砧修复] 已加载 " + repairMaterials.size() + " 种修复材料");
    }

    /** 获取修复材料配置（按material ID查找） */
    public static RepairMaterialConfig getRepairMaterial(Material material, @SuppressWarnings("unused") ItemStack gun) {
        return repairMaterials.get(material.name());
    }

    /** 获取枪械修复消耗的经验等级 */
    public static int getRepairCost(Material repairMaterial, ItemStack gun) {
        RepairMaterialConfig cfg = getRepairMaterial(repairMaterial, gun);
        if (cfg == null) return 0;

        if ("flat".equals(cfg.costMode)) {
            return cfg.flatCost;
        }
        // formula 模式预留
        return cfg.flatCost;
    }

    /** 判断物品是否为可用的修复材料 */
    public static boolean isRepairMaterial(ItemStack item, ItemStack gun) {
        if (item == null || !GunListener.isGunStatic(gun)) return false;
        return getRepairMaterial(item.getType(), gun) != null;
    }

    /** 计算修复后耐久 */
    public static int calcRepairAmount(Material repairMaterial, ItemStack gun) {
        RepairMaterialConfig cfg = getRepairMaterial(repairMaterial, gun);
        if (cfg == null) return 0;
        return cfg.repairPerItem;
    }

    /** 是否完美修复 */
    public static boolean isPerfectRepair(Material repairMaterial, ItemStack gun) {
        RepairMaterialConfig cfg = getRepairMaterial(repairMaterial, gun);
        return cfg != null && cfg.perfectRepair;
    }

    /** 执行枪械修复 */
    public static ItemStack repairGun(ItemStack gun, Player player, ItemStack repairItem) {
        if (!GunListener.isGunStatic(gun)) return gun;

        // 检查破损后是否可修复
        double currentDura = DurabilityManager.getDurability(gun);
        if (currentDura <= 0) {
            double repairable = io.github.liyughj.xH.rpg.Attribute.AttributeStorage
                .getAttrValue(gun, io.github.liyughj.xH.rpg.Attribute.RpgAttribute.GUN_DURA_BROKEN_REPAIRABLE);
            if (repairable < 1.0) {
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "该武器已永久损坏!", net.kyori.adventure.text.format.NamedTextColor.DARK_RED));
                return gun;
            }
        }

        RepairMaterialConfig cfg = getRepairMaterial(repairItem.getType(), gun);
        if (cfg == null) return gun;

        double max = DurabilityManager.getMaxDurability(gun);

        if (cfg.perfectRepair) {
            DurabilityManager.setDurability(gun, max);
        } else {
            double amount = cfg.repairPerItem;
            DurabilityManager.repair(gun, amount);
        }

        return gun;
    }

    public static boolean isEnabled() { return enabled; }

    public static void reload() { loadConfig(); }

    private static void createDefaultConfig(File file) {
        FileConfiguration dc = new YamlConfiguration();
        dc.set("enabled", true);

        // 支持多种材料修复
        dc.set("repair_materials.iron_ingot.material", "IRON_INGOT");
        dc.set("repair_materials.iron_ingot.repair_per_item", 5);
        dc.set("repair_materials.iron_ingot.cost_mode", "flat");
        dc.set("repair_materials.iron_ingot.flat_cost", 1);
        dc.set("repair_materials.iron_ingot.perfect_repair", false);

        dc.set("repair_materials.diamond.material", "DIAMOND");
        dc.set("repair_materials.diamond.repair_per_item", 25);
        dc.set("repair_materials.diamond.cost_mode", "flat");
        dc.set("repair_materials.diamond.flat_cost", 3);
        dc.set("repair_materials.diamond.perfect_repair", false);

        dc.set("repair_materials.netherite_ingot.material", "NETHERITE_INGOT");
        dc.set("repair_materials.netherite_ingot.repair_per_item", 50);
        dc.set("repair_materials.netherite_ingot.cost_mode", "flat");
        dc.set("repair_materials.netherite_ingot.flat_cost", 5);
        dc.set("repair_materials.netherite_ingot.perfect_repair", false);

        dc.set("repair_materials.gun_parts.material", "IRON_INGOT");
        dc.set("repair_materials.gun_parts.perfect_repair", true);
        dc.set("repair_materials.gun_parts.repair_per_item", 100);
        dc.set("repair_materials.gun_parts.cost_mode", "flat");
        dc.set("repair_materials.gun_parts.flat_cost", 10);

        try { dc.save(file); } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "无法创建默认 anvil.yml", e);
        }
    }
}
