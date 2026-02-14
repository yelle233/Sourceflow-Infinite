package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.block.InfiniteFluidMachineBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/** 方块注册表（1.20.1 Forge） */
public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SourceflowInfinite.MODID);

    /** 无限流体机器方块 */
    public static final RegistryObject<Block> INFINITE_FLUID_MACHINE =
            registerBlock("infinite_fluid_machine",
                    () -> new InfiniteFluidMachineBlock(
                            Block.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .strength(3.0F, 6.0F)
                                    .lightLevel(state -> state.getValue(InfiniteFluidMachineBlock.LIT) ? 8 : 0)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()
                    ));

    /** 无限核心装饰方块（仅用于 BER 渲染） */
    public static final RegistryObject<Block> INFINITE_CORE_BLOCK =
            registerBlock("infinite_core_block",
                    () -> new Block(Block.Properties.of()));

    /* ====== 辅助方法 ====== */

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> blockSupplier) {
        RegistryObject<T> block = BLOCKS.register(name, blockSupplier);
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
}
