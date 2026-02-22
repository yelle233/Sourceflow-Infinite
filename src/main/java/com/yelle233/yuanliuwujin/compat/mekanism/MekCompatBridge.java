package com.yelle233.yuanliuwujin.compat.mekanism;

import com.yelle233.yuanliuwujin.blockentity.DestructionMachineBlockEntity;
import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import com.yelle233.yuanliuwujin.registry.ModBlocks;

/**
 * Mekanism 兼容桥接类。
 * <p>
 * <b>关键设计</b>：此类及 compat.mekanism 包下所有类都直接引用 Mekanism API，
 * 因此只能在确认 Mekanism 已加载（{@code MekanismChecker.isLoaded() == true}）后才能访问。
 * <p>
 * 非 Mekanism 兼容层的类（如 ModCapabilities、两个 BlockEntity）不应直接 import 本包下的任何类，
 * 而应通过此桥接类的静态方法间接调用。
 */
public final class MekCompatBridge {

    private MekCompatBridge() {}

    // ════════════════════════════════════════════════════════════════
    //  销毁机器化学品 Sink 创建
    // ════════════════════════════════════════════════════════════════

    /**
     * 创建 DestructionChemicalSink 实例（返回 Object 以避免调用方 import Mek 类）。
     */
    public static Object createDestructionChemicalSink(
            java.util.function.BooleanSupplier canWork,
            java.util.function.Supplier<net.neoforged.neoforge.fluids.capability.templates.FluidTank> voidTankSupplier,
            java.util.function.IntSupplier ratioSupplier,
            java.util.function.IntSupplier budgetSupplier) {
        java.util.function.Supplier<Boolean> canWorkBoxed = () -> canWork.getAsBoolean();
        return new DestructionChemicalSink(canWorkBoxed, voidTankSupplier, ratioSupplier, budgetSupplier);
    }

    // ════════════════════════════════════════════════════════════════
    //  无限流体机器化学品 Output 创建
    // ════════════════════════════════════════════════════════════════

    /**
     * 创建 InfiniteChemicalOutput 实例（返回 Object 以避免调用方 import Mek 类）。
     * chemSupplier 返回 Object（实际是 Chemical），在此处安全转型。
     */
    @SuppressWarnings("unchecked")
    public static Object createInfiniteChemicalOutput(
            java.util.function.Supplier<Object> chemSupplier,
            java.util.function.BooleanSupplier canWork,
            java.util.function.Supplier<net.neoforged.neoforge.fluids.capability.templates.FluidTank> voidTankSupplier,
            java.util.function.IntSupplier ratioSupplier,
            java.util.function.IntSupplier budgetSupplier,
            java.util.function.IntConsumer budgetConsumer) {
        // 将 Supplier<Object> 转为 Supplier<Chemical>
        java.util.function.Supplier<mekanism.api.chemical.Chemical> typedSupplier =
                () -> (mekanism.api.chemical.Chemical) chemSupplier.get();
        java.util.function.Supplier<Boolean> canWorkBoxed = () -> canWork.getAsBoolean();
        return new InfiniteChemicalOutput(typedSupplier, canWorkBoxed, voidTankSupplier, ratioSupplier, budgetSupplier, budgetConsumer);
    }

    // ════════════════════════════════════════════════════════════════
    //  Capability 注册
    // ════════════════════════════════════════════════════════════════

    /**
     * 注册无限流体机器的 Mekanism Chemical Capability。
     * 仅在 Mekanism 已加载时调用。
     */
    public static void registerInfiniteChemicalCapability(RegisterCapabilitiesEvent event) {
        event.registerBlock(MekChemicalHelper.CHEMICAL_HANDLER_CAP, (level, pos, state, be, ctx) -> {
            if (!(be instanceof InfiniteFluidMachineBlockEntity m)) return null;
            if (!m.hasValidBinding()) return null;
            if (m.getCoreBindType() != InfiniteCoreItem.BindType.CHEMICAL) return null;
            if (ctx == null) return (mekanism.api.chemical.IChemicalHandler) m.getInfiniteChemicalOutput();
            if (ctx == Direction.UP || ctx == Direction.DOWN) return null;
            return switch (m.getSideMode(ctx)) {
                case OFF -> null;
                case PULL, BOTH -> (mekanism.api.chemical.IChemicalHandler) m.getInfiniteChemicalOutput();
            };
        }, ModBlocks.INFINITE_FLUID_MACHINE.get());
    }

    /**
     * 注册销毁机器的 Mekanism Chemical Capability。
     * 仅在 Mekanism 已加载时调用。
     */
    public static void registerDestructionChemicalCapability(RegisterCapabilitiesEvent event) {
        event.registerBlock(MekChemicalHelper.CHEMICAL_HANDLER_CAP, (level, pos, state, be, ctx) -> {
            if (!(be instanceof DestructionMachineBlockEntity m)) return null;
            if (ctx == Direction.UP || ctx == Direction.DOWN) return null;
            var mode = m.getSideMode(ctx);
            if (mode == DestructionMachineBlockEntity.SideMode.OFF) return null;
            return (mekanism.api.chemical.IChemicalHandler) m.getChemSink();
        }, ModBlocks.DESTRUCTION_MACHINE.get());
    }

    // ════════════════════════════════════════════════════════════════
    //  化学品抽取 / 推送（供 BlockEntity.serverTick 调用）
    // ════════════════════════════════════════════════════════════════

    /**
     * 从相邻方块抽取化学品并转换为虚空流体（销毁机器 BOTH 模式）。
     */
    public static void pullChemicalFromNeighbor(
            net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
            Direction dir, int tickBudget, int ratio,
            net.neoforged.neoforge.fluids.capability.templates.FluidTank voidTank) {
        int voidSpace = voidTank.getCapacity() - voidTank.getFluidAmount();
        long maxChem = Math.min(tickBudget, (long) voidSpace * ratio);
        if (maxChem <= 0) return;

        long drained = MekChemicalHelper.drainAndDestroyChemical(level, pos, dir, maxChem);
        if (drained > 0) {
            int voidProduced = (int) Math.max(1, drained / ratio);
            voidTank.fill(
                    new net.neoforged.neoforge.fluids.FluidStack(
                            com.yelle233.yuanliuwujin.registry.ModFluids.VOID_FLUID_SOURCE.get(), voidProduced),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        }
    }

    /**
     * 向相邻方块推送化学品（无限流体机器 BOTH 模式）。
     * @return 实际推送的化学品量
     */
    public static long pushChemicalToNeighbor(
            net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
            Direction dir, ResourceLocation chemId, int amount) {
        return MekChemicalHelper.pushChemicalWithLimit(level, pos, dir, chemId, amount);
    }
}
