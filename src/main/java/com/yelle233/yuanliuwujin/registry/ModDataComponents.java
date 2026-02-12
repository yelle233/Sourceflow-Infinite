package com.yelle233.yuanliuwujin.registry;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 注册自定义 Data Component 类型。
 * <p>
 * Data Component 是 1.20.5+ 替代旧版 NBT 的物品数据存储方式。
 * 每个 Component 需要同时提供持久化编解码器（persistent）和网络编解码器（networkSynchronized）。
 */
public class ModDataComponents {

    public static final DeferredRegister.DataComponents REGISTRAR =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, "yuanliuwujin");

    /**
     * 无限核心绑定的液体 ID。
     * <p>
     * 使用 {@link ResourceLocation} 存储，例如 "minecraft:water"。
     * 同时注册了持久化和网络同步编解码器，确保客户端能正确显示绑定状态。
     */
    public static final Supplier<DataComponentType<ResourceLocation>> BOUND_FLUID =
            REGISTRAR.registerComponentType("bound_fluid",
                    builder -> builder
                            .persistent(ResourceLocation.CODEC)
                            .networkSynchronized(ResourceLocation.STREAM_CODEC)
            );
}
