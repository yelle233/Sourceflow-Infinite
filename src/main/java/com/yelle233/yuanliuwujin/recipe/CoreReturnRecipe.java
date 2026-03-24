package com.yelle233.yuanliuwujin.recipe;

import com.google.gson.JsonObject;
import com.yelle233.yuanliuwujin.registry.ModRecipeSerializers;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * 自定义配方：合成后返还核心（1.20.1 Forge 版本）
 */
public class CoreReturnRecipe implements CraftingRecipe {

    private final ShapedRecipe baseRecipe;
    private final ResourceLocation id;

    public CoreReturnRecipe(ResourceLocation id, ShapedRecipe baseRecipe) {
        this.id = id;
        this.baseRecipe = baseRecipe;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return baseRecipe.matches(container, level);
    }

    @Override
    public @NotNull ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        return baseRecipe.assemble(container, registryAccess);
    }

    @Override
    public @NotNull NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && (stack.is(ItemTags.create(new ResourceLocation("yuanliuwujin", "infinite_cores")))
                || stack.is(ItemTags.create(new ResourceLocation("yuanliuwujin", "destruction_cores"))))) {
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
    public @NotNull ItemStack getResultItem(RegistryAccess registryAccess) {
        return baseRecipe.getResultItem(registryAccess);
    }

    @Override
    public @NotNull NonNullList<Ingredient> getIngredients() {
        return baseRecipe.getIngredients();
    }

    @Override
    public @NotNull ResourceLocation getId() {
        return id;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.CORE_RETURN.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return RecipeType.CRAFTING;
    }

    @Override
    public boolean isSpecial() {
        return false;
    }

    @Override
    public @NotNull CraftingBookCategory category() {
        return baseRecipe.category();
    }

    public ShapedRecipe getBaseRecipe() {
        return baseRecipe;
    }

    public static class Serializer implements RecipeSerializer<CoreReturnRecipe> {
        private static final RecipeSerializer<ShapedRecipe> SHAPED_SERIALIZER = RecipeSerializer.SHAPED_RECIPE;

        @Override
        public @NotNull CoreReturnRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            ShapedRecipe baseRecipe = SHAPED_SERIALIZER.fromJson(recipeId, json);
            return new CoreReturnRecipe(recipeId, baseRecipe);
        }

        @Override
        public @Nullable CoreReturnRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            ShapedRecipe baseRecipe = SHAPED_SERIALIZER.fromNetwork(recipeId, buffer);
            if (baseRecipe == null) return null;
            return new CoreReturnRecipe(recipeId, baseRecipe);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, CoreReturnRecipe recipe) {
            SHAPED_SERIALIZER.toNetwork(buffer, recipe.getBaseRecipe());
        }
    }
}
