package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.block.DestructionMachineBlock;
import com.yelle233.yuanliuwujin.block.InfiniteFluidMachineBlock;
import com.yelle233.yuanliuwujin.block.VoidFluidBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * 方块注册表（1.20.1 Forge 版本，v2.0）。
 */
public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SourceflowInfinite.MODID);

    // ── 无限流体机器 ──
    public static final RegistryObject<Block> INFINITE_FLUID_MACHINE =
            registerBlock("infinite_fluid_machine",
                    () -> new InfiniteFluidMachineBlock(
                            Block.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .strength(3.0F, 6.0F)
                                    .lightLevel(s -> s.getValue(InfiniteFluidMachineBlock.LIT) ? 8 : 0)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()));

    // 原来使用 registerBlock() 会自动注册 BlockItem，改为 BLOCKS.register() 直接注册。
    public static final RegistryObject<Block> INFINITE_CORE_BLOCK =
            BLOCKS.register("infinite_core_block", () -> new Block(Block.Properties.of()));

    // ── 销毁机器 ──
    public static final RegistryObject<Block> DESTRUCTION_MACHINE =
            registerBlock("destruction_machine",
                    () -> new DestructionMachineBlock(
                            Block.Properties.of()
                                    .mapColor(MapColor.DEEPSLATE)
                                    .strength(3.0F, 6.0F)
                                    .lightLevel(s -> s.getValue(DestructionMachineBlock.LIT) ? 8 : 0)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> DESTRUCTION_CORE_BLOCK =
            BLOCKS.register("destruction_core_block", () -> new Block(Block.Properties.of()));

    // ── 虚空流体方块──
    public static final RegistryObject<VoidFluidBlock> VOID_FLUID_BLOCK =
            BLOCKS.register("void_fluid",
                    () -> new VoidFluidBlock(
                            (net.minecraft.world.level.material.FlowingFluid) ModFluids.VOID_FLUID_FLOWING.get(),
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.COLOR_PURPLE)
                                    .replaceable()
                                    .noCollission()
                                    .strength(100.0F)
                                    .pushReaction(PushReaction.DESTROY)
                                    .noLootTable()
                                    .randomTicks()
                                    .sound(SoundType.EMPTY)
                                    .lightLevel(s -> 2)
                    ));

    // ── 辅助方法 ──
    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> supplier) {
        RegistryObject<T> block = BLOCKS.register(name, supplier);
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
}
