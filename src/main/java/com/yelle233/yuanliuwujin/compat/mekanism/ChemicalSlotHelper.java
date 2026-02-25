package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.chemical.slurry.SlurryStack;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeIngredientRole;

import java.util.List;

/**
 * 辅助工具：向 JEI RecipeLayoutBuilder 添加化学品槽（1.20.1 Forge 版本）。
 * <p>
 * Mekanism 10.4.x（1.20.1）使用四种独立化学品类型，
 * 此类通过 instanceof 检测传入的化学品对象类型，
 * 并使用对应的 JEI 原料类型注册槽位。
 * <p>
 * 调用方须通过完全限定名调用，且只在 {@code recipe.isChemical()} 为 true 时调用。
 */
public final class ChemicalSlotHelper {

    private ChemicalSlotHelper() {}

    /**
     * 向 builder 中添加一个化学品输入槽（16×16）。
     *
     * @param builder   JEI 配方布局构建器
     * @param chemical  化学品对象（GasStack / InfusionStack / PigmentStack / SlurryStack）
     * @param x         槽的 X 坐标
     * @param y         槽的 Y 坐标
     */
    public static void addInputSlot(IRecipeLayoutBuilder builder, Object chemical, int x, int y) {
        addSlot(builder, chemical, RecipeIngredientRole.INPUT, x, y);
    }

    /**
     * 向 builder 中添加一个化学品输出槽（16×16）。
     *
     * @param builder   JEI 配方布局构建器
     * @param chemical  化学品对象（GasStack / InfusionStack / PigmentStack / SlurryStack）
     * @param x         槽的 X 坐标
     * @param y         槽的 Y 坐标
     */
    public static void addOutputSlot(IRecipeLayoutBuilder builder, Object chemical, int x, int y) {
        addSlot(builder, chemical, RecipeIngredientRole.OUTPUT, x, y);
    }

    @SuppressWarnings("unchecked")
    private static void addSlot(IRecipeLayoutBuilder builder, Object chemical,
                                RecipeIngredientRole role, int x, int y) {
        if (chemical instanceof GasStack gasStack) {
            IIngredientType<GasStack> type = MekJeiHelper.getGasType();
            if (type == null) return;
            builder.addSlot(role, x, y)
                    .addIngredients(type, List.of(gasStack));

        } else if (chemical instanceof InfusionStack infusionStack) {
            IIngredientType<InfusionStack> type = MekJeiHelper.getInfusionType();
            if (type == null) return;
            builder.addSlot(role, x, y)
                    .addIngredients(type, List.of(infusionStack));

        } else if (chemical instanceof PigmentStack pigmentStack) {
            IIngredientType<PigmentStack> type = MekJeiHelper.getPigmentType();
            if (type == null) return;
            builder.addSlot(role, x, y)
                    .addIngredients(type, List.of(pigmentStack));

        } else if (chemical instanceof SlurryStack slurryStack) {
            IIngredientType<SlurryStack> type = MekJeiHelper.getSlurryType();
            if (type == null) return;
            builder.addSlot(role, x, y)
                    .addIngredients(type, List.of(slurryStack));
        }
    }
}
