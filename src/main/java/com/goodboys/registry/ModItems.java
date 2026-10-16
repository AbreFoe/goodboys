package com.goodboys.registry;

import com.goodboys.GoodBoys;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GoodBoys.MODID);

    // Egg colours are the dog's base brown and saddle black.
    public static final DeferredItem<DeferredSpawnEggItem> DOG_SPAWN_EGG = ITEMS.register("dog_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.DOG, 0x7B3800, 0x271000, new Item.Properties()));

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ModItems::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(DOG_SPAWN_EGG);
        }
    }
}
