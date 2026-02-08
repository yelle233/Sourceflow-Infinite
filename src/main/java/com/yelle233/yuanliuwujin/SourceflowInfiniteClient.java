package com.yelle233.yuanliuwujin;

import com.yelle233.yuanliuwujin.blockentity.InfiniteFluidMachineBlockEntity;
import com.yelle233.yuanliuwujin.ebr.InfiniteFluidMachineBER;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;


@Mod(value = SourceflowInfinite.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = SourceflowInfinite.MODID, value = Dist.CLIENT)
public class SourceflowInfiniteClient {
    public SourceflowInfiniteClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {

    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.INFINITE_FLUID_MACHINE.get(),
                InfiniteFluidMachineBER::new
        );
    }


    //扳手改变模式:客户端监听滚轮
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // 只在潜行时触发
        if (!player.isShiftKeyDown()) return;

        ItemStack main = player.getMainHandItem();
        if (!(main.getItem() instanceof WrenchItem)) return;

        double dy = e.getScrollDeltaY();
        if (dy == 0) return;

        int delta = dy > 0 ? 1 : -1;

        // 发包给服务端，让服务端改扳手 NBT
        PacketDistributor.sendToServer(new WrenchModeScrollPayload(delta));

        // 可选：阻止原版滚轮切物品栏（让体验更像 Create）
        // 不同版本事件取消方法可能不同；如果你的事件支持 setCanceled/cancel 就用之
         e.setCanceled(true);
    }

    //机器GUI显示
    @SubscribeEvent
    public static void onRenderGui(net.neoforged.neoforge.client.event.RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        // 仅当准星命中方块时显示
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr)) return;

        BlockPos pos = bhr.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof InfiniteFluidMachineBlockEntity machine)) return;

        GuiGraphics gg = e.getGuiGraphics();

        // ===== 数据 =====
        long energy = machine.getEnergyStorage().getEnergyStored();
        long cap = machine.getEnergyStorage().getMaxEnergyStored();

        int costPerTick = machine.getFeCostPerTick();
        int costPerSec = costPerTick * 20;

        boolean hasCore = !machine.getCoreSlot().getStackInSlot(0).isEmpty();

        Fluid bound = machine.getBoundSourceFluid();
        Component fluidName = (bound == null)
                ? Component.literal("—")
                : bound.getFluidType().getDescription();

        // Faces：只显示启用的面（PULL/BOTH），用 N/E/S/W/D 缩写
        int enabledFaces = 0;
        StringBuilder facesShort = new StringBuilder();
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;

            var mode = machine.getSideMode(d);
            boolean enabled = (mode == InfiniteFluidMachineBlockEntity.SideMode.PULL
                    || mode == InfiniteFluidMachineBlockEntity.SideMode.BOTH);

            if (!enabled) continue;

            enabledFaces++;
            if (!facesShort.isEmpty()) facesShort.append(' ');

            String letter = switch (d) {
                case NORTH -> "N";
                case SOUTH -> "S";
                case WEST  -> "W";
                case EAST  -> "E";
                case DOWN  -> "D";
                default    -> "?";
            };

            // 如果你想区分 PULL/BOTH，可以改成：N(P) / N(B)
            facesShort.append(letter);
        }

        // ===== 文本行组装（标签灰，数值亮）=====
        Component lineEnergy = Component.literal("Energy ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(compactFE(energy) + "/" + compactFE(cap) + " FE")
                        .withStyle(ChatFormatting.WHITE));

        ChatFormatting costColor =
                (costPerSec >= 2000) ? ChatFormatting.RED :
                        (costPerSec >= 800)  ? ChatFormatting.GOLD :
                                ChatFormatting.GREEN;

        Component lineCost = Component.literal("Cost ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(compactFE(costPerSec) + " FE/s")
                        .withStyle(costColor))
                .append(Component.literal("  (" + costPerTick + "/t)").withStyle(ChatFormatting.DARK_GRAY));

        Component lineCore = Component.literal("Core ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(hasCore ? "Inserted" : "Missing")
                        .withStyle(hasCore ? ChatFormatting.GREEN : ChatFormatting.RED));

        Component lineFluid = Component.literal("Fluid ").withStyle(ChatFormatting.GRAY)
                .append(fluidName.copy().withStyle(bound == null ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA));

        Component lineFaces = Component.literal("Faces ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("(" + enabledFaces + ") ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(enabledFaces == 0 ? "—" : facesShort.toString())
                        .withStyle(enabledFaces == 0 ? ChatFormatting.DARK_GRAY : ChatFormatting.GREEN));

        List<Component> rawLines = List.of(lineEnergy, lineCost, lineCore, lineFluid);

        // ===== 布局参数 =====
        int padding = 4;
        int gap = 2;

        // 你想要的最大宽度（越小越“机械动力”，越大越信息板）
        int maxWidth = 190;
        int innerMaxWidth = maxWidth - padding * 2;

        // Faces 可能很长：split 成 FormattedCharSequence（1.21 正确类型）
        List<FormattedCharSequence> faceLines = mc.font.split(lineFaces, innerMaxWidth);

        // 计算面板宽度：取所有行的最大宽度（不超过 maxWidth）
        int innerMax = 0;
        for (Component c : rawLines) innerMax = Math.max(innerMax, mc.font.width(c));
        for (FormattedCharSequence seq : faceLines) innerMax = Math.max(innerMax, mc.font.width(seq));
        int panelW = Math.min(innerMax + padding * 2, maxWidth);

        // Faces split 需要根据最终 panelW 再算一次（否则 panelW 变窄时还可能溢出）
        innerMaxWidth = panelW - padding * 2;
        faceLines = mc.font.split(lineFaces, innerMaxWidth);

        // 计算高度
        int lh = mc.font.lineHeight;
        int mainH = rawLines.size() * lh + (rawLines.size() - 1) * gap;
        int facesH = faceLines.size() * lh + (faceLines.size() - 1) * gap;

        int sepH = 5; // 分隔区高度
        int panelH = padding * 2 + mainH + sepH + facesH;

        // ===== 悬浮准心附近（右下），并 clamp 防止出屏 =====
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int cx = screenW / 2;
        int cy = screenH / 2;

        int x = cx + 10;
        int y = cy + 10;

        x = Mth.clamp(x, 6, screenW - panelW - 6);
        y = Mth.clamp(y, 6, screenH - panelH - 6);

        // ===== 背景/边框（不挡视野）=====
        int bg = 0x55000000;      // 半透明黑
        int border = 0x22FFFFFF;  // 淡边框
        gg.fill(x - 1, y - 1, x + panelW + 1, y + panelH + 1, border);
        gg.fill(x, y, x + panelW, y + panelH, bg);

        // ===== 绘制文本 =====
        int ty = y + padding;
        for (Component c : rawLines) {
            gg.drawString(mc.font, c, x + padding, ty, 0xFFFFFF, false);
            ty += lh + gap;
        }

        // 分隔线
        ty += 1;
        gg.fill(x + padding, ty, x + panelW - padding, ty + 1, 0x22FFFFFF);
        ty += 3;

        for (FormattedCharSequence seq : faceLines) {
            gg.drawString(mc.font, seq, x + padding, ty, 0xFFFFFF, false);
            ty += lh + gap;
        }
    }

    // ===== 数值缩写 =====
    private static String compactFE(long v) {
        if (v < 1_000L) return Long.toString(v);
        if (v < 1_000_000L) return format1(v / 1_000.0) + "k";
        if (v < 1_000_000_000L) return format1(v / 1_000_000.0) + "M";
        if (v < 1_000_000_000_000L) return format1(v / 1_000_000_000.0) + "G";
        return format1(v / 1_000_000_000_000.0) + "T";
    }

    private static String format1(double d) {
        String s = String.format(Locale.ROOT, "%.1f", d);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
