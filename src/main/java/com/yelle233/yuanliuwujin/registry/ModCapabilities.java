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

public class ModCapabilities {

    public static void register(RegisterCapabilitiesEvent event) {
        registerInfiniteMachineCapabilities(event);
        registerDestructionMachineCapabilities(event);
    }

    private static void registerInfiniteMachineCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, be, ctx) -> {
            if (!(be instanceof InfiniteFluidMachineBlockEntity m)) return null;
            return (ctx == null || ctx == Direction.UP) ? m.getEnergyStorage() : null;
        }, ModBlocks.INFINITE_FLUID_MACHINE.get());

        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, ctx) -> {
            if (!(be instanceof InfiniteFluidMachineBlockEntity m)) return null;
            if (ctx == Direction.UP) return null;
            if (ctx == Direction.DOWN || ctx == null) return makeVoidAcceptHandler(m);
            if (!m.hasValidBinding()) return null;
            if (m.getCoreBindType() != BindType.FLUID) return null;
            var mode = m.getSideMode(ctx);
            if (mode == InfiniteFluidMachineBlockEntity.SideMode.OFF) return null;
            final Direction faceDir = ctx;
            return new IFluidHandler() {
                @Override public int getTanks() { return m.canWork() ? 1 : 0; }
                @Override public FluidStack getFluidInTank(int t) {
                    var fs = m.extractForSide(1, FluidAction.SIMULATE, faceDir);
                    return fs.isEmpty() ? FluidStack.EMPTY : new FluidStack(fs.getFluid(), Integer.MAX_VALUE);
                }
                @Override public int getTankCapacity(int t) { return Integer.MAX_VALUE; }
                @Override public boolean isFluidValid(int t, FluidStack fs) { return false; }
                @Override public int fill(FluidStack r, FluidAction a) { return 0; }
                @Override public FluidStack drain(int maxDrain, FluidAction action) { return m.extractForSide(maxDrain, action, faceDir); }
                @Override public FluidStack drain(FluidStack res, FluidAction action) {
                    var sim = m.extractForSide(res.getAmount(), FluidAction.SIMULATE, faceDir);
                    if (sim.isEmpty() || !sim.getFluid().isSame(res.getFluid())) return FluidStack.EMPTY;
                    return m.extractForSide(res.getAmount(), action, faceDir);
                }
            };
        }, ModBlocks.INFINITE_FLUID_MACHINE.get());

        if (MekanismChecker.isLoaded()) registerInfiniteChemicalCapability(event);
    }

    private static IFluidHandler makeVoidAcceptHandler(InfiniteFluidMachineBlockEntity m) {
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int t) { return m.getVoidTank().getFluid().copy(); }
            @Override public int getTankCapacity(int t) { return m.getVoidTank().getCapacity(); }
            @Override public boolean isFluidValid(int t, FluidStack fs) { return fs.getFluid().isSame(ModFluids.VOID_FLUID_SOURCE.get()); }
            @Override public int fill(FluidStack resource, FluidAction action) {
                if (!resource.getFluid().isSame(ModFluids.VOID_FLUID_SOURCE.get())) return 0;
                return m.getVoidTank().fill(resource, action);
            }
            @Override public FluidStack drain(int m2, FluidAction a) { return FluidStack.EMPTY; }
            @Override public FluidStack drain(FluidStack r, FluidAction a) { return FluidStack.EMPTY; }
        };
    }

    private static void registerInfiniteChemicalCapability(RegisterCapabilitiesEvent event) {
        event.registerBlock(MekChemicalHelper.CHEMICAL_HANDLER_CAP, (level, pos, state, be, ctx) -> {
            if (!(be instanceof InfiniteFluidMachineBlockEntity m)) return null;
            if (!m.hasValidBinding()) return null;
            if (m.getCoreBindType() != BindType.CHEMICAL) return null;
            if (ctx == null) return (mekanism.api.chemical.IChemicalHandler) m.getInfiniteChemicalOutput();
            if (ctx == Direction.UP || ctx == Direction.DOWN) return null;
            return switch (m.getSideMode(ctx)) {
                case OFF -> null;
                case PULL, BOTH -> (mekanism.api.chemical.IChemicalHandler) m.getInfiniteChemicalOutput();
            };
        }, ModBlocks.INFINITE_FLUID_MACHINE.get());
    }

    private static void registerDestructionMachineCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, be, ctx) -> {
            if (!(be instanceof DestructionMachineBlockEntity m)) return null;
            return (ctx == null || ctx == Direction.UP) ? m.getEnergyStorage() : null;
        }, ModBlocks.DESTRUCTION_MACHINE.get());

        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, ctx) -> {
            if (!(be instanceof DestructionMachineBlockEntity m)) return null;
            if (ctx == Direction.UP) return null;
            if (ctx == Direction.DOWN || ctx == null) return makeVoidOutputHandler(m);
            var mode = m.getSideMode(ctx);
            if (mode == DestructionMachineBlockEntity.SideMode.OFF) return null;
            return m.makeSideSinkHandler(ctx);
        }, ModBlocks.DESTRUCTION_MACHINE.get());

        if (MekanismChecker.isLoaded()) registerDestructionChemicalCapability(event);
    }

    private static IFluidHandler makeVoidOutputHandler(DestructionMachineBlockEntity m) {
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int t) { return m.getVoidTank().getFluid().copy(); }
            @Override public int getTankCapacity(int t) { return m.getVoidTank().getCapacity(); }
            @Override public boolean isFluidValid(int t, FluidStack fs) { return false; }
            @Override public int fill(FluidStack r, FluidAction a) { return 0; }
            @Override public FluidStack drain(int maxDrain, FluidAction action) { return m.getVoidTank().drain(maxDrain, action); }
            @Override public FluidStack drain(FluidStack resource, FluidAction action) { return m.getVoidTank().drain(resource, action); }
        };
    }

    private static void registerDestructionChemicalCapability(RegisterCapabilitiesEvent event) {
        event.registerBlock(MekChemicalHelper.CHEMICAL_HANDLER_CAP, (level, pos, state, be, ctx) -> {
            if (!(be instanceof DestructionMachineBlockEntity m)) return null;
            if (ctx == Direction.UP || ctx == Direction.DOWN) return null;
            var mode = m.getSideMode(ctx);
            if (mode == DestructionMachineBlockEntity.SideMode.OFF) return null;
            return (mekanism.api.chemical.IChemicalHandler) m.getChemSink();
        }, ModBlocks.DESTRUCTION_MACHINE.get());
    }
}
