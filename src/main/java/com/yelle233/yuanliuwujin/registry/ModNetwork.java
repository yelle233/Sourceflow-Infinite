package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.network.FaceRateUpdateMessage;
import com.yelle233.yuanliuwujin.network.WrenchModeScrollMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络通道注册（1.20.1 Forge v2.0 版本）。
 * <p>
 * 使用 Forge 的 {@link SimpleChannel} 替代 NeoForge 的 PayloadRegistrar。
 * 在 {@link SourceflowInfinite} 构造函数中调用 {@link #register()}。
 */
public class ModNetwork {

    private static final String PROTOCOL_VERSION = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private static int nextId() { return packetId++; }

    public static void register() {
        CHANNEL.registerMessage(
                nextId(),
                WrenchModeScrollMessage.class,
                WrenchModeScrollMessage::encode,
                WrenchModeScrollMessage::decode,
                WrenchModeScrollMessage::handle
        );

        CHANNEL.registerMessage(
                nextId(),
                FaceRateUpdateMessage.class,
                FaceRateUpdateMessage::encode,
                FaceRateUpdateMessage::decode,
                FaceRateUpdateMessage::handle
        );
    }
}

