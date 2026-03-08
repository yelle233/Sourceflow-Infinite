package com.yelle233.yuanliuwujin.network;

import com.yelle233.yuanliuwujin.blockentity.ICoreMachine;
import com.yelle233.yuanliuwujin.blockentity.IVoidGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 设置面速率消息（客户端 → 服务端）
 */
public class SetFaceRateMessage {
    private final Direction dir;
    private final int rate;

    public SetFaceRateMessage(Direction dir, int rate) {
        this.dir = dir;
        this.rate = rate;
    }

    public static void encode(SetFaceRateMessage msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.dir.ordinal());
        buf.writeInt(msg.rate);
    }

    public static SetFaceRateMessage decode(FriendlyByteBuf buf) {
        Direction dir = Direction.values()[buf.readByte()];
        int rate = buf.readInt();
        return new SetFaceRateMessage(dir, rate);
    }

    public static void handle(SetFaceRateMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            HitResult hit = player.pick(6.0, 0f, false);
            if (hit.getType() != HitResult.Type.BLOCK) return;
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            BlockEntity be = player.level().getBlockEntity(pos);

            int rate = Math.max(1, Math.min(msg.rate, Integer.MAX_VALUE - 1));

            // 支持虚空发电机
            if (be instanceof IVoidGenerator generator) {
                generator.adjustSideRate(msg.dir, rate - generator.getSideRate(msg.dir));
                player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 1.2f);
                return;
            }

            if (!(be instanceof ICoreMachine machine)) return;
            machine.adjustFaceRate(msg.dir, rate - machine.getFaceRate(msg.dir));
            player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 1.2f);
        });
        ctx.get().setPacketHandled(true);
    }
}
