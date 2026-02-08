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

                    // 顶面不给出液能力（顶面留给 FE/应力）
                    if (ctx == Direction.UP) return null;

                    // ctx == null：无方向访问（有些管道/设备会这样）
                    // ——策略 1：兼容优先（推荐）：只要“存在任一可抽的侧面”，就返回能力
                    if (ctx == null) {
                        for (Direction d : Direction.values()) {
                            if (d == Direction.UP) continue;
                            var mode = machine.getSideMode(d);
                            if (mode == InfiniteFluidMachineBlockEntity.SideMode.PULL
                                    || mode == InfiniteFluidMachineBlockEntity.SideMode.BOTH) {
                                return machine.getInfiniteOutput();
                            }
                        }
                        return null;
                    }

                    // 正常情况：按访问的面返回能力
                    return switch (machine.getSideMode(ctx)) {
                        case OFF -> null;
                        case PULL, BOTH -> machine.getInfiniteOutput();
                    };
                },
                ModBlocks.INFINITE_FLUID_MACHINE.get()
        );
    }


}


