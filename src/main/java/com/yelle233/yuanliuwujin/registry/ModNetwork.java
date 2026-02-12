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

/**
 * 网络包注册。
 * <p>
 * 注意：{@link RegisterPayloadHandlersEvent} 在 MOD 总线上触发，
 * 因此必须指定 {@code bus = EventBusSubscriber.Bus.MOD}。
 */
@EventBusSubscriber(modid = SourceflowInfinite.MODID)
public class ModNetwork {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // 扳手模式切换包：客户端 → 服务端
        registrar.playToServer(
                WrenchModeScrollPayload.TYPE,
                WrenchModeScrollPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    ServerPlayer player = (ServerPlayer) ctx.player();
                    ItemStack mainHand = player.getMainHandItem();
                    if (!(mainHand.getItem() instanceof WrenchItem)) return;

                    // 切换模式
                    WrenchItem.WrenchMode current = WrenchItem.getMode(mainHand);
                    WrenchItem.WrenchMode next = current.next(payload.delta());
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
                })
        );
    }
}
