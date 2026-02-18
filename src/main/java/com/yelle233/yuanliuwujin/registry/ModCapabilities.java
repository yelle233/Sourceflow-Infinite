package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.blockentity.DestructionMachineBlockEntity;
import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import com.yelle233.yuanliuwujin.compat.MekanismChecker;
import com.yelle233.yuanliuwujin.compat.mekanism.MekChemicalHelper;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem.BindType;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * 注册方块的 Capability。
 * <p>
 * 无限流体机器（输出端）：FluidHandler / EnergyStorage / ChemicalHandler
 * 销毁机器（输入端，虚空）：FluidHandler / EnergyStorage / ChemicalHandler
 */
public class ModCapabilities {

    public static void register(RegisterCapabilitiesEvent event) {
        registerInfiniteMachineCapabilities(event);
        registerDestructionMachineCapabilities(event);
    }

    /* ====== 无限流体机器 Capability（原有逻辑，保持不变） ====== */

    private static void registerInfiniteMachineCapabilities(RegisterCapabilitiesEvent event) {

        event.registerBlock(
                Capabilities.FluidHandler.BLOCK,
                (level, pos, state, be, ctx) -> {
                    if (!(be instanceof InfiniteFluidMachineBlockEntity machine)) return null;
                    if (!machine.hasValidBinding()) return null;
                    if (machine.getCoreBindType() != BindType.FLUID) return null;
                    if (ctx == null) return machine.getInfiniteOutput();
                    if (ctx == Direction.UP) return null;
                    return switch (machine.getSideMode(ctx)) {
                        case OFF -> null;
                        case PULL, BOTH -> machine.getInfiniteOutput();
                    };
                },
                ModBlocks.INFINITE_FLUID_MACHINE.get()
        );

        event.registerBlock(
                Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, be, ctx) -> {
                    if (!(be instanceof InfiniteFluidMachineBlockEntity machine)) return null;
                    return (ctx == null || ctx == Direction.UP) ? machine.getEnergyStorage() : null;
                },
                ModBlocks.INFINITE_FLUID_MACHINE.get()
        );

        if (MekanismChecker.isLoaded()) {
            registerInfiniteChemicalCapability(event);
        }
    }

    private static void registerInfiniteChemicalCapability(RegisterCapabilitiesEvent event) {
        event.registerBlock(
                MekChemicalHelper.CHEMICAL_HANDLER_CAP,
                (level, pos, state, be, ctx) -> {
                    if (!(be instanceof InfiniteFluidMachineBlockEntity machine)) return null;
                    if (!machine.hasValidBinding()) return null;
                    if (machine.getCoreBindType() != BindType.CHEMICAL) return null;
                    if (ctx == null) return (IChemicalHandler) machine.getInfiniteChemicalOutput();
                    if (ctx == Direction.UP) return null;
                    return switch (machine.getSideMode(ctx)) {
                        case OFF -> null;
                        case PULL, BOTH -> (IChemicalHandler) machine.getInfiniteChemicalOutput();
                    };
                },
                ModBlocks.INFINITE_FLUID_MACHINE.get()
        );
    }

    /* ====== 销毁机器 Capability（新增） ====== */

    private static void registerDestructionMachineCapabilities(RegisterCapabilitiesEvent event) {

        // ── 流体虚空 Handler：BOTH/PUSH 模式面接受任意流体并销毁 ──
        event.registerBlock(
                Capabilities.FluidHandler.BLOCK,
                (level, pos, state, be, ctx) -> {
                    if (!(be instanceof DestructionMachineBlockEntity machine)) return null;
                    // 无核心时不暴露（机器停机）
                    if (!machine.hasValidBinding()) return null;

                    // ctx == null：Jade 等信息查询，直接返回 handler
                    if (ctx == null) return machine.getVoidSink();
                    // 顶面不接受流体（仅接收能量）
                    if (ctx == Direction.UP) return null;

                    // 仅 BOTH 和 PUSH 模式面暴露流体接收 capability
                    return switch (machine.getSideMode(ctx)) {
                        case OFF    -> null;
                        case BOTH, PUSH -> machine.getVoidSink();
                    };
                },
                ModBlocks.DESTRUCTION_MACHINE.get()
        );

        // ── 能量接收（仅顶面） ──
        event.registerBlock(
                Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, be, ctx) -> {
                    if (!(be instanceof DestructionMachineBlockEntity machine)) return null;
                    return (ctx == null || ctx == Direction.UP) ? machine.getEnergyStorage() : null;
                },
                ModBlocks.DESTRUCTION_MACHINE.get()
        );

        // ── Mekanism 化学品虚空（仅 Mekanism 已加载时注册） ──
        if (MekanismChecker.isLoaded()) {
            registerDestructionChemicalCapability(event);
        }
    }

    /**
     * 为销毁机器注册 Mekanism 化学品虚空 Capability。
     * 单独提取，确保只在 Mekanism 存在时加载引用了 Mekanism API 的代码。
     */
    private static void registerDestructionChemicalCapability(RegisterCapabilitiesEvent event) {
        event.registerBlock(
                MekChemicalHelper.CHEMICAL_HANDLER_CAP,
                (level, pos, state, be, ctx) -> {
                    if (!(be instanceof DestructionMachineBlockEntity machine)) return null;
                    if (!machine.hasValidBinding()) return null;

                    if (ctx == null) return (IChemicalHandler) machine.getChemicalSink();
                    if (ctx == Direction.UP) return null;

                    return switch (machine.getSideMode(ctx)) {
                        case OFF    -> null;
                        case BOTH, PUSH -> (IChemicalHandler) machine.getChemicalSink();
                    };
                },
                ModBlocks.DESTRUCTION_MACHINE.get()
        );
    }
}
