package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.recipe.InfiniteCoreUpgradeRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, SourceflowInfinite.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<InfiniteCoreUpgradeRecipe>> INFINITE_CORE_UPGRADE =
            RECIPE_SERIALIZERS.register("infinite_core_upgrade", InfiniteCoreUpgradeRecipe.Serializer::new);
}
