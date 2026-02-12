package com.yelle233.yuanliuwujin.network;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 扳手模式滚轮切换的网络包（客户端 → 服务端）。
 *
 * @param delta 滚轮方向：+1 = 向上，-1 = 向下
 */
public record WrenchModeScrollPayload(int delta) implements CustomPacketPayload {

    public static final Type<WrenchModeScrollPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, "wrench_mode_scroll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WrenchModeScrollPayload> CODEC =
            StreamCodec.of(
                    (buf, msg) -> buf.writeVarInt(msg.delta),
                    buf -> new WrenchModeScrollPayload(buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
