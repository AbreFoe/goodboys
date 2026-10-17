package com.goodboys.entity.ai;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.goodboys.block.DogRestBlock;
import com.goodboys.entity.Dog;
import com.goodboys.entity.DogMode;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Walks to a free pet bed or kennel and lies down there: when told to sit near one, or at night when told to wait.
 */
public class DogRestSpotGoal extends Goal {
    private static final double RADIUS = 8.0;

    private final Dog dog;
    @Nullable
    private BlockPos spot;
    private int ticks;

    public DogRestSpotGoal(Dog dog) {
        this.dog = dog;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    private boolean nightWait() {
        return dog.getMode() == DogMode.STAY && dog.level().isNight() && !dog.isOrderedToSit();
    }

    @Override
    public boolean canUse() {
        if (!dog.isTame() || dog.isNapping() || dog.isDowned() || dog.getTarget() != null || dog.isInWater() || dog.getRestSpot() != null) {
            return false;
        }
        boolean wanted = dog.wantsRestSpot() || nightWait() && dog.getRandom().nextInt(100) == 0;
        if (!wanted) {
            return false;
        }
        BlockPos around = dog.getMode() == DogMode.STAY && dog.getAnchor() != null ? dog.getAnchor() : dog.blockPosition();
        spot = Dog.findFreeRestSpot(dog, around, RADIUS);
        if (spot == null) {
            if (dog.wantsRestSpot()) {
                dog.cancelRestSpot(true); // nothing free after all: just sit down here
            }
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        ticks = 0;
        moveTo();
    }

    private void moveTo() {
        Vec3 p = restPos();
        if (p != null) {
            dog.getNavigation().moveTo(p.x, p.y, p.z, 1.0);
        }
    }

    @Nullable
    private Vec3 restPos() {
        BlockState state = dog.level().getBlockState(spot);
        return state.getBlock() instanceof DogRestBlock rest ? rest.restPosition(state, spot) : null;
    }

    @Override
    public boolean canContinueToUse() {
        return spot != null && restPos() != null && ticks < 300 && dog.getRestSpot() == null
                && !dog.isDowned() && dog.getTarget() == null && (dog.wantsRestSpot() || nightWait());
    }

    @Override
    public void stop() {
        if (dog.getRestSpot() == null && dog.wantsRestSpot()) {
            dog.cancelRestSpot(true); // couldn't get there: sit where it is
        }
        spot = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ticks++;
        Vec3 p = restPos();
        if (p == null) {
            return;
        }
        if (ticks % 20 == 0) {
            moveTo();
        }
        double dx = dog.getX() - p.x;
        double dz = dog.getZ() - p.z;
        double flat = dx * dx + dz * dz;
        // close enough: hop into place (kennel walls can make the last step fiddly for the pathfinder)
        if (flat < 0.3 * 0.3 || ticks > 60 && flat < 1.6 * 1.6 && dog.getNavigation().isDone()) {
            dog.settleAt(spot, nightWait() && !dog.wantsRestSpot());
        }
    }
}
