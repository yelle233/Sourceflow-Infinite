package com.yelle233.yuanliuwujin.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 注册自定义 Data Component 类型。
 * <p>
 * 新增：
 * <ul>
 *   <li>{@link #CORE_LEVEL} – 核心等级（int，1–4）</li>
 *   <li>{@link #IS_OVERCLOCKED} – 是否已超频（boolean）</li>
 * </ul>
 */
public class ModDataComponents {

    public static final DeferredRegister.DataComponents REGISTRAR =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, "yuanliuwujin");

    /** 无限核心绑定的流体 ID */
    public static final Supplier<DataComponentType<ResourceLocation>> BOUND_FLUID =
            REGISTRAR.registerComponentType("bound_fluid",
                    builder -> builder
                            .persistent(ResourceLocation.CODEC)
                            .networkSynchronized(ResourceLocation.STREAM_CODEC));

    /** 无限核心绑定的 Mekanism 化学品 ID */
    public static final Supplier<DataComponentType<ResourceLocation>> BOUND_CHEMICAL =
            REGISTRAR.registerComponentType("bound_chemical",
                    builder -> builder
                            .persistent(ResourceLocation.CODEC)
                            .networkSynchronized(ResourceLocation.STREAM_CODEC));

    /**
     * 核心等级（1–4）。两种核心共用此组件。
     * 未设置时默认视为等级 1。
     */
    public static final Supplier<DataComponentType<Integer>> CORE_LEVEL =
            REGISTRAR.registerComponentType("core_level",
                    builder -> builder
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT));

    /**
     * 是否已超频。只有等级 4 的核心才能超频。
     * 超频后比例达到配置中的超频值（默认 1:1），但会积累压力导致爆炸风险。
     */
    public static final Supplier<DataComponentType<Boolean>> IS_OVERCLOCKED =
            REGISTRAR.registerComponentType("is_overclocked",
                    builder -> builder
                            .persistent(Codec.BOOL)
                            .networkSynchronized(ByteBufCodecs.BOOL));
}
