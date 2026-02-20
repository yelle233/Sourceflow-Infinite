package com.yelle233.yuanliuwujin.compat.mekanism;

import com.yelle233.yuanliuwujin.compat.mekanism.MekJeiHelper;
import mekanism.api.chemical.ChemicalStack;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeIngredientRole;

/**
 * 辅助工具：向 JEI RecipeLayoutBuilder 添加化学品槽。
 * <p>
 * 此类放在 mekanism 包下，以隔离对 {@code ChemicalStack} 的直接引用，
 * 防止 {@code DestructionCategory} 等始终加载的类意外触发 Mekanism 类加载。
 * <p>
 * 调用方（{@code DestructionCategory}, {@code InfiniteCategory}）须通过完全限定名调用，
 * 且只在 {@code recipe.isChemical()} 为 true 时才调用——此时 Mekanism 必然已加载。
 */
public final class ChemicalSlotHelper {

    private ChemicalSlotHelper() {}

    /**
     * 向 builder 中添加一个 16×40 的化学品输入槽。
     *
     * @param builder   JEI 配方布局构建器
     * @param chemical  化学品对象（实际为 ChemicalStack，此处以 Object 传入避免调用方触发类加载）
     * @param x         槽的 X 坐标
     * @param y         槽的 Y 坐标
     */
    public static void addInputSlot(IRecipeLayoutBuilder builder, Object chemical, int x, int y) {
        IIngredientType<ChemicalStack> type = MekJeiHelper.getChemicalType();
        if (type == null) return;

        ChemicalStack stack = (ChemicalStack) chemical;
        builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                .setCustomRenderer(type, new ChemicalTankRenderer(stack.getAmount()))
                .addIngredient(type, stack);
    }

    /**
     * 向 builder 中添加一个 16×40 的化学品输出槽。
     */
    public static void addOutputSlot(IRecipeLayoutBuilder builder, Object chemical, int x, int y) {
        IIngredientType<ChemicalStack> type = MekJeiHelper.getChemicalType();
        if (type == null) return;

        ChemicalStack stack = (ChemicalStack) chemical;
        builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
                .setCustomRenderer(type, new ChemicalTankRenderer(stack.getAmount()))
                .addIngredient(type, stack);
    }
}
