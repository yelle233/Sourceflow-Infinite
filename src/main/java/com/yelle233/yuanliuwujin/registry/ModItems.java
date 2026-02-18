package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.item.DestructionCoreItem;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem;
import com.yelle233.yuanliuwujin.item.WrenchItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 物品注册表 */
public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(SourceflowInfinite.MODID);

    /** 无限核心：绑定液体后放入无限流体机器以产出液体 */
    public static final DeferredItem<Item> INFINITE_CORE =
            ITEMS.register("infinite_core",
                    () -> new InfiniteCoreItem(new Item.Properties().stacksTo(1)));

    /**
     * 销毁核心：放入销毁机器后，机器可接收并虚空销毁任意流体/化学品。
     * 无需绑定特定流体，通用虚空核心。
     */
    public static final DeferredItem<Item> DESTRUCTION_CORE =
            ITEMS.register("destruction_core",
                    () -> new DestructionCoreItem(new Item.Properties().stacksTo(1)));

    /** 扳手：操作无限流体机器和销毁机器（插入核心、配置面模式） */
    public static final DeferredItem<Item> WRENCH =
            ITEMS.register("wrench",
                    () -> new WrenchItem(new Item.Properties().stacksTo(1)));
}
