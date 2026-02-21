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
 * 分类栏：
 * <ul>
 *   <li>销毁机器：任意流体或化学品 → 虚空流体</li>
 *   <li>无限流体机器：虚空流体 → 任意流体或化学品</li>
 * </ul>
 * 化学品配方（需要 Mekanism）合并进相同分类栏，无单独化学品栏。
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
        // 只有两个分类栏，始终注册
        registration.addRecipeCategories(new DestructionCategory(guiHelper));
        registration.addRecipeCategories(new InfiniteCategory(guiHelper));
        // 化学品配方合并进以上两栏，无需额外分类
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.DESTRUCTION_MACHINE.get()),
                DestructionCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.INFINITE_FLUID_MACHINE.get()),
                InfiniteCategory.RECIPE_TYPE);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // ── 流体配方 ──
        List<ConversionRecipe> destructionRecipes = new ArrayList<>();
        List<ConversionRecipe> infiniteRecipes    = new ArrayList<>();

        Fluid voidSource  = ModFluids.VOID_FLUID_SOURCE.get();
        Fluid voidFlowing = ModFluids.VOID_FLUID_FLOWING.get();

        int destroyRatio  = Modconfigs.DESTROY_RATIO_L1.get();
        int infiniteRatio = Modconfigs.INFINITE_RATIO_L1.get();

        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid == Fluids.EMPTY) continue;
            if (fluid == voidSource || fluid == voidFlowing) continue;
            if (!fluid.isSource(fluid.defaultFluidState())) continue;

            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
            if (Modconfigs.isFluidBanned(fluidId)) continue;

            destructionRecipes.add(new ConversionRecipe(
                    new FluidStack(fluid, destroyRatio), null, 0,
                    new FluidStack(voidSource, 1)
            ));
            infiniteRecipes.add(new ConversionRecipe(
                    new FluidStack(fluid, 1), null, 0,
                    new FluidStack(voidSource, infiniteRatio)
            ));
        }

        registration.addRecipes(DestructionCategory.RECIPE_TYPE, destructionRecipes);
        registration.addRecipes(InfiniteCategory.RECIPE_TYPE, infiniteRecipes);

        // ── Mekanism 化学品配方（合并进同一分类栏） ──
        if (MekanismChecker.isLoaded()) {
            com.yelle233.yuanliuwujin.compat.mekanism.MekJeiHelper.registerRecipes(registration);
        }
    }
}
