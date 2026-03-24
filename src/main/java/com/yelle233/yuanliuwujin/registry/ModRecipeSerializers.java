package com.yelle233.yuanliuwujin.registry;

import com.yelle233.yuanliuwujin.SourceflowInfinite;
import com.yelle233.yuanliuwujin.recipe.CoreReturnRecipe;
import com.yelle233.yuanliuwujin.recipe.InfiniteCoreUpgradeRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 配方序列化器注册表（1.20.1 Forge 版本）
 */
public class ModRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, SourceflowInfinite.MODID);

    public static final RegistryObject<RecipeSerializer<InfiniteCoreUpgradeRecipe>> INFINITE_CORE_UPGRADE =
            RECIPE_SERIALIZERS.register("infinite_core_upgrade", InfiniteCoreUpgradeRecipe.Serializer::new);

    public static final RegistryObject<RecipeSerializer<CoreReturnRecipe>> CORE_RETURN =
            RECIPE_SERIALIZERS.register("core_return", CoreReturnRecipe.Serializer::new);
}
