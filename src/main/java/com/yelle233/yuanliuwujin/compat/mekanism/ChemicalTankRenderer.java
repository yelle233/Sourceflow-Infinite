package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.chemical.ChemicalStack;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * 化学品 JEI 槽渲染器：将化学品绘制成 16×40 的竖直色条，与流体槽完全对齐。
 * <p>
 * 使用化学品的 {@code getTint()} 颜色，填充高度按 "数量/容量" 比例缩放（从底部向上）。
 * 边框样式与流体槽一致。
 */
public class ChemicalTankRenderer implements IIngredientRenderer<ChemicalStack> {

    private static final int WIDTH  = 16;
    private static final int HEIGHT = 40;
    private static final int COLOR_BORDER = 0xFF888888;

    private final long capacity;

    /**
     * @param capacity 该槽的容量（mB），用于计算填充高度比例
     */
    public ChemicalTankRenderer(long capacity) {
        this.capacity = Math.max(1, capacity);
    }

    @Override
    public void render(GuiGraphics guiGraphics, ChemicalStack chemical) {
        if (chemical == null || chemical.isEmpty()) return;

        // 化学品颜色（强制不透明）
        int tint = chemical.getChemical().getTint() | 0xFF000000;

        // 按数量/容量比例填充（从底部向上，与流体槽一致）
        int fillH = (int) Math.max(1, Math.min(HEIGHT, HEIGHT * chemical.getAmount() / capacity));
        int top = HEIGHT - fillH;

        guiGraphics.fill(0, top, WIDTH, HEIGHT, tint);

        // 边框（四条线，与流体槽风格相同）
        guiGraphics.fill(0,         0,          WIDTH, 1,      COLOR_BORDER); // 上
        guiGraphics.fill(0,         HEIGHT - 1, WIDTH, HEIGHT, COLOR_BORDER); // 下
        guiGraphics.fill(0,         0,          1,     HEIGHT, COLOR_BORDER); // 左
        guiGraphics.fill(WIDTH - 1, 0,          WIDTH, HEIGHT, COLOR_BORDER); // 右
    }

    @Override
    public List<Component> getTooltip(ChemicalStack chemical, TooltipFlag flag) {
        List<Component> tips = new ArrayList<>();
        if (chemical == null || chemical.isEmpty()) return tips;
        // 化学品名称（Mekanism 提供的本地化名称）
        tips.add(chemical.getTextComponent());
        // 数量
        tips.add(Component.literal(chemical.getAmount() + " mB"));
        return tips;
    }


    public Font getFont() {
        return Minecraft.getInstance().font;
    }

    @Override
    public int getWidth()  { return WIDTH; }

    @Override
    public int getHeight() { return HEIGHT; }
}
