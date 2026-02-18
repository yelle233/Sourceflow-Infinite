package com.yelle233.yuanliuwujin;

import com.yelle233.yuanliuwujin.blockentity.DestructionMachineBlockEntity;
import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity.SideMode;
import com.yelle233.yuanliuwujin.ber.DestructionMachineBER;
import com.yelle233.yuanliuwujin.ber.InfiniteFluidMachineBER;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem.BindType;
import com.yelle233.yuanliuwujin.item.WrenchItem;
import com.yelle233.yuanliuwujin.network.WrenchModeScrollPayload;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
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

import java.util.List;
import java.util.Locale;

/**
 * 客户端入口类。
 * <ul>
 *   <li>注册两种机器的 BER</li>
 *   <li>扳手滚轮模式切换</li>
 *   <li>两种机器的准星 HUD 信息面板（含面模式标注）</li>
 * </ul>
 */
@Mod(value = SourceflowInfinite.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = SourceflowInfinite.MODID, value = Dist.CLIENT)
public class SourceflowInfiniteClient {

    public SourceflowInfiniteClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    /* ====== BER 注册 ====== */

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.INFINITE_FLUID_MACHINE.get(), InfiniteFluidMachineBER::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.DESTRUCTION_MACHINE.get(), DestructionMachineBER::new);
    }

    /* ====== 扳手滚轮 ====== */

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !player.isShiftKeyDown()) return;
        if (!(player.getMainHandItem().getItem() instanceof WrenchItem)) return;
        double scrollY = event.getScrollDeltaY();
        if (scrollY == 0) return;
        PacketDistributor.sendToServer(new WrenchModeScrollPayload(scrollY > 0 ? 1 : -1));
        event.setCanceled(true);
    }

    /* ====== HUD ====== */

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr)) return;
        BlockPos pos = bhr.getBlockPos();
        Object be = mc.level.getBlockEntity(pos);

        if (be instanceof InfiniteFluidMachineBlockEntity inf) {
            renderInfiniteMachineHud(event.getGuiGraphics(), mc, inf);
        } else if (be instanceof DestructionMachineBlockEntity dest) {
            renderDestructionMachineHud(event.getGuiGraphics(), mc, dest);
        }
    }

    /* ====== 无限流体机器 HUD ====== */

    private static void renderInfiniteMachineHud(GuiGraphics gg, Minecraft mc,
                                                  InfiniteFluidMachineBlockEntity machine) {
        long energy     = machine.getEnergyStorage().getEnergyStored();
        long capacity   = machine.getEnergyStorage().getMaxEnergyStored();
        int costPerTick = machine.getFeCostPerTick();
        int costPerSec  = costPerTick * 20;
        boolean hasCore = !machine.getCoreSlot().getStackInSlot(0).isEmpty();
        BindType bindType     = machine.getCoreBindType();
        Component substanceName = machine.getBoundSubstanceName();

        Component displayName;
        if (substanceName == null) {
            displayName = Component.translatable("hud.sourceflowinfinite.none")
                    .withStyle(ChatFormatting.DARK_GRAY);
        } else if (bindType == BindType.CHEMICAL) {
            displayName = substanceName.copy().withStyle(ChatFormatting.LIGHT_PURPLE);
        } else {
            displayName = substanceName.copy().withStyle(ChatFormatting.AQUA);
        }

        // ── 统计激活面，在方向字母后附加模式标注 ──
        // 格式示例：N(P) S(B) E(P)
        // (P) = PULL 模式，(B) = BOTH 模式
        StringBuilder facesShort = new StringBuilder();
        int enabledFaces = 0;
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            SideMode mode = machine.getSideMode(d);
            if (mode != SideMode.PULL && mode != SideMode.BOTH) continue;
            enabledFaces++;
            if (!facesShort.isEmpty()) facesShort.append(' ');
            facesShort.append(dirShort(d))
                      .append(mode == SideMode.PULL ? "(P)" : "(B)");
        }

        String typeLabel = switch (bindType) {
            case FLUID    -> "§b[Fluid]§r ";
            case CHEMICAL -> "§d[Chemical]§r ";
            default       -> "";
        };

        List<Component> lines = List.of(
                Component.translatable("hud.sourceflowinfinite.energy",
                        compactFE(energy), compactFE(capacity)).withStyle(ChatFormatting.WHITE),
                buildCostLine(costPerSec, costPerTick),
                Component.translatable("hud.sourceflowinfinite.core",
                        Component.translatable(hasCore
                                ? "hud.sourceflowinfinite.core.inserted"
                                : "hud.sourceflowinfinite.core.missing")
                                .withStyle(hasCore ? ChatFormatting.GREEN : ChatFormatting.RED)),
                Component.translatable("hud.sourceflowinfinite.fluid",
                        Component.literal(typeLabel).append(displayName))
        );

        Component facesLine = Component.translatable("hud.sourceflowinfinite.faces",
                enabledFaces,
                enabledFaces == 0
                        ? Component.translatable("hud.sourceflowinfinite.none")
                                .withStyle(ChatFormatting.DARK_GRAY)
                        : Component.literal(facesShort.toString()).withStyle(ChatFormatting.GREEN)
        ).withStyle(ChatFormatting.WHITE);

        renderHudPanel(gg, mc, lines, facesLine);
    }

    /* ====== 销毁机器 HUD ====== */

    private static void renderDestructionMachineHud(GuiGraphics gg, Minecraft mc,
                                                     DestructionMachineBlockEntity machine) {
        long energy     = machine.getEnergyStorage().getEnergyStored();
        long capacity   = machine.getEnergyStorage().getMaxEnergyStored();
        int costPerTick = machine.getFeCostPerTick();
        int costPerSec  = costPerTick * 20;
        boolean hasCore = machine.hasCoreInserted();
        boolean canWork = machine.canWorkNow();

        Component statusComp = hasCore
                ? (canWork
                    ? Component.translatable("hud.yuanliuwujin.destruction.active")
                            .withStyle(ChatFormatting.GREEN)
                    : Component.translatable("hud.yuanliuwujin.destruction.no_power")
                            .withStyle(ChatFormatting.RED))
                : Component.translatable("hud.yuanliuwujin.destruction.no_core")
                        .withStyle(ChatFormatting.GRAY);

        // ── 统计激活面，在方向字母后附加模式标注 ──
        // 格式示例：N(B) S(P) W(B)
        // (B) = BOTH 模式（被动接受），(P) = PUSH 模式（主动抽取）
        StringBuilder facesShort = new StringBuilder();
        int enabledFaces = 0;
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            DestructionMachineBlockEntity.SideMode mode = machine.getSideMode(d);
            if (mode == DestructionMachineBlockEntity.SideMode.OFF) continue;
            enabledFaces++;
            if (!facesShort.isEmpty()) facesShort.append(' ');
            facesShort.append(dirShort(d))
                      .append(mode == DestructionMachineBlockEntity.SideMode.PUSH ? "(P)" : "(B)");
        }

        List<Component> lines = List.of(
                Component.translatable("hud.sourceflowinfinite.energy",
                        compactFE(energy), compactFE(capacity)).withStyle(ChatFormatting.WHITE),
                buildCostLine(costPerSec, costPerTick),
                Component.translatable("hud.sourceflowinfinite.core",
                        Component.translatable(hasCore
                                ? "hud.sourceflowinfinite.core.inserted"
                                : "hud.sourceflowinfinite.core.missing")
                                .withStyle(hasCore ? ChatFormatting.GREEN : ChatFormatting.RED)),
                Component.translatable("hud.yuanliuwujin.destruction.status_label", statusComp)
        );

        Component facesLine = Component.translatable("hud.sourceflowinfinite.faces",
                enabledFaces,
                enabledFaces == 0
                        ? Component.translatable("hud.sourceflowinfinite.none")
                                .withStyle(ChatFormatting.DARK_GRAY)
                        : Component.literal(facesShort.toString()).withStyle(ChatFormatting.AQUA)
        ).withStyle(ChatFormatting.WHITE);

        renderHudPanel(gg, mc, lines, facesLine);
    }

    /* ====== HUD 面板绘制（两种机器共用） ====== */

    private static void renderHudPanel(GuiGraphics gg, Minecraft mc,
                                        List<Component> mainLines, Component facesLine) {
        int padding = 4, gap = 2, maxWidth = 220, lh = mc.font.lineHeight;
        int innerMax = 0;
        for (Component c : mainLines) innerMax = Math.max(innerMax, mc.font.width(c));
        List<FormattedCharSequence> faceSeqs = mc.font.split(facesLine, maxWidth - padding * 2);
        for (FormattedCharSequence seq : faceSeqs)
            innerMax = Math.max(innerMax, mc.font.width(seq));
        int panelW = Math.min(innerMax + padding * 2, maxWidth);
        faceSeqs = mc.font.split(facesLine, panelW - padding * 2);
        int mainH  = mainLines.size() * lh + (mainLines.size() - 1) * gap;
        int facesH = faceSeqs.size() * lh + (faceSeqs.size() - 1) * gap;
        int panelH = padding * 2 + mainH + 5 + facesH;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int x = Mth.clamp(screenW / 2 + 10, 6, screenW - panelW - 6);
        int y = Mth.clamp(screenH / 2 + 10, 6, screenH - panelH - 6);
        gg.fill(x - 1, y - 1, x + panelW + 1, y + panelH + 1, 0x22FFFFFF);
        gg.fill(x, y, x + panelW, y + panelH, 0x55000000);
        int ty = y + padding;
        for (Component c : mainLines) {
            gg.drawString(mc.font, c, x + padding, ty, 0xFFFFFF, false);
            ty += lh + gap;
        }
        ty += 1;
        gg.fill(x + padding, ty, x + panelW - padding, ty + 1, 0x22FFFFFF);
        ty += 3;
        for (FormattedCharSequence seq : faceSeqs) {
            gg.drawString(mc.font, seq, x + padding, ty, 0xFFFFFF, false);
            ty += lh + gap;
        }
    }

    /* ====== 工具方法 ====== */

    private static Component buildCostLine(int costPerSec, int costPerTick) {
        ChatFormatting color = costPerSec >= 2000 ? ChatFormatting.RED
                : costPerSec >= 800 ? ChatFormatting.GOLD : ChatFormatting.GREEN;
        return Component.translatable("hud.sourceflowinfinite.cost",
                compactFE(costPerSec), costPerTick).withStyle(color);
    }

    private static String dirShort(Direction d) {
        return switch (d) {
            case NORTH -> "N"; case SOUTH -> "S";
            case WEST  -> "W"; case EAST  -> "E";
            case DOWN  -> "D"; default    -> "?";
        };
    }

    private static String compactFE(long value) {
        if (value < 1_000L)             return Long.toString(value);
        if (value < 1_000_000L)         return formatDecimal(value / 1_000.0) + "k";
        if (value < 1_000_000_000L)     return formatDecimal(value / 1_000_000.0) + "M";
        if (value < 1_000_000_000_000L) return formatDecimal(value / 1_000_000_000.0) + "G";
        return formatDecimal(value / 1_000_000_000_000.0) + "T";
    }

    private static String formatDecimal(double d) {
        String s = String.format(Locale.ROOT, "%.1f", d);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
