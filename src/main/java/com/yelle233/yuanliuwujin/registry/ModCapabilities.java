package com.yelle233.yuanliuwujin.registry;

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
 * 无限流体机器暴露的能力：
 * <ul>
 *   <li><b>FluidHandler</b>（五个侧面）：绑定流体时提供无限流体输出</li>
 *   <li><b>EnergyStorage</b>（仅顶面）：接收 FE 能量输入</li>
 *   <li><b>ChemicalHandler</b>（五个侧面，仅 Mekanism 存在时）：绑定化学品时提供无限化学品输出</li>
 * </ul>
 */
public class ModCapabilities {

    public static void register(RegisterCapabilitiesEvent event) {

        // ===== 流体能力：仅在 PULL/BOTH + 绑定流体 + 有电时暴露 =====
        event.registerBlock(
                Capabilities.FluidHandler.BLOCK,
                (level, pos, state, be, ctx) -> {
                    if (!(be instanceof InfiniteFluidMachineBlockEntity machine)) return null;
                    if (ctx == null || ctx == Direction.UP) return null;
                    if (!machine.canWorkNow()) return null;
                    // 仅当绑定的是流体时才暴露流体 Handler
                    if (machine.getCoreBindType() != BindType.FLUID) return null;

                    return switch (machine.getSideMode(ctx)) {
                        case OFF -> null;
                        case PULL, BOTH -> machine.getInfiniteOutput();
                    };
                },
                ModBlocks.INFINITE_FLUID_MACHINE.get()
        );

        // ===== 能量能力：仅从顶面接收 =====
        event.registerBlock(
                Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, be, ctx) -> {
                    if (!(be instanceof InfiniteFluidMachineBlockEntity machine)) return null;
                    return ctx == Direction.UP ? machine.getEnergyStorage() : null;
                },
                ModBlocks.INFINITE_FLUID_MACHINE.get()
        );

        // ===== Mekanism 化学品能力（仅 Mekanism 已加载时注册） =====
        if (MekanismChecker.isLoaded()) {
            registerChemicalCapability(event);
        }
    }

    /**
     * 注册 Mekanism 化学品 Capability。
     * <p>
     * 此方法单独提取，确保只有 Mekanism 存在时才加载引用了 Mekanism API 的代码。
     */
    private static void registerChemicalCapability(RegisterCapabilitiesEvent event) {
        event.registerBlock(
                MekChemicalHelper.CHEMICAL_HANDLER_CAP,
                (level, pos, state, be, ctx) -> {
                    if (!(be instanceof InfiniteFluidMachineBlockEntity machine)) return null;
                    if (ctx == null || ctx == Direction.UP) return null;
                    if (!machine.canWorkNow()) return null;
                    // 仅当绑定的是化学品时才暴露化学品 Handler
                    if (machine.getCoreBindType() != BindType.CHEMICAL) return null;

                    return switch (machine.getSideMode(ctx)) {
                        case OFF -> null;
                        case PULL, BOTH -> (IChemicalHandler) machine.getInfiniteChemicalOutput();
                    };
                },
                ModBlocks.INFINITE_FLUID_MACHINE.get()
        );
    }
}
