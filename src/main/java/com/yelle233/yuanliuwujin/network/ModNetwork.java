package com.yelle233.yuanliuwujin.network;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络通道注册（1.20.1 Forge）。
 * <p>
 * 使用 Forge 的 {@link SimpleChannel} 替代 NeoForge 的 PayloadRegistrar。
 */
public class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";

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
    }
}
