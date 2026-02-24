package com.yelle233.yuanliuwujin.network;

import com.yelle233.yuanliuwujin.item.WrenchItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 扳手模式切换网络包（客户端 → 服务端）。
 * <p>
 * 当玩家在持有扳手时按 Shift+滚轮，此包发送到服务端切换扳手模式。
 * 使用 Forge 1.20.1 的 SimpleChannel 传输。
 */
public class WrenchModeScrollMessage {

    private final int delta;

    public WrenchModeScrollMessage(int delta) {
        this.delta = delta;
    }

    public static void encode(WrenchModeScrollMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.delta);
    }

    public static WrenchModeScrollMessage decode(FriendlyByteBuf buf) {
        return new WrenchModeScrollMessage(buf.readInt());
    }

    public static void handle(WrenchModeScrollMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            ItemStack mainHand = player.getMainHandItem();
            if (!(mainHand.getItem() instanceof WrenchItem)) return;

            WrenchItem.WrenchMode current = WrenchItem.getMode(mainHand);
            WrenchItem.WrenchMode next    = current.next(msg.delta);
            WrenchItem.setMode(mainHand, next);

            Component modeName = switch (next) {
                case IO     -> Component.translatable("mode.yuanliuwujin.wrench.io")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
                case CONFIG -> Component.translatable("mode.yuanliuwujin.wrench.config")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            };

            player.displayClientMessage(
                    Component.literal(" ")
                            .append(Component.translatable("msg.yuanliuwujin.wrench_mode").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(modeName),
                    true);

            player.level().playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.UI_BUTTON_CLICK.value(),
                    SoundSource.PLAYERS,
                    0.5f,
                    1.0f
            );

        });
        ctx.setPacketHandled(true);
    }
}

