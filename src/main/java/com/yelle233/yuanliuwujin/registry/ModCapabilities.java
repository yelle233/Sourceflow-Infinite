package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.core.Direction;
import javax.annotation.Nullable;

public class ModCapabilities {
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlock(
                Capabilities.FluidHandler.BLOCK,
                (level, pos, state, be, ctx) -> {
                    if (!(be instanceof InfiniteFluidMachineBlockEntity machine)) return null;

                    if (ctx == null) return null;           // ✅避免绕过面
                    if (ctx == Direction.UP) return null;   // 顶面不给

                    // ✅没电/不工作：不给抽
                    if (!machine.canWorkNow()) return null;

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
                    return (ctx == Direction.UP) ? machine.getEnergyStorage() : null;
                },
                ModBlocks.INFINITE_FLUID_MACHINE.get()
        );
    }


}


