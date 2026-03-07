package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.block.DestructionMachineBlock;
import com.yelle233.yuanliuwujin.block.InfiniteFluidMachineBlock;
import com.yelle233.yuanliuwujin.block.VoidFluidBlock;
import com.yelle233.yuanliuwujin.block.VoidGeneratorBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
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

    // ── 无限流体机器 ──────────────────────────────────────────
    public static final DeferredBlock<Block> INFINITE_FLUID_MACHINE =
            registerBlock("infinite_fluid_machine",
                    () -> new InfiniteFluidMachineBlock(
                            Block.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .strength(3.0F, 6.0F)
                                    .lightLevel(s -> s.getValue(InfiniteFluidMachineBlock.LIT) ? 8 : 0)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()));

    // 原来使用 registerBlock() 会自动注册 BlockItem，改为 BLOCKS.register() 直接注册。
    public static final DeferredBlock<Block> INFINITE_CORE_BLOCK =
            BLOCKS.register("infinite_core_block", () -> new Block(Block.Properties.of()));

    // ── 销毁机器 ──────────────────────────────────────────────
    public static final DeferredBlock<Block> DESTRUCTION_MACHINE =
            registerBlock("destruction_machine",
                    () -> new DestructionMachineBlock(
                            Block.Properties.of()
                                    .mapColor(MapColor.DEEPSLATE)
                                    .strength(3.0F, 6.0F)
                                    .lightLevel(s -> s.getValue(DestructionMachineBlock.LIT) ? 8 : 0)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> DESTRUCTION_CORE_BLOCK =
            BLOCKS.register("destruction_core_block", () -> new Block(Block.Properties.of()));

    // ── 虚空发电机 ──────────────────────────────────────────────
    public static final DeferredBlock<Block> VOID_GENERATOR =
            registerBlock("void_generator",
                    () -> new VoidGeneratorBlock(
                            Block.Properties.of()
                                    .mapColor(MapColor.COLOR_PURPLE)
                                    .strength(3.0F, 6.0F)
                                    .lightLevel(s -> s.getValue(VoidGeneratorBlock.LIT) ? 10 : 0)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()));

    // ── 虚空流体方块 ──────────────────────────────────────────
    /**
     * 虚空流体的世界方块表现形式。
     * <p>
     * <b>注意</b>：此方块不应出现在创造栏，仅在机器爆炸时由代码生成。
     * 玩家可用桶收集虚空流体（会填充 VOID_BUCKET），或让其自然消散。
     */
    public static final DeferredBlock<VoidFluidBlock> VOID_FLUID_BLOCK =
            BLOCKS.register("void_fluid",
                    () -> new VoidFluidBlock(
                            (net.minecraft.world.level.material.FlowingFluid) ModFluids.VOID_FLUID_FLOWING.get(),
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_PURPLE)
                                    .replaceable()
                                    .noCollission()
                                    .strength(100.0F)
                                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                                    .noLootTable()
                                    .randomTicks()
                                    .sound(SoundType.EMPTY)
                                    .lightLevel(s -> 2)
                    ));


    // 注意：VOID_FLUID_BLOCK 不注册 BlockItem，玩家不能在背包中持有方块形式

    // ── 强化虚空方块 ──────────────────────────────────────────
    /**
     * 强化虚空方块 - 不会被虚空流体吞噬的建筑方块。
     * <p>
     * 硬度比黑曜石高，比基岩低，需要钻石镐开采。
     */
    public static final DeferredBlock<Block> REINFORCED_VOID_BLOCK =
            registerBlock("reinforced_void_block",
                    () -> new Block(
                            Block.Properties.of()
                                    .mapColor(MapColor.COLOR_BLACK)
                                    .strength(100.0F, 2400.0F) // 硬度100（黑曜石50），爆炸抗性2400（黑曜石1200，基岩3600000）
                                    .requiresCorrectToolForDrops()
                                    .sound(SoundType.STONE)
                    ));

    // ── 辅助方法 ──────────────────────────────────────────────

    private static <T extends Block> DeferredBlock<T> registerBlock(
            String name, Supplier<T> supplier) {
        DeferredBlock<T> block = BLOCKS.register(name, supplier);
        ModItems.ITEMS.register(name,
                () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
}
