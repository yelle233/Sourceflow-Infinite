package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem;
import com.yelle233.yuanliuwujin.item.WrenchItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 物品注册表 */
public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SourceflowInfinite.MODID);

    /** 无限核心：绑定液体后放入机器以产出液体 */
    public static final DeferredItem<Item> INFINITE_CORE =
            ITEMS.register("infinite_core", () -> new InfiniteCoreItem(new Item.Properties().stacksTo(1)));

    /** 扳手：操作无限流体机器（插入核心、配置面模式） */
    public static final DeferredItem<Item> WRENCH =
            ITEMS.register("wrench", () -> new WrenchItem(new Item.Properties().stacksTo(1)));
}
