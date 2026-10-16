package com.goodboys.entity;

import javax.annotation.Nullable;

import com.goodboys.entity.ai.DogBegGoal;
import com.goodboys.entity.ai.DogPlayGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
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

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private int sitTicks;
    // server side
    private int actionTicks;
    private int idleCooldown = 200;
    private int playCooldown = PLAY_COOLDOWN_TICKS / 2;
    private int shakeTicks;
    private boolean wet;
    // client side
    private int airTicks;

    public Dog(EntityType<? extends Dog> type, Level level) {
        super(type, level);
        setTame(false, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(4, new LeapAtTargetGoal(this, 0.4F));
        goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 10.0F, 2.0F));
        goalSelector.addGoal(7, new TemptGoal(this, 1.0, stack -> stack.is(ItemTags.WOLF_FOOD), false));
        goalSelector.addGoal(7, new DogPlayGoal(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0) {
            @Override
            public boolean canUse() {
                return !isBusy() && super.canUse();
            }
        });
        goalSelector.addGoal(9, new DogBegGoal(this, 8.0F));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_NAPPING, false);
        builder.define(DATA_NAP_STYLE, 0);
        builder.define(DATA_INTERESTED, false);
        builder.define(DATA_EXCITED, false);
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
        return actionTicks > 0 || isInterested();
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
        return super.isImmobile() || shakeTicks > 0;
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

        if (!isTame()) {
            if (stack.is(Items.BONE)) {
                stack.consume(1, player);
                tryToTame(player);
                return InteractionResult.SUCCESS;
            }
            return super.mobInteract(player, hand);
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
        if (random.nextInt(3) == 0 && !EventHooks.onAnimalTame(this, player)) {
            tame(player);
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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_NAP_STYLE, Math.floorMod(tag.getInt("NapStyle"), NAP_STYLES));
        entityData.set(DATA_NAPPING, tag.getBoolean("Napping"));
        sitTicks = tag.getInt("SitTicks");
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
        if (isInWater() || isInLava()) {
            return state.setAndContinue(SWIM);
        }
        if (airTicks > 3) {
            return state.setAndContinue(getY() > yo ? JUMP : FALL);
        }
        if (isNapping()) {
            return state.setAndContinue(NAPS[Math.floorMod(getNapStyle(), NAP_STYLES)]);
        }
        if (isInSittingPose()) {
            return state.setAndContinue(SIT);
        }
        if (state.isMoving()) {
            boolean running = isAggressive() || isExcited() || state.getLimbSwingAmount() > 0.75F;
            return state.setAndContinue(running ? RUN : WALK);
        }
        return state.setAndContinue(IDLE);
    }

    private PlayState tailAnimation(AnimationState<Dog> state) {
        if (isNapping() || isInWater()) {
            return PlayState.STOP;
        }
        LivingEntity owner = getOwner();
        boolean happy = isExcited() || isInterested()
                || isTame() && !isAggressive() && owner != null && distanceToSqr(owner) < 8 * 8;
        return happy ? state.setAndContinue(TAIL_WAG) : PlayState.STOP;
    }

    private PlayState headAnimation(AnimationState<Dog> state) {
        return isInterested() && !isNapping() ? state.setAndContinue(HEAD_TILT) : PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
