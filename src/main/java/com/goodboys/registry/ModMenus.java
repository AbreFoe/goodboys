package com.goodboys.registry;

import com.goodboys.GoodBoys;
import com.goodboys.crafting.DogWorkbenchMenu;
import com.goodboys.entity.DogMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, GoodBoys.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<DogWorkbenchMenu>> DOG_WORKBENCH =
            MENUS.register("dog_workbench", () -> new MenuType<>(DogWorkbenchMenu::new, FeatureFlags.DEFAULT_FLAGS));
    /** Opened with the dog's entity id in the extra data. */
    public static final DeferredHolder<MenuType<?>, MenuType<DogMenu>> DOG =
            MENUS.register("dog", () -> IMenuTypeExtension.create(DogMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
