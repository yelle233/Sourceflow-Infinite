package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem;
import com.yelle233.yuanliuwujin.item.WrenchItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SourceflowInfinite.MODID);

    public static final DeferredItem<Item> INFINITE_CORE =
            ITEMS.register("infinite_core", () -> new InfiniteCoreItem(new Item.Properties()));

    public static final DeferredItem<Item> WRENCH =
            ITEMS.register("wrench", () -> new WrenchItem(new Item.Properties().stacksTo(1)));

}
