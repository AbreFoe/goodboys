package com.goodboys.entity.ai;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.goodboys.entity.Dog;
import com.goodboys.entity.DogMode;
import com.goodboys.item.DogToyItem;
import com.goodboys.item.DogToyItem.Toy;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Playing with toys lying around: a tennis ball gets fetched back to the owner, a chew bone gets carried off and
 * chewed lying down, a rope toy gets shaken, tossed and pounced on a few times.
 */
public class DogToyGoal extends Goal {
    private enum Phase { FETCH, RETURN, SHAKE, CHASE, CHEW }

    private static final double SEARCH = 12.0;
    /** A following dog leaves its chew bone or rope once the owner is this far away. */
    private static final double OWNER_LEFT = 14.0;

    private final Dog dog;
    @Nullable
    private ItemEntity target;
    private Phase phase = Phase.FETCH;
    private Toy toy;
    private int ticks;
    private int rounds;
    private boolean pounced;
    private boolean done;

    public DogToyGoal(Dog dog) {
        this.dog = dog;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!dog.isTame() || dog.isOrderedToSit() || dog.isNapping() || dog.isDowned() || dog.isShaking()
                || dog.getTarget() != null || dog.isInWater()) {
            return false;
        }
        ItemEntity thrown = dog.takeThrownToy();
        if (thrown != null && dog.getCarried().isEmpty()) {
            target = thrown; // the owner just threw it: no dawdling, no range limit
            toy = DogToyItem.toyOf(thrown.getItem());
            return toy != null;
        }
        if (dog.getToyCooldown() > 0 || !(dog.getMode() == DogMode.FOLLOW || dog.getMode() == DogMode.STAY)) {
            return false;
        }
        if (!dog.getCarried().isEmpty()) {
            toy = DogToyItem.toyOf(dog.getCarried());
            return toy != null;
        }
        if (dog.getRandom().nextInt(10) != 0) {
            return false; // look around for toys about twice a second
        }
        target = findToy();
        if (target == null) {
            return false;
        }
        toy = DogToyItem.toyOf(target.getItem());
        return true;
    }

    @Nullable
    private ItemEntity findToy() {
        List<ItemEntity> toys = dog.level().getEntitiesOfClass(ItemEntity.class, dog.getBoundingBox().inflate(SEARCH),
                e -> e.isAlive() && DogToyItem.toyOf(e.getItem()) != null && !e.getTags().contains(Dog.RETURNED_TAG));
        BlockPos anchor = dog.getAnchor();
        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity e : toys) {
            if (dog.getMode() == DogMode.STAY && anchor != null && e.distanceToSqr(anchor.getCenter()) > 8 * 8) {
                continue; // a waiting dog only plays close to its spot
            }
            double dist = dog.distanceToSqr(e);
            if (dist < bestDist) {
                best = e;
                bestDist = dist;
            }
        }
        return best;
    }

    @Override
    public void start() {
        ticks = 0;
        rounds = 2 + dog.getRandom().nextInt(2);
        pounced = false;
        done = false;
        if (!dog.getCarried().isEmpty()) {
            phase = afterPickup();
        } else {
            phase = Phase.FETCH;
            dog.setExcited(toy == Toy.TENNIS_BALL);
        }
        dog.setResting(false);
        dog.setFetching(phase == Phase.FETCH || phase == Phase.RETURN || phase == Phase.CHASE);
    }

    private Phase afterPickup() {
        return switch (toy) {
            case TENNIS_BALL -> Phase.RETURN;
            case CHEW_BONE -> Phase.CHEW;
            case ROPE_TOY -> Phase.SHAKE;
        };
    }

    @Override
    public boolean canContinueToUse() {
        if (done || dog.isOrderedToSit() || dog.isNapping() || dog.isDowned() || dog.getTarget() != null) {
            return false;
        }
        // chewing and rope games stop when the owner walks off (fetching a throw goes on: it ends with the owner)
        LivingEntity owner = dog.getOwner();
        if (phase != Phase.FETCH && phase != Phase.RETURN && dog.getMode().followsOwner()
                && owner != null && dog.distanceToSqr(owner) > OWNER_LEFT * OWNER_LEFT) {
            return false;
        }
        return switch (phase) {
            case FETCH, CHASE -> target != null && target.isAlive() && ticks < 400;
            case RETURN -> !dog.getCarried().isEmpty() && ticks < 600;
            case SHAKE -> true;
            case CHEW -> dog.isChewing() || ticks == 0;
        };
    }

    @Override
    public void stop() {
        dog.setFetching(false);
        dog.setExcited(false);
        dog.setChewing(false);
        if (!dog.getCarried().isEmpty() && phase != Phase.RETURN) {
            dog.dropCarried(Vec3.ZERO);
        }
        dog.getNavigation().stop();
        dog.setToyCooldown(toy == Toy.TENNIS_BALL ? 20 : 300 + dog.getRandom().nextInt(300));
        target = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ticks++;
        switch (phase) {
            case FETCH, CHASE -> chase();
            case RETURN -> bringBack();
            case SHAKE -> shakeAndToss();
            case CHEW -> chew();
        }
    }

    private void chase() {
        dog.getLookControl().setLookAt(target, 30, 30);
        if (ticks % 10 == 1) {
            dog.getNavigation().moveTo(target, toy == Toy.TENNIS_BALL ? 1.5 : 1.15);
        }
        double dist = dog.distanceToSqr(target);
        if (phase == Phase.CHASE && !pounced && dist < 3.0 * 3.0 && dist > 1.4 * 1.4 && target.onGround()) {
            pounced = true;
            dog.triggerAnim("actions", "pounce");
            dog.schedulePounce(16, target.position());
        }
        if (dist < 1.4 * 1.4 && (phase == Phase.FETCH || ticks > 20)) {
            dog.pickUp(target);
            target = null;
            ticks = 0;
            pounced = false;
            phase = afterPickup();
            dog.setFetching(phase == Phase.RETURN);
        }
    }

    private void bringBack() {
        LivingEntity owner = dog.getOwner();
        if (owner == null) {
            dog.dropCarried(Vec3.ZERO);
            done = true;
            return;
        }
        dog.getLookControl().setLookAt(owner, 30, 30);
        if (ticks % 10 == 1) {
            dog.getNavigation().moveTo(owner, 1.3);
        }
        if (dog.distanceToSqr(owner) > 64 * 64) {
            dog.dropCarried(Vec3.ZERO); // owner went off somewhere: give up
            done = true;
            return;
        }
        if (dog.distanceToSqr(owner) < 1.9 * 1.9) {
            Vec3 towards = owner.position().subtract(dog.position()).normalize().scale(0.22).add(0, 0.12, 0);
            ItemEntity ball = dog.dropCarried(towards); // "again! again!"
            if (ball != null) {
                ball.addTag(Dog.RETURNED_TAG); // stays at the owner's feet until thrown again
            }
            done = true;
            dog.triggerAnim("actions", "bark");
            dog.playSound(SoundEvents.WOLF_AMBIENT, 0.8F, 1.3F);
        }
    }

    private void shakeAndToss() {
        dog.getNavigation().stop();
        if (ticks == 1) {
            dog.triggerAnim("actions", "toy_shake");
        } else if (ticks == 26) {
            dog.triggerAnim("actions", "toss");
        } else if (ticks == 33) {
            Vec3 look = Vec3.directionFromRotation(0, dog.getYRot());
            target = dog.dropCarried(look.scale(0.28).add(0, 0.32, 0));
            if (target == null || --rounds < 0) {
                done = true; // leaves the rope lying there
                return;
            }
            phase = Phase.CHASE;
            dog.setFetching(true);
            ticks = 0;
        }
    }

    private void chew() {
        dog.getNavigation().stop();
        if (ticks == 1) {
            dog.setChewing(true);
            dog.setChewTicks(160 + dog.getRandom().nextInt(140));
        }
    }
}
