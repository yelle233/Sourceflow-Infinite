package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.blockentity.ICoreMachine;
import com.yelle233.yuanliuwujin.item.WrenchItem;
import com.yelle233.yuanliuwujin.network.FaceRateUpdatePayload;
import com.yelle233.yuanliuwujin.network.WrenchModeScrollPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/**
 * 网络包注册。
 * <p>
 * 包含：
 * <ul>
 *   <li>{@link WrenchModeScrollPayload} – 扳手模式切换（Shift+滚轮）</li>
 *   <li>{@link FaceRateUpdatePayload} – 面速率调整（CONFIG 模式下操作）</li>
 * </ul>
 */
@EventBusSubscriber(modid = SourceflowInfinite.MODID,
        bus = EventBusSubscriber.Bus.MOD)
public class ModNetwork {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // ── 扳手模式切换包 ───────────────────────────────────────
        registrar.playToServer(
                WrenchModeScrollPayload.TYPE,
                WrenchModeScrollPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    ServerPlayer player = (ServerPlayer) ctx.player();
                    ItemStack mainHand = player.getMainHandItem();
                    if (!(mainHand.getItem() instanceof WrenchItem)) return;

                    WrenchItem.WrenchMode current = WrenchItem.getMode(mainHand);
                    WrenchItem.WrenchMode next = current.next(payload.delta());
                    WrenchItem.setMode(mainHand, next);

                    player.sendSystemMessage(Component.translatable(
                            "mode.yuanliuwujin.wrench." + next.name().toLowerCase())
                            .withStyle(ChatFormatting.YELLOW));
                    player.level().playSound(null, player.blockPosition(),
                            SoundEvents.UI_BUTTON_CLICK.value(),
                            SoundSource.PLAYERS, 0.5f, 1.0f);
                }));

        // ── 面速率调整包 ──────────────────────────────────────────
        registrar.playToServer(
                FaceRateUpdatePayload.TYPE,
                FaceRateUpdatePayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    ServerPlayer player = (ServerPlayer) ctx.player();
                    ItemStack mainHand = player.getMainHandItem();
                    if (!(mainHand.getItem() instanceof WrenchItem)) return;
                    if (WrenchItem.getMode(mainHand) != WrenchItem.WrenchMode.CONFIG) return;

                    // 找到准星对准的方块
                    HitResult hit = player.pick(6.0, 0f, false);
                    if (hit.getType() != HitResult.Type.BLOCK) return;
                    BlockPos pos = ((BlockHitResult) hit).getBlockPos();
                    Direction face = ((BlockHitResult) hit).getDirection();

                    BlockEntity be = player.level().getBlockEntity(pos);
                    if (!(be instanceof ICoreMachine machine)) return;

                    machine.adjustFaceRate(payload.dir(), payload.delta());

                    // 向玩家反馈当前速率
                    int newRate = machine.getFaceRate(payload.dir());
                    player.sendSystemMessage(Component.translatable(
                            "msg.yuanliuwujin.face_rate",
                            payload.dir().getName(), newRate)
                            .withStyle(ChatFormatting.AQUA));
                }));
    }
}
