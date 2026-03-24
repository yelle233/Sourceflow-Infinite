package com.yelle233.yuanliuwujin.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yelle233.yuanliuwujin.registry.ModRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/**
 * 自定义配方：合成后返还核心
 */
public class CoreReturnRecipe implements CraftingRecipe {

    private final ShapedRecipe baseRecipe;

    public CoreReturnRecipe(ShapedRecipe baseRecipe) {
        this.baseRecipe = baseRecipe;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return baseRecipe.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return baseRecipe.assemble(input, registries);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty() && stack.is(net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("yuanliuwujin", "infinite_cores")))
                || stack.is(net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("yuanliuwujin", "destruction_cores")))) {
                remaining.set(i, stack.copy());
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return baseRecipe.canCraftInDimensions(width, height);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return baseRecipe.getResultItem(registries);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return baseRecipe.getIngredients();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.CORE_RETURN.get();
    }

    @Override
    public RecipeType<?> getType() {
        return RecipeType.CRAFTING;
    }

    @Override
    public CraftingBookCategory category() {
        return baseRecipe.category();
    }

    @Override
    public boolean isSpecial() {
        return false;
    }

    public ShapedRecipe getBaseRecipe() {
        return baseRecipe;
    }

    public static class Serializer implements RecipeSerializer<CoreReturnRecipe> {
        private final MapCodec<CoreReturnRecipe> codec = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                ShapedRecipe.Serializer.CODEC.forGetter(CoreReturnRecipe::getBaseRecipe)
            ).apply(instance, CoreReturnRecipe::new)
        );

        private final StreamCodec<RegistryFriendlyByteBuf, CoreReturnRecipe> streamCodec =
            StreamCodec.composite(
                ShapedRecipe.Serializer.STREAM_CODEC,
                CoreReturnRecipe::getBaseRecipe,
                CoreReturnRecipe::new
            );

        @Override
        public MapCodec<CoreReturnRecipe> codec() {
            return codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CoreReturnRecipe> streamCodec() {
            return streamCodec;
        }
    }
}

