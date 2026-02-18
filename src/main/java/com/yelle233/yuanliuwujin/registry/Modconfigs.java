package com.yelle233.yuanliuwujin.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 模组配置（1.20.1 Forge 版本）。
 * <p>
 * 使用 {@link ForgeConfigSpec} 替代 NeoForge 的 ModConfigSpec。
 */
public class Modconfigs {

    public static final ForgeConfigSpec SPEC;

    // ===== 无限流体机器 =====
    public static final ForgeConfigSpec.IntValue FE_PER_TICK;
    public static final ForgeConfigSpec.IntValue BASE_PUSH_PER_TICK;
    public static final ForgeConfigSpec.IntValue FE_PER_ENABLED_FACE_PER_TICK;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BANNED_FLUIDS;
    public static final ForgeConfigSpec.IntValue BASE_PULL_PER_TICK;
    public static final ForgeConfigSpec.BooleanValue ALLOW_UNBIND_SURVIVAL;

    // ===== 销毁机器（新增） =====

    /** 销毁机器每 tick 基础 FE 消耗（有核心时，无论是否有面启用） */
    public static final ForgeConfigSpec.IntValue DESTROY_FE_PER_TICK;

    /** 销毁机器每启用一个面额外增加的 FE/tick 消耗 */
    public static final ForgeConfigSpec.IntValue DESTROY_FE_PER_ENABLED_FACE;

    /**
     * 销毁机器每 tick 可销毁的流体/化学品总量（mB）。
     * 此值同时限制：BOTH 模式主动抽取量 + PUSH 模式被动接收量。
     * 设为最大值（Integer.MAX_VALUE - 1）相当于无限制。
     */
    public static final ForgeConfigSpec.IntValue DESTROY_PER_TICK;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        // ===== 无限核心 banlist =====
        b.push("infinite_core");
        BANNED_FLUIDS = b.comment(
                        "Ban list for Infinite Core binding AND Infinite Fluid Machine output.",
                        "Supports both direct fluid ids and fluid tags.",
                        "Format:",
                        "  - \"namespace:path\"  (fluid id)",
                        "  - \"#namespace:path\" (fluid tag)",
                        "Examples: \"minecraft:lava\", \"#c:toxic_fluids\"",
                        "无限核心绑定及无限液体机器输出的禁用列表",
                        "支持直接填写流体 ID 或流体标签",
                        "格式:",
                        "  - \"namespace:path\"  (流体ID)",
                        "  - \"#namespace:path\" (流体标签)",
                        "示例: \"minecraft:lava\", \"#c:toxic_fluids\""
                )
                .defineList("banlist", new ArrayList<>(), o -> o instanceof String s && isValidBanEntry(s));

        ALLOW_UNBIND_SURVIVAL = b.comment(
                        "Whether players in survival mode can unbind a core (shift+right-click).",
                        "Creative mode is always allowed regardless of this setting.",
                        "生存模式下是否允许取消核心绑定，创造模式不受此设置影响")
                .define("allow_unbind_in_survival", true);
        b.pop();

        // ===== 无限流体机器 =====
        b.push("infinite_fluid_machine");
        FE_PER_TICK = b.comment("Base FE consumed per tick when machine has a valid core",
                        "机器在待机状态下的每tick耗电")
                .defineInRange("fePerTick", 2, 0, Integer.MAX_VALUE - 1);
        BASE_PUSH_PER_TICK = b.comment("Fluid pushed per tick per BOTH-mode face (mB)",
                        "每tick每面主动推送的液体量(mB)，设为最大值等于无限制")
                .defineInRange("PushPerTick", Integer.MAX_VALUE - 1, 0, Integer.MAX_VALUE - 1);
        BASE_PULL_PER_TICK = b.comment("Max drain per tick per face (mB) in PULL/BOTH mode. Set to max for unlimited.",
                        "每tick每面最大被抽取量(mB)，设为最大值等于无限制")
                .defineInRange("PullPerTick", Integer.MAX_VALUE - 1, 1, Integer.MAX_VALUE - 1);
        FE_PER_ENABLED_FACE_PER_TICK = b.comment("Additional FE per tick for each enabled face",
                        "每启用一个面额外增加的 FE/tick 消耗")
                .defineInRange("fePerEnableFacePerTick", 8, 0, Integer.MAX_VALUE - 1);
        b.pop();

        // ===== 销毁机器（新增配置节） =====
        b.push("destruction_machine");

        DESTROY_FE_PER_TICK = b.comment(
                        "Base FE consumed per tick when Destruction Machine has a core inserted.",
                        "销毁机器插入销毁核心后的每tick基础耗电（待机耗电）")
                .defineInRange("fePerTick", 4, 0, Integer.MAX_VALUE - 1);

        DESTROY_FE_PER_ENABLED_FACE = b.comment(
                        "Additional FE per tick for each enabled face (PUSH or BOTH mode).",
                        "销毁机器每启用一个面额外增加的FE/tick消耗")
                .defineInRange("fePerEnabledFace", 8, 0, Integer.MAX_VALUE - 1);

        DESTROY_PER_TICK = b.comment(
                        "Max fluid/chemical amount (mB) the Destruction Machine can void per tick.",
                        "This limit applies to both passive fill() and active BOTH drain.",
                        "Set to max value (2147483646) for effectively unlimited.",
                        "销毁机器每tick可销毁的流体/化学品总量(mB)。",
                        "同时限制被动接受量和主动抽取量，设为最大值等于无限制。")
                .defineInRange("destroyPerTick", Integer.MAX_VALUE - 1, 1, Integer.MAX_VALUE - 1);

        b.pop();

        SPEC = b.build();
    }

    /* ====== banlist 工具方法 ====== */

    private static boolean isValidBanEntry(String s) {
        if (s == null || s.isBlank()) return false;
        try {
            ResourceLocation.tryParse(s.startsWith("#") ? s.substring(1) : s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isFluidBanned(ResourceLocation fluidId) {
        if (fluidId == null) return false;
        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
        for (String entry : BANNED_FLUIDS.get()) {
            if (entry == null || entry.isBlank()) continue;
            if (!entry.startsWith("#")) {
                if (entry.equals(fluidId.toString())) return true;
            } else {
                try {
                    ResourceLocation tagId = ResourceLocation.tryParse(entry.substring(1));
                    TagKey<Fluid> tagKey = TagKey.create(Registries.FLUID, tagId);
                    if (fluid.builtInRegistryHolder().is(tagKey)) return true;
                } catch (Exception ignored) {}
            }
        }
        return false;
    }
}
