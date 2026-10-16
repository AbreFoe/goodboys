package com.goodboys.pack;

import com.goodboys.GoodBoys;
import com.goodboys.entity.Dog;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.Wolf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;

@EventBusSubscriber(modid = GoodBoys.MODID)
public final class PackEvents {
    private PackEvents() {
    }

    /** A full pack can't take a sixth member, not even a vanilla wolf (it would become a dog right after). */
    @SubscribeEvent
    static void onAnimalTame(AnimalTameEvent event) {
        if (event.getAnimal() instanceof Wolf && !event.getAnimal().level().isClientSide) {
            PackData packs = PackData.get(event.getAnimal().level().getServer());
            if (!packs.canJoin(event.getTamer().getUUID(), event.getAnimal().getUUID())) {
                event.setCanceled(true);
                event.getTamer().displayClientMessage(Component.translatable("message.goodboys.pack_full", PackData.MAX_PACK_SIZE), true);
            }
        }
    }

    /**
     * Dogs tamed before the pack limit existed (or whose record got lost) sign up when they load.
     * They are let in even over the limit; the limit only stops new tamings.
     */
    @SubscribeEvent
    static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Dog dog && dog.isTame() && dog.getOwnerUUID() != null) {
            PackData.get(event.getLevel().getServer()).add(dog.getOwnerUUID(), dog.getUUID());
        }
    }
}
