package io.github.liyughj.xH.make;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * 图纸配置 —— 从 blueprints.yml 加载。
 *
 * <h3>YAML 格式</h3>
 * <pre>
 * blueprints:
 *   ak47:
 *     name: "AK-47 图纸"
 *     material: PAPER        # 默认 PAPER
 *     durability: 5          # 可使用次数，归零损坏
 *     lore:
 *       - "§7用于制作 AK-47 突击步枪"
 * </pre>
 */
public class BlueprintConfig {

    private static final String CONFIG_FILE = "blueprints.yml";

    private final JavaPlugin plugin;
    private final Map<String, BlueprintDef> blueprints = new LinkedHashMap<>();

    public BlueprintConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void reload() { loadConfig(); }

    public BlueprintDef get(String id) { return blueprints.get(id); }

    public Collection<BlueprintDef> getAll() { return Collections.unmodifiableCollection(blueprints.values()); }

    /* ==================== 加载 ==================== */

    private void loadConfig() {
        blueprints.clear();
        File file = new File(plugin.getDataFolder(), CONFIG_FILE);

        if (!file.exists()) {
            createDefaultConfig(file);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("blueprints");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection cs = section.getConfigurationSection(key);
            if (cs == null) continue;

            String name = cs.getString("name", key);
            String matName = cs.getString("material", "PAPER");
            Material material = Material.getMaterial(matName.toUpperCase());
            if (material == null) material = Material.PAPER;

            int durability = cs.getInt("durability", 1);
            if (durability < 1) durability = 1;

            List<String> lore = cs.getStringList("lore");

            blueprints.put(key, new BlueprintDef(key, name, material, durability, lore));
        }
    }

    private void createDefaultConfig(File file) {
        YamlConfiguration dc = new YamlConfiguration();

        dc.set("blueprints.ak47.name", "AK-47 图纸");
        dc.set("blueprints.ak47.material", "PAPER");
        dc.set("blueprints.ak47.durability", 5);
        dc.set("blueprints.ak47.lore", Collections.singletonList("§7用于制作 AK-47 突击步枪"));

        dc.set("blueprints.glock.name", "Glock 17 图纸");
        dc.set("blueprints.glock.material", "PAPER");
        dc.set("blueprints.glock.durability", 3);
        dc.set("blueprints.glock.lore", Collections.singletonList("§7用于制作 Glock 17 手枪"));

        try { dc.save(file); } catch (Exception ignored) {}
    }

    /* ==================== 数据类 ==================== */

    public static class BlueprintDef {
        public final String id;
        public final String name;
        public final Material material;
        public final int durability;
        public final List<String> lore;

        BlueprintDef(String id, String name, Material material, int durability, List<String> lore) {
            this.id = id;
            this.name = name;
            this.material = material;
            this.durability = durability;
            this.lore = lore;
        }
    }
}