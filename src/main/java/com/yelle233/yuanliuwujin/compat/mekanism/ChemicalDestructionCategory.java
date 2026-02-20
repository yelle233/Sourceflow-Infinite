package com.yelle233.yuanliuwujin.compat.mekanism;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.registry.ModBlocks;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import mekanism.api.chemical.ChemicalStack;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientType;
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

/**
 * 销毁机器化学品 JEI 分类：化学品 → 虚空流体。
 * <p>
 * 化学品使用 Mekanism 在 JEI 中注册的 {@code IIngredientType<ChemicalStack>}
 * （由 {@link MekJeiHelper} 动态查找），因此化学品能正确显示图标并支持点击跳转。
 */
public class ChemicalDestructionCategory implements IRecipeCategory<ChemicalConversionRecipe> {

    public static final RecipeType<ChemicalConversionRecipe> RECIPE_TYPE =
            RecipeType.create(SourceflowInfinite.MODID, "chemical_destruction", ChemicalConversionRecipe.class);

    // ═══════════ 布局常量 ═══════════
    private static final int GUI_WIDTH  = 160;
    private static final int GUI_HEIGHT = 80;
    private static final int TANK_W = 16;
    private static final int TANK_H = 40;

    private static final int INPUT_X  = 10;
    private static final int INPUT_Y  = 6;
    private static final int OUTPUT_X = 68;
    private static final int OUTPUT_Y = 6;
    private static final int ARROW_X  = 36;
    private static final int ARROW_Y  = 17;

    private static final int INPUT_LABEL_X  = 6;
    private static final int INPUT_LABEL_Y  = 50;
    private static final int OUTPUT_LABEL_X = 64;
    private static final int OUTPUT_LABEL_Y = 50;

    private static final int RATIO_X      = 98;
    private static final int RATIO_Y      = 4;
    private static final int RATIO_LINE_H = 10;

    private static final int COLOR_TEXT   = 0xFF333333;
    private static final int COLOR_HEADER = 0xFF555555;
    // ═══════════════════════════════════

    private final IDrawable icon;
    private final IDrawable arrow;
    private final Component title;

    public ChemicalDestructionCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.DESTRUCTION_MACHINE.get()));
        this.arrow = guiHelper.drawableBuilder(
                ResourceLocation.fromNamespaceAndPath("jei", "textures/jei/atlas/gui/recipe_arrow.png"),
                0, 0, 24, 17
        ).setTextureSize(24, 17).build();
        this.title = Component.translatable("jei.yuanliuwujin.category.chemical_destruction");
    }

    @Override public RecipeType<ChemicalConversionRecipe> getRecipeType() { return RECIPE_TYPE; }
    @Override public Component getTitle() { return title; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth()  { return GUI_WIDTH; }
    @Override public int getHeight() { return GUI_HEIGHT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ChemicalConversionRecipe recipe, IFocusGroup focuses) {
        IIngredientType<ChemicalStack> chemType = MekJeiHelper.getChemicalType();
        int fluidAmt = recipe.voidFluid().getAmount();

        // 输入：化学品（使用 Mekanism 注册的原料类型）
        if (chemType != null) {
            builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, INPUT_Y)
                    .addIngredient(chemType, recipe.chemical());
        }

        // 输出：虚空流体
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .setFluidRenderer(fluidAmt, false, TANK_W, TANK_H)
                .addIngredient(NeoForgeTypes.FLUID_STACK, recipe.voidFluid());
    }

    @Override
    public void draw(ChemicalConversionRecipe recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;

        arrow.draw(guiGraphics, ARROW_X, ARROW_Y);

        guiGraphics.drawString(font,
                recipe.chemical().getAmount() + " mB", INPUT_LABEL_X, INPUT_LABEL_Y, COLOR_TEXT, false);
        guiGraphics.drawString(font,
                recipe.voidFluid().getAmount() + " mB", OUTPUT_LABEL_X, OUTPUT_LABEL_Y, COLOR_TEXT, false);

        drawRatios(guiGraphics, font, true);
    }

    // ═══════════ 公共绘制工具（供 InfiniteCategory 复用） ═══════════

    static void drawRatios(GuiGraphics guiGraphics, Font font, boolean isDestruction) {
        int y = RATIO_Y;
        guiGraphics.drawString(font,
                Component.translatable("jei.yuanliuwujin.ratio_header"),
                RATIO_X, y, COLOR_HEADER, false);
        y += RATIO_LINE_H + 2;

        int[] ratios = isDestruction
                ? new int[]{
                    Modconfigs.DESTROY_RATIO_L1.get(), Modconfigs.DESTROY_RATIO_L2.get(),
                    Modconfigs.DESTROY_RATIO_L3.get(), Modconfigs.DESTROY_RATIO_L4.get(),
                    Modconfigs.DESTROY_RATIO_OC.get()}
                : new int[]{
                    Modconfigs.INFINITE_RATIO_L1.get(), Modconfigs.INFINITE_RATIO_L2.get(),
                    Modconfigs.INFINITE_RATIO_L3.get(), Modconfigs.INFINITE_RATIO_L4.get(),
                    Modconfigs.INFINITE_RATIO_OC.get()};

        String[] labels = {"L1", "L2", "L3", "L4", "OC"};
        int[] colors = {0xFF888888, 0xFF55AA55, 0xFF5555FF, 0xFFAA55AA, 0xFFFF5555};

        for (int i = 0; i < 5; i++) {
            guiGraphics.drawString(font,
                    labels[i] + ": " + ratios[i] + ":1", RATIO_X, y, colors[i], false);
            y += RATIO_LINE_H;
        }
    }
}
