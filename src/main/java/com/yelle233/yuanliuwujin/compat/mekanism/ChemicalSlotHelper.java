package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.chemical.ChemicalStack;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeIngredientRole;

import java.util.List;

/**
 * 辅助工具：向 JEI RecipeLayoutBuilder 添加化学品槽。
 * <p>
 * 此类放在 mekanism 包下，以隔离对 {@code ChemicalStack} 的直接引用，
 * 防止 {@code DestructionCategory} 等始终加载的类意外触发 Mekanism 类加载。
 * <p>
 * 调用方须通过完全限定名调用，且只在 {@code recipe.isChemical()} 为 true 时调用。
 *
 * <p><b>FIX v2.1 - 修复化学品 JEI 槽使用静态纯色贴图问题：</b>
 * 原版本使用 {@link ChemicalTankRenderer}（自定义 {@code IIngredientRenderer}）渲染
 * 化学品为静态色条，与 JEI 侧边栏的动态动画效果不一致、观感差。
 * 现在不再指定自定义渲染器，直接让 JEI 使用 Mekanism 自行注册的动态渲染器
 * （会显示化学品图标 + 颜色动画，与 JEI 物品列表完全一致）。
 */
public final class ChemicalSlotHelper {

    private ChemicalSlotHelper() {}

    /**
     * 向 builder 中添加一个标准尺寸的化学品输入槽（16×16）。
     * 使用 Mekanism 在 JEI 中注册的原生渲染器，支持动态动画效果。
     *
     * @param builder   JEI 配方布局构建器
     * @param chemical  化学品对象（实际为 ChemicalStack，以 Object 传入避免调用方触发类加载）
     * @param x         槽的 X 坐标
     * @param y         槽的 Y 坐标
     */
    public static void addInputSlot(IRecipeLayoutBuilder builder, Object chemical, int x, int y) {
        IIngredientType<ChemicalStack> type = MekJeiHelper.getChemicalType();
        if (type == null) return;

        ChemicalStack stack = (ChemicalStack) chemical;
        // ★ FIX: 不调用 .setCustomRenderer()，让 JEI 使用 Mekanism 注册的原生动态渲染器
        builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                .addIngredients(type, List.of(stack));
    }

    /**
     * 向 builder 中添加一个标准尺寸的化学品输出槽（16×16）。
     * 使用 Mekanism 在 JEI 中注册的原生渲染器，支持动态动画效果。
     *
     * @param builder   JEI 配方布局构建器
     * @param chemical  化学品对象（实际为 ChemicalStack，以 Object 传入避免调用方触发类加载）
     * @param x         槽的 X 坐标
     * @param y         槽的 Y 坐标
     */
    public static void addOutputSlot(IRecipeLayoutBuilder builder, Object chemical, int x, int y) {
        IIngredientType<ChemicalStack> type = MekJeiHelper.getChemicalType();
        if (type == null) return;

        ChemicalStack stack = (ChemicalStack) chemical;
        // ★ FIX: 同上，使用原生动态渲染器
        builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
                .addIngredients(type, List.of(stack));
    }
}
