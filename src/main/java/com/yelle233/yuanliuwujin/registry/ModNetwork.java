package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.blockentity.ICoreMachine;
import com.yelle233.yuanliuwujin.blockentity.IVoidGenerator;
import com.yelle233.yuanliuwujin.item.WrenchItem;
import com.yelle233.yuanliuwujin.network.FaceRateUpdatePayload;
import com.yelle233.yuanliuwujin.network.SetFaceRatePayload;
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
            // 移除临时消息，因为现在有持久 HUD 显示
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

            // 支持 ICoreMachine
            if (be instanceof ICoreMachine machine) {
                machine.adjustFaceRate(payload.dir(), payload.delta());
                int newRate = machine.getFaceRate(payload.dir());
                player.displayClientMessage(Component.translatable("msg.yuanliuwujin.face_rate", payload.dir().getName(), newRate).withStyle(ChatFormatting.AQUA), true);
                return;
            }

            // 支持 IVoidGenerator
            if (be instanceof IVoidGenerator generator) {
                generator.adjustSideRate(payload.dir(), payload.delta());
                int newRate = generator.getSideRate(payload.dir());
                player.displayClientMessage(Component.translatable("msg.yuanliuwujin.generator_rate", payload.dir().getName(), newRate).withStyle(ChatFormatting.GREEN), true);
            }
        }));

        registrar.playToServer(SetFaceRatePayload.TYPE, SetFaceRatePayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            BlockEntity be = player.level().getBlockEntity(payload.pos());

            // 支持 ICoreMachine
            if (be instanceof ICoreMachine machine) {
                int rate = Math.max(1, Math.min(payload.rate(), Integer.MAX_VALUE - 1));
                machine.adjustFaceRate(payload.dir(), rate - machine.getFaceRate(payload.dir()));
                player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 1.2f);
                return;
            }

            // 支持 IVoidGenerator
            if (be instanceof IVoidGenerator generator) {
                int rate = Math.max(1, Math.min(payload.rate(), Integer.MAX_VALUE - 1));
                generator.adjustSideRate(payload.dir(), rate - generator.getSideRate(payload.dir()));
                player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 1.2f);
            }
        }));
    }
}
