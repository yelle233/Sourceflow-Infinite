package com.yelle233.yuanliuwujin.registry;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModDataComponents {

    public static final DeferredRegister.DataComponents REGISTRAR =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, "yuanliuwujin");

    public static final Supplier<DataComponentType<ResourceLocation>> BOUND_FLUID =
            REGISTRAR.registerComponentType("bound_fluid",
                    builder -> builder.persistent(ResourceLocation.CODEC)
                            .networkSynchronized(ResourceLocation.STREAM_CODEC));

    public static final Supplier<DataComponentType<ResourceLocation>> BOUND_CHEMICAL =
            REGISTRAR.registerComponentType("bound_chemical",
                    builder -> builder.persistent(ResourceLocation.CODEC)
                            .networkSynchronized(ResourceLocation.STREAM_CODEC));
}
