package com.yelle233.yuanliuwujin.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 模组配置（1.20.1 Forge 版本，v2.0）。
 * 使用 ForgeConfigSpec 代替 NeoForge 的 ModConfigSpec，API 基本一致。
 */
public class Modconfigs {

    public static final ForgeConfigSpec SPEC;

    // ── 通用设置 ──
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BANNED_FLUIDS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_UNBIND_SURVIVAL;

    // ── 虚空流体 ──
    public static final ForgeConfigSpec.IntValue VOID_FLUID_TICK_RATE;
    public static final ForgeConfigSpec.IntValue VOID_GRACE_PERIOD;
    public static final ForgeConfigSpec.BooleanValue VOID_DESTROY_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue VOID_KILL_ITEMS;
    public static final ForgeConfigSpec.BooleanValue VOID_KILL_ENTITIES;

    // ── 机器通用 ──
    public static final ForgeConfigSpec.IntValue MACHINE_VOID_TANK_CAPACITY;

    // ── 销毁机器 ──
    public static final ForgeConfigSpec.IntValue DESTROY_FE_BASE;
    public static final ForgeConfigSpec.IntValue DESTROY_FE_PER_MB_RATE;
    public static final ForgeConfigSpec.IntValue DESTROY_RATIO_L1;
    public static final ForgeConfigSpec.IntValue DESTROY_RATIO_L2;
    public static final ForgeConfigSpec.IntValue DESTROY_RATIO_L3;
    public static final ForgeConfigSpec.IntValue DESTROY_RATIO_L4;
    public static final ForgeConfigSpec.IntValue DESTROY_RATIO_OC;

    // ── 无限流体机器 ──
    public static final ForgeConfigSpec.IntValue INFINITE_FE_BASE;
    public static final ForgeConfigSpec.IntValue INFINITE_FE_PER_MB_RATE;
    public static final ForgeConfigSpec.IntValue INFINITE_RATIO_L1;
    public static final ForgeConfigSpec.IntValue INFINITE_RATIO_L2;
    public static final ForgeConfigSpec.IntValue INFINITE_RATIO_L3;
    public static final ForgeConfigSpec.IntValue INFINITE_RATIO_L4;
    public static final ForgeConfigSpec.IntValue INFINITE_RATIO_OC;

    // ── 超频压力 ──
    public static final ForgeConfigSpec.DoubleValue OVERCLOCK_PRESSURE_PER_TICK;
    public static final ForgeConfigSpec.DoubleValue PRESSURE_DECAY_PER_TICK;

    // ── 爆炸 ──
    public static final ForgeConfigSpec.DoubleValue EXPLOSION_STRENGTH;
    public static final ForgeConfigSpec.IntValue EXPLOSION_VOID_BLOCKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        // ── 通用设置 ──────────────────────────────────────────
        b.comment("通用设置 / General Settings").push("common");
        BANNED_FLUIDS = b.comment(
                "核心绑定黑名单，支持流体 ID（如 minecraft:water）和 tag（如 #forge:milk）",
                "Ban list for core binding. Supports fluid IDs and #tags."
        ).defineListAllowEmpty("banlist", List.of(), o -> o instanceof String s && !s.isBlank());
        ALLOW_UNBIND_SURVIVAL = b.comment(
                "生存模式下是否允许取消核心绑定（默认 true）",
                "Whether survival-mode players can unbind cores."
        ).define("allow_unbind_in_survival", true);
        b.pop();

        // ── 虚空流体 ──────────────────────────────────────────
        b.comment("虚空流体设置 / Void Fluid Settings").push("void_fluid");
        VOID_FLUID_TICK_RATE = b.comment(
                "虚空流体流动更新间隔（tick），越大流动越慢（默认 20，原版水为 5，岩浆为 40）",
                "Void fluid flow tick rate. Higher = slower. Water=5, Lava=40."
        ).defineInRange("fluidTickRate", 20, 5, 200);
        VOID_GRACE_PERIOD = b.comment(
                "虚空流体活跃阶段（恩惠期）持续时间（tick，默认 200 = 10 秒）。在此期间虚空流体扩散并吞噬方块，之后自然消失。",
                "Grace period (ticks) before void fluid disappears. During this time it spreads and destroys."
        ).defineInRange("gracePeriod", 200, 0, 6000);
        VOID_DESTROY_BLOCKS = b.comment(
                "虚空流体活跃阶段是否吞噬相邻方块（默认 true）",
                "Whether void fluid destroys adjacent blocks during grace period."
        ).define("destroyBlocks", true);
        VOID_KILL_ITEMS = b.comment(
                "虚空流体是否销毁接触到的掉落物品（默认 true）",
                "Whether void fluid destroys item entities on contact."
        ).define("killItems", true);
        VOID_KILL_ENTITIES = b.comment(
                "虚空流体是否杀死接触到的实体（默认 true）",
                "Whether void fluid kills entities on contact."
        ).define("killEntities", true);
        b.pop();

        // ── 机器通用 ──────────────────────────────────────────
        b.comment("机器通用设置 / Machine Common Settings").push("machine_common");
        MACHINE_VOID_TANK_CAPACITY = b.comment(
                "两种机器内部虚空流体储罐最大容量（mB，默认 1000 = 1 桶）",
                "Void tank capacity in mB. 1000 mB = 1 bucket."
        ).defineInRange("voidTankCapacity", 1000000, 10000, Integer.MAX_VALUE - 1);
        b.pop();

        // ── 销毁机器 ──────────────────────────────────────────
        b.comment("销毁机器设置 / Destruction Machine Settings").push("destruction_machine");
        DESTROY_FE_BASE = b.comment(
                "插入核心后每 tick 基础 FE 消耗（默认 2）",
                "Base FE/tick when core is inserted."
        ).defineInRange("feBase", 2, 0, Integer.MAX_VALUE - 1);
        DESTROY_FE_PER_MB_RATE = b.comment(
                "每 1 mB/s 面速率额外消耗的 FE/tick（默认 1）",
                "Extra FE/tick per 1 mB/s face rate."
        ).defineInRange("fePerMbRate", 1, 0, Integer.MAX_VALUE - 1);
        b.pop();

        // ── 无限流体机器 ──────────────────────────────────────
        b.comment("无限流体机器设置 / Infinite Fluid Machine Settings").push("infinite_fluid_machine");
        INFINITE_FE_BASE = b.comment(
                "插入核心后每 tick 基础 FE 消耗（默认 2）",
                "Base FE/tick when core is inserted."
        ).defineInRange("feBase", 2, 0, Integer.MAX_VALUE - 1);
        INFINITE_FE_PER_MB_RATE = b.comment(
                "每 1 mB/s 面速率额外消耗的 FE/tick（默认 1）",
                "Extra FE/tick per 1 mB/s face rate."
        ).defineInRange("fePerMbRate", 1, 0, Integer.MAX_VALUE - 1);
        b.pop();

        // ── 超频压力 ──────────────────────────────────────────
        b.comment("超频压力设置 / Overclock Pressure Settings").push("overclock");
        OVERCLOCK_PRESSURE_PER_TICK = b.comment(
                "超频核心运行时每 tick 积累的压力百分比（默认 0.006，约 166 秒满压）",
                "Pressure gained per tick (%). Default ~166 seconds to full."
        ).defineInRange("pressurePerTick", 0.006, 0.0001, 1.0);
        PRESSURE_DECAY_PER_TICK = b.comment(
                "停机时每 tick 压力衰减百分比（默认 0.05，约 100 秒完全衰减）",
                "Pressure decay per tick (%) when idle."
        ).defineInRange("pressureDecayPerTick", 0.05, 0.0001, 1.0);
        b.pop();

        // ── 爆炸 ──────────────────────────────────────────────
        b.comment("爆炸设置 / Explosion Settings").push("explosion");
        EXPLOSION_STRENGTH = b.comment(
                "超频爆炸威力（默认 80，TNT 为 4，末影水晶为 6）",
                "Explosion strength when pressure reaches 100%. TNT=4, end crystal=6."
        ).defineInRange("strength", 80.0, 0.1, 100.0);
        EXPLOSION_VOID_BLOCKS = b.comment(
                "爆炸时生成的虚空流体方块数量（默认 32）",
                "Number of void fluid blocks spawned on explosion."
        ).defineInRange("voidBlockCount", 32, 0, 256);
        b.pop();

        // ── 核心等级 ──────────────────────────────────────────────
        b.comment("核心等级设置 / Core Settings").push("core");
        DESTROY_RATIO_L1 = b.comment(
                "Lv.1：消耗 X mB 任意流体 → 1 mB 虚空流体（默认 1000）",
                "L1: mB fluid consumed per 1 mB void produced."
        ).defineInRange("destruction_core_l1", 1000, 1, Integer.MAX_VALUE - 1);
        DESTROY_RATIO_L2 = b.comment("Lv.2 转换比（默认 100）")
                .defineInRange("destruction_core_l2", 100, 1, Integer.MAX_VALUE - 1);
        DESTROY_RATIO_L3 = b.comment("Lv.3 转换比（默认 10）")
                .defineInRange("destruction_core_l3", 10, 1, Integer.MAX_VALUE - 1);
        DESTROY_RATIO_L4 = b.comment("Lv.4 转换比（默认 2）")
                .defineInRange("destruction_core_l4", 2, 1, Integer.MAX_VALUE - 1);
        DESTROY_RATIO_OC = b.comment(
                "超频销毁核心转换比（默认 1，即 1:1）",
                "OC ratio. Default 1 = 1:1 conversion."
        ).defineInRange("destruction_core_l4OC", 1, 1, Integer.MAX_VALUE - 1);
        INFINITE_RATIO_L1 = b.comment(
                "Lv.1：消耗 X mB 虚空流体 → 1 mB 任意流体（默认 1000）",
                "L1: mB void consumed per 1 mB fluid produced."
        ).defineInRange("infinite_core_l1", 1000, 1, Integer.MAX_VALUE - 1);
        INFINITE_RATIO_L2 = b.comment("Lv.2 转换比（默认 100）")
                .defineInRange("infinite_core_l2", 100, 1, Integer.MAX_VALUE - 1);
        INFINITE_RATIO_L3 = b.comment("Lv.3 转换比（默认 10）")
                .defineInRange("infinite_core_l3", 10, 1, Integer.MAX_VALUE - 1);
        INFINITE_RATIO_L4 = b.comment("Lv.4 转换比（默认 2）")
                .defineInRange("infinite_core_l4", 2, 1, Integer.MAX_VALUE - 1);
        INFINITE_RATIO_OC = b.comment(
                "超频无限核心转换比（默认 1，即 1:1）",
                "OC ratio. Default 1 = 1:1 conversion."
        ).defineInRange("infinite_core_l4OC", 1, 1, Integer.MAX_VALUE - 1);
        b.pop();

        SPEC = b.build();
    }

    /** 获取销毁核心转换比 */
    public static int getDestroyRatio(int level, boolean overclocked) {
        if (overclocked) return DESTROY_RATIO_OC.get();
        return switch (level) {
            case 1 -> DESTROY_RATIO_L1.get();
            case 2 -> DESTROY_RATIO_L2.get();
            case 3 -> DESTROY_RATIO_L3.get();
            case 4 -> DESTROY_RATIO_L4.get();
            default -> DESTROY_RATIO_L1.get();
        };
    }

    /** 获取无限核心转换比 */
    public static int getInfiniteRatio(int level, boolean overclocked) {
        if (overclocked) return INFINITE_RATIO_OC.get();
        return switch (level) {
            case 1 -> INFINITE_RATIO_L1.get();
            case 2 -> INFINITE_RATIO_L2.get();
            case 3 -> INFINITE_RATIO_L3.get();
            case 4 -> INFINITE_RATIO_L4.get();
            default -> INFINITE_RATIO_L1.get();
        };
    }

    /** 检查流体是否被禁用 */
    public static boolean isFluidBanned(ResourceLocation fluidId) {
        if (fluidId == null) return false;
        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
        for (String entry : BANNED_FLUIDS.get()) {
            if (entry == null || entry.isBlank()) continue;
            if (!entry.startsWith("#")) {
                if (entry.equals(fluidId.toString())) return true;
            } else {
                try {
                    ResourceLocation tagId = ResourceLocation.parse(entry.substring(1));
                    TagKey<Fluid> tagKey = TagKey.create(Registries.FLUID, tagId);
                    if (fluid.builtInRegistryHolder().is(tagKey)) return true;
                } catch (Exception ignored) {}
            }
        }
        return false;
    }
}
