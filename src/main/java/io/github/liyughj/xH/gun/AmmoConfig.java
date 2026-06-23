package io.github.liyughj.xH.gun;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * 弹药系统配置（ammo.yml）。
 * 管理口径定义和弹种定义，提供弹种修正系数查询。
 */
public class AmmoConfig {

    private static final String CONFIG_FILE_NAME = "ammo.yml";
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private boolean enabled;

    /** caliberId → CaliberDef */
    private final Map<String, CaliberDef> calibers = new LinkedHashMap<>();
    /** ammoTypeId → AmmoTypeDef */
    private final Map<String, AmmoTypeDef> ammoTypes = new LinkedHashMap<>();

    public AmmoConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE_NAME);
        if (!configFile.exists()) {
            createDefaultConfig(configFile);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        enabled = config.getBoolean("enabled", false);

        calibers.clear();
        ammoTypes.clear();

        // 口径（支持递归加载兼容旧配置中的点号口径ID）
        ConfigurationSection calibersSection = config.getConfigurationSection("calibers");
        if (calibersSection != null) {
            loadCalibersRecursive(calibersSection, "");
        }

        // 弹种
        ConfigurationSection typesSection = config.getConfigurationSection("ammo_types");
        if (typesSection != null) {
            for (String id : typesSection.getKeys(false)) {
                ConfigurationSection cs = typesSection.getConfigurationSection(id);
                if (cs == null) continue;
                AmmoTypeDef def = new AmmoTypeDef();
                def.id = id;
                def.displayName = cs.getString("display_name", id);
                def.lore = cs.getString("lore", "");
                def.damageMult = cs.getDouble("damage_mult", 1.0);
                def.penetrationBonus = cs.getInt("penetration_bonus", 0);
                def.spreadMult = cs.getDouble("spread_mult", 1.0);
                def.recoilMult = cs.getDouble("recoil_mult", 1.0);
                def.heatMult = cs.getDouble("heat_mult", 1.0);
                def.durabilityMult = cs.getDouble("durability_mult", 1.0);
                def.bulletSpeedMult = cs.getDouble("bullet_speed_mult", 1.0);
                def.bulletGravityMult = cs.getDouble("bullet_gravity_mult", 1.0);
                def.bulletDragMult = cs.getDouble("bullet_drag_mult", 1.0);
                def.effects = cs.getConfigurationSection("effects");
                def.itemMaterial = cs.getString("item_material", "IRON_NUGGET");
                def.itemCustomModelData = cs.getInt("item_custom_model_data", 0);
                def.itemColor = cs.getString("item_color", "&7");
                ammoTypes.put(id, def);
            }
        }

        plugin.getLogger().info("[弹药] 已加载 " + calibers.size() + " 个口径, " + ammoTypes.size() + " 个弹种");
    }

    /** 将口径ID中的 . 替换为 _（兼容旧YAML中点号路径分裂问题） */
    static String normalizeCaliberId(String id) {
        if (id == null) return null;
        return id.replace('.', '_');
    }

    /** 递归加载口径：处理旧配置中点号被路径分隔符拆分的情况 */
    private void loadCalibersRecursive(ConfigurationSection section, String prefix) {
        for (String key : section.getKeys(false)) {
            ConfigurationSection cs = section.getConfigurationSection(key);
            if (cs == null) continue;
            if (cs.contains("display_name")) {
                // 叶子节点：这是一个口径定义
                String fullId = prefix.isEmpty() ? key : prefix + "." + key;
                String normalizedId = normalizeCaliberId(fullId);
                CaliberDef def = new CaliberDef();
                def.id = normalizedId;
                def.displayName = cs.getString("display_name", normalizedId);
                def.category = cs.getString("category", "rifle");
                def.stackSize = cs.getInt("stack_size", 64);
                def.availableTypes = cs.getStringList("available_types");
                def.defaultType = cs.getString("default_type",
                    def.availableTypes.isEmpty() ? "fmj" : def.availableTypes.get(0));
                def.craftBaseMaterial = cs.getString("craft_base_material", "IRON_INGOT");
                def.craftBaseCount = cs.getInt("craft_base_count", 16);
                calibers.put(normalizedId, def);
            } else {
                // 中间节点：递归深入（旧配置中被路径分隔符拆分的）
                String newPrefix = prefix.isEmpty() ? key : prefix + "." + key;
                loadCalibersRecursive(cs, newPrefix);
            }
        }
    }

    /** 获取某个弹种的修正系数，找不到返回 null */
    public AmmoTypeDef getAmmoType(String ammoTypeId) {
        return ammoTypes.get(ammoTypeId);
    }

    /** 获取某个口径定义 */
    public CaliberDef getCaliber(String caliberId) {
        return calibers.get(normalizeCaliberId(caliberId));
    }

    /** 获取口径的默认弹种ID */
    public String getDefaultAmmoType(String caliberId) {
        CaliberDef def = calibers.get(normalizeCaliberId(caliberId));
        return def != null ? def.defaultType : "fmj";
    }

    /** 获取某个口径可用的弹种ID列表 */
    public List<String> getAvailableTypes(String caliberId) {
        CaliberDef def = calibers.get(normalizeCaliberId(caliberId));
        return def != null ? new ArrayList<>(def.availableTypes) : Collections.emptyList();
    }

    /** 检查某个弹种是否属于某口径 */
    public boolean isTypeAvailableForCaliber(String caliberId, String ammoTypeId) {
        CaliberDef def = calibers.get(normalizeCaliberId(caliberId));
        return def != null && def.availableTypes.contains(ammoTypeId);
    }

    public void reload() { loadConfig(); }

    public boolean isEnabled() { return enabled; }

    /** 获取所有弹种ID */
    public Set<String> getAllAmmoTypeIds() { return Collections.unmodifiableSet(ammoTypes.keySet()); }

    /** 获取所有口径ID */
    public Set<String> getCaliberIds() { return Collections.unmodifiableSet(calibers.keySet()); }

    /** 根据口径+弹种ID创建弹药 ItemStack */
    public ItemStack createAmmoItemStack(String caliberId, String ammoTypeId) {
        caliberId = normalizeCaliberId(caliberId);
        AmmoTypeDef def = ammoTypes.get(ammoTypeId);
        if (def == null) return null;
        Material mat = Material.getMaterial(def.itemMaterial);
        if (mat == null) mat = Material.IRON_NUGGET;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        CaliberDef cal = calibers.get(caliberId);
        String caliberLabel = cal != null ? cal.displayName : caliberId;
        meta.displayName(Component.text("§e" + def.displayName + " §7(" + caliberLabel + ")"));
        meta.getPersistentDataContainer().set(new NamespacedKey("xh", "ammo_caliber"), PersistentDataType.STRING, caliberId);
        meta.getPersistentDataContainer().set(new NamespacedKey("xh", "ammo_type"), PersistentDataType.STRING, ammoTypeId);
        if (def.itemCustomModelData > 0) meta.setCustomModelData(def.itemCustomModelData);
        item.setItemMeta(meta);

        // 应用 LoreManager 模板生成 lore
        if (io.github.liyughj.xH.lore.LoreConfig.hasInstance()
            && io.github.liyughj.xH.lore.LoreConfig.instance().isEnabled()) {
            List<Component> loreLines = io.github.liyughj.xH.lore.LoreManager.buildAmmoLore(
                caliberId, def.displayName,
                def.damageMult, def.penetrationBonus,
                def.spreadMult, def.recoilMult, def.bulletSpeedMult);
            if (!loreLines.isEmpty()) {
                ItemMeta updatedMeta = item.getItemMeta();
                updatedMeta.lore(loreLines);
                item.setItemMeta(updatedMeta);
            }
        }

        return item;
    }

    /* ==================== 数据类 ==================== */

    public static class CaliberDef {
        public String id;
        public String displayName;
        public String category;       // pistol/rifle/shotgun/sniper/explosive/energy
        public int stackSize;
        public List<String> availableTypes;
        public String defaultType;
        public String craftBaseMaterial;
        public int craftBaseCount;
    }

    public static class AmmoTypeDef {
        public String id;
        public String displayName;
        public String lore;
        // 修正系数
        public double damageMult = 1.0;
        public int penetrationBonus = 0;
        public double spreadMult = 1.0;
        public double recoilMult = 1.0;
        public double heatMult = 1.0;
        public double durabilityMult = 1.0;
        public double bulletSpeedMult = 1.0;
        public double bulletGravityMult = 1.0;
        public double bulletDragMult = 1.0;
        // 特殊效果
        public ConfigurationSection effects;
        // 物品外观
        public String itemMaterial;
        public int itemCustomModelData;
        public String itemColor;

        /** 从 effects 节点读取 double 值 */
        public double getEffectDouble(String key, double defaultVal) {
            if (effects == null) return defaultVal;
            return effects.getDouble(key, defaultVal);
        }

        /** 从 effects 节点读取 int 值 */
        public int getEffectInt(String key, int defaultVal) {
            if (effects == null) return defaultVal;
            return effects.getInt(key, defaultVal);
        }

        /** 从 effects 节点读取 boolean 值 */
        public boolean getEffectBool(String key) {
            if (effects == null) return false;
            return effects.getBoolean(key, false);
        }
    }

    /* ==================== 默认配置 ==================== */

    private void createDefaultConfig(File file) {
        FileConfiguration dc = new YamlConfiguration();
        dc.set("enabled", false);

        // --- 口径 ---
        // Pistol
        dc.set("calibers.9mm.display_name", "9×19mm 帕拉贝鲁姆");
        dc.set("calibers.9mm.category", "pistol");
        dc.set("calibers.9mm.stack_size", 64);
        dc.set("calibers.9mm.available_types", Arrays.asList("fmj", "hp", "ap", "tracer", "subsonic", "+p", "match", "rubber"));
        dc.set("calibers.9mm.default_type", "fmj");
        dc.set("calibers.9mm.craft_base_material", "IRON_INGOT");
        dc.set("calibers.9mm.craft_base_count", 32);

        dc.set("calibers.45acp.display_name", ".45 ACP");
        dc.set("calibers.45acp.category", "pistol");
        dc.set("calibers.45acp.stack_size", 48);
        dc.set("calibers.45acp.available_types", Arrays.asList("fmj", "hp", "subsonic", "+p", "match"));
        dc.set("calibers.45acp.default_type", "fmj");
        dc.set("calibers.45acp.craft_base_material", "IRON_INGOT");
        dc.set("calibers.45acp.craft_base_count", 24);

        // Rifle
        dc.set("calibers.5_56mm.display_name", "5.56×45mm NATO");
        dc.set("calibers.5_56mm.category", "rifle");
        dc.set("calibers.5_56mm.stack_size", 64);
        dc.set("calibers.5_56mm.available_types", Arrays.asList("fmj", "hp", "ap", "tracer", "incendiary", "subsonic", "match", "rubber"));
        dc.set("calibers.5_56mm.default_type", "fmj");
        dc.set("calibers.5_56mm.craft_base_material", "IRON_INGOT");
        dc.set("calibers.5_56mm.craft_base_count", 20);

        dc.set("calibers.7_62mm.display_name", "7.62×51mm NATO");
        dc.set("calibers.7_62mm.category", "rifle");
        dc.set("calibers.7_62mm.stack_size", 48);
        dc.set("calibers.7_62mm.available_types", Arrays.asList("fmj", "hp", "ap", "tracer", "incendiary", "match"));
        dc.set("calibers.7_62mm.default_type", "fmj");
        dc.set("calibers.7_62mm.craft_base_material", "IRON_INGOT");
        dc.set("calibers.7_62mm.craft_base_count", 16);

        dc.set("calibers.7_62x39mm.display_name", "7.62×39mm");
        dc.set("calibers.7_62x39mm.category", "rifle");
        dc.set("calibers.7_62x39mm.stack_size", 64);
        dc.set("calibers.7_62x39mm.available_types", Arrays.asList("fmj", "hp", "ap", "tracer", "incendiary", "match"));
        dc.set("calibers.7_62x39mm.default_type", "fmj");
        dc.set("calibers.7_62x39mm.craft_base_material", "IRON_INGOT");
        dc.set("calibers.7_62x39mm.craft_base_count", 20);

        // Sniper
        dc.set("calibers.300win.display_name", ".300 Winchester Magnum");
        dc.set("calibers.300win.category", "sniper");
        dc.set("calibers.300win.stack_size", 32);
        dc.set("calibers.300win.available_types", Arrays.asList("fmj", "hp", "ap", "match", "incendiary"));
        dc.set("calibers.300win.default_type", "match");
        dc.set("calibers.300win.craft_base_material", "DIAMOND");
        dc.set("calibers.300win.craft_base_count", 8);

        dc.set("calibers.338lapua.display_name", ".338 Lapua Magnum");
        dc.set("calibers.338lapua.category", "sniper");
        dc.set("calibers.338lapua.stack_size", 24);
        dc.set("calibers.338lapua.available_types", Arrays.asList("fmj", "ap", "match", "incendiary"));
        dc.set("calibers.338lapua.default_type", "ap");
        dc.set("calibers.338lapua.craft_base_material", "DIAMOND");
        dc.set("calibers.338lapua.craft_base_count", 6);

        dc.set("calibers.50bmg.display_name", ".50 BMG");
        dc.set("calibers.50bmg.category", "sniper");
        dc.set("calibers.50bmg.stack_size", 16);
        dc.set("calibers.50bmg.available_types", Arrays.asList("fmj", "ap", "incendiary", "tracer"));
        dc.set("calibers.50bmg.default_type", "ap");
        dc.set("calibers.50bmg.craft_base_material", "NETHERITE_INGOT");
        dc.set("calibers.50bmg.craft_base_count", 4);

        // Shotgun
        dc.set("calibers.12gauge.display_name", "12 Gauge");
        dc.set("calibers.12gauge.category", "shotgun");
        dc.set("calibers.12gauge.stack_size", 32);
        dc.set("calibers.12gauge.available_types", Arrays.asList("buckshot", "slug", "flechette", "dragon_breath", "rubber"));
        dc.set("calibers.12gauge.default_type", "buckshot");
        dc.set("calibers.12gauge.craft_base_material", "IRON_INGOT");
        dc.set("calibers.12gauge.craft_base_count", 12);

        // Explosive
        dc.set("calibers.40mm.display_name", "40×46mm 榴弹");
        dc.set("calibers.40mm.category", "explosive");
        dc.set("calibers.40mm.stack_size", 4);
        dc.set("calibers.40mm.available_types", Arrays.asList("he", "smoke", "flash", "he_frag"));
        dc.set("calibers.40mm.default_type", "he");
        dc.set("calibers.40mm.craft_base_material", "IRON_BLOCK");
        dc.set("calibers.40mm.craft_base_count", 1);

        dc.set("calibers.rocket.display_name", "火箭弹");
        dc.set("calibers.rocket.category", "explosive");
        dc.set("calibers.rocket.stack_size", 1);
        dc.set("calibers.rocket.available_types", Arrays.asList("he", "heat", "incendiary"));
        dc.set("calibers.rocket.default_type", "he");
        dc.set("calibers.rocket.craft_base_material", "IRON_BLOCK");
        dc.set("calibers.rocket.craft_base_count", 1);

        // Energy
        dc.set("calibers.energy_cell.display_name", "能量电池");
        dc.set("calibers.energy_cell.category", "energy");
        dc.set("calibers.energy_cell.stack_size", 16);
        dc.set("calibers.energy_cell.available_types", Arrays.asList("standard", "overcharged", "focused"));
        dc.set("calibers.energy_cell.default_type", "standard");
        dc.set("calibers.energy_cell.craft_base_material", "REDSTONE_BLOCK");
        dc.set("calibers.energy_cell.craft_base_count", 2);

        // --- 弹种 ---
        addAmmoType(dc, "fmj", "全金属被甲弹", 1.0, 0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, "IRON_NUGGET", 1);
        addAmmoType(dc, "hp", "空尖弹", 1.30, -2, 1.0, 1.05, 1.0, 1.0, 0.95, 1.0, 1.02, "REDSTONE", 2);
        addAmmoType(dc, "+p", "+P高压弹", 1.20, 1, 1.10, 1.30, 1.40, 1.30, 1.15, 1.0, 0.97, "GLOWSTONE_DUST", 3);
        addAmmoType(dc, "ap", "穿甲弹", 0.85, 3, 0.95, 1.05, 1.10, 1.15, 1.05, 0.95, 0.98, "IRON_NUGGET", 4);
        addAmmoType(dc, "flechette", "箭形弹", 0.75, 5, 0.80, 1.15, 1.05, 1.10, 1.10, 0.90, 0.95, "ARROW", 5);
        addAmmoType(dc, "match", "竞赛弹", 1.0, 0, 0.70, 0.95, 1.0, 0.90, 1.02, 0.95, 0.95, "GOLD_NUGGET", 6);
        addAmmoType(dc, "tracer", "曳光弹", 1.0, 0, 0.85, 1.0, 1.05, 1.0, 1.0, 1.0, 1.0, "GLOWSTONE_DUST", 7);
        addAmmoType(dc, "incendiary", "燃烧弹", 1.10, 0, 1.0, 1.05, 1.30, 1.05, 1.0, 1.0, 1.0, "BLAZE_POWDER", 8);
        addAmmoType(dc, "subsonic", "亚音速弹", 0.95, 0, 1.0, 0.80, 0.90, 1.0, 0.65, 1.3, 1.15, "GUNPOWDER", 9);
        addAmmoType(dc, "rubber", "橡胶弹", 0.20, -5, 1.05, 0.50, 0.50, 0.50, 0.80, 1.1, 1.10, "SLIME_BALL", 10);
        addAmmoType(dc, "buckshot", "鹿弹", 1.0, 0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.02, "IRON_NUGGET", 11);
        addAmmoType(dc, "slug", "独头弹", 2.0, 1, 1.20, 1.50, 1.10, 1.05, 0.90, 1.1, 1.01, "IRON_INGOT", 12);
        addAmmoType(dc, "dragon_breath", "龙息弹", 0.80, 0, 1.0, 1.20, 2.0, 1.20, 0.70, 1.0, 1.03, "BLAZE_POWDER", 13);
        addAmmoType(dc, "he", "高爆弹", 1.0, 0, 1.0, 1.3, 1.5, 1.0, 1.0, 1.0, 1.0, "TNT", 14);
        addAmmoType(dc, "heat", "破甲高爆弹", 1.15, 4, 1.0, 1.5, 1.8, 1.0, 0.85, 1.2, 1.0, "TNT", 15);
        addAmmoType(dc, "smoke", "烟雾弹", 0.0, 0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, "GRAY_DYE", 16);
        addAmmoType(dc, "flash", "闪光弹", 0.0, 0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, "GLOWSTONE_DUST", 17);
        addAmmoType(dc, "he_frag", "破片榴弹", 1.0, 0, 1.0, 1.3, 1.5, 1.0, 1.0, 1.0, 1.0, "TNT", 18);
        addAmmoType(dc, "standard", "标准能量电池", 1.0, 0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, "REDSTONE", 19);
        addAmmoType(dc, "overcharged", "过载能量电池", 1.40, 1, 1.0, 1.0, 1.80, 1.10, 1.0, 1.0, 1.0, "GLOWSTONE_DUST", 20);
        addAmmoType(dc, "focused", "聚焦能量电池", 0.90, 3, 0.60, 1.0, 1.20, 1.0, 1.0, 1.0, 1.0, "AMETHYST_SHARD", 21);

        // --- 弹种特殊效果 ---
        // hp: 空尖弹 → 流血
        dc.set("ammo_types.hp.effects.bleed.chance", 20);
        dc.set("ammo_types.hp.effects.bleed.damage", 3);
        dc.set("ammo_types.hp.effects.bleed.ticks", 40);
        // ap: 穿甲弹 → 无视护甲
        dc.set("ammo_types.ap.effects.armor_ignore", 20);
        // flechette: 箭形弹 → 更高护甲穿透
        dc.set("ammo_types.flechette.effects.armor_ignore", 30);
        // tracer: 曳光弹 → 弹道尾迹 + 微量点燃
        dc.set("ammo_types.tracer.effects.trail", "flame");
        dc.set("ammo_types.tracer.effects.ignite.chance", 15);
        dc.set("ammo_types.tracer.effects.ignite.ticks", 30);
        // incendiary: 燃烧弹 → 点燃 + 火焰额外伤害
        dc.set("ammo_types.incendiary.effects.ignite.chance", 100);
        dc.set("ammo_types.incendiary.effects.ignite.ticks", 60);
        dc.set("ammo_types.incendiary.effects.fire_damage", 2);
        // subsonic: 亚音速弹 → 消音
        dc.set("ammo_types.subsonic.effects.silent", true);
        // rubber: 橡胶弹 → 击退 + 非致命
        dc.set("ammo_types.rubber.effects.knockback", 3.0);
        dc.set("ammo_types.rubber.effects.no_kill", true);
        // dragon_breath: 龙息弹 → 点燃 + 火焰伤害 + AOE火焰
        dc.set("ammo_types.dragon_breath.effects.ignite.chance", 100);
        dc.set("ammo_types.dragon_breath.effects.ignite.ticks", 80);
        dc.set("ammo_types.dragon_breath.effects.fire_damage", 4);
        dc.set("ammo_types.dragon_breath.effects.aoe_fire", true);
        // he: 高爆弹 → 爆炸
        dc.set("ammo_types.he.effects.explosion", true);
        // heat: 破甲高爆弹 → 爆炸 + 护甲穿透
        dc.set("ammo_types.heat.effects.explosion", true);
        dc.set("ammo_types.heat.effects.armor_ignore", 50);
        // smoke: 烟雾弹 → 烟雾区域
        dc.set("ammo_types.smoke.effects.smoke", true);
        // flash: 闪光弹 → 致盲
        dc.set("ammo_types.flash.effects.blind.radius", 8);
        dc.set("ammo_types.flash.effects.blind.ticks", 80);
        // he_frag: 破片榴弹 → 爆炸 + 破片
        dc.set("ammo_types.he_frag.effects.explosion", true);
        dc.set("ammo_types.he_frag.effects.frag_count", 12);
        dc.set("ammo_types.he_frag.effects.frag_damage", 8);
        try { dc.save(file); } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "无法创建默认 ammo.yml", e);
        }
    }

    private void addAmmoType(FileConfiguration dc, String id, String displayName,
                             double dmg, int pen, double spread, double recoil,
                             double heat, double dura, double speed, double grav, double drag,
                             String material, int cmd) {
        String base = "ammo_types." + id + ".";
        dc.set(base + "display_name", displayName);
        dc.set(base + "damage_mult", dmg);
        dc.set(base + "penetration_bonus", pen);
        dc.set(base + "spread_mult", spread);
        dc.set(base + "recoil_mult", recoil);
        dc.set(base + "heat_mult", heat);
        dc.set(base + "durability_mult", dura);
        dc.set(base + "bullet_speed_mult", speed);
        dc.set(base + "bullet_gravity_mult", grav);
        dc.set(base + "bullet_drag_mult", drag);
        dc.set(base + "item_material", material);
        dc.set(base + "item_custom_model_data", cmd);
    }
}
