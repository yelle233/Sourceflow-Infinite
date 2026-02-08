package com.yelle233.yuanliuwujin.network;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WrenchModeScrollPayload(int delta) implements CustomPacketPayload {
    public static final Type<WrenchModeScrollPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, "wrench_mode_scroll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WrenchModeScrollPayload> CODEC =
            StreamCodec.of(
                    (buf, msg) -> buf.writeVarInt(msg.delta),
                    buf -> new WrenchModeScrollPayload(buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

