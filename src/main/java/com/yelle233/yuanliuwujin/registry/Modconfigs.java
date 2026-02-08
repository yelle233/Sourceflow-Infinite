package com.yelle233.yuanliuwujin.registry;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Modconfigs {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue FE_PER_TICK;
    public static final ModConfigSpec.IntValue BASE_PUSH_PER_TICK;
    public static final ModConfigSpec.IntValue ENERGY_CAPACITY;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("infinite_fluid_machine");
        FE_PER_TICK = b.comment("Base FE consumed per tick when machine is outputting")
                .defineInRange("fePerTick", 20, 0, 1_000_000);

        BASE_PUSH_PER_TICK = b.comment("Default push per tick (mB)")
                .defineInRange("defaultPushPerTick", 10000000, 1, Integer.MAX_VALUE);

        ENERGY_CAPACITY = b.comment("Internal battery capacity (FE)")
                .defineInRange("energyCapacity", 100_000, 0, 10_000_000);
        b.pop();

        SPEC = b.build();
    }
}
