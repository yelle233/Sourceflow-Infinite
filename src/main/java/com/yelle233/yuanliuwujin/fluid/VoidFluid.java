package com.yelle233.yuanliuwujin.fluid;

import com.yelle233.yuanliuwujin.block.VoidFluidBlock;
import com.yelle233.yuanliuwujin.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * 虚空流体（Void Fluid）。
 * <p>
 * 虚空流体是两种机器之间的"货币"：
 * <ul>
 *   <li>销毁机器将普通流体转换为虚空流体并储存；底面向外输出虚空流体</li>
 *   <li>无限流体机器从底面吸入虚空流体，将其转换为绑定流体后输出</li>
 * </ul>
 * <p>
 * <b>重要</b>：恩惠期结束后，流体系统的 {@code tick()} 和 {@code spread()} 都会被阻止，
 * 防止原版流体逻辑通过 getNewLiquid() 重新计算流动方块状态，
 * 从而避免源方块不断重新生成已被衰减系统移除的流动方块。
 */
public abstract class VoidFluid extends BaseFlowingFluid {

    protected VoidFluid(Properties properties) {
        super(properties);
    }

    @Override
    public net.neoforged.neoforge.fluids.FluidType getFluidType() {
        return ModFluids.VOID_FLUID_TYPE.get();
    }

    /**
     * 检查指定位置的虚空流体是否仍在恩惠期内。
     * 恩惠期内允许正常流体扩散；恩惠期结束后阻止扩散，让衰减系统接管。
     */
    protected boolean shouldAllowSpread(Level level, BlockPos pos) {
        if (level instanceof ServerLevel sl) {
            return VoidFluidBlock.isBlockInGracePeriod(sl, pos);
        }
        return true;
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

        /**
         * 【核心修复】恩惠期结束后，彻底跳过原版流体 tick 逻辑。
         * <p>
         * 原版 FlowingFluid.tick() 内部会调用 getNewLiquid() 根据相邻方块
         * 重新计算流体状态，这会导致源方块持续"补给"已进入衰减阶段的流动方块。
         * 恩惠期结束后，完全由 VoidFluidBlock.tick() 的衰减系统独立管理。
         */
        @Override
        public void tick(Level level, BlockPos pos, FluidState state) {
            if (!shouldAllowSpread(level, pos)) {
                // 恩惠期结束 → 跳过全部原版流体逻辑（getNewLiquid + spread）
                // VoidFluidBlock.tick() 会独立处理浓度衰减和方块移除
                return;
            }
            super.tick(level, pos, state);
        }

        /**
         * 恩惠期结束后阻止源方块扩散流动方块。
         */
        @Override
        protected void spread(Level level, BlockPos pos, FluidState state) {
            if (!shouldAllowSpread(level, pos)) return;
            super.spread(level, pos, state);
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

        /**
         * 【核心修复】恩惠期结束后，彻底跳过原版流体 tick 逻辑。
         * <p>
         * 对于流动方块尤其关键：原版 tick 中 getNewLiquid() 会检测相邻源方块，
         * 并将流动方块的等级"刷新"回源方块决定的值，导致衰减系统的效果被覆盖。
         * 恩惠期结束后直接 return，让 VoidFluidBlock.tick() 全权负责衰减。
         */
        @Override
        public void tick(Level level, BlockPos pos, FluidState state) {
            if (!shouldAllowSpread(level, pos)) {
                // 恩惠期结束 → 跳过全部原版流体逻辑
                return;
            }
            super.tick(level, pos, state);
        }

        /**
         * 恩惠期结束后也阻止流动方块继续扩散。
         */
        @Override
        protected void spread(Level level, BlockPos pos, FluidState state) {
            if (!shouldAllowSpread(level, pos)) return;
            super.spread(level, pos, state);
        }
    }
}
