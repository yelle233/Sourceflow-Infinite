package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 创造模式物品栏注册表（1.20.1 Forge v2.0 版本）。
 */
public class ModTab {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SourceflowInfinite.MODID);

    public static final RegistryObject<CreativeModeTab> INFINITE_WATER_TAB =
            TABS.register("infinite_water_tab", () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.INFINITE_CORE_L4.get()))
                    .title(Component.translatable("itemgroup.infinitewater"))
                    .displayItems((parameters, output) -> {
                        // 无限核心 L1-L4 + OC
                        output.accept(ModItems.INFINITE_CORE_L1.get());
                        output.accept(ModItems.INFINITE_CORE_L2.get());
                        output.accept(ModItems.INFINITE_CORE_L3.get());
                        output.accept(ModItems.INFINITE_CORE_L4.get());
                        output.accept(ModItems.INFINITE_CORE_L4_OC.get());
                        // 销毁核心 L1-L4 + OC
                        output.accept(ModItems.DESTRUCTION_CORE_L1.get());
                        output.accept(ModItems.DESTRUCTION_CORE_L2.get());
                        output.accept(ModItems.DESTRUCTION_CORE_L3.get());
                        output.accept(ModItems.DESTRUCTION_CORE_L4.get());
                        output.accept(ModItems.DESTRUCTION_CORE_L4_OC.get());
                        // 机器
                        output.accept(ModBlocks.INFINITE_FLUID_MACHINE.get());
                        output.accept(ModBlocks.DESTRUCTION_MACHINE.get());
                        // 工具
                        output.accept(ModItems.WRENCH.get());
                        output.accept(ModItems.VOID_BUCKET.get());
                    }).build());
}

