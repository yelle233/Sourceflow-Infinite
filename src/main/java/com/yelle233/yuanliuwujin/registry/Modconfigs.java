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

    public static final ForgeConfigSpec.IntValue FE_PER_TICK;
    public static final ForgeConfigSpec.IntValue BASE_PUSH_PER_TICK;
    public static final ForgeConfigSpec.IntValue FE_PER_ENABLED_FACE_PER_TICK;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BANNED_FLUIDS;
    public static final ForgeConfigSpec.IntValue BASE_PULL_PER_TICK;

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
                        "Examples: \"minecraft:lava\", \"#c:toxic_fluids\"")
                .defineList("banlist", new ArrayList<>(), o -> o instanceof String s && isValidBanEntry(s));
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
