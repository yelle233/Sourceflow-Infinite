package com.yelle233.yuanliuwujin.compat.mekanism;

import com.yelle233.yuanliuwujin.compat.jei.ConversionRecipe;
import com.yelle233.yuanliuwujin.compat.jei.DestructionCategory;
import com.yelle233.yuanliuwujin.compat.jei.InfiniteCategory;
import com.yelle233.yuanliuwujin.registry.ModFluids;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import net.neoforged.neoforge.fluids.FluidStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Mekanism 化学品 JEI 集成帮助类。
 * <p>
 * 化学品配方被注册到主分类（{@link DestructionCategory} / {@link InfiniteCategory}）中，
 * 与流体配方共用同一个 JEI 分类栏。
 * <p>
 * <b>重要</b>：此类引用了 Mekanism API 类，只能在确认 Mekanism 已加载后调用！
 */
public final class MekJeiHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("yuanliuwujin/MekJeiHelper");

    /**
     * Mekanism 在 JEI 中注册的 ChemicalStack 原料类型实例。
     * 在 {@link #registerRecipes} 中从 JEI 的 IIngredientManager 动态查找并缓存。
     */
    @Nullable
    private static IIngredientType<ChemicalStack> chemicalType = null;

    /** 获取缓存的化学品原料类型，供 {@link ChemicalSlotHelper} 在 setRecipe 中使用 */
    @Nullable
    public static IIngredientType<ChemicalStack> getChemicalType() {
        return chemicalType;
    }

    /**
     * 动态查找 Mekanism 注册的 ChemicalStack 原料类型，
     * 然后向主分类贡献化学品配方。
     */
    @SuppressWarnings("unchecked")
    public static void registerRecipes(IRecipeRegistration registration) {
        // ── 第一步：从 JEI 查找 Mekanism 注册的 IIngredientType<ChemicalStack> ──
        IIngredientManager manager = registration.getIngredientManager();
        chemicalType = null;

        for (IIngredientType<?> type : manager.getRegisteredIngredientTypes()) {
            if (type.getIngredientClass() == ChemicalStack.class) {
                chemicalType = (IIngredientType<ChemicalStack>) type;
                break;
            }
        }

        if (chemicalType == null) {
            LOGGER.warn("[yuanliuwujin] Mekanism is loaded but ChemicalStack is not registered " +
                    "as a JEI ingredient type. Chemical JEI recipes will be skipped.");
            return;
        }

        LOGGER.info("[yuanliuwujin] Mekanism ChemicalStack JEI type found, adding chemical recipes.");

        // ── 第二步：生成配方（注册到主分类） ──
        List<ConversionRecipe> destructionRecipes = new ArrayList<>();
        List<ConversionRecipe> infiniteRecipes    = new ArrayList<>();

        int destroyRatio = Modconfigs.DESTROY_RATIO_L1.get();
        int infiniteRatio = Modconfigs.INFINITE_RATIO_L1.get();

        for (Chemical chemical : MekanismAPI.CHEMICAL_REGISTRY) {
            if (chemical.isEmptyType()) continue;

            ChemicalStack baseStack = chemical.getStack(1);
            if (baseStack.isEmpty()) continue;

            // 销毁配方：destroyRatio mB 化学品 → 1 mB 虚空流体
            destructionRecipes.add(new ConversionRecipe(
                    null,
                    chemical.getStack(destroyRatio),
                    destroyRatio,
                    new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), 1)
            ));

            // 无限配方：infiniteRatio mB 虚空流体 → 1 mB 化学品
            infiniteRecipes.add(new ConversionRecipe(
                    null,
                    chemical.getStack(1),
                    1,
                    new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), infiniteRatio)
            ));
        }

        registration.addRecipes(DestructionCategory.RECIPE_TYPE, destructionRecipes);
        registration.addRecipes(InfiniteCategory.RECIPE_TYPE, infiniteRecipes);
    }

    private MekJeiHelper() {}
}
