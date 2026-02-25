package com.yelle233.yuanliuwujin.compat.jei;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.registry.ModBlocks;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
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
 * 销毁机器 JEI 分类：任意流体或化学品 → 虚空流体（1.20.1 Forge 版本）。
 * <p>
 * 同一分类栏同时展示流体和化学品配方。
 */
public class DestructionCategory implements IRecipeCategory<ConversionRecipe> {

    public static final RecipeType<ConversionRecipe> RECIPE_TYPE =
            RecipeType.create(SourceflowInfinite.MODID, "destruction", ConversionRecipe.class);

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

    // 化学品输入槽：标准 16×16，垂直居中于流体槽区域
    private static final int CHEM_INPUT_X  = INPUT_X;
    private static final int CHEM_INPUT_Y  = INPUT_Y + (TANK_H - 18) / 2;

    private static final int INPUT_LABEL_X  = 6;
    private static final int INPUT_LABEL_Y  = 50;
    private static final int OUTPUT_LABEL_X = 64;
    private static final int OUTPUT_LABEL_Y = 50;

    private static final int RATIO_X      = 98;
    private static final int RATIO_Y      = 4;
    private static final int RATIO_LINE_H = 10;

    private static final int COLOR_TEXT   = 0xFF333333;
    private static final int COLOR_HEADER = 0xFF555555;
    // ════════════════════════════════════

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable arrow;
    private final Component title;

    public DestructionCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(GUI_WIDTH, GUI_HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.DESTRUCTION_MACHINE.get()));
        this.arrow = guiHelper.drawableBuilder(
                ResourceLocation.fromNamespaceAndPath("jei", "textures/jei/atlas/gui/recipe_arrow.png"),
                0, 0, 24, 17
        ).setTextureSize(24, 17).build();
        this.title = Component.translatable("jei.yuanliuwujin.category.destruction");
    }

    @Override public RecipeType<ConversionRecipe> getRecipeType() { return RECIPE_TYPE; }
    @Override public Component getTitle() { return title; }
    @Override public IDrawable getBackground() { return background; }
    @Override public IDrawable getIcon() { return icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ConversionRecipe recipe, IFocusGroup focuses) {
        int voidAmt = recipe.voidFluid().getAmount();

        if (recipe.isChemical()) {
            com.yelle233.yuanliuwujin.compat.mekanism.ChemicalSlotHelper
                    .addInputSlot(builder, recipe.chemicalOther(), CHEM_INPUT_X, CHEM_INPUT_Y);
        } else {
            int fluidAmt = recipe.fluidOther().getAmount();
            builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, INPUT_Y)
                    .setFluidRenderer(fluidAmt, false, TANK_W, TANK_H)
                    .addIngredient(ForgeTypes.FLUID_STACK, recipe.fluidOther());
        }

        // 虚空流体输出槽
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .setFluidRenderer(voidAmt, false, TANK_W, TANK_H)
                .addIngredient(ForgeTypes.FLUID_STACK, recipe.voidFluid());
    }

    @Override
    public void draw(ConversionRecipe recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;

        arrow.draw(guiGraphics, ARROW_X, ARROW_Y);

        guiGraphics.drawString(font,
                recipe.otherAmount() + " mB", INPUT_LABEL_X, INPUT_LABEL_Y, COLOR_TEXT, false);
        guiGraphics.drawString(font,
                recipe.voidFluid().getAmount() + " mB", OUTPUT_LABEL_X, OUTPUT_LABEL_Y, COLOR_TEXT, false);

        drawRatios(guiGraphics, font, Modconfigs.DESTROY_RATIO_L1.get(),
                Modconfigs.DESTROY_RATIO_L2.get(), Modconfigs.DESTROY_RATIO_L3.get(),
                Modconfigs.DESTROY_RATIO_L4.get(), Modconfigs.DESTROY_RATIO_OC.get());
    }

    // ═══════════ 静态工具（供 InfiniteCategory 复用） ═══════════

    static void drawRatios(GuiGraphics g, Font font, int l1, int l2, int l3, int l4, int oc) {
        int y = RATIO_Y;
        g.drawString(font,
                Component.translatable("jei.yuanliuwujin.ratio_header"),
                RATIO_X, y, COLOR_HEADER, false);
        y += RATIO_LINE_H + 2;

        int[]    ratios  = {l1, l2, l3, l4, oc};
        String[] labels  = {"L1", "L2", "L3", "L4", "OC"};
        int[]    colors  = {0xFF888888, 0xFF55AA55, 0xFF5555FF, 0xFFAA55AA, 0xFFFF5555};

        for (int i = 0; i < 5; i++) {
            g.drawString(font, labels[i] + ": " + ratios[i] + ":1", RATIO_X, y, colors[i], false);
            y += RATIO_LINE_H;
        }
    }
}
