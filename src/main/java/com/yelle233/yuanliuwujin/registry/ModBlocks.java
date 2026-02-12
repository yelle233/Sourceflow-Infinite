package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.block.InfiniteFluidMachineBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 方块注册表。
 * <p>
 * 使用 {@link #registerBlock} 辅助方法同时注册方块及其对应的 BlockItem。
 */
public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SourceflowInfinite.MODID);

    /** 无限流体机器方块 */
    public static final DeferredBlock<Block> INFINITE_FLUID_MACHINE =
            registerBlock("infinite_fluid_machine",
                    () -> new InfiniteFluidMachineBlock(
                            Block.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .strength(3.0F, 6.0F)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()
                    ));

    /** 无限核心装饰方块（仅用于 BER 渲染机器内部的旋转核心） */
    public static final DeferredBlock<Block> INFINITE_CORE_BLOCK =
            registerBlock("infinite_core_block",
                    () -> new Block(Block.Properties.of()));

    /* ====== 辅助方法：同时注册方块和 BlockItem ====== */

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> blockSupplier) {
        DeferredBlock<T> block = BLOCKS.register(name, blockSupplier);
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
}
