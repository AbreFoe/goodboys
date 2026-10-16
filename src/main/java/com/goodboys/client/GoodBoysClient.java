package com.goodboys.client;

import com.goodboys.GoodBoys;
import com.goodboys.registry.ModEntities;
import com.goodboys.registry.ModMenus;
import com.goodboys.registry.ModParticles;
import com.goodboys.registry.ModRecipes;

import net.minecraft.client.RecipeBookCategories;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterRecipeBookCategoriesEvent;

// Client-only entry point: renderers, keybinds and screens live under this package.
@Mod(value = GoodBoys.MODID, dist = Dist.CLIENT)
public class GoodBoysClient {
    public GoodBoysClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(GoodBoysClient::registerRenderers);
        modBus.addListener(GoodBoysClient::registerScreens);
        modBus.addListener(GoodBoysClient::registerRecipeBook);
        modBus.addListener(GoodBoysClient::registerHud);
        modBus.addListener(GoodBoysClient::registerParticles);
        ClientEvents.register(modBus);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.DOG.get(), DogScreen::new);
        event.register(ModMenus.DOG_WORKBENCH.get(), DogWorkbenchScreen::new);
    }

    /** No recipe book for the workbench (it has its own list); this just keeps the log free of "unknown category" spam. */
    private static void registerRecipeBook(RegisterRecipeBookCategoriesEvent event) {
        event.registerRecipeCategoryFinder(ModRecipes.DOG_WORKBENCH.get(), recipe -> RecipeBookCategories.UNKNOWN);
    }

    private static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SLEEP_Z.get(), DogParticles.SleepProvider::new);
        event.registerSpriteSet(ModParticles.DIZZY_STAR.get(), DogParticles.StarProvider::new);
    }

    private static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(GoodBoys.id("tracker"), TrackerHud::render);
        event.registerAboveAll(GoodBoys.id("look"), DogLookHud::render);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DOG.get(), DogRenderer::new);
    }
}
