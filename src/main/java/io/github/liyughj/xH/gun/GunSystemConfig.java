package io.github.liyughj.xH.gun;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 枪械全局系统开关辅助。
 * 从 gun.yml systems 节点读取全局开关，也可通过枪械自身属性 override。
 */
public final class GunSystemConfig {

    private static GunItemConfig gunConfig;
    private static AmmoConfig ammoConfig;

    public static void init(GunItemConfig gConfig, AmmoConfig aConfig) {
        gunConfig = gConfig;
        ammoConfig = aConfig;
    }

    /** 获取弹药配置 */
    public static AmmoConfig ammo() { return ammoConfig; }

    /** 获取枪械配置 */
    public static GunItemConfig gun() { return gunConfig; }

    /** 检查某系统是否对玩家启用（全局开关 disabled → 永远 false） */
    public static boolean isSystemEnabled(Player player, String systemName) {
        if (gunConfig == null) return false;
        return gunConfig.isSystemEnabled(systemName);
    }

    /** 检查某系统是否对枪械启用（全局开关 + 枪械自身属性） */
    public static boolean isSystemEnabledForWeapon(ItemStack weapon, String systemName) {
        if (gunConfig == null || !gunConfig.isSystemEnabled(systemName)) return false;
        return true;
    }

    /** 检查全局自动换弹开关 */
    public static boolean isAutoReloadEnabled() {
        ConfigurationSection sec = getSystemSection("magazine");
        if (sec == null) return true; // 默认开启
        return sec.getBoolean("auto_reload", true);
    }

    /** 获取全局系统节点 */
    public static ConfigurationSection getSystemSection(String systemName) {
        if (gunConfig == null) return null;
        return gunConfig.getSystemsSection().getConfigurationSection(systemName);
    }

    private GunSystemConfig() {}
}
