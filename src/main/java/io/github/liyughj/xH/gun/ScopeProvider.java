package io.github.liyughj.xH.gun;

import org.bukkit.inventory.ItemStack;

/**
 * 瞄具/倍镜扩展接口 —— 供配件/符文/附魔模块实现，为枪械提供放大倍率。
 *
 * <h3>用法</h3>
 * <pre>
 *   // 配件模块：4倍镜
 *   ScopeProvider.register((item, currentMag) -&gt; {
 *       if (hasScope4x(item)) return 4.0;
 *       return currentMag;
 *   });
 * </pre>
 *
 * <p>默认所有枪械为 1× 机瞄。配件模块注册 Provider 后可叠加更高倍率。
 * 多个 Provider 按注册顺序链式调用。</p>
 */
@FunctionalInterface
public interface ScopeProvider {

    /**
     * 修改当前倍率。
     *
     * @param item                 枪械物品
     * @param currentMagnification 当前倍率（默认 1.0 = 1×）
     * @return 修改后的倍率（≥ 0.5 避免负 FOV）
     */
    double modifyMagnification(ItemStack item, double currentMagnification);

    /* ---- 注册表 ---- */

    static void register(ScopeProvider provider) {
        Registry.INSTANCE.providers.add(provider);
    }

    static java.util.List<ScopeProvider> getAll() {
        return java.util.Collections.unmodifiableList(Registry.INSTANCE.providers);
    }

    /** 遍历所有 Provider 计算最终倍率 */
    static double compute(ItemStack item) {
        double mag = 1.0;
        for (ScopeProvider p : Registry.INSTANCE.providers) {
            mag = p.modifyMagnification(item, mag);
        }
        return Math.max(0.5, mag);
    }

    final class Registry {
        static final Registry INSTANCE = new Registry();
        final java.util.List<ScopeProvider> providers = new java.util.ArrayList<>();
    }
}
