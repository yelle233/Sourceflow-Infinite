package com.yelle233.yuanliuwujin;

import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity.SideMode;
import com.yelle233.yuanliuwujin.ber.InfiniteFluidMachineBER;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem.BindType;
import com.yelle233.yuanliuwujin.item.WrenchItem;
import com.yelle233.yuanliuwujin.network.ModNetwork;
import com.yelle233.yuanliuwujin.network.WrenchModeScrollMessage;
import com.yelle233.yuanliuwujin.registry.ModBlockEntities;
import com.yelle233.yuanliuwujin.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
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
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.List;
import java.util.Locale;

/**
 * 客户端入口类（1.20.1 Forge 版本）。
 * <p>
 * 分为 MOD 总线事件（BER 注册）和 GAME 总线事件（滚轮、HUD）。
 */
public class SourceflowInfiniteClient {

    /* ====== MOD 总线事件（BER 注册等） ====== */

    @Mod.EventBusSubscriber(modid = SourceflowInfinite.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ModBlockEntities.INFINITE_FLUID_MACHINE.get(), InfiniteFluidMachineBER::new);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                ItemProperties.register(
                        ModItems.INFINITE_CORE.get(),
                        ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, "filled"),
                        (stack, level, entity, seed) -> {
                            CompoundTag tag = stack.getTag();
                            return (tag != null && tag.getBoolean("Filled")) ? 1.0f : 0.0f;
                        }
                );
            });
        }
    }

    /* ====== GAME 总线事件（滚轮、HUD） ====== */

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

            HitResult hit = mc.hitResult;
            if (!(hit instanceof BlockHitResult bhr)) return;

            BlockPos pos = bhr.getBlockPos();
            if (!(mc.level.getBlockEntity(pos) instanceof InfiniteFluidMachineBlockEntity machine)) return;

            renderMachineHud(event.getGuiGraphics(), mc, machine);
        }
    }

    private static void renderMachineHud(GuiGraphics gg, Minecraft mc, InfiniteFluidMachineBlockEntity machine) {
        // ===== 收集数据 =====
        long energy = machine.getEnergyStorage().getEnergyStored();
        long capacity = machine.getEnergyStorage().getMaxEnergyStored();
        int costPerTick = machine.getFeCostPerTick();
        int costPerSec = costPerTick * 20;
        boolean hasCore = !machine.getCoreSlot().getStackInSlot(0).isEmpty();

        // 获取绑定物质名称（支持流体和化学品）
        BindType bindType = machine.getCoreBindType();
        Component substanceName = machine.getBoundSubstanceName();

        // 用于显示的名称和颜色
        Component displayName;
        if (substanceName == null) {
            displayName = Component.translatable("hud.sourceflowinfinite.none").withStyle(ChatFormatting.DARK_GRAY);
        } else if (bindType == BindType.CHEMICAL) {
            displayName = substanceName.copy().withStyle(ChatFormatting.LIGHT_PURPLE);
        } else {
            displayName = substanceName.copy().withStyle(ChatFormatting.AQUA);
        }

        // 统计启用的面
        StringBuilder facesShort = new StringBuilder();
        int enabledFaces = 0;
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            SideMode mode = machine.getSideMode(d);
            if (mode != SideMode.PULL && mode != SideMode.BOTH) continue;
            enabledFaces++;
            if (!facesShort.isEmpty()) facesShort.append(' ');
            facesShort.append(switch (d) {
                case NORTH -> "N"; case SOUTH -> "S";
                case WEST  -> "W"; case EAST  -> "E";
                case DOWN  -> "D"; default    -> "?";
            });
        }

        // ===== 构建文本行 =====
        Component lineEnergy = Component.translatable(
                "hud.sourceflowinfinite.energy", compactFE(energy), compactFE(capacity)
        ).withStyle(ChatFormatting.WHITE);

        ChatFormatting costColor = costPerSec >= 2000 ? ChatFormatting.RED
                : costPerSec >= 800 ? ChatFormatting.GOLD : ChatFormatting.GREEN;
        Component lineCost = Component.translatable(
                "hud.sourceflowinfinite.cost", compactFE(costPerSec), costPerTick
        ).withStyle(costColor);

        Component lineCore = Component.translatable("hud.sourceflowinfinite.core",
                Component.translatable(hasCore
                        ? "hud.sourceflowinfinite.core.inserted"
                        : "hud.sourceflowinfinite.core.missing"
                ).withStyle(hasCore ? ChatFormatting.GREEN : ChatFormatting.RED));

        String typeLabel = switch (bindType) {
            case FLUID -> "§b[Fluid]§r ";
            case CHEMICAL -> "§d[Chemical]§r ";
            default -> "";
        };
        Component lineFluid = Component.translatable("hud.sourceflowinfinite.fluid",
                Component.literal(typeLabel).append(displayName));

        Component lineFaces = Component.translatable("hud.sourceflowinfinite.faces",
                enabledFaces,
                enabledFaces == 0
                        ? Component.translatable("hud.sourceflowinfinite.none").withStyle(ChatFormatting.DARK_GRAY)
                        : Component.literal(facesShort.toString()).withStyle(ChatFormatting.GREEN)
        ).withStyle(ChatFormatting.WHITE);

        List<Component> mainLines = List.of(lineEnergy, lineCost, lineCore, lineFluid);

        // ===== 布局 =====
        int padding = 4, gap = 2, maxWidth = 190;
        int lh = mc.font.lineHeight;

        int innerMax = 0;
        for (Component c : mainLines) innerMax = Math.max(innerMax, mc.font.width(c));

        List<FormattedCharSequence> faceLines = mc.font.split(lineFaces, maxWidth - padding * 2);
        for (FormattedCharSequence seq : faceLines) innerMax = Math.max(innerMax, mc.font.width(seq));

        int panelW = Math.min(innerMax + padding * 2, maxWidth);
        faceLines = mc.font.split(lineFaces, panelW - padding * 2);

        int mainH = mainLines.size() * lh + (mainLines.size() - 1) * gap;
        int facesH = faceLines.size() * lh + (faceLines.size() - 1) * gap;
        int panelH = padding * 2 + mainH + 5 + facesH;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int x = Mth.clamp(screenW / 2 + 10, 6, screenW - panelW - 6);
        int y = Mth.clamp(screenH / 2 + 10, 6, screenH - panelH - 6);

        // ===== 绘制 =====
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

        for (FormattedCharSequence seq : faceLines) {
            gg.drawString(mc.font, seq, x + padding, ty, 0xFFFFFF, false);
            ty += lh + gap;
        }
    }

    /* ====== 数值缩写 ====== */

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
