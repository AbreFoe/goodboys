package com.goodboys.client;

import com.goodboys.GoodBoys;
import com.goodboys.registry.ModEntities;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// Client-only entry point: renderers, keybinds and screens live under this package.
@Mod(value = GoodBoys.MODID, dist = Dist.CLIENT)
public class GoodBoysClient {
    public GoodBoysClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(GoodBoysClient::registerRenderers);
        ClientEvents.register(modBus);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DOG.get(), DogRenderer::new);
    }
}
