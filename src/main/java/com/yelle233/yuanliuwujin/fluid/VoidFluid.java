package com.yelle233.yuanliuwujin.fluid;

import com.yelle233.yuanliuwujin.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * 虚空流体（Void Fluid）—— 纯注册用空壳。
 * <p>
 * 该流体类仅用于满足 NeoForge 流体注册系统的需要（桶、FluidType、方块关联等），
 * <b>不执行任何原版流体逻辑</b>。所有的扩散、销毁、衰减行为均由
 * {@link com.yelle233.yuanliuwujin.block.VoidFluidBlock} 的调度 tick 独立管理。
 * <p>
 * 这样做的原因：原版 {@code FlowingFluid.tick()} 内部的 {@code getNewLiquid()}
 * 会根据相邻方块重新计算流动状态，导致源方块不断"补给"已进入衰减阶段的流动方块，
 * 使虚空流体永远无法消失。彻底禁用原版逻辑后，两套系统不再冲突。
 */
public abstract class VoidFluid extends BaseFlowingFluid {

    protected VoidFluid(Properties properties) {
        super(properties);
    }

    @Override
    public net.neoforged.neoforge.fluids.FluidType getFluidType() {
        return ModFluids.VOID_FLUID_TYPE.get();
    }

    // ══════════════════════════════════════════════════════════════
    //  彻底禁用原版流体逻辑
    // ══════════════════════════════════════════════════════════════

    /**
     * 完全跳过原版流体 tick。
     * 原版 tick 会调用 getNewLiquid() 重新计算并覆写方块状态，
     * 与 VoidFluidBlock 的衰减系统冲突。这里直接 return。
     */
    @Override
    public void tick(Level level, BlockPos pos, FluidState state) {
        // no-op: 所有逻辑由 VoidFluidBlock.tick() 管理
    }

    /**
     * 完全跳过原版流体扩散。
     * 扩散逻辑由 VoidFluidBlock 在恩惠期内自行处理。
     */
    @Override
    protected void spread(Level level, BlockPos pos, FluidState state) {
        // no-op: 所有逻辑由 VoidFluidBlock.tick() 管理
    }

    // ── 静止态（源方块） ──────────────────────────────────────────────

    public static class Source extends VoidFluid {

        public Source(Properties properties) {
            super(properties);
        }

        @Override
        public boolean isSource(FluidState state) { return true; }

        @Override
        public int getAmount(FluidState state) { return 8; }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<
                net.minecraft.world.level.material.Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
        }

        @Override
        public net.minecraft.world.level.material.Fluid getFlowing() {
            return ModFluids.VOID_FLUID_FLOWING.get();
        }

        @Override
        public net.minecraft.world.level.material.Fluid getSource() {
            return ModFluids.VOID_FLUID_SOURCE.get();
        }

        @Override
        protected boolean canConvertToSource(Level level) {
            return false;
        }
    }

    // ── 流动态 ──────────────────────────────────────────────────────

    public static class Flowing extends VoidFluid {

        public Flowing(Properties properties) {
            super(properties);
        }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<
                net.minecraft.world.level.material.Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) { return false; }

        @Override
        public int getAmount(FluidState state) { return state.getValue(LEVEL); }

        @Override
        public net.minecraft.world.level.material.Fluid getFlowing() {
            return ModFluids.VOID_FLUID_FLOWING.get();
        }

        @Override
        public net.minecraft.world.level.material.Fluid getSource() {
            return ModFluids.VOID_FLUID_SOURCE.get();
        }

        @Override
        protected boolean canConvertToSource(Level level) {
            return false;
        }
    }
}
