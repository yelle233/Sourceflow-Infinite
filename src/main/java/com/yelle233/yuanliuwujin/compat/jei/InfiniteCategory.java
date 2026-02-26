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

/**
 * 无限流体机器 JEI 分类：虚空流体 → 任意流体或化学品。
 * <p>
 * 同一分类栏同时展示流体和化学品配方。
 */
public class InfiniteCategory implements IRecipeCategory<ConversionRecipe> {

    public static final RecipeType<ConversionRecipe> RECIPE_TYPE =
            RecipeType.create(SourceflowInfinite.MODID, "infinite", ConversionRecipe.class);

    // ═══════════ 布局常量 ═══════════
    private static final int GUI_WIDTH  = 160;
    private static final int GUI_HEIGHT = 80;
    private static final int TANK_W = 16;
    private static final int TANK_H = 40;

    // 流体槽：16×40 高槽
    private static final int INPUT_X  = 10;
    private static final int INPUT_Y  = 6;
    private static final int OUTPUT_X = 68;
    private static final int OUTPUT_Y = 6;
    private static final int ARROW_X  = 36;
    private static final int ARROW_Y  = 17;

    // 化学品输出槽：标准 16×16，垂直居中于流体槽区域
    private static final int CHEM_OUTPUT_X = OUTPUT_X;
    private static final int CHEM_OUTPUT_Y = OUTPUT_Y + (TANK_H - 18) / 2;  // ≈ 17

    private static final int INPUT_LABEL_X  = 6;
    private static final int INPUT_LABEL_Y  = 50;
    private static final int OUTPUT_LABEL_X = 64;
    private static final int OUTPUT_LABEL_Y = 50;

    private static final int COLOR_TEXT = 0xFF333333;
    // ════════════════════════════════════════════════

    private final IDrawable icon;
    private final IDrawable arrow;
    private final Component title;

    public InfiniteCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.INFINITE_FLUID_MACHINE.get()));
        this.arrow = guiHelper.drawableBuilder(
                ResourceLocation.fromNamespaceAndPath("jei", "textures/jei/atlas/gui/recipe_arrow.png"),
                0, 0, 24, 17
        ).setTextureSize(24, 17).build();
        this.title = Component.translatable("jei.yuanliuwujin.category.infinite");
    }

    @Override public RecipeType<ConversionRecipe> getRecipeType() { return RECIPE_TYPE; }
    @Override public Component getTitle() { return title; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth()  { return GUI_WIDTH; }
    @Override public int getHeight() { return GUI_HEIGHT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ConversionRecipe recipe, IFocusGroup focuses) {
        int voidAmt = recipe.voidFluid().getAmount();

        // 虚空流体输入槽
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, INPUT_Y)
                .setFluidRenderer(voidAmt, false, TANK_W, TANK_H)
                .addIngredient(NeoForgeTypes.FLUID_STACK, recipe.voidFluid());

        if (recipe.isChemical()) {
            // 使用 Mekanism 注册的动态动画渲染器
            com.yelle233.yuanliuwujin.compat.mekanism.ChemicalSlotHelper
                    .addOutputSlot(builder, recipe.chemicalOther(), CHEM_OUTPUT_X, CHEM_OUTPUT_Y);
        } else {
            // 普通流体输出槽
            int fluidAmt = recipe.fluidOther().getAmount();
            builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                    .setFluidRenderer(fluidAmt, false, TANK_W, TANK_H)
                    .addIngredient(NeoForgeTypes.FLUID_STACK, recipe.fluidOther());
        }
    }

    @Override
    public void draw(ConversionRecipe recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;

        arrow.draw(guiGraphics, ARROW_X, ARROW_Y);

        guiGraphics.drawString(font,
                recipe.voidFluid().getAmount() + " mB", INPUT_LABEL_X, INPUT_LABEL_Y, COLOR_TEXT, false);

        // 右侧输出数量标注
        guiGraphics.drawString(font,
                recipe.otherAmount() + " mB", OUTPUT_LABEL_X, OUTPUT_LABEL_Y, COLOR_TEXT, false);

        DestructionCategory.drawRatios(guiGraphics, font,
                Modconfigs.INFINITE_RATIO_L1.get(), Modconfigs.INFINITE_RATIO_L2.get(),
                Modconfigs.INFINITE_RATIO_L3.get(), Modconfigs.INFINITE_RATIO_L4.get(),
                Modconfigs.INFINITE_RATIO_OC.get());
    }
}
