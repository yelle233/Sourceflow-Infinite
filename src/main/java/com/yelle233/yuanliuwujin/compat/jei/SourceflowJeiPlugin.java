package com.yelle233.yuanliuwujin.compat.jei;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.compat.MekanismChecker;
import com.yelle233.yuanliuwujin.registry.ModBlocks;
import com.yelle233.yuanliuwujin.registry.ModFluids;
import com.yelle233.yuanliuwujin.registry.Modconfigs;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 源流无尽 JEI 插件。
 * <p>
 * 注册配方分类：
 * <ul>
 *   <li>销毁机器：任意流体 → 虚空流体</li>
 *   <li>无限流体机器：虚空流体 → 任意流体</li>
 *   <li>（Mekanism 可选）销毁机器：化学品 → 虚空流体</li>
 *   <li>（Mekanism 可选）无限流体机器：虚空流体 → 化学品</li>
 * </ul>
 */
@JeiPlugin
public class SourceflowJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID =
            ResourceLocation.fromNamespaceAndPath(SourceflowInfinite.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();

        // 流体配方分类（始终注册）
        registration.addRecipeCategories(new DestructionCategory(guiHelper));
        registration.addRecipeCategories(new InfiniteCategory(guiHelper));

        // Mekanism 化学品配方分类（仅在 Mekanism 加载时注册）
        if (MekanismChecker.isLoaded()) {
            com.yelle233.yuanliuwujin.compat.mekanism.MekJeiHelper.registerCategories(registration);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.DESTRUCTION_MACHINE.get()),
                DestructionCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.INFINITE_FLUID_MACHINE.get()),
                InfiniteCategory.RECIPE_TYPE);

        if (MekanismChecker.isLoaded()) {
            com.yelle233.yuanliuwujin.compat.mekanism.MekJeiHelper.registerCatalysts(registration);
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // ── 流体配方 ──
        registerFluidRecipes(registration);

        // ── Mekanism 化学品配方（可选） ──
        if (MekanismChecker.isLoaded()) {
            com.yelle233.yuanliuwujin.compat.mekanism.MekJeiHelper.registerRecipes(registration);
        }
    }

    private void registerFluidRecipes(IRecipeRegistration registration) {
        List<FluidConversionRecipe> destructionRecipes = new ArrayList<>();
        List<FluidConversionRecipe> infiniteRecipes = new ArrayList<>();

        Fluid voidSource = ModFluids.VOID_FLUID_SOURCE.get();
        Fluid voidFlowing = ModFluids.VOID_FLUID_FLOWING.get();

        int destroyRatio = safeGetRatio(Modconfigs.DESTROY_RATIO_L1, 1000);
        int infiniteRatio = safeGetRatio(Modconfigs.INFINITE_RATIO_L1, 1000);

        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid == Fluids.EMPTY) continue;
            if (fluid == voidSource || fluid == voidFlowing) continue;
            if (!fluid.isSource(fluid.defaultFluidState())) continue;

            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
            if (Modconfigs.isFluidBanned(fluidId)) continue;

            destructionRecipes.add(new FluidConversionRecipe(
                    new FluidStack(fluid, destroyRatio),
                    new FluidStack(voidSource, 1)
            ));

            infiniteRecipes.add(new FluidConversionRecipe(
                    new FluidStack(voidSource, infiniteRatio),
                    new FluidStack(fluid, 1)
            ));
        }

        registration.addRecipes(DestructionCategory.RECIPE_TYPE, destructionRecipes);
        registration.addRecipes(InfiniteCategory.RECIPE_TYPE, infiniteRecipes);
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
