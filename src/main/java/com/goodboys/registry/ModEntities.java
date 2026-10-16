package com.goodboys.registry;

import com.goodboys.GoodBoys;
import com.goodboys.entity.Dog;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, GoodBoys.MODID);

    // Same hitbox as the vanilla wolf so it fits the same gaps and doors.
    public static final DeferredHolder<EntityType<?>, EntityType<Dog>> DOG = ENTITY_TYPES.register("dog",
            () -> EntityType.Builder.of(Dog::new, MobCategory.CREATURE)
                    .sized(0.6F, 0.85F)
                    .eyeHeight(0.68F)
                    .clientTrackingRange(10)
                    .build("dog"));

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(ModEntities::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DOG.get(), Dog.createAttributes().build());
    }
}
