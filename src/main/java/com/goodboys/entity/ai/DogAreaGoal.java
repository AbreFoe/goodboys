package com.goodboys.entity.ai;

import java.util.EnumSet;

import com.goodboys.entity.Dog;
import com.goodboys.entity.DogMode;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * For STAY and GUARD: keeps the dog around its anchor. Walks back when it has strayed,
 * otherwise picks a new spot inside the area now and then (a lazy potter for STAY, a patrol for GUARD).
 */
public class DogAreaGoal extends Goal {
    private final Dog dog;
    private int cooldown;

    public DogAreaGoal(Dog dog) {
        this.dog = dog;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!dog.getMode().usesAnchor() || dog.getAnchor() == null || dog.isOrderedToSit() || dog.isBusy() || dog.getTarget() != null) {
            return false;
        }
        if (strayed()) {
            return true;
        }
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !dog.getNavigation().isDone() && !dog.isOrderedToSit() && dog.getTarget() == null && dog.getMode().usesAnchor();
    }

    @Override
    public void start() {
        BlockPos anchor = dog.getAnchor();
        double radius = dog.getMode().radius();
        boolean patrol = dog.getMode() == DogMode.GUARD;
        if (strayed()) {
            dog.getNavigation().moveTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, 1.1);
        } else {
            float angle = dog.getRandom().nextFloat() * Mth.TWO_PI;
            double dist = dog.getRandom().nextDouble() * radius;
            dog.getNavigation().moveTo(anchor.getX() + 0.5 + Mth.cos(angle) * dist, anchor.getY(),
                    anchor.getZ() + 0.5 + Mth.sin(angle) * dist, patrol ? 0.9 : 0.7);
        }
        // a patrol keeps moving, a waiting dog mostly hangs around
        cooldown = reducedTickDelay(patrol ? 60 + dog.getRandom().nextInt(80) : 120 + dog.getRandom().nextInt(240));
    }

    private boolean strayed() {
        BlockPos anchor = dog.getAnchor();
        double leash = dog.getMode().radius() + 1.5;
        return dog.distanceToSqr(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5) > leash * leash;
    }
}
