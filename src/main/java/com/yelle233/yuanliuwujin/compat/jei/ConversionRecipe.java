package com.yelle233.yuanliuwujin.compat.jei;

import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;

/**
 * JEI 虚拟配方：任意流体或化学品与虚空流体之间的转换。
 * <p>
 * 化学品以 {@link Object} 存储（实际类型为 {@code mekanism.api.chemical.ChemicalStack}），
 * 避免在非 Mekanism 环境中触发该类的类加载。
 *
 * @param fluidOther    非虚空流体（流体配方时有值，化学品配方时为 null）
 * @param chemicalOther 化学品（化学品配方时有值，实际为 ChemicalStack；流体配方时为 null）
 * @param voidFluid     虚空流体
 */
public record ConversionRecipe(
        @Nullable FluidStack fluidOther,
        @Nullable Object chemicalOther,
        long chemicalAmount,
        FluidStack voidFluid
) {
    /** 是否为化学品配方 */
    public boolean isChemical() {
        return chemicalOther != null;
    }

    /**
     * 非虚空侧的数量（mB）。流体配方返回 fluidOther 的数量，化学品配方返回 chemicalAmount。
     * 统一供 draw() 使用，避免在 jei 包中引入 Mekanism 类型。
     */
    public long otherAmount() {
        return isChemical() ? chemicalAmount : (fluidOther != null ? fluidOther.getAmount() : 0);
    }
}
