package com.goodboys.entity.ai;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.goodboys.entity.Dog;
import com.goodboys.entity.DogMode;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Every few minutes a tamed dog near its owner invites them to play: a play bow,
 * then a few excited dashes around them ("zoomies").
 */
public class DogPlayGoal extends Goal {
    private static final int BOW_TICKS = 32;   // length of animation.dog.play_bow
    private static final int DASH_TIMEOUT = 40;

    private final Dog dog;
    @Nullable
    private LivingEntity owner;
    private boolean bowing;
    private int phaseTicks;
    private int dashesLeft;

    public DogPlayGoal(Dog dog) {
        this.dog = dog;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!dog.isTame() || dog.getMode() != DogMode.FOLLOW || dog.isOrderedToSit() || dog.isNapping() || dog.isBusy() || !dog.canPlay()
                || dog.getTarget() != null || !dog.onGround() || dog.isInWater()) {
            return false;
        }
        owner = dog.getOwner();
        return owner != null && dog.distanceToSqr(owner) < 8 * 8 && dog.getRandom().nextInt(reducedTickDelay(400)) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return owner != null && owner.isAlive() && (bowing || dashesLeft > 0) && dog.getTarget() == null
                && !dog.isOrderedToSit() && dog.distanceToSqr(owner) < 16 * 16;
    }

    @Override
    public void start() {
        bowing = true;
        phaseTicks = BOW_TICKS;
        dashesLeft = 3 + dog.getRandom().nextInt(3);
        dog.getNavigation().stop();
        dog.triggerAnim("actions", "play_bow");
    }

    @Override
    public void stop() {
        dog.setExcited(false);
        dog.finishedPlaying();
        dog.getNavigation().stop();
        owner = null;
    }

    @Override
    public void tick() {
        if (bowing) {
            dog.getLookControl().setLookAt(owner, 30.0F, 30.0F);
            if (--phaseTicks <= 0) {
                bowing = false;
                dog.setExcited(true);
                dash();
            }
            return;
        }
        if (dog.getNavigation().isDone() || --phaseTicks <= 0) {
            dashesLeft--;
            if (dashesLeft > 0) {
                dash();
            }
        }
    }

    private void dash() {
        float angle = dog.getRandom().nextFloat() * Mth.TWO_PI;
        double dist = 2.5 + dog.getRandom().nextDouble() * 3.0;
        dog.getNavigation().moveTo(owner.getX() + Mth.cos(angle) * dist, owner.getY(), owner.getZ() + Mth.sin(angle) * dist, 1.6);
        phaseTicks = DASH_TIMEOUT;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
