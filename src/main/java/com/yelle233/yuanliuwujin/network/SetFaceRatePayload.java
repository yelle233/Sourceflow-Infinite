package com.yelle233.yuanliuwujin.network;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 设置面速率网络包（客户端 → 服务端）
 * 用于从 GUI 界面直接设置指定面的速率值
 */
public record SetFaceRatePayload(BlockPos pos, Direction dir, int rate) implements CustomPacketPayload {

    public static final ResourceLocation ID_RL =
            ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, "set_face_rate");

    public static final CustomPacketPayload.Type<SetFaceRatePayload> TYPE =
            new CustomPacketPayload.Type<>(ID_RL);

    public static final StreamCodec<FriendlyByteBuf, SetFaceRatePayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBlockPos(payload.pos);
                        buf.writeByte(payload.dir.ordinal());
                        buf.writeInt(payload.rate);
                    },
                    buf -> {
                        BlockPos pos = buf.readBlockPos();
                        Direction dir = Direction.values()[buf.readByte()];
                        int rate = buf.readInt();
                        return new SetFaceRatePayload(pos, dir, rate);
                    }
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
