package com.goodboys.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.goodboys.GoodBoys;
import com.goodboys.entity.ai.DogAreaGoal;
import com.goodboys.entity.ai.DogBegGoal;
import com.goodboys.entity.ai.DogPlayGoal;
import com.goodboys.pack.PackData;
import com.goodboys.registry.ModGameRules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.event.EventHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Good Boys dog. First playable version: wolf-like taming and combat, sits on command,
 * and dozes off if left sitting for a while.
 */
public class Dog extends TamableAnimal implements GeoEntity {
    /** How long a dog stays sitting before it falls asleep. */
    public static final int NAP_DELAY_TICKS = 20 * 20;

    /** Number of sleeping poses (animation.dog.sleep, sleep_side, sleep_curled). */
    public static final int NAP_STYLES = 3;
    /** Ticks between spontaneous play sessions, at least. */
    private static final int PLAY_COOLDOWN_TICKS = 20 * 120;
    private static final int SHAKE_TICKS = 26;

    private static final EntityDataAccessor<Boolean> DATA_NAPPING =
            SynchedEntityData.defineId(Dog.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_NAP_STYLE =
            SynchedEntityData.defineId(Dog.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_INTERESTED =
            SynchedEntityData.defineId(Dog.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_EXCITED =
            SynchedEntityData.defineId(Dog.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(Dog.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LEVEL =
            SynchedEntityData.defineId(Dog.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_XP =
            SynchedEntityData.defineId(Dog.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DOWNED =
            SynchedEntityData.defineId(Dog.class, EntityDataSerializers.BOOLEAN);
    /** Happy Doggo: a knocked-out dog nobody helps dies after this long (counted while the owner is online). */
    public static final int BLEED_OUT_TICKS = 20 * 60 * 5;
    private static final int BLEED_OUT_WARNING_TICKS = BLEED_OUT_TICKS - 20 * 60;
    /** Second shears click within this many ticks confirms releasing the dog. */
    private static final int RELEASE_CONFIRM_TICKS = 60;

    /** Game animals HUNT mode goes after. Data-driven so packs can change it. */
    public static final TagKey<EntityType<?>> HUNTABLE = TagKey.create(Registries.ENTITY_TYPE, GoodBoys.id("huntable"));
    /** A guard chases intruders this far past the edge of its area before giving up. */
    private static final double GUARD_CHASE_MARGIN = 6.0;
    /** HUNT only looks for game this close to the owner. */
    private static final double HUNT_RANGE = 16.0;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.dog.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.dog.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.dog.run");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("animation.dog.sit");
    private static final RawAnimation[] NAPS = {
            RawAnimation.begin().thenLoop("animation.dog.sleep"),
            RawAnimation.begin().thenLoop("animation.dog.sleep_side"),
            RawAnimation.begin().thenLoop("animation.dog.sleep_curled"),
    };
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("animation.dog.swim");
    private static final RawAnimation JUMP = RawAnimation.begin().thenPlayAndHold("animation.dog.jump");
    private static final RawAnimation FALL = RawAnimation.begin().thenLoop("animation.dog.fall");
    private static final RawAnimation TAIL_WAG = RawAnimation.begin().thenLoop("animation.dog.tail_wag");
    private static final RawAnimation HEAD_TILT = RawAnimation.begin().thenLoop("animation.dog.head_tilt");
    private static final RawAnimation KNOCKED_OUT = RawAnimation.begin().thenLoop("animation.dog.knocked_out");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private int sitTicks;
    // server side
    private int actionTicks;
    private int idleCooldown = 200;
    private int playCooldown = PLAY_COOLDOWN_TICKS / 2;
    private int shakeTicks;
    private boolean wet;
    @Nullable
    private BlockPos anchor;
    private int companionTicks;
    private long releaseArmedUntil;
    private int downedTicks;
    /** Set while a hit is allowed to really kill the dog, so die() doesn't turn it into a knockout. */
    private boolean finishing;
    // client side
    private int airTicks;

    public Dog(EntityType<? extends Dog> type, Level level) {
        super(type, level);
        setTame(false, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MOVEMENT_SPEED, DogLevels.BASE_SPEED)
                .add(Attributes.MAX_HEALTH, DogLevels.UNTAMED_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, DogLevels.BASE_ATTACK);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        // Vanilla pets also sit down whenever the owner is offline. A dog on STAY or GUARD keeps working instead.
        goalSelector.addGoal(2, new SitWhenOrderedToGoal(this) {
            @Override
            public boolean canUse() {
                if (getMode().followsOwner()) {
                    return super.canUse();
                }
                return isTame() && isOrderedToSit() && !isInWaterOrBubble() && onGround();
            }

            @Override
            public boolean canContinueToUse() {
                return getMode().followsOwner() ? super.canContinueToUse() : isOrderedToSit();
            }
        });
        goalSelector.addGoal(4, new LeapAtTargetGoal(this, 0.4F));
        goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 10.0F, 2.0F) {
            @Override
            public boolean canUse() {
                return getMode().followsOwner() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return getMode().followsOwner() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(6, new DogAreaGoal(this));
        goalSelector.addGoal(7, new TemptGoal(this, 1.0, stack -> stack.is(ItemTags.WOLF_FOOD), false) {
            @Override
            public boolean canUse() {
                return (!isTame() || getMode() == DogMode.FOLLOW) && super.canUse();
            }
        });
        goalSelector.addGoal(7, new DogPlayGoal(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0) {
            @Override
            public boolean canUse() {
                return getMode().followsOwner() && !isBusy() && super.canUse();
            }
        });
        goalSelector.addGoal(9, new DogBegGoal(this, 8.0F));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));

        // defending the owner only while with them; a guard or a waiting dog keeps its post
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return getMode().followsOwner() && super.canUse();
            }
        });
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this) {
            @Override
            public boolean canUse() {
                return getMode().followsOwner() && super.canUse();
            }
        });
        targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false, this::isIntruder));
        targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Animal.class, 10, true, false, this::isGame));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_NAPPING, false);
        builder.define(DATA_NAP_STYLE, 0);
        builder.define(DATA_INTERESTED, false);
        builder.define(DATA_EXCITED, false);
        builder.define(DATA_MODE, DogMode.FOLLOW.ordinal());
        builder.define(DATA_LEVEL, 1);
        builder.define(DATA_XP, 0);
        builder.define(DATA_DOWNED, false);
    }

    // ------------------------------------------------------------------ survival (Happy Doggo knockouts)

    /** Knocked out: lying there until the owner helps. Only happens with {@code goodboysPermadeath} off. */
    public boolean isDowned() {
        return entityData.get(DATA_DOWNED);
    }

    private boolean happyDoggo() {
        return isTame() && !ModGameRules.permadeath(level());
    }

    /** What still hurts a knocked-out dog: fire and lava, drowning, suffocation, the void and /kill. */
    private static boolean finishesDownedDog(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypes.IN_WALL);
    }

    private void knockOut() {
        entityData.set(DATA_DOWNED, true);
        downedTicks = 0;
        setNapping(false);
        setOrderedToSit(false);
        setInSittingPose(false);
        setExcited(false);
        setInterested(false);
        setTarget(null);
        navigation.stop();
        stopRiding();
        if (actionTicks > 0) {
            stopTriggeredAnim("actions", null);
            actionTicks = 0;
        }
        playSound(SoundEvents.WOLF_WHINE, 1.0F, 0.8F);
        if (getOwner() instanceof Player owner) {
            BlockPos pos = blockPosition();
            owner.sendSystemMessage(Component.translatable("message.goodboys.knocked_out", getDisplayName(), pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    /** Back on its paws with a bit of health. */
    public void revive() {
        entityData.set(DATA_DOWNED, false);
        downedTicks = 0;
        setHealth((float) Math.max(4.0, getMaxHealth() * 0.3));
        level().broadcastEntityEvent(this, (byte) 7); // hearts
        playSound(SoundEvents.WOLF_AMBIENT, 1.0F, 1.2F);
    }

    private void tickDowned() {
        UUID ownerId = getOwnerUUID();
        boolean ownerOnline = ownerId != null && level().getServer() != null && level().getServer().getPlayerList().getPlayer(ownerId) != null;
        if (ownerOnline) {
            downedTicks++;
        }
        if (downedTicks == BLEED_OUT_WARNING_TICKS && getOwner() instanceof Player owner) {
            owner.sendSystemMessage(Component.translatable("message.goodboys.bleeding_out", getDisplayName()));
        }
        if (downedTicks >= BLEED_OUT_TICKS) {
            kill();
            return;
        }
        if (tickCount % 40 == 0 && level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR, getX(), getY() + 0.5, getZ(), 1, 0.2, 0.1, 0.2, 0.0);
        }
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && !finishing && !isDowned() && happyDoggo() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            setHealth(1.0F);
            knockOut();
            return;
        }
        finishing = false;
        entityData.set(DATA_DOWNED, false);
        super.die(source);
    }

    /** Mobs lose interest in a knocked-out dog. */
    @Override
    public boolean canBeSeenAsEnemy() {
        return !isDowned() && super.canBeSeenAsEnemy();
    }

    // ------------------------------------------------------------------ levels

    public int getDogLevel() {
        return entityData.get(DATA_LEVEL);
    }

    /** Experience collected towards the next level. */
    public int getXp() {
        return entityData.get(DATA_XP);
    }

    public void addExperience(int amount) {
        if (amount <= 0 || level().isClientSide || getDogLevel() >= DogLevels.MAX_LEVEL) {
            return;
        }
        int level = getDogLevel();
        int xp = getXp() + amount;
        int before = level;
        while (level < DogLevels.MAX_LEVEL && xp >= DogLevels.xpToNext(level)) {
            xp -= DogLevels.xpToNext(level);
            level++;
        }
        entityData.set(DATA_LEVEL, level);
        entityData.set(DATA_XP, level >= DogLevels.MAX_LEVEL ? 0 : xp);
        if (level > before) {
            onLevelUp(level);
        }
    }

    private void onLevelUp(int level) {
        applyLevelStats();
        setHealth(getMaxHealth());
        playSound(SoundEvents.PLAYER_LEVELUP, 0.6F, 1.6F);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getBbHeight() * 0.7, getZ(), 14, 0.4, 0.35, 0.4, 0.0);
        }
        if (getOwner() instanceof Player owner) {
            String key = level >= DogLevels.MAX_LEVEL ? "message.goodboys.max_level" : "message.goodboys.level_up";
            owner.sendSystemMessage(Component.translatable(key, getDisplayName(), level));
        }
    }

    /** Health, damage and speed for the current level (and tamed state). */
    public void applyLevelStats() {
        int level = getDogLevel();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(DogLevels.maxHealth(level, isTame()));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(DogLevels.attack(level));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(DogLevels.speed(level));
        if (getHealth() > getMaxHealth()) {
            setHealth(getMaxHealth());
        }
    }

    @Override
    protected void applyTamingSideEffects() {
        applyLevelStats();
        if (isTame()) {
            setHealth(getMaxHealth());
        }
    }

    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity killed) {
        boolean result = super.killedEntity(level, killed);
        if (isTame()) {
            addExperience(DogLevels.killReward(killed));
        }
        return result;
    }

    // ------------------------------------------------------------------ pack

    /** Tames the dog for {@code player} unless their pack is already full. */
    public boolean tameIfPackHasRoom(Player player) {
        if (!level().isClientSide && !PackData.get(level().getServer()).canJoin(player.getUUID(), getUUID())) {
            return false;
        }
        tame(player);
        return true;
    }

    @Override
    public void tame(Player player) {
        super.tame(player);
        if (!level().isClientSide) {
            PackData.get(level().getServer()).add(player.getUUID(), getUUID());
        }
    }

    /** Lets the dog go: it turns wild again (keeping its level) and frees a pack slot. */
    public void release() {
        UUID owner = getOwnerUUID();
        if (owner != null && !level().isClientSide) {
            PackData.get(level().getServer()).remove(owner, getUUID());
        }
        setOrderedToSit(false);
        setInSittingPose(false);
        setNapping(false);
        setTarget(null);
        stopRiding();
        setMode(DogMode.FOLLOW);
        anchor = null;
        setOwnerUUID(null);
        setTame(false, true);
        level().broadcastEntityEvent(this, (byte) 6); // smoke puff
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy() && getOwnerUUID() != null && level().getServer() != null) {
            PackData.get(level().getServer()).remove(getOwnerUUID(), getUUID());
        }
        super.remove(reason);
    }

    private void showStatus(Player player) {
        int level = getDogLevel();
        Component xp = level >= DogLevels.MAX_LEVEL
                ? Component.translatable("message.goodboys.status.max")
                : Component.translatable("message.goodboys.status.xp", getXp(), DogLevels.xpToNext(level));
        player.displayClientMessage(Component.translatable("message.goodboys.status", getDisplayName(), level, xp,
                Mth.ceil(getHealth()), Mth.ceil(getMaxHealth())), true);
    }

    private void handleRelease(Player player) {
        long now = level().getGameTime();
        if (now <= releaseArmedUntil) {
            Component name = getDisplayName();
            release();
            player.displayClientMessage(Component.translatable("message.goodboys.released", name), true);
        } else {
            releaseArmedUntil = now + RELEASE_CONFIRM_TICKS;
            player.displayClientMessage(Component.translatable("message.goodboys.release_confirm", getDisplayName()), true);
        }
    }

    // ------------------------------------------------------------------ commands

    public DogMode getMode() {
        DogMode[] modes = DogMode.values();
        return modes[Math.floorMod(entityData.get(DATA_MODE), modes.length)];
    }

    private void setMode(DogMode mode) {
        entityData.set(DATA_MODE, mode.ordinal());
    }

    /** Centre of the STAY/GUARD area, server side only. */
    @Nullable
    public BlockPos getAnchor() {
        return anchor;
    }

    /**
     * Carries out an order from the owner's command menu.
     *
     * @param pos    where to go, for {@link DogCommand#GO_TO}
     * @param target what to attack, for {@link DogCommand#ATTACK}
     */
    public void command(DogCommand command, @Nullable BlockPos pos, @Nullable LivingEntity target) {
        setNapping(false);
        if (command == DogCommand.SIT) {
            setOrderedToSit(true);
            setInSittingPose(true);
            navigation.stop();
            setTarget(null);
            return;
        }
        if (command == DogCommand.FIND) {
            return; // not implemented yet
        }
        setOrderedToSit(false);
        setInSittingPose(false);
        switch (command) {
            case FOLLOW, HUNT -> {
                setMode(command == DogCommand.HUNT ? DogMode.HUNT : DogMode.FOLLOW);
                anchor = null;
                setTarget(null);
            }
            case STAY -> {
                setMode(DogMode.STAY);
                anchor = blockPosition();
                setTarget(null);
                navigation.stop();
            }
            case GUARD -> {
                // "go there" followed by "guard" guards that spot, not wherever the dog stands right now
                anchor = getMode() == DogMode.STAY && anchor != null ? anchor : blockPosition();
                setMode(DogMode.GUARD);
                setTarget(null);
            }
            case GO_TO -> {
                if (pos != null) {
                    setMode(DogMode.STAY);
                    anchor = pos.immutable();
                    setTarget(null);
                    navigation.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.2);
                }
            }
            case ATTACK -> {
                if (target != null && target != getOwner() && wantsToAttack(target, getOwner())) {
                    setTarget(target);
                }
            }
            default -> {
            }
        }
    }

    /** GUARD target filter: hostile mobs inside the guarded area. */
    private boolean isIntruder(LivingEntity entity) {
        return getMode() == DogMode.GUARD && anchor != null && entity instanceof Enemy && !(entity instanceof Creeper)
                && entity.distanceToSqr(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5) <= sq(DogMode.GUARD.radius() + 2);
    }

    /** HUNT target filter: wild game near the owner (or near the dog if the owner is away). */
    private boolean isGame(LivingEntity entity) {
        if (getMode() != DogMode.HUNT || !entity.getType().is(HUNTABLE) || entity.isBaby()
                || entity.hasCustomName() || entity instanceof Mob mob && mob.isLeashed()) {
            return false;
        }
        LivingEntity owner = getOwner();
        return entity.distanceToSqr(owner != null ? owner : this) <= sq(HUNT_RANGE);
    }

    private static double sq(double d) {
        return d * d;
    }

    /** Owner in a boat with a free seat: hop in. Owner gets out: get out too. */
    private void tickBoat() {
        LivingEntity owner = getOwner();
        if (isPassenger()) {
            if (owner == null || owner.getVehicle() != getVehicle() || !getMode().followsOwner()) {
                stopRiding();
            }
            return;
        }
        if (owner != null && getMode().followsOwner() && !isOrderedToSit() && getTarget() == null
                && owner.getVehicle() instanceof Boat boat && distanceToSqr(boat) < 4 * 4) {
            startRiding(boat);
        }
    }

    // ------------------------------------------------------------------ napping

    /** Dozing while sitting. Not {@link #isSleeping()}, which means sleeping in a bed. */
    public boolean isNapping() {
        return entityData.get(DATA_NAPPING);
    }

    public void setNapping(boolean napping) {
        if (napping && !isNapping()) {
            entityData.set(DATA_NAP_STYLE, random.nextInt(NAP_STYLES)); // a different pose each nap
        }
        entityData.set(DATA_NAPPING, napping);
        if (!napping) {
            sitTicks = 0;
        }
    }

    public int getNapStyle() {
        return entityData.get(DATA_NAP_STYLE);
    }

    // ------------------------------------------------------------------ moods and little actions

    /** Begging: someone nearby holds food. Drives the head tilt. */
    public boolean isInterested() {
        return entityData.get(DATA_INTERESTED);
    }

    public void setInterested(boolean interested) {
        entityData.set(DATA_INTERESTED, interested);
    }

    /** Playing with its owner: runs instead of walking and wags its tail. */
    public boolean isExcited() {
        return entityData.get(DATA_EXCITED);
    }

    public void setExcited(boolean excited) {
        entityData.set(DATA_EXCITED, excited);
    }

    public boolean canPlay() {
        return playCooldown <= 0;
    }

    public void finishedPlaying() {
        playCooldown = PLAY_COOLDOWN_TICKS + random.nextInt(PLAY_COOLDOWN_TICKS);
    }

    /** In the middle of a short idle animation (sniff, stretch, shake...) that wandering would break. */
    public boolean isBusy() {
        return actionTicks > 0 || isInterested() || isDowned();
    }

    public boolean isShaking() {
        return shakeTicks > 0;
    }

    private void playAction(String animation, int ticks) {
        triggerAnim("actions", animation);
        actionTicks = ticks;
        navigation.stop();
    }

    private void pickIdleAction() {
        if (isNapping() || isInWater() || !onGround() || getTarget() != null || isInterested() || isExcited()) {
            return;
        }
        if (isInSittingPose()) {
            if (random.nextBoolean()) {
                playAction("scratch", 40);
            } else {
                playAction("yawn", 32);
            }
            return;
        }
        if (!navigation.isDone()) {
            return;
        }
        switch (random.nextInt(3)) {
            case 0 -> playAction("sniff", 50);
            case 1 -> playAction("stretch", 60);
            default -> playAction("yawn", 32);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (isDowned()) {
            tickDowned();
            return;
        }
        if (isOrderedToSit()) {
            if (!isNapping() && ++sitTicks >= NAP_DELAY_TICKS) {
                setNapping(true);
            }
        } else if (isNapping() || sitTicks > 0) {
            setNapping(false);
        }

        if (playCooldown > 0) {
            playCooldown--;
        }
        tickShake();
        tickBoat();

        LivingEntity owner = getOwner();
        if (isTame() && !isNapping() && owner != null && distanceToSqr(owner) < 16 * 16
                && ++companionTicks >= DogLevels.COMPANION_INTERVAL) {
            companionTicks = 0;
            addExperience(DogLevels.COMPANION_XP);
        }

        // a guard gives up the chase once the intruder is well away from its area
        LivingEntity target = getTarget();
        if (target != null && getMode() == DogMode.GUARD && anchor != null
                && target.distanceToSqr(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5) > sq(DogMode.GUARD.radius() + GUARD_CHASE_MARGIN)) {
            setTarget(null);
        }

        if (actionTicks > 0) {
            actionTicks--;
            // walking off (following the owner, chasing) cuts the little animation short
            boolean interrupted = getTarget() != null || !navigation.isDone() && !isInSittingPose();
            if (interrupted && shakeTicks == 0) {
                stopTriggeredAnim("actions", null);
                actionTicks = 0;
            }
        } else if (--idleCooldown <= 0) {
            idleCooldown = 200 + random.nextInt(400);
            pickIdleAction();
        }
    }

    private void tickShake() {
        if (isInWater()) {
            wet = true;
        } else if (wet && onGround()) {
            wet = false;
            shakeTicks = SHAKE_TICKS;
            playAction("shake", SHAKE_TICKS);
            playSound(SoundEvents.WOLF_SHAKE, getSoundVolume(), (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
        }
        if (shakeTicks > 0) {
            shakeTicks--;
            if (shakeTicks > 4 && shakeTicks < SHAKE_TICKS - 4 && level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SPLASH, getX(), getY() + getBbHeight() * 0.6, getZ(),
                        4, getBbWidth() * 0.5, 0.15, getBbWidth() * 0.5, 0.0);
            }
        }
    }

    /** Stands still while shaking off water, even if the owner walks away. */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || shakeTicks > 0 || isDowned();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // airborne time drives jump/fall, a long drop ends in a landing squash
            if (onGround() || isInWater() || isPassenger()) {
                if (airTicks > 8 && onGround()) {
                    triggerAnim("actions", "land");
                }
                airTicks = 0;
            } else {
                airTicks++;
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) {
            return false;
        }
        if (!level().isClientSide && happyDoggo()) {
            if (isDowned()) {
                if (!finishesDownedDog(source)) {
                    return false;
                }
                finishing = true; // this one is fatal for real (unless invulnerability frames swallow it)
                boolean hurt = super.hurt(source, amount);
                finishing = false;
                return hurt;
            }
            if (source.is(DamageTypeTags.IS_FALL)) {
                return false;
            }
            if (source.getEntity() != null && source.getEntity() == getOwner()) {
                return false; // swords, arrows and potions from the owner don't hurt
            }
        }
        if (!level().isClientSide) {
            setNapping(false);
            setOrderedToSit(false);
        }
        return super.hurt(source, amount);
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level().isClientSide) {
            boolean handled = isOwnedBy(player) || stack.is(Items.BONE) && !isTame();
            return handled ? InteractionResult.CONSUME : InteractionResult.PASS;
        }

        if (isDowned()) {
            if (isOwnedBy(player) && isFood(stack)) {
                stack.consume(1, player);
                revive();
                player.displayClientMessage(Component.translatable("message.goodboys.revived", getDisplayName()), true);
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.translatable("message.goodboys.needs_food", getDisplayName()), true);
            return InteractionResult.CONSUME;
        }

        if (!isTame()) {
            if (stack.is(Items.BONE)) {
                if (!PackData.get(level().getServer()).canJoin(player.getUUID(), getUUID())) {
                    player.displayClientMessage(Component.translatable("message.goodboys.pack_full", PackData.MAX_PACK_SIZE), true);
                    return InteractionResult.CONSUME;
                }
                stack.consume(1, player);
                tryToTame(player);
                return InteractionResult.SUCCESS;
            }
            return super.mobInteract(player, hand);
        }

        if (isOwnedBy(player) && player.isSecondaryUseActive()) {
            if (stack.is(Items.SHEARS)) {
                handleRelease(player);
                return InteractionResult.SUCCESS;
            }
            if (stack.isEmpty()) {
                showStatus(player);
                return InteractionResult.SUCCESS;
            }
        }

        if (isFood(stack) && getHealth() < getMaxHealth()) {
            FoodProperties food = stack.getFoodProperties(this);
            heal(food != null ? food.nutrition() : 1.0F);
            stack.consume(1, player);
            gameEvent(GameEvent.EAT);
            return InteractionResult.SUCCESS;
        }

        if (isOwnedBy(player) && stack.isEmpty()) {
            boolean sit = !isOrderedToSit();
            setOrderedToSit(sit);
            setInSittingPose(sit);
            setNapping(false);
            jumping = false;
            navigation.stop();
            setTarget(null);
            return InteractionResult.SUCCESS;
        }

        return super.mobInteract(player, hand);
    }

    private void tryToTame(Player player) {
        if (random.nextInt(3) == 0 && !EventHooks.onAnimalTame(this, player) && tameIfPackHasRoom(player)) {
            navigation.stop();
            setTarget(null);
            setOrderedToSit(true);
            setInSittingPose(true);
            level().broadcastEntityEvent(this, (byte) 7);
        } else {
            level().broadcastEntityEvent(this, (byte) 6);
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.WOLF_FOOD);
    }

    @Override
    public boolean canMate(Animal other) {
        return false; // no puppies yet: there is no baby model
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target instanceof Creeper || target instanceof Ghast) {
            return false;
        }
        if (target instanceof TamableAnimal other && other.isTame() && other.getOwner() == owner) {
            return false;
        }
        if (target instanceof Player targetPlayer && owner instanceof Player ownerPlayer && !ownerPlayer.canHarmPlayer(targetPlayer)) {
            return false;
        }
        return !(target instanceof AbstractHorse horse && horse.isTamed());
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            triggerAnim("actions", "bark");
        }
        return hit;
    }

    // ------------------------------------------------------------------ sounds

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        if (isNapping()) {
            return null;
        }
        if (isAggressive()) {
            return SoundEvents.WOLF_GROWL;
        }
        if (random.nextInt(3) == 0) {
            return isTame() && getHealth() < getMaxHealth() / 2 ? SoundEvents.WOLF_WHINE : SoundEvents.WOLF_PANT;
        }
        return SoundEvents.WOLF_AMBIENT;
    }

    @Override
    public void playAmbientSound() {
        SoundEvent sound = getAmbientSound();
        if (sound != null) {
            playSound(sound, getSoundVolume(), getVoicePitch());
            if (sound == SoundEvents.WOLF_AMBIENT && !level().isClientSide) {
                triggerAnim("actions", "bark");
            }
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOLF_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.WOLF_STEP, 0.15F, 1.0F);
    }

    // ------------------------------------------------------------------ save data

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Napping", isNapping());
        tag.putInt("NapStyle", getNapStyle());
        tag.putInt("SitTicks", sitTicks);
        tag.putString("Mode", getMode().name());
        tag.putInt("Level", getDogLevel());
        tag.putInt("Xp", getXp());
        tag.putInt("CompanionTicks", companionTicks);
        tag.putBoolean("Downed", isDowned());
        tag.putInt("DownedTicks", downedTicks);
        if (anchor != null) {
            tag.putLong("Anchor", anchor.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_NAP_STYLE, Math.floorMod(tag.getInt("NapStyle"), NAP_STYLES));
        entityData.set(DATA_NAPPING, tag.getBoolean("Napping"));
        sitTicks = tag.getInt("SitTicks");
        setMode(DogMode.byName(tag.getString("Mode")));
        anchor = tag.contains("Anchor") ? BlockPos.of(tag.getLong("Anchor")) : null;
        entityData.set(DATA_LEVEL, Mth.clamp(tag.contains("Level") ? tag.getInt("Level") : 1, 1, DogLevels.MAX_LEVEL));
        entityData.set(DATA_XP, Math.max(0, tag.getInt("Xp")));
        companionTicks = tag.getInt("CompanionTicks");
        entityData.set(DATA_DOWNED, tag.getBoolean("Downed"));
        downedTicks = tag.getInt("DownedTicks");
        applyLevelStats();
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Order matters: a later controller overrides the bones it animates.
        controllers.add(new AnimationController<>(this, "body", 5, this::bodyAnimation));
        controllers.add(new AnimationController<>(this, "tail", 5, this::tailAnimation));
        controllers.add(new AnimationController<>(this, "head", 5, this::headAnimation));
        controllers.add(new AnimationController<>(this, "actions", 3, state -> PlayState.STOP)
                .triggerableAnim("bark", action("bark"))
                .triggerableAnim("land", action("land"))
                .triggerableAnim("shake", action("shake"))
                .triggerableAnim("play_bow", action("play_bow"))
                .triggerableAnim("sniff", action("sniff"))
                .triggerableAnim("stretch", action("stretch"))
                .triggerableAnim("yawn", action("yawn"))
                .triggerableAnim("scratch", action("scratch")));
    }

    private static RawAnimation action(String name) {
        return RawAnimation.begin().thenPlay("animation.dog." + name);
    }

    private PlayState bodyAnimation(AnimationState<Dog> state) {
        if (isDowned()) {
            return state.setAndContinue(KNOCKED_OUT);
        }
        if (isInWater() || isInLava()) {
            return state.setAndContinue(SWIM);
        }
        if (airTicks > 3) {
            return state.setAndContinue(getY() > yo ? JUMP : FALL);
        }
        if (isNapping()) {
            return state.setAndContinue(NAPS[Math.floorMod(getNapStyle(), NAP_STYLES)]);
        }
        if (isInSittingPose() || isPassenger()) {
            return state.setAndContinue(SIT);
        }
        if (state.isMoving()) {
            boolean running = isAggressive() || isExcited() || state.getLimbSwingAmount() > 0.75F;
            return state.setAndContinue(running ? RUN : WALK);
        }
        return state.setAndContinue(IDLE);
    }

    private PlayState tailAnimation(AnimationState<Dog> state) {
        if (isNapping() || isInWater() || isDowned()) {
            return PlayState.STOP;
        }
        LivingEntity owner = getOwner();
        boolean happy = isExcited() || isInterested()
                || isTame() && !isAggressive() && owner != null && distanceToSqr(owner) < 8 * 8;
        return happy ? state.setAndContinue(TAIL_WAG) : PlayState.STOP;
    }

    private PlayState headAnimation(AnimationState<Dog> state) {
        return isInterested() && !isNapping() && !isDowned() ? state.setAndContinue(HEAD_TILT) : PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
