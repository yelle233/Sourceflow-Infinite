package com.yelle233.yuanliuwujin.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class Modconfigs {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue FE_PER_TICK;
    public static final ModConfigSpec.IntValue BASE_PUSH_PER_TICK;
    public static final ModConfigSpec.IntValue FE_PER_ENABLED_FACE_PER_TICK;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BANNED_FLUIDS;





    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("infinite_core");
        BANNED_FLUIDS = b.comment(
                        "Ban list for Infinite Core binding AND Infinite Fluid Machine output.",
                        "Supports both direct fluid ids and fluid tags.",
                        "Format:",
                        "  - \"namespace:path\"  (fluid id)",
                        "  - \"#namespace:path\" (fluid tag)",
                        "Examples:",
                        "  \"minecraft:lava\"",
                        "  \"gtceu:oxygen\"",
                        "  \"#c:toxic_fluids\""
                )
                .comment("无限核心绑定及无限流体机器输出的禁用列表.",
                        "支持使用流体 ID 或流体标签 Tag 进行配置。")
                .defineListAllowEmpty(
                        "banlist",
                        List.of(

                        ),
                        o -> o instanceof String s && isValidBanEntry(s)
                );
        b.pop();

        b.push("infinite_fluid_machine");
        FE_PER_TICK = b.comment("Base FE consumed per tick when machine is outputting")
                .comment("机器默认消耗的FE")
                .defineInRange("fePerTick", 2, 0, Integer.MAX_VALUE-1);

        BASE_PUSH_PER_TICK = b.comment("Default push per tick (mB)")
                .comment("每tick主动输出的液体量")
                .defineInRange("PushPerTick", Integer.MAX_VALUE-1, 0, Integer.MAX_VALUE-1);

        FE_PER_ENABLED_FACE_PER_TICK= b.comment("Additional FE consumption for each enabled side.")
                .comment("每增加一个启用的面额外消耗的FE")
                .defineInRange("fePerEnableFacePerTick", 8, 0, Integer.MAX_VALUE-1);

        b.pop();

        SPEC = b.build();
    }

    private static boolean isValidBanEntry(String s) {
        if (s == null || s.isBlank()) return false;
        try {
            if (s.startsWith("#")) {
                ResourceLocation.parse(s.substring(1));
                return true;
            } else {
                ResourceLocation.parse(s);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }


    public static boolean isFluidBanned(ResourceLocation fluidId) {
        if (fluidId == null) return false;

        // 取到 Fluid 本体，用于 tag 判断
        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);

        for (String entry : BANNED_FLUIDS.get()) {
            if (entry == null || entry.isBlank()) continue;

            // 1) 直接 id 禁用
            if (!entry.startsWith("#")) {
                if (entry.equals(fluidId.toString())) return true;
                continue;
            }

            // 2) tag 禁用：#namespace:path
            try {
                ResourceLocation tagId = ResourceLocation.parse(entry.substring(1));
                TagKey<Fluid> tagKey = TagKey.create(Registries.FLUID, tagId);
                if (fluid.builtInRegistryHolder().is(tagKey)) return true;
            } catch (Exception ignored) {
            }
        }

        return false;
    }



}
