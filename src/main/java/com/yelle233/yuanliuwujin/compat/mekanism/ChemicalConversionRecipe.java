package com.yelle233.yuanliuwujin.compat.mekanism;

import mekanism.api.chemical.ChemicalStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * JEI 虚拟配方：化学品与虚空流体之间的转换。
 * <p>
 * 销毁配方：chemical = 输入化学品，voidFluid = 输出虚空流体<br>
 * 无限配方：voidFluid = 输入虚空流体，chemical = 输出化学品
 */
public record ChemicalConversionRecipe(ChemicalStack chemical, FluidStack voidFluid) {
}
