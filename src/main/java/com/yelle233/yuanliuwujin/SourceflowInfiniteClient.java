package com.yelle233.yuanliuwujin;

import com.yelle233.yuanliuwujin.ber.DestructionMachineBER;
import com.yelle233.yuanliuwujin.ber.InfiniteFluidMachineBER;
import com.yelle233.yuanliuwujin.blockentity.DestructionMachineBlockEntity;
import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity.SideMode;
import com.yelle233.yuanliuwujin.fluid.VoidFluidType;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem.BindType;
import com.yelle233.yuanliuwujin.item.WrenchItem;
import com.yelle233.yuanliuwujin.network.FaceRateUpdateMessage;
import com.yelle233.yuanliuwujin.registry.ModFluids;
import com.yelle233.yuanliuwujin.registry.ModNetwork;
import com.yelle233.yuanliuwujin.network.WrenchModeScrollMessage;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
import com.yelle233.yuanliuwujin.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 客户端入口类。
 */
public class SourceflowInfiniteClient {

    /* ====== MOD 总线事件 ====== */

    @Mod.EventBusSubscriber(modid = SourceflowInfinite.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ModBlockEntities.INFINITE_FLUID_MACHINE.get(), InfiniteFluidMachineBER::new);
            event.registerBlockEntityRenderer(ModBlockEntities.DESTRUCTION_MACHINE.get(), DestructionMachineBER::new);

        }

        @SubscribeEvent
        public static void onItemColors(RegisterColorHandlersEvent.Item event) {
            event.register((stack, tintIndex) -> {
                return tintIndex == 1 ? VoidFluidType.COLOR_ARGB : 0xFFFFFFFF;
            }, ModItems.VOID_BUCKET.get());
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                // 为所有无限核心注册 filled 属性
                for (var item : new net.minecraft.world.item.Item[]{
                        ModItems.INFINITE_CORE_L1.get(),
                        ModItems.INFINITE_CORE_L2.get(),
                        ModItems.INFINITE_CORE_L3.get(),
                        ModItems.INFINITE_CORE_L4.get(),
                        ModItems.INFINITE_CORE_L4_OC.get()
                }) {
                    ItemProperties.register(item,
                            ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, "filled"),
                            (stack, level, entity, seed) -> {
                                CompoundTag tag = stack.getTag();
                                return (tag != null && tag.getBoolean("Filled")) ? 1.0f : 0.0f;
                            });
                }

                ItemBlockRenderTypes.setRenderLayer(
                        ModFluids.VOID_FLUID_SOURCE.get(), RenderType.translucent());
                ItemBlockRenderTypes.setRenderLayer(
                        ModFluids.VOID_FLUID_FLOWING.get(), RenderType.translucent());
                ItemBlockRenderTypes.setRenderLayer(ModFluids.VOID_FLUID_SOURCE.get(), RenderType.translucent());
                ItemBlockRenderTypes.setRenderLayer(ModFluids.VOID_FLUID_FLOWING.get(), RenderType.translucent());
            });
        }
    }

    /* ====== GAME 总线事件 ====== */

    @Mod.EventBusSubscriber(modid = SourceflowInfinite.MODID, value = Dist.CLIENT)
    public static class GameEvents {

        /* ====== 扳手滚轮 ====== */

        @SubscribeEvent
        public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || !player.isShiftKeyDown()) return;
            if (!(player.getMainHandItem().getItem() instanceof WrenchItem)) return;
            double scrollY = event.getScrollDelta();
            if (scrollY == 0) return;

            // 仅用于切换扳手模式
            ModNetwork.CHANNEL.sendToServer(new WrenchModeScrollMessage(scrollY > 0 ? 1 : -1));
            event.setCanceled(true);
        }

        /* ====== 机器信息 HUD ====== */

        @SubscribeEvent
        public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
            if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.options.hideGui) return;
            LocalPlayer player = mc.player;
            if (player == null || mc.level == null) return;

            // 渲染扳手模式指示器
            renderWrenchModeIndicator(event.getGuiGraphics(), mc, player);

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
    }

    /**
     * 渲染扳手模式指示器（持久显示在物品栏上方）
     */
    private static void renderWrenchModeIndicator(GuiGraphics gg, Minecraft mc, LocalPlayer player) {
        var mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof WrenchItem)) return;

        WrenchItem.WrenchMode mode = WrenchItem.getMode(mainHand);
        Component modeName;

        if (mode == WrenchItem.WrenchMode.IO) {
            modeName = Component.translatable("mode.yuanliuwujin.wrench.io").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        } else {
            modeName = Component.translatable("mode.yuanliuwujin.wrench.config").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        }

        Component fullText = Component.literal(" ")
            .append(Component.translatable("msg.yuanliuwujin.wrench_mode").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
            .append(modeName);

        // 使用 Minecraft 原生的 action bar 位置和样式
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int textWidth = mc.font.width(fullText);
        int x = (screenW - textWidth) / 2;
        int y = screenH - 59; // action bar 位置

        // 绘制文本
        gg.drawString(mc.font, fullText, x, y, 0xFFFFFF, false);
    }

    /* ====== 无限流体机器 HUD ====== */

    private static void renderInfiniteMachineHud(GuiGraphics gg, Minecraft mc,
                                                 InfiniteFluidMachineBlockEntity machine) {
        long energy   = machine.getEnergyStorage().getEnergyStored();
        long capacity = machine.getEnergyStorage().getMaxEnergyStored();
        boolean hasCore = !machine.getCoreSlot().getStackInSlot(0).isEmpty();
        BindType bindType = machine.getCoreBindType();
        Component substanceName = machine.getBoundSubstanceName();

        Component displayName;
        if (substanceName == null) {
            displayName = Component.translatable("hud.sourceflowinfinite.none").withStyle(ChatFormatting.DARK_GRAY);
        } else if (bindType == BindType.CHEMICAL) {
            displayName = substanceName.copy().withStyle(ChatFormatting.LIGHT_PURPLE);
        } else {
            displayName = substanceName.copy().withStyle(ChatFormatting.AQUA);
        }

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

        String typeLabel = switch (bindType) {
            case FLUID    -> "§b[Fluid]§r ";
            case CHEMICAL -> "§d[Chemical]§r ";
            default       -> "";
        };

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("hud.sourceflowinfinite.energy", compactFE(energy), compactFE(capacity)).withStyle(ChatFormatting.WHITE));

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

        {
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
                enabledFaces == 0 ? Component.translatable("hud.sourceflowinfinite.none").withStyle(ChatFormatting.DARK_GRAY)
                        : Component.literal(facesShort.toString()).withStyle(ChatFormatting.GREEN)).withStyle(ChatFormatting.WHITE);

        renderHudPanel(gg, mc, lines, facesLine);
    }

    /* ====== 销毁机器 HUD ====== */

    private static void renderDestructionMachineHud(GuiGraphics gg, Minecraft mc,
                                                    DestructionMachineBlockEntity machine) {
        long energy   = machine.getEnergyStorage().getEnergyStored();
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

        var vt = machine.getVoidTank();
        Component statusComp;
        if (!hasCore) statusComp = Component.translatable("hud.yuanliuwujin.destruction.no_core").withStyle(ChatFormatting.GRAY);
        else if (energy <= 0) statusComp = Component.translatable("hud.yuanliuwujin.destruction.no_power").withStyle(ChatFormatting.RED);
        else if (enabledFaces == 0 || vt.getFluidAmount() >= vt.getCapacity()) statusComp = Component.translatable("hud.yuanliuwujin.destruction.standby").withStyle(ChatFormatting.YELLOW);
        else statusComp = Component.translatable("hud.yuanliuwujin.destruction.active").withStyle(ChatFormatting.GREEN);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("hud.sourceflowinfinite.energy", compactFE(energy), compactFE(capacity)).withStyle(ChatFormatting.WHITE));

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
                enabledFaces == 0 ? Component.translatable("hud.sourceflowinfinite.none").withStyle(ChatFormatting.DARK_GRAY)
                        : Component.literal(facesShort.toString()).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.WHITE);

        renderHudPanel(gg, mc, lines, facesLine);
    }

    /* ====== HUD 面板绘制 ====== */

    private static void renderHudPanel(GuiGraphics gg, Minecraft mc,
                                       List<Component> mainLines, Component facesLine) {
        int padding = 4, gap = 2, maxWidth = 220, lh = mc.font.lineHeight;
        int innerMax = 0;
        for (Component c : mainLines) innerMax = Math.max(innerMax, mc.font.width(c));
        List<FormattedCharSequence> faceSeqs = mc.font.split(facesLine, maxWidth - padding * 2);
        for (FormattedCharSequence seq : faceSeqs) innerMax = Math.max(innerMax, mc.font.width(seq));
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
        for (Component c : mainLines) { gg.drawString(mc.font, c, x + padding, ty, 0xFFFFFF, false); ty += lh + gap; }
        ty += 1;
        gg.fill(x + padding, ty, x + panelW - padding, ty + 1, 0x22FFFFFF);
        ty += 3;
        for (FormattedCharSequence seq : faceSeqs) { gg.drawString(mc.font, seq, x + padding, ty, 0xFFFFFF, false); ty += lh + gap; }
    }

    private static String dirShort(Direction d) {
        return switch (d) { case NORTH -> "N"; case SOUTH -> "S"; case WEST -> "W"; case EAST -> "E"; case DOWN -> "D"; default -> "?"; };
    }

    private static String compactFE(long value) {
        if (value < 1_000L) return Long.toString(value);
        if (value < 1_000_000L) return formatDecimal(value / 1_000.0) + "k";
        if (value < 1_000_000_000L) return formatDecimal(value / 1_000_000.0) + "M";
        if (value < 1_000_000_000_000L) return formatDecimal(value / 1_000_000_000.0) + "G";
        return formatDecimal(value / 1_000_000_000_000.0) + "T";
    }

    private static String compactMB(long mb) {
        if (mb < 1_000L) return mb + " mB";
        double buckets = mb / 1_000.0;
        if (buckets < 1_000.0) return formatDecimal(buckets) + " B";
        if (buckets < 1_000_000.0) return formatDecimal(buckets / 1_000.0) + " kB";
        return formatDecimal(buckets / 1_000_000.0) + " MB";
    }

    private static String formatDecimal(double d) {
        String s = String.format(Locale.ROOT, "%.2f", d);
        if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s;
    }
}

