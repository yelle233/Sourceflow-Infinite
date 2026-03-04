package com.yelle233.yuanliuwujin.client;

import com.yelle233.yuanliuwujin.network.ModNetwork;
import com.yelle233.yuanliuwujin.network.SetFaceRateMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * 速率输入界面（1.20.1 Forge）
 */
public class RateInputScreen extends Screen {
    private final BlockPos machinePos;
    private final Direction face;
    private final int currentRate;
    private EditBox rateInput;

    public RateInputScreen(BlockPos pos, Direction face, int currentRate) {
        super(Component.translatable("gui.yuanliuwujin.rate_input.title"));
        this.machinePos = pos;
        this.face = face;
        this.currentRate = currentRate;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 输入框
        rateInput = new EditBox(this.font, centerX - 100, centerY - 10, 200, 20,
                Component.translatable("gui.yuanliuwujin.rate_input.field"));
        rateInput.setValue(String.valueOf(currentRate));
        rateInput.setFilter(s -> s.matches("\\d*"));
        addRenderableWidget(rateInput);

        // 确定按钮
        addRenderableWidget(Button.builder(
                Component.translatable("gui.yuanliuwujin.rate_input.confirm"),
                btn -> {
                    try {
                        int rate = Integer.parseInt(rateInput.getValue());
                        if (rate >= 1 && rate < Integer.MAX_VALUE) {
                            ModNetwork.CHANNEL.sendToServer(new SetFaceRateMessage(face, rate));
                            this.onClose();
                        }
                    } catch (NumberFormatException ignored) {}
                }
        ).bounds(centerX - 100, centerY + 20, 95, 20).build());

        // 重置按钮
        addRenderableWidget(Button.builder(
                Component.translatable("gui.yuanliuwujin.rate_input.reset"),
                btn -> {
                    ModNetwork.CHANNEL.sendToServer(new SetFaceRateMessage(face, 1));
                    this.onClose();
                }
        ).bounds(centerX + 5, centerY + 20, 95, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 30, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
