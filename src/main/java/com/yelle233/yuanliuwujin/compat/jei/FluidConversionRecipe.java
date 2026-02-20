package com.yelle233.yuanliuwujin.compat.jei;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * JEI 虚拟配方，表示一个流体转换关系。
 * <p>
 * 对于销毁配方：input = 任意流体，output = 虚空流体<br>
 * 对于无限配方：input = 虚空流体，output = 任意流体
 */
public record FluidConversionRecipe(FluidStack input, FluidStack output) {
}
