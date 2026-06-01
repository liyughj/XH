# XH 插件设计文档

## 一、功能概述

XH 插件是一个 Minecraft 服务器插件，主要提供以下功能：

1. **满级附魔台限制** - 只有达到指定书架数量的附魔台才能使用
2. **附魔等级限制** - 强制所有附魔等级为 I级（1级）
3. **附魔物品限制** - 只有普通书（BOOK）才能进行附魔
4. **铁砧经验限制** - 固定铁砧经验成本为指定等级（默认30级）
5. **附魔等级系统** - 附魔可通过使用获得经验并升级，同步更新原版附魔等级
6. **附魔经验显示** - 通过 `/xh lore xp` 命令切换到经验显示模式，查看附魔经验进度

---

## 二、思维导图

### 2.1 满级附魔台机制

```
满级附魔台机制
│
├── 1. 核心功能
│   ├── 检测附魔台周围书架数量
│   ├── 判断是否达到满级标准（默认15个书架，可配置）
│   ├── 非满级附魔台禁止打开附魔界面
│   └── 满级附魔台正常进行附魔
│
├── 2. 检测机制
│   ├── 触发时机
│   │   └── 玩家打开附魔台界面时
│   └── 检测范围
│       └── 以附魔台为中心的15x15区域（原版标准）
│           Y轴检测当前层和上一层
│
├── 3. 阻止机制
│   ├── 打开界面时检测
│   │   ├── 非满级 → 静默阻止打开（无任何反应）
│   │   └── 满级 → 正常打开附魔界面
│   └── 位置获取优化
│       ├── 优先从 InventoryHolder 获取
│       └── 备用方案：搜索玩家附近3格范围内的附魔台
│           只返回在合理交互距离内（6格）的附魔台
│
├── 4. 配置系统
│   └── 满级所需书架数量（默认15，可配置1-30）
│
└── 5. 书架监控
    ├── 监听书架放置事件
    ├── 监听书架破坏事件
    └── 触发附近附魔台的重新检测（用于未来缓存扩展）
```

### 2.2 附魔等级限制机制

```
附魔等级限制机制
│
├── 1. 核心功能
│   ├── 监听附魔完成事件
│   ├── 获取附魔结果的所有附魔类型
│   ├── 将所有附魔等级强制设为 I级（1级）
│   └── 应用修改后的附魔到物品
│
├── 2. 触发时机
│   └── 玩家点击附魔台完成附魔时
│       └── EnchantItemEvent 事件触发
│
├── 3. 等级修改机制
│   ├── 获取原附魔结果的 Map<Enchantment, Integer>
│   ├── 遍历所有附魔类型
│   ├── 将每个附魔的等级设为 1
│   └── 清除原附魔，重新添加 I级附魔
│
├── 4. 处理细节
│   ├── 保留所有附魔类型（种类不变）
│   ├── 只修改等级为 I级
│   ├── 支持所有可附魔物品
│   └── 兼容附魔书
│
└── 5. 配置项
    └── 强制附魔等级（固定为 I级，不可配置）
```

### 2.3 附魔物品限制机制

```
附魔物品限制机制
│
├── 1. 核心功能
│   ├── 监听物品放入附魔台事件
│   ├── 判断放入的物品类型
│   ├── 只有普通书（BOOK）显示附魔选项
│   └── 其他物品不显示任何附魔选项
│
├── 2. 触发时机
│   ├── 玩家将物品放入附魔位置时
│   │   └── PrepareItemEnchantEvent 事件触发
│   └── 玩家完成附魔时（二次检查）
│       └── EnchantItemEvent 事件触发
│
├── 3. 双重验证机制
│   ├── 第一层：PrepareItemEnchantEvent
│   │   ├── 获取附魔位置的物品
│   │   ├── 判断物品类型是否为 BOOK（普通书）
│   │   ├── 是普通书 → 正常显示附魔选项
│   │   └── 非普通书 → 将所有附魔选项设为null
│   │
│   └── 第二层：EnchantItemEvent（二次检查）
│       ├── 获取被附魔的物品
│       ├── 判断物品类型是否为 BOOK
│       ├── 是普通书 → 正常完成附魔
│       └── 非普通书 → 取消附魔事件，清除所有附魔
│
├── 4. 处理细节
│   ├── 物品可以放入附魔位置（不阻止）
│   ├── 非普通书放入后附魔台显示为空
│   ├── 不显示附魔所需青金石和经验
│   └── 玩家无法点击附魔（因为没有选项）
│
└── 5. 静默处理
    └── 不发送任何提示消息给玩家
```

### 2.4 铁砧经验限制机制

```
铁砧经验限制机制
│
├── 1. 核心功能
│   ├── 监听铁砧准备事件
│   ├── 监听铁砧点击事件（双重保障）
│   ├── 获取当前计算的经验成本
│   ├── 将经验成本强制设为固定值（默认30级，可配置）
│   └── 无论何种操作，经验成本始终为固定值
│
├── 2. 双重保障机制
│   ├── 第一层：PrepareAnvilEvent
│   │   ├── 玩家将物品放入铁砧时触发
│   │   ├── 获取当前修复成本
│   │   ├── 只有存在有效操作时才修改（originalCost > 0）
│   │   └── 强制设置修复成本为配置值
│   │
│   └── 第二层：InventoryClickEvent
│       ├── 玩家点击铁砧结果槽位时触发
│       ├── 确保点击的是结果槽位（slot 2）
│       ├── 获取当前修复成本
│       ├── 如果成本不是固定值，则重新设置
│       └── 防止服务器后续计算覆盖设置的成本
│
├── 3. 经验修改机制
│   ├── 使用 AnvilView 的新API（替代过时的 AnvilInventory）
│   ├── 获取当前修复成本 getRepairCost()
│   ├── 强制设置修复成本 setRepairCost(fixedCost)
│   └── 应用到铁砧界面和实际经验消耗
│
├── 4. 处理细节
│   ├── 支持所有铁砧操作（修复/重命名/合并附魔）
│   ├── 无论放入什么物品组合
│   ├── 经验成本始终固定
│   └── 不阻止操作，只修改经验成本
│
├── 5. ProtocolLib 修复
│   ├── 使用 ProtocolLib 监听 WINDOW_DATA 数据包
│   ├── 修改 Property ID=0（最大经验成本）
│   ├── 防止显示"过于昂贵"
│   └── 优雅降级：ProtocolLib 不存在时禁用此功能
│
└── 6. 配置项
    ├── 固定经验成本（默认30，可配置0-1000）
    └── 最大修复成本（默认30，用于数据包修复）
```

---

## 三、系统架构

### 3.1 类结构图

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                    XH (主类)                                             │
│                               插件入口，管理生命周期                                      │
│                               注册事件监听器和命令                                        │
└───────────────────────────────────┬─────────────────────────────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┬─────────────────────────┐
        │                           │                           │                         │
        ▼                           ▼                           ▼                         ▼
┌───────────────┐    ┌─────────────────────┐    ┌─────────────────┐    ┌─────────────────────┐
│  enchanting   │    │   enchanting        │    │    anvil        │    │     command         │
│    Table      │    │     Table           │    │                 │    │                     │
│    包         │    │    Config           │    │     包          │    │   XHCommand         │
│               │    │                     │    │                 │    │                     │
│ - Listener    │    │ - 书架数量配置       │    │ - AnvilConfig   │    │ - /xh reload        │
│ - Bookshelf   │    │ - 重载方法           │    │ - AnvilListener │    │ - /xh lore xp       │
│   Listener    │    │                     │    │ - AnvilPacket   │    │ - /xh lore          │
│ - Level       │    │                     │    │   Listener      │    │ - /xh help          │
│   Listener    │    │                     │    │                 │    │                     │
│ - Item        │    │                     │    │                 │    │                     │
│   Listener    │    │                     │    │                 │    │                     │
│ - Utils       │    │                     │    │                 │    │                     │
└───────────────┘    └─────────────────────┘    └─────────────────┘    └─────────────────────┘
        │
        │
        ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                           enchantmentLevel 包（附魔等级系统）                             │
│                                                                                         │
│  ┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────────────────┐  │
│  │ EnchantmentLevel    │  │ EnchantmentLevel    │  │ EnchantmentLevelListener        │  │
│  │   Config            │  │   Data              │  │   （经验获取监听）               │  │
│  │ - 经验公式配置       │  │ - 等级/经验数据存储  │  │ - 挖掘方块获得经验               │  │
│  │ - 增长模式配置       │  │ - PDC读写           │  │ - 攻击实体获得经验               │  │
│  │ - 最高等级配置       │  │ - 数据序列化         │  │ - 承受伤害获得经验               │  │
│  └─────────────────────┘  └─────────────────────┘  └─────────────────────────────────┘  │
│                                                                                         │
│  ┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────────────────┐  │
│  │ EnchantmentLevel    │  │ EnchantmentLevel    │  │ SpecialEffects                  │  │
│  │   Manager           │  │   Display           │  │   （特效系统）                   │  │
│  │ - 经验计算           │  │ - ProtocolLib数据包 │  │ - 升级粒子效果                   │  │
│  │ - 升级判断           │  │   拦截修改Lore      │  │ - 升级音效                       │  │
│  │ - 附魔顺序排序       │  │ - 经验条生成         │  │ - 满级特殊效果                   │  │
│  └─────────────────────┘  └─────────────────────┘  └─────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 核心类职责

| 类名 | 包路径 | 职责 |
|------|--------|------|
| `XH` | `io.github.liyughj.xH` | 插件主类，注册事件监听器和命令 |
| `EnchantingTableListener` | `enchantingTable` | 监听玩家与附魔台交互，阻止非满级附魔台打开 |
| `BookshelfListener` | `enchantingTable` | 监听书架放置/破坏事件，触发附近附魔台重新检测 |
| `EnchantingLevelListener` | `enchantingTable` | 监听附魔完成事件，强制附魔等级为I级 |
| `EnchantingItemListener` | `enchantingTable` | 监听物品放入附魔台和附魔完成事件，限制只有普通书可附魔 |
| `EnchantingUtils` | `enchantingTable` | 工具类，提供书架数量计算和满级判断方法 |
| `EnchantingTableConfig` | `enchantingTable` | 附魔台配置管理，读取和提供配置项访问 |
| `AnvilListener` | `anvil` | 监听铁砧准备和点击事件，固定经验成本 |
| `AnvilPacketListener` | `anvil` | 使用ProtocolLib监听数据包，修复"过于昂贵"显示问题 |
| `AnvilConfig` | `anvil` | 铁砧配置管理，读取和提供配置项访问 |
| `XHCommand` | `command` | 命令执行器，处理 `/xh` 命令及其子命令 |
| **附魔等级系统** |||
| `EnchantmentLevelConfig` | `enchantmentLevel` | 附魔等级配置管理，经验公式、增长模式、最高等级配置 |
| `EnchantmentLevelData` | `enchantmentLevel` | 附魔等级数据存储，PDC读写，数据序列化 |
| `EnchantmentLevelManager` | `enchantmentLevel` | 经验计算、升级判断、附魔顺序排序、原版附魔同步 |
| `EnchantmentLevelListener` | `enchantmentLevel` | 经验获取监听（挖掘、攻击、承受伤害） |
| `EnchantmentLevelDisplay` | `enchantmentLevel` | ProtocolLib数据包拦截，经验条Lore注入，显示模式切换 |
| `SpecialEffects` | `enchantmentLevel` | 升级特效系统（粒子、音效、满级特效） |

---

## 四、事件流程

### 4.1 玩家打开附魔台流程

```
┌─────────┐     ┌──────────────────┐     ┌─────────────────┐
│ 玩家右键 │────▶│ InventoryOpenEvent│────▶│ 判断是否为附魔台 │
│ 附魔台  │     │   (监听打开事件)  │     │   类型界面       │
└─────────┘     └──────────────────┘     └────────┬────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │   是附魔台界面   │
                                         └────────┬────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │ 获取附魔台位置   │
                                         │ - 优先从Holder   │
                                         │ - 备用：搜索附近 │
                                         └────────┬────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │ 计算周围书架数量 │
                                         │ (EnchantingUtils)│
                                         └────────┬────────┘
                                                  │
                              ┌───────────────────┴───────────────────┐
                              │                                       │
                              ▼                                       ▼
                     ┌─────────────────┐                     ┌─────────────────┐
                     │ 书架数量 >= 配置 │                     │ 书架数量 < 配置  │
                     │    (满级)        │                     │   (非满级)       │
                     └────────┬────────┘                     └────────┬────────┘
                              │                                       │
                              ▼                                       ▼
                     ┌─────────────────┐                     ┌─────────────────┐
                     │   允许打开界面   │                     │  取消打开事件    │
                     │   正常进行附魔   │                     │  静默处理        │
                     └─────────────────┘                     │  不发送任何提示  │
                                                            └─────────────────┘
```

### 4.2 附魔等级限制流程

```
┌─────────┐     ┌──────────────────┐     ┌─────────────────┐
│ 玩家点击 │────▶│ EnchantItemEvent │────▶│ 获取附魔结果     │
│ 完成附魔 │     │  (监听附魔完成)   │     │ 的附魔Map       │
└─────────┘     └──────────────────┘     └────────┬────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │ 创建新的附魔Map  │
                                         │ 遍历所有附魔类型 │
                                         │ 将等级强制设为1  │
                                         │ (I级)           │
                                         └────────┬────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │ 清除原物品的     │
                                         │ 所有附魔        │
                                         └────────┬────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │ 重新添加所有附魔 │
                                         │ 等级均为 I级    │
                                         └────────┬────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │ 玩家获得         │
                                         │ I级附魔物品     │
                                         └─────────────────┘
```

### 4.3 附魔物品限制流程（双重验证）

```
第一层验证：PrepareItemEnchantEvent
┌─────────┐     ┌─────────────────────────┐     ┌─────────────────┐
│ 玩家将  │────▶│ PrepareItemEnchantEvent │────▶│ 获取附魔位置    │
│ 物品放入 │     │   (监听物品放入)         │     │ 的物品          │
│ 附魔位置 │     │                         │     │                 │
└─────────┘     └─────────────────────────┘     └────────┬────────┘
                                                         │
                                                         ▼
                                                ┌─────────────────┐
                                                │ 判断物品类型     │
                                                │ 是否为 BOOK     │
                                                │ （普通书）       │
                                                └────────┬────────┘
                                                         │
                              ┌─────────────────────────┴─────────────────────────┐
                              │                                                   │
                              ▼                                                   ▼
                     ┌─────────────────┐                               ┌─────────────────┐
                     │ 是普通书（BOOK） │                               │ 非普通书        │
                     │                 │                               │ （工具/装备等）  │
                     └────────┬────────┘                               └────────┬────────┘
                              │                                                   │
                              ▼                                                   ▼
                     ┌─────────────────┐                               ┌─────────────────┐
                     │   正常显示       │                               │ 将所有附魔选项   │
                     │   附魔选项       │                               │ 设为null        │
                     │   （原版逻辑）   │                               │ 不显示任何选项   │
                     └─────────────────┘                               └─────────────────┘

第二层验证：EnchantItemEvent（防止绕过）
┌─────────┐     ┌──────────────────┐     ┌─────────────────┐
│ 玩家点击 │────▶│ EnchantItemEvent │────▶│ 获取被附魔物品   │
│ 完成附魔 │     │  (二次检查)       │     │                 │
└─────────┘     └──────────────────┘     └────────┬────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │ 判断物品类型     │
                                         │ 是否为 BOOK     │
                                         └────────┬────────┘
                                                  │
                              ┌───────────────────┴───────────────────┐
                              │                                       │
                              ▼                                       ▼
                     ┌─────────────────┐                     ┌─────────────────┐
                     │ 是普通书（BOOK） │                     │ 非普通书        │
                     │                 │                     │                 │
                     └────────┬────────┘                     └────────┬────────┘
                              │                                       │
                              ▼                                       ▼
                     ┌─────────────────┐                     ┌─────────────────┐
                     │   正常完成附魔   │                     │ 取消附魔事件    │
                     │                 │                     │ 清除所有附魔    │
                     └─────────────────┘                     └─────────────────┘
```

### 4.4 铁砧经验限制流程（双重保障）

```
第一层保障：PrepareAnvilEvent
┌─────────┐     ┌──────────────────┐     ┌─────────────────┐
│ 玩家将  │────▶│ PrepareAnvilEvent│────▶│ 获取当前修复成本 │
│ 物品放入 │     │ (监听铁砧准备)    │     │                 │
│ 铁砧    │     │                  │     │                 │
└─────────┘     └──────────────────┘     └────────┬────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │ originalCost > 0?│
                                         └────────┬────────┘
                                                  │
                              ┌───────────────────┴───────────────────┐
                              │                                       │
                              ▼                                       ▼
                     ┌─────────────────┐                     ┌─────────────────┐
                     │      是         │                     │       否        │
                     │ 存在有效操作    │                     │ 空铁砧/无操作   │
                     └────────┬────────┘                     └────────┬────────┘
                              │                                       │
                              ▼                                       ▼
                     ┌─────────────────┐                     ┌─────────────────┐
                     │ 强制设置修复成本 │                     │   不做任何修改   │
                     │ 为配置值        │                     │                 │
                     │（默认30级）     │                     │                 │
                     └────────┬────────┘                     └─────────────────┘
                              │
                              ▼
                     ┌─────────────────┐
                     │ 更新铁砧界面     │
                     │ 显示固定经验成本 │
                     └─────────────────┘

第二层保障：InventoryClickEvent
┌─────────┐     ┌──────────────────┐     ┌─────────────────┐
│ 玩家点击 │────▶│ InventoryClickEvent│───▶│ 判断是否为铁砧   │
│ 结果槽位 │     │ (监听点击事件)    │     │ 界面            │
└─────────┘     └──────────────────┘     └────────┬────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │ 判断点击的是     │
                                         │ 结果槽位(槽位2)? │
                                         └────────┬────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │ 获取当前修复成本 │
                                         └────────┬────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │ currentCost !=  │
                                         │ fixedCost ?     │
                                         └────────┬────────┘
                                                  │
                              ┌───────────────────┴───────────────────┐
                              │                                       │
                              ▼                                       ▼
                     ┌─────────────────┐                     ┌─────────────────┐
                     │      是         │                     │       否        │
                     │ 成本被覆盖      │                     │ 成本正确        │
                     └────────┬────────┘                     └────────┬────────┘
                              │                                       │
                              ▼                                       ▼
                     ┌─────────────────┐                     ┌─────────────────┐
                     │ 重新设置修复成本 │                     │   不做任何修改   │
                     │ 为固定值        │                     │                 │
                     └─────────────────┘                     └─────────────────┘
```

---

## 五、关键API和实现要点

### 5.1 书架检测算法

```java
/**
 * 计算附魔台周围的有效书架数量
 * 使用原版附魔台的检测逻辑
 *
 * @param enchantingTable 附魔台位置
 * @return 有效书架数量（最大15）
 */
public static int countBookshelves(Location enchantingTable) {
    int count = 0;
    Block tableBlock = enchantingTable.getBlock();

    /* 遍历以附魔台为中心的15x15区域，Y轴检测当前层和上一层 */
    for (int dx = -CHECK_RADIUS; dx <= CHECK_RADIUS; dx++) {
        for (int dz = -CHECK_RADIUS; dz <= CHECK_RADIUS; dz++) {
            for (int dy = 0; dy <= 1; dy++) {
                Location checkLoc = enchantingTable.clone().add(dx, dy, dz);
                Block checkBlock = checkLoc.getBlock();

                /* 检查是否为书架方块 */
                if (checkBlock.getType() == Material.BOOKSHELF) {
                    /* 检查书架和附魔台之间是否有阻挡 */
                    if (!isBlocked(tableBlock, checkBlock)) {
                        count++;
                        /* 原版最大有效书架数为15，超过不再计算 */
                        if (count >= DEFAULT_REQUIRED_BOOKSHELVES) {
                            return DEFAULT_REQUIRED_BOOKSHELVES;
                        }
                    }
                }
            }
        }
    }

    return count;
}
```

### 5.2 附魔台位置获取（带距离检查）

```java
/**
 * 查找玩家附近的附魔台
 * 只在合理范围内搜索（玩家与附魔台交互的最大距离为6格）
 *
 * @param location 玩家位置
 * @return 最近的附魔台位置，如果找不到则返回null
 */
private Location findNearestEnchantingTable(Location location) {
    /* 在玩家周围3格范围内搜索附魔台（确保找到的是玩家正在交互的附魔台） */
    int searchRadius = 3;
    Location nearestTable = null;
    double nearestDistance = Double.MAX_VALUE;

    for (int dx = -searchRadius; dx <= searchRadius; dx++) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                Block block = location.clone().add(dx, dy, dz).getBlock();
                if (block.getType() == Material.ENCHANTING_TABLE) {
                    /* 计算与玩家的距离 */
                    double distance = block.getLocation().distanceSquared(location);
                    /* 只接受在合理交互距离内的附魔台（6格以内） */
                    if (distance <= 36.0 && distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestTable = block.getLocation();
                    }
                }
            }
        }
    }

    return nearestTable;
}
```

### 5.3 附魔等级限制算法

```java
/**
 * 监听附魔物品事件
 * 当玩家通过附魔台完成附魔时，强制将所有附魔等级设为 I级
 *
 * @param event 附魔物品事件
 */
@EventHandler(priority = EventPriority.HIGH)
public void onEnchantItem(EnchantItemEvent event) {
    /* 获取即将添加的附魔Map */
    Map<Enchantment, Integer> enchantsToAdd = event.getEnchantsToAdd();

    /* 如果附魔列表为空，直接返回 */
    if (enchantsToAdd.isEmpty()) {
        return;
    }

    /* 创建新的附魔Map，所有等级强制设为 I级（1级） */
    Map<Enchantment, Integer> levelOneEnchants = new HashMap<>();

    for (Enchantment enchantment : enchantsToAdd.keySet()) {
        /* 强制等级为 I级（1级） */
        levelOneEnchants.put(enchantment, 1);
    }

    /* 清除原附魔列表 */
    enchantsToAdd.clear();

    /* 添加 I级附魔 */
    enchantsToAdd.putAll(levelOneEnchants);
}
```

### 5.4 附魔物品限制算法（双重验证）

```java
/**
 * 第一层验证：PrepareItemEnchantEvent
 * 限制只有普通书（BOOK）才能显示附魔选项
 */
@EventHandler(priority = EventPriority.HIGHEST)
public void onPrepareItemEnchant(PrepareItemEnchantEvent event) {
    ItemStack item = event.getItem();

    if (item == null || item.getType() == Material.AIR) {
        return;
    }

    /* 判断物品是否为普通书（BOOK） */
    if (item.getType() != Material.BOOK) {
        /* 非普通书：将所有附魔选项设为null */
        EnchantmentOffer[] offers = event.getOffers();
        if (offers != null) {
            for (int i = 0; i < offers.length; i++) {
                offers[i] = null;
            }
        }
    }
}

/**
 * 第二层验证：EnchantItemEvent
 * 二次检查：确保只有普通书才能完成附魔
 */
@EventHandler(priority = EventPriority.HIGHEST)
public void onEnchantItem(EnchantItemEvent event) {
    ItemStack item = event.getItem();

    if (item == null) {
        return;
    }

    /* 判断物品是否为普通书（BOOK） */
    if (item.getType() != Material.BOOK) {
        /* 非普通书：取消附魔事件 */
        event.setCancelled(true);
        /* 清除所有要添加的附魔 */
        event.getEnchantsToAdd().clear();
    }
}
```

### 5.5 铁砧经验限制算法（双重保障）

```java
/**
 * 第一层保障：PrepareAnvilEvent
 */
@EventHandler(priority = EventPriority.HIGHEST)
public void onPrepareAnvil(PrepareAnvilEvent event) {
    AnvilView anvilView = event.getView();
    int originalCost = anvilView.getRepairCost();

    /* 只有存在有效操作时才修改 */
    if (originalCost > 0) {
        int fixedCost = anvilConfig.getFixedExpCost();
        anvilView.setRepairCost(fixedCost);
    }
}

/**
 * 第二层保障：InventoryClickEvent
 */
@EventHandler(priority = EventPriority.HIGHEST)
public void onInventoryClick(InventoryClickEvent event) {
    /* 只处理铁砧界面 */
    if (event.getInventory().getType() != InventoryType.ANVIL) {
        return;
    }

    /* 只处理结果槽位的点击 */
    if (event.getRawSlot() != 2) {
        return;
    }

    /* 确保点击者是人类玩家 */
    if (!(event.getWhoClicked() instanceof Player)) {
        return;
    }

    /* 获取铁砧视图 */
    if (!(event.getView() instanceof AnvilView)) {
        return;
    }

    AnvilView anvilView = (AnvilView) event.getView();
    int currentCost = anvilView.getRepairCost();

    /* 只有在存在有效成本时才修改 */
    if (currentCost > 0) {
        int fixedCost = anvilConfig.getFixedExpCost();

        /* 如果成本不是固定值，则重新设置 */
        if (currentCost != fixedCost) {
            anvilView.setRepairCost(fixedCost);
        }
    }
}
```

### 5.6 ProtocolLib 数据包修复（带存在性检查）

```java
/**
 * 注册 ProtocolLib 数据包监听器
 */
private void registerPacketListener(Plugin plugin) {
    /* 检查 ProtocolLib 是否已加载 */
    if (!isProtocolLibAvailable()) {
        plugin.getLogger().warning("ProtocolLib 未找到...");
        return;
    }

    try {
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

        protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                PacketType.Play.Server.WINDOW_DATA
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                /* ... 数据包处理逻辑 ... */
            }
        });
    } catch (Exception e) {
        plugin.getLogger().warning("注册 ProtocolLib 数据包监听器时发生错误...");
    }
}

/**
 * 检查 ProtocolLib 是否可用
 */
private boolean isProtocolLibAvailable() {
    try {
        return Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
    } catch (Exception e) {
        return false;
    }
}
```

### 5.7 命令系统

```java
/**
 * XH插件命令执行器
 * 处理 /xh 命令及其子命令
 */
public class XHCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, 
                           String label, String[] args) {
        /* 无参数时显示帮助信息 */
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
            case "rl":
                return handleReload(sender);
            case "help":
                sendHelp(sender);
                return true;
            default:
                sender.sendMessage("§c未知命令...");
                return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        /* 检查权限 */
        if (!sender.hasPermission("xh.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }

        /* 重载配置 */
        plugin.getEnchantingTableConfig().reload();
        plugin.getAnvilConfig().reload();

        sender.sendMessage("§aXH 插件配置重载完成！");
        return true;
    }
}
```

### 5.8 事件监听优先级

| 事件 | 优先级 | 原因 |
|------|--------|------|
| `InventoryOpenEvent` | `HIGH` | 确保在其他插件之前处理，及时取消 |
| `BlockPlaceEvent` | `MONITOR` | 监控书架放置，不干预其他插件 |
| `BlockBreakEvent` | `MONITOR` | 监控书架破坏，不干预其他插件 |
| `EnchantItemEvent` | `HIGH` / `HIGHEST` | 在附魔应用前修改等级或取消事件 |
| `PrepareItemEnchantEvent` | `HIGHEST` | 确保最后执行，覆盖其他插件的修改 |
| `PrepareAnvilEvent` | `HIGHEST` | 确保最后执行，强制固定经验成本 |
| `InventoryClickEvent` | `HIGHEST` | 确保在点击时再次验证铁砧成本 |

---

## 六、配置文件

### 6.1 enchantingTable.yml

```yaml
# 满级附魔台配置文件
required-bookshelves: 15
# 满级所需书架数量（默认15，范围0-30）
# 附魔等级强制为 I级，无需配置
```

### 6.2 anvil.yml

```yaml
# 铁砧经验限制配置文件
fixed-exp-cost: 30
# 铁砧固定经验成本（默认30，范围0-1000）
# 无论何种操作（修复/重命名/合并附魔），经验成本始终为 fixed-exp-cost

max-repair-cost: 30
# 最大修复成本（默认30，用于 ProtocolLib 修复"过于昂贵"显示）
```

---

## 七、权限节点

| 权限节点 | 描述 | 默认值 |
|----------|------|--------|
| `xh.use` | 允许使用 `/xh` 命令 | true（所有人） |
| `xh.admin` | 允许重载插件配置 | op |

---

## 八、命令列表

| 命令 | 描述 | 权限 |
|------|------|------|
| `/xh` | 显示帮助信息 | xh.use |
| `/xh help` | 显示帮助信息 | xh.use |
| `/xh reload` | 重载插件配置 | xh.admin |
| `/xh lore xp` | 切换到经验显示模式（显示附魔经验进度） | xh.use |
| `/xh lore` | 切换回正常附魔显示模式 | xh.use |

### 8.1 附魔显示模式切换

插件提供两种附魔显示模式，玩家可以通过指令自由切换：

**正常模式**（默认）
- 显示原版附魔名称和等级
- 等级 ≤10 时显示原版数字（如"锋利 V"）
- 等级 >10 时显示中文附魔名+罗马数字（如"锋利 XI"）

**经验显示模式**
- 显示附魔经验进度条
- 格式：`附魔名 等级 [████████░░] 80/100`
- 满级时显示 `[MAX]` 标记

**切换指令**
```
/xh lore xp  → 切换到经验显示模式
/xh lore     → 切换回正常模式
```

**注意事项**
- 每个玩家的显示模式独立保存，切换后仅影响自己
- 玩家退出后模式重置为正常模式
- 模式切换通过 ProtocolLib 数据包修改实现，仅客户端显示变化，不影响实际物品数据

---

## 九、注意事项

### 9.1 性能考虑
- 书架检测涉及遍历大量方块，但只在打开附魔台时执行
- 附魔等级修改和铁砧经验修改在事件中进行，开销极小
- 书架变动时只更新附近附魔台的缓存（为未来缓存扩展预留）
- 附魔等级系统的经验获取监听开销极小，只在实际事件发生时处理
- ProtocolLib 数据包拦截对性能影响极小，只在发送数据包时处理

### 9.2 兼容性考虑
- 与其他修改附魔机制的插件可能存在冲突
- 使用适当的事件优先级避免问题
- ProtocolLib 为可选依赖，不存在时优雅降级
- 附魔等级系统使用 PDC 存储数据，与其他插件兼容性好

---

## 十、附魔等级系统详解

### 10.1 系统概述

附魔等级系统允许玩家通过使用带有附魔的物品来获得经验，当经验达到阈值时附魔等级会提升，同时同步更新原版附魔等级。

### 10.2 思维导图

```
附魔等级系统
│
├── 1. 核心功能
│   ├── 经验获取
│   │   ├── 挖掘方块（工具类附魔）
│   │   ├── 攻击实体（武器类附魔）
│   │   └── 承受伤害（护甲类附魔）
│   ├── 经验存储
│   │   └── 使用 PersistentDataContainer (PDC)
│   ├── 升级判断
│   │   ├── 指数增长公式
│   │   └── 线性增长公式
│   └── 原版附魔同步
│       └── 升级时同步更新原版附魔等级
│
├── 2. 经验获取机制
│   ├── 工具类（镐/斧/锹）
│   │   └── BlockBreakEvent 触发
│   ├── 武器类（剑/斧/弓/弩）
│   │   └── EntityDamageByEntityEvent 触发
│   ├── 护甲类（头盔/胸甲/护腿/靴子）
│   │   └── EntityDamageEvent 触发
│   └── 三叉戟
│       └── 投掷命中和近战攻击
│
├── 3. 经验计算公式
│   ├── 指数增长
│   │   └── 基础经验 × (增长倍率 ^ 当前等级)
│   ├── 线性增长
│   │   └── 基础经验 + (增长增量 × 当前等级)
│   └── 满级判断
│       └── 当前等级 >= 配置的最高等级
│
├── 4. 经验显示
│   ├── 命令切换
│   │   ├── /xh lore xp → 开启经验显示模式
│   │   └── /xh lore → 关闭经验显示模式
│   ├── ProtocolLib 数据包拦截
│   │   ├── WINDOW_ITEMS 数据包
│   │   └── SET_SLOT 数据包
│   └── Lore 格式
│       ├── 附魔名 + 等级 + 经验条 + 进度
│       └── 满级显示 [MAX] 标签
│
├── 5. 附魔顺序
│   └── 按照原版附魔注册顺序排序
│       ├── 剑类：锋利→亡灵杀手→节肢杀手→横扫之刃→火焰附加→击退→抢夺
│       ├── 工具类：效率→精准采集→时运
│       ├── 通用：耐久→经验修补
│       └── 护甲/弓/弩/三叉戟等其他附魔
│
└── 6. 特殊效果
    ├── 升级时粒子效果
    ├── 升级时音效
    └── 满级时特殊庆祝效果
```

### 10.3 配置文件

**enchantmentLevel.yml**

```yaml
# 经验公式配置
exp-formula: "EXPONENTIAL"
# 经验公式类型：EXPONENTIAL（指数增长）或 LINEAR（线性增长）

base-exp: 100
# 基础经验值（1级升到2级所需经验）

exp-multiplier: 1.5
# 指数增长倍率（每升1级，所需经验乘以该值）
# 仅在 EXPONENTIAL 模式下有效

exp-increment: 50
# 线性增长增量（每升1级，所需经验增加该值）
# 仅在 LINEAR 模式下有效

# 各附魔最高等级限制
max-levels:
  sharpness: 5           # 锋利
  smite: 5               # 亡灵杀手
  bane_of_arthropods: 5  # 节肢杀手
  sweeping: 3            # 横扫之刃
  fire_aspect: 2         # 火焰附加
  knockback: 2           # 击退
  looting: 3             # 抢夺
  efficiency: 5          # 效率
  silk_touch: 1          # 精准采集
  fortune: 3             # 时运
  unbreaking: 3          # 耐久
  mending: 1             # 经验修补
  # ... 其他附魔

# 经验获取倍率
exp-multipliers:
  mining: 1.0      # 挖掘
  combat: 1.0      # 战斗
  defense: 0.5     # 防御（承受伤害）

# 特效配置
effects:
  enabled: true
  upgrade-particles: true
  upgrade-sound: true
  max-level-celebration: true
```

### 10.4 事件流程

**经验获取流程**

```
┌─────────┐     ┌──────────────────────┐     ┌─────────────────┐
│ 玩家    │────▶│ BlockBreakEvent      │────▶│ 获取主手物品    │
│ 挖掘方块│     │ / EntityDamageEvent  │     │                 │
│         │     │ / EntityDamageByEntity│    │                 │
└─────────┘     └──────────────────────┘     └────────┬────────┘
                                                      │
                                                      ▼
                                             ┌─────────────────┐
                                             │ 检查物品是否有   │
                                             │ 可升级的附魔     │
                                             └────────┬────────┘
                                                      │
                                                      ▼
                                             ┌─────────────────┐
                                             │ 计算获得的经验值 │
                                             │ （根据配置倍率） │
                                             └────────┬────────┘
                                                      │
                                                      ▼
                                             ┌─────────────────┐
                                             │ 添加经验到PDC    │
                                             │ 检查是否升级     │
                                             └────────┬────────┘
                                                      │
                              ┌───────────────────────┴───────────────────────┐
                              │                                               │
                              ▼                                               ▼
                     ┌─────────────────┐                             ┌─────────────────┐
                     │ 未升级          │                             │ 升级了          │
                     │ 只更新经验数据  │                             │ 更新等级和经验  │
                     └─────────────────┘                             │ 同步原版附魔    │
                                                                     │ 播放特效        │
                                                                     └─────────────────┘
```

**经验显示流程**

```
┌─────────┐     ┌──────────────────────┐     ┌─────────────────┐
│ 玩家    │────▶│ /xh lore xp          │────▶│ 将玩家UUID加入   │
│ 执行命令│     │                      │     │ xpModePlayers   │
└─────────┘     └──────────────────────┘     └─────────────────┘

┌─────────┐     ┌──────────────────────┐     ┌─────────────────┐
│ 服务器  │────▶│ WINDOW_ITEMS /       │────▶│ 检查玩家是否在   │
│ 发送数据包│    │ SET_SLOT 数据包      │     │ xpModePlayers   │
└─────────┘     └──────────────────────┘     └────────┬────────┘
                                                      │
                                                      ▼
                                             ┌─────────────────┐
                                             │ 是经验显示模式   │
                                             │ 修改物品Lore    │
                                             │ 注入经验进度信息 │
                                             └────────┬────────┘
                                                      │
                                                      ▼
                                             ┌─────────────────┐
                                             │ 客户端显示      │
                                             │ 附魔名+经验条    │
                                             └─────────────────┘
```

### 9.3 静默处理原则
- 所有阻止操作都不发送任何提示消息给玩家
- 保持原版体验，玩家不会感到突兀

---

## 十、版本历史

### v0.0.1
- 初始版本
- 满级附魔台限制功能
- 附魔等级强制 I级功能
- 附魔物品限制（仅普通书）功能
- 铁砧经验固定功能
- ProtocolLib 数据包修复
- 命令系统（/xh reload）
