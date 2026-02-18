package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.block.DestructionMachineBlock;
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
 */
public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(SourceflowInfinite.MODID);

    /** 无限流体机器方块 */
    public static final DeferredBlock<Block> INFINITE_FLUID_MACHINE =
            registerBlock("infinite_fluid_machine",
                    () -> new InfiniteFluidMachineBlock(
                            Block.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .strength(3.0F, 6.0F)
                                    .lightLevel(state -> state.getValue(InfiniteFluidMachineBlock.LIT) ? 8 : 0)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()
                    ));

    /** 无限核心装饰方块（仅用于 BER 渲染机器内部的旋转核心） */
    public static final DeferredBlock<Block> INFINITE_CORE_BLOCK =
            registerBlock("infinite_core_block",
                    () -> new Block(Block.Properties.of()));

    // ===== 销毁机器（新增） =====

    /**
     * 销毁机器方块。
     * 使用深色地图颜色与无限流体机器区分，同样在插入核心时发光。
     */
    public static final DeferredBlock<Block> DESTRUCTION_MACHINE =
            registerBlock("destruction_machine",
                    () -> new DestructionMachineBlock(
                            Block.Properties.of()
                                    .mapColor(MapColor.DEEPSLATE)  // 深色，视觉上区别于无限流体机器
                                    .strength(3.0F, 6.0F)
                                    .lightLevel(state -> state.getValue(DestructionMachineBlock.LIT) ? 4 : 0)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()
                    ));

    /**
     * 销毁核心装饰方块（仅用于 BER 渲染销毁机器内部的旋转核心）。
     * 不会出现在玩家背包中（通过 registerBlock 自动注册了 BlockItem，
     * 但不会加入创造栏，可在 ModTab 中选择性添加）。
     */
    public static final DeferredBlock<Block> DESTRUCTION_CORE_BLOCK =
            registerBlock("destruction_core_block",
                    () -> new Block(Block.Properties.of()));

    /* ====== 辅助方法：同时注册方块和 BlockItem ====== */

    private static <T extends Block> DeferredBlock<T> registerBlock(
            String name, Supplier<T> blockSupplier) {
        DeferredBlock<T> block = BLOCKS.register(name, blockSupplier);
        ModItems.ITEMS.register(name,
                () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
}
