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
 * 扳手模式滚轮切换网络消息（客户端 → 服务端）。
 * <p>
 * 1.20.1 Forge 版本，使用 {@link FriendlyByteBuf} 编解码。
 */
public class WrenchModeScrollMessage {

    private final int delta;

    public WrenchModeScrollMessage(int delta) {
        this.delta = delta;
    }

    /* ====== 编解码 ====== */

    public static void encode(WrenchModeScrollMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.delta);
    }

    public static WrenchModeScrollMessage decode(FriendlyByteBuf buf) {
        return new WrenchModeScrollMessage(buf.readVarInt());
    }

    /* ====== 服务端处理 ====== */

    public static void handle(WrenchModeScrollMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            ItemStack mainHand = player.getMainHandItem();
            if (!(mainHand.getItem() instanceof WrenchItem)) return;

            // 切换模式
            WrenchItem.WrenchMode current = WrenchItem.getMode(mainHand);
            WrenchItem.WrenchMode next = current.next(msg.delta);
            WrenchItem.setMode(mainHand, next);

            // 发送 actionbar 消息
            Component modeName = switch (next) {
                case IO -> Component.translatable("mode.yourmod.wrench.io")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
                case CONFIG -> Component.translatable("mode.yourmod.wrench.config")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            };

            player.displayClientMessage(
                    Component.literal(" ")
                            .append(Component.translatable("msg.yourmod.wrench_mode").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(modeName),
                    true);

            // 切换音效
            player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 1.2f);
        });
        ctx.setPacketHandled(true);
    }
}
