package io.github.liyughj.xH.make;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * 工作台配方配置 —— 从 gun-recipes.yml 加载。
 *
 * <p>图纸驱动模式：匹配时先读取图纸 ID，再检查 5×5 区域材料数量是否足够。</p>
 *
 * <h3>YAML 格式</h3>
 * <pre>
 * recipes:
 *   ak47:
 *     name: "AK-47 突击步枪"
 *     type: gun
 *     output_id: ak47
 *     blueprint_id: ak47
 *     materials:
 *       IRON_BLOCK: 3
 *       REDSTONE_BLOCK: 1
 *       IRON_INGOT: 5
 * </pre>
 */
public class WorkbenchRecipeConfig {

    private static final String CONFIG_FILE = "guns/gun-recipes.yml";

    private final JavaPlugin plugin;
    private final Map<String, RecipeDef> recipes = new LinkedHashMap<>();

    public WorkbenchRecipeConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void reload() { loadConfig(); }

    public RecipeDef get(String blueprintId) { return recipes.get(blueprintId); }

    public Collection<RecipeDef> getAll() { return Collections.unmodifiableCollection(recipes.values()); }

    /**
     * 用蓝图 ID + 材料计数匹配配方。
     * @param blueprintId 图纸 ID
     * @param materialCounts 5×5 区域每种 Material 的数量
     * @return 匹配到的配方，材料不足返回 null
     */
    public RecipeDef matchRecipe(String blueprintId, Map<Material, Integer> materialCounts) {
        RecipeDef recipe = recipes.get(blueprintId);
        if (recipe == null) return null;

        for (Map.Entry<Material, Integer> required : recipe.materials.entrySet()) {
            int have = materialCounts.getOrDefault(required.getKey(), 0);
            if (have < required.getValue()) return null;
        }
        return recipe;
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

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection recipeSection = config.getConfigurationSection("recipes");
        if (recipeSection == null) return;

        for (String key : recipeSection.getKeys(false)) {
            ConfigurationSection cs = recipeSection.getConfigurationSection(key);
            if (cs == null) continue;

            String name = cs.getString("name", key);
            String type = cs.getString("type", "gun");
            String outputId = cs.getString("output_id", key);
            String blueprintId = cs.getString("blueprint_id", key);
            int outputAmount = cs.getInt("output_amount", 0);

            /* custom 类型 */
            String outputMaterial = cs.getString("output_material", "");
            String outputDisplayName = cs.getString("output_display_name", name);
            Map<String, String> outputAttrs = new HashMap<>();
            ConfigurationSection attrSection = cs.getConfigurationSection("output_attrs");
            if (attrSection != null) {
                for (String attrKey : attrSection.getKeys(false)) {
                    outputAttrs.put(attrKey, attrSection.getString(attrKey));
                }
            }

            /* 材料计数：Material → 数量 */
            Map<Material, Integer> materials = new LinkedHashMap<>();
            ConfigurationSection matSection = cs.getConfigurationSection("materials");
            if (matSection != null) {
                for (String matName : matSection.getKeys(false)) {
                    Material mat = Material.getMaterial(matName.toUpperCase());
                    if (mat == null) continue;
                    int count = matSection.getInt(matName, 1);
                    if (count > 0) {
                        materials.put(mat, count);
                    }
                }
            }

            recipes.put(key, new RecipeDef(key, name, type, outputId, blueprintId, outputAmount,
                outputMaterial, outputDisplayName, outputAttrs, materials));
        }
    }

    private void createDefaultConfig(File file) {
        YamlConfiguration dc = new YamlConfiguration();

        dc.set("recipes.ak47.name", "AK-47 突击步枪");
        dc.set("recipes.ak47.type", "gun");
        dc.set("recipes.ak47.output_id", "ak47");
        dc.set("recipes.ak47.blueprint_id", "ak47");
        dc.set("recipes.ak47.materials.IRON_BLOCK", 3);
        dc.set("recipes.ak47.materials.REDSTONE_BLOCK", 1);
        dc.set("recipes.ak47.materials.IRON_INGOT", 5);

        dc.set("recipes.glock.name", "Glock 17");
        dc.set("recipes.glock.type", "gun");
        dc.set("recipes.glock.output_id", "glock");
        dc.set("recipes.glock.blueprint_id", "glock");
        dc.set("recipes.glock.materials.IRON_BLOCK", 1);
        dc.set("recipes.glock.materials.REDSTONE_BLOCK", 1);
        dc.set("recipes.glock.materials.IRON_INGOT", 3);

        dc.set("recipes.9mm_ammo.name", "9mm 弹药 ×16");
        dc.set("recipes.9mm_ammo.type", "ammo");
        dc.set("recipes.9mm_ammo.output_id", "9mm:standard");
        dc.set("recipes.9mm_ammo.output_amount", 16);
        dc.set("recipes.9mm_ammo.blueprint_id", "9mm_ammo");
        dc.set("recipes.9mm_ammo.materials.IRON_INGOT", 2);
        dc.set("recipes.9mm_ammo.materials.GUNPOWDER", 1);

        dc.set("recipes.ak_mag.name", "AK弹匣");
        dc.set("recipes.ak_mag.type", "mag");
        dc.set("recipes.ak_mag.output_id", "ak_mag");
        dc.set("recipes.ak_mag.blueprint_id", "ak_mag");
        dc.set("recipes.ak_mag.materials.IRON_INGOT", 4);

        try { dc.save(file); } catch (Exception ignored) {}
    }

    /* ==================== 数据类 ==================== */

    public static class RecipeDef {
        public final String id;
        public final String name;
        public final String type;
        public final String outputId;
        public final String blueprintId;
        public final int outputAmount;
        public final String outputMaterial;
        public final String outputDisplayName;
        public final Map<String, String> outputAttrs;
        /** Material → 所需数量 */
        public final Map<Material, Integer> materials;

        RecipeDef(String id, String name, String type, String outputId, String blueprintId,
                  int outputAmount, String outputMaterial, String outputDisplayName,
                  Map<String, String> outputAttrs, Map<Material, Integer> materials) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.outputId = outputId;
            this.blueprintId = blueprintId;
            this.outputAmount = outputAmount;
            this.outputMaterial = outputMaterial;
            this.outputDisplayName = outputDisplayName;
            this.outputAttrs = outputAttrs;
            this.materials = materials;
        }
    }
}