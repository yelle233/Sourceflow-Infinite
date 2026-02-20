package com.yelle233.yuanliuwujin.compat.jei;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.registry.ModBlocks;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * 销毁机器 JEI 配方分类。
 * <p>
 * 显示：任意流体 → 虚空流体，附带各核心等级转换比。
 */
public class DestructionCategory implements IRecipeCategory<FluidConversionRecipe> {

    public static final RecipeType<FluidConversionRecipe> RECIPE_TYPE =
            RecipeType.create(SourceflowInfinite.MODID, "destruction", FluidConversionRecipe.class);

    // ═══════════════════════════════════════════════════════════════
    //  布局常量 —— 修改这里即可调整整体排版
    // ═══════════════════════════════════════════════════════════════

    /** 配方界面总宽度 */
    private static final int GUI_WIDTH  = 160;
    /** 配方界面总高度 */
    private static final int GUI_HEIGHT = 80;

    /** 流体槽宽度 */
    private static final int TANK_W = 16;
    /** 流体槽高度 */
    private static final int TANK_H = 40;

    /** 输入流体槽位置 */
    private static final int INPUT_X = 10;
    private static final int INPUT_Y = 6;

    /** 输出流体槽位置 */
    private static final int OUTPUT_X = 68;
    private static final int OUTPUT_Y = 6;

    /** 箭头位置 */
    private static final int ARROW_X = 36;
    private static final int ARROW_Y = 17;

    /** 输入 mB 文字位置（流体槽下方） */
    private static final int INPUT_LABEL_X = 6;
    private static final int INPUT_LABEL_Y = 50;

    /** 输出 mB 文字位置 */
    private static final int OUTPUT_LABEL_X = 64;
    private static final int OUTPUT_LABEL_Y = 50;

    /** 转换比信息区域 */
    private static final int RATIO_X          = 98;
    private static final int RATIO_Y          = 4;
    private static final int RATIO_LINE_H     = 10;
    private static final int RATIO_HEADER_GAP = 2;

    /** 颜色 */
    private static final int COLOR_TEXT   = 0xFF333333;
    private static final int COLOR_HEADER = 0xFF555555;

    // ═══════════════════════════════════════════════════════════════

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
    private static String formatDecimal(double d) { String s = String.format(Locale.ROOT, "%.1f", d); return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s; }

    private final IDrawable icon;
    private final IDrawable arrow;
    private final Component title;

    public DestructionCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.DESTRUCTION_MACHINE.get()));
        this.arrow = guiHelper.drawableBuilder(
                ResourceLocation.fromNamespaceAndPath("jei", "textures/jei/atlas/gui/recipe_arrow.png"),
                0, 0, 24, 17
        ).setTextureSize(24, 17).build();
        this.title = Component.translatable("jei.yuanliuwujin.category.destruction");
    }

    @Override public RecipeType<FluidConversionRecipe> getRecipeType() { return RECIPE_TYPE; }
    @Override public Component getTitle() { return title; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth()  { return GUI_WIDTH; }
    @Override public int getHeight() { return GUI_HEIGHT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FluidConversionRecipe recipe, IFocusGroup focuses) {
        int inputAmt  = recipe.input().getAmount();
        int outputAmt = recipe.output().getAmount();

        // 每个槽的容量 = 自身的量 → 两边都显示"满格"，视觉平衡
        // 鼠标悬停的 tooltip 仍正确显示实际 mB 数值
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, INPUT_Y)
                .setFluidRenderer(inputAmt, false, TANK_W, TANK_H)
                .addIngredient(NeoForgeTypes.FLUID_STACK, recipe.input());

        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .setFluidRenderer(outputAmt, false, TANK_W, TANK_H)
                .addIngredient(NeoForgeTypes.FLUID_STACK, recipe.output());
    }

    @Override
    public void draw(FluidConversionRecipe recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;

        // 箭头
        arrow.draw(guiGraphics, ARROW_X, ARROW_Y);

        // 流体槽下方的 mB 数量标注
        guiGraphics.drawString(font,
                compactMB(recipe.input().getAmount()) , INPUT_LABEL_X, INPUT_LABEL_Y, COLOR_TEXT, false);
        guiGraphics.drawString(font,
                compactMB(recipe.output().getAmount()) , OUTPUT_LABEL_X, OUTPUT_LABEL_Y, COLOR_TEXT, false);

        // 核心等级转换比
        int y = RATIO_Y;
        guiGraphics.drawString(font,
                Component.translatable("jei.yuanliuwujin.ratio_header"),
                RATIO_X, y, COLOR_HEADER, false);
        y += RATIO_LINE_H + RATIO_HEADER_GAP;

        int[] ratios = {
                Modconfigs.DESTROY_RATIO_L1.get(),
                Modconfigs.DESTROY_RATIO_L2.get(),
                Modconfigs.DESTROY_RATIO_L3.get(),
                Modconfigs.DESTROY_RATIO_L4.get(),
                Modconfigs.DESTROY_RATIO_OC.get()
        };
        String[] labels = {"L1", "L2", "L3", "L4", "OC"};
        int[] colors = {0xFF888888, 0xFF55AA55, 0xFF5555FF, 0xFFAA55AA, 0xFFFF5555};

        for (int i = 0; i < 5; i++) {
            guiGraphics.drawString(font,
                    labels[i] + ": " + ratios[i] + ":1mB", RATIO_X, y, colors[i], false);
            y += RATIO_LINE_H;
        }
    }
}
