# 枪械未实现系统详细设计

> 审阅后逐系统实现。标注「全局」的写在 gun.yml 顶层 `systems:` 节下，标注「枪械」的写在 `items.MATERIAL.*` 下。

---

## 目录

1. [过热系统 (Overheat)](#1-过热系统-overheat)
2. [故障系统 (Malfunction)](#2-故障系统-malfunction)
3. [弹夹系统 (Magazine)](#3-弹夹系统-magazine)
4. [枪膛系统 (Chamber)](#4-枪膛系统-chamber)
5. [弹药系统 (Ammo)](#5-弹药系统-ammo)
6. [耐久度系统 (Durability)](#6-耐久度系统-durability)
7. [穿透系统 (Penetration)](#7-穿透系统-penetration)
8. [弹道系统 (Ballistics)](#8-弹道系统-ballistics)
9. [特殊武器类型](#9-特殊武器类型)
10. [全局开关总览](#10-全局开关总览)
11. [扩展接口总览](#11-扩展接口总览)
12. [完整射击流水线](#12-完整射击流水线)

---

## 1. 过热系统 (Overheat)

### 1.1 概念

每次射击累积热量。热量越高，散布和后坐越差，故障率越高。超过阈值进入过热状态，强制停火冷却。

### 1.2 状态机

```
  IDLE ──射击──> 热量 += heat_per_shot
                 ├─ 热量 < 阈值 → 正常（散布/后坐/故障受热量系数影响）
                 └─ 热量 ≥ 阈值 → OVERHEAT
                                    ├─ 禁止射击
                                    ├─ 强制冷却（热量每秒恢复 cool_rate）
                                    ├─ 玩家提示：枪管过热
                                    └─ 热量 < 阈值 → IDLE (可射)
```

### 1.3 属性清单

| # | key | 名称 | 类型 | 范围 | 默认 | 级别 | 说明 |
|---|-----|------|------|------|:--:|:--:|------|
| 1 | `gun_heat_per_shot` | 每发热量 | FLAT | 0~1000 | 5 | 枪械 | 每发子弹产生的热量值 |
| 2 | `gun_heat_threshold` | 过热阈值 | FLAT | 1~10000 | 100 | 枪械 | 热量达标后触发过热 |
| 3 | `gun_heat_cool_rate` | 冷却速率(/s) | FLAT | 0.1~1000 | 20 | 枪械 | 停火时每秒散热量 |
| 4 | `gun_heat_overheat_penalty_ticks` | 过热惩罚时间 | FLAT(tick) | 0~400 | 40 | 枪械 | 过热后强制停火时间，0=冷却完即时恢复 |
| 5 | `gun_heat_spread_factor` | 热量散布系数 | PERCENT | 0~100 | 50 | 枪械 | 热量% × 此系数 = 额外散布倍率；如热量60%×50%=1.3倍 |
| 6 | `gun_heat_recoil_factor` | 热量后坐系数 | PERCENT | 0~100 | 50 | 枪械 | 同上，影响后坐力 |
| 7 | `gun_heat_malfunction_factor` | 热量故障系数 | PERCENT | 0~100 | 100 | 枪械 | 热量% × 此系数 × 基础故障率 = 额外故障% |
| 8 | `gun_heat_ads_cool_bonus` | 开镜冷却加成 | PERCENT | 0~100 | 30 | 枪械 | 开镜时冷却速率×(1+此%)，狙击手开镜散热更快（预留） |
| 9 | `gun_heat_smoke_threshold` | 冒烟阈值(%) | PERCENT | 0~100 | 60 | 枪械 | 热量/阈值≥此%时冒烟粒子效果 |
| 10 | `overheat_system_enabled` | 过热系统开关 | — | — | true | **全局** | gun.yml → `systems.overheat.enabled` |

### 1.4 与其他系统联动

| 联动方向 | 公式 |
|---------|------|
| → 散布 | `额外散布倍率 = 1 + (heat% × heat_spread_factor / 100)` |
| → 后坐 | `额外后坐倍率 = 1 + (heat% × heat_recoil_factor / 100)` |
| → 故障 | `故障率增量 = base_malfunction × heat% × heat_malfunction_factor / 100` |
| → 射击 | 过热状态直接禁止射击（doShoot 最前端拦截） |
| ← 开镜 | 开镜时 cool_rate × (1 + ads_cool_bonus/100) |
| → 弹夹 | 过热期间换弹正常进行（换弹不能加速冷却，但热量继续自然衰减） |

### 1.5 细节

- 热量为 **每枪独立**（per-player × per-weapon-identity）
- 切换武器后热量 **保留**（模拟真实枪管残留温度），但防止过于不友好：`切枪时热量衰减 = max(切枪间隔 × cool_rate_offhand, 0)` ；即切枪后副枪仍在冷却
- 过热时 HotBar / ActionBar 显示红色警告条
- 冒烟粒子：热量达 smoke_threshold 后在枪口位置生成 `smoke` 粒子，密度随热量升高

---

## 2. 故障系统 (Malfunction)

### 2.1 概念

每次射击有概率触发故障，故障发生后枪械暂时不可用。故障概率受热量、耐久度影响。

### 2.2 故障类型

| 类型码 | 名称 | 效果 | 解除方式 | 典型持续 |
|:--:|------|------|------|:--:|
| 0 | 哑火 (Misfire) | 当发不发射，短暂停顿 | 自动恢复 | 10 tick |
| 1 | 卡壳 (Jam) | 枪械卡死，完全无法射击 | **左键排除** (玩家手动) | 直到排除 |
| 2 | 炸膛 (Catastrophic) | 发射但耐久大减 + 对自身造成溅射伤害 | 自动恢复（但耐久永久损失） | 瞬间 |

### 2.3 属性清单

| # | key | 名称 | 类型 | 范围 | 默认 | 级别 | 说明 |
|---|-----|------|------|------|:--:|:--:|------|
| 1 | `gun_malfunc_base_chance` | 基础故障率 | PERCENT | 0~100 | 2 | 枪械 | 每发独立 roll 的基础概率 |
| 2 | `gun_malfunc_heat_factor` | 热量故障因子 | PERCENT | 0~500 | 100 | 枪械 | 与过热系统联动的乘数，独立于过热系统内的系数（叠加用） |
| 3 | `gun_malfunc_dura_factor` | 耐久故障因子 | PERCENT | 0~500 | 50 | 枪械 | 耐久% × 此系数 = 额外故障% |
| 4 | `gun_malfunc_type_weights` | 故障类型权重 | FLAT(csv) | 0~100各 | "80,20,0" | 枪械 | 3个值的逗号分隔字符串: 哑火,卡壳,炸膛 的权重（总和不必=100，按比例抽） |
| 5 | `gun_malfunc_jam_clear_ticks` | 卡壳排除时间 | FLAT(tick) | 1~200 | 30 | 枪械 | 左键排除完成后等待的 ticks（模拟排障动作） |
| 6 | `gun_malfunc_cata_damage` | 炸膛自伤 | FLAT | 0~100 | 10 | 枪械 | 对持枪者自身造成的伤害值 |
| 7 | `gun_malfunc_cata_dura_loss` | 炸膛耐久损耗 | FLAT | 0~10000 | 500 | 枪械 | 炸膛时额外扣除的耐久度 |
| 8 | `malfunction_system_enabled` | 故障系统开关 | — | — | true | **全局** | gun.yml → `systems.malfunction.enabled` |
| 9 | `malfunction_global_base_chance` | 全局基础故障率 | PERCENT | 0~10 | 0 | **全局** | 所有枪额外叠加，0=不叠加 |

### 2.4 故障概率公式

```
总故障率 = gun_malfunc_base_chance
         + (全局 base)         // 全局
         + (热量% × gun_malfunc_heat_factor / 100)  // 过热联动
         + ((1 - 当前耐久%) × gun_malfunc_dura_factor / 100)  // 耐久联动
         + (GunAttributeProvider 注入)  // 配件/符文可能加减

总故障率 clamp(0, 100)

每发 roll:
  if (rng(0~100) < 总故障率) → 触发故障
    → 按 type_weights 抽类型
```

### 2.5 故障触发后

```
【哑火 0】
  - 当前 doShoot 直接 return (当发不发射)
  - 散布累加不生效（因为没有发射）
  - 后坐不生效
  - 热量不增加
  - 弹夹不减
  - 耐久不减
  - 自动恢复 10 tick 后可再次射击

【卡壳 1】
  - 禁止射击直到玩家左键排除
  - 左键排除 → gun_malfunc_jam_clear_ticks 后恢复
  - ActionBar: "§c卡壳！左键排除"

【炸膛 2】
  - 当发正常发射（伤害正常计算）
  - 自身受到 gun_malfunc_cata_damage 伤害
  - 耐久扣 gun_malfunc_cata_dura_loss
  - 屏幕震动 + 爆炸音效
  - 粒子效果
```

### 2.6 细节

- 左键排除卡壳时：播放排障音效，ActionBar 倒计时
- 排障期间可移动但不可射击
- 切换武器 → 卡壳标记清除（模拟放下卡壳枪换副武器）
- 炸膛的频率应该极低（默认权重 0），否则影响体验

---

## 3. 弹夹系统 (Magazine)

### 3.1 概念

每把枪有弹夹容量。射击消耗弹量，弹尽后需换弹。换弹有时间惩罚。

### 3.2 状态机

```
  RELOADING ──时间到──> 弹量 = 容量（或容量+1 枪膛）
  ▲                    │
  │                    ▼
  ├── 弹量=0 自动触发 ──> IDLE
  ├── 右键空枪触发
  ├── 按 R 强制换弹
  │
  IDLE ──射击──> 弹量--
```

### 3.3 属性清单

| # | key | 名称 | 类型 | 范围 | 默认 | 级别 | 说明 |
|---|-----|------|------|------|:--:|:--:|------|
| 1 | `gun_mag_capacity` | 弹夹容量 | FLAT | 1~500 | 30 | 枪械 | 装满时的弹量 |
| 2 | `gun_reload_time_ticks` | 换弹时间 | FLAT(tick) | 1~600 | 40 | 枪械 | 换弹动作持续的 tick 数 |
| 3 | `gun_reload_empty_time_ticks` | 空仓换弹时间 | FLAT(tick) | 1~600 | 60 | 枪械 | 弹量为 0 时的换弹时间（通常更长） |
| 4 | `gun_reload_staged` | 分阶段换弹 | FLAT(0~1) | 0~1 | 0 | 枪械 | 1=换弹分"卸弹夹"和"装弹夹"两段，可中途打断保留进度（高端枪用） |
| 5 | `gun_dry_fire_sound` | 空仓击发音效 | STRING | — | "" | 枪械 | 空弹时点击播放的音效，空字符串=默认 click |
| 6 | `gun_reload_sound` | 换弹音效 | STRING | — | "" | 枪械 | |
| 7 | `magazine_system_enabled` | 弹夹系统开关 | — | — | true | **全局** | gun.yml → `systems.magazine.enabled` |
| 8 | `magazine_auto_reload` | 自动换弹 | bool | — | true | **全局** | 弹尽后自动开始换弹 vs 需要手动右键 |
| 9 | `magazine_reload_key` | 换弹键 | string | — | "drop" | **全局** | 手动换弹按键：「drop」(Q键弃物)、「swap」(F键)、「sneak_right」(潜行+右键) |

### 3.4 与其他系统联动

| 联动 | 说明 |
|------|------|
| → 射击模式 | 全自动弹尽 → 自动停火 → 开始换弹 → 换完后需重新 Toggle 全自动 |
| → 过热 | 换弹期间热量继续自然冷却 |
| → 故障 | 换弹期间不能触发故障（因为不射击），但也不能排除卡壳（需先左键排除再换弹） |
| → 枪膛 | 战术换弹速度 = reload_time × (1 - chamber_reload_bonus/100) |
| → 开镜 | 换弹期间强制退出开镜 |

### 3.5 细节

- 弹量显示在 ActionBar：`§a|||||||||| §7|||||||||| §f30/30`
- 换弹期间移动速度降为 80%
- 切换武器 → 换弹中断
- 弹量保存在物品 PDC 上（掉地/死亡保留），不依赖玩家状态

---

## 4. 枪膛系统 (Chamber)

### 4.1 概念

模拟真实枪械的"膛内预装弹"机制。弹夹打完时枪膛可能还有一发。

### 4.2 逻辑

```
场景 A：正常射击到弹夹空
  弹夹=0，枪膛=1 → 还能再射一发 → 弹夹=0，枪膛=0 → 换弹

场景 B：弹夹未空时换弹（战术换弹）
  弹夹=20，枪膛=1
  换弹 → 卸掉旧弹夹 → 装新弹夹
  新弹量 = capacity + 1（枪膛那发保留）
  换弹更快（跳过上膛动作）

场景 C：完全空枪换弹
  弹夹=0，枪膛=0
  换弹 → 装新弹夹 → 上膛
  新弹量 = capacity
  换弹较慢（需附加上膛时间）
```

### 4.3 属性清单

| # | key | 名称 | 类型 | 范围 | 默认 | 级别 | 说明 |
|---|-----|------|------|------|:--:|:--:|------|
| 1 | `gun_chamber_enabled` | 启用枪膛 | FLAT(0~1) | 0~1 | 0 | 枪械 | 1=启用（栓动狙击、手枪等），0=不启用（开放式枪机） |
| 2 | `gun_chamber_tactical_reload_bonus` | 战术换弹缩减 | PERCENT | 0~100 | 30 | 枪械 | 膛内有弹时换弹时间缩减比例 |
| 3 | `gun_chamber_bolt_time_ticks` | 上膛/拉栓时间 | FLAT(tick) | 0~100 | 10 | 枪械 | 空仓换弹后附加的拉栓时间（狙击枪更长） |
| 4 | `gun_chamber_auto_bolt` | 自动上膛 | FLAT(0~1) | 0~1 | 1 | 枪械 | 1=射击后自动上膛(半自动/全自动)，0=需手动拉栓(栓动狙击) |
| 5 | `chamber_system_enabled` | 枪膛系统全局开关 | — | — | true | **全局** | gun.yml → `systems.chamber.enabled` |

### 4.4 手动拉栓

如果 `gun_chamber_auto_bolt = 0`（栓动狙击）：
- 每发射击后 → 枪膛空 → 需左键拉栓装填下一发
- 拉栓时间 = `gun_chamber_bolt_time_ticks`
- 弹夹弹量正常消耗
- 这个机制模拟 AWP/M24 等栓动狙击

---

## 5. 弹药系统 (Ammo)

### 5.1 核心概念

弹药系统是整个枪械体系的中枢——**口径决定兼容性，弹种决定弹道行为**。

```
口径 (Caliber)  ──物理规格──  枪械必须匹配口径才能使用
                          弹夹受口径约束
                          弹药物品标定口径

弹种 (AmmoType) ──性能分支──  同一口径下的不同弹头
                          修改伤害/穿透/散布/后坐/热量等
                          由独立 ammo.yml 配置
```

### 5.2 新配置文件：`ammo.yml`

独立于 `gun.yml`，专门管理口径和弹种。放置在 `plugins/XH/ammo.yml`。

```yaml
# ============================================================
#  弹药系统配置文件 (ammo.yml)
# ============================================================
#  口径 = 物理规格，枪械/弹夹/弹药通过口径匹配
#  弹种 = 同一口径下的性能变体（如空尖弹vs穿甲弹）
# ============================================================

enabled: true

# ============================================================
#  一、口径定义 (Caliber)
# ============================================================
#  每个口径定义其可用的弹种列表和默认弹种
#  key 即为口径ID，供 gun.yml 的 caliber 字段引用
#
#  category: 口径大类 (pistol/rifle/shotgun/sniper/explosive/energy)
#    - pistol:  手枪/冲锋枪口径 (9mm/.45)
#    - rifle:   步枪/卡宾枪口径 (5.56mm/7.62mm)
#    - sniper:  狙击专用口径 (.300/.338/.50)
#    - shotgun: 霰弹 (12 Gauge)
#    - explosive: 榴弹/火箭 (40mm/Rocket)
#    - energy:  激光/能量武器
# ============================================================

calibers:
  # ---- 手枪/冲锋枪口径 ----
  "9mm":
    display_name: "9×19mm 帕拉贝鲁姆"
    category: "pistol"
    stack_size: 64               # 每格堆叠上限
    available_types:             # 该口径可用的弹种
      - "fmj"
      - "hp"
      - "ap"
      - "tracer"
      - "subsonic"
      - "+p"
      - "match"
      - "rubber"
    default_type: "fmj"
    craft_base_material: "IRON_INGOT"
    craft_base_count: 32         # 1个铁锭 = 32发

  ".45acp":
    display_name: ".45 ACP"
    category: "pistol"
    stack_size: 48
    available_types:
      - "fmj"
      - "hp"
      - "subsonic"
      - "+p"
      - "match"
    default_type: "fmj"
    craft_base_material: "IRON_INGOT"
    craft_base_count: 24

  "5.7mm":
    display_name: "5.7×28mm"
    category: "pistol"
    stack_size: 64
    available_types:
      - "fmj"
      - "ap"
      - "tracer"
      - "match"
    default_type: "ap"
    craft_base_material: "IRON_INGOT"
    craft_base_count: 36

  # ---- 步枪/卡宾枪口径 ----
  "5.56mm":
    display_name: "5.56×45mm NATO"
    category: "rifle"
    stack_size: 64
    available_types:
      - "fmj"
      - "hp"
      - "ap"
      - "tracer"
      - "incendiary"
      - "subsonic"
      - "match"
      - "rubber"
    default_type: "fmj"
    craft_base_material: "IRON_INGOT"
    craft_base_count: 20

  "7.62mm":
    display_name: "7.62×51mm NATO"
    category: "rifle"
    stack_size: 48
    available_types:
      - "fmj"
      - "hp"
      - "ap"
      - "tracer"
      - "incendiary"
      - "match"
    default_type: "fmj"
    craft_base_material: "IRON_INGOT"
    craft_base_count: 16

  "7.62x39mm":
    display_name: "7.62×39mm"
    category: "rifle"
    stack_size: 64
    available_types:
      - "fmj"
      - "hp"
      - "ap"
      - "tracer"
      - "incendiary"
      - "match"
    default_type: "fmj"
    craft_base_material: "IRON_INGOT"
    craft_base_count: 20

  # ---- 狙击专用口径 ----
  ".300win":
    display_name: ".300 Winchester Magnum"
    category: "sniper"
    stack_size: 32
    available_types:
      - "fmj"
      - "hp"
      - "ap"
      - "match"
      - "incendiary"
    default_type: "match"
    craft_base_material: "DIAMOND"
    craft_base_count: 8

  ".338lapua":
    display_name: ".338 Lapua Magnum"
    category: "sniper"
    stack_size: 24
    available_types:
      - "fmj"
      - "ap"
      - "match"
      - "incendiary"
    default_type: "ap"
    craft_base_material: "DIAMOND"
    craft_base_count: 6

  ".50bmg":
    display_name: ".50 BMG"
    category: "sniper"
    stack_size: 16
    available_types:
      - "fmj"
      - "ap"
      - "incendiary"
      - "tracer"
    default_type: "ap"
    craft_base_material: "NETHERITE_INGOT"
    craft_base_count: 4

  # ---- 霰弹口径 ----
  "12gauge":
    display_name: "12 Gauge"
    category: "shotgun"
    stack_size: 32
    available_types:
      - "buckshot"
      - "slug"
      - "flechette"
      - "dragon_breath"
      - "rubber"
    default_type: "buckshot"
    craft_base_material: "IRON_INGOT"
    craft_base_count: 12

  # ---- 爆炸物口径 ----
  "40mm":
    display_name: "40×46mm 榴弹"
    category: "explosive"
    stack_size: 4
    available_types:
      - "he"
      - "smoke"
      - "flash"
      - "he_frag"
    default_type: "he"
    craft_base_material: "IRON_BLOCK"
    craft_base_count: 1

  "rocket":
    display_name: "火箭弹"
    category: "explosive"
    stack_size: 1
    available_types:
      - "he"
      - "heat"
      - "incendiary"
    default_type: "he"
    craft_base_material: "IRON_BLOCK"
    craft_base_count: 1

  # ---- 能量武器 ----
  "energy_cell":
    display_name: "能量电池"
    category: "energy"
    stack_size: 16
    available_types:
      - "standard"
      - "overcharged"
      - "focused"
    default_type: "standard"
    craft_base_material: "REDSTONE_BLOCK"
    craft_base_count: 2

# ============================================================
#  二、弹种定义 (Ammo Type)
# ============================================================
#  每个弹种定义其性能修正和特殊效果
#  所有修正系数 1.0 = 不变，>1.0 = 增强，<1.0 = 削弱
# ============================================================

ammo_types:
  # ---------- 通用类 ----------
  "fmj":
    display_name: "全金属被甲弹"
    lore: "§7标准军用弹药"
    description: "各项性能均衡的标准弹药"
    # 伤害修正
    damage_mult: 1.0
    # 穿透修正 (penetration_count += this)
    penetration_bonus: 0
    # 散布修正 (>1.0 = 更散)
    spread_mult: 1.0
    # 后坐修正
    recoil_mult: 1.0
    # 热量生成修正
    heat_mult: 1.0
    # 耐久损耗修正
    durability_mult: 1.0
    # 弹道修正
    bullet_speed_mult: 1.0
    bullet_gravity_mult: 1.0
    bullet_drag_mult: 1.0
    # 特殊效果 (见 5.7)
    effects: {}
    # 物品外观
    item_material: "IRON_NUGGET"
    item_custom_model_data: 1
    item_color: "&7"

  # ---------- 伤害强化类 ----------
  "hp":
    display_name: "空尖弹"
    lore: "§c高伤害 §7| §c低穿透"
    description: "命中后扩张变形，对无甲目标致命"
    damage_mult: 1.30
    penetration_bonus: -2
    spread_mult: 1.0
    recoil_mult: 1.05
    heat_mult: 1.0
    durability_mult: 1.0
    bullet_speed_mult: 0.95
    bullet_gravity_mult: 1.0
    bullet_drag_mult: 1.02
    effects:
      bleed:
        chance: 20
        damage: 3
        ticks: 40
    item_material: "REDSTONE"
    item_custom_model_data: 2
    item_color: "&c"

  "+p":
    display_name: "+P 高压弹"
    lore: "§c高伤害 §7| §c高后坐 §7| §c高损耗"
    description: "增加装药量提升初速和伤害，但枪械损耗加剧"
    damage_mult: 1.20
    penetration_bonus: +1
    spread_mult: 1.10
    recoil_mult: 1.30
    heat_mult: 1.40
    durability_mult: 1.30
    bullet_speed_mult: 1.15
    bullet_gravity_mult: 1.0
    bullet_drag_mult: 0.97
    effects: {}
    item_material: "GLOWSTONE_DUST"
    item_custom_model_data: 3
    item_color: "&6"

  # ---------- 穿透强化类 ----------
  "ap":
    display_name: "穿甲弹"
    lore: "§b高穿透 §7| §c低伤害"
    description: "硬质弹芯，穿透多层目标，对护甲有效"
    damage_mult: 0.85
    penetration_bonus: +3
    spread_mult: 0.95
    recoil_mult: 1.05
    heat_mult: 1.10
    durability_mult: 1.15
    bullet_speed_mult: 1.05
    bullet_gravity_mult: 0.95
    bullet_drag_mult: 0.98
    effects:
      armor_ignore: 20         # 无视目标 20% 护甲值
    item_material: "IRON_NUGGET"
    item_custom_model_data: 4
    item_color: "&b"

  "flechette":
    display_name: "箭形弹"
    lore: "§b高穿透 §7| §7霰弹专用"
    description: "多枚微型箭矢，穿透力极强"
    damage_mult: 0.75
    penetration_bonus: +5
    spread_mult: 0.80
    recoil_mult: 1.15
    heat_mult: 1.05
    durability_mult: 1.10
    bullet_speed_mult: 1.10
    bullet_gravity_mult: 0.90
    bullet_drag_mult: 0.95
    effects:
      armor_ignore: 30
    item_material: "ARROW"
    item_custom_model_data: 5
    item_color: "&3"

  # ---------- 精度强化类 ----------
  "match":
    display_name: "竞赛弹"
    lore: "§a高精度 §7| §a低损耗"
    description: "精密加工，散布最小化，适合狙击"
    damage_mult: 1.0
    penetration_bonus: 0
    spread_mult: 0.70
    recoil_mult: 0.95
    heat_mult: 1.0
    durability_mult: 0.90
    bullet_speed_mult: 1.02
    bullet_gravity_mult: 0.95
    bullet_drag_mult: 0.95
    effects: {}
    item_material: "GOLD_NUGGET"
    item_custom_model_data: 6
    item_color: "&a"

  # ---------- 特种效果类 ----------
  "tracer":
    display_name: "曳光弹"
    lore: "§e可见弹道 §7| §e微量点燃"
    description: "弹尾燃烧发光，修正弹道，轻微点燃目标"
    damage_mult: 1.0
    penetration_bonus: 0
    spread_mult: 0.85          # 可视弹道帮助修正
    recoil_mult: 1.0
    heat_mult: 1.05
    durability_mult: 1.0
    bullet_speed_mult: 1.0
    bullet_gravity_mult: 1.0
    bullet_drag_mult: 1.0
    effects:
      trail: "flame"            # 强制显示尾迹
      ignite:
        chance: 15
        ticks: 30
    item_material: "GLOWSTONE_DUST"
    item_custom_model_data: 7
    item_color: "&e"

  "incendiary":
    display_name: "燃烧弹"
    lore: "§6点燃目标 §7| §6高热量"
    description: "命中后点燃目标，持续火焰伤害"
    damage_mult: 1.10
    penetration_bonus: 0
    spread_mult: 1.0
    recoil_mult: 1.05
    heat_mult: 1.30
    durability_mult: 1.05
    bullet_speed_mult: 1.0
    bullet_gravity_mult: 1.0
    bullet_drag_mult: 1.0
    effects:
      ignite:
        chance: 100
        ticks: 60
      fire_damage: 2           # 每 tick 额外火焰伤害
    item_material: "BLAZE_POWDER"
    item_custom_model_data: 8
    item_color: "&6"

  "subsonic":
    display_name: "亚音速弹"
    lore: "§8消音 §7| §8低后坐"
    description: "低于音速飞行，降低枪声和后坐，适合消音器"
    damage_mult: 0.95
    penetration_bonus: 0
    spread_mult: 1.0
    recoil_mult: 0.80
    heat_mult: 0.90
    durability_mult: 1.0
    bullet_speed_mult: 0.65
    bullet_gravity_mult: 1.3
    bullet_drag_mult: 1.15
    effects:
      silent: true              # 降低枪声音量
    item_material: "GUNPOWDER"
    item_custom_model_data: 9
    item_color: "&8"

  # ---------- 非致命类 ----------
  "rubber":
    display_name: "橡胶弹"
    lore: "§f非致命 §7| §f高击退"
    description: "橡胶弹头，伤害极低但击退极强，非致命镇压"
    damage_mult: 0.20
    penetration_bonus: -5
    spread_mult: 1.05
    recoil_mult: 0.50
    heat_mult: 0.50
    durability_mult: 0.50
    bullet_speed_mult: 0.80
    bullet_gravity_mult: 1.1
    bullet_drag_mult: 1.10
    effects:
      knockback: 3.0            # 击退倍数
      no_kill: true             # 不会致死（最低保留 1HP）
    item_material: "SLIME_BALL"
    item_custom_model_data: 10
    item_color: "&f"

  # ---------- 霰弹专用 ----------
  "buckshot":
    display_name: "鹿弹"
    lore: "§7标准霰弹"
    description: "多颗铅丸，近距离毁灭性"
    damage_mult: 1.0
    penetration_bonus: 0
    spread_mult: 1.0
    recoil_mult: 1.0
    heat_mult: 1.0
    durability_mult: 1.0
    bullet_speed_mult: 1.0
    bullet_gravity_mult: 1.0
    bullet_drag_mult: 1.02
    effects: {}
    item_material: "IRON_NUGGET"
    item_custom_model_data: 11
    item_color: "&7"

  "slug":
    display_name: "独头弹"
    lore: "§c高伤害 §7| §c高后坐"
    description: "单枚大弹头，远距离精确打击"
    damage_mult: 2.0
    penetration_bonus: +1
    spread_mult: 1.20
    recoil_mult: 1.50
    heat_mult: 1.10
    durability_mult: 1.05
    bullet_speed_mult: 0.90
    bullet_gravity_mult: 1.1
    bullet_drag_mult: 1.01
    effects: {}
    item_material: "IRON_INGOT"
    item_custom_model_data: 12
    item_color: "&4"

  "dragon_breath":
    display_name: "龙息弹"
    lore: "§6火焰喷射 §7| §6霰弹专用"
    description: "发射高温镁粉，前方扇形火焰"
    damage_mult: 0.80
    penetration_bonus: 0
    spread_mult: 1.0
    recoil_mult: 1.20
    heat_mult: 2.0
    durability_mult: 1.20
    bullet_speed_mult: 0.70
    bullet_gravity_mult: 1.0
    bullet_drag_mult: 1.03
    effects:
      ignite:
        chance: 100
        ticks: 80
      fire_damage: 4
      aoe_fire: true             # 命中点产生火焰区域
    item_material: "BLAZE_POWDER"
    item_custom_model_data: 13
    item_color: "&6"

  # ---------- 爆炸类 ----------
  "he":
    display_name: "高爆弹"
    lore: "§4爆炸 §7| §4AOE"
    description: "触发引信后爆炸，范围伤害"
    damage_mult: 1.0
    penetration_bonus: 0
    spread_mult: 1.0
    recoil_mult: 1.3
    heat_mult: 1.5
    durability_mult: 1.0
    bullet_speed_mult: 1.0
    bullet_gravity_mult: 1.0
    bullet_drag_mult: 1.0
    effects:
      explosion: true
    item_material: "TNT"
    item_custom_model_data: 14
    item_color: "&4"

  "heat":
    display_name: "破甲高爆弹"
    lore: "§4爆炸 §7| §b穿甲"
    description: "金属射流穿透装甲后爆炸"
    damage_mult: 1.15
    penetration_bonus: +4
    spread_mult: 1.0
    recoil_mult: 1.5
    heat_mult: 1.8
    durability_mult: 1.0
    bullet_speed_mult: 0.85
    bullet_gravity_mult: 1.2
    bullet_drag_mult: 1.0
    effects:
      explosion: true
      armor_ignore: 50
    item_material: "TNT"
    item_custom_model_data: 15
    item_color: "&5"

  "smoke":
    display_name: "烟雾弹"
    lore: "§8烟雾遮蔽"
    description: "命中后产生烟雾区域"
    damage_mult: 0.0
    penetration_bonus: 0
    spread_mult: 1.0
    recoil_mult: 1.0
    heat_mult: 1.0
    durability_mult: 1.0
    bullet_speed_mult: 1.0
    bullet_gravity_mult: 1.0
    bullet_drag_mult: 1.0
    effects:
      smoke: true
    item_material: "GRAY_DYE"
    item_custom_model_data: 16
    item_color: "&7"

  "flash":
    display_name: "闪光弹"
    lore: "§f致盲 §7| §f失聪"
    description: "爆炸产生强光，致盲范围内目标"
    damage_mult: 0.0
    penetration_bonus: 0
    spread_mult: 1.0
    recoil_mult: 1.0
    heat_mult: 1.0
    durability_mult: 1.0
    bullet_speed_mult: 1.0
    bullet_gravity_mult: 1.0
    bullet_drag_mult: 1.0
    effects:
      blind:
        radius: 8
        ticks: 80
    item_material: "GLOWSTONE_DUST"
    item_custom_model_data: 17
    item_color: "&f"

  "he_frag":
    display_name: "破片榴弹"
    lore: "§4大范围爆炸 §7| §b多枚破片"
    description: "爆炸产生多枚破片弹丸"
    damage_mult: 1.0
    penetration_bonus: 0
    spread_mult: 1.0
    recoil_mult: 1.3
    heat_mult: 1.5
    durability_mult: 1.0
    bullet_speed_mult: 1.0
    bullet_gravity_mult: 1.0
    bullet_drag_mult: 1.0
    effects:
      explosion: true
      frag_count: 12           # 爆炸后额外生成 12 枚破片
      frag_damage: 8
    item_material: "TNT"
    item_custom_model_data: 18
    item_color: "&4"

  # ---------- 能量类 ----------
  "standard":
    display_name: "标准能量电池"
    lore: "§d标准能量"
    description: "标准能量电池，性能均衡"
    damage_mult: 1.0
    penetration_bonus: 0
    spread_mult: 1.0
    recoil_mult: 1.0
    heat_mult: 1.0
    durability_mult: 1.0
    bullet_speed_mult: 1.0
    bullet_gravity_mult: 1.0
    bullet_drag_mult: 1.0
    effects: {}
    item_material: "REDSTONE"
    item_custom_model_data: 19
    item_color: "&d"

  "overcharged":
    display_name: "过载能量电池"
    lore: "§d高伤害 §7| §c高热量"
    description: "过载能量输出，伤害提升但易过热"
    damage_mult: 1.40
    penetration_bonus: +1
    spread_mult: 1.0
    recoil_mult: 1.0
    heat_mult: 1.80
    durability_mult: 1.10
    bullet_speed_mult: 1.0
    bullet_gravity_mult: 1.0
    bullet_drag_mult: 1.0
    effects: {}
    item_material: "GLOWSTONE_DUST"
    item_custom_model_data: 20
    item_color: "&5"

  "focused":
    display_name: "聚焦能量电池"
    lore: "§d高精度 §7| §b高穿透"
    description: "聚焦能量束，穿透力强精度高"
    damage_mult: 0.90
    penetration_bonus: +3
    spread_mult: 0.60
    recoil_mult: 1.0
    heat_mult: 1.20
    durability_mult: 1.0
    bullet_speed_mult: 1.0
    bullet_gravity_mult: 1.0
    bullet_drag_mult: 1.0
    effects: {}
    item_material: "AMETHYST_SHARD"
    item_custom_model_data: 21
    item_color: "&b"
```

### 5.3 弹药物品在游戏中的形态

```
弹药物品 = 实际 ItemStack，放入玩家背包
  ┌─────────────────────────────────┐
  │ PDC 标记:                        │
  │   ammo_caliber: "5.56mm"        │  ← 口径ID
  │   ammo_type: "ap"               │  ← 弹种ID
  │   ammo_item: true               │  ← 标记为弹药物品
  │                                 │
  │ ItemMeta:                        │
  │   displayName: "穿甲弹"          │
  │   lore:                          │
  │     "§7口径: §f5.56×45mm NATO"  │
  │     "§7弹种: §b穿甲弹"          │
  │     "§7伤害: §c×0.85"           │
  │     "§7穿透: §b+3"              │
  │   material: IRON_NUGGET          │
  │   customModelData: 4             │
  │   stack: 1~64 (由口径 stack_size 决定) │
  └─────────────────────────────────┘
```

### 5.4 枪械 ↔ 口径绑定

`gun.yml` 中每个枪械新增 `caliber` 和 `default_ammo` 字段：

```yaml
# gun.yml
items:
  IRON_HOE:                          # 手枪
    caliber: "9mm"
    default_ammo: "fmj"

  DIAMOND_HOE:                       # 步枪
    caliber: "5.56mm"
    default_ammo: "fmj"

  NETHERITE_HOE:                     # 狙击枪
    caliber: ".338lapua"
    default_ammo: "match"

  # 新增预设 — 霰弹枪
  WOODEN_HOE:
    gun_weapon_type: "shotgun"
    caliber: "12gauge"
    default_ammo: "buckshot"
    gun_shotgun_pellet_count: 10

  # 新增预设 — 冲锋枪
  STONE_HOE:
    caliber: "9mm"
    default_ammo: "fmj"
    gun_rpm: 900
    gun_fire_default_mode: 3        # 全自动
```

### 5.5 弹夹 ↔ 口径约束

弹夹系统从口径读取初始弹量（不再从 `gun_mag_capacity` 硬编码）：

```
换弹流程:
  player 持枪 (caliber = "5.56mm")
    ↓
  查找背包中 caliber = "5.56mm" 的弹药物品
    ↓
    找到 → 取弹种类型 → 消耗1个弹药物品 → 弹夹填满
    未找到 → ActionBar: "§c弹药不足 §7(需要 5.56mm)"
               → 无法换弹
```

弹夹容量仍由 `gun_mag_capacity` 设定（每个枪可以独立设置），但口径决定能装什么子弹。

### 5.6 弹种对枪械全部子系统的修正表

| 修正项 | 变量 | 作用域 | 说明 |
|------|------|------|------|
| 伤害 | `damage_mult` | RPG计算 | `finalDamage × damage_mult` |
| 穿透 | `penetration_bonus` | 穿透系统 | `penetrationCount += bonus`（可为负） |
| 散布 | `spread_mult` | 散布系统 | `spreadAngle × spread_mult` |
| 后坐 | `recoil_mult` | 后坐系统 | `vertical × recoil_mult`, `horizontal × recoil_mult` |
| 热量 | `heat_mult` | 过热系统 | `heat_per_shot × heat_mult` |
| 耐久 | `durability_mult` | 耐久系统 | `dura_loss_per_shot × durability_mult` |
| 弹速 | `bullet_speed_mult` | 弹道系统 | `bullet_speed × bullet_speed_mult` |
| 重力 | `bullet_gravity_mult` | 弹道系统 | `bullet_gravity × bullet_gravity_mult` |
| 阻力 | `bullet_drag_mult` | 弹道系统 | `bullet_drag × bullet_drag_mult` |

### 5.7 弹种特殊效果 (effects) 完整列表

| effect key | 参数 | 触发时机 | 说明 |
|------|------|------|------|
| `bleed` | `chance, damage, ticks` | 命中实体 | 目标流血 DoT (弩/空尖弹) |
| `ignite` | `chance, ticks` | 命中实体 | 点燃目标 (燃烧弹/曳光弹/龙息弹) |
| `explosion` | — | 命中实体/方块 | 产生爆炸 AoE (高爆弹/破甲弹/破片弹) |
| `frag_count` | `count, damage` | 爆炸时 | 额外生成破片弹丸 (破片榴弹) |
| `fire_damage` | `damage/tick` | 每 tick | 着火期间额外伤害 |
| `aoe_fire` | — | 命中点 | 落地产生火焰方块 (龙息弹) |
| `armor_ignore` | `percent` | 伤害计算 | 无视目标护甲值的百分比 |
| `knockback` | `multiplier` | 命中实体 | 击退倍数 (橡胶弹) |
| `no_kill` | — | 伤害结算 | 不会致死 (橡胶弹) |
| `silent` | — | 射击/命中 | 降低音效 (亚音速弹) |
| `trail` | `particle` | 飞行中 | 强制弹道尾迹 (曳光弹) |
| `smoke` | — | 命中点 | 产生烟雾区域 (烟雾弹) |
| `blind` | `radius, ticks` | 命中点 | 范围致盲 (闪光弹) |

### 5.8 弹药消耗逻辑

```
doShoot() 时:
  ┌─ 单发模式 → 弹夹 ammo -= 1
  ├─ 连发模式 → 弹夹 ammo -= burst_count (调度器每发各 -1)
  ├─ 全自动   → 弹夹 ammo -= 1 per shot
  └─ 霰弹     → 弹夹 ammo -= 1 (发射N颗弹丸但只耗1发)

弹夹 ammo == 0:
  ┌─ auto_reload = true  → 自动开始换弹
  └─ auto_reload = false → 阻止射击，等待手动换弹

换弹时:
  1. 读玩家背包中所有 ItemStack
  2. 过滤 caliber == 枪械.caliber 的弹药物品
  3. 取第一组 → 消耗 1 个 → 弹夹 ammo = gun_mag_capacity
     （枪膛启用时 +1）
  4. 若背包无匹配口径弹药 → 无法换弹

弹药优先级:
  1. 配件模块注入的弹药 (AmmoProvider.getAmmoType)
  2. 枪械 default_ammo 字段中设定的弹种
  3. 弹药物品自身的 ammo_type PDC 标记
```

### 5.9 弹药与插件全局联动

| 联动模块 | 联动方式 |
|------|------|
| **RPG 伤害** | `damage_mult` 直接乘到 `calcFinalDamage()` 结果 |
| **暴击** | 弹药不修改暴击概率，但暴击后伤害受 `damage_mult` 二次影响 |
| **吸血** | 吸血量 = 最终伤害 × 吸血率，弹药伤害修正已计入 |
| **破甲** | `armor_ignore` 效果在破甲计算前生效 |
| **致盲** | 闪光弹 `blind` 效果直接调用 BlindManager |
| **穿透** | `penetration_bonus` 加到 `gun_penetration_count` 上 |
| **弹道** | `bullet_speed/gravity/drag_mult` 实时修正 Arrow 物理 |
| **过热** | `heat_mult` 乘 `gun_heat_per_shot` |
| **故障** | 弹药不单独增加故障率（但热量/耐久修正会间接影响） |
| **耐久** | `durability_mult` 乘 `gun_dura_loss_per_shot` |
| **射击模式** | 不联动（弹药不影响模式） |
| **开镜** | 不联动 |
| **爆头** | 不联动（弹药不修改身体部位判定） |

### 5.10 弹药配置文件结构总览

```
plugins/XH/
├── gun.yml              ← 枪械基础属性 + caliber 绑定
├── ammo.yml             ← 口径定义 + 弹种定义 (新)
└── anvil.yml            ← 铁砧系统

枪械配置 (gun.yml):
  items:
    IRON_HOE:
      caliber: "9mm"           ← 绑定口径
      default_ammo: "fmj"      ← 默认弹种
      gun_mag_capacity: 15
      ...

口径定义 (ammo.yml):
  calibers:
    "9mm":
      available_types: [fmj, hp, ap, ...]
      default_type: "fmj"
      ...
    "5.56mm": ...
    "12gauge": ...

弹种定义 (ammo.yml):
  ammo_types:
    "fmj": { damage_mult: 1.0, penetration_bonus: 0, ... }
    "hp":  { damage_mult: 1.3, penetration_bonus: -2, ... }
    "ap":  { damage_mult: 0.85, penetration_bonus: +3, ... }
    ...
```

### 5.11 物品属性读取优先级

```
doShoot → 读取当前弹种的属性修正:

  1. AmmoProvider (配件模块) 注入的弹种
  2. 玩家背包中物品 PDC 的 ammo_type
  3. 枪械 default_ammo 配置
  4. 口径的 default_type

  → 得到弹种ID → 查 ammo.yml ammo_types → 得到修正系数
  → 各子系统 apply 修正
```

### 5.12 属性清单

| # | key | 位置 | 类型 | 说明 |
|---|-----|------|------|------|
| 1 | `ammo_system_enabled` | gun.yml systems | bool | 弹药系统全局开关 |
| 2 | `caliber` | gun.yml items.* | STRING | 枪械绑定的口径ID |
| 3 | `default_ammo` | gun.yml items.* | STRING | 枪械默认弹种ID |
| — | `calibers.*` (全部) | ammo.yml | — | 口径定义（约 14 个口径） |
| — | `ammo_types.*` (全部) | ammo.yml | — | 弹种定义（约 21 个弹种，每个 9 个修正系数 + effects） |


---

## 6. 耐久度系统 (Durability)

### 6.1 概念

枪械有耐久度，每发射击消耗。耐久越低，散布/后坐/故障率越差。耐久归零后枪械破损不可用。

### 6.2 属性清单

| # | key | 名称 | 类型 | 范围 | 默认 | 级别 | 说明 |
|---|-----|------|------|------|:--:|:--:|------|
| 1 | `gun_dura_max` | 最大耐久 | FLAT | 1~100000 | 3000 | 枪械 | 满耐久值 |
| 2 | `gun_dura_loss_per_shot` | 每发耐久损耗 | FLAT | 0~1000 | 1 | 枪械 | 炸膛额外损耗在故障系统中 |
| 3 | `gun_dura_spread_penalty` | 低耐久散布惩罚 | PERCENT | 0~200 | 30 | 枪械 | 耐久0%时额外散布倍率 = 1 + 此%/100 |
| 4 | `gun_dura_recoil_penalty` | 低耐久后坐惩罚 | PERCENT | 0~200 | 30 | 枪械 | 同上 |
| 5 | `gun_dura_malfunc_penalty` | 低耐久故障惩罚 | PERCENT | 0~200 | 50 | 枪械 | 耐久0%时额外故障率增量 |
| 6 | `gun_dura_broken_repairable` | 破损后可修复 | FLAT(0~1) | 0~1 | 1 | 枪械 | 1=耐久归零后可通过铁砧/磨石修复，0=永久报废 |
| 7 | `gun_dura_warning_threshold` | 耐久警告阈值 | PERCENT | 0~100 | 20 | 枪械 | 耐久低于此%时聊天栏警告 + ActionBar 红色耐久条 |
| 8 | `gun_dura_broken_spread_penalty` | 破损额外散布惩罚 | PERCENT | 0~500 | 100 | 枪械 | 耐久=0时的额外散布倍增（与低耐久惩罚叠加） |
| 9 | `durability_system_enabled` | 耐久系统开关 | — | — | true | **全局** | gun.yml → `systems.durability.enabled` |

### 6.3 耐久存入 PDC

耐久值存储在物品 PDC 中（`gun_durability_current`），确保掉地/死亡不丢失。

### 6.4 联动公式

```
耐久% = current / max

散布倍率 = 1 + (1 - 耐久%) × gun_dura_spread_penalty / 100
         + (耐久=0 ? gun_dura_broken_spread_penalty / 100 : 0)

后坐倍率 = 1 + (1 - 耐久%) × gun_dura_recoil_penalty / 100

故障率增量 = (1 - 耐久%) × gun_dura_malfunc_penalty / 100
```

### 6.5 铁砧修复系统（重构现有 anvil 模块）

#### 6.5.1 现有铁砧系统分析

当前 `anvil/` 模块结构：

| 文件 | 职责 |
|------|------|
| `AnvilConfig.java` | 加载 `anvil.yml`，读 `fixed-exp-cost` / `max-repair-cost` |
| `AnvilListener.java` | 监听 `PrepareAnvilEvent`，强制经验成本固定值；处理附魔合并 |
| `AnvilEnchantMerger.java` | 附魔冲突检测、合并逻辑、互斥组 |
| `AnvilPacketListener.java` | ProtocolLib 修复"过于昂贵"显示 |

当前系统的局限性：
1. 只能处理**附魔合并**和**原版物品修复**，不能识别枪械 PDC 耐久
2. 经验成本全局固定，不够灵活（不同枪/不同材料修复成本应不同）
3. 物品命名与枪械耐久修复未区分

#### 6.5.2 重构方案

```
anvil/
├── AnvilConfig.java              # 重构：增加修复材料、成本公式配置
├── AnvilListener.java            # 重构：增加枪械耐久修复分支
├── AnvilEnchantMerger.java       # 保持
├── AnvilPacketListener.java      # 保持
└── AnvilRepairManager.java       # 新增：枪械修复计算引擎
```

#### 6.5.3 anvil.yml 扩展配置

```yaml
# ========== 原版铁砧 ==========
fixed-exp-cost: 30
max-repair-cost: 30

# ========== 枪械修复系统 ==========
gun-repair:
  enabled: true
  # 修复模式: "material"(用材料修复), "exp"(纯经验修复), "both"(两者皆可)
  mode: "both"

  # 经验成本公式: "flat"(固定值) / "formula"(按耐久%计算)
  exp-cost-mode: "formula"
  # flat 模式下的固定成本
  exp-cost-flat: 15
  # formula 模式的参数: cost = base + (1 - dura%) × per_percent
  exp-cost-base: 5
  exp-cost-per-percent: 20
  # 最大经验成本上限
  exp-cost-max: 40

  # 修复材料映射
  repair-materials:
    # 手枪 (IRON_HOE) → 用铁锭修复
    IRON_HOE:
      material: "IRON_INGOT"
      per-item: 25        # 每个材料恢复 25% 耐久
      name: "手枪零件"
    # 步枪 (DIAMOND_HOE) → 用钻石修复
    DIAMOND_HOE:
      material: "DIAMOND"
      per-item: 15        # 每个材料恢复 15% 耐久
      name: "步枪零件"
    # 狙击枪 (NETHERITE_HOE) → 用下界合金锭修复
    NETHERITE_HOE:
      material: "NETHERITE_INGOT"
      per-item: 10        # 每个材料恢复 10% 耐久
      name: "狙击枪零件"

  # 默认修复材料（枪型未配置时使用）
  default-material:
    material: "IRON_INGOT"
    per-item: 20
    name: "枪械零件"

  # 完美修复：用原品材料+额外经验 → 恢复 100% 耐久
  perfect-repair:
    enabled: true
    material: "DIAMOND"      # 第二格放的材料
    exp-cost: 35
```

#### 6.5.4 AnvilRepairManager 新增

```java
/**
 * 枪械铁砧修复管理器
 *
 * 流程：
 * 1. PrepareAnvilEvent → 检测第一格是否为枪械
 * 2. 如果是枪械 + 第二格是修复材料 → 计算修复量
 * 3. 计算经验成本
 * 4. 克隆结果物品并设置新耐久
 */
public class AnvilRepairManager {

    /** 计算修复结果 */
    static RepairResult calcRepair(ItemStack gunItem, ItemStack materialItem, AnvilConfig config);

    record RepairResult(ItemStack result, double newDura, double prevDura, int expCost, String materialName) {}
}
```

#### 6.5.5 铁砧流程重构

```
PrepareAnvilEvent 触发:

  Item0 = 第一格, Item1 = 第二格

  【分支 A：附魔书/同物品合并 → 现有逻辑】
    if (Item1 是附魔书 || Item1 同类型可合并附魔)
      → handleEnchantMerge()   // 现有逻辑不变
      → 经验成本 = fixed-exp-cost

  【分支 B：枪械修复 → 新增】
    if (Item0 是枪械 && Item1 是修复材料)
      → AnvilRepairManager.calcRepair()
      → 设置结果物品（保留原物品所有属性+PDS，更新耐久）
      → 经验成本按 formula/flat 计算
      → 消耗材料（第二格数量 − 1）

  【分支 C：原版修复 → 原版处理】
    if (Item0 原版工具/护甲 && Item1 同材料)
      → 原版逻辑处理
      → 经验成本 = fixed-exp-cost

  【分支 D：命名 → 新增区分】
    if (仅修改名称)
      → 经验成本 = 1（命名成本低）
```

#### 6.5.6 修复后的 PDC 更新

```
修复完成 → 事件取出结果物品:
  1. 读 PDC gun_durability_current
  2. 新耐久 = min(current + 修复量, gun_dura_max)
  3. 写回 PDC
  4. 结果物品的 Lore 更新耐久条显示
```

#### 6.5.7 ActionBar / Lore 显示

枪械物品 Lore 示例（自动生成）：
```
§7耐久: §a|||||||||||||||||| §f1800/3000  §7(60%)
§7修复材料: §f铁锭 §7(每个 +25%)
```

破损枪械 Lore：
```
§7耐久: §c|||||||| §f0/3000  §c(已损坏)
§c需要铁砧修复
```

---

## 7. 穿透系统 (Penetration)

### 7.1 概念

弹射物命中实体后不消失，继续飞行并命中后续实体。每次穿透后伤害衰减。

### 7.2 属性清单

| # | key | 名称 | 类型 | 范围 | 默认 | 级别 | 说明 |
|---|-----|------|------|------|:--:|:--:|------|
| 1 | `gun_penetration_count` | 穿透层数 | FLAT | 0~20 | 0 | 枪械 | 最多穿透几个实体，0=不穿透 |
| 2 | `gun_penetration_falloff` | 穿透伤害衰减 | PERCENT | 0~100 | 30 | 枪械 | 每穿透一层伤害× (1-此%)；线性还是指数由 mode 决定 |
| 3 | `gun_penetration_falloff_mode` | 衰减模式 | FLAT(0~1) | 0~1 | 0 | 枪械 | 0=线性(每层-固定%)，1=指数(每层×(1-%)，伤害越来越小) |
| 4 | `gun_penetration_min_damage` | 穿透最低伤害 | PERCENT | 0~100 | 10 | 枪械 | 穿透后伤害不低于原始伤害×此%（防止无限衰减变 0） |
| 5 | `gun_penetration_block_break` | 可破坏方块 | FLAT(0~1) | 0~1 | 0 | 枪械 | 1=穿透弹可击穿薄墙（玻璃、栅栏等），后续配件扩展 |
| 6 | `gun_penetration_particle` | 穿透粒子 | STRING | — | "crit" | 枪械 | 穿透实体时播放的粒子效果 |
| 7 | `gun_penetration_sound` | 穿透音效 | STRING | — | "" | 枪械 | |
| 8 | `penetration_system_enabled` | 穿透系统开关 | — | — | true | **全局** | gun.yml → `systems.penetration.enabled` |

### 7.3 实现细节

- 在 `RpgCombatListener.handleProjectile` 中：判定穿透后不取消 Arrow，而是修改伤害 + 标记穿透计数到 Arrow PDC
- Arrow 命中事件 → 减穿透计数 → 计数>0 → 不取消实体 → 伤害×衰减
- 穿透链：`目标1(damage 100%) → 目标2(70%) → 目标3(49%) → 目标4(34%，若低于min则用min)`

---

## 8. 弹道系统 (Ballistics)

### 8.1 概念

子弹有飞行速度、重力下坠、空气阻力、存在时间。子弹速度越快越容易命中移动目标。

### 8.2 属性清单

| # | key | 名称 | 类型 | 范围 | 默认 | 级别 | 说明 |
|---|-----|------|------|------|:--:|:--:|------|
| 1 | `gun_bullet_speed` | 子弹初速 | FLAT(m/s) | 1~500 | 60 | 枪械 | 发射瞬间的速度，映射到 Arrow velocity = speed/20 |
| 2 | `gun_bullet_gravity` | 重力系数 | FLAT | 0~2 | 0 | 枪械 | 0=无重力（激光枪），1=正常重力(9.8m/s²)，2=重弹 |
| 3 | `gun_bullet_lifetime_ticks` | 存活时间 | FLAT(tick) | 1~600 | 100 | 枪械 | 超时自动 remove，防止 Arrow 永久漂移 |
| 4 | `gun_bullet_trail` | 弹道尾迹 | STRING | — | "" | 枪械 | 粒子尾迹效果，空=无，"smoke"/"flame"/"crit"/"end_rod" |
| 5 | `gun_bullet_trail_interval` | 尾迹间隔 | FLAT(tick) | 1~20 | 2 | 枪械 | 每 N tick 生成一次尾迹粒子 |
| 6 | `gun_bullet_drag` | 空气阻力系数 | FLAT | 0~0.5 | 0 | 枪械 | 每 tick 速度衰减比例，0=无阻力(太空枪)，0.01=轻微 |
| 7 | `gun_bullet_damage_falloff_start` | 伤害衰减起始距离 | FLAT(block) | 0~500 | 0 | 枪械 | 超过此距离开始衰减，0=不衰减 |
| 8 | `gun_bullet_damage_falloff_end` | 伤害衰减结束距离 | FLAT(block) | 0~500 | 0 | 枪械 | 超过此距离伤害降到最低 |
| 9 | `gun_bullet_damage_min_percent` | 最低伤害比例 | PERCENT | 0~100 | 50 | 枪械 | 远程衰减后不低于此比例 |
| 10 | `gun_bullet_hitscan` | 即时命中 | FLAT(0~1) | 0~1 | 0 | 枪械 | 1=射线检测(sniper)，0=实体弹(Arrow) |
| 11 | `ballistic_system_enabled` | 弹道系统开关 | — | — | true | **全局** | gun.yml → `systems.ballistics.enabled` |

### 8.3 子弹飞行物理模型

#### 8.3.1 发射瞬间

```
发射时:
  baseDirection = player.getEyeLocation().getDirection()   // 单位向量
  arrow.setVelocity(baseDirection × bullet_speed / 20)
  // 注：Minecraft velocity 单位是 block/tick
  //     1 m/s = 1 block/s = 1/20 block/tick
  //     所以 velocity = speed(m/s) ÷ 20
```

#### 8.3.2 每 tick 空气阻力衰减

```
每 tick (50ms):
  velocity(t+1) = velocity(t) × (1 − drag)

例: drag=0.01, bullet_speed=100 m/s
  t=0:  v = 5.0  block/tick  (100m/s ÷ 20)
  t=1:  v = 4.95
  t=2:  v = 4.9005
  t=10: v = 4.52
  t=50: v = 3.03  (约 60 m/s)
  t=100: v = 1.83  (约 36 m/s)

阻力对弹道的影响:
  - 子弹速度持续降低
  - 重力效应越来越明显（同样时间内下降更多）
  - 远程命中移动目标更困难
  - 弹道曲线逐渐弯曲下坠
```

#### 8.3.3 重力下坠

```
每 tick:
  arrow.setGravity(bullet_gravity > 0)   // 启用/禁用重力

Minecraft 默认重力加速度 ≈ 0.04 block/tick² (≈ 16 m/s²，比真实 9.8 略大)

实际实现：
  - bullet_gravity = 0 → arrow.setGravity(false), 直线飞行
  - bullet_gravity = 1 → arrow.setGravity(true), 标准重力
  - bullet_gravity = 2 → arrow.setGravity(true) + 每 tick 额外施加向下的 velocity 修正

重弹(bullet_gravity=2)的额外修正:
  if (bullet_gravity > 1 && arrow.isGravity()) {
      // 每 tick 额外施加向下加速度
      arrow.setVelocity(arrow.getVelocity().add(
          new Vector(0, -0.04 * (bullet_gravity - 1), 0)
      ));
  }
```

#### 8.3.4 弹道轨迹可视化

```
    速度方向 + 散布偏移
         ╱
        ╱  ← drag 逐渐减速
       ╱
      ╱    ← gravity 开始下坠
     ╱
    ╱      ← 远距离明显下坠
   ╱
  ╱_____________ 地面
  ↑
  枪口位置
```

#### 8.3.5 伤害距离衰减——完整模型

```
飞行距离 = ∥弹射物当前位置 − 发射位置∥ (block)

if (飞行距离 ≤ falloff_start):
    damageMultiplier = 1.0

elif (飞行距离 ≤ falloff_end):
    t = (飞行距离 − falloff_start) / (falloff_end − falloff_start)
    // t ∈ [0, 1]
    damageMultiplier = 1.0 − t × (1.0 − min_percent / 100)

else (飞行距离 > falloff_end):
    damageMultiplier = min_percent / 100

finalDamage = originalDamage × damageMultiplier
```

**典型配法示例**：

| 枪型 | start | end | min% | 效果 |
|------|:--:|:--:|:--:|------|
| 手枪 | 10 | 30 | 40 | 10格内满伤，30格外降到40% |
| 步枪 | 30 | 80 | 60 | 30格内满伤，80格外降到60% |
| 狙击枪 | 50 | 150 | 80 | 50格内满伤，150格外降到80%（远射特化） |
| 霰弹枪 | 3 | 10 | 20 | 3格外急剧衰减（近距离武器） |

#### 8.3.6 综合弹道公式（伤害衰减 × 阻力速度衰减）

```
最终命中伤害 = RPG计算伤害
             × 距离衰减系数(location)
             × 速度衰减系数(velocity/muzzleVelocity)
             × 爆头/部位系数

其中速度衰减(可选，默认不启用):
  speedRatio = arrow.getVelocity().length() / (bullet_speed / 20)
  velocityPenalty = clamp(1.0 − (1 − speedRatio) × 0.5, 0.7, 1.0)
  // 速度降到一半时伤害×0.85，最低×0.7
  // 此功能由 gun_bullet_speed_damage_factor 控制(默认 0 关闭)
```

#### 8.3.7 即时命中 Hitscan 模式

```
当 gun_bullet_hitscan = 1 时:
  - 不生成 Arrow 实体
  - 直接射线检测: player.rayTraceBlocks(maxDistance) + rayTraceEntities
  - 第一个命中的实体受到伤害
  - 伤害穿透按穿透规则
  - 没有飞行时间，瞬间命中（适合狙击/激光枪）
  - 散布仍然生效（射线方向经 SpreadCalculator 偏移）

hitscan 与实体弹的区别:
  实体弹(Arrow):   可观测弹道轨迹 / 有时间延迟 / 可被方块阻挡 / 重力下坠
  hitscan(射线):   瞬间命中 / 无弹道 / 无法被中间插入的实体拦截 / 无重力
```

#### 8.3.8 弹道尾迹实现

```
在 GunTickTask(每5 tick)中遍历所有活跃枪械弹射物:

  if (arrow PDC 有 gun_is_gun):
      trail = arrow PDC 读取 gun_bullet_trail
      if (trail 非空):
          在 arrow.getLocation() 生成粒子:
            "smoke"    → Particle.SMOKE × 1
            "flame"    → Particle.FLAME × 1
            "crit"     → Particle.CRIT × 1
            "end_rod"  → Particle.END_ROD × 1
            "spell"    → Particle.SPELL_WITCH × 1

  trail_interval: 每 N tick 生成一次（默认每 2 tick ≈ 每 0.1s）
```

### 8.4 弹道配置预设示例

```yaml
# 手枪: 中速, 轻微衰减
IRON_HOE:
  gun_bullet_speed: 80
  gun_bullet_gravity: 0.3
  gun_bullet_drag: 0.002
  gun_bullet_damage_falloff_start: 10
  gun_bullet_damage_falloff_end: 35
  gun_bullet_damage_min_percent: 40
  gun_bullet_trail: "smoke"
  gun_bullet_trail_interval: 3

# 步枪: 高速, 远程衰减小
DIAMOND_HOE:
  gun_bullet_speed: 200
  gun_bullet_gravity: 0.1
  gun_bullet_drag: 0.0005
  gun_bullet_damage_falloff_start: 30
  gun_bullet_damage_falloff_end: 100
  gun_bullet_damage_min_percent: 60
  gun_bullet_trail: ""
  gun_bullet_trail_interval: 2

# 狙击枪: 超高速, hitscan, 极远衰减
NETHERITE_HOE:
  gun_bullet_speed: 400
  gun_bullet_gravity: 0.05
  gun_bullet_drag: 0
  gun_bullet_damage_falloff_start: 50
  gun_bullet_damage_falloff_end: 200
  gun_bullet_damage_min_percent: 80
  gun_bullet_trail: "crit"
  gun_bullet_trail_interval: 1

# 霰弹枪(后续): 低速, 重弹, 极近距离衰减
SHOTGUN:
  gun_bullet_speed: 50
  gun_bullet_gravity: 1.0
  gun_bullet_drag: 0.05
  gun_bullet_damage_falloff_start: 2
  gun_bullet_damage_falloff_end: 8
  gun_bullet_damage_min_percent: 15
  gun_bullet_trail: "smoke"
  gun_bullet_trail_interval: 2
```

---

## 9. 特殊武器类型

### 9.0 总概念

枪械不只是步枪/手枪——通过 `gun_weapon_type` 属性切换武器的**发射行为**。

```
gun_weapon_type: "normal" | "shotgun" | "crossbow" | "flamethrower"
                | "grenade_launcher" | "rocket_launcher" | "laser"

默认: "normal"（标准步枪/手枪行为）
```

不同武器类型**复用**已有属性体系（散布/后坐/过热/故障等），同时**覆盖/新增**发射逻辑。类型切换由属性的 `gun_weapon_type` 字符串决定，不改变属性存储方式。

---

### 9.1 标准型 (normal)

行为不变，当前所有已实现逻辑。

| gun_weapon_type | 已有属性体系 | 新增属性 |
|:--:|:--|:--|
| `"normal"` | 全部适用 | 无 |

---

### 9.2 霰弹枪 (shotgun)

#### 9.2.1 概念

每次射击发射 N 颗弹丸，每颗独立计算散布。近距离毁天灭地，远程刮痧。

#### 9.2.2 弹丸发射模型

```
射击时:
  for (i = 0; i < pellet_count; i++):
    pelletDir = SpreadCalculator.applySpread(player, weapon, baseDir)
    // 注意：每颗弹丸独立 roll 散布，不是共享一个散布角
    launchArrow(pelletDir × bullet_speed / 20)
  
  每颗弹丸:
    - 独立 PDC 标记（gun_is_gun）
    - 独立伤害 = 总伤害 / pellet_count
    - 独立爆头判定
    - 独立穿透判定
```

#### 9.2.3 散布特点

霰弹枪的散布与其他枪不同：`gun_spread_min` 设定的是**弹丸群的整体散布角**。每颗弹丸在散布角内均匀分布或按模式分布。

```
弹丸分布模式 (gun_shotgun_spread_mode):
  0 = 均匀圆 (每颗随机角度, 随机方向)
  1 = 均匀圆 + 中心偏重 (中心 1 颗 + 剩余均匀分布)
  2 = 环形 (弹丸均匀分布在圆周上)
  3 = 水平线 (弹丸呈水平排列, 适合横拉)
```

#### 9.2.4 属性清单

| # | key | 名称 | 类型 | 范围 | 默认 | 级别 | 说明 |
|---|-----|------|------|------|:--:|:--:|------|
| 1 | `gun_weapon_type` | 武器类型 | STRING | — | "normal" | 枪械 | ← 设为 "shotgun" |
| 2 | `gun_shotgun_pellet_count` | 弹丸数量 | FLAT | 1~30 | 8 | 枪械 | 每发射击发射的弹丸数 |
| 3 | `gun_shotgun_spread_mode` | 弹丸分布模式 | FLAT(0~3) | 0~3 | 0 | 枪械 | 0=均匀圆 1=中心偏重 2=环形 3=水平线 |
| 4 | `gun_shotgun_damage_divider` | 伤害分配模式 | FLAT(0~1) | 0~1 | 0 | 枪械 | 0=每颗=总伤/pellet(默认), 1=每颗=总伤(变态模式) |
| 5 | `gun_shotgun_pellet_speed` | 弹丸速度 | FLAT(m/s) | 1~200 | 40 | 枪械 | 覆写 bullet_speed，单独设置弹丸速度 |

#### 9.2.5 与现有系统的联动

| 系统 | 联动方式 |
|------|------|
| 散布 | `gun_spread_*` 设定弹丸群的整体散布角，每颗在圆锥内独立 roll |
| 后坐 | 仅施加一次（不是 N 次），但 `recoil_vertical × sqrt(pellet_count)/2` 略微增强 |
| 爆头 | 每颗弹丸独立爆头判定 |
| 穿透 | 每颗弹丸独立穿透 |
| 弹夹 | 每次射击消耗 1 发（不是 N 发），弹夹容量通常小 |
| 过热 | 每次射击热量 += heat_per_shot × sqrt(pellet_count)/2 |
| 故障 | 故障判定仅滚一次（不会出现"第3颗弹丸触发卡壳"这种怪事） |

---

### 9.3 弩 (crossbow)

#### 9.3.1 概念

用弩发射弩箭。与枪械不同：单发蓄力或即时，弹道重度下坠，高单发伤害。适合远程狙杀。

#### 9.3.2 发射模型

```
射击时:
  - 发射 Arrow（自带弩箭视觉效果）
  - 弩箭速度较慢，重力 = 1.0
  - 弩箭伤害独立于 gun_damage，由 gun_crossbow_damage 控制
  - 弩箭不受枪械散布系统的随机偏移（精准武器）
  - 弩箭命中后可有特殊效果
```

#### 9.3.3 属性清单

| # | key | 名称 | 类型 | 范围 | 默认 | 级别 | 说明 |
|---|-----|------|------|------|:--:|:--:|------|
| 1 | `gun_weapon_type` | 武器类型 | STRING | — | "normal" | 枪械 | ← 设为 "crossbow" |
| 2 | `gun_crossbow_damage` | 弩箭伤害 | FLAT | 0~∞ | 40 | 枪械 | 覆写 gun_damage，弩箭独立伤害 |
| 3 | `gun_crossbow_reload_ticks` | 装填时间 | FLAT(tick) | 1~200 | 30 | 枪械 | 发射后的装填时间（比换弹快） |
| 4 | `gun_crossbow_gravity` | 弩箭重力 | FLAT | 0~2 | 1.5 | 枪械 | 弩箭比子弹重得多，明显下坠 |
| 5 | `gun_crossbow_bleed_chance` | 流血概率 | PERCENT | 0~100 | 60 | 枪械 | 命中后目标流血的概率 |
| 6 | `gun_crossbow_bleed_damage` | 流血伤害 | FLAT | 0~100 | 5 | 枪械 | 每次流血 tick 的伤害值 |
| 7 | `gun_crossbow_bleed_ticks` | 流血持续时间 | FLAT(tick) | 0~200 | 60 | 枪械 | 总共 3s 持续流血 |
| 8 | `gun_crossbow_headshot_mult` | 弩爆头倍率 | PERCENT | 0~∞ | 300 | 枪械 | 覆写 gun_headshot_mult，弩爆头更致命 |

#### 9.3.4 与现有系统的联动

| 系统 | 联动方式 |
|------|------|
| 散布 | 散布角 ≈ 0（弩是精准武器），但移动/跳跃仍略微影响 |
| 后坐 | 轻微后坐（比枪小得多）或不启用后坐 |
| 射击模式 | 通常仅单发和安全，`gun_fire_available_modes` 限制 |
| 弹夹 | 单发装填 → `gun_mag_capacity = 1`，装填时间由 `gun_crossbow_reload_ticks` 控制 |
| 开镜 | 开镜可触发高倍镜 + 屏息稳定效果 |
| 爆头 | 独立爆头倍率（弩爆头通常更高） |
| 穿透 | 弩箭可穿透 N 层（模拟穿透力） |

---

### 9.4 喷火器 (flamethrower)

#### 9.4.1 概念

全自动武器，不发射弹丸，而是在前方产生火焰粒子 + 持续伤害判定。没有散布和后坐概念。消耗燃料而非弹药。

#### 9.4.2 发射模型

```
全自动模式下每 tick:
  - 在玩家前方 N 格范围内创建火焰粒子
  - 对范围内的实体施加火伤（频率: 每 N tick 一次）
  - 伤害为持续伤害（非爆发）
  - 燃料消耗

燃料系统:
  fuel_max = gun_flame_fuel_max
  fuel_per_tick = gun_flame_fuel_per_tick
  燃料耗尽 → 火焰停止
  停止射击 → 燃料缓慢恢复
```

#### 9.4.3 伤害判定方式

```
每 server tick 判定:
  取玩家视线方向前方 2~5 格
  创建扇形检测区域:
    角度 = gun_flame_spread_angle (默认 20°)
    距离 = gun_flame_range (默认 5 block)
  
  区域内所有 LivingEntity:
    applyFireDamage(player, weapon, target, flameDamagePerTick)
    target.setFireTicks(gun_flame_ignite_ticks)
```

#### 9.4.4 属性清单

| # | key | 名称 | 类型 | 范围 | 默认 | 级别 | 说明 |
|---|-----|------|------|------|:--:|:--:|------|
| 1 | `gun_weapon_type` | 武器类型 | STRING | — | "normal" | 枪械 | ← 设为 "flamethrower" |
| 2 | `gun_flame_damage_per_tick` | 火焰灼伤/tick | FLAT | 0~100 | 3 | 枪械 | 每一判定tick 造成的伤害 |
| 3 | `gun_flame_damage_interval` | 伤害判定间隔 | FLAT(tick) | 1~20 | 4 | 枪械 | 每 N tick 判定一次（不要每 tick 都判） |
| 4 | `gun_flame_range` | 火焰射程 | FLAT(block) | 1~15 | 5 | 枪械 | 火焰有效伤害距离 |
| 5 | `gun_flame_spread_angle` | 火焰扩散角 | FLAT(度) | 5~60 | 20 | 枪械 | 扇形检测角度 |
| 6 | `gun_flame_ignite_ticks` | 点燃时间 | FLAT(tick) | 0~200 | 40 | 枪械 | 目标着火持续 tick 数 |
| 7 | `gun_flame_fuel_max` | 燃料上限 | FLAT | 1~10000 | 1000 | 枪械 | |
| 8 | `gun_flame_fuel_per_tick` | 燃料消耗/tick | FLAT | 1~100 | 5 | 枪械 | 每 tick 燃料值消耗 |
| 9 | `gun_flame_fuel_regen` | 燃料恢复/s | FLAT | 0~500 | 50 | 枪械 | 停火时每秒恢复 |
| 10 | `gun_flame_particle_density` | 粒子密度 | FLAT | 1~10 | 3 | 枪械 | 每判定生成粒子数 |

#### 9.4.5 与现有系统的联动

| 系统 | 联动方式 |
|------|------|
| 散布 | **不适用**（喷火器无弹道散布） |
| 后坐 | **不适用**（喷火器无后坐） |
| 弹夹 | **替换为燃料系统** |
| 射击模式 | 通常仅全自动（`gun_fire_available_modes = 8`，bit3） |
| 过热 | **天然联动**：喷火器燃料=热量，过热=必须停火冷却 |
| 故障 | 燃料品质影响故障率（劣质燃料 = 更高哑火率） |
| 耐久 | 燃料喷嘴磨损 = 每 tick 损耗 |
| 爆头 | **不适用**（AOE 伤害不判定爆头部位） |
| 弹道 | **不适用**（无弹丸） |
| 穿透 | **不适用** |

---

### 9.5 榴弹发射器 (grenade_launcher)

#### 9.5.1 概念

发射抛物线爆炸弹。弹丸受重力影响，命中后产生爆炸 AoE 伤害。弹匣小，装填慢。

#### 9.5.2 发射模型

```
射击时:
  发射实体的 Arrow（带重力），速度较低
  Arrow 命中时:
    → 取消 Arrow
    → 在命中位置创建爆炸:
        - explosionPower = gun_grenade_explosion_power
        - 爆炸范围内所有实体受到伤害
        - 伤害随距离衰减
        - 不破坏方块（除非配置允许）
        - 播放爆炸粒子 + 音效
```

#### 9.5.3 伤害距离衰减（爆炸内部）

```
实体距离爆炸中心 d 格:

  伤害 = grenade_damage × (1 − d/explosion_radius) × damage_falloff
  
  d = 0 → 100% 伤害
  d = radius/2 → 50% 伤害
  d = radius → 0% 伤害（边界无伤）
```

#### 9.5.4 属性清单

| # | key | 名称 | 类型 | 范围 | 默认 | 级别 | 说明 |
|---|-----|------|------|------|:--:|:--:|------|
| 1 | `gun_weapon_type` | 武器类型 | STRING | — | "normal" | 枪械 | ← 设为 "grenade_launcher" |
| 2 | `gun_grenade_damage` | 爆炸中心伤害 | FLAT | 0~∞ | 80 | 枪械 | 覆写 gun_damage，爆炸正中心伤害 |
| 3 | `gun_grenade_radius` | 爆炸半径 | FLAT(block) | 1~20 | 5 | 枪械 | 伤害有效范围 |
| 4 | `gun_grenade_fuse_ticks` | 引信时间 | FLAT(tick) | 0~200 | 60 | 枪械 | 命中后延迟爆炸(0=撞击即爆)，-1=撞击引爆 |
| 5 | `gun_grenade_bounce` | 弹跳次数 | FLAT | 0~5 | 0 | 枪械 | 撞击方块后弹跳次数（0=粘性榴弹） |
| 6 | `gun_grenade_destroy_blocks` | 破坏方块 | FLAT(0~1) | 0~1 | 0 | 枪械 | 1=爆炸破坏方块（谨慎使用） |
| 7 | `gun_grenade_knockback` | 击退倍数 | FLAT | 0~5 | 1.5 | 枪械 | 爆炸击退强度倍数 |
| 8 | `gun_grenade_self_damage_factor` | 自伤系数 | PERCENT | 0~100 | 80 | 枪械 | 自身在爆炸范围内受伤害的比例 |

#### 9.5.5 与现有系统的联动

| 系统 | 联动方式 |
|------|------|
| 散布 | 适用：影响榴弹出膛角度的随机偏移 |
| 后坐 | 适用：榴弹发射器后坐很强 |
| 弹夹 | 适用：通常 1~6 发容量 |
| 射击模式 | 通常仅单发（bit1） |
| 弹道 | 重力必须开启（bullet_gravity ≥ 1），速度较慢 |
| 过热 | 适用 |
| 故障 | 适用（榴弹卡壳极为危险） |
| 爆头 | **不适用**（AoE 伤害不区分部位） |
| 穿透 | **不适用**（爆炸 AoE 穿透无意义） |

---

### 9.6 火箭筒 (rocket_launcher)

#### 9.6.1 概念

发射慢速导弹，追击目标或直射。爆炸半径大，伤害极高。弹夹通常 1 发，装填极慢。导弹飞行时间较长。

#### 9.6.2 导弹行为

```
【模式 0: 直线飞行】(默认)
  - 发射后直线飞行，撞击实体/方块 → 爆炸
  - 不受重力影响
  - 速度: bullet_speed

【模式 1: 跟踪导弹】(gun_rocket_homing = 1)
  - 发射后寻找最近的敌对实体
  - 每 tick 微调方向指向目标
  - 追踪强度由 homing_strength 控制

【模式 2: 遥控引爆】(gun_rocket_remote = 1)
  - 发射后导弹正常飞行
  - 再次左键 → 引爆（空中爆炸）
```

#### 9.6.3 属性清单

| # | key | 名称 | 类型 | 范围 | 默认 | 级别 | 说明 |
|---|-----|------|------|------|:--:|:--:|------|
| 1 | `gun_weapon_type` | 武器类型 | STRING | — | "normal" | 枪械 | ← 设为 "rocket_launcher" |
| 2 | `gun_rocket_damage` | 爆炸中心伤害 | FLAT | 0~∞ | 150 | 枪械 | 覆写 gun_damage |
| 3 | `gun_rocket_radius` | 爆炸半径 | FLAT(block) | 1~30 | 8 | 枪械 | |
| 4 | `gun_rocket_velocity` | 导弹速度 | FLAT(m/s) | 5~100 | 30 | 枪械 | 覆写 bullet_speed，导弹比子弹慢 |
| 5 | `gun_rocket_homing` | 追踪导弹 | FLAT(0~1) | 0~1 | 0 | 枪械 | 1=追踪最近敌对实体 |
| 6 | `gun_rocket_homing_strength` | 追踪强度 | PERCENT | 0~100 | 30 | 枪械 | 每 tick 方向修正系数，过高会过度摆动 |
| 7 | `gun_rocket_homing_range` | 追踪范围 | FLAT(block) | 5~100 | 40 | 枪械 | 超出此距离停止追踪 |
| 8 | `gun_rocket_remote` | 遥控引爆 | FLAT(0~1) | 0~1 | 0 | 枪械 | 1=左键引爆已发射导弹 |
| 9 | `gun_rocket_self_damage_factor` | 自伤系数 | PERCENT | 0~100 | 100 | 枪械 | 火箭筒自伤通常 100%（真实）或可设为 0（无敌模式） |
| 10 | `gun_rocket_destroy_blocks` | 破坏方块 | FLAT(0~1) | 0~1 | 0 | 枪械 | 谨慎：1=炸毁地形 |

#### 9.6.4 与现有系统的联动

| 系统 | 联动 |
|------|------|
| 散布 | 适用：导弹出膛偏移 |
| 后坐 | 极强后坐（rocket_launcher 后坐 × 2~3） |
| 弹夹 | 通常 capacity = 1，装填 = 80~120 ticks |
| 射击模式 | 仅单发（bit1） |
| 弹道 | **覆写**：导弹使用独立速度、不受 drag/gun_bullet_speed 控制 |
| 爆头 | **不适用** |
| 穿透 | **不适用** |

---

### 9.7 激光枪 / 轨道炮 (laser)

#### 9.7.1 概念

即时命中（hitscan），无弹道飞行时间。通过射线检测瞬间判定命中。精度极高，无重力，无散布（或极小散布）。通常有过热冷却机制。

#### 9.7.2 发射模型

```
射击时:
  - 不生成 Arrow 实体
  - 直接 player.rayTraceEntities(laser_range)
  - 第一个命中的实体受到伤害
  - 生成一条激光粒子线（枪口 → 命中点）
  - 无散布（方向完全准星）或微小散布

持续激光模式 (gun_laser_continuous = 1):
  - 按住时每 N tick 发射一次射线
  - 每次消耗能量
```

#### 9.7.3 属性清单

| # | key | 名称 | 类型 | 范围 | 默认 | 级别 | 说明 |
|---|-----|------|------|------|:--:|:--:|------|
| 1 | `gun_weapon_type` | 武器类型 | STRING | — | "normal" | 枪械 | ← 设为 "laser" |
| 2 | `gun_laser_damage` | 激光伤害 | FLAT | 0~∞ | 25 | 枪械 | 覆写 gun_damage |
| 3 | `gun_laser_range` | 激光射程 | FLAT(block) | 5~200 | 60 | 枪械 | 射线最大检测距离 |
| 4 | `gun_laser_continuous` | 持续激光 | FLAT(0~1) | 0~1 | 0 | 枪械 | 1=按住持续发射(光束), 0=点射 |
| 5 | `gun_laser_energy_max` | 能量上限 | FLAT | 1~10000 | 1000 | 枪械 | 类似燃料 |
| 6 | `gun_laser_energy_per_shot` | 单发能耗 | FLAT | 1~500 | 30 | 枪械 | |
| 7 | `gun_laser_energy_regen` | 能量恢复/s | FLAT | 0~500 | 40 | 枪械 | |
| 8 | `gun_laser_color` | 激光颜色 | STRING | — | "red" | 枪械 | 粒子颜色: red/green/blue/yellow/purple/cyan/white |
| 9 | `gun_laser_thickness` | 激光粗细 | FLAT | 1~5 | 1 | 枪械 | 粒子线条粗细 |
| 10 | `gun_laser_pierce` | 激光穿透 | FLAT | 0~10 | 0 | 枪械 | 穿透实体数(覆写 penetration_count) |

#### 9.7.4 与现有系统的联动

| 系统 | 联动 |
|------|------|
| 散布 | 极小(0°~0.5°)，或无散布 |
| 后坐 | 无传统后坐，但有能量消耗反馈 |
| 弹夹 | **替换为能量系统** |
| 射击模式 | 点射(单发) 或 持续(全自动) |
| 过热 | 天然联动：连续射击 → 能量枯竭 + 过热 → 强制冷却 |
| 故障 | 能量模块故障 → 暂时无法射击 |
| 爆头 | 适用（射线判定落点即头部） |
| 穿透 | 适用：`gun_laser_pierce` 覆写 |
| 弹道 | **不适用**（hitscan，无飞行时间） |
| 部位 | **适用**（射线落点可判定爆头） |

---

### 9.8 特殊武器类型属性汇总

| # | key | 类型 | 归属 | 说明 |
|---|-----|------|:--:|------|
| 1 | `gun_weapon_type` | STRING | 全类型 | 武器类型枚举 |
| 2 | `gun_shotgun_pellet_count` | FLAT | 霰弹 | 弹丸数 |
| 3 | `gun_shotgun_spread_mode` | FLAT(0~3) | 霰弹 | 弹丸分布模式 |
| 4 | `gun_shotgun_damage_divider` | FLAT(0~1) | 霰弹 | 伤害分配 |
| 5 | `gun_shotgun_pellet_speed` | FLAT(m/s) | 霰弹 | 弹丸速度 |
| 6 | `gun_crossbow_damage` | FLAT | 弩 | 弩箭伤害 |
| 7 | `gun_crossbow_reload_ticks` | FLAT(tick) | 弩 | 装填时间 |
| 8 | `gun_crossbow_gravity` | FLAT | 弩 | 弩箭重力 |
| 9 | `gun_crossbow_bleed_chance` | PERCENT | 弩 | 流血概率 |
| 10 | `gun_crossbow_bleed_damage` | FLAT | 弩 | 流血伤害/tick |
| 11 | `gun_crossbow_bleed_ticks` | FLAT(tick) | 弩 | 流血持续 tick |
| 12 | `gun_crossbow_headshot_mult` | PERCENT | 弩 | 弩爆头倍率(覆写) |
| 13 | `gun_flame_damage_per_tick` | FLAT | 喷火器 | 灼伤/tick |
| 14 | `gun_flame_damage_interval` | FLAT(tick) | 喷火器 | 判定间隔 |
| 15 | `gun_flame_range` | FLAT(block) | 喷火器 | 火焰射程 |
| 16 | `gun_flame_spread_angle` | FLAT(度) | 喷火器 | 扇形角度 |
| 17 | `gun_flame_ignite_ticks` | FLAT(tick) | 喷火器 | 点燃持续 |
| 18 | `gun_flame_fuel_max` | FLAT | 喷火器 | 燃料上限 |
| 19 | `gun_flame_fuel_per_tick` | FLAT | 喷火器 | 燃料消耗/tick |
| 20 | `gun_flame_fuel_regen` | FLAT/s | 喷火器 | 燃料恢复 |
| 21 | `gun_flame_particle_density` | FLAT | 喷火器 | 粒子密度 |
| 22 | `gun_grenade_damage` | FLAT | 榴弹 | 爆炸伤害 |
| 23 | `gun_grenade_radius` | FLAT(block) | 榴弹 | 爆炸半径 |
| 24 | `gun_grenade_fuse_ticks` | FLAT(tick) | 榴弹 | 引信时间 |
| 25 | `gun_grenade_bounce` | FLAT | 榴弹 | 弹跳次数 |
| 26 | `gun_grenade_destroy_blocks` | FLAT(0~1) | 榴弹 | 破坏方块 |
| 27 | `gun_grenade_knockback` | FLAT | 榴弹 | 击退倍数 |
| 28 | `gun_grenade_self_damage_factor` | PERCENT | 榴弹 | 自伤系数 |
| 29 | `gun_rocket_damage` | FLAT | 火箭筒 | 爆炸伤害 |
| 30 | `gun_rocket_radius` | FLAT(block) | 火箭筒 | 爆炸半径 |
| 31 | `gun_rocket_velocity` | FLAT(m/s) | 火箭筒 | 导弹速度 |
| 32 | `gun_rocket_homing` | FLAT(0~1) | 火箭筒 | 追踪 |
| 33 | `gun_rocket_homing_strength` | PERCENT | 火箭筒 | 追踪强度 |
| 34 | `gun_rocket_homing_range` | FLAT(block) | 火箭筒 | 追踪范围 |
| 35 | `gun_rocket_remote` | FLAT(0~1) | 火箭筒 | 遥控引爆 |
| 36 | `gun_rocket_self_damage_factor` | PERCENT | 火箭筒 | 自伤系数 |
| 37 | `gun_rocket_destroy_blocks` | FLAT(0~1) | 火箭筒 | 破坏方块 |
| 38 | `gun_laser_damage` | FLAT | 激光 | 激光伤害 |
| 39 | `gun_laser_range` | FLAT(block) | 激光 | 激光射程 |
| 40 | `gun_laser_continuous` | FLAT(0~1) | 激光 | 持续激光 |
| 41 | `gun_laser_energy_max` | FLAT | 激光 | 能量上限 |
| 42 | `gun_laser_energy_per_shot` | FLAT | 激光 | 单发能耗 |
| 43 | `gun_laser_energy_regen` | FLAT/s | 激光 | 能量恢复 |
| 44 | `gun_laser_color` | STRING | 激光 | 激光颜色 |
| 45 | `gun_laser_thickness` | FLAT | 激光 | 激光粗细 |
| 46 | `gun_laser_pierce` | FLAT | 激光 | 穿透数 |

**新增属性总计：46 个**（1 公共 + 4 霰弹 + 7 弩 + 9 喷火器 + 7 榴弹 + 9 火箭筒 + 9 激光）

---

### 9.9 各武器类型与通用系统的兼容矩阵

```
                    散布  后坐  弹夹  射击模式  过热  故障  耐久  爆头  穿透  弹道
normal              ✅   ✅   ✅      ✅      ✅   ✅    ✅    ✅    ✅    ✅
shotgun             ✅   ✅   ✅      ✅      ✅   ✅    ✅    ✅    ✅    ✅
crossbow            △¹  △²   ✅      ✅      ❌   ✅    ✅    ✅    ✅    ✅
flamethrower        ❌   ❌   🔄³     🔄⁴     🔄⁵  ✅    ✅    ❌    ❌    ❌
grenade_launcher    ✅   ✅   ✅      ✅      ✅   ✅    ✅    ❌    ❌    ✅
rocket_launcher     ✅   ✅   ✅      ✅      ✅   ✅    ✅    ❌    ❌    🔄⁶
laser               △⁷   ❌   🔄⁸     ✅      🔄⁹  ✅    ✅    ✅    🔄¹⁰   ❌

✅ = 完全复用
△ = 数值调低但不等于零
❌ = 不适用
🔄 = 替换为专用系统
¹ 散布极小(趋近0)
² 后坐极小或为零
³ 弹夹 → 燃料系统
⁴ 仅全自动
⁵ 天然联动（燃料=热量）
⁶ 导弹独立速度
⁷ 散布极小(趋近0)
⁸ 弹夹 → 能量系统
⁹ 能量枯竭→强制冷却
¹⁰ laser_pierce 覆写
```

---

## 10. 全局开关总览

gun.yml 顶层新增 `systems` 节：

```yaml
systems:
  overheat:
    enabled: true
  malfunction:
    enabled: true
    global_base_chance: 0
  magazine:
    enabled: true
    auto_reload: true
    reload_key: "drop"
  chamber:
    enabled: true
  ammo:
    enabled: false
  durability:
    enabled: true
  penetration:
    enabled: true
  ballistics:
    enabled: true
```

当对应系统 `enabled: false` 时：
- 相关 manager 的 tick/事件逻辑跳过
- 相关属性即使配置也不生效
- doShoot 流程中对应步骤跳过

---

## 11. 扩展接口总览

当前已有的扩展点：

| 接口 | 用途 | 调用时机 |
|------|------|------|
| `GunAttributeProvider` | 配件/符文/附魔修改任意属性 | `AttributeStorage.getItemAttrRange()` 链 |
| `ScopeProvider` | 倍镜修改放大倍率 | `AdsManager.toggle()` 时 |
| `AmmoProvider` | 弹药类型 + 伤害修改 | 射击时读取弹药类型 |
| `DamageCalculator` | 额外伤害计算流程 | `AttributeCalculator.calcFinalDamage()` |

---

## 12. 完整射击流水线

```
持枪右键
  │
  ├─ [全局开关] 各系统 enabled 检查
  │
  ├─ 1. [弹夹] 弹量检查
  │    ├─ 弹量=0 → 触发换弹 / 空仓音效 → return
  │    └─ 弹量>0 → 继续
  │
  ├─ 2. [过热] 热量检查
  │    ├─ 过热状态 → 禁止射击 → return
  │    └─ 正常 → 继续
  │
  ├─ 3. [故障] 故障检查
  │    ├─ 卡壳中 → 禁止射击(须左键排除) → return
  │    ├─ roll 触发故障 → 按类型处理 → return
  │    └─ 正常 → 继续
  │
  ├─ 4. [射击模式] 模式判定
  │    ├─ 安全 → return
  │    ├─ 单发 → 发射 1 发
  │    ├─ 连发 → 启动连发调度
  │    └─ 全自动 → Toggle 或持续
  │
  ├─ 5. [开镜] 散布/后坐 ADS 修正已实时生效
  │
  ├─ 6. [散布] SpreadCalculator.applySpread()
  │    └─ 散布角 ← SpreadManager.consumeSpread()
  │         ├─ min/max 钳制
  │         ├─ 首发精度
  │         ├─ 移动/跳跃/蹲下修正
  │         ├─ ADS 修正
  │         └─ 热量散布系数 (过热联动)
  │
  ├─ 7. [弹道] 构建 Arrow
  │    ├─ velocity = bullet_speed / 20
  │    ├─ gravity = bullet_gravity
  │    ├─ lifetime
  │    └─ 尾迹粒子
  │
  ├─ 8. [后坐] RecoilManager.applyRecoil()
  │    └─ 旋转 pitch/yaw
  │         ├─ 首发减免
  │         ├─ 模式(直线/锯齿/S/倒T)
  │         ├─ 蹲下/ADS 修正
  │         └─ 热量后坐系数 (过热联动)
  │
  ├─ 9. [热量] heat += per_shot
  │    └─ 检查是否过热
  │
  ├─ 10. [耐久] dura -= loss_per_shot
  │     └─ 检查是否破损
  │
  ├─ 11. [弹夹] ammo -= 1
  │     └─ 存入 PDC
  │
  └─ 12. [枪膛] chamber = 0 (auto_bolt=1 时自动上膛恢复 chamber=1)
       └─ auto_bolt=0 → 需左键拉栓

────────── Arrow 命中目标 ──────────

  ├─ [穿透] 穿透计数 > 0
  │    └─ Arrow 不消失 → 衰减伤害 → 继续飞行
  │
  ├─ [爆头/部位] applyHitzone (RpgCombatListener)
  ├─ [RPG计算] calcFinalDamage (暴击/吸血)
  ├─ [弹药] AmmoProvider 伤害修正
  ├─ [破甲]
  └─ [致盲]
```

---

## 属性统计

| 系统 | 枪械属性 | 全局属性 | 独立配置文件 | 小计 |
|------|:--:|:--:|:--:|:--:|
| 已完成 (6系统) | 61 | 0 | — | 61 |
| 过热 | 9 | 1 | — | 10 |
| 故障 | 7 | 2 | — | 9 |
| 弹夹 | 6 | 3 | — | 9 |
| 枪膛 | 4 | 1 | — | 5 |
| 弹药 | 2 | 1 | **ammo.yml** (14口径 + 21弹种) | 3 |
| 耐久 | 8 | 1 | — | 9 |
| 穿透 | 7 | 1 | — | 8 |
| 弹道 | 10 | 1 | — | 11 |
| 特殊武器类型 | 46 | 0 | — | 46 |
| **总计** | **160** | **11** | **ammo.yml 独立** | **171** |

> 弹药系统的 14 个口径和 21 个弹种定义在独立的 `ammo.yml` 中，不占用 `RpgAttribute` 枚举。
> 每个弹种包含 9 个修正系数 + effects 特殊效果，通过配置驱动而非硬编码。
> gun.yml 仅新增 `caliber` 和 `default_ammo` 两个字段。
