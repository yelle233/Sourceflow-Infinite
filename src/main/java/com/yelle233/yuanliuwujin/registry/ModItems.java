package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem;
import com.yelle233.yuanliuwujin.item.WrenchItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 物品注册表（1.20.1 Forge） */
public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SourceflowInfinite.MODID);

    /** 无限核心 */
    public static final RegistryObject<Item> INFINITE_CORE =
            ITEMS.register("infinite_core", () -> new InfiniteCoreItem(new Item.Properties().stacksTo(1)));

    /** 扳手 */
    public static final RegistryObject<Item> WRENCH =
            ITEMS.register("wrench", () -> new WrenchItem(new Item.Properties().stacksTo(1)));
}
