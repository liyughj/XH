# XH

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-blue.svg)](https://www.minecraft.net)
[![Platform](https://img.shields.io/badge/Platform-Paper-purple.svg)](https://papermc.io)

XH 是一个面向 Minecraft Paper 1.21.4 的全功能 RPG 枪械插件，提供真实的弹道模拟、完整的 RPG 属性系统、可升级的附魔机制以及高度可定制的物品描述。

## 特性概览

### 枪械系统
- **物理弹道** — 射线追踪 / 弹丸飞行，支持重力、空气阻力、水中弹速、伤害距离衰减
- **散布系统** — 最小/最大扩散、移动/跳跃/蹲下/开镜惩罚、首发精度、射击象限、散布模式
- **后坐力系统** — 垂直/水平后坐、连射增长、硬上限、视角震动、首发减免、多种后坐模式
- **射击模式** — 安全/单发/连发/全自动，可在游戏中 F 键切换
- **过热系统** — 单发热量、过热阈值、冷却速率、过热惩罚、散布/后坐/故障因子
- **故障系统** — 卡壳/哑火/炸膛，受热量与耐久影响，排障交互
- **弹夹与枪膛** — 弹夹容量、战术换弹、空仓换弹、分段换弹、膛内单发、手动/自动拉栓
- **耐久度系统** — 射击损耗、破损惩罚、低耐久散布/后坐/故障惩罚、可修复开关
- **穿透系统** — 实体穿透、方块击穿、伤害衰减模式、最低保留伤害
- **部位伤害** — 爆头/上肢/下身/腿部独立概率与倍率、阈值判定
- **特殊弹道** — 跳弹（入射角判定）、玻璃穿透

### 特殊武器
| 类型 | 特性 |
|------|------|
| 霰弹枪 | 多弹丸、散布模式、独立弹丸速度 |
| 弩 | 重力控制、爆头倍率覆盖、装填冷却 |
| 榴弹发射器 | 爆炸半径、引信时间、弹跳、自伤系数、方块破坏 |
| 火箭筒 | 追踪导弹、遥控引爆、爆炸范围、自伤系数 |
| 喷火器 | 持续伤害、燃料系统、粒子密度、点燃控制 |
| 激光枪 | 持续/脉冲模式、能量系统、颜色与粗细、穿透开关 |

### 人体工学与机动性
- 切枪/收枪延迟、疾跑开火延迟、开镜渐入渐出
- 持枪移速/疾跑/跳跃高度修正、开镜移速独立控制
- 开镜屏息系统（消耗/恢复/阈值）、瞄具类型（机瞄至热成像）、开镜夜视

### 命中特效
- 减速、硬直（击退）、致盲 — 独立概率与参数
- 击杀连锁 — 击杀触发换弹加速/伤害加成/生命回复，持续 buff

### RPG 属性系统
所有 RPG 属性均可与枪械叠加使用：
- **伤害** — `damage` / `damage_bonus` + 武器专属（`gun_damage` / `gun_bonus`）
- **暴击** — 概率 + 倍率 + 两种模式（加法/乘法）
- **吸血** — 偷取（百分比）/ 固定吸血 / 汲取（额外伤害）
- **穿透** — 低穿（受韧性抵抗）/ 高穿（无视韧性、超额转伤）
- **破甲** — 浅/中/深三级 debuff，持续降低护甲值
- **流血** — 独立概率/伤害/tick
- **闪避/致盲** — 对方命中率修正
- **攻击速度** — 百分比修正原版攻速（A+C 方案）

### 附魔系统
- **可升级附魔** — 原版附魔升级路线，经验积累→自动升级
- **自定义效果** — 完全替代原版附魔效果（锋利/亡灵杀手/火焰附加/击退/横扫等）
- **特殊效果** — 引雷伤害自定义、穿透计算、致密独立公式
- **铁砧增强** — 固定经验成本、"过于昂贵"数据包修复
- **附魔台限制** — 书架检测、附魔等级强制 I 级、物品类型白名单

### 弹药系统
- 口径体系 — 多口径 + 口径内多弹种
- 弹药效果 — 伤害倍率、护甲穿透、消音
- 弹匣物品 — 独立物品类型，可 GUI 管理

### 其他
- **Lore 模板系统** — YAML 可配置，枪械/武器/附魔/魔法/修仙多模块
- **GUI 系统** — 弹匣管理界面、配件界面（预留）
- **调试模式** — `/xh debug` 输出全部枪械属性区间与 RPG 判定过程
- **名称修复** — `/xh restore` 还原铁砧改名

## 快速开始

### 环境要求
- **Paper** 1.21.4+
- **Java** 21+
- **ProtocolLib**（必需依赖）

### 安装
1. 下载最新 `XH.jar`
2. 放入 `plugins/` 目录
3. 确保已安装 ProtocolLib
4. 重启服务器

### 配置
插件首次加载后会在 `plugins/XH/` 下生成完整配置文件，包括：
- `gun.yml` — 枪械与弹匣属性
- `ammo.yml` — 弹药口径与弹种
- `lore.yml` — Lore 显示模板
- `enchantmentLevel.yml` — 附魔升级规则
- `Level.yml` — 附魔效果强度
- `specialEffects.yml` — 升级特效
- `enchantingTable.yml` — 附魔台配置
- `anvil.yml` — 铁砧配置
- `rpg_items.yml` — RPG 属性全局设定

## 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/xh give gun <ID>` | 获取枪械 | `xh.admin` |
| `/xh give ammo <口径> <弹种> [数量]` | 获取弹药 | `xh.admin` |
| `/xh give mag <ID>` | 获取弹匣 | `xh.admin` |
| `/xh reload` | 重载所有配置 | `xh.admin` |
| `/xh debug` | 切换调试模式 | `xh.admin` |
| `/xh restore` | 修复手中被改名的 RPG 武器 | `xh.admin` |
| `/xh lore` | 切换附魔显示模式 | `xh.admin` |
| `/xh help` | 查看帮助 | `xh.use` |

## 开发

```bash
git clone https://github.com/liyughj/XH.git
cd XH
mvn clean package
```

### 构建
Java 21 + Maven，构建输出在 `target/XH-0.0.1.jar`。

### 代码结构

```
xH/
├── anvil/                  # 铁砧增强（成本控制、数据包修复）
├── command/                # /xh 命令分发
├── debug/                  # 调试模式 + 名称修复工具
├── enchantingTable/        # 附魔台限制
├── enchantmentLevel/       # 附魔升级系统 + 自定义效果
├── gun/                    # 枪械核心
│   ├── GUI/                #   弹匣/配件 GUI
│   ├── GunsSystemConfig     #   配置加载
│   ├── GunListener          #   事件入口（射击/换弹/开镜）
│   ├── RayTraceManager      #   射线命中（弹道+穿透+伤害管线）
│   ├── SpreadManager        #   散布系统
│   ├── RecoilManager        #   后坐力系统
│   ├── MagazineManager      #   弹夹管理
│   ├── ChamberManager       #   枪膛系统
│   ├── OverheatManager      #   过热系统
│   ├── MalfunctionManager   #   故障系统
│   ├── DurabilityManager    #   耐久度系统
│   ├── SpecialWeapons       #   特殊武器（霰弹/弩/榴弹/火箭/喷火/激光）
│   ├── BallisticsManager    #   弹道实体管理
│   ├── AdsManager           #   开镜系统
│   ├── EquipManager         #   切枪/收枪延迟
│   ├── MobilityManager      #   机动性（移速/跳跃修正）
│   ├── FireModeManager      #   射击模式切换
│   ├── SuppressionManager   #   压制系统
│   └── AmmoConfig           #   弹药配置
├── lore/                   # Lore 模板渲染
└── rpg/Attribute/          # RPG 属性核心
    ├── RpgAttribute         #   属性枚举（100+ 属性）
    ├── AttributeCalculator  #   伤害计算（暴击/吸血/穿透）
    ├── AttributeStorage     #   PDC 持久化存储
    ├── RpgCombatListener    #   战斗事件管线
    └── ArmorBreakManager    #   破甲系统
```

## 许可证

本项目使用 [MIT License](LICENSE)。

Copyright (c) 2026 liyughj
