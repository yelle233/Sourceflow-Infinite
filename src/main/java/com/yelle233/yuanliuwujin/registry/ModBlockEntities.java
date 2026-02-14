package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 方块实体类型注册表（1.20.1 Forge） */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SourceflowInfinite.MODID);

    public static final RegistryObject<BlockEntityType<InfiniteFluidMachineBlockEntity>> INFINITE_FLUID_MACHINE =
            BLOCK_ENTITIES.register("infinite_fluid_machine", () ->
                    BlockEntityType.Builder.of(
                            InfiniteFluidMachineBlockEntity::new,
                            ModBlocks.INFINITE_FLUID_MACHINE.get()
                    ).build(null));
}
