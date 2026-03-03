
# 中文

---
# 源流无尽 / Sourceflow Infinite

---

## 模组简介

**源流无尽**是一个以「流体回收生产」为核心玩法的科技向模组。通过两台机器和一种名为「虚空流体」的中间媒介，玩家可以将任意流体销毁并转化为虚空流体，再将虚空流体转化为任意已绑定的流体，从而实现流体的无限生产。


---

## 核心概念

### 虚空流体（Void Fluid）

虚空流体是两种机器之间的"货币"。它不能直接使用，只作为销毁机器与无限流体机器之间的转换媒介。

虚空流体被放置在世界中时（如机器爆炸产生），具有两个生命阶段：

- **恩惠期**（默认 10 秒）：虚空流体会积极向周围扩散，销毁接触到的方块、流体和实体。接触虚空流体的实体会受到虚空伤害并被立即消灭，物品实体会被直接销毁。
- **衰减阶段**：恩惠期结束后，虚空流体浓度逐步降低直至消失。

>  虚空流体极其危险！它可以摧毁除基岩以外的一切方块（包括水、岩浆等其他流体），并对任何实体造成致命伤害。

### 转换比

核心等级决定了流体与虚空流体之间的转换比：

| 核心等级 | 销毁转换比（流体 → 虚空） | 无限转换比（虚空 → 流体） |
|:---:|:---:|:---:|
| Lv.1 | 1000 : 1 | 1000 : 1 |
| Lv.2 | 100 : 1 | 100 : 1 |
| Lv.3 | 10 : 1 | 10 : 1 |
| Lv.4 | 2 : 1 | 2 : 1 |
| Lv.4 ★超频 | 1 : 1 | 1 : 1 |

例如：使用 Lv.1 销毁核心时，需要消耗 1000 mB 的任意流体才能产出 1 mB 虚空流体。

---

## 物品与方块

### 扳手

扳手是操作两种机器的核心工具，拥有两种模式（ **潜行 + 滚轮** 切换）：

**装配模式（IO Mode）**
- 右键机器：从副手将核心插入机器
- 潜行 + 右键机器：从机器中取出核心

**配置模式（Config Mode）**
- 右键机器侧面：循环切换该面的输入/输出模式
- 潜行 + 右键侧面：增加该面的流量速率（+10 mB/s）
- 潜行 + 滚轮上/下：大幅调整速率（±1000 mB/s）

**模式指示器：** 手持扳手时，屏幕物品栏上方会持久显示当前模式（IO 模式为青色，CONFIG 模式为金色），方便随时查看。

---

### 销毁核心（Destruction Core）

销毁核心用于插入销毁机器，将任意流体转换为虚空流体。

### 无限核心（Infinite Core）

无限核心用于插入无限流体机器，需要先绑定一种流体或化学品。

**绑定方式：**
- 手持无限核心**右键或潜行右键流体源方块**即可绑定该流体
- 若安装了 Mekanism，潜行＋右键化学品储罐可绑定化学品
- 对着空气**潜行 + 右键**清除已有绑定（生存模式下可在配置中禁用）

**核心升级：** 无限核心可以通过合成升级到更高等级（Lv.1 → Lv.2 → Lv.3 → Lv.4 → Lv.4 ★超频），升级时会**自动保留绑定的流体或化学品**，无需重新绑定。升级后的核心在物品栏中也会保持已绑定的贴图状态。

## 机器

### 销毁机器（Destruction Machine）

销毁机器将输入的任意流体转换为虚空流体并存储在内部储罐中。

**面功能说明：**

| 方向 | 功能 |
|---|---|
| 顶面（UP） | 能量输入（FE） |
| 底面（DOWN） | 虚空流体输出 |
| 四个侧面 | 流体输入，可通过扳手配置模式切换为 OFF / PUSH / BOTH |

**侧面模式说明：**
- **OFF**（关闭）：该面不参与流体传输，完全禁用。
- **PUSH**（被动接收）：该面仅接受外部管道推入的流体。适用于通过管道从远处输入流体的场景。
- **BOTH**（双向传输）：该面既接受管道推入，也会主动从相邻方块（如储罐、其他机器）抽取流体。适用于机器直接贴着流体容器放置的场景，省去管道。

**工作条件：**
1. 已插入销毁核心
2. 至少一个侧面处于启用状态
3. 有足够的 FE 能量

**能耗机制：**
- **无核心**：不消耗能量
- **待机**（有核心但无法工作）：消耗基础能量（默认 4 FE/tick）
- **工作中**：消耗基础能量 + 面速率额外能量

**虚空储罐：** 默认容量 1,000 mB

**待机：** 当虚空储罐为满或没有面启用时机器变为待机模式，此时仅消耗待机电量

**红石控制：** 机器支持红石信号控制。每次接收到红石信号（从无到有）时，会切换工作状态：
- 首次信号：保存当前侧面配置并将所有侧面设为 OFF，机器停止工作
- 再次信号：恢复之前保存的侧面配置，机器恢复工作
- 适用于超频核心的自动安全控制，防止无人看管时爆炸

### 无限流体机器（Infinite Fluid Machine）

无限流体机器消耗虚空流体，产出核心绑定的流体。

**面功能说明：**

| 方向 | 功能 |
|---|---|
| 顶面（UP） | 能量输入（FE） |
| 底面（DOWN） | 虚空流体输入 |
| 四个侧面 | 绑定流体输出，可配置为 OFF / PULL / BOTH |

**侧面模式说明：**
- **OFF**（关闭）：该面不参与流体传输，完全禁用。
- **PULL**（被动输出）：该面允许外部管道抽取流体，但机器不会主动推送。适用于由外部管道控制抽取节奏的场景。
- **BOTH**（双向传输）：该面既允许外部管道抽取，也会主动将流体推送到相邻方块（如储罐、其他机器）。适用于机器直接贴着储罐放置的场景，无需额外管道。

**工作条件：**
1. 已插入绑定了流体的无限核心
2. 至少一个侧面处于启用状态
3. 有足够的 FE 能量和虚空流体

**能耗机制：**
- **无核心**：不消耗能量
- **待机**（有核心但无法工作）：消耗基础能量（默认 2 FE/tick）
- **工作中**：消耗基础能量 + 面速率额外能量

**面速率：** 每个侧面默认 20 mB/s，可用扳手调节。面速率决定了该面每秒可以输入或输出的流体量。

**待机：** 当虚空储罐为空或没有面启用时机器变为待机模式，此时仅消耗待机电量

**红石控制：** 机器支持红石信号控制。每次接收到红石信号（从无到有）时，会切换工作状态：
- 首次信号：保存当前侧面配置并将所有侧面设为 OFF，机器停止工作
- 再次信号：恢复之前保存的侧面配置，机器恢复工作
- 适用于超频核心的自动安全控制，防止无人看管时爆炸

### HUD 信息显示

手持扳手或空手对准任一机器时，屏幕上会显示机器的实时 HUD 信息，包括：

- **能量**：当前储量 / 最大容量
- **耗电**：当前消耗（FE/s 和 FE/t），无核心时为 0，有核心时始终显示满载耗电以便查看所需电量
- **核心**：是否插入，以及当前核心等级（如 `已插入 Lv.3` 或 `已插入 Lv.4 ★`）
- **状态**：运行中 / 能量不足 / 未插入核心
- **绑定流体**（仅无限流体机器）：当前绑定的流体或化学品名称
- **虚空储罐**：虚空流体存量 / 容量
- **压力**（仅超频核心）：当前压力百分比，颜色随压力升高变化（绿 → 黄 → 红）
- **启用面**：各侧面的模式与速率（手持扳手时显示详细速率）

---

## 超频与爆炸系统

### 超频核心

Lv.4 ★超频核心拥有 **1:1** 的转换比，效率极高，但代价是机器运行时会持续积累**压力**。

**压力机制：**
- 运行时每 tick 积累约 0.006% 的压力（约 166 秒积满）
- 停止运行时每 tick 衰减约 0.05% 的压力（约 100 秒完全衰减）
- 当压力达到 **100%** 时，机器立刻爆炸

**爆炸后果：**
- 产生威力为 80 的爆炸（TNT = 4，末影水晶 = 6）
- 在爆炸点周围生成大量虚空流体方块（默认 32 个），虚空流体会进一步扩散并销毁周围的方块、流体和实体
- 机器本身与核心被摧毁

---

## 简单教程

### 第一步：搭建销毁系统

1. 合成**销毁核心**和**销毁机器**
2. 合成**扳手**
3. 将销毁核心放在副手，扳手放在主手，右键销毁机器插入核心
4. 用扳手切换到**配置模式**（Shift + 滚轮），右键侧面启用流体输入
5. 从顶面接入能量（FE）
6. 从侧面输入任意流体（如水、岩浆等）
7. 销毁机器会自动将流体转换为虚空流体，存储在内部储罐

### 第二步：搭建无限流体系统

1. 合成**无限核心**，手持右键你想要无限生产的流体源方块进行绑定
2. 合成**无限流体机器**，用扳手插入已绑定的无限核心
3. 用管道将销毁机器底面的虚空流体输出连接到无限流体机器底面的虚空流体输入
4. 从顶面接入能量
5. 配置侧面输出模式和速率
6. 无限流体机器会消耗虚空流体，持续产出绑定的流体


---

## 与其他模组的联动或兼容

---
### Mekanism 兼容

如果同时安装了 **Mekanism** 模组：

- 罐来绑定化学品（而非流体）
- 销毁机器的侧面可以接收 Mekanism 化学品，按同样的转换比转换为虚空流体
- 无限流体机器可以输出绑定的化学品
- 转换比和面速率预算同样适用于化学品

---

## 服务端配置

所有数值均可在 `serverconfig` 目录下的配置文件中修改。以下为主要配置项及默认值：

### 通用设置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| 核心绑定黑名单 | 空 | 支持流体 ID（如 `minecraft:water`）和 tag（如 `#forge:milk`） |
| 生存模式允许解绑 | true | 是否允许生存模式玩家清除核心绑定 |

### 虚空流体

| 配置项      | 默认值            | 范围         | 说明          |
|----------|----------------|------------|-------------|
| 流动更新间隔   | 20             | 5-200      | 越大流动越慢      |
| 恩惠期时长    | 200 tick（10 秒） | 0-6000     | 恩惠期持续时间     |
| 是否吞噬方块   | true           | true/false | 同时控制方块和流体的吞噬 |
| 是否吞噬掉落物品 | true           | true/false       | 略 |
| 是否杀死实体   | true           | true/false     | 略   |

### 机器设置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| 虚空储罐容量 | 1,000 mB | 两种机器的虚空流体储罐上限 |
| 销毁机器基础能耗 | 4 FE/tick | 待机时也会消耗 |
| 无限机器基础能耗 | 2 FE/tick | 待机时也会消耗 |

### 超频与爆炸

| 配置项 | 默认值 | 说明 |
|---|---|---|
| 压力积累速率 | 0.006%/tick | 约 166 秒积满 |
| 压力衰减速率 | 0.05%/tick | 约 100 秒完全衰减 |
| 爆炸威力 | 80 | TNT = 4，末影水晶 = 6 |
| 爆炸虚空方块数 | 32 | 爆炸时生成的虚空流体方块数量 |

### 核心等级对应的转换比

> 此处不再赘述，即可以修改四个等级与超频核心的转换比

---

## 常见问题

**Q：虚空流体桶放出来的流体会消失吗？**
A：会。虚空流体放置后会经历恩惠期（扩散+销毁），之后浓度逐步衰减直到完全消失。

**Q：虚空流体会吞噬水和岩浆吗？**
A：会。虚空流体在扩散和随机 tick 中都会吞噬接触到的其他流体（水、岩浆等），将其替换为虚空流体或直接清除。此行为受「是否吞噬方块」配置控制。

**Q：超频核心爆炸能避免吗？**
A：可以。有以下几种方法：
1. 手动监控 HUD 上的压力值，在压力较高时暂停机器运行让压力衰减
2. 使用红石信号控制机器自动停机：给机器一个红石信号即可关闭所有侧面，压力衰减后再次给信号恢复工作
3. 配合红石时钟或比较器等红石电路实现自动循环启停，防止压力积累

**Q：可以禁止某些流体被绑定吗？**
A：可以。在服务端配置文件的黑名单中添加流体 ID 或 tag 即可。

**Q：两台机器必须紧挨着吗？**
A：不必。用任何流体管道将销毁机器底面的虚空流体输出连接到无限流体机器底面即可。

**Q：机器没插核心时会耗电吗？**
A：不会。只有插入核心后才会开始消耗待机电量（基础 FE/tick）。工作时消耗满载电量（基础 + 面速率额外消耗）。


---

# English

---

# Sourceflow Infinite


---

## Overview

**Sourceflow Infinite** is a tech-focused mod built around **fluid recycling and production**. Using two machines and an intermediate medium called **Void Fluid**, you can destroy any fluid (or chemical), convert it into Void Fluid, and then convert Void Fluid into any **bound** fluid—allowing **infinite fluid production**.

---

## Core Concepts

### Void Fluid

Void Fluid is the “currency” between the two machines. It cannot be used directly; it only serves as the conversion medium between the **Destruction Machine** and the **Infinite Fluid Machine**.

When placed in the world (for example, created by an explosion), Void Fluid has two life stages:

* **Grace Period** (10 seconds by default): Void Fluid aggressively spreads and destroys blocks, fluids, and entities it touches. Entities that come into contact with it take void damage and are instantly killed; item entities are deleted.
* **Decay Phase**: After the grace period ends, the Void Fluid concentration gradually decreases until it disappears.

> **Void Fluid is extremely dangerous!** It can destroy everything except bedrock (including water, lava, and other fluids), and it is lethal to any entity.

---

### Conversion Ratios

The **core level** determines the conversion ratio between fluids and Void Fluid:

|     Core Level     | Destruction Ratio (Fluid → Void) | Infinite Ratio (Void → Fluid) |
| :----------------: | :------------------------------: | :---------------------------: |
|        Lv.1        |             1000 : 1             |            1000 : 1           |
|        Lv.2        |              100 : 1             |            100 : 1            |
|        Lv.3        |              10 : 1              |             10 : 1            |
|        Lv.4        |               2 : 1              |             2 : 1             |
| Lv.4 ★ Overclocked |               1 : 1              |             1 : 1             |

Example: With a Lv.1 Destruction Core, **1000 mB** of any fluid is required to produce **1 mB** of Void Fluid.

---

## Items & Blocks

### Wrench

The Wrench is the primary tool used to operate both machines. It has two modes (hold **Shift + Mouse Wheel** to switch):

**IO Mode**

* Right-click a machine: inserts the core from your offhand
* Sneak + right-click a machine: removes the inserted core

**Config Mode**

* Right-click a machine side: cycles that side’s input/output mode
* Sneak + right-click a side: increases that side’s rate (+10 mB/s)
* Shift + mouse wheel up/down: adjust rate in large steps (±1000 mB/s)

**Mode Indicator:** When holding the wrench, the current mode is persistently displayed above the hotbar (IO mode in cyan, CONFIG mode in gold) for easy reference.

---

### Destruction Core

Inserted into the Destruction Machine to convert any fluid into Void Fluid.

---

### Infinite Core

Inserted into the Infinite Fluid Machine. It must first be bound to a fluid or chemical.

**How to bind:**

* Hold the Infinite Core and **right-click or sneak-right-click a fluid source block** to bind that fluid.
* If **Mekanism** is installed: sneak-right-click a **Chemical Tank** to bind a chemical.
* **Sneak + right-click air** to clear an existing binding (this can be disabled in the config for Survival mode).

**Core Upgrading:** Infinite Cores can be upgraded to higher levels through crafting (Lv.1 → Lv.2 → Lv.3 → Lv.4 → Lv.4 ★ Overclocked). When upgrading, the **bound fluid or chemical is automatically preserved**—no need to rebind. The upgraded core will also maintain its bound texture state in the inventory.

---

## Machines

### Destruction Machine

Converts any input fluid into Void Fluid and stores it in its internal tank.

**Side functions:**

| Side          | Function                                                    |
| ------------- | ----------------------------------------------------------- |
| Top (UP)      | Energy input (FE)                                           |
| Bottom (DOWN) | Void Fluid output                                           |
| Four sides    | Fluid input; configurable in Config Mode: OFF / PUSH / BOTH |

**Side modes:**

* **OFF (Disabled):** Completely disables fluid transfer on that side.
* **PUSH (Passive intake):** Only accepts fluids pushed in by external pipes. Best for feeding fluids from a distance.
* **BOTH (Two-way transfer):** Accepts fluids pushed in, and also actively pulls from adjacent blocks (e.g., tanks or machines). Useful when placing the machine directly against a container without pipes.

**Operating requirements:**

1. A Destruction Core is inserted
2. At least one side is enabled
3. Sufficient FE power

**Power consumption:**

* **No core:** no power consumed
* **Idle** (core inserted but cannot operate): consumes base power (default **4 FE/tick**)
* **Running:** base power + additional cost based on side rate

**Void Tank:** default capacity **1,000 mB**

**Idle behavior:** The machine becomes idle when the Void Tank is full or no sides are enabled; in this state it only consumes idle power.

**Redstone Control:** The machine supports redstone signal control. Each time a redstone signal is received (transitioning from off to on), the machine toggles its working state:
- First signal: Saves current side configuration and sets all sides to OFF, stopping the machine
- Second signal: Restores the previously saved side configuration, resuming operation
- Useful for automatic safety control with overclocked cores to prevent unattended explosions

---

### Infinite Fluid Machine

Consumes Void Fluid and outputs the fluid bound to its core.

**Side functions:**

| Side          | Function                                              |
| ------------- | ----------------------------------------------------- |
| Top (UP)      | Energy input (FE)                                     |
| Bottom (DOWN) | Void Fluid input                                      |
| Four sides    | Bound fluid output; configurable as OFF / PULL / BOTH |

**Side modes:**

* **OFF (Disabled):** Completely disables fluid transfer on that side.
* **PULL (Passive output):** Allows external pipes to extract, but the machine does not actively push. Best when the pipe network controls the extraction rate.
* **BOTH (Two-way transfer):** Allows extraction and also actively pushes fluids to adjacent blocks (e.g., tanks or machines). Useful when placing the machine directly against a tank without extra pipes.

**Operating requirements:**

1. An Infinite Core bound to a fluid is inserted
2. At least one side is enabled
3. Sufficient FE power and enough Void Fluid

**Power consumption:**

* **No core:** no power consumed
* **Idle** (core inserted but cannot operate): consumes base power (default **2 FE/tick**)
* **Running:** base power + additional cost based on side rate

**Side rate:** Each side defaults to **20 mB/s** and can be adjusted with the wrench. The side rate determines how much fluid that side can input/output per second.

**Idle behavior:** The machine becomes idle when the Void Tank is empty or no sides are enabled; in this state it only consumes idle power.

**Redstone Control:** The machine supports redstone signal control. Each time a redstone signal is received (transitioning from off to on), the machine toggles its working state:
- First signal: Saves current side configuration and sets all sides to OFF, stopping the machine
- Second signal: Restores the previously saved side configuration, resuming operation
- Useful for automatic safety control with overclocked cores to prevent unattended explosions

---

## HUD Information

When holding the Wrench (or empty hand) and looking at either machine, an on-screen HUD displays real-time information, including:

* **Energy:** current / maximum
* **Power draw:** current consumption (FE/s and FE/t); shows **0** with no core, and shows **full-load power draw** when a core is inserted (to preview required power)
* **Core:** whether inserted, and the core level (e.g., `Inserted Lv.3` or `Inserted Lv.4 ★`)
* **Status:** Running / Insufficient Power / No Core
* **Bound fluid** (Infinite Fluid Machine only): the currently bound fluid or chemical
* **Void Tank:** amount / capacity
* **Pressure** (Overclocked Core only): current pressure percentage (color shifts as it rises: green → yellow → red)
* **Enabled sides:** each side’s mode and rate (shows detailed rates while holding the wrench)

---

## Overclocking & Explosion System

### Overclocked Core

The Lv.4 ★ Overclocked Core has a **1:1** conversion ratio and is extremely efficient, but it continuously builds **pressure** while the machine is running.

**Pressure mechanics:**

* While running: gains ~0.006% pressure per tick (fills in ~166 seconds)
* While stopped: decays ~0.05% pressure per tick (fully decays in ~100 seconds)
* At **100%** pressure, the machine explodes immediately

**Explosion effects:**

* Creates an explosion with power **80** (TNT = 4, End Crystal = 6) 
* Spawns a large number of Void Fluid blocks around the blast (default **32**), which then spreads and destroys nearby blocks, fluids, and entities
* Destroys the machine and the core

---

## Quick Tutorial

### Step 1: Build the Destruction System

1. Craft a **Destruction Core** and a **Destruction Machine**
2. Craft a **Wrench**
3. Put the Destruction Core in your offhand and the wrench in your main hand, then right-click the Destruction Machine to insert the core
4. Switch the wrench to **Config Mode** (Shift + Mouse Wheel) and right-click a side to enable fluid input
5. Provide FE power to the **top** side
6. Input any fluid from a side (e.g., water, lava)
7. The Destruction Machine will convert the fluid into Void Fluid and store it internally

### Step 2: Build the Infinite Fluid System

1. Craft an **Infinite Core** and right-click the fluid source block you want to produce infinitely to bind it
2. Craft an **Infinite Fluid Machine** and use the wrench to insert the bound Infinite Core
3. Connect the Destruction Machine’s **bottom** Void Fluid output to the Infinite Fluid Machine’s **bottom** Void Fluid input using pipes
4. Provide FE power to the top side
5. Configure output side modes and rates
6. The Infinite Fluid Machine will consume Void Fluid and continuously output the bound fluid

---

## Mod Compatibility / Integration

### Mekanism Compatibility

If **Mekanism** is installed:

* You can bind **chemicals** using a Mekanism Chemical Tank (instead of a fluid)
* The Destruction Machine sides can accept Mekanism chemicals and convert them into Void Fluid using the same conversion ratios
* The Infinite Fluid Machine can output bound chemicals
* Conversion ratios and side rate limits also apply to chemicals

---

## Server Configuration

All values can be changed in the config files under the `serverconfig` directory. Below are the main options and their default values:

### General

| Option                      | Default | Description                                                                 |
| --------------------------- | ------: | --------------------------------------------------------------------------- |
| Core binding blacklist      |   Empty | Supports fluid IDs (e.g., `minecraft:water`) and tags (e.g., `#forge:milk`) |
| Allow unbinding in Survival |    true | Whether Survival players are allowed to clear an Infinite Core binding      |

### Void Fluid

| Option                |         Default |      Range | Description                    |
| --------------------- | --------------: | ---------: | ------------------------------ |
| Flow update interval  |              20 |      5–200 | Higher = slower flow           |
| Grace period duration | 200 ticks (10s) |     0–6000 | Duration of the grace period   |
| Consume blocks        |            true | true/false | Also controls consuming fluids |
| Consume dropped items |            true | true/false | —                              |
| Kill entities         |            true | true/false | —                              |

### Machine Settings

| Option                         |   Default | Description                                    |
| ------------------------------ | --------: | ---------------------------------------------- |
| Void Tank capacity             |  1,000 mB | Max Void Fluid tank capacity for both machines |
| Destruction Machine base power | 4 FE/tick | Consumed even while idle                       |
| Infinite Machine base power    | 2 FE/tick | Consumed even while idle                       |

### Overclocking & Explosions

| Option              |     Default | Description                                      |
| ------------------- | ----------: | ------------------------------------------------ |
| Pressure build rate | 0.006%/tick | Fills in ~166s                                   |
| Pressure decay rate |  0.05%/tick | Fully decays in ~100s                            |
| Explosion strength  |          80 | TNT = 4, End Crystal = 6                         |
| Void blocks spawned |          32 | Number of Void Fluid blocks created on explosion |

### Conversion Ratios by Core Level

> Not listed here—each core level’s conversion ratios (including the overclocked core) can be configured.

---

## FAQ

**Q: Will Void Fluid placed from a bucket disappear?**
A: Yes. Once placed, it goes through the grace period (spreading + destroying), then gradually decays until it disappears.

**Q: Will Void Fluid consume water and lava?**
A: Yes. During spreading and random ticks, Void Fluid will consume other fluids (water, lava, etc.), replacing them with Void Fluid or removing them entirely. This behavior is controlled by the “Consume blocks” config option.

**Q: Can I prevent an overclocked-core explosion?**
A: Yes. There are several methods:
1. Manually monitor the pressure value on the HUD and pause the machine when pressure gets high to allow decay
2. Use redstone signals to automatically stop the machine: sending a redstone signal will disable all sides; after pressure decays, send another signal to resume operation
3. Combine with redstone clocks or comparators to create automatic start-stop cycles that prevent pressure buildup

**Q: Can I ban certain fluids from being bound?**
A: Yes. Add the fluid ID or tag to the blacklist in the server config.

**Q: Do the two machines have to be placed next to each other?**
A: No. Connect the Destruction Machine’s bottom Void Fluid output to the Infinite Fluid Machine’s bottom input using any fluid pipes.

**Q: Do machines consume power when no core is inserted?**
A: No. Power consumption starts only after a core is inserted (idle/base FE/t). When running, the machine consumes full-load power (base + side-rate cost).


