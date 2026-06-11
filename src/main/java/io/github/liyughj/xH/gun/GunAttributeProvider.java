package io.github.liyughj.xH.gun;

import io.github.liyughj.xH.rpg.Attribute.AttributeRange;
import io.github.liyughj.xH.rpg.Attribute.RpgAttribute;
import org.bukkit.inventory.ItemStack;

/**
 * 枪械属性提供者接口 —— 预留扩展点。
 *
 * <p>外部模块（配件/符文/附魔/职业）实现此接口并注册，
 * 可在 gun.yml 模板值基础上增加或覆盖属性区间。</p>
 *
 * <h3>优先级链（由高到低）：</h3>
 * <ol>
 *   <li>物品 PDC（玩家通过指令/铁砧赋予）</li>
 *   <li>已注册的 GunAttributeProvider（配件/符文/附魔）</li>
 *   <li>gun.yml 材质模板</li>
 *   <li>属性默认值</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   GunAttributeProvider.register(new GunAttributeProvider() {
 *       public AttributeRange modifyRange(ItemStack item, RpgAttribute attr, AttributeRange current) {
 *           if (attr == RpgAttribute.GUN_DAMAGE) {
 *               return AttributeRange.of(current.roll() + 5.0); // 配件 +5 伤害
 *           }
 *           return current;
 *       }
 *   });
 * </pre>
 *
 * @see AttributeStorage 消费方
 */
public interface GunAttributeProvider {

    /**
     * 在基础值上叠加修改。
     *
     * @param item    枪械物品栈（只读，不应修改物品）
     * @param attr    属性枚举
     * @param current 当前值（来自 lower-priority 源，可为 null 表示尚未有值）
     * @return 叠加后的新区间，返回 null 表示不修改
     */
    AttributeRange modifyRange(ItemStack item, RpgAttribute attr, AttributeRange current);

    /* ==================== 注册 ==================== */

    static void register(GunAttributeProvider provider) {
        Registry.INSTANCE.providers.add(provider);
    }

    static java.util.List<GunAttributeProvider> getAll() {
        return java.util.Collections.unmodifiableList(Registry.INSTANCE.providers);
    }

    /** 持有者（避免 public static 字段） */
    final class Registry {
        static final Registry INSTANCE = new Registry();
        final java.util.List<GunAttributeProvider> providers = new java.util.ArrayList<>();
    }
}
