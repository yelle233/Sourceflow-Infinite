package com.yelle233.yuanliuwujin.fluid;

import com.yelle233.yuanliuwujin.block.VoidFluidBlock;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import com.yelle233.yuanliuwujin.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * 虚空流体（Void Fluid）。
 * <p>
 * v3.0 重构：恢复使用原版 FlowingFluid 扩散机制（不再 no-op tick/spread），
 * 这样桶、Jade 等外部模组能正确识别流体状态。
 * <p>
 * 改动点：
 * <ul>
 *   <li>扩散速度由 {@code VOID_FLUID_TICK_RATE} 配置控制（默认20tick，介于水5和岩浆40之间）</li>
 *   <li>覆写 {@link #spreadTo}：扩散前先销毁目标方块（受 VOID_DESTROY_BLOCKS 配置控制）</li>
 *   <li>覆写 {@link #canSpreadTo}：若 VOID_DESTROY_BLOCKS=true，允许流体扩散到可破坏方块</li>
 *   <li>恩惠期结束 / 方块吞噬逻辑移入 {@link VoidFluidBlock}（scheduledTick + randomTick）</li>
 * </ul>
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
    //  扩散控制：速度 + 吞噬方块
    // ══════════════════════════════════════════════════════════════

    /**
     * 返回流体 tick 间隔（tick），由配置文件控制。
     * 默认 20，比水（5）慢，比岩浆（40）快。
     */
    @Override
    public int getTickDelay(LevelReader level) {
        return Modconfigs.VOID_FLUID_TICK_RATE.get();
    }

    /**
     * 判断虚空流体是否可以扩散到目标位置。
     * <p>
     * 若 {@code VOID_DESTROY_BLOCKS=true}，允许扩散到非基岩的可破坏固体方块；
     * 否则只能扩散到空气和已有流体的位置（原版行为）。
     */
    @Override
    protected boolean canSpreadTo(BlockGetter level, BlockPos fromPos, BlockState fromBlockState,
                                  Direction direction, BlockPos toPos, BlockState toBlockState,
                                  FluidState toFluidState, Fluid fluid) {
        // 永远不扩散到基岩
        if (toBlockState.is(Blocks.BEDROCK)) return false;
        // 不替换同类流体
        if (toBlockState.getBlock() instanceof VoidFluidBlock) return false;

        // 如果目标是可破坏的固体方块，且配置允许销毁方块，则可以扩散
        if (!toBlockState.isAir() && toFluidState.isEmpty()) {
            return Modconfigs.VOID_DESTROY_BLOCKS.get()
                    && toBlockState.getDestroySpeed(level, toPos) >= 0;
        }

        // 其他情况走原版逻辑（空气、其他流体等）
        return super.canSpreadTo(level, fromPos, fromBlockState, direction, toPos,
                toBlockState, toFluidState, fluid);
    }

    /**
     * 实际扩散到目标位置。
     * <p>
     * 若目标是固体方块（非流体），且配置允许，先销毁目标方块（不掉落），再放置虚空流体。
     * 这样确保"吞噬方块"和"扩散"同步发生，不会出现"先吞噬后流"的问题。
     */
    @Override
    protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState blockState,
                            Direction direction, FluidState fluidState) {
        // 如果目标是可破坏固体方块，先移除（无掉落）
        if (!blockState.isAir() && blockState.getFluidState().isEmpty()) {
            if (Modconfigs.VOID_DESTROY_BLOCKS.get()
                    && !blockState.is(Blocks.BEDROCK)
                    && blockState.getDestroySpeed(level, pos) >= 0) {
                level.destroyBlock(pos, false); // false = 不掉落物品
            } else {
                return; // 无法销毁（可能是基岩或配置关闭），放弃扩散
            }
        }
        super.spreadTo(level, pos, level.getBlockState(pos), direction, fluidState);
    }

    // ══════════════════════════════════════════════════════════════
    //  源方块
    // ══════════════════════════════════════════════════════════════

    public static class Source extends VoidFluid {

        public Source(Properties properties) {
            super(properties);
        }

        @Override
        public boolean isSource(FluidState state) { return true; }

        @Override
        public int getAmount(FluidState state) { return 8; }

        @Override
        public Fluid getFlowing() { return ModFluids.VOID_FLUID_FLOWING.get(); }

        @Override
        public Fluid getSource() { return ModFluids.VOID_FLUID_SOURCE.get(); }

        @Override
        protected boolean canConvertToSource(net.minecraft.world.level.Level level) {
            return false; // 禁止源方块自动生成（2格流体合并成源）
        }

        @Override
        protected void createFluidStateDefinition(
                StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  流动方块
    // ══════════════════════════════════════════════════════════════

    public static class Flowing extends VoidFluid {

        public Flowing(Properties properties) {
            super(properties);
        }

        @Override
        public boolean isSource(FluidState state) { return false; }

        @Override
        public int getAmount(FluidState state) { return state.getValue(LEVEL); }

        @Override
        public Fluid getFlowing() { return ModFluids.VOID_FLUID_FLOWING.get(); }

        @Override
        public Fluid getSource() { return ModFluids.VOID_FLUID_SOURCE.get(); }

        @Override
        protected boolean canConvertToSource(net.minecraft.world.level.Level level) {
            return false;
        }

        @Override
        protected void createFluidStateDefinition(
                StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }
    }
}
