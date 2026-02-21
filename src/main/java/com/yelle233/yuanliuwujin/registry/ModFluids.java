package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.fluid.VoidFluid;
import com.yelle233.yuanliuwujin.fluid.VoidFluidType;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, SourceflowInfinite.MODID);

    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.FLUID,
                    SourceflowInfinite.MODID);

    public static final Supplier<VoidFluidType> VOID_FLUID_TYPE =
            FLUID_TYPES.register("void_fluid", () -> new VoidFluidType(VoidFluidType.makeProperties()));

    public static final DeferredHolder<Fluid, VoidFluid.Source> VOID_FLUID_SOURCE =
            FLUIDS.register("void_fluid", () -> new VoidFluid.Source(voidProps()));

    public static final DeferredHolder<Fluid, VoidFluid.Flowing> VOID_FLUID_FLOWING =
            FLUIDS.register("void_fluid_flowing", () -> new VoidFluid.Flowing(voidProps()));

    private static BaseFlowingFluid.Properties VOID_PROPS;

    private static BaseFlowingFluid.Properties voidProps() {
        if (VOID_PROPS == null) {
            VOID_PROPS = new BaseFlowingFluid.Properties(
                    VOID_FLUID_TYPE, VOID_FLUID_SOURCE, VOID_FLUID_FLOWING
            )
                    .block(ModBlocks.VOID_FLUID_BLOCK)
                    .bucket(ModItems.VOID_BUCKET)
                    // slopeFindDistance=2：水=4（流得很远），岩浆=2，虚空流体对标岩浆
                    .slopeFindDistance(2)
                    // levelDecreasePerBlock=2：水=1（流7格），岩浆=2（流3格），虚空流体流3-4格
                    .levelDecreasePerBlock(2)
                    // tickRate 由 VoidFluid.getTickDelay() 从配置读取，此处的值作为备用
                    .tickRate(20);
        }
        return VOID_PROPS;
    }
}
