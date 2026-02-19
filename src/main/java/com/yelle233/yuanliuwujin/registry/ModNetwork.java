package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.blockentity.ICoreMachine;
import com.yelle233.yuanliuwujin.item.WrenchItem;
import com.yelle233.yuanliuwujin.network.FaceRateUpdatePayload;
import com.yelle233.yuanliuwujin.network.WrenchModeScrollPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = SourceflowInfinite.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModNetwork {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(WrenchModeScrollPayload.TYPE, WrenchModeScrollPayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            ItemStack mainHand = player.getMainHandItem();
            if (!(mainHand.getItem() instanceof WrenchItem)) return;
            WrenchItem.WrenchMode current = WrenchItem.getMode(mainHand);
            WrenchItem.WrenchMode next = current.next(payload.delta());
            WrenchItem.setMode(mainHand, next);
            Component modeName = switch (next) {
                case IO -> Component.translatable("mode.yuanliuwujin.wrench.io").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
                case CONFIG -> Component.translatable("mode.yuanliuwujin.wrench.config").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            };
            player.displayClientMessage(Component.literal(" ").append(Component.translatable("msg.yuanliuwujin.wrench_mode").withStyle(ChatFormatting.GRAY)).append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY)).append(modeName), true);
            player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 1.2f);
        }));

        registrar.playToServer(FaceRateUpdatePayload.TYPE, FaceRateUpdatePayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            ItemStack mainHand = player.getMainHandItem();
            if (!(mainHand.getItem() instanceof WrenchItem)) return;
            if (WrenchItem.getMode(mainHand) != WrenchItem.WrenchMode.CONFIG) return;
            HitResult hit = player.pick(6.0, 0f, false);
            if (hit.getType() != HitResult.Type.BLOCK) return;
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            BlockEntity be = player.level().getBlockEntity(pos);
            if (!(be instanceof ICoreMachine machine)) return;
            machine.adjustFaceRate(payload.dir(), payload.delta());
            int newRate = machine.getFaceRate(payload.dir());
            // 显示 mB/s
            player.displayClientMessage(Component.translatable("msg.yuanliuwujin.face_rate", payload.dir().getName(), newRate).withStyle(ChatFormatting.AQUA), true);
        }));
    }
}
