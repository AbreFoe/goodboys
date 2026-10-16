package com.goodboys.crafting;

import com.goodboys.registry.ModRecipes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

/** A shaped 3x3 recipe that only the Dog Workbench can make. JSON looks like a vanilla shaped recipe. */
public record DogWorkbenchRecipe(ShapedRecipePattern pattern, ItemStack result) implements Recipe<CraftingInput> {
    @Override
    public boolean matches(CraftingInput input, Level level) {
        return pattern.matches(input);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= pattern.width() && height >= pattern.height();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return pattern.ingredients();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.DOG_WORKBENCH_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.DOG_WORKBENCH.get();
    }

    public static class Serializer implements RecipeSerializer<DogWorkbenchRecipe> {
        private static final MapCodec<DogWorkbenchRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ShapedRecipePattern.MAP_CODEC.forGetter(DogWorkbenchRecipe::pattern),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(DogWorkbenchRecipe::result)
        ).apply(instance, DogWorkbenchRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, DogWorkbenchRecipe> STREAM_CODEC = StreamCodec.composite(
                ShapedRecipePattern.STREAM_CODEC, DogWorkbenchRecipe::pattern,
                ItemStack.STREAM_CODEC, DogWorkbenchRecipe::result,
                DogWorkbenchRecipe::new);

        @Override
        public MapCodec<DogWorkbenchRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DogWorkbenchRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
