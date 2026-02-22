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
     * 【修复】在流体 tick 中检查恩惠期是否结束。
     * <p>
     * 原先的方案是通过 {@code VoidFluidBlock.onPlace} 安排 scheduled block tick，
     * 但 block tick 和 fluid tick 是两套系统，LiquidBlock 的 block tick 可能不会可靠触发
     * （流体系统更新方块状态时可能干扰 block tick 调度）。
     * <p>
     * 新方案：在每次流体 tick（由流体调度器可靠触发）时检查恩惠期，
     * 超期则移除方块，未超期则正常扩散。
     * <p>
     * 关键修复：{@code FlowingFluid.tick()} 对<b>源方块</b>只调用 {@code spread()} 而
     * <b>不会重新调度下一次流体 tick</b>。因此必须在 {@code super.tick()} 之后
     * 显式重新调度流体 tick，否则恩惠期检查只会执行一次就永远不再触发。
     */
    @Override
    public void tick(Level level, BlockPos pos, FluidState state) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            long key = VoidFluidBlock.makeKey(level, pos);
            Long birthTime = VoidFluidBlock.getBirthTime(key);
            long gracePeriod = Modconfigs.VOID_GRACE_PERIOD.get();

            if (birthTime != null && (level.getGameTime() - birthTime) >= gracePeriod) {
                VoidFluidBlock.removeBirthTime(key);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                return; // 不再扩散
            }
        }
        // 恩惠期内正常扩散
        super.tick(level, pos, state);

        // 【关键】显式重新调度流体 tick
        // FlowingFluid.tick() 对源方块（isSource=true）只调用 spread() 后就结束，
        // 不会重新 scheduleTick —— 导致恩惠期检查只执行一次后再也不触发。
        // 对流动方块，super.tick() 会自行调度，hasScheduledTick 返回 true，此处是 no-op。
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            if (!serverLevel.getFluidTicks().hasScheduledTick(pos, state.getType())) {
                serverLevel.scheduleTick(pos, state.getType(), getTickDelay(level));
            }
        }
    }

    /**
     * 判断虚空流体是否可以扩散到目标位置。
     * <p>
     * 若 {@code VOID_DESTROY_BLOCKS=true}，允许扩散到非基岩的可破坏固体方块和其他流体方块；
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

        // 目标是其他流体（水、岩浆等）→ 允许吞噬（配置控制）
        if (!toFluidState.isEmpty()) {
            return Modconfigs.VOID_DESTROY_BLOCKS.get();
        }

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
     * 若目标是固体方块（非流体）或其他流体方块，且配置允许，先移除目标，再放置虚空流体。
     * 这样确保"吞噬方块/流体"和"扩散"同步发生。
     */
    @Override
    protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState blockState,
                            Direction direction, FluidState fluidState) {
        if (!blockState.isAir() && !(blockState.getBlock() instanceof VoidFluidBlock)) {
            if (!blockState.getFluidState().isEmpty()) {
                // 目标是其他流体（水、岩浆等）→ 强制设为 AIR 后再放置虚空流体
                // destroyBlock 对流体方块无效（MC 会用流体状态回填），必须显式设为 AIR
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            } else if (Modconfigs.VOID_DESTROY_BLOCKS.get()
                    && !blockState.is(Blocks.BEDROCK)
                    && blockState.getDestroySpeed(level, pos) >= 0) {
                // 固体方块 → 销毁（无掉落）
                level.destroyBlock(pos, false);
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
