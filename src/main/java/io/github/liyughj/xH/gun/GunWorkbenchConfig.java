package io.github.liyughj.xH.gun;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * 枪械工作台配方配置。
 *
 * <p>从 guns/gun-recipes.yml 加载，支持 5×5 (25格) 材料配方。
 * 配方网格索引 0-24 映射到 GUI 中 Row 0-4 Col 0-4 的位置。</p>
 *
 * <h3>YAML 格式</h3>
 * <pre>
 * recipes:
 *   ak47:
 *     name: "AK-47"
 *     type: gun          # gun / ammo / mag / attachment
 *     output_id: ak47    # 对应 createGunItem / createAmmoItemStack / createMagazineItem
 *     materials:
 *       0: "IRON_BLOCK"
 *       1: "REDSTONE_BLOCK"
 *       2: "IRON_INGOT"
 * </pre>
 */
public class GunWorkbenchConfig {

    private static final String CONFIG_FILE = "guns/gun-recipes.yml";

    private final JavaPlugin plugin;
    private YamlConfiguration config;
    private final List<RecipeDef> recipes = new ArrayList<>();

    public GunWorkbenchConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /** 重新加载配方 */
    public void reload() { loadConfig(); }

    /** 获取全部配方 */
    public List<RecipeDef> getRecipes() { return Collections.unmodifiableList(recipes); }

    /**
     * 用 25格材料匹配配方。
     * @param grid 索引 0-24 对应的 Material（null=空槽）
     * @return 匹配到的配方，无匹配返回 null
     */
    public RecipeDef matchRecipe(Material[] grid) {
        outer:
        for (RecipeDef recipe : recipes) {
            /* 配方必须有至少 1 个材料 */
            if (recipe.materials.isEmpty()) continue;
            if (recipe.materials.size() > grid.length) continue;

            for (int i = 0; i < grid.length; i++) {
                Material expected = recipe.materials.get(i);
                if (expected == null) continue;
                if (grid[i] != expected) continue outer;
            }

            /* 额外检查：配方需要的槽位不能有多余材料（精确匹配） */
            for (int i = 0; i < grid.length; i++) {
                if (grid[i] != null && !recipe.materials.containsKey(i)) continue outer;
            }

            return recipe;
        }
        return null;
    }

    /* ==================== 加载 ==================== */

    private void loadConfig() {
        recipes.clear();
        File dir = new File(plugin.getDataFolder(), "guns");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(plugin.getDataFolder(), CONFIG_FILE);

        if (!file.exists()) {
            createDefaultConfig(file);
        }

        config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection recipeSection = config.getConfigurationSection("recipes");
        if (recipeSection == null) return;

        for (String key : recipeSection.getKeys(false)) {
            ConfigurationSection cs = recipeSection.getConfigurationSection(key);
            if (cs == null) continue;

            String name = cs.getString("name", key);
            String type = cs.getString("type", "gun");
            String outputId = cs.getString("output_id", key);
            int outputAmount = cs.getInt("output_amount", 0);

            /* custom 类型：指定 output_material */
            String outputMaterial = cs.getString("output_material", "");
            String outputDisplayName = cs.getString("output_display_name", name);
            /* custom 属性写入（PDC key → value） */
            Map<String, String> outputAttrs = new HashMap<>();
            ConfigurationSection attrSection = cs.getConfigurationSection("output_attrs");
            if (attrSection != null) {
                for (String attrKey : attrSection.getKeys(false)) {
                    outputAttrs.put(attrKey, attrSection.getString(attrKey));
                }
            }

            Map<Integer, Material> materials = new HashMap<>();
            ConfigurationSection matSection = cs.getConfigurationSection("materials");
            if (matSection != null) {
                for (String idxStr : matSection.getKeys(false)) {
                    try {
                        int idx = Integer.parseInt(idxStr);
                        String matName = matSection.getString(idxStr);
                        Material mat = Material.getMaterial(matName != null ? matName.toUpperCase() : "");
                        if (mat != null && idx >= 0 && idx < 25) {
                            materials.put(idx, mat);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            recipes.add(new RecipeDef(key, name, type, outputId, outputAmount,
                outputMaterial, outputDisplayName, outputAttrs, materials));
        }
    }

    private void createDefaultConfig(File file) {
        YamlConfiguration dc = new YamlConfiguration();

        /* --- 示例步枪 --- */
        dc.set("recipes.ak47.name", "AK-47 突击步枪");
        dc.set("recipes.ak47.type", "gun");
        dc.set("recipes.ak47.output_id", "ak47");
        dc.set("recipes.ak47.materials.0", "IRON_BLOCK");
        dc.set("recipes.ak47.materials.1", "IRON_BLOCK");
        dc.set("recipes.ak47.materials.2", "REDSTONE_BLOCK");
        dc.set("recipes.ak47.materials.6", "IRON_INGOT");
        dc.set("recipes.ak47.materials.7", "IRON_INGOT");
        dc.set("recipes.ak47.materials.8", "IRON_INGOT");

        /* --- 示例手枪 --- */
        dc.set("recipes.glock.name", "Glock 17");
        dc.set("recipes.glock.type", "gun");
        dc.set("recipes.glock.output_id", "glock");
        dc.set("recipes.glock.materials.0", "IRON_BLOCK");
        dc.set("recipes.glock.materials.1", "REDSTONE_BLOCK");
        dc.set("recipes.glock.materials.6", "IRON_INGOT");
        dc.set("recipes.glock.materials.7", "IRON_INGOT");

        /* --- 示例弹药 --- */
        dc.set("recipes.9mm_ammo.name", "9mm 弹药 ×16");
        dc.set("recipes.9mm_ammo.type", "ammo");
        dc.set("recipes.9mm_ammo.output_id", "9mm:standard");
        dc.set("recipes.9mm_ammo.materials.0", "IRON_INGOT");
        dc.set("recipes.9mm_ammo.materials.1", "GUNPOWDER");

        /* --- 示例弹匣 --- */
        dc.set("recipes.ak_mag.name", "AK弹匣");
        dc.set("recipes.ak_mag.type", "mag");
        dc.set("recipes.ak_mag.output_id", "ak_mag");
        dc.set("recipes.ak_mag.materials.0", "IRON_INGOT");
        dc.set("recipes.ak_mag.materials.1", "IRON_INGOT");
        dc.set("recipes.ak_mag.materials.2", "IRON_INGOT");

        try { dc.save(file); } catch (Exception ignored) {}
    }

    /* ==================== 数据类 ==================== */

    /** 单个配方定义 */
    public static class RecipeDef {
        /** 配方ID */
        public final String id;
        /** 显示名称 */
        public final String name;
        /** 输出类型: gun / ammo / mag / custom (RPG属性物品) */
        public final String type;
        /** 输出ID（gun_id / caliber:ammoType / mag_id） */
        public final String outputId;
        /** 输出数量（弹药默认16，其他默认1） */
        public final int outputAmount;
        /** custom 类型：输出物品的 Material */
        public final String outputMaterial;
        /** custom 类型：输出物品显示名 */
        public final String outputDisplayName;
        /** custom 类型：写入物品 PDC 的属性 (key → value string) */
        public final Map<String, String> outputAttrs;
        /** 索引(0-24) → Material */
        public final Map<Integer, Material> materials;

        RecipeDef(String id, String name, String type, String outputId, int outputAmount,
                  String outputMaterial, String outputDisplayName, Map<String, String> outputAttrs,
                  Map<Integer, Material> materials) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.outputId = outputId;
            this.outputAmount = outputAmount;
            this.outputMaterial = outputMaterial;
            this.outputDisplayName = outputDisplayName;
            this.outputAttrs = outputAttrs;
            this.materials = materials;
        }
    }
}
