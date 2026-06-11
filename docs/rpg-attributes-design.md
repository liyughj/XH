# XH 插件 RPG 属性设计文档

---

## 数值类型

| 类型 | 标识 | 说明 |
|------|------|------|
| FLAT | 绝对值 | 直接加减，如 +5 点伤害、+20 血量 |
| PERCENT | 百分比 | 百分比加成，计算时 /100，如 50% 表示 ×0.5 |

## 区间支持

所有属性值支持 **单值** 或 **区间（min~max）**，每次触发时在区间内随机取值。

```
单值:      melee_damage: 20            → 每次都是 20
区间:      melee_damage: 20~40         → 每次随机 20~40
百分比单值: melee_bonus: 80%           → 每次都是 80%
百分比区间: melee_bonus: 80%~120%      → 每次随机 80%~120%
```

---

# 一、攻击属性

## 1.1 基础伤害（6 个） — ✅ 已实现

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `melee_damage` | 近战伤害 | FLAT | ∞ | 仅近战生效（剑/斧/锤/三叉戟近战） |
| `melee_bonus` | 近战加成 | PERCENT | ∞ | 乘 melee_damage |
| `projectile_damage` | 射弹伤害 | FLAT | ∞ | 仅远程生效（弓/弩/三叉戟投掷） |
| `projectile_bonus` | 射弹加成 | PERCENT | ∞ | 乘 projectile_damage |
| `damage` | 伤害 | FLAT | ∞ | 同时加到近战+远程 |
| `damage_bonus` | 伤害加成 | PERCENT | ∞ | 乘 damage |

**公式：**
```
近战 = melee_damage×(1 + melee_bonus/100) + damage×(1 + damage_bonus/100)
远程 = projectile_damage×(1 + projectile_bonus/100) + damage×(1 + damage_bonus/100)
```

---

## 1.2 暴击（3 个）

| key | 名称 | 类型 | 上限 | 状态 | 说明 |
|-----|------|------|:--:|:--:|------|
| `critical_chance` | 暴击率 | PERCENT | 100% | ✅已实现 | 触发暴击几率 |
| `critical_multiplier` | 暴击倍率 | PERCENT | ∞ | ✅已实现 | 与全局 crit-default 叠加 |
| `critical_correction` | 暴击修正 | PERCENT | ±∞ | ❌未实现 | 特殊效果修正暴击触发条件 |

**公式：**
```
ADD 模式:     最终暴击率 = crit-default + critical_multiplier
MULTIPLY 模式: 最终暴击率 = crit-default + crit-default × critical_multiplier
暴击伤害 = 基础伤害 × (1 + 最终暴击率/100)
```

---

## 1.3 吸血（4 个） — ✅ 已实现

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `lifesteal_chance` | 吸血概率 | PERCENT | 100% | 偷取/固定吸血/汲取三机制共用 |
| `lifesteal_multiplier` | 吸血倍率 | PERCENT | ∞ | 影响偷取效率+汲取回血倍率 |
| `lifesteal_flat` | 固定吸血 | FLAT | ∞ | 恢复固定血量（恢复机制） |
| `lifesteal_drain` | 汲取 | FLAT | ∞ | 额外伤害+回血（偷取机制） |

**三机制各自独立 roll：**

| 机制 | 类型 | 回血量 |
|------|------|--------|
| 偷取 | 恢复 | `最终伤害 × 偷取率%` |
| 固定吸血 | 恢复 | `roll(lifesteal_flat)` |
| 汲取 | 偷取 | `min(roll(drain), 目标剩余血量) × (1+倍率%)` |

---

## 1.4 穿透（4 个） — ✅ 已实现

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `low_penetration` | 低穿 | PERCENT | 100% | 无视目标 X% 护甲减免 |
| `high_penetration` | 高穿 | FLAT | ∞ | 忽略 X 点护甲韧性，超额转伤害 |
| `penetration_efficiency` | 穿透效能 | PERCENT | ∞ | 同时增幅低穿和高穿 |
| `armor_toughness` | 护甲韧性 | PERCENT | 100% | 免疫低穿（材质默认值+物品属性） |

**公式：**
```
最终低穿 = low_penetration × (1 + efficiency/100)
最终高穿 = high_penetration × (1 + efficiency/100)
有效低穿 = max(0, 最终低穿 - 防御方总韧性)
高穿超额 = max(0, 最终高穿 - 防御方总韧性) × 残余护甲比例
```

---

## 1.5 攻速（1 个） — ✅ 已实现

| key | 名称 | 类型 | 范围 | 说明 |
|-----|------|------|:--:|------|
| `attack_speed` | 攻击速度 | PERCENT | -80%~500% | 基值 4.0 次/秒 |

```
实际攻速 = 4.0 × (1 + attack_speed/100)
CD 秒数 = 1 / 实际攻速
未到 CD: 伤害 × max(0.2, 实际间隔/CD)
```

---

## 1.6 元素伤害（待实现）

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `fire_damage` | 火焰伤害 | FLAT | ∞ | 额外火焰伤害，附带燃烧 tick |
| `ice_damage` | 冰霜伤害 | FLAT | ∞ | 额外伤害+减速效果 |
| `lightning_damage` | 雷电伤害 | FLAT | ∞ | 额外伤害+雷击粒子特效 |
| `poison_damage` | 毒伤害 | FLAT | ∞ | 持续中毒伤害 |
| `wither_damage` | 凋零伤害 | FLAT | ∞ | 持续凋零伤害 |
| `holy_damage` | 神圣伤害 | FLAT | ∞ | 对亡灵生物额外倍率 |
| `true_damage` | 真实伤害 | FLAT | ∞ | 无视一切减免的固定伤害 |
| `fire_chance` | 火焰概率 | PERCENT | 100% | 触发火焰伤害的概率 |
| `ice_chance` | 冰霜概率 | PERCENT | 100% | 触发冰霜伤害的概率 |
| `lightning_chance` | 雷电概率 | PERCENT | 100% | 触发雷电伤害的概率 |
| `element_amplifier` | 元素增幅 | PERCENT | ∞ | 增幅所有元素伤害 |

---

## 1.7 连击/机制（待实现）

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `combo_chance` | 连击概率 | PERCENT | 100% | X% 概率伤害翻倍 |
| `rampage_stack` | 嗜血层数 | FLAT | ∞ | 连续命中 N 次增伤 |
| `rampage_damage_pct` | 嗜血增伤 | PERCENT | ∞ | 嗜血每层增伤百分比 |
| `execute_threshold` | 斩杀阈值 | PERCENT | 100% | 目标血量低于 X% 触发 |
| `execute_multiplier` | 斩杀倍率 | PERCENT | ∞ | 斩杀时伤害倍率 |
| `backstab_pct` | 背刺加成 | PERCENT | ∞ | 背后攻击额外伤害比例 |
| `ricochet_count` | 弹射数量 | FLAT | ∞ | 命中后弹射目标数 |
| `ricochet_range` | 弹射范围 | FLAT | ∞ | 弹射搜索范围（格） |
| `ricochet_damage_pct` | 弹射伤害 | PERCENT | 100% | 弹射伤害比例 |
| `splash_range` | 溅射范围 | FLAT | ∞ | 溅射伤害半径（格） |
| `splash_damage_pct` | 溅射伤害 | PERCENT | 100% | 溅射伤害比例 |

---

## 1.8 目标减益（待实现）

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `armor_break_chance` | 破甲概率 | PERCENT | 100% | 触发破甲 |
| `armor_break_pct` | 破甲量 | PERCENT | 100% | 降低目标防御% |
| `armor_break_ticks` | 破甲持续 | FLAT | ∞ | 破甲持续时间(tick) |-*/*/
| `blind_chance` | 致盲概率 | PERCENT | 100% | 触发致盲 |
| `blind_ticks` | 致盲持续 | FLAT | ∞ | 致盲持续时间(tick) |
| `slow_chance` | 减速概率 | PERCENT | 100% | 触发减速 |
| `slow_level` | 减速等级 | FLAT | ∞ | 药水效果等级 |
| `slow_ticks` | 减速持续 | FLAT | ∞ | 减速持续时间(tick) |
| `levitate_chance` | 浮空概率 | PERCENT | 100% | 触发浮空 |
| `levitate_ticks` | 浮空持续 | FLAT | ∞ | 浮空持续时间(tick) |
| `mark_chance` | 标记概率 | PERCENT | 100% | 标记目标 |
| `mark_damage_pct` | 标记增伤 | PERCENT | ∞ | 后续攻击增伤比例 |
| `mark_ticks` | 标记持续 | FLAT | ∞ | 标记持续时间(tick) |

---

## 1.9 自身增益（待实现）

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `bloodthirst_speed_pct` | 嗜血攻速 | PERCENT | ∞ | 连续命中后攻速加成 |
| `adrenaline_threshold` | 肾上腺阈值 | PERCENT | 100% | 血量低于 X% 触发 |
| `adrenaline_damage_pct` | 肾上腺增伤 | PERCENT | ∞ | 触发后增伤比例 |
| `adrenaline_speed_pct` | 肾上腺攻速 | PERCENT | ∞ | 触发后攻速加成 |
| `kill_streak_damage_pct` | 击杀增伤 | PERCENT | ∞ | 击杀后 N 秒内增伤 |
| `kill_streak_ticks` | 击杀持续 | FLAT | ∞ | 增伤持续时间(tick) |

---

## 1.10 特殊机制（待实现）

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `attack_range` | 攻击距离 | FLAT | ∞ | 近战攻击距离加成(格) |
| `knockback_pct` | 击退强度 | PERCENT | ∞ | 击退距离倍率 |
| `suck_range` | 吸怪距离 | FLAT | ∞ | 将目标拉向自己的距离(格) |
| `shield_bypass_chance` | 盾破概率 | PERCENT | 100% | 无视盾牌格挡的概率 |
| `lifesteal_cap` | 吸血上限 | FLAT | ∞ | 单次吸血最大回复量 |
| `projectile_count` | 多重射击 | FLAT | ∞ | 同时射出 N 支箭 |
| `projectile_speed_pct` | 弹射速度 | PERCENT | ∞ | 弹射物飞行速度倍率 |
| `sweep_damage_pct` | 横扫伤害 | PERCENT | ∞ | 横扫攻击伤害比例 |

---

# 二、生存属性

## 2.1 基础生存（待实现）

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `health_bonus` | 生命加成 | FLAT | ∞ | 增加最大生命值 |
| `defense` | 防御 | PERCENT | 100% | 最终伤害减免百分比 |
| `health_regen` | 生命回复 | FLAT | ∞ | 每秒回复生命值 |
| `health_regen_pct` | 生命回复率 | PERCENT | ∞ | 每秒回复最大生命百分比 |
| `absorption` | 吸收护盾 | FLAT | ∞ | 脱战后生成的临时护盾 |
| `absorption_regen_ticks` | 护盾恢复 | FLAT | ∞ | 脱战多少 tick 后恢复护盾 |

---

## 2.2 伤害减免（待实现）

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `fire_resistance` | 火焰抗性 | PERCENT | 100% | 减免火焰伤害% |
| `ice_resistance` | 冰霜抗性 | PERCENT | 100% | 减免冰霜伤害% |
| `lightning_resistance` | 雷电抗性 | PERCENT | 100% | 减免雷电伤害% |
| `poison_resistance` | 毒抗性 | PERCENT | 100% | 减免毒伤害% |
| `wither_resistance` | 凋零抗性 | PERCENT | 100% | 减免凋零伤害% |
| `projectile_resistance` | 射弹抗性 | PERCENT | 100% | 减免弹射物伤害% |
| `explosion_resistance` | 爆炸抗性 | PERCENT | 100% | 减免爆炸伤害% |
| `fall_resistance` | 摔落抗性 | PERCENT | 100% | 减免摔落伤害% |
| `magic_resistance` | 魔法抗性 | PERCENT | 100% | 减免魔法伤害% |
| `true_damage_resistance` | 真伤抗性 | PERCENT | 100% | 减免真实伤害% |

---

## 2.3 闪避/格挡（待实现）

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `dodge` | 闪避率 | PERCENT | 75% | 完全闪避攻击的概率 |
| `block_chance` | 格挡率 | PERCENT | 100% | 手持武器时自动格挡概率 |
| `block_damage_pct` | 格挡减免 | PERCENT | 100% | 格挡成功时减免伤害的比例 |
| `parry_chance` | 招架率 | PERCENT | 100% | 招架成功反弹伤害给攻击者 |
| `parry_damage_pct` | 招架伤害 | PERCENT | ∞ | 招架反弹伤害比例 |

---

## 2.4 状态硬控防护（待实现）

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `tenacity` | 韧性 | PERCENT | 100% | 减免被控制时间 |
| `knockback_resistance` | 击退抗性 | PERCENT | 100% | 减免击退距离 |
| `stun_resistance` | 眩晕抗性 | PERCENT | 100% | 免疫眩晕概率 |
| `blind_resistance` | 致盲抗性 | PERCENT | 100% | 免疫致盲概率 |
| `slow_resistance` | 减速抗性 | PERCENT | 100% | 免疫减速概率 |
| `suck_resistance` | 吸怪抗性 | PERCENT | 100% | 免疫吸怪概率 |
| `levitate_resistance` | 浮空抗性 | PERCENT | 100% | 免疫浮空概率 |

---

## 2.5 反伤/承伤转移（待实现）

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `thorns_damage_pct` | 反伤比例 | PERCENT | ∞ | 受击反弹 X% 伤害 |
| `thorns_range` | 反伤范围 | FLAT | ∞ | 远程反伤有效距离 |
| `damage_share_pct` | 伤害分担 | PERCENT | 100% | 将 X% 受到伤害转移给周围队友 |
| `damage_share_range` | 分担范围 | FLAT | ∞ | 伤害分担搜索范围(格) |
| `death_save_chance` | 免死概率 | PERCENT | 100% | 致命伤害时 X% 概率保留 1 血 |
| `death_save_health` | 免死回复 | FLAT | ∞ | 免死触发后回复的血量 |

---

# 三、功能属性

## 3.1 速度（待实现）

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `movement_speed` | 移动速度 | PERCENT | ±∞ | 影响行走/跑步速度 |
| `sprint_speed` | 冲刺速度 | PERCENT | ±∞ | 额外影响冲刺速度倍率 |
| `sneak_speed` | 潜行速度 | PERCENT | ±∞ | 影响潜行移动速度 |
| `swim_speed` | 游泳速度 | PERCENT | ±∞ | 影响水中移动速度 |
| `fly_speed` | 飞行速度 | PERCENT | ±∞ | 影响创造/鞘翅飞行速度 |
| `step_height` | 台阶高度 | FLAT | ∞ | 自动走上的方块高度(格) |

---

## 3.2 跳跃（待实现）

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `jump_height` | 跳跃高度 | PERCENT | ∞ | 跳跃高度倍率 |
| `double_jump` | 二段跳 | FLAT | 1 | 0=禁用 1=启用 |
| `wall_jump` | 蹬墙跳 | FLAT | 1 | 0=禁用 1=启用 |
| `fall_damage_reduce` | 摔落减免 | PERCENT | 100% | 跳跃相关摔落减免 |

---

## 3.3 采集/建造（待实现）

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `mining_speed` | 挖掘速度 | PERCENT | ∞ | 影响所有方块挖掘速度 |
| `pickaxe_speed` | 镐速度 | PERCENT | ∞ | 影响镐类挖掘速度 |
| `axe_speed` | 斧速度 | PERCENT | ∞ | 影响斧类挖掘速度 |
| `shovel_speed` | 锹速度 | PERCENT | ∞ | 影响锹类挖掘速度 |
| `hoe_speed` | 锄速度 | PERCENT | ∞ | 影响锄类挖掘速度 |
| `block_reach` | 建造距离 | FLAT | ∞ | 方块放置/交互距离加成(格) |
| `break_range` | 挖掘距离 | FLAT | ∞ | 方块破坏距离加成(格) |

---

## 3.4 掉落/收益（待实现）

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `looting_bonus` | 额外掉落 | PERCENT | ∞ | 生物额外掉落物概率 |
| `exp_bonus` | 经验加成 | PERCENT | ∞ | 经验球获取倍率 |
| `ore_double_chance` | 双倍矿物 | PERCENT | 100% | 挖矿双倍掉落概率 |
| `crop_double_chance` | 双倍作物 | PERCENT | 100% | 收割双倍掉落概率 |
| `fish_speed` | 钓鱼速度 | PERCENT | ∞ | 钓鱼等待时间减少 |
| `fish_treasure_chance` | 钓鱼宝藏 | PERCENT | 100% | 钓到宝藏概率加成 |
| `repair_speed` | 修复速度 | PERCENT | ∞ | 经验修补速度倍率 |

---

## 3.5 药水/效果（待实现）

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `potion_duration_pct` | 药水持久 | PERCENT | ∞ | 所有药水效果持续时间倍率 |
| `potion_amplifier` | 药水增幅 | FLAT | ∞ | 所有药水效果等级+1（每点） |
| `buff_duration_pct` | 增益持久 | PERCENT | ∞ | 正面效果持续时间倍率 |
| `debuff_reduce_pct` | 减益缩短 | PERCENT | 100% | 负面效果时间缩短比例 |
| `effect_resistance` | 效果抗性 | PERCENT | 100% | 免疫一切非伤害药水效果概率 |

---

## 3.6 特殊功能（待实现）

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `water_breathing` | 水下呼吸 | FLAT | 1 | 0=禁用 1=启用 |
| `night_vision` | 夜视 | FLAT | 1 | 0=禁用 1=启用（换上装备即生效） |
| `step_assist` | 自动上坡 | FLAT | ∞ | 自动走上台阶的最大高度 |
| `lava_walker` | 熔岩行者 | FLAT | 1 | 0=禁用 1=启用（踏岩浆） |
| `void_rescue` | 虚空救援 | FLAT | 1 | 0=禁用 1=启用（掉虚空弹回） |
| `ender_pearl_range` | 末影距离 | PERCENT | ∞ | 末影珍珠投掷距离倍率 |
| `ender_pearl_cooldown` | 末影冷却 | PERCENT | -100% | 末影珍珠冷却时间倍率（负=减少） |
| `bow_draw_speed` | 拉弓速度 | PERCENT | ∞ | 弓/弩蓄力速度倍率 |
| `crossbow_load_speed` | 装填速度 | PERCENT | ∞ | 弩装填速度倍率 |
| `trident_velocity` | 三叉戟速 | PERCENT | ∞ | 三叉戟投掷速度倍率 |
| `elytra_speed` | 鞘翅速度 | PERCENT | ∞ | 鞘翅飞行速度倍率 |
| `elytra_lift` | 鞘翅升力 | PERCENT | ∞ | 鞘翅爬升能力 |

---

## 3.7 社交/经济（待实现）

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `trade_discount_pct` | 交易折扣 | PERCENT | 100% | 村民交易价格折扣 |
| `villager_restock_speed` | 补货速度 | PERCENT | ∞ | 村民补货速度倍率 |
| `taming_chance` | 驯服概率 | PERCENT | 100% | 驯服动物额外概率加成 |
| `breed_speed` | 繁殖速度 | PERCENT | ∞ | 动物繁殖冷却减少 |
| `mob_scare_range` | 威慑范围 | FLAT | ∞ | 周围怪物主动避开的范围(格) |
| `mob_attract_range` | 嘲讽范围 | FLAT | ∞ | 周围怪物主动攻击的范围(格) |

---

## 3.8 时装/无属性（待实现）

| key | 名称 | 类型 | 上限 | 说明 |
|-----|------|------|:--:|------|
| `glow` | 发光 | FLAT | 1 | 0=禁用 1=启用（常驻 Glowing） |
| `trail_type` | 粒子轨迹 | FLAT | ∞ | 粒子效果 ID（行走轨迹特效） |
| `emote` | 表情动作 | FLAT | ∞ | 动作 ID（右键触发表情） |
| `pet_skin` | 宠物皮肤 | FLAT | ∞ | 宠物外观 ID |

---

# 四、属性汇总

## 已实现（16 个）

| 类别 | 属性 |
|------|------|
| 伤害 | MELEE_DAMAGE, MELEE_BONUS, PROJECTILE_DAMAGE, PROJECTILE_BONUS, DAMAGE, DAMAGE_BONUS |
| 暴击 | CRITICAL_CHANCE, CRITICAL_MULTIPLIER |
| 吸血 | LIFESTEAL_CHANCE, LIFESTEAL_MULTIPLIER, LIFESTEAL_FLAT, LIFESTEAL_DRAIN |
| 穿透 | LOW_PENETRATION, HIGH_PENETRATION, PENETRATION_EFFICIENCY, ARMOR_TOUGHNESS |
| 攻速 | ATTACK_SPEED |

## 待实现

| 类别 | 数量 |
|------|:--:|
| 元素伤害 | 11 |
| 连击/机制 | 10 |
| 目标减益 | 12 |
| 自身增益 | 6 |
| 特殊机制 | 8 |
| 基础生存 | 6 |
| 伤害减免 | 10 |
| 闪避/格挡 | 5 |
| 状态防护 | 7 |
| 反伤/承伤 | 6 |
| 速度 | 6 |
| 跳跃 | 4 |
| 采集/建造 | 7 |
| 掉落/收益 | 7 |
| 药水/效果 | 5 |
| 特殊功能 | 12 |
| 社交/经济 | 6 |
| 时装 | 4 |
| **合计** | **126** |

---

# 五、全局配置项

| 配置 | 默认值 | 说明 |
|------|:--:|------|
| `crit-default` | 50.0 | 默认暴击额外伤害% |
| `crit-mode` | ADD | 暴击倍率模式(ADD/MULTIPLY) |
| `lifesteal-default` | 50.0 | 默认偷取% |
| `lifesteal-mode` | ADD | 吸血倍率模式(ADD/MULTIPLY) |
| `armor-toughness` | 按材质配置 | 材质默认护甲韧性 |

---

# 六、开发优先级建议

```
第一梯队（核心战斗完整）
  ├─ 元素伤害（10个）       → 火焰/冰霜/雷电/真实伤害体系
  ├─ 连击/斩杀/弹射（10个） → 高端战斗机制
  └─ 生存基础（6个）         → 生命/防御/回复
第二梯队（攻防平衡）
  ├─ 闪避/格挡/招架（5个）
  ├─ 状态防护（7个）
  └─ 反伤/免死（6个）
第三梯队（功能扩展）
  ├─ 速度/跳跃（10个）
  ├─ 减益/增益（18个）
  ├─ 采集/掉落（14个）
  └─ 特殊机制（8个）
第四梯队（锦上添花）
  ├─ 药水/特殊功能（17个）
  ├─ 社交/经济（6个）
  └─ 时装（4个）
```

---

> 文档版本: v1.0 | 更新时间: 2026-06-11 | 共 142 个属性
