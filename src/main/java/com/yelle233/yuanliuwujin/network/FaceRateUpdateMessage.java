package com.yelle233.yuanliuwujin.network;

import com.yelle233.yuanliuwujin.blockentity.ICoreMachine;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 面速率调整网络包（客户端 → 服务端）。
 * <p>
 * 当玩家在 CONFIG 模式下使用扳手调整某个面的速率时，此包被发送到服务端。
 * 使用 Forge 1.20.1 的 {@link net.minecraftforge.network.simple.SimpleChannel} 传输。
 * <ul>
 *   <li>{@code dir} – 要调整速率的面方向（-1 表示使用玩家视线面）</li>
 *   <li>{@code delta} – 速率变化量（可为负数，例如 -1000 表示减少 1000 mB/s）</li>
 * </ul>
 */
public class FaceRateUpdateMessage {

    private final Direction dir;
    private final int delta;

    public FaceRateUpdateMessage(Direction dir, int delta) {
        this.dir = dir;
        this.delta = delta;
    }

    public static void encode(FaceRateUpdateMessage msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.dir.ordinal());
        buf.writeInt(msg.delta);
    }

    public static FaceRateUpdateMessage decode(FriendlyByteBuf buf) {
        Direction dir = Direction.values()[buf.readByte() & 0xFF];
        int delta = buf.readInt();
        return new FaceRateUpdateMessage(dir, delta);
    }

    public static void handle(FaceRateUpdateMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            // 找到玩家当前瞄准的方块
            HitResult hit = player.pick(5.0, 1.0f, false);
            if (hit.getType() != HitResult.Type.BLOCK) return;
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos pos = blockHit.getBlockPos();

            if (!(player.level() instanceof ServerLevel serverLevel)) return;
            BlockEntity be = serverLevel.getBlockEntity(pos);
            if (!(be instanceof ICoreMachine machine)) return;

            machine.adjustFaceRate(msg.dir, msg.delta);
            int newRate = machine.getFaceRate(msg.dir);
            // 显示 mB/s
            player.displayClientMessage(Component.translatable("msg.yuanliuwujin.face_rate", msg.dir.getName(), newRate).withStyle(ChatFormatting.AQUA), true);
        });
        ctx.setPacketHandled(true);
    }
}

