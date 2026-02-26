package com.yelle233.yuanliuwujin;

import com.yelle233.yuanliuwujin.blockentity.DestructionMachineBlockEntity;
import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity.SideMode;
import com.yelle233.yuanliuwujin.ber.DestructionMachineBER;
import com.yelle233.yuanliuwujin.ber.InfiniteFluidMachineBER;
import com.yelle233.yuanliuwujin.fluid.VoidFluidType;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem.BindType;
import com.yelle233.yuanliuwujin.item.WrenchItem;
import com.yelle233.yuanliuwujin.network.FaceRateUpdatePayload;
import com.yelle233.yuanliuwujin.network.WrenchModeScrollPayload;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
import com.yelle233.yuanliuwujin.registry.ModFluids;
import com.yelle233.yuanliuwujin.registry.ModItems;
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
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mod(value = SourceflowInfinite.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = SourceflowInfinite.MODID, value = Dist.CLIENT)
public class SourceflowInfiniteClient {

    public SourceflowInfiniteClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // 虚空流体桶颜色
        container.getEventBus().addListener((RegisterColorHandlersEvent.Item event) -> {
            event.register((stack, tintIndex) -> {
                // tintIndex == 1 是桶模型中流体层的 tint
                return tintIndex == 1 ? VoidFluidType.COLOR_ARGB : 0xFFFFFFFF;
            }, ModItems.VOID_BUCKET.get());
        });

        // 虚空流体使用半透明渲染层
        container.getEventBus().addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> {
            event.enqueueWork(() -> {
                net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                        ModFluids.VOID_FLUID_SOURCE.get(),
                        net.minecraft.client.renderer.RenderType.translucent());
                net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                        ModFluids.VOID_FLUID_FLOWING.get(),
                        net.minecraft.client.renderer.RenderType.translucent());
            });
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.INFINITE_FLUID_MACHINE.get(), InfiniteFluidMachineBER::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DESTRUCTION_MACHINE.get(), DestructionMachineBER::new);
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !player.isShiftKeyDown()) return;
        if (!(player.getMainHandItem().getItem() instanceof WrenchItem)) return;
        double scrollY = event.getScrollDeltaY();
        if (scrollY == 0) return;
        if (WrenchItem.getMode(player.getMainHandItem()) == WrenchItem.WrenchMode.CONFIG) {
            HitResult hit = mc.hitResult;
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockHitResult bhr = (BlockHitResult) hit;
                Direction face = bhr.getDirection();
                // 仅在对准本模组机器的侧面时才拦截滚轮做速率调节
                if (face != Direction.UP && face != Direction.DOWN
                        && mc.level != null
                        && mc.level.getBlockEntity(bhr.getBlockPos()) instanceof com.yelle233.yuanliuwujin.blockentity.ICoreMachine) {
                    int delta = scrollY > 0 ? 1000 : -1000;
                    PacketDistributor.sendToServer(new FaceRateUpdatePayload(face, delta));
                    event.setCanceled(true);
                    return;
                }
            }
        }
        PacketDistributor.sendToServer(new WrenchModeScrollPayload(scrollY > 0 ? 1 : -1));
        event.setCanceled(true);
    }

    private static boolean rightMouseHeld = false;
    private static int holdTicks = 0;
    private static int lastSentTick = 0;
    private static Direction heldFace = null;

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (!(player.getMainHandItem().getItem() instanceof WrenchItem)) return;
        if (WrenchItem.getMode(player.getMainHandItem()) != WrenchItem.WrenchMode.CONFIG) return;
        if (!player.isShiftKeyDown()) return;
        if (event.getButton() == 1) {
            if (event.getAction() == 1) {
                HitResult hit = mc.hitResult;
                if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult bhr = (BlockHitResult) hit;
                    Direction face = bhr.getDirection();
                    // 仅在对准本模组机器的侧面时才启用长按速率调节
                    if (face != Direction.UP && face != Direction.DOWN
                            && mc.level != null
                            && mc.level.getBlockEntity(bhr.getBlockPos()) instanceof com.yelle233.yuanliuwujin.blockentity.ICoreMachine) {
                        rightMouseHeld = true; holdTicks = 0; lastSentTick = 0; heldFace = face;
                    }
                }
            } else if (event.getAction() == 0) { rightMouseHeld = false; holdTicks = 0; heldFace = null; }
        }
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
        if (!rightMouseHeld || heldFace == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (!(player.getMainHandItem().getItem() instanceof WrenchItem)) { rightMouseHeld = false; return; }
        if (!player.isShiftKeyDown()) return;
        holdTicks++; lastSentTick++;
        int interval = holdTicks < 20 ? 10 : holdTicks < 60 ? 5 : 2;
        if (lastSentTick >= interval) {
            lastSentTick = 0;
            PacketDistributor.sendToServer(new FaceRateUpdatePayload(heldFace, 10));
        }
    }

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
        if (be instanceof InfiniteFluidMachineBlockEntity inf) renderInfiniteMachineHud(event.getGuiGraphics(), mc, inf);
        else if (be instanceof DestructionMachineBlockEntity dest) renderDestructionMachineHud(event.getGuiGraphics(), mc, dest);
    }

    private static void renderInfiniteMachineHud(GuiGraphics gg, Minecraft mc, InfiniteFluidMachineBlockEntity machine) {
        long energy = machine.getEnergyStorage().getEnergyStored();
        long capacity = machine.getEnergyStorage().getMaxEnergyStored();
        boolean hasCore = !machine.getCoreSlot().getStackInSlot(0).isEmpty();
        BindType bindType = machine.getCoreBindType();
        Component substanceName = machine.getBoundSubstanceName();

        Component displayName;
        if (substanceName == null) displayName = Component.translatable("hud.sourceflowinfinite.none").withStyle(ChatFormatting.DARK_GRAY);
        else if (bindType == BindType.CHEMICAL) displayName = substanceName.copy().withStyle(ChatFormatting.LIGHT_PURPLE);
        else displayName = substanceName.copy().withStyle(ChatFormatting.AQUA);

        StringBuilder facesShort = new StringBuilder();
        int enabledFaces = 0;
        for (Direction d : Direction.values()) {
            if (d == Direction.UP || d == Direction.DOWN) continue;
            SideMode mode = machine.getSideMode(d);
            if (mode != SideMode.PULL && mode != SideMode.BOTH) continue;
            enabledFaces++;
            if (!facesShort.isEmpty()) facesShort.append(' ');
            facesShort.append(dirShort(d)).append(mode == SideMode.PULL ? "(P)" : "(B)");
            if (mc.player != null && mc.player.getMainHandItem().getItem() instanceof WrenchItem) {
                facesShort.append(':').append(machine.getFaceRate(d)).append("mB/s");
            }
        }

        String typeLabel = switch (bindType) { case FLUID -> "§b[Fluid]§r "; case CHEMICAL -> "§d[Chemical]§r "; default -> ""; };

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("hud.sourceflowinfinite.energy", compactFE(energy), compactFE(capacity)).withStyle(ChatFormatting.WHITE));

        // ★ 实时耗电显示
        int fePerTick = machine.getLastTickFEConsumed();
        long fePerSecond = (long) fePerTick * 20;
        lines.add(Component.translatable("hud.sourceflowinfinite.cost", compactFE(fePerSecond), compactFE(fePerTick)).withStyle(ChatFormatting.GOLD));

        lines.add(Component.translatable("hud.sourceflowinfinite.core",
                hasCore
                    ? Component.translatable("hud.sourceflowinfinite.core.inserted").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(" Lv." + InfiniteCoreItem.getLevel(machine.getCoreSlot().getStackInSlot(0))
                            + (InfiniteCoreItem.isOverclocked(machine.getCoreSlot().getStackInSlot(0)) ? " ★" : ""))
                            .withStyle(ChatFormatting.YELLOW))
                    : Component.translatable("hud.sourceflowinfinite.core.missing").withStyle(ChatFormatting.RED)));
        {   // 状态行
            Component statusComp;
            if (!hasCore) statusComp = Component.translatable("hud.yuanliuwujin.destruction.no_core").withStyle(ChatFormatting.GRAY);
            else if (energy <= 0) statusComp = Component.translatable("hud.yuanliuwujin.destruction.no_power").withStyle(ChatFormatting.RED);
            else if (enabledFaces == 0 || machine.getVoidTank().isEmpty()) statusComp = Component.translatable("hud.yuanliuwujin.destruction.standby").withStyle(ChatFormatting.YELLOW);
            else statusComp = Component.translatable("hud.yuanliuwujin.destruction.active").withStyle(ChatFormatting.GREEN);
            lines.add(Component.translatable("hud.yuanliuwujin.destruction.status_label", statusComp));
        }
        lines.add(Component.translatable("hud.sourceflowinfinite.fluid", Component.literal(typeLabel).append(displayName)));

        var vt = machine.getVoidTank();
        lines.add(Component.translatable("hud.yuanliuwujin.void_tank", compactMB(vt.getFluidAmount()), compactMB(vt.getCapacity())).withStyle(ChatFormatting.DARK_PURPLE));

        if (hasCore) {
            var cs = machine.getCoreSlot().getStackInSlot(0);
            boolean oc = InfiniteCoreItem.isOverclocked(cs);
            if (oc) {
                float p = machine.getPressure();
                ChatFormatting pColor = p < 50 ? ChatFormatting.GREEN : p < 80 ? ChatFormatting.YELLOW : ChatFormatting.RED;
                lines.add(Component.translatable("hud.yuanliuwujin.pressure", String.format("%.1f%%", p)).withStyle(pColor));
            }
        }

        Component facesLine = Component.translatable("hud.sourceflowinfinite.faces", enabledFaces,
                enabledFaces == 0 ? Component.translatable("hud.sourceflowinfinite.none").withStyle(ChatFormatting.DARK_GRAY) : Component.literal(facesShort.toString()).withStyle(ChatFormatting.GREEN)).withStyle(ChatFormatting.WHITE);

        renderHudPanel(gg, mc, lines, facesLine);
    }

    private static void renderDestructionMachineHud(GuiGraphics gg, Minecraft mc, DestructionMachineBlockEntity machine) {
        long energy = machine.getEnergyStorage().getEnergyStored();
        long capacity = machine.getEnergyStorage().getMaxEnergyStored();
        boolean hasCore = !machine.getCoreSlot().getStackInSlot(0).isEmpty();



        StringBuilder facesShort = new StringBuilder();
        int enabledFaces = 0;
        for (Direction d : Direction.values()) {
            if (d == Direction.UP || d == Direction.DOWN) continue;
            DestructionMachineBlockEntity.SideMode mode = machine.getSideMode(d);
            if (mode == DestructionMachineBlockEntity.SideMode.OFF) continue;
            enabledFaces++;
            if (!facesShort.isEmpty()) facesShort.append(' ');
            facesShort.append(dirShort(d)).append(mode == DestructionMachineBlockEntity.SideMode.PUSH ? "(P)" : "(B)");
            if (mc.player != null && mc.player.getMainHandItem().getItem() instanceof WrenchItem) {
                facesShort.append(':').append(machine.getFaceRate(d)).append("mB/s");
            }
        }

        Component statusComp;
        var vt = machine.getVoidTank();
        if (!hasCore) statusComp = Component.translatable("hud.yuanliuwujin.destruction.no_core").withStyle(ChatFormatting.GRAY);
        else if (energy <= 0) statusComp = Component.translatable("hud.yuanliuwujin.destruction.no_power").withStyle(ChatFormatting.RED);
        else if (enabledFaces == 0 || vt.getFluidAmount() >= vt.getCapacity()) statusComp = Component.translatable("hud.yuanliuwujin.destruction.standby").withStyle(ChatFormatting.YELLOW);
        else statusComp = Component.translatable("hud.yuanliuwujin.destruction.active").withStyle(ChatFormatting.GREEN);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("hud.sourceflowinfinite.energy", compactFE(energy), compactFE(capacity)).withStyle(ChatFormatting.WHITE));

        // ★ 实时耗电显示
        int fePerTick = machine.getLastTickFEConsumed();
        long fePerSecond = (long) fePerTick * 20;
        lines.add(Component.translatable("hud.sourceflowinfinite.cost", compactFE(fePerSecond), compactFE(fePerTick)).withStyle(ChatFormatting.GOLD));

        lines.add(Component.translatable("hud.sourceflowinfinite.core",
                hasCore
                    ? Component.translatable("hud.sourceflowinfinite.core.inserted").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(" Lv." + com.yelle233.yuanliuwujin.item.DestructionCoreItem.getLevel(machine.getCoreSlot().getStackInSlot(0))
                            + (com.yelle233.yuanliuwujin.item.DestructionCoreItem.isOverclocked(machine.getCoreSlot().getStackInSlot(0)) ? " ★" : ""))
                            .withStyle(ChatFormatting.YELLOW))
                    : Component.translatable("hud.sourceflowinfinite.core.missing").withStyle(ChatFormatting.RED)));
        lines.add(Component.translatable("hud.yuanliuwujin.destruction.status_label", statusComp));

        vt = machine.getVoidTank();
        lines.add(Component.translatable("hud.yuanliuwujin.void_tank", compactMB(vt.getFluidAmount()), compactMB(vt.getCapacity())).withStyle(ChatFormatting.DARK_PURPLE));

        if (hasCore) {
            var cs = machine.getCoreSlot().getStackInSlot(0);
            boolean oc = com.yelle233.yuanliuwujin.item.DestructionCoreItem.isOverclocked(cs);
            if (oc) {
                float p = machine.getPressure();
                ChatFormatting pColor = p < 50 ? ChatFormatting.GREEN : p < 80 ? ChatFormatting.YELLOW : ChatFormatting.RED;
                lines.add(Component.translatable("hud.yuanliuwujin.pressure", String.format("%.1f%%", p)).withStyle(pColor));
            }
        }

        Component facesLine = Component.translatable("hud.sourceflowinfinite.faces", enabledFaces,
                enabledFaces == 0 ? Component.translatable("hud.sourceflowinfinite.none").withStyle(ChatFormatting.DARK_GRAY) : Component.literal(facesShort.toString()).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.WHITE);

        renderHudPanel(gg, mc, lines, facesLine);
    }

    private static void renderHudPanel(GuiGraphics gg, Minecraft mc, List<Component> mainLines, Component facesLine) {
        int padding = 4, gap = 2, maxWidth = 220, lh = mc.font.lineHeight;
        int innerMax = 0;
        for (Component c : mainLines) innerMax = Math.max(innerMax, mc.font.width(c));
        List<FormattedCharSequence> faceSeqs = mc.font.split(facesLine, maxWidth - padding * 2);
        for (FormattedCharSequence seq : faceSeqs) innerMax = Math.max(innerMax, mc.font.width(seq));
        int panelW = Math.min(innerMax + padding * 2, maxWidth);
        faceSeqs = mc.font.split(facesLine, panelW - padding * 2);
        int mainH = mainLines.size() * lh + (mainLines.size() - 1) * gap;
        int facesH = faceSeqs.size() * lh + (faceSeqs.size() - 1) * gap;
        int panelH = padding * 2 + mainH + 5 + facesH;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int x = Mth.clamp(screenW / 2 + 10, 6, screenW - panelW - 6);
        int y = Mth.clamp(screenH / 2 + 10, 6, screenH - panelH - 6);
        gg.fill(x - 1, y - 1, x + panelW + 1, y + panelH + 1, 0x22FFFFFF);
        gg.fill(x, y, x + panelW, y + panelH, 0x55000000);
        int ty = y + padding;
        for (Component c : mainLines) { gg.drawString(mc.font, c, x + padding, ty, 0xFFFFFF, false); ty += lh + gap; }
        ty += 1;
        gg.fill(x + padding, ty, x + panelW - padding, ty + 1, 0x22FFFFFF);
        ty += 3;
        for (FormattedCharSequence seq : faceSeqs) { gg.drawString(mc.font, seq, x + padding, ty, 0xFFFFFF, false); ty += lh + gap; }
    }

    private static String dirShort(Direction d) { return switch (d) { case NORTH -> "N"; case SOUTH -> "S"; case WEST -> "W"; case EAST -> "E"; case DOWN -> "D"; default -> "?"; }; }
    /** 紧凑 FE 显示（纯数值，不含单位） */
    private static String compactFE(long value) {
        if (value < 1_000L) return Long.toString(value);
        if (value < 1_000_000L) return formatDecimal(value / 1_000.0) + "k";
        if (value < 1_000_000_000L) return formatDecimal(value / 1_000_000.0) + "M";
        if (value < 1_000_000_000_000L) return formatDecimal(value / 1_000_000_000.0) + "G";
        return formatDecimal(value / 1_000_000_000_000.0) + "T";
    }
    /**
     * 紧凑流体量显示（mB 输入，以 B 桶为基础单位，与 Jade 一致）。
     * < 1000 mB → 显示为 "X mB"
     * >= 1000 mB → 转换为 B 桶再缩写：B, kB, MB, GB
     */
    private static String compactMB(long mb) {
        if (mb < 1_000L) return mb + " mB";
        double buckets = mb / 1_000.0;
        if (buckets < 1_000.0) return formatDecimal(buckets) + " B";
        if (buckets < 1_000_000.0) return formatDecimal(buckets / 1_000.0) + " kB";
        if (buckets < 1_000_000_000.0) return formatDecimal(buckets / 1_000_000.0) + " MB";
        return formatDecimal(buckets / 1_000_000_000.0) + " GB";
    }
    private static String formatDecimal(double d) { String s = String.format(Locale.ROOT, "%.2f", d); if (s.endsWith("0")) s = s.substring(0, s.length() - 1); if (s.endsWith(".0")) s = s.substring(0, s.length() - 2); return s; }
}
