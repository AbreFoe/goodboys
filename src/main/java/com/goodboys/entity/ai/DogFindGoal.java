package com.goodboys.entity.ai;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.goodboys.entity.Dog;
import com.goodboys.entity.FindScent;
import com.goodboys.item.DogToyItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The FIND command. Sniffs out what the owner held up: items of that kind lying about get brought back one after
 * another (an empty hand means "pick up whatever got dropped"); failing that, a matching block or ore gets tracked
 * down, and the dog stands over it barking until the owner comes. Nothing there: a sniff round and a whine.
 */
public class DogFindGoal extends Goal {
    private enum Phase { FETCH, RETURN, LEAD, POINT, NOTHING }

    private static final double ITEM_RANGE = 24.0;
    private static final int BLOCK_RANGE = 24;
    private static final int BLOCK_DEPTH = 24;
    private static final int BLOCK_HEIGHT = 8;
    private static final int MAX_TRIPS = 12;

    private final Dog dog;
    private Phase phase = Phase.NOTHING;
    @Nullable
    private ItemEntity item;
    @Nullable
    private BlockPos found;
    private int ticks;
    private int trips;
    private boolean done;

    public DogFindGoal(Dog dog) {
        this.dog = dog;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return dog.isFinding() && !dog.isDowned() && !dog.isNapping() && dog.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return dog.isFinding() && !done && !dog.isDowned() && !dog.isNapping() && dog.getTarget() == null;
    }

    @Override
    public void start() {
        done = false;
        trips = 0;
        dog.setResting(false);
        plan();
    }

    @Override
    public void stop() {
        if (!dog.getCarried().isEmpty()) {
            dog.dropCarried(Vec3.ZERO);
        }
        dog.endFind();
        dog.setFetching(false);
        dog.setExcited(false);
        dog.getNavigation().stop();
        item = null;
        found = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** Decide what to do next: fetch another item, track down a block, or give up. */
    private void plan() {
        ticks = 0;
        item = trips < MAX_TRIPS ? nearestItem() : null;
        if (item != null) {
            phase = Phase.FETCH;
            dog.setFetching(true);
            dog.setExcited(true);
            return;
        }
        dog.setExcited(false);
        if (trips > 0) {
            done = true; // brought back everything there was
            return;
        }
        Predicate<BlockState> wanted = FindScent.blocks(dog.getFindScent());
        found = wanted != null ? FindScent.nearest(dog.level(), dog.blockPosition(), wanted, BLOCK_RANGE, BLOCK_DEPTH, BLOCK_HEIGHT) : null;
        if (found != null) {
            phase = Phase.LEAD;
            dog.setFetching(true);
            dog.getNavigation().moveTo(found.getX() + 0.5, found.getY(), found.getZ() + 0.5, 1.2);
        } else {
            phase = Phase.NOTHING;
            dog.getNavigation().stop();
            dog.triggerAnim("actions", "sniff");
        }
    }

    @Nullable
    private ItemEntity nearestItem() {
        ItemStack scent = dog.getFindScent();
        LivingEntity owner = dog.getOwner();
        List<ItemEntity> items = dog.level().getEntitiesOfClass(ItemEntity.class, dog.getBoundingBox().inflate(ITEM_RANGE), e -> {
            if (!e.isAlive() || e.getTags().contains(Dog.RETURNED_TAG)) {
                return false;
            }
            if (!scent.isEmpty()) {
                return ItemStack.isSameItem(e.getItem(), scent);
            }
            // empty hand: whatever got dropped, but not toys, and not what the owner threw away on purpose
            return DogToyItem.toyOf(e.getItem()) == null && (owner == null || e.getOwner() != owner);
        });
        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity e : items) {
            double dist = dog.distanceToSqr(e);
            if (dist < bestDist) {
                best = e;
                bestDist = dist;
            }
        }
        return best;
    }

    @Override
    public void tick() {
        ticks++;
        switch (phase) {
            case FETCH -> fetch();
            case RETURN -> bringBack();
            case LEAD -> lead();
            case POINT -> point();
            case NOTHING -> nothing();
        }
    }

    private void fetch() {
        if (item == null || !item.isAlive() || ticks > 400) {
            plan();
            return;
        }
        dog.getLookControl().setLookAt(item, 30, 30);
        if (ticks % 10 == 1) {
            dog.getNavigation().moveTo(item, 1.35);
        }
        if (dog.distanceToSqr(item) < 1.5 * 1.5) {
            dog.pickUpAll(item);
            item = null;
            phase = Phase.RETURN;
            ticks = 0;
        }
    }

    private void bringBack() {
        LivingEntity owner = dog.getOwner();
        if (owner == null || dog.getCarried().isEmpty() || ticks > 600 || dog.distanceToSqr(owner) > 64 * 64) {
            dog.dropCarried(Vec3.ZERO);
            done = true;
            return;
        }
        dog.getLookControl().setLookAt(owner, 30, 30);
        if (ticks % 10 == 1) {
            dog.getNavigation().moveTo(owner, 1.3);
        }
        if (dog.distanceToSqr(owner) < 1.9 * 1.9) {
            ItemEntity dropped = dog.dropCarried(owner.position().subtract(dog.position()).normalize().scale(0.2).add(0, 0.12, 0));
            if (dropped != null) {
                dropped.addTag(Dog.RETURNED_TAG);
            }
            trips++;
            plan();
        }
    }

    private void lead() {
        double dx = dog.getX() - (found.getX() + 0.5);
        double dz = dog.getZ() - (found.getZ() + 0.5);
        double flat = dx * dx + dz * dz;
        if (ticks % 20 == 0) {
            dog.getNavigation().moveTo(found.getX() + 0.5, found.getY(), found.getZ() + 0.5, 1.2);
        }
        // as close as it gets: right over it, or the path ran out (an ore inside rock)
        if (flat < 2.0 * 2.0 || ticks > 40 && dog.getNavigation().isDone() || ticks > 600) {
            phase = Phase.POINT;
            ticks = 0;
            dog.getNavigation().stop();
            tellOwner();
        }
    }

    private void point() {
        dog.getNavigation().stop();
        dog.getLookControl().setLookAt(found.getX() + 0.5, found.getY() + 0.5, found.getZ() + 0.5);
        if (ticks % 50 == 1) {
            boolean below = found.getY() < dog.getBlockY() - 1;
            dog.triggerAnim("actions", below ? "dig" : "bark");
            dog.playSound(SoundEvents.WOLF_AMBIENT, 1.0F, 1.2F);
            if (dog.level() instanceof ServerLevel level) {
                Vec3 at = below ? dog.position().add(Vec3.directionFromRotation(0, dog.getYRot()).scale(0.6)) : Vec3.atCenterOf(found);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.2, at.z, 6, 0.3, 0.2, 0.3, 0.0);
            }
        }
        if (ticks % 40 == 0) {
            tellOwner();
        }
        LivingEntity owner = dog.getOwner();
        boolean ownerHere = owner != null && ticks > 40 && owner.distanceToSqr(dog) < 3.0 * 3.0;
        if (ownerHere || ticks > 20 * 30 || dog.level().getBlockState(found).isAir()) {
            done = true;
        }
    }

    private void nothing() {
        if (ticks == 56) {
            dog.playSound(SoundEvents.WOLF_WHINE, 0.8F, 1.1F);
            if (dog.getOwner() instanceof Player owner) {
                ItemStack scent = dog.getFindScent();
                owner.displayClientMessage(Component.translatable("message.goodboys.found_nothing", dog.getDisplayName(),
                        scent.isEmpty() ? Component.translatable("message.goodboys.find_dropped") : scent.getHoverName()), true);
            }
        }
        if (ticks > 60) {
            done = true;
        }
    }

    private void tellOwner() {
        if (!(dog.getOwner() instanceof Player owner)) {
            return;
        }
        int dy = found.getY() - dog.getBlockY();
        Component where = dy < -1 ? Component.translatable("message.goodboys.found.below", -dy)
                : dy > 1 ? Component.translatable("message.goodboys.found.above", dy)
                : Component.translatable("message.goodboys.found.here");
        Component what = dog.level().getBlockState(found).getBlock().getName();
        owner.displayClientMessage(Component.translatable("message.goodboys.found", dog.getDisplayName(), what, where), true);
    }
}
