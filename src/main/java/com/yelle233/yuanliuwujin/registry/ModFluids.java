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

/**
 * 流体注册表。
 * <p>
 * 虚空流体（Void Fluid）是本模组的核心媒介，连接销毁机器与无限流体机器。
 * 它作为一种真实的 NeoForge 流体注册，可被第三方容器/管道正常储存和传输。
 */
public class ModFluids {

    /** 流体类型注册表 */
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, SourceflowInfinite.MODID);

    /** 流体注册表（使用原版流体注册） */
    public static final DeferredRegister<net.minecraft.world.level.material.Fluid> FLUIDS =
            DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.FLUID,
                    SourceflowInfinite.MODID);

    // ── 虚空流体类型 ──────────────────────────────────────────────
    public static final Supplier<VoidFluidType> VOID_FLUID_TYPE =
            FLUID_TYPES.register("void_fluid",
                    () -> new VoidFluidType(VoidFluidType.makeProperties()));

    // ── 虚空流体（静止态/源方块） ────────────────────────────────────
    public static final DeferredHolder<Fluid, VoidFluid.Source> VOID_FLUID_SOURCE =
            FLUIDS.register("void_fluid",
                    () -> new VoidFluid.Source(voidProps()));

    // ── 虚空流体（流动态） ────────────────────────────────────────
    public static final DeferredHolder<Fluid, VoidFluid.Flowing> VOID_FLUID_FLOWING =
            FLUIDS.register("void_fluid_flowing",
                    () -> new VoidFluid.Flowing(voidProps()));

    // ── 懒加载缓存 ────────────────────────────────────────────────
    private static BaseFlowingFluid.Properties VOID_PROPS;

    private static BaseFlowingFluid.Properties voidProps() {
        if (VOID_PROPS == null) {
            VOID_PROPS = new BaseFlowingFluid.Properties(
                    VOID_FLUID_TYPE,
                    VOID_FLUID_SOURCE,
                    VOID_FLUID_FLOWING
            )
                    .block(ModBlocks.VOID_FLUID_BLOCK)
                    .bucket(ModItems.VOID_BUCKET)
                    .slopeFindDistance(1)     // 限制流动寻路距离（默认4，水类）
                    .levelDecreasePerBlock(2) // 每格流动减少2级（默认1），流得更短
                    .tickRate(10);            // 流体更新间隔10 tick（比水的5更慢）
        }
        return VOID_PROPS;
    }
}
