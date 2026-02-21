package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * 模组物品 Tag 定义。
 * <p>
 * 在配方、合成表或其他系统中引用所有等级的核心时，推荐使用这里的 TagKey，
 * 而非逐一引用各个物品，方便后续扩展。
 *
 * <p>对应的 JSON 数据文件位于：
 * {@code data/yuanliuwujin/tags/item/infinite_cores.json}
 * {@code data/yuanliuwujin/tags/item/destruction_cores.json}
 */
public final class ModTags {

    private ModTags() {}

    public static final class Items {

        /**
         * 所有等级的无限核心（L1、L2、L3、L4、L4超频）。
         * <p>
         * Tag ID: {@code yuanliuwujin:infinite_cores}
         */
        public static final TagKey<Item> INFINITE_CORES = tag("infinite_cores");

        /**
         * 所有等级的销毁核心（L1、L2、L3、L4、L4超频）。
         * <p>
         * Tag ID: {@code yuanliuwujin:destruction_cores}
         */
        public static final TagKey<Item> DESTRUCTION_CORES = tag("destruction_cores");

        private static TagKey<Item> tag(String name) {
            return TagKey.create(Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, name));
        }
    }
}
