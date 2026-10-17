package com.goodboys.entity;

import com.goodboys.GoodBoys;
import com.goodboys.pack.PackData;
import com.goodboys.registry.ModEntities;

import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Every tamed vanilla wolf becomes a Good Boys dog: freshly tamed ones right after the hearts,
 * and already tamed ones the first time they tick with the mod installed, as long as the owner's pack has room.
 */
@EventBusSubscriber(modid = GoodBoys.MODID)
public final class WolfConversion {
    private WolfConversion() {
    }

    @SubscribeEvent
    static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Wolf wolf && !wolf.level().isClientSide && wolf.isTame() && wolf.isAlive()
                && wolf.getOwnerUUID() != null && PackData.get(wolf.level().getServer()).size(wolf.getOwnerUUID()) < PackData.MAX_PACK_SIZE) {
            convert(wolf); // with a full pack the wolf simply stays a vanilla wolf
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
        DogVariant variant = DogVariant.fromWolf(wolf.getVariant());

        Dog dog = wolf.convertTo(ModEntities.DOG.get(), false);
        if (dog == null) {
            return null;
        }
        dog.setVariant(variant);
        if (wolf.getOwnerUUID() != null) {
            dog.setTame(true, true);
            dog.setOwnerUUID(wolf.getOwnerUUID());
            PackData.get(dog.level().getServer()).add(wolf.getOwnerUUID(), dog.getUUID());
        }
        dog.setOrderedToSit(sitting);
        dog.setInSittingPose(sitting);
        dog.setHealth(Math.max(1.0F, health * dog.getMaxHealth()));
        return dog;
    }
}
