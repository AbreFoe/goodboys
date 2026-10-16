package com.goodboys.entity.ai;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.goodboys.entity.Dog;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Stares at a nearby player holding something tasty and tilts its head (see {@link Dog#isInterested()}). */
public class DogBegGoal extends Goal {
    private final Dog dog;
    private final float range;
    @Nullable
    private Player player;
    private int lookTime;

    public DogBegGoal(Dog dog, float range) {
        this.dog = dog;
        this.range = range;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (dog.isNapping()) {
            return false;
        }
        player = dog.level().getNearestPlayer(dog, range);
        return player != null && holdsSomethingTasty(player);
    }

    @Override
    public boolean canContinueToUse() {
        return player != null && player.isAlive() && !dog.isNapping() && lookTime > 0
                && dog.distanceToSqr(player) <= range * range && holdsSomethingTasty(player);
    }

    @Override
    public void start() {
        dog.setInterested(true);
        lookTime = adjustedTickDelay(40 + dog.getRandom().nextInt(40));
    }

    @Override
    public void stop() {
        dog.setInterested(false);
        player = null;
    }

    @Override
    public void tick() {
        dog.getLookControl().setLookAt(player.getX(), player.getEyeY(), player.getZ(), 10.0F, dog.getMaxHeadXRot());
        lookTime--;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean holdsSomethingTasty(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (dog.isFood(stack) || !dog.isTame() && stack.is(Items.BONE)) {
                return true;
            }
        }
        return false;
    }
}
