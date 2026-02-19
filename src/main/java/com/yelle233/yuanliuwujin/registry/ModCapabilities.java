package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.blockentity.DestructionMachineBlockEntity;
import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import com.yelle233.yuanliuwujin.compat.MekanismChecker;
import com.yelle233.yuanliuwujin.compat.mekanism.MekChemicalHelper;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem.BindType;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Capability 注册。
 * <p>
 * <b>无限流体机器：</b>
 * <ul>
 *   <li>TOP（UP）：Energy IN</li>
 *   <li>BOTTOM（DOWN）：Void Fluid IN（接受虚空流体推入 / 主动吸取）</li>
 *   <li>四个侧面（OFF/PULL/BOTH）：绑定流体 OUT</li>
 * </ul>
 * <b>销毁机器：</b>
 * <ul>
 *   <li>TOP（UP）：Energy IN</li>
 *   <li>BOTTOM（DOWN）：Void Fluid OUT（输出内部虚空流体）</li>
 *   <li>四个侧面（OFF/PUSH/BOTH）：任意流体 IN（虚空 Sink）</li>
 * </ul>
 */
public class ModCapabilities {

    public static void register(RegisterCapabilitiesEvent event) {
        registerInfiniteMachineCapabilities(event);
        registerDestructionMachineCapabilities(event);
    }

    // ════════════════════════════════════════════════════════════
    //  无限流体机器
    // ════════════════════════════════════════════════════════════

    private static void registerInfiniteMachineCapabilities(RegisterCapabilitiesEvent event) {

        // ── 能量（仅顶面） ──────────────────────────────────────
        event.registerBlock(
                Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, be, ctx) -> {
                    if (!(be instanceof InfiniteFluidMachineBlockEntity m)) return null;
                    return (ctx == null || ctx == Direction.UP) ? m.getEnergyStorage() : null;
                },
                ModBlocks.INFINITE_FLUID_MACHINE.get());

        // ── 底面：接受虚空流体推入 ────────────────────────────────
        event.registerBlock(
                Capabilities.FluidHandler.BLOCK,
                (level, pos, state, be, ctx) -> {
                    if (!(be instanceof InfiniteFluidMachineBlockEntity m)) return null;
                    if (ctx == Direction.UP) return null;

                    // 底面：暴露虚空流体储罐（可接受外部推入虚空流体）
                    if (ctx == Direction.DOWN || ctx == null) {
                        return makeVoidAcceptHandler(m);
                    }

                    // 侧面：输出绑定流体
                    if (!m.hasValidBinding()) return null;
                    if (m.getCoreBindType() != BindType.FLUID) return null;
                    var mode = m.getSideMode(ctx);
                    if (mode == InfiniteFluidMachineBlockEntity.SideMode.OFF) return null;

                    final Direction faceDir = ctx;
                    return new IFluidHandler() {
                        @Override public int getTanks() { return m.canWork() ? 1 : 0; }
                        @Override public FluidStack getFluidInTank(int t) {
                            var fs = m.extractForSide(1, IFluidHandler.FluidAction.SIMULATE, faceDir);
                            return fs.isEmpty() ? FluidStack.EMPTY
                                    : new FluidStack(fs.getFluid(), Integer.MAX_VALUE);
                        }
                        @Override public int getTankCapacity(int t) { return Integer.MAX_VALUE; }
                        @Override public boolean isFluidValid(int t, FluidStack fs) { return false; }
                        @Override public int fill(FluidStack r, FluidAction a) { return 0; }
                        @Override
                        public FluidStack drain(int maxDrain, FluidAction action) {
                            return m.extractForSide(maxDrain, action, faceDir);
                        }
                        @Override
                        public FluidStack drain(FluidStack res, FluidAction action) {
                            var sim = m.extractForSide(res.getAmount(), FluidAction.SIMULATE, faceDir);
                            if (sim.isEmpty() || !sim.getFluid().isSame(res.getFluid()))
                                return FluidStack.EMPTY;
                            return m.extractForSide(res.getAmount(), action, faceDir);
                        }
                    };
                },
                ModBlocks.INFINITE_FLUID_MACHINE.get());

        // ── Mekanism 化学品输出（侧面） ──────────────────────────
        if (MekanismChecker.isLoaded()) {
            registerInfiniteChemicalCapability(event);
        }
    }

    /** 创建虚空流体接收 Handler（供无限机器底面使用） */
    private static IFluidHandler makeVoidAcceptHandler(InfiniteFluidMachineBlockEntity m) {
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int t) { return m.getVoidTank().getFluid().copy(); }
            @Override public int getTankCapacity(int t) { return m.getVoidTank().getCapacity(); }
            @Override public boolean isFluidValid(int t, FluidStack fs) {
                return fs.getFluid().isSame(ModFluids.VOID_FLUID_SOURCE.get());
            }
            @Override
            public int fill(FluidStack resource, FluidAction action) {
                if (!resource.getFluid().isSame(ModFluids.VOID_FLUID_SOURCE.get())) return 0;
                return m.getVoidTank().fill(resource, action);
            }
            @Override public FluidStack drain(int m2, FluidAction a) { return FluidStack.EMPTY; }
            @Override public FluidStack drain(FluidStack r, FluidAction a) { return FluidStack.EMPTY; }
        };
    }

    private static void registerInfiniteChemicalCapability(RegisterCapabilitiesEvent event) {
        event.registerBlock(
                MekChemicalHelper.CHEMICAL_HANDLER_CAP,
                (level, pos, state, be, ctx) -> {
                    if (!(be instanceof InfiniteFluidMachineBlockEntity m)) return null;
                    if (!m.hasValidBinding()) return null;
                    if (m.getCoreBindType() != BindType.CHEMICAL) return null;
                    if (ctx == null) return (mekanism.api.chemical.IChemicalHandler) m.getInfiniteChemicalOutput();
                    if (ctx == Direction.UP || ctx == Direction.DOWN) return null;
                    return switch (m.getSideMode(ctx)) {
                        case OFF -> null;
                        case PULL, BOTH -> (mekanism.api.chemical.IChemicalHandler) m.getInfiniteChemicalOutput();
                    };
                },
                ModBlocks.INFINITE_FLUID_MACHINE.get());
    }

    // ════════════════════════════════════════════════════════════
    //  销毁机器
    // ════════════════════════════════════════════════════════════

    private static void registerDestructionMachineCapabilities(RegisterCapabilitiesEvent event) {

        // ── 能量（仅顶面） ──────────────────────────────────────
        event.registerBlock(
                Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, be, ctx) -> {
                    if (!(be instanceof DestructionMachineBlockEntity m)) return null;
                    return (ctx == null || ctx == Direction.UP) ? m.getEnergyStorage() : null;
                },
                ModBlocks.DESTRUCTION_MACHINE.get());

        // ── 底面：输出虚空流体 ────────────────────────────────────
        event.registerBlock(
                Capabilities.FluidHandler.BLOCK,
                (level, pos, state, be, ctx) -> {
                    if (!(be instanceof DestructionMachineBlockEntity m)) return null;
                    if (ctx == Direction.UP) return null;

                    // 底面：输出虚空流体储罐中的内容
                    if (ctx == Direction.DOWN || ctx == null) {
                        return makeVoidOutputHandler(m);
                    }

                    // 侧面：接受任意流体（虚空 Sink）
                    var mode = m.getSideMode(ctx);
                    if (mode == DestructionMachineBlockEntity.SideMode.OFF) return null;
                    return m.makeSideSinkHandler(ctx);
                },
                ModBlocks.DESTRUCTION_MACHINE.get());

        // ── Mekanism 化学品 Sink（侧面） ─────────────────────────
        if (MekanismChecker.isLoaded()) {
            registerDestructionChemicalCapability(event);
        }
    }

    /** 创建虚空流体输出 Handler（供销毁机器底面使用） */
    private static IFluidHandler makeVoidOutputHandler(DestructionMachineBlockEntity m) {
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int t) { return m.getVoidTank().getFluid().copy(); }
            @Override public int getTankCapacity(int t) { return m.getVoidTank().getCapacity(); }
            @Override public boolean isFluidValid(int t, FluidStack fs) { return false; }
            @Override public int fill(FluidStack r, FluidAction a) { return 0; }
            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                return m.getVoidTank().drain(maxDrain, action);
            }
            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                return m.getVoidTank().drain(resource, action);
            }
        };
    }

    private static void registerDestructionChemicalCapability(RegisterCapabilitiesEvent event) {
        event.registerBlock(
                MekChemicalHelper.CHEMICAL_HANDLER_CAP,
                (level, pos, state, be, ctx) -> {
                    if (!(be instanceof DestructionMachineBlockEntity m)) return null;
                    if (ctx == Direction.UP || ctx == Direction.DOWN) return null;
                    var mode = m.getSideMode(ctx);
                    if (mode == DestructionMachineBlockEntity.SideMode.OFF) return null;
                    return (mekanism.api.chemical.IChemicalHandler) m.getChemSink();
                },
                ModBlocks.DESTRUCTION_MACHINE.get());
    }
}
