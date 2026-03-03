package com.yelle233.yuanliuwujin.recipe;

import com.google.gson.JsonObject;
import com.yelle233.yuanliuwujin.item.InfiniteCoreItem;
import com.yelle233.yuanliuwujin.registry.ModRecipeSerializers;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * 自定义配方：无限核心升级配方，保留绑定数据（1.20.1 Forge 版本）
 * <p>
 * 包装一个 ShapedRecipe，在合成时复制输入核心的绑定数据到输出核心。
 */
public class InfiniteCoreUpgradeRecipe implements CraftingRecipe {

    private final ShapedRecipe baseRecipe;
    private final ResourceLocation id;

    public InfiniteCoreUpgradeRecipe(ResourceLocation id, ShapedRecipe baseRecipe) {
        this.id = id;
        this.baseRecipe = baseRecipe;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return baseRecipe.matches(container, level);
    }

    @Override
    public @NotNull ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        // 获取基础配方的结果
        ItemStack result = baseRecipe.assemble(container, registryAccess);

        // 查找输入中的无限核心并复制其绑定数据
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
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
        // 将在 ModRecipeSerializers 中注册
        return ModRecipeSerializers.INFINITE_CORE_UPGRADE.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return RecipeType.CRAFTING;
    }

    @Override
    public boolean isSpecial() {
        return false; // 允许在 JEI 中显示配方
    }

    @Override
    public @NotNull CraftingBookCategory category() {
        return baseRecipe.category();
    }

    public ShapedRecipe getBaseRecipe() {
        return baseRecipe;
    }

    // ── 序列化器 ──────────────────────────────────────────────

    public static class Serializer implements RecipeSerializer<InfiniteCoreUpgradeRecipe> {

        private static final RecipeSerializer<ShapedRecipe> SHAPED_SERIALIZER = RecipeSerializer.SHAPED_RECIPE;

        @Override
        public @NotNull InfiniteCoreUpgradeRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            // 使用原版 ShapedRecipe 序列化器解析 JSON
            ShapedRecipe baseRecipe = SHAPED_SERIALIZER.fromJson(recipeId, json);
            return new InfiniteCoreUpgradeRecipe(recipeId, baseRecipe);
        }

        @Override
        public @Nullable InfiniteCoreUpgradeRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            // 使用原版 ShapedRecipe 序列化器从网络读取
            ShapedRecipe baseRecipe = SHAPED_SERIALIZER.fromNetwork(recipeId, buffer);
            if (baseRecipe == null) return null;
            return new InfiniteCoreUpgradeRecipe(recipeId, baseRecipe);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, InfiniteCoreUpgradeRecipe recipe) {
            // 使用原版 ShapedRecipe 序列化器写入网络
            SHAPED_SERIALIZER.toNetwork(buffer, recipe.getBaseRecipe());
        }
    }
}
