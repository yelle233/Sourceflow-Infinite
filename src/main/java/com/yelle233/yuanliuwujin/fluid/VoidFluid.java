package com.yelle233.yuanliuwujin.fluid;

import com.yelle233.yuanliuwujin.block.VoidFluidBlock;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import com.yelle233.yuanliuwujin.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;

/**
 * 虚空流体（1.20.1 Forge 版本）。
 * 使用 ForgeFlowingFluid 代替 NeoForge 的 BaseFlowingFluid。
 */
public abstract class VoidFluid extends ForgeFlowingFluid {

    protected VoidFluid(Properties properties) {
        super(properties);
    }

    @Override
    public FluidType getFluidType() {
        return ModFluids.VOID_FLUID_TYPE.get();
    }

    @Override
    public int getTickDelay(LevelReader level) {
        return Modconfigs.VOID_FLUID_TICK_RATE.get();
    }

    @Override
    public void tick(Level level, BlockPos pos, FluidState state) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            long key = VoidFluidBlock.makeKey(level, pos);
            Long birthTime = VoidFluidBlock.getBirthTime(key);
            long gracePeriod = Modconfigs.VOID_GRACE_PERIOD.get();

            if (birthTime != null && (level.getGameTime() - birthTime) >= gracePeriod) {
                VoidFluidBlock.removeBirthTime(key);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                return;
            }
        }
        super.tick(level, pos, state);

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            if (!serverLevel.getFluidTicks().hasScheduledTick(pos, state.getType())) {
                serverLevel.scheduleTick(pos, state.getType(), getTickDelay(level));
            }
        }
    }

    @Override
    protected boolean canSpreadTo(BlockGetter level, BlockPos fromPos, BlockState fromBlockState,
                                  Direction direction, BlockPos toPos, BlockState toBlockState,
                                  FluidState toFluidState, Fluid fluid) {
        if (toBlockState.is(Blocks.BEDROCK)) return false;
        if (toBlockState.getBlock() instanceof VoidFluidBlock) return false;

        if (!toFluidState.isEmpty()) {
            return Modconfigs.VOID_DESTROY_BLOCKS.get();
        }
        if (!toBlockState.isAir() && toFluidState.isEmpty()) {
            return Modconfigs.VOID_DESTROY_BLOCKS.get()
                    && toBlockState.getDestroySpeed(level, toPos) >= 0;
        }
        return super.canSpreadTo(level, fromPos, fromBlockState, direction, toPos,
                toBlockState, toFluidState, fluid);
    }

    @Override
    protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState blockState,
                            Direction direction, FluidState fluidState) {
        if (!blockState.isAir() && !(blockState.getBlock() instanceof VoidFluidBlock)) {
            if (!blockState.getFluidState().isEmpty()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            } else if (Modconfigs.VOID_DESTROY_BLOCKS.get()
                    && !blockState.is(Blocks.BEDROCK)
                    && blockState.getDestroySpeed(level, pos) >= 0) {
                level.destroyBlock(pos, false);
            } else {
                return;
            }
        }
        super.spreadTo(level, pos, level.getBlockState(pos), direction, fluidState);
    }

    // ═══ 源方块 ═══

    public static class Source extends VoidFluid {

        public Source(Properties properties) { super(properties); }

        @Override public boolean isSource(FluidState state) { return true; }
        @Override public int getAmount(FluidState state) { return 8; }
        @Override public Fluid getFlowing() { return ModFluids.VOID_FLUID_FLOWING.get(); }
        @Override public Fluid getSource() { return ModFluids.VOID_FLUID_SOURCE.get(); }

        @Override
        protected boolean canConvertToSource(Level level) { return false; }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
        }
    }

    // ═══ 流动方块 ═══

    public static class Flowing extends VoidFluid {

        public Flowing(Properties properties) { super(properties); }

        @Override public boolean isSource(FluidState state) { return false; }
        @Override public int getAmount(FluidState state) { return state.getValue(LEVEL); }
        @Override public Fluid getFlowing() { return ModFluids.VOID_FLUID_FLOWING.get(); }
        @Override public Fluid getSource() { return ModFluids.VOID_FLUID_SOURCE.get(); }

        @Override
        protected boolean canConvertToSource(Level level) { return false; }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }
    }
}

