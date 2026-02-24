package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.fluid.VoidFluid;
import com.yelle233.yuanliuwujin.fluid.VoidFluidType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 流体注册表（1.20.1 Forge 版本）。
 * 使用 ForgeFlowingFluid 代替 NeoForge 的 BaseFlowingFluid。
 */
public class ModFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, SourceflowInfinite.MODID);

    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, SourceflowInfinite.MODID);

    // ── 流体类型 ──
    public static final RegistryObject<VoidFluidType> VOID_FLUID_TYPE =
            FLUID_TYPES.register("void_fluid", () -> new VoidFluidType(VoidFluidType.makeProperties()));

    // ── 源方块 & 流动方块 ──
    public static final RegistryObject<VoidFluid.Source> VOID_FLUID_SOURCE =
            FLUIDS.register("void_fluid", () -> new VoidFluid.Source(voidProps()));

    public static final RegistryObject<VoidFluid.Flowing> VOID_FLUID_FLOWING =
            FLUIDS.register("void_fluid_flowing", () -> new VoidFluid.Flowing(voidProps()));

    private static ForgeFlowingFluid.Properties VOID_PROPS;

    private static ForgeFlowingFluid.Properties voidProps() {
        if (VOID_PROPS == null) {
            VOID_PROPS = new ForgeFlowingFluid.Properties(
                    VOID_FLUID_TYPE, VOID_FLUID_SOURCE, VOID_FLUID_FLOWING
            )
                    .block(ModBlocks.VOID_FLUID_BLOCK)
                    .bucket(ModItems.VOID_BUCKET)
                    .slopeFindDistance(2)
                    .levelDecreasePerBlock(2)
                    .tickRate(20);
        }
        return VOID_PROPS;
    }
}

