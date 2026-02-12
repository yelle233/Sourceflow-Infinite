package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * 注册方块的 Capability（NeoForge 能力系统）。
 * <p>
 * 无限流体机器暴露两种能力：
 * <ul>
 *   <li><b>FluidHandler</b>（五个侧面，不含顶面）：根据面模式返回无限流体输出</li>
 *   <li><b>EnergyStorage</b>（仅顶面）：接收 FE 能量输入</li>
 * </ul>
 */
public class ModCapabilities {

    public static void register(RegisterCapabilitiesEvent event) {

        // 流体能力：仅在 PULL/BOTH 模式 + 有电时暴露
        event.registerBlock(
                Capabilities.FluidHandler.BLOCK,
                (level, pos, state, be, ctx) -> {
                    if (!(be instanceof InfiniteFluidMachineBlockEntity machine)) return null;
                    if (ctx == null || ctx == Direction.UP) return null;
                    if (!machine.canWorkNow()) return null;

                    return switch (machine.getSideMode(ctx)) {
                        case OFF -> null;
                        case PULL, BOTH -> machine.getInfiniteOutput();
                    };
                },
                ModBlocks.INFINITE_FLUID_MACHINE.get()
        );

        // 能量能力：仅从顶面接收
        event.registerBlock(
                Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, be, ctx) -> {
                    if (!(be instanceof InfiniteFluidMachineBlockEntity machine)) return null;
                    return ctx == Direction.UP ? machine.getEnergyStorage() : null;
                },
                ModBlocks.INFINITE_FLUID_MACHINE.get()
        );
    }
}
