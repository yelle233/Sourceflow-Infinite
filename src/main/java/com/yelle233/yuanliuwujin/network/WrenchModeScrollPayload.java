package com.yelle233.yuanliuwujin.network;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 扳手模式切换网络包（客户端 → 服务端）。
 * <p>
 * Shift + 滚轮触发，{@code delta} 为 +1（向下滚）或 -1（向上滚）。
 */
public record WrenchModeScrollPayload(int delta) implements CustomPacketPayload {

    public static final ResourceLocation ID_RL =
            ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, "wrench_scroll");

    public static final CustomPacketPayload.Type<WrenchModeScrollPayload> TYPE =
            new CustomPacketPayload.Type<>(ID_RL);

    public static final StreamCodec<FriendlyByteBuf, WrenchModeScrollPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeInt(p.delta),
                    buf -> new WrenchModeScrollPayload(buf.readInt())
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
