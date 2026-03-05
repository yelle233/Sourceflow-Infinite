package com.yelle233.yuanliuwujin.advancement;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 成就触发器注册
 */
public class ModCriteriaTriggers {

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(
            Registries.TRIGGER_TYPE,
            SourceflowInfinite.MODID
    );

    public static final DeferredHolder<CriterionTrigger<?>, MachineExplosionTrigger> MACHINE_EXPLOSION =
            TRIGGERS.register("machine_explosion", MachineExplosionTrigger::new);
}
