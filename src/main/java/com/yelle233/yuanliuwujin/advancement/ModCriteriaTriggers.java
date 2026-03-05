package com.yelle233.yuanliuwujin.advancement;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;

/**
 * 成就触发器注册（1.20.1 Forge版本）
 */
public class ModCriteriaTriggers {

    public static MachineExplosionTrigger MACHINE_EXPLOSION;

    public static void register() {
        MACHINE_EXPLOSION = CriteriaTriggers.register(new MachineExplosionTrigger());
    }
}
