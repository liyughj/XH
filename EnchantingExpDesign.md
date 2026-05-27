# 附魔升级（装备经验）系统设计方案

## 一、功能概述

1. **附魔经验累积** - 工具/武器/护甲使用时积累经验，附魔等级随经验增长自动升级
2. **背包Shift悬停显示** - 在背包内按住 Shift 悬停物品时，显示每个附魔的经验进度条
3. **原版附魔同步** - 升级时自动更新物品的原版附魔等级，保持一致性
4. **罗马数字无限支持** - 最高支持显示 3999 级（Mↀↁↂↇↈ），覆盖所有实际场景

---

## 二、系统架构总览

### 2.1 核心流程

```
物品使用 → 经验获取 → 经验累积 → 等级升级 → 原版附魔同步 → UI显示更新
  │           │           │           │           │              │
  │     挖方块/攻击实体    │    PDC存储    │   升级特效    │     ProtocolLib
  │     受伤/箭矢命中     │   物品NBT    │   粒子效果    │     客户端发包
```

### 2.2 类关系图

```
XH (主类) ── 注册 ──┐
                    ├── EnchantmentLevelConfig    配置管理（程序生成YAML）
                    ├── EnchantmentLevelManager   经验计算 / 等级管理 / Lore生成
                    ├── EnchantmentLevelListener  事件监听（经验来源）
                    ├── EnchantmentLevelDisplay   客户端显示（ProtocolLib发包）
                    ├── EnchantmentLevelData      持久化存储（PDC）
                    └── SpecialEffects            升级粒子特效
```

### 2.3 文件清单

| 文件 | 职责 | 行数 |
|------|------|------|
| `EnchantmentLevelConfig.java` | 配置加载/重载/默认生成 | ~300 |
| `EnchantmentLevelManager.java` | 经验计算/Lore生成/升级逻辑 | ~400 |
| `EnchantmentLevelListener.java` | 经验获取事件监听 | ~250 |
| `EnchantmentLevelDisplay.java` | ProtocolLib客户端显示 | ~300 |
| `EnchantmentLevelData.java` | PDC持久化读写 | ~200 |
| `SpecialEffects.java` | 升级特效配置 | ~250 |

---

## 三、核心模块设计

### 3.1 持久化存储（EnchantmentLevelData）

#### 存储策略

使用 Bukkit `PersistentDataContainer (PDC)` 存储于物品 NBT，物品掉落/交易/死亡不掉落时数据跟随物品。

#### 数据结构

```
物品NBT根节点
├── xh_data (PersistentDataContainer，命名空间容器)
│   ├── enchant.{ENCHANT_NAME}.level    → Integer   当前等级
│   ├── enchant.{ENCHANT_NAME}.exp      → Integer   当前经验值
│   └── enchant.{ENCHANT_NAME}.max      → Integer   已达最高等级（历史记录）
```

#### 核心API

```java
// 读取物品上所有附魔的经验数据
Map<Enchantment, EnchantmentLevelData> loadFromItem(ItemStack item);

// 保存所有附魔经验数据到物品
void saveToItem(ItemStack item, Map<Enchantment, EnchantmentLevelData> data);

// 获取物品的全部附魔类型（支持附魔书）
Set<Enchantment> getEnchantments(ItemStack item);
```

#### 关键设计决策

- **附魔书兼容**：通过 `EnchantmentStorageMeta` 读取 `addStoredEnchant()/getStoredEnchants()` 而非普通 `addEnchant()/getEnchants()`
- **Key规范化**：`NamespacedKey` 的 key 必须小写，创建时调用 `.toLowerCase()`
- **物品印记保护**：不修改已有Lore（Lore由 EnchantmentLevelDisplay 客户端侧处理）

### 3.2 配置管理（EnchantmentLevelConfig）

#### 设计原则

与 `AnvilConfig`、`EnchantingTableConfig` 风格一致：**不依赖 jar 内资源文件**，通过 `createDefaultConfig()` 程序生成。

#### 生成的配置文件结构

```yaml
# 附魔升级系统配置文件
enabled: true                          # 总开关

level-formula:
  formula: EXPONENTIAL                 # EXPONENTIAL（指数）或 LINEAR（线性）
  base-exp: 100                        # 第一级所需经验
  multiplier: 2.0                      # 指数公式倍率
  increment: 50                        # 线性公式增量
  absolute-max: 100                    # 绝对最高等级
  respect-vanilla-max: false           # 是否遵守原版最大等级
  custom-max:                          # 自定义附魔最大等级（优先级最高）
    EFFICIENCY: 10
    PROTECTION: 10

# 注：经验来源按物品类型路由，
# - tool (工具) → BlockBreakEvent
# - melee-weapon (近战) → EntityDamageByEntityEvent
# - ranged-weapon (远程) → ProjectileHitEvent
# - armor (护甲) → EntityDamageEvent
exp-sources:
  tool:
    enabled: true
    block-exp-multiplier: 1.0          # 方块硬度乘数
  melee-weapon:
    enabled: true
    entity-exp-multiplier: 1.0         # 实体血量乘数
  armor:
    enabled: true
    damage-exp-multiplier: 1.0         # 受伤乘数

display:
  format:
    show-when-shift-only: true         # 仅Shift时显示
    exp-bar-length: 10                 # 经验条长度

upgrade-effects:
  enabled: true
```

#### 配置重载

```java
public void reload() {
    config = YamlConfiguration.loadConfiguration(configFile);
    loadAll();           // 解析全部配置
    validate();          // 验证数值合理性（如 max ≥ 1）
}
```

### 3.3 经验计算（EnchantmentLevelManager）

#### 升级经验公式

```
指数公式 (EXPONENTIAL)：
  requiredExp = baseExp * (multiplier ^ (targetLevel - 1))

线性公式 (LINEAR)：
  requiredExp = baseExp + increment * (targetLevel - 1)
```

#### 罗马数字转换（贪心算法）

```java
// 标准罗马数字 I~MMMCMXCIX (1~3999)
private static final int[] ROMAN_VALUES = {1000,900,500,400,100,90,50,40,10,9,5,4,1};
private static final String[] ROMAN_SYMBOLS = {"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};

public static String toRoman(int number) {
    if (number <= 0) return String.valueOf(number);
    StringBuilder roman = new StringBuilder();
    int n = number;
    for (int i = 0; i < ROMAN_VALUES.length; i++) {
        while (n >= ROMAN_VALUES[i]) {
            roman.append(ROMAN_SYMBOLS[i]);
            n -= ROMAN_VALUES[i];
        }
    }
    return roman.toString();
}
```

#### 升级时同步原版附魔

```java
// addExp() 中升级成功后：
ItemMeta meta = item.getItemMeta();
if (meta instanceof EnchantmentStorageMeta storageMeta) {
    storageMeta.addStoredEnchant(enchant, data.getLevel(), true);
} else {
    meta.addEnchant(enchant, data.getLevel(), true);
}
item.setItemMeta(meta);
```

#### 中文显示

使用 `TranslatableComponent` 而非硬编码英文名，由客户端根据语言设置自动翻译：

```java
// 旧方案（仅英文）
Component name = Component.text("锋利");

// 新方案（客户端翻译成中文）
Component name = enchant.description();
// 显示为 "锋利"（客户端语言为中文时）
```

### 3.4 背包Shift悬停显示（EnchantmentLevelDisplay）

#### 技术方案

使用 **ProtocolLib** 拦截 `SET_SLOT` 和 `WINDOW_ITEMS` 数据包，在客户端收到物品数据前注入经验进度Lore。

#### 显示逻辑

```
玩家打开背包 → 检测Shift按下 → 修改物品Lore →
通过ProtocolLib发包 → 客户端渲染自定义Lore

非Shift状态 → 发包时不注入Lore → 客户端显示原版附魔
```

#### Lore格式

```
┌──────────────────────────┐
│ 钻石剑                    │
│                          │
│ 锋利 V                   │ ← HIDE_ENCHANTS 隐藏原版 → 自定义渲染
│ ═══════ 500/1000         │ ← 经验条 + 数字
│                          │
│ 耐久 III                 │
│ ════════△△△ 200/500     │
│                          │
│ [原有Lore保持不变]        │
└──────────────────────────┘
```

#### 空槽位处理

```java
// 必须填充 Air 而非 null，避免 EncoderException
for (int i = 0; i < slots.size(); i++) {
    ItemStack item = slots.get(i);
    if (item == null) {
        slotData[i] = new ItemStack(Material.AIR);  // ← 关键
    } else {
        slotData[i] = modifyItemLore(item);
    }
}
```

#### Shift状态追踪

```java
// 监听玩家Shift切换
@EventHandler
public void onSneak(ToggleSneakEvent event) {
    if (event.isSneaking()) {
        shiftingPlayers.add(player.getUniqueId());
        refreshInventory(player);
    } else {
        shiftingPlayers.remove(player.getUniqueId());
        refreshInventory(player);
    }
}
```

### 3.5 经验来源（EnchantmentLevelListener）

#### 事件路由

| 物品类型 | 触发事件 | 经验获取逻辑 |
|----------|----------|-------------|
| 工具（镐/斧/铲/锄） | `BlockBreakEvent` | 基于方块硬度 × 乘数 |
| 近战武器 | `EntityDamageByEntityEvent` | 基于实体血量 × 乘数 |
| 远程武器 | `ProjectileHitEvent` | 追踪箭矢来源，基于实体血量 × 乘数 |
| 护甲 | `EntityDamageEvent` | 基于受伤量 × 乘数 |

#### 初始化时机

物品获得附魔后自动初始化经验数据（等级=原版等级，经验=0）：

- `EnchantItemEvent`（附魔台附魔）
- `PrepareAnvilEvent`（铁砧合并时，结果物品）

### 3.6 升级特效（SpecialEffects）

#### 配置风格

与 EnchantmentLevelConfig 保持一致，`createDefaultConfig()` 程序生成：

```java
private void createDefaultConfig() {
    YamlConfiguration defaultConfig = new YamlConfiguration();
    defaultConfig.set("default.particle", "ENCHANTED_HIT");
    defaultConfig.set("default.count", 30);
    // ...
    defaultConfig.options().setHeader(headerLines);
    defaultConfig.save(configFile);
}
```

#### 触发时机

```java
// EnchantmentLevelListener 中
if (upgradedEnchants.size() > 0 && SpecialEffects.getInstance() != null) {
    for (Enchantment enchant : upgradedEnchants) {
        location.getWorld().spawnParticle(
            SpecialEffects.getInstance().getParticle(enchant),
            location, count, offsetX, offsetY, offsetZ, speed
        );
    }
}
```

---

## 四、关键设计决策和踩坑记录

### 4.1 为什么不在 PrepareAnvilEvent 同时处理附魔升级？

铁砧附魔合并时，AnvilEnchantMerger 已经处理了互斥规则和附魔等级。EnchantmentLevelListener 仅负责：
1. **初始化结果物品的经验数据**（等级=原版等级，经验=0）
2. **合并经验数据**（两个输入物品的经验数据各取最大）

### 4.2 为什么不直接用 item.addEnchantment() 来控制显示？

客户端的附魔显示由原版渲染引擎管理，Lore的顺序和附魔列表是分离的。采用 `ItemFlag.HIDE_ENCHANTS` 隐藏原版显示，全部用自定义Lore渲染，才能精确控制排列。

### 4.3 为什么空槽位不能传 null？

ProtocolLib 序列化时 `ItemStack(null)` 会触发 `EncoderException: Cannot invoke "ItemStack.isEmpty()" because "itemStack" is null`。必须用 `new ItemStack(Material.AIR)`。

### 4.4 为什么 NamespacedKey 的 key 必须小写？

Minecraft 1.16+ 对 NBT key 格式有严格限制：`Invalid key. Must be [a-z0-9/._-]`。存储附魔名时必须 `.toLowerCase()`。

### 4.5 为什么用 TranslatableComponent 而不是硬编码中文？

硬编码中文在英文客户端会显示中文（不可读），而 `TranslatableComponent` 由客户端根据语言文件动态翻译成对应语言，更符合原版机制。

---

## 五、配置文件

### enchantmentLevel.yml（程序自动生成）

首次加载时由 `EnchantmentLevelConfig.createDefaultConfig()` 生成，不在 jar 内打包。

### specialEffects.yml（程序自动生成）

首次加载时由 `SpecialEffects.createDefaultConfig()` 生成，不在 jar 内打包。

---

## 六、命令

| 命令 | 功能 | 权限 |
|------|------|------|
| `/xh reload` | 重载所有配置（含附魔升级配置） | `xh.admin` |

---

## 七、注意事项

1. **事件优先级**：EnchantmentLevelListener 使用 `EventPriority.MONITOR`，不干预其他插件
2. **ProtocolLib 依赖**：EnchantmentLevelDisplay 需要 ProtocolLib，不存在时静默降级
3. **PDC 兼容性**：低于 1.14 的版本不支持 PDC，需做版本检查
4. **附魔书特殊处理**：所有读写操作都需区分 `ItemMeta.getEnchants()` 和 `EnchantmentStorageMeta.getStoredEnchants()`
5. **经验数据清理**：物品被清除/丢弃/销毁时 PDC 数据随物品 GC，无需手动清理