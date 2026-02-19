package com.yelle233.yuanliuwujin.registry;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * 模组配置（服务端配置，存放在 serverconfig 目录）。
 * <p>
 * 所有数值均可在服务端配置文件中修改，无需重启客户端（热重载需要 /reload 或重进世界）。
 */
public class Modconfigs {

    public static final ModConfigSpec SPEC;

    // ===== 通用 =====
    /** 核心 banlist（无限核心绑定的黑名单流体，支持流体 ID 和 tag） */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BANNED_FLUIDS;
    /** 生存模式是否允许取消绑定 */
    public static final ModConfigSpec.BooleanValue ALLOW_UNBIND_SURVIVAL;

    // ===== 虚空流体 =====
    /** 虚空流体方块每 20 tick 浓度衰减量（默认 2，约 5 * 20t = 100 tick 完全消失）*/
    public static final ModConfigSpec.IntValue VOID_DECAY_PER_20T;
    /** 虚空流体最大扩散半径（以浓度衰减层数计，默认 4）*/
    public static final ModConfigSpec.IntValue VOID_MAX_SPREAD_RADIUS;

    // ===== 机器通用 =====
    /** 两种机器内部虚空流体储罐最大容量（mB，默认 100000） */
    public static final ModConfigSpec.IntValue MACHINE_VOID_TANK_CAPACITY;

    // ===== 销毁机器 =====
    /** 销毁机器每 tick 基础 FE 消耗（有核心时，无论面是否启用） */
    public static final ModConfigSpec.IntValue DESTROY_FE_BASE;
    /** 销毁机器速率耗电比：每 1 mB/tick 面速率额外消耗 FE/tick */
    public static final ModConfigSpec.IntValue DESTROY_FE_PER_MB_RATE;
    /** 销毁机器各级销毁比（任意流体 : 虚空流体），即消耗多少 mB 任意流体生成 1 mB 虚空流体 */
    public static final ModConfigSpec.IntValue DESTROY_RATIO_L1;
    public static final ModConfigSpec.IntValue DESTROY_RATIO_L2;
    public static final ModConfigSpec.IntValue DESTROY_RATIO_L3;
    public static final ModConfigSpec.IntValue DESTROY_RATIO_L4;
    /** 超频销毁核心比（任意 : 虚空），默认 1:1 即值为 1 */
    public static final ModConfigSpec.IntValue DESTROY_RATIO_OC;

    // ===== 无限流体机器 =====
    /** 无限流体机器每 tick 基础 FE 消耗 */
    public static final ModConfigSpec.IntValue INFINITE_FE_BASE;
    /** 无限流体机器速率耗电比：每 1 mB/tick 面速率额外消耗 FE/tick */
    public static final ModConfigSpec.IntValue INFINITE_FE_PER_MB_RATE;
    /** 无限流体机器各级产出比（虚空流体 : 任意流体），即消耗多少 mB 虚空流体生成 1 mB 任意流体 */
    public static final ModConfigSpec.IntValue INFINITE_RATIO_L1;
    public static final ModConfigSpec.IntValue INFINITE_RATIO_L2;
    public static final ModConfigSpec.IntValue INFINITE_RATIO_L3;
    public static final ModConfigSpec.IntValue INFINITE_RATIO_L4;
    /** 超频无限核心比（虚空 : 任意），默认 1:1 即值为 1 */
    public static final ModConfigSpec.IntValue INFINITE_RATIO_OC;

    // ===== 超频压力 =====
    /** 超频核心运行时每 tick 积累的压力（百分比，默认 0.01 即 1% per 100 tick ≈ 166 秒满压） */
    public static final ModConfigSpec.DoubleValue OVERCLOCK_PRESSURE_PER_TICK;
    /** 停机时每 tick 压力衰减量（百分比，默认 0.05，约 2000 tick 即 100 秒清空） */
    public static final ModConfigSpec.DoubleValue PRESSURE_DECAY_PER_TICK;

    // ===== 爆炸 =====
    /** 爆炸强度（原版 TNT 为 4.0） */
    public static final ModConfigSpec.DoubleValue EXPLOSION_STRENGTH;
    /** 爆炸时生成的虚空流体方块数量（额外泄漏，上限 64） */
    public static final ModConfigSpec.IntValue EXPLOSION_VOID_BLOCKS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        // ── 通用 ──────────────────────────────────────────────────
        b.push("common");
        BANNED_FLUIDS = b.comment(
                "Ban list for Infinite Core binding AND Infinite Fluid Machine output.",
                "Supports direct fluid ids and fluid tags.",
                "Format: \"namespace:path\" or \"#namespace:path\" (tag)"
        ).defineListAllowEmpty("banlist", List.of(),
                o -> o instanceof String s && !s.isBlank());

        ALLOW_UNBIND_SURVIVAL = b.comment(
                "Whether survival-mode players can unbind an Infinite Core (sneak+right-click).",
                "生存模式是否允许取消无限核心绑定（潜行右键）"
        ).define("allow_unbind_in_survival", true);
        b.pop();

        // ── 虚空流体 ─────────────────────────────────────────────
        b.push("void_fluid");
        VOID_DECAY_PER_20T = b.comment(
                "Concentration decay amount per 20 ticks for Void Fluid blocks.",
                "Range: 1–15. Higher = faster decay. Default=2 (~75 seconds total lifespan).",
                "虚空流体方块每 20 tick 的浓度衰减量，越大消失越快"
        ).defineInRange("decayPer20Ticks", 2, 1, 15);

        VOID_MAX_SPREAD_RADIUS = b.comment(
                "Max spread radius for Void Fluid blocks (measured in concentration-loss layers).",
                "Range: 0–14. Default=4.",
                "虚空流体最大扩散半径（以浓度层数计）"
        ).defineInRange("maxSpreadRadius", 4, 0, 14);
        b.pop();

        // ── 机器通用 ───────────────────────────────────────────
        b.push("machine_common");
        MACHINE_VOID_TANK_CAPACITY = b.comment(
                "Internal void fluid tank capacity for both machines (mB). Default=100000.",
                "两种机器内部虚空流体储罐最大容量（mB）"
        ).defineInRange("voidTankCapacity", 100000, 1000, Integer.MAX_VALUE - 1);
        b.pop();

        // ── 销毁机器 ──────────────────────────────────────────
        b.push("destruction_machine");
        DESTROY_FE_BASE = b.comment(
                "Base FE consumed per tick when the Destruction Machine has a core (idle).",
                "销毁机器插入核心时的每 tick 基础耗电"
        ).defineInRange("feBase", 4, 0, Integer.MAX_VALUE - 1);

        DESTROY_FE_PER_MB_RATE = b.comment(
                "Additional FE consumed per tick for every 1 mB/tick of face rate.",
                "每 1 mB/tick 面速率额外消耗的 FE/tick（速率耗电系数）"
        ).defineInRange("fePerMbRate", 1, 0, Integer.MAX_VALUE - 1);

        DESTROY_RATIO_L1 = b.comment(
                "Level-1 Destruction Core: mB of ANY fluid consumed to produce 1 mB of Void Fluid.",
                "1 级销毁核心：消耗多少 mB 任意流体产生 1 mB 虚空流体"
        ).defineInRange("ratioLevel1", 1000, 1, Integer.MAX_VALUE - 1);

        DESTROY_RATIO_L2 = b.comment("Level-2 ratio. Default=100.")
                .defineInRange("ratioLevel2", 100, 1, Integer.MAX_VALUE - 1);
        DESTROY_RATIO_L3 = b.comment("Level-3 ratio. Default=10.")
                .defineInRange("ratioLevel3", 10, 1, Integer.MAX_VALUE - 1);
        DESTROY_RATIO_L4 = b.comment("Level-4 ratio. Default=2.")
                .defineInRange("ratioLevel4", 2, 1, Integer.MAX_VALUE - 1);
        DESTROY_RATIO_OC = b.comment(
                "Overclocked Destruction Core ratio. Default=1 (1:1, lossless destruction).",
                "超频销毁核心比，默认 1（无损）"
        ).defineInRange("ratioOverclock", 1, 1, Integer.MAX_VALUE - 1);
        b.pop();

        // ── 无限流体机器 ─────────────────────────────────────
        b.push("infinite_fluid_machine");
        INFINITE_FE_BASE = b.comment(
                "Base FE consumed per tick when the Infinite Machine has a core (idle).",
                "无限流体机器插入核心时的每 tick 基础耗电"
        ).defineInRange("feBase", 2, 0, Integer.MAX_VALUE - 1);

        INFINITE_FE_PER_MB_RATE = b.comment(
                "Additional FE consumed per tick for every 1 mB/tick of face rate.",
                "每 1 mB/tick 面速率额外消耗的 FE/tick"
        ).defineInRange("fePerMbRate", 1, 0, Integer.MAX_VALUE - 1);

        INFINITE_RATIO_L1 = b.comment(
                "Level-1 Infinite Core: mB of Void Fluid consumed to produce 1 mB of any fluid.",
                "1 级无限核心：消耗多少 mB 虚空流体产生 1 mB 任意流体"
        ).defineInRange("ratioLevel1", 1000, 1, Integer.MAX_VALUE - 1);

        INFINITE_RATIO_L2 = b.comment("Level-2 ratio. Default=100.")
                .defineInRange("ratioLevel2", 100, 1, Integer.MAX_VALUE - 1);
        INFINITE_RATIO_L3 = b.comment("Level-3 ratio. Default=10.")
                .defineInRange("ratioLevel3", 10, 1, Integer.MAX_VALUE - 1);
        INFINITE_RATIO_L4 = b.comment("Level-4 ratio. Default=2.")
                .defineInRange("ratioLevel4", 2, 1, Integer.MAX_VALUE - 1);
        INFINITE_RATIO_OC = b.comment(
                "Overclocked Infinite Core ratio. Default=1 (1:1, maximum efficiency).",
                "超频无限核心比，默认 1"
        ).defineInRange("ratioOverclock", 1, 1, Integer.MAX_VALUE - 1);
        b.pop();

        // ── 超频压力 ──────────────────────────────────────────
        b.push("overclock");
        OVERCLOCK_PRESSURE_PER_TICK = b.comment(
                "Pressure accumulated per tick while an overclocked machine is running (%).",
                "Range: 0.0001–1.0. Default=0.006 (~2777 ticks ≈ 2.3 minutes to 100%).",
                "超频机器每 tick 积累的压力（%），越大爆炸越快"
        ).defineInRange("pressurePerTick", 0.006, 0.0001, 1.0);

        PRESSURE_DECAY_PER_TICK = b.comment(
                "Pressure lost per tick when machine is idle/stopped (%).",
                "Default=0.05 (100% cleared in 2000 ticks ≈ 100 seconds).",
                "停机时每 tick 压力衰减量（%）"
        ).defineInRange("pressureDecayPerTick", 0.05, 0.0001, 1.0);
        b.pop();

        // ── 爆炸 ─────────────────────────────────────────────
        b.push("explosion");
        EXPLOSION_STRENGTH = b.comment(
                "Explosion strength when pressure reaches 100%. Default=6.0 (stronger than TNT).",
                "爆炸强度，原版 TNT=4.0"
        ).defineInRange("strength", 6.0, 0.1, 100.0);

        EXPLOSION_VOID_BLOCKS = b.comment(
                "Number of Void Fluid blocks spawned around the explosion. Max=64.",
                "爆炸时在周围生成的虚空流体方块数量（上限 64）"
        ).defineInRange("voidBlockCount", 16, 0, 64);
        b.pop();

        SPEC = b.build();
    }

    // ── 工具方法：根据等级获取对应配置比例 ─────────────────────────

    /** 获取销毁核心指定等级的销毁比（任意→虚空，多少 mB 任意流体 = 1 mB 虚空） */
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

    /** 获取无限核心指定等级的产出比（虚空→任意，多少 mB 虚空流体 = 1 mB 任意流体） */
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
}
