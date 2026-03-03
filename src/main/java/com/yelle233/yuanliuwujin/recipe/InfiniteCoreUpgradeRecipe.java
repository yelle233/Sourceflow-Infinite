package com.yelle233.yuanliuwujin.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem;
import com.yelle233.yuanliuwujin.registry.ModRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/**
 * 自定义配方：无限核心升级配方，保留绑定数据
 */
public class InfiniteCoreUpgradeRecipe implements CraftingRecipe {

    private final ShapedRecipe baseRecipe;

    public InfiniteCoreUpgradeRecipe(ShapedRecipe baseRecipe) {
        this.baseRecipe = baseRecipe;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return baseRecipe.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        // 获取基础配方的结果
        ItemStack result = baseRecipe.assemble(input, registries);

        // 查找输入中的无限核心并复制其绑定数据
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof InfiniteCoreItem) {
                // 复制绑定数据到结果物品
                InfiniteCoreItem.copyBindingData(stack, result);
                break;
            }
        }

        return result;
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
        return ModRecipeSerializers.INFINITE_CORE_UPGRADE.get();
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
        return false; // 允许在 JEI 中显示配方
    }

    public ShapedRecipe getBaseRecipe() {
        return baseRecipe;
    }

    // 序列化器
    public static class Serializer implements RecipeSerializer<InfiniteCoreUpgradeRecipe> {

        public static final MapCodec<ShapedRecipe> SHAPED_RECIPE_CODEC = ShapedRecipe.Serializer.CODEC;
        public static final StreamCodec<RegistryFriendlyByteBuf, ShapedRecipe> SHAPED_RECIPE_STREAM_CODEC =
            ShapedRecipe.Serializer.STREAM_CODEC;

        private final MapCodec<InfiniteCoreUpgradeRecipe> codec = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                SHAPED_RECIPE_CODEC.forGetter(InfiniteCoreUpgradeRecipe::getBaseRecipe)
            ).apply(instance, InfiniteCoreUpgradeRecipe::new)
        );

        private final StreamCodec<RegistryFriendlyByteBuf, InfiniteCoreUpgradeRecipe> streamCodec =
            StreamCodec.composite(
                SHAPED_RECIPE_STREAM_CODEC,
                InfiniteCoreUpgradeRecipe::getBaseRecipe,
                InfiniteCoreUpgradeRecipe::new
            );

        @Override
        public MapCodec<InfiniteCoreUpgradeRecipe> codec() {
            return codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, InfiniteCoreUpgradeRecipe> streamCodec() {
            return streamCodec;
        }
    }
}
