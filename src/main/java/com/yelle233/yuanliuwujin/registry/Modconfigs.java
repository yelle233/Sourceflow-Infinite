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
 * <p>
 * 配置项：
 * <ul>
 *   <li>液体 banlist：支持直接液体 ID 和 Tag</li>
 *   <li>基础 FE 消耗 / 每面额外 FE 消耗</li>
 *   <li>每 tick 主动推送量（mB）</li>
 * </ul>
 */
public class Modconfigs {

    public static final ModConfigSpec SPEC;

    /** 机器每 tick 基础 FE 消耗（有核心且有液体时） */
    public static final ModConfigSpec.IntValue FE_PER_TICK;

    /** 每 tick 主动推送的液体量（mB），默认为 Integer.MAX_VALUE - 1 */
    public static final ModConfigSpec.IntValue BASE_PUSH_PER_TICK;

    /** 每启用一个面额外增加的 FE/tick 消耗 */
    public static final ModConfigSpec.IntValue FE_PER_ENABLED_FACE_PER_TICK;

    /** 液体禁用列表，支持 "namespace:path"（ID）和 "#namespace:path"（Tag） */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BANNED_FLUIDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        // ===== 无限核心 banlist =====
        b.push("infinite_core");
        BANNED_FLUIDS = b.comment(
                        "Ban list for Infinite Core binding AND Infinite Fluid Machine output.",
                        "Supports both direct fluid ids and fluid tags.",
                        "Format:",
                        "  - \"namespace:path\"  (fluid id)",
                        "  - \"#namespace:path\" (fluid tag)",
                        "Examples: \"minecraft:lava\", \"#c:toxic_fluids\"")
                .defineListAllowEmpty("banlist", List.of(), o -> o instanceof String s && isValidBanEntry(s));
        b.pop();

        // ===== 无限流体机器 =====
        b.push("infinite_fluid_machine");

        FE_PER_TICK = b.comment("Base FE consumed per tick when machine has a valid core")
                .defineInRange("fePerTick", 2, 0, Integer.MAX_VALUE - 1);

        BASE_PUSH_PER_TICK = b.comment("Fluid pushed per tick per BOTH-mode face (mB)")
                .defineInRange("PushPerTick", Integer.MAX_VALUE - 1, 0, Integer.MAX_VALUE - 1);

        FE_PER_ENABLED_FACE_PER_TICK = b.comment("Additional FE per tick for each enabled face")
                .defineInRange("fePerEnableFacePerTick", 8, 0, Integer.MAX_VALUE - 1);

        b.pop();

        SPEC = b.build();
    }

    /* ====== banlist 工具方法 ====== */

    /** 检验 banlist 条目格式是否合法 */
    private static boolean isValidBanEntry(String s) {
        if (s == null || s.isBlank()) return false;
        try {
            ResourceLocation.parse(s.startsWith("#") ? s.substring(1) : s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断指定液体是否被 banlist 禁用。
     * 支持直接 ID 匹配和 Tag 匹配。
     */
    public static boolean isFluidBanned(ResourceLocation fluidId) {
        if (fluidId == null) return false;

        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);

        for (String entry : BANNED_FLUIDS.get()) {
            if (entry == null || entry.isBlank()) continue;

            if (!entry.startsWith("#")) {
                // 直接 ID 匹配
                if (entry.equals(fluidId.toString())) return true;
            } else {
                // Tag 匹配：去掉 # 前缀
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
