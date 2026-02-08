package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.item.WrenchItem;
import com.yelle233.yuanliuwujin.network.WrenchModeScrollPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = SourceflowInfinite.MODID)
public class ModNetwork {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("1");

        r.playToServer(
                WrenchModeScrollPayload.TYPE,
                WrenchModeScrollPayload.CODEC,
                (payload, ctx) -> {
                    ctx.enqueueWork(() -> {
                        ServerPlayer player = (ServerPlayer) ctx.player();
                        ItemStack main = player.getMainHandItem();
                        if (!(main.getItem() instanceof WrenchItem)) return;

                        WrenchItem.WrenchMode cur = WrenchItem.getMode(main);
                        WrenchItem.WrenchMode next = cur.next(payload.delta());
                        WrenchItem.setMode(main, next);

                        Component modeName = switch (next) {
                            case IO -> Component.translatable("mode.yourmod.wrench.io")
                                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
                            case CONFIG -> Component.translatable("mode.yourmod.wrench.config")
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
                        };

                        Component msg = Component.literal(" ")
                                .append(Component.translatable("msg.yourmod.wrench_mode").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                                .append(modeName);


                        // 给玩家提示：actionbar
                        player.displayClientMessage(msg, true);

                        //切模式音效
                        player.playNotifySound(
                                SoundEvents.UI_BUTTON_CLICK.value(),
                                SoundSource.PLAYERS,
                                0.4f,
                                1.2f
                        );
                    });
                }
        );
    }
}
