package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** 方块实体类型注册表 */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SourceflowInfinite.MODID);

    /** 无限流体机器的方块实体类型 */
    public static final Supplier<BlockEntityType<InfiniteFluidMachineBlockEntity>> INFINITE_FLUID_MACHINE =
            BLOCK_ENTITIES.register("infinite_fluid_machine", () ->
                    BlockEntityType.Builder.of(
                            InfiniteFluidMachineBlockEntity::new,
                            ModBlocks.INFINITE_FLUID_MACHINE.get()
                    ).build(null));
}
