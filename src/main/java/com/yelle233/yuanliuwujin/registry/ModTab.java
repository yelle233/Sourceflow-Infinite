package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** 创造模式物品栏注册表（1.20.1 Forge） */
public class ModTab {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SourceflowInfinite.MODID);

    public static final RegistryObject<CreativeModeTab> INFINITE_WATER_TAB =
            TABS.register("infinite_water_tab", () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.INFINITE_CORE.get()))
                    .title(Component.translatable("itemgroup.infinitewater"))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.INFINITE_CORE.get());
                        output.accept(ModBlocks.INFINITE_FLUID_MACHINE.get());
                        output.accept(ModItems.WRENCH.get());
                    }).build());
}
