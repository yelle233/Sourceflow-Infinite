package com.yelle233.yuanliuwujin.registry;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public class ModCapabilities {
    // 这个方法会在 modEventBus 上被调用
    public static void register(RegisterCapabilitiesEvent event) {

        // 给“无限液体机器”的 BlockEntity 注册流体能力（让管道能抽）
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.INFINITE_FLUID_MACHINE.get(),
                (be, side) -> be.getInfiniteOutput()  // 返回无限输出的 FluidHandler
        );

        // （可选：后面我们做自动化/GUI时再开）
        // event.registerBlockEntity(
        //         Capabilities.ItemHandler.BLOCK,
        //         ModBlockEntities.INFINITE_FLUID_MACHINE.get(),
        //         (be, side) -> be.getCoreSlot()
        // );
    }

}
