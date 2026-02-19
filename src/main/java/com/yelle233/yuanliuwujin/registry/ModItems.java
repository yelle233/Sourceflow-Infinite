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
 * <p>
 * 核心物品各只注册一次，等级通过 DataComponent {@code CORE_LEVEL} 区分。
 * 创造栏（ModTab）应展示 1–4 级的核心（包括超频版本）。
 */
public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(SourceflowInfinite.MODID);

    // ── 无限核心（绑定流体/化学品后产出） ────────────────────────
    public static final DeferredItem<Item> INFINITE_CORE =
            ITEMS.register("infinite_core",
                    () -> new InfiniteCoreItem(new Item.Properties().stacksTo(1)));

    // ── 销毁核心（销毁任意流体，转化为虚空流体） ─────────────────
    public static final DeferredItem<Item> DESTRUCTION_CORE =
            ITEMS.register("destruction_core",
                    () -> new DestructionCoreItem(new Item.Properties().stacksTo(1)));

    // ── 扳手 ──────────────────────────────────────────────────
    public static final DeferredItem<Item> WRENCH =
            ITEMS.register("wrench",
                    () -> new WrenchItem(new Item.Properties().stacksTo(1)));

    /**
     * 虚空流体桶。
     * <p>
     * 玩家可用空桶右键收集地面上的虚空流体，也可将桶内虚空流体倒入管道/储罐，
     * 或用来手动填充机器的虚空储罐（虽然量较少）。
     * <b>警告</b>：将虚空流体倒出到地面将产生有破坏性的虚空流体方块。
     */
    public static final DeferredItem<Item> VOID_BUCKET =
            ITEMS.register("void_bucket",
                    () -> new BucketItem(
                            ModFluids.VOID_FLUID_SOURCE.get(),
                            new Item.Properties()
                                    .stacksTo(1)
                                    .craftRemainder(Items.BUCKET)
                    ));

}
