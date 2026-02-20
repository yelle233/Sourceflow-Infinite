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
 * 销毁机器 JEI 分类：任意流体或化学品 → 虚空流体。
 * <p>
 * 同一分类栏同时展示流体和化学品配方；化学品槽使用自定义渲染器，
 * 保证与流体槽完全相同的 16×40 尺寸和对齐方式。
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

    public DestructionCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.DESTRUCTION_MACHINE.get()));
        this.arrow = guiHelper.drawableBuilder(
                ResourceLocation.fromNamespaceAndPath("jei", "textures/jei/atlas/gui/recipe_arrow.png"),
                0, 0, 24, 17
        ).setTextureSize(24, 17).build();
        this.title = Component.translatable("jei.yuanliuwujin.category.destruction");
    }

    @Override public RecipeType<ConversionRecipe> getRecipeType() { return RECIPE_TYPE; }
    @Override public Component getTitle() { return title; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth()  { return GUI_WIDTH; }
    @Override public int getHeight() { return GUI_HEIGHT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ConversionRecipe recipe, IFocusGroup focuses) {
        int voidAmt = recipe.voidFluid().getAmount();

        if (recipe.isChemical()) {
            // 化学品输入槽（通过隔离的 helper 访问 Mekanism 类）
            com.yelle233.yuanliuwujin.compat.mekanism.ChemicalSlotHelper
                    .addInputSlot(builder, recipe.chemicalOther(), INPUT_X, INPUT_Y);
        } else {
            // 普通流体输入槽
            int fluidAmt = recipe.fluidOther().getAmount();
            builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, INPUT_Y)
                    .setFluidRenderer(fluidAmt, false, TANK_W, TANK_H)
                    .addIngredient(NeoForgeTypes.FLUID_STACK, recipe.fluidOther());
        }

        // 虚空流体输出槽（始终是流体）
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .setFluidRenderer(voidAmt, false, TANK_W, TANK_H)
                .addIngredient(NeoForgeTypes.FLUID_STACK, recipe.voidFluid());
    }

    @Override
    public void draw(ConversionRecipe recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;

        arrow.draw(guiGraphics, ARROW_X, ARROW_Y);

        // 左侧输入数量标注
        String inputLabel = recipe.isChemical()
                ? recipe.chemicalOther().toString()   // 不直接用，下面单独处理
                : recipe.fluidOther().getAmount() + " mB";
        if (!recipe.isChemical()) {
            guiGraphics.drawString(font, inputLabel, INPUT_LABEL_X, INPUT_LABEL_Y, COLOR_TEXT, false);
        }
        // 右侧输出数量标注
        guiGraphics.drawString(font,
                recipe.voidFluid().getAmount() + " mB", OUTPUT_LABEL_X, OUTPUT_LABEL_Y, COLOR_TEXT, false);

        // 转换比
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

        int[] ratios  = {l1, l2, l3, l4, oc};
        String[] labels = {"L1", "L2", "L3", "L4", "OC"};
        int[]    colors = {0xFF888888, 0xFF55AA55, 0xFF5555FF, 0xFFAA55AA, 0xFFFF5555};

        for (int i = 0; i < 5; i++) {
            g.drawString(font, labels[i] + ": " + ratios[i] + ":1", RATIO_X, y, colors[i], false);
            y += RATIO_LINE_H;
        }
    }
}
