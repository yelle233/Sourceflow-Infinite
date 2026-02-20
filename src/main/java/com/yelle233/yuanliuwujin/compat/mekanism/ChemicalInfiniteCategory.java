package com.yelle233.yuanliuwujin.compat.mekanism;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.registry.ModBlocks;
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
 * 无限流体机器化学品 JEI 分类：虚空流体 → 化学品。
 * <p>
 * 化学品使用 Mekanism 在 JEI 中注册的 {@code IIngredientType<ChemicalStack>}
 * （由 {@link MekJeiHelper} 动态查找），因此化学品能正确显示图标并支持点击跳转。
 */
public class ChemicalInfiniteCategory implements IRecipeCategory<ChemicalConversionRecipe> {

    public static final RecipeType<ChemicalConversionRecipe> RECIPE_TYPE =
            RecipeType.create(SourceflowInfinite.MODID, "chemical_infinite", ChemicalConversionRecipe.class);

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

    private static final int COLOR_TEXT = 0xFF333333;
    // ═══════════════════════════════════

    private final IDrawable icon;
    private final IDrawable arrow;
    private final Component title;

    public ChemicalInfiniteCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.INFINITE_FLUID_MACHINE.get()));
        this.arrow = guiHelper.drawableBuilder(
                ResourceLocation.fromNamespaceAndPath("jei", "textures/jei/atlas/gui/recipe_arrow.png"),
                0, 0, 24, 17
        ).setTextureSize(24, 17).build();
        this.title = Component.translatable("jei.yuanliuwujin.category.chemical_infinite");
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

        // 输入：虚空流体
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, INPUT_Y)
                .setFluidRenderer(fluidAmt, false, TANK_W, TANK_H)
                .addIngredient(NeoForgeTypes.FLUID_STACK, recipe.voidFluid());

        // 输出：化学品（使用 Mekanism 注册的原料类型）
        if (chemType != null) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                    .addIngredient(chemType, recipe.chemical());
        }
    }

    @Override
    public void draw(ChemicalConversionRecipe recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;

        arrow.draw(guiGraphics, ARROW_X, ARROW_Y);

        guiGraphics.drawString(font,
                recipe.voidFluid().getAmount() + " mB", INPUT_LABEL_X, INPUT_LABEL_Y, COLOR_TEXT, false);
        guiGraphics.drawString(font,
                recipe.chemical().getAmount() + " mB", OUTPUT_LABEL_X, OUTPUT_LABEL_Y, COLOR_TEXT, false);

        ChemicalDestructionCategory.drawRatios(guiGraphics, font, false);
    }
}
