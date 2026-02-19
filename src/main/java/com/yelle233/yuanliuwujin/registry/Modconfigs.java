package com.yelle233.yuanliuwujin.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * 模组配置（服务端配置，存放在 serverconfig 目录）。
 * 所有数值均可在服务端配置文件中修改，热重载需要 /reload 或重进世界。
 */
public class Modconfigs {

    public static final ModConfigSpec SPEC;

    // ===== 通用 / 一般设置 =====
    /** 核心绑定黑名单（支持流体 ID 和 #tag 格式） */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BANNED_FLUIDS;
    /** 生存模式下是否允许取消核心绑定 */
    public static final ModConfigSpec.BooleanValue ALLOW_UNBIND_SURVIVAL;

    // ===== 虚空流体 =====
    /** 虚空流体方块每次调度衰减的浓度值（默认 1，范围 1-15） */
    public static final ModConfigSpec.IntValue VOID_DECAY_PER_20T;
    /** 虚空流体最大扩散半径（以浓度衰减层数计，默认 4） */
    public static final ModConfigSpec.IntValue VOID_MAX_SPREAD_RADIUS;
    /** 虚空流体方块浓度衰减调度间隔（tick，默认 40） */
    public static final ModConfigSpec.IntValue VOID_DECAY_INTERVAL;
    /** 虚空流体活跃阶段（恩惠期）持续时间（tick，默认 200 = 10 秒），在此期间只扩散/销毁不衰减 */
    public static final ModConfigSpec.IntValue VOID_GRACE_PERIOD;
    /** 虚空流体活跃阶段扩散/销毁的调度间隔（tick，默认 5），越小扩散越快 */
    public static final ModConfigSpec.IntValue VOID_SPREAD_INTERVAL;

    // ===== 机器通用 =====
    /** 两种机器内部虚空流体储罐最大容量（mB，默认 1000000 = 1000 桶） */
    public static final ModConfigSpec.IntValue MACHINE_VOID_TANK_CAPACITY;

    // ===== 销毁机器 =====
    /** 销毁机器插入核心后每 tick 基础 FE 消耗 */
    public static final ModConfigSpec.IntValue DESTROY_FE_BASE;
    /** 销毁机器每 1 mB/s 面速率额外消耗的 FE/tick */
    public static final ModConfigSpec.IntValue DESTROY_FE_PER_MB_RATE;
    /** 销毁核心 Lv.1 转换比（消耗 X mB 任意流体产出 1 mB 虚空流体） */
    public static final ModConfigSpec.IntValue DESTROY_RATIO_L1;
    /** 销毁核心 Lv.2 转换比 */
    public static final ModConfigSpec.IntValue DESTROY_RATIO_L2;
    /** 销毁核心 Lv.3 转换比 */
    public static final ModConfigSpec.IntValue DESTROY_RATIO_L3;
    /** 销毁核心 Lv.4 转换比 */
    public static final ModConfigSpec.IntValue DESTROY_RATIO_L4;
    /** 超频销毁核心转换比（默认 1:1） */
    public static final ModConfigSpec.IntValue DESTROY_RATIO_OC;

    // ===== 无限流体机器 =====
    /** 无限流体机器插入核心后每 tick 基础 FE 消耗 */
    public static final ModConfigSpec.IntValue INFINITE_FE_BASE;
    /** 无限流体机器每 1 mB/s 面速率额外消耗的 FE/tick */
    public static final ModConfigSpec.IntValue INFINITE_FE_PER_MB_RATE;
    /** 无限核心 Lv.1 转换比（消耗 X mB 虚空流体产出 1 mB 任意流体） */
    public static final ModConfigSpec.IntValue INFINITE_RATIO_L1;
    /** 无限核心 Lv.2 转换比 */
    public static final ModConfigSpec.IntValue INFINITE_RATIO_L2;
    /** 无限核心 Lv.3 转换比 */
    public static final ModConfigSpec.IntValue INFINITE_RATIO_L3;
    /** 无限核心 Lv.4 转换比 */
    public static final ModConfigSpec.IntValue INFINITE_RATIO_L4;
    /** 超频无限核心转换比（默认 1:1） */
    public static final ModConfigSpec.IntValue INFINITE_RATIO_OC;

    // ===== 超频压力 =====
    /** 超频核心运行时每 tick 积累的压力百分比 */
    public static final ModConfigSpec.DoubleValue OVERCLOCK_PRESSURE_PER_TICK;
    /** 停机时每 tick 压力衰减百分比 */
    public static final ModConfigSpec.DoubleValue PRESSURE_DECAY_PER_TICK;

    // ===== 爆炸 =====
    /** 超频爆炸威力 */
    public static final ModConfigSpec.DoubleValue EXPLOSION_STRENGTH;
    /** 爆炸时生成的虚空流体方块数量 */
    public static final ModConfigSpec.IntValue EXPLOSION_VOID_BLOCKS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("通用设置 / General Settings").push("common");
        BANNED_FLUIDS = b.comment(
                "核心绑定黑名单，支持流体 ID（如 minecraft:water）和 tag（如 #forge:milk）",
                "Ban list for Infinite Core binding. Supports fluid IDs and #tags."
        ).defineListAllowEmpty("banlist", List.of(), o -> o instanceof String s && !s.isBlank());
        ALLOW_UNBIND_SURVIVAL = b.comment(
                "生存模式下是否允许取消核心绑定（默认 true）",
                "Whether survival-mode players can unbind cores."
        ).define("allow_unbind_in_survival", true);
        b.pop();

        b.comment("虚空流体设置 / Void Fluid Settings").push("void_fluid");
        VOID_DECAY_PER_20T = b.comment(
                "虚空流体每次衰减减少的浓度值（范围 1-15，默认 1）",
                "Concentration decay per scheduled tick."
        ).defineInRange("decayPer20Ticks", 1, 1, 15);
        VOID_MAX_SPREAD_RADIUS = b.comment(
                "虚空流体最大扩散半径（范围 0-14，默认 4）",
                "Max spread radius in concentration layers."
        ).defineInRange("maxSpreadRadius", 4, 0, 14);
        VOID_DECAY_INTERVAL = b.comment(
                "虚空流体方块浓度衰减调度间隔（tick，默认 40）",
                "Void fluid block decay scheduled tick interval."
        ).defineInRange("decayInterval", 40, 10, 200);
        VOID_GRACE_PERIOD = b.comment(
                "虚空流体活跃阶段（恩惠期）持续时间（tick，默认 200 = 10 秒）。在此期间虚空流体只扩散和销毁方块，不会衰减浓度。",
                "Grace period (ticks) before void fluid starts decaying. During this time it only spreads and destroys."
        ).defineInRange("gracePeriod", 200, 0, 6000);
        VOID_SPREAD_INTERVAL = b.comment(
                "虚空流体活跃阶段扩散/销毁的调度间隔（tick，默认 5），越小扩散和销毁方块越快",
                "Spread/destroy tick interval during grace period. Lower = faster."
        ).defineInRange("spreadInterval", 5, 1, 100);
        b.pop();

        b.comment("机器通用设置 / Machine Common Settings").push("machine_common");
        MACHINE_VOID_TANK_CAPACITY = b.comment(
                "两种机器内部虚空流体储罐最大容量（mB，默认 1000000 = 1000 桶）",
                "Void tank capacity in mB. 1000 mB = 1 bucket."
        ).defineInRange("voidTankCapacity", 1000000, 1000, Integer.MAX_VALUE - 1);
        b.pop();

        b.comment("销毁机器设置 / Destruction Machine Settings").push("destruction_machine");
        DESTROY_FE_BASE = b.comment(
                "插入核心后每 tick 基础 FE 消耗（默认 4）",
                "Base FE/tick when core is inserted."
        ).defineInRange("feBase", 4, 0, Integer.MAX_VALUE - 1);
        DESTROY_FE_PER_MB_RATE = b.comment(
                "每 1 mB/s 面速率额外消耗的 FE/tick（默认 1）",
                "Extra FE/tick per 1 mB/s face rate."
        ).defineInRange("fePerMbRate", 1, 0, Integer.MAX_VALUE - 1);
        DESTROY_RATIO_L1 = b.comment(
                "Lv.1 销毁核心：消耗 X mB 任意流体 → 1 mB 虚空流体（默认 1000）",
                "L1: mB fluid consumed per 1 mB void produced."
        ).defineInRange("ratioLevel1", 1000, 1, Integer.MAX_VALUE - 1);
        DESTROY_RATIO_L2 = b.comment(
                "Lv.2 销毁核心转换比（默认 100）"
        ).defineInRange("ratioLevel2", 100, 1, Integer.MAX_VALUE - 1);
        DESTROY_RATIO_L3 = b.comment(
                "Lv.3 销毁核心转换比（默认 10）"
        ).defineInRange("ratioLevel3", 10, 1, Integer.MAX_VALUE - 1);
        DESTROY_RATIO_L4 = b.comment(
                "Lv.4 销毁核心转换比（默认 2）"
        ).defineInRange("ratioLevel4", 2, 1, Integer.MAX_VALUE - 1);
        DESTROY_RATIO_OC = b.comment(
                "超频销毁核心转换比（默认 1，即 1:1）",
                "OC ratio. Default 1 = 1:1 conversion."
        ).defineInRange("ratioOverclock", 1, 1, Integer.MAX_VALUE - 1);
        b.pop();

        b.comment("无限流体机器设置 / Infinite Fluid Machine Settings").push("infinite_fluid_machine");
        INFINITE_FE_BASE = b.comment(
                "插入核心后每 tick 基础 FE 消耗（默认 2）",
                "Base FE/tick when core is inserted."
        ).defineInRange("feBase", 2, 0, Integer.MAX_VALUE - 1);
        INFINITE_FE_PER_MB_RATE = b.comment(
                "每 1 mB/s 面速率额外消耗的 FE/tick（默认 1）",
                "Extra FE/tick per 1 mB/s face rate."
        ).defineInRange("fePerMbRate", 1, 0, Integer.MAX_VALUE - 1);
        INFINITE_RATIO_L1 = b.comment(
                "Lv.1 无限核心：消耗 X mB 虚空流体 → 1 mB 任意流体（默认 1000）",
                "L1: mB void consumed per 1 mB fluid produced."
        ).defineInRange("ratioLevel1", 1000, 1, Integer.MAX_VALUE - 1);
        INFINITE_RATIO_L2 = b.comment(
                "Lv.2 无限核心转换比（默认 100）"
        ).defineInRange("ratioLevel2", 100, 1, Integer.MAX_VALUE - 1);
        INFINITE_RATIO_L3 = b.comment(
                "Lv.3 无限核心转换比（默认 10）"
        ).defineInRange("ratioLevel3", 10, 1, Integer.MAX_VALUE - 1);
        INFINITE_RATIO_L4 = b.comment(
                "Lv.4 无限核心转换比（默认 2）"
        ).defineInRange("ratioLevel4", 2, 1, Integer.MAX_VALUE - 1);
        INFINITE_RATIO_OC = b.comment(
                "超频无限核心转换比（默认 1，即 1:1）",
                "OC ratio. Default 1 = 1:1 conversion."
        ).defineInRange("ratioOverclock", 1, 1, Integer.MAX_VALUE - 1);
        b.pop();

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

        b.comment("爆炸设置 / Explosion Settings").push("explosion");
        EXPLOSION_STRENGTH = b.comment(
                "超频爆炸威力（默认 80，TNT 为 4，末影水晶为 6）",
                "Explosion strength when pressure reaches 100%. TNT=4, end crystal=6."
        ).defineInRange("strength", 80.0, 0.1, 500.0);
        EXPLOSION_VOID_BLOCKS = b.comment(
                "爆炸时生成的虚空流体方块数量（默认 32）",
                "Number of void fluid blocks spawned on explosion."
        ).defineInRange("voidBlockCount", 32, 0, 256);
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
