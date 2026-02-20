package com.yelle233.yuanliuwujin.compat.mekanism;

import com.yelle233.yuanliuwujin.registry.ModBlocks;
import com.yelle233.yuanliuwujin.registry.ModFluids;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Mekanism 化学品 JEI 集成帮助类。
 * <p>
 * <b>核心原理</b>：不自己创建 {@code IIngredientType<ChemicalStack>}，
 * 而是从 JEI 的 {@link IIngredientManager#getRegisteredIngredientTypes()}
 * 中动态查找 Mekanism 注册的实例。
 * <p>
 * <b>重要</b>：此类引用了 Mekanism API 类，只能在确认 Mekanism 已加载后调用！
 */
public final class MekJeiHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("yuanliuwujin/MekJeiHelper");

    private MekJeiHelper() {}

    /**
     * Mekanism 在 JEI 中注册的 ChemicalStack 原料类型。
     * 在 {@link #registerRecipes} 中从 JEI 的 IIngredientManager 动态查找并缓存。
     */
    @Nullable
    private static IIngredientType<ChemicalStack> chemicalType = null;

    /** 获取缓存的化学品原料类型，供 Category 在 setRecipe 中使用 */
    @Nullable
    public static IIngredientType<ChemicalStack> getChemicalType() {
        return chemicalType;
    }

    /** 注册化学品配方分类 */
    public static void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new ChemicalDestructionCategory(guiHelper));
        registration.addRecipeCategories(new ChemicalInfiniteCategory(guiHelper));
    }

    /** 注册催化剂 */
    public static void registerCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.DESTRUCTION_MACHINE.get()),
                ChemicalDestructionCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.INFINITE_FLUID_MACHINE.get()),
                ChemicalInfiniteCategory.RECIPE_TYPE);
    }

    /**
     * 从 JEI 的 IIngredientManager 中查找 Mekanism 注册的 ChemicalStack 原料类型，
     * 然后遍历化学品注册表生成配方。
     */
    @SuppressWarnings("unchecked")
    public static void registerRecipes(IRecipeRegistration registration) {
        // ── 第一步：从 JEI 中动态查找 Mekanism 注册的 IIngredientType<ChemicalStack> ──
        IIngredientManager ingredientManager = registration.getIngredientManager();
        chemicalType = null;

        for (IIngredientType<?> type : ingredientManager.getRegisteredIngredientTypes()) {
            if (type.getIngredientClass() == ChemicalStack.class) {
                chemicalType = (IIngredientType<ChemicalStack>) type;
                break;
            }
        }

        if (chemicalType == null) {
            // Mekanism 没有在 JEI 中注册 ChemicalStack 类型（可能版本不兼容），跳过
            LOGGER.warn("Mekanism is loaded but ChemicalStack is not registered as a JEI ingredient type. " +
                    "Chemical JEI recipes will not be available.");
            return;
        }

        LOGGER.info("Found Mekanism ChemicalStack JEI ingredient type, registering chemical recipes.");

        // ── 第二步：生成配方 ──
        List<ChemicalConversionRecipe> destructionRecipes = new ArrayList<>();
        List<ChemicalConversionRecipe> infiniteRecipes = new ArrayList<>();

        int destroyRatio = safeGetRatio(Modconfigs.DESTROY_RATIO_L1, 1000);
        int infiniteRatio = safeGetRatio(Modconfigs.INFINITE_RATIO_L1, 1000);

        for (Chemical chemical : MekanismAPI.CHEMICAL_REGISTRY) {
            if (chemical.isEmptyType()) continue;

            ChemicalStack chemStack = chemical.getStack(1);
            if (chemStack.isEmpty()) continue;

            // 销毁配方：ratio mB 化学品 → 1 mB 虚空流体
            destructionRecipes.add(new ChemicalConversionRecipe(
                    chemical.getStack(destroyRatio),
                    new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), 1)
            ));

            // 无限配方：ratio mB 虚空流体 → 1 mB 化学品
            infiniteRecipes.add(new ChemicalConversionRecipe(
                    chemical.getStack(1),
                    new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), infiniteRatio)
            ));
        }

        registration.addRecipes(ChemicalDestructionCategory.RECIPE_TYPE, destructionRecipes);
        registration.addRecipes(ChemicalInfiniteCategory.RECIPE_TYPE, infiniteRecipes);
    }

    private static int safeGetRatio(Object configValue, int defaultValue) {
        try {
            if (configValue instanceof net.neoforged.neoforge.common.ModConfigSpec.IntValue intValue) {
                return intValue.get();
            }
        } catch (Exception ignored) {}
        return defaultValue;
    }
}
