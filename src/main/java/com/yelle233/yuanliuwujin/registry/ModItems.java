package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.item.DestructionCoreItem;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem;
import com.yelle233.yuanliuwujin.item.WrenchItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 物品注册表。
 * 各等级核心（含超频）均为独立注册物品。
 */
public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(SourceflowInfinite.MODID);

    // ── 无限核心（5 个独立物品） ────────────────────────
    public static final DeferredItem<Item> INFINITE_CORE_L1 = ITEMS.register("infinite_core_l1",
            () -> new InfiniteCoreItem(new Item.Properties().stacksTo(1), 1, false));
    public static final DeferredItem<Item> INFINITE_CORE_L2 = ITEMS.register("infinite_core_l2",
            () -> new InfiniteCoreItem(new Item.Properties().stacksTo(1), 2, false));
    public static final DeferredItem<Item> INFINITE_CORE_L3 = ITEMS.register("infinite_core_l3",
            () -> new InfiniteCoreItem(new Item.Properties().stacksTo(1), 3, false));
    public static final DeferredItem<Item> INFINITE_CORE_L4 = ITEMS.register("infinite_core_l4",
            () -> new InfiniteCoreItem(new Item.Properties().stacksTo(1), 4, false));
    public static final DeferredItem<Item> INFINITE_CORE_L4_OC = ITEMS.register("infinite_core_l4_oc",
            () -> new InfiniteCoreItem(new Item.Properties().stacksTo(1), 4, true));

    // ── 销毁核心（5 个独立物品） ────────────────────────
    public static final DeferredItem<Item> DESTRUCTION_CORE_L1 = ITEMS.register("destruction_core_l1",
            () -> new DestructionCoreItem(new Item.Properties().stacksTo(1), 1, false));
    public static final DeferredItem<Item> DESTRUCTION_CORE_L2 = ITEMS.register("destruction_core_l2",
            () -> new DestructionCoreItem(new Item.Properties().stacksTo(1), 2, false));
    public static final DeferredItem<Item> DESTRUCTION_CORE_L3 = ITEMS.register("destruction_core_l3",
            () -> new DestructionCoreItem(new Item.Properties().stacksTo(1), 3, false));
    public static final DeferredItem<Item> DESTRUCTION_CORE_L4 = ITEMS.register("destruction_core_l4",
            () -> new DestructionCoreItem(new Item.Properties().stacksTo(1), 4, false));
    public static final DeferredItem<Item> DESTRUCTION_CORE_L4_OC = ITEMS.register("destruction_core_l4_oc",
            () -> new DestructionCoreItem(new Item.Properties().stacksTo(1), 4, true));

    // ── 扳手 ──
    public static final DeferredItem<Item> WRENCH = ITEMS.register("wrench",
            () -> new WrenchItem(new Item.Properties().stacksTo(1)));

    // ── 虚空流体桶 ──
    public static final DeferredItem<Item> VOID_BUCKET = ITEMS.register("void_bucket",
            () -> new BucketItem(ModFluids.VOID_FLUID_SOURCE.get(),
                    new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET)));
}
