package com.yelle233.yuanliuwujin;

import com.yelle233.yuanliuwujin.blockentity.DestructionMachineBlockEntity;
import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import com.yelle233.yuanliuwujin.ber.DestructionMachineBER;
import com.yelle233.yuanliuwujin.ber.InfiniteFluidMachineBER;
import com.yelle233.yuanliuwujin.item.WrenchItem;
import com.yelle233.yuanliuwujin.network.FaceRateUpdatePayload;
import com.yelle233.yuanliuwujin.network.WrenchModeScrollPayload;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端入口类。
 * <p>
 * 新增功能：
 * <ul>
 *   <li>HUD 显示虚空流体储罐、面速率、超频压力</li>
 *   <li>CONFIG 模式下潜行右键长按持续增加面速率</li>
 *   <li>CONFIG 模式下潜行 + 滚轮调整面速率 ±1000</li>
 * </ul>
 */
@Mod(value = SourceflowInfinite.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = SourceflowInfinite.MODID, value = Dist.CLIENT)
public class SourceflowInfiniteClient {

    public SourceflowInfiniteClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    // ── BER 注册 ──────────────────────────────────────────────

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.INFINITE_FLUID_MACHINE.get(), InfiniteFluidMachineBER::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.DESTRUCTION_MACHINE.get(), DestructionMachineBER::new);
    }

    // ── 扳手模式切换（Shift + 滚轮） ─────────────────────────────

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !player.isShiftKeyDown()) return;
        if (!(player.getMainHandItem().getItem() instanceof WrenchItem)) return;
        double scrollY = event.getScrollDeltaY();
        if (scrollY == 0) return;

        // CONFIG 模式下：调整面速率 ±1000
        if (WrenchItem.getMode(player.getMainHandItem()) == WrenchItem.WrenchMode.CONFIG) {
            HitResult hit = mc.hitResult;
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                Direction face = ((BlockHitResult) hit).getDirection();
                if (face != Direction.UP && face != Direction.DOWN) {
                    int delta = scrollY > 0 ? 1000 : -1000;
                    PacketDistributor.sendToServer(new FaceRateUpdatePayload(face, delta));
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // 否则切换扳手模式
        PacketDistributor.sendToServer(new WrenchModeScrollPayload(scrollY > 0 ? 1 : -1));
        event.setCanceled(true);
    }

    // ── 长按逻辑状态 ──────────────────────────────────────────

    /** 鼠标右键是否正在按下 */
    private static boolean rightMouseHeld = false;
    /** 长按持续时间（tick） */
    private static int holdTicks = 0;
    /** 上一次发送速率包的 tick */
    private static int lastSentTick = 0;
    /** 当前准星对准的面方向 */
    private static Direction heldFace = null;

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (!(player.getMainHandItem().getItem() instanceof WrenchItem)) return;
        if (WrenchItem.getMode(player.getMainHandItem()) != WrenchItem.WrenchMode.CONFIG) return;
        if (!player.isShiftKeyDown()) return;

        // 右键 = 按钮 1
        if (event.getButton() == 1) {
            if (event.getAction() == 1) { // 按下
                HitResult hit = mc.hitResult;
                if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                    Direction face = ((BlockHitResult) hit).getDirection();
                    if (face != Direction.UP && face != Direction.DOWN) {
                        rightMouseHeld = true;
                        holdTicks = 0;
                        lastSentTick = 0;
                        heldFace = face;
                    }
                }
            } else if (event.getAction() == 0) { // 松开
                rightMouseHeld = false;
                holdTicks = 0;
                heldFace = null;
            }
        }
    }

    // ── Client Tick：处理长按速率增加 ─────────────────────────

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
        if (!rightMouseHeld || heldFace == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (!(player.getMainHandItem().getItem() instanceof WrenchItem)) {
            rightMouseHeld = false;
            return;
        }
        if (!player.isShiftKeyDown()) return;

        holdTicks++;
        lastSentTick++;

        // 根据持续时间决定发包间隔：
        // 0–20 tick: 每 10 tick 发一次（+10）
        // 20–60 tick: 每 5 tick 发一次（+10）
        // 60+ tick: 每 2 tick 发一次（+10）
        int interval;
        if (holdTicks < 20) interval = 10;
        else if (holdTicks < 60) interval = 5;
        else interval = 2;

        if (lastSentTick >= interval) {
            lastSentTick = 0;
            PacketDistributor.sendToServer(new FaceRateUpdatePayload(heldFace, 10));
        }
    }

    // ── HUD 渲染 ──────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        LocalPlayer player = mc.player;
        if (player == null) return;

        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();

        var be = mc.level == null ? null : mc.level.getBlockEntity(pos);
        List<Component> lines = new ArrayList<>();

        boolean isWrench = player.getMainHandItem().getItem() instanceof WrenchItem;

        if (be instanceof InfiniteFluidMachineBlockEntity m) {
            buildInfiniteHUD(m, lines, isWrench);
        } else if (be instanceof DestructionMachineBlockEntity m) {
            buildDestructionHUD(m, lines, isWrench);
        }

        if (lines.isEmpty()) return;

        GuiGraphics g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int x = screenW / 2 + 12;
        int y = screenH / 2 - (lines.size() * 10) / 2;

        // 背景半透明遮罩
        int boxW = lines.stream().mapToInt(l -> mc.font.width(l)).max().orElse(80) + 8;
        int boxH = lines.size() * 10 + 6;
        g.fill(x - 4, y - 3, x + boxW, y + boxH - 3, 0x88_000000);

        for (Component line : lines) {
            g.drawString(mc.font, line, x, y, 0xFFFFFF, true);
            y += 10;
        }
    }

    private static void buildInfiniteHUD(InfiniteFluidMachineBlockEntity m,
                                          List<Component> lines, boolean isWrench) {
        // 机器名
        lines.add(Component.translatable("block.yuanliuwujin.infinite_fluid_machine")
                .withStyle(ChatFormatting.AQUA));
        // 工作状态
        String statusKey = m.canWork()
                ? "hud.sourceflowinfinite.status.running"
                : "hud.sourceflowinfinite.status.stopped";
        lines.add(Component.translatable(statusKey)
                .withStyle(m.canWork() ? ChatFormatting.GREEN : ChatFormatting.RED));
        // 能量
        var es = m.getEnergyStorage();
        lines.add(Component.translatable("hud.sourceflowinfinite.energy",
                es.getEnergyStored(), es.getMaxEnergyStored())
                .withStyle(ChatFormatting.YELLOW));
        // 虚空流体储罐
        var vt = m.getVoidTank();
        lines.add(Component.translatable("hud.yuanliuwujin.void_tank",
                vt.getFluidAmount(), vt.getCapacity())
                .withStyle(ChatFormatting.DARK_PURPLE));
        // 核心
        var cs = m.getCoreSlot().getStackInSlot(0);
        if (!cs.isEmpty()) {
            int lvl = com.yelle233.yuanliuwujin.item.InfiniteCoreItem.getLevel(cs);
            boolean oc = com.yelle233.yuanliuwujin.item.InfiniteCoreItem.isOverclocked(cs);
            lines.add(Component.translatable("hud.sourceflowinfinite.core",
                    "Lv." + lvl + (oc ? "★" : ""))
                    .withStyle(oc ? ChatFormatting.GOLD : ChatFormatting.WHITE));
            // 超频压力
            if (oc) {
                float p = m.getPressure();
                ChatFormatting pColor = p < 50 ? ChatFormatting.GREEN
                        : p < 80 ? ChatFormatting.YELLOW : ChatFormatting.RED;
                lines.add(Component.translatable("hud.yuanliuwujin.pressure",
                        String.format("%.1f%%", p)).withStyle(pColor));
            }
        } else {
            lines.add(Component.translatable("hud.sourceflowinfinite.core.missing")
                    .withStyle(ChatFormatting.RED));
        }
        // 面速率（仅持有扳手时显示）
        if (isWrench) {
            for (Direction dir : new Direction[]{
                    Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                var mode = m.getSideMode(dir);
                if (mode == InfiniteFluidMachineBlockEntity.SideMode.OFF) continue;
                lines.add(Component.literal(String.format("  %s [%s]: %d mB/t",
                        dir.getName().toUpperCase().charAt(0),
                        mode.name(), m.getFaceRate(dir)))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static void buildDestructionHUD(DestructionMachineBlockEntity m,
                                             List<Component> lines, boolean isWrench) {
        lines.add(Component.translatable("block.yuanliuwujin.destruction_machine")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        String statusKey = m.canWork()
                ? "hud.yuanliuwujin.destruction.active"
                : (!m.getCoreSlot().getStackInSlot(0).isEmpty()
                ? "hud.yuanliuwujin.destruction.no_power"
                : "hud.yuanliuwujin.destruction.no_core");
        lines.add(Component.translatable(statusKey)
                .withStyle(m.canWork() ? ChatFormatting.GREEN : ChatFormatting.RED));
        // 能量
        var es = m.getEnergyStorage();
        lines.add(Component.translatable("hud.sourceflowinfinite.energy",
                es.getEnergyStored(), es.getMaxEnergyStored())
                .withStyle(ChatFormatting.YELLOW));
        // 虚空流体储罐
        var vt = m.getVoidTank();
        lines.add(Component.translatable("hud.yuanliuwujin.void_tank",
                vt.getFluidAmount(), vt.getCapacity())
                .withStyle(ChatFormatting.DARK_PURPLE));
        // 核心
        var cs = m.getCoreSlot().getStackInSlot(0);
        if (!cs.isEmpty()) {
            int lvl = com.yelle233.yuanliuwujin.item.DestructionCoreItem.getLevel(cs);
            boolean oc = com.yelle233.yuanliuwujin.item.DestructionCoreItem.isOverclocked(cs);
            lines.add(Component.translatable("hud.sourceflowinfinite.core",
                    "Lv." + lvl + (oc ? "★" : ""))
                    .withStyle(oc ? ChatFormatting.GOLD : ChatFormatting.WHITE));
            if (oc) {
                float p = m.getPressure();
                ChatFormatting pColor = p < 50 ? ChatFormatting.GREEN
                        : p < 80 ? ChatFormatting.YELLOW : ChatFormatting.RED;
                lines.add(Component.translatable("hud.yuanliuwujin.pressure",
                        String.format("%.1f%%", p)).withStyle(pColor));
            }
        } else {
            lines.add(Component.translatable("hud.sourceflowinfinite.core.missing")
                    .withStyle(ChatFormatting.RED));
        }
        // 面速率
        if (isWrench) {
            for (Direction dir : new Direction[]{
                    Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                var mode = m.getSideMode(dir);
                if (mode == DestructionMachineBlockEntity.SideMode.OFF) continue;
                lines.add(Component.literal(String.format("  %s [%s]: %d mB/t",
                        dir.getName().toUpperCase().charAt(0),
                        mode.name(), m.getFaceRate(dir)))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
