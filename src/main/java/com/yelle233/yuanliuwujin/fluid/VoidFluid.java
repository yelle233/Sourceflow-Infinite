package com.yelle233.yuanliuwujin.fluid;

import com.yelle233.yuanliuwujin.registry.ModFluids;
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
 * 流体本身可被管道、容器等第三方模组的流体存储设备正常储存。
 * 当虚空流体泄漏到世界（如机器爆炸），将以 {@link com.yelle233.yuanliuwujin.block.VoidFluidBlock}
 * 形式存在，并逐渐消散，同时销毁接触到的方块和实体。
 */
public abstract class VoidFluid extends BaseFlowingFluid {

    protected VoidFluid(Properties properties) {
        super(properties);
    }

    @Override
    public net.neoforged.neoforge.fluids.FluidType getFluidType() {
        return ModFluids.VOID_FLUID_TYPE.get();
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
        protected boolean canConvertToSource(net.minecraft.world.level.Level level) {
            return false; // 禁止从相邻两块创造源，防止无限复制
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
        protected boolean canConvertToSource(net.minecraft.world.level.Level level) {
            return false;
        }
    }
}
