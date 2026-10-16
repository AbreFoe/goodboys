package com.goodboys.registry;

import com.goodboys.GoodBoys;
import com.goodboys.crafting.DogWorkbenchRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, GoodBoys.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, GoodBoys.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<DogWorkbenchRecipe>> DOG_WORKBENCH =
            TYPES.register("dog_workbench", () -> RecipeType.simple(GoodBoys.id("dog_workbench")));
    public static final DeferredHolder<RecipeSerializer<?>, DogWorkbenchRecipe.Serializer> DOG_WORKBENCH_SERIALIZER =
            SERIALIZERS.register("dog_workbench", DogWorkbenchRecipe.Serializer::new);

    private ModRecipes() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
    }
}
