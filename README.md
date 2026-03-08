# Sourceflow Infinite

[**中文**](./README_zh.md) | [**English**](README.md)

---

## Overview

**Sourceflow Infinite** is a tech-focused mod built around **fluid recycling and production**. Using two machines and an intermediate medium called **Void Fluid**, you can destroy any fluid (or chemical), convert it into Void Fluid, and then convert Void Fluid into any **bound** fluid—allowing **infinite fluid production**.

---

## Core Concepts

### Void Fluid

Void Fluid is the "currency" between the two machines. It cannot be used directly; it only serves as the conversion medium between the **Destruction Machine** and the **Infinite Fluid Machine**.

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

* Right-click a machine with empty offhand: removes the inserted core
* Right-click a machine with a core in offhand: inserts the core
* Sneak + right-click a machine: dismantles the machine and returns it (along with the core) to your inventory
* Sneak + right-click a Void Generator: dismantles it and returns it to your inventory

**Config Mode**

* Right-click a machine side: cycles that side's input/output mode
* Sneak + right-click a side: increases that side's rate (+10 mB/s)
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

### Void Generator

The Void Generator consumes Void Fluid and converts it into FE energy, outputting it to adjacent blocks.

**Side functions:**

| Side          | Function                                                        |
| ------------- | --------------------------------------------------------------- |
| Bottom (DOWN) | Void Fluid input                                                |
| Other 5 sides | Energy output (FE); each side can be toggled on/off separately |

**Operating requirements:**

1. Void Fluid is being supplied to the bottom
2. At least one output side is enabled

**Conversion ratio:** 1 mB Void Fluid = 10 FE by default (configurable)

**Side rate:** Each side has an independently configurable output rate (FE/t). Use the wrench in Config Mode to toggle sides on/off; sneak + right-click to open the rate input screen.

**Redstone Control:** Each time a redstone signal is received (transitioning from off to on), all output sides are toggled together (all on ↔ all off).

**HUD:** Displays energy stored, void fluid consumption (mB/s and mB/t), status, void tank level, and per-side output rates.

**Recipe:** Iron Ingots + Redstone + Destruction Core + Infinite Core + Void Bucket (3×3 crafting)

---

### Reinforced Void Block

An extremely durable block that is completely immune to Void Fluid.

* **Hardness:** 100 (Obsidian is 50); requires a diamond pickaxe to mine
* **Blast resistance:** 2400 (Obsidian is 1200)
* **Void immunity:** Void Fluid cannot consume or spread into Reinforced Void Blocks, making them ideal for building safe containment areas

**Recipe:** Obsidian + Diamond + Void Bucket

---

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
* **Power draw / Consumption:** the two machines show power draw (FE/s and FE/t); the Void Generator shows void fluid consumption (mB/s and mB/t)
* **Core:** whether inserted, and the core level (e.g., `Inserted Lv.3` or `Inserted Lv.4 ★`)
* **Status:** Running / Insufficient Power / No Core
* **Bound fluid** (Infinite Fluid Machine only): the currently bound fluid or chemical
* **Void Tank:** amount / capacity
* **Pressure** (Overclocked Core only): current pressure percentage (color shifts as it rises: green → yellow → red)
* **Enabled sides:** each side's mode and rate (shows detailed rates while holding the wrench)

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
3. Connect the Destruction Machine's **bottom** Void Fluid output to the Infinite Fluid Machine's **bottom** Void Fluid input using pipes
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

> Not listed here—each core level's conversion ratios (including the overclocked core) can be configured.

---

## FAQ

**Q: Will Void Fluid placed from a bucket disappear?**
A: Yes. Once placed, it goes through the grace period (spreading + destroying), then gradually decays until it disappears.

**Q: Will Void Fluid consume water and lava?**
A: Yes. During spreading and random ticks, Void Fluid will consume other fluids (water, lava, etc.), replacing them with Void Fluid or removing them entirely. This behavior is controlled by the "Consume blocks" config option.

**Q: Can I prevent an overclocked-core explosion?**
A: Yes. There are several methods:
1. Manually monitor the pressure value on the HUD and pause the machine when pressure gets high to allow decay
2. Use redstone signals to automatically stop the machine: sending a redstone signal will disable all sides; after pressure decays, send another signal to resume operation
3. Combine with redstone clocks or comparators to create automatic start-stop cycles that prevent pressure buildup

**Q: Can I ban certain fluids from being bound?**
A: Yes. Add the fluid ID or tag to the blacklist in the server config.

**Q: Do the two machines have to be placed next to each other?**
A: No. Connect the Destruction Machine's bottom Void Fluid output to the Infinite Fluid Machine's bottom input using any fluid pipes.

**Q: Do machines consume power when no core is inserted?**
A: No. Power consumption starts only after a core is inserted (idle/base FE/t). When running, the machine consumes full-load power (base + side-rate cost).






