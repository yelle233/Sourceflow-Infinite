package com.yelle233.yuanliuwujin.compat.mekanism;

import com.yelle233.yuanliuwujin.compat.jei.ConversionRecipe;
import com.yelle233.yuanliuwujin.compat.jei.DestructionCategory;
import com.yelle233.yuanliuwujin.compat.jei.InfiniteCategory;
import com.yelle233.yuanliuwujin.registry.ModFluids;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.chemical.slurry.Slurry;
import mekanism.api.chemical.slurry.SlurryStack;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraftforge.fluids.FluidStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Mekanism 化学品 JEI 集成帮助类（1.20.1 Forge 版本）。
 * <p>
 * 1.20.1 的 Mekanism 10.4.x 使用四种独立化学品类型：
 * Gas / InfuseType / Pigment / Slurry，各自有独立的 JEI 原料类型。
 * <p>
 * 化学品配方被注册到主分类（{@link DestructionCategory} / {@link InfiniteCategory}）中，
 * 与流体配方共用同一个 JEI 分类栏。
 * <p>
 * <b>重要</b>：此类引用了 Mekanism API 类，只能在确认 Mekanism 已加载后调用！
 */
public final class MekJeiHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("yuanliuwujin/MekJeiHelper");

    // 缓存 Mekanism 在 JEI 中注册的四种化学品原料类型
    @Nullable private static IIngredientType<GasStack> gasType = null;
    @Nullable private static IIngredientType<InfusionStack> infusionType = null;
    @Nullable private static IIngredientType<PigmentStack> pigmentType = null;
    @Nullable private static IIngredientType<SlurryStack> slurryType = null;

    @Nullable public static IIngredientType<GasStack> getGasType() { return gasType; }
    @Nullable public static IIngredientType<InfusionStack> getInfusionType() { return infusionType; }
    @Nullable public static IIngredientType<PigmentStack> getPigmentType() { return pigmentType; }
    @Nullable public static IIngredientType<SlurryStack> getSlurryType() { return slurryType; }

    /**
     * 动态查找 Mekanism 注册的四种化学品原料类型，
     * 然后向主分类贡献化学品配方。
     */
    @SuppressWarnings("unchecked")
    public static void registerRecipes(IRecipeRegistration registration) {
        // ── 第一步：从 JEI 查找 Mekanism 注册的四种原料类型 ──
        IIngredientManager manager = registration.getIngredientManager();
        gasType = null;
        infusionType = null;
        pigmentType = null;
        slurryType = null;

        for (IIngredientType<?> type : manager.getRegisteredIngredientTypes()) {
            Class<?> clazz = type.getIngredientClass();
            if (clazz == GasStack.class) {
                gasType = (IIngredientType<GasStack>) type;
            } else if (clazz == InfusionStack.class) {
                infusionType = (IIngredientType<InfusionStack>) type;
            } else if (clazz == PigmentStack.class) {
                pigmentType = (IIngredientType<PigmentStack>) type;
            } else if (clazz == SlurryStack.class) {
                slurryType = (IIngredientType<SlurryStack>) type;
            }
        }

        boolean anyFound = gasType != null || infusionType != null
                || pigmentType != null || slurryType != null;
        if (!anyFound) {
            LOGGER.warn("[yuanliuwujin] Mekanism is loaded but no chemical JEI ingredient types found. " +
                    "Chemical JEI recipes will be skipped.");
            return;
        }

        LOGGER.info("[yuanliuwujin] Mekanism chemical JEI types found (gas={}, infusion={}, pigment={}, slurry={}), adding recipes.",
                gasType != null, infusionType != null, pigmentType != null, slurryType != null);

        // ── 第二步：遍历四种注册表，生成配方 ──
        List<ConversionRecipe> destructionRecipes = new ArrayList<>();
        List<ConversionRecipe> infiniteRecipes    = new ArrayList<>();

        int destroyRatio = Modconfigs.DESTROY_RATIO_L1.get();
        int infiniteRatio = Modconfigs.INFINITE_RATIO_L1.get();
        FluidStack voidFluid1 = new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), 1);

        // Gas
        if (gasType != null) {
            for (Gas gas : MekanismAPI.gasRegistry()) {
                if (gas.isEmptyType()) continue;
                destructionRecipes.add(new ConversionRecipe(
                        null, new GasStack(gas, destroyRatio), destroyRatio,
                        voidFluid1.copy()
                ));
                infiniteRecipes.add(new ConversionRecipe(
                        null, new GasStack(gas, 1), 1,
                        new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), infiniteRatio)
                ));
            }
        }

        // InfuseType
        if (infusionType != null) {
            for (InfuseType type : MekanismAPI.infuseTypeRegistry()) {
                if (type.isEmptyType()) continue;
                destructionRecipes.add(new ConversionRecipe(
                        null, new InfusionStack(type, destroyRatio), destroyRatio,
                        voidFluid1.copy()
                ));
                infiniteRecipes.add(new ConversionRecipe(
                        null, new InfusionStack(type, 1), 1,
                        new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), infiniteRatio)
                ));
            }
        }

        // Pigment
        if (pigmentType != null) {
            for (Pigment pigment : MekanismAPI.pigmentRegistry()) {
                if (pigment.isEmptyType()) continue;
                destructionRecipes.add(new ConversionRecipe(
                        null, new PigmentStack(pigment, destroyRatio), destroyRatio,
                        voidFluid1.copy()
                ));
                infiniteRecipes.add(new ConversionRecipe(
                        null, new PigmentStack(pigment, 1), 1,
                        new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), infiniteRatio)
                ));
            }
        }

        // Slurry
        if (slurryType != null) {
            for (Slurry slurry : MekanismAPI.slurryRegistry()) {
                if (slurry.isEmptyType()) continue;
                destructionRecipes.add(new ConversionRecipe(
                        null, new SlurryStack(slurry, destroyRatio), destroyRatio,
                        voidFluid1.copy()
                ));
                infiniteRecipes.add(new ConversionRecipe(
                        null, new SlurryStack(slurry, 1), 1,
                        new FluidStack(ModFluids.VOID_FLUID_SOURCE.get(), infiniteRatio)
                ));
            }
        }

        registration.addRecipes(DestructionCategory.RECIPE_TYPE, destructionRecipes);
        registration.addRecipes(InfiniteCategory.RECIPE_TYPE, infiniteRecipes);
    }

    private MekJeiHelper() {}
}
