# 铁砧附魔合并规则设计方案

## 一、设计目标

实现铁砧附魔合并的自定义规则，同时确保与原版铁砧系统的兼容性，避免之前出现的各种错误。

## 二、核心规则

### 2.1 附魔升级规则

| 场景 | 原版行为 | 自定义行为 |
|------|----------|-----------|
| 相同等级合并 | 锋利I + 锋利I = 锋利II | 锋利I + 锋利I = 锋利I（不升级） |
| 不同等级合并 | 锋利II + 锋利I = 锋利III | 锋利II + 锋利I = 锋利II（保留最高） |

### 2.2 互斥附魔规则

#### 互斥附魔组

| 组别 | 互斥的附魔 |
|------|-----------|
| **剑类伤害附魔** | 锋利、亡灵杀手、节肢杀手 |
| **护甲保护附魔** | 保护、火焰保护、爆炸保护、弹射物保护 |
| **挖掘工具附魔** | 精准采集、时运 |
| **弓类附魔** | 无限、经验修补 |
| **弩类附魔** | 多重射击、穿透 |

#### 互斥处理逻辑

| 场景 | 处理方式 |
|------|---------|
| **附魔书 + 附魔书** | 不检查互斥，所有附魔都可以共存 |
| **附魔书 + 普通物品** | 检查互斥，同一组只保留第一个物品的附魔 |
| **普通物品 + 附魔书** | 检查互斥，同一组只保留第一个物品的附魔 |
| **普通物品 + 普通物品** | 检查互斥，同一组只保留第一个物品的附魔 |

### 2.3 附魔兼容性规则

| 场景 | 检查方式 |
|------|---------|
| **附魔书作为目标** | 不检查兼容性，任何附魔都可以添加到附魔书 |
| **普通物品作为目标** | 检查附魔是否可以应用到该物品类型 |

## 三、技术实现方案

### 3.1 事件监听策略

#### 方案A：监听 PrepareAnvilEvent（推荐）

**流程：**
1. 监听 `PrepareAnvilEvent` 事件
2. 获取原版计算的结果物品
3. 分析两个输入物品的附魔
4. 根据自定义规则重新计算附魔
5. 创建新的结果物品并设置到事件中

**优点：**
- 与原版系统集成度高
- 不需要手动处理物品给予
- 经验成本计算由原版处理

**缺点：**
- 需要正确处理附魔书的特殊存储方式
- 需要确保事件优先级正确

#### 方案B：监听 InventoryClickEvent

**流程：**
1. 监听 `InventoryClickEvent` 事件
2. 检测玩家点击铁砧结果槽位
3. 取消默认事件处理
4. 手动计算附魔合并结果
5. 给予玩家结果物品
6. 扣除经验值
7. 清空铁砧槽位

**优点：**
- 完全控制整个流程
- 可以避免原版系统的干扰

**缺点：**
- 需要手动处理所有逻辑（经验扣除、物品给予等）
- 容易与原版系统冲突
- 之前尝试此方案出现较多问题

### 3.2 附魔获取策略

#### 普通物品
```
ItemMeta meta = item.getItemMeta();
if (meta != null) {
    Map<Enchantment, Integer> enchants = meta.getEnchants();
}
```

#### 附魔书
```
ItemMeta meta = item.getItemMeta();
if (meta instanceof EnchantmentStorageMeta) {
    EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) meta;
    Map<Enchantment, Integer> enchants = bookMeta.getStoredEnchants();
}
```

### 3.3 附魔应用策略

#### 普通物品
```
ItemMeta meta = result.getItemMeta();
if (meta != null) {
    // 清除原有附魔
    for (Enchantment enchant : new HashSet<>(meta.getEnchants().keySet())) {
        meta.removeEnchant(enchant);
    }
    // 添加新附魔
    for (Map.Entry<Enchantment, Integer> entry : mergedEnchants.entrySet()) {
        meta.addEnchant(entry.getKey(), entry.getValue(), true);
    }
    result.setItemMeta(meta);
}
```

#### 附魔书
```
ItemMeta meta = result.getItemMeta();
if (meta instanceof EnchantmentStorageMeta) {
    EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) meta;
    // 清除原有存储附魔
    for (Enchantment enchant : new HashSet<>(bookMeta.getStoredEnchants().keySet())) {
        bookMeta.removeStoredEnchant(enchant);
    }
    // 添加新存储附魔
    for (Map.Entry<Enchantment, Integer> entry : mergedEnchants.entrySet()) {
        bookMeta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
    }
    result.setItemMeta(bookMeta);
}
```

## 四、关键算法设计

### 4.1 附魔合并算法

```
输入：第一个物品，第二个物品
输出：合并后的附魔映射

1. 获取第一个物品的所有附魔
2. 获取第二个物品的所有附魔
3. 创建空的合并结果映射
4. 将第一个物品的所有附魔添加到结果映射
5. 遍历第二个物品的每个附魔：
   a. 如果该附魔已存在于结果映射中：
      - 比较等级，保留较高等级
      - 不升级（相同等级保持原样）
   b. 如果该附魔不存在于结果映射中：
      - 检查是否与结果映射中的附魔互斥
      - 如果不互斥，添加到结果映射
6. 返回合并结果映射
```

### 4.2 互斥检查算法

```
输入：要检查的附魔，已存在的附魔集合
输出：是否互斥

1. 确定要检查的附魔属于哪个互斥组
2. 遍历已存在的附魔集合：
   a. 如果已存在的附魔与要检查的附魔在同一互斥组
   b. 返回 true（互斥）
3. 返回 false（不互斥）
```

### 4.3 兼容性检查算法

```
输入：目标物品，要检查的附魔
输出：是否兼容

1. 如果目标物品是附魔书：
   a. 返回 true（附魔书可以存储任何附魔）
2. 如果目标物品是普通物品：
   a. 使用 enchant.canEnchantItem(target) 检查
   b. 返回检查结果
```

## 五、实现步骤

### 步骤1：创建附魔合并工具类
- 创建 `AnvilEnchantMerger` 类
- 实现附魔获取方法（支持普通物品和附魔书）
- 实现附魔合并算法
- 实现互斥检查算法
- 实现兼容性检查算法

### 步骤2：修改 AnvilListener
- 在 `PrepareAnvilEvent` 中调用附魔合并工具
- 获取原版结果物品
- 重新计算附魔
- 设置新的结果物品

### 步骤3：测试和验证
- 测试附魔书 + 附魔书合并
- 测试普通物品 + 附魔书合并
- 测试普通物品 + 普通物品合并
- 测试互斥附魔处理
- 测试兼容性检查

## 六、注意事项

### 6.1 避免之前的错误

1. **不要同时设置多种结果方式**
   - 只使用 `event.setResult()`
   - 不要使用 `anvilView.setItem(2, result)`

2. **正确处理附魔书**
   - 使用 `EnchantmentStorageMeta` 处理附魔书
   - 不要混淆 `getEnchants()` 和 `getStoredEnchants()`

3. **正确处理事件优先级**
   - 使用 `EventPriority.HIGHEST` 确保最后执行
   - 但不要过早干预原版计算

4. **避免重复处理**
   - 只在 `PrepareAnvilEvent` 中处理
   - 不要在 `InventoryClickEvent` 中重复处理

### 6.2 兼容性考虑

1. **ProtocolLib 集成**
   - 确保 ProtocolLib 的 "过于昂贵" 修复仍然有效
   - 不要与附魔合并功能冲突

2. **其他插件兼容性**
   - 考虑其他可能修改铁砧行为的插件
   - 使用适当的事件优先级避免冲突

## 七、配置选项

在 `anvil.yml` 中添加以下配置选项：

```yaml
# 是否启用自定义附魔合并规则
enable-custom-enchant-merge: true

# 是否禁止附魔升级
prevent-enchant-upgrade: true

# 是否检查互斥附魔
check-exclusive-enchants: true

# 是否检查附魔兼容性
check-enchant-compatibility: true
```

## 八、总结

本设计方案采用 **方案A（监听 PrepareAnvilEvent）**，通过创建独立的附魔合并工具类，正确处理附魔书的特殊存储方式，避免之前的各种错误。核心原则是**不干预铁砧的正常工作流程，只在必要时修改附魔结果**。
