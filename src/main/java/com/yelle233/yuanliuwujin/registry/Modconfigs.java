package com.yelle233.yuanliuwujin.registry;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Modconfigs {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue FE_PER_TICK;
    public static final ModConfigSpec.IntValue BASE_PUSH_PER_TICK;
    public static final ModConfigSpec.IntValue FE_PER_ENABLED_FACE_PER_TICK;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("infinite_fluid_machine");
        FE_PER_TICK = b.comment("Base FE consumed per tick when machine is outputting")
                .comment("机器默认消耗的FE")
                .defineInRange("fePerTick", 2, 0, Integer.MAX_VALUE);

        BASE_PUSH_PER_TICK = b.comment("Default push per tick (mB)")
                .comment("每tick主动输出的液体量")
                .defineInRange("defaultPushPerTick", 10000000, 0, Integer.MAX_VALUE);

        FE_PER_ENABLED_FACE_PER_TICK= b.comment("Additional FE consumption for each enabled side.")
                .comment("每增加一个启用的面额外消耗的FE")
                .defineInRange("fePerEnableFacePerTick", 8, 0, Integer.MAX_VALUE);

        b.pop();

        SPEC = b.build();
    }
}
