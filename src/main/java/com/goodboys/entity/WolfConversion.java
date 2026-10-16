package com.goodboys.entity;

import com.goodboys.GoodBoys;
import com.goodboys.registry.ModEntities;

import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Every tamed vanilla wolf becomes a Good Boys dog: freshly tamed ones right after the hearts,
 * and already tamed ones the first time they tick with the mod installed.
 */
@EventBusSubscriber(modid = GoodBoys.MODID)
public final class WolfConversion {
    private WolfConversion() {
    }

    @SubscribeEvent
    static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Wolf wolf && !wolf.level().isClientSide && wolf.isTame() && wolf.isAlive()) {
            convert(wolf);
        }
    }

    public static Dog convert(Wolf wolf) {
        // Dogs have their own armour system (coming later), so hand back any wolf armour instead of losing it.
        ItemStack armor = wolf.getBodyArmorItem();
        if (!armor.isEmpty()) {
            wolf.spawnAtLocation(armor.copy());
            wolf.setBodyArmorItem(ItemStack.EMPTY);
        }
        boolean sitting = wolf.isOrderedToSit();
        float health = wolf.getHealth() / wolf.getMaxHealth();

        Dog dog = wolf.convertTo(ModEntities.DOG.get(), false);
        if (dog == null) {
            return null;
        }
        dog.setTame(true, true);
        dog.setOwnerUUID(wolf.getOwnerUUID());
        dog.setOrderedToSit(sitting);
        dog.setInSittingPose(sitting);
        dog.setHealth(Math.max(1.0F, health * dog.getMaxHealth()));
        return dog;
    }
}
