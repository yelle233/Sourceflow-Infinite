package com.yelle233.yuanliuwujin.network;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 面速率调整网络包（客户端 → 服务端）。
 * <p>
 * 当玩家在 CONFIG 模式下使用扳手调整某个面的速率时，此包被发送到服务端。
 * <ul>
 *   <li>{@code dir} – 要调整速率的面方向</li>
 *   <li>{@code delta} – 速率变化量（可为负数，例如 -1000 表示减少 1000 mB/tick）</li>
 * </ul>
 */
public record FaceRateUpdatePayload(Direction dir, int delta) implements CustomPacketPayload {

    public static final ResourceLocation ID_RL =
            ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, "face_rate_update");

    public static final CustomPacketPayload.Type<FaceRateUpdatePayload> TYPE =
            new CustomPacketPayload.Type<>(ID_RL);

    /** 流式编解码器，用于网络序列化 */
    public static final StreamCodec<FriendlyByteBuf, FaceRateUpdatePayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeByte(payload.dir.ordinal());
                        buf.writeInt(payload.delta);
                    },
                    buf -> {
                        Direction dir = Direction.values()[buf.readByte()];
                        int delta = buf.readInt();
                        return new FaceRateUpdatePayload(dir, delta);
                    }
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
