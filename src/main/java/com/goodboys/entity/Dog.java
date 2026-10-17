package com.goodboys.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.goodboys.GoodBoys;
import com.goodboys.block.DogRestBlock;
import com.goodboys.entity.ai.DogAreaGoal;
import com.goodboys.entity.ai.DogBegGoal;
import com.goodboys.entity.ai.DogPlayGoal;
import com.goodboys.entity.ai.DogFindGoal;
import com.goodboys.entity.ai.DogRestSpotGoal;
import com.goodboys.entity.ai.DogToyGoal;
import com.goodboys.item.DogAccessoryItem;
import com.goodboys.item.DogArmorItem;
import com.goodboys.item.DogUpgrade;
import com.goodboys.pack.PackData;
import com.goodboys.registry.ModGameRules;
import com.goodboys.registry.ModParticles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
import net.minecraft.world.entity.item.ItemEntity;
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
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
    /** Copies of the equipped armour (with its upgrades) and head accessory, for the renderer. */
    private static final EntityDataAccessor<ItemStack> DATA_ARMOR =
            SynchedEntityData.defineId(Dog.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_HEAD =
            SynchedEntityData.defineId(Dog.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(Dog.class, EntityDataSerializers.INT);
    /** The toy (or anything else) held in the mouth. */
    private static final EntityDataAccessor<ItemStack> DATA_CARRIED =
            SynchedEntityData.defineId(Dog.class, EntityDataSerializers.ITEM_STACK);
    /** Lying down awake after standing about for a while. */
    private static final EntityDataAccessor<Boolean> DATA_RESTING =
            SynchedEntityData.defineId(Dog.class, EntityDataSerializers.BOOLEAN);
    /** Lying down gnawing a chew toy. */
    private static final EntityDataAccessor<Boolean> DATA_CHEWING =
            SynchedEntityData.defineId(Dog.class, EntityDataSerializers.BOOLEAN);
    /** Standing still this long (and not sitting) makes a dog lie down for a rest. */
    private static final int REST_AFTER_TICKS = 20 * 30;
    private static final int SETTLE_TICKS = 34;

    public static final int GEAR_HEAD = 0;
    public static final int GEAR_ARMOR = 1;
    /** A knocked-out dog with a field kit and the Dog's Heart perk gets up by itself after this long. */
    private static final int FIELD_KIT_TICKS = 20 * 20;

    private static final ResourceLocation MOD_ARMOR = GoodBoys.id("dog_armor");
    private static final ResourceLocation MOD_TOUGHNESS = GoodBoys.id("dog_armor_toughness");
    private static final ResourceLocation MOD_PLATE = GoodBoys.id("upgrade_plate");
    private static final ResourceLocation MOD_CLAWS = GoodBoys.id("upgrade_claws");
    private static final ResourceLocation MOD_JAW = GoodBoys.id("upgrade_jaw");
    private static final ResourceLocation MOD_JAW_KNOCKBACK = GoodBoys.id("upgrade_jaw_knockback");
    private static final ResourceLocation MOD_SHOES = GoodBoys.id("upgrade_running_shoes");
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
    private static final RawAnimation BLINK = RawAnimation.begin().thenLoop("animation.dog.blink");
    private static final RawAnimation REST = RawAnimation.begin().thenLoop("animation.dog.rest");
    private static final RawAnimation CHEW = RawAnimation.begin().thenLoop("animation.dog.chew");
    private static final RawAnimation BEG = RawAnimation.begin().thenLoop("animation.dog.beg");
    private static final RawAnimation CARRY = RawAnimation.begin().thenLoop("animation.dog.carry");

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
    private int downedAge;
    // toys, rest spots, resting
    private int toyCooldown;
    /** A ball the owner just threw: chase it now, however far it went. */
    @Nullable
    private ItemEntity thrownToy;
    /** Running after a toy or bringing it back: following the owner waits till it's done. */
    private boolean fetching;
    /** FIND in progress: what the owner held up (empty = anything dropped), null when not searching. */
    @Nullable
    private ItemStack findScent;
    private int chewTicks;
    private int stillTicks;
    private int restTicks;
    private int pounceIn;
    @Nullable
    private Vec3 pounceAt;
    @Nullable
    private BlockPos restSpot;
    private boolean restSpotIsAuto;
    private boolean wantsRestSpot;
    private int settleTicks;
    private int settleCount;
    private int restHealTicks;
    private String currentAction = "";
    /** Client side: how much the head follows the look target (eased in/out by DogGeoModel). */
    public float lookWeight = 1.0F;
    private final SimpleContainer gear = new SimpleContainer(2);
    private final SimpleContainer bag = new SimpleContainer(DogMenu.BAG_SIZE);
    /** Set while a hit is allowed to really kill the dog, so die() doesn't turn it into a knockout. */
    private boolean finishing;
    // client side
    private int airTicks;

    public Dog(EntityType<? extends Dog> type, Level level) {
        super(type, level);
        setTame(false, false);
        gear.addListener(container -> onGearChanged());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MOVEMENT_SPEED, DogLevels.BASE_SPEED)
                .add(Attributes.MAX_HEALTH, DogLevels.UNTAMED_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, DogLevels.BASE_ATTACK)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0);
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
        goalSelector.addGoal(4, new DogFindGoal(this));
        goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 10.0F, 2.0F) {
            @Override
            public boolean canUse() {
                return getMode().followsOwner() && !fetching && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return getMode().followsOwner() && !fetching && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(6, new DogAreaGoal(this));
        goalSelector.addGoal(7, new TemptGoal(this, 1.0, stack -> stack.is(ItemTags.WOLF_FOOD), false) {
            @Override
            public boolean canUse() {
                return (!isTame() || getMode() == DogMode.FOLLOW) && super.canUse();
            }
        });
        goalSelector.addGoal(5, new DogRestSpotGoal(this));
        goalSelector.addGoal(7, new DogPlayGoal(this));
        goalSelector.addGoal(5, new DogToyGoal(this)); // above following, so a long throw still gets fetched
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0) {
            @Override
            public boolean canUse() {
                return getMode().followsOwner() && !isBusy() && super.canUse();
            }
        });
        goalSelector.addGoal(9, new DogBegGoal(this, 8.0F));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override
            public boolean canUse() {
                return !isNapping() && !isDowned() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !isNapping() && !isDowned() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(10, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return !isNapping() && !isDowned() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !isNapping() && !isDowned() && super.canContinueToUse();
            }
        });

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
        builder.define(DATA_ARMOR, ItemStack.EMPTY);
        builder.define(DATA_HEAD, ItemStack.EMPTY);
        builder.define(DATA_VARIANT, DogVariant.SHEPHERD.ordinal());
        builder.define(DATA_CARRIED, ItemStack.EMPTY);
        builder.define(DATA_RESTING, false);
        builder.define(DATA_CHEWING, false);
    }

    // ------------------------------------------------------------------ coat

    public DogVariant getVariant() {
        return DogVariant.byId(entityData.get(DATA_VARIANT));
    }

    public void setVariant(DogVariant variant) {
        entityData.set(DATA_VARIANT, variant.ordinal());
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData spawnGroupData) {
        setVariant(DogVariant.random(random));
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
    }

    // ------------------------------------------------------------------ equipment

    /** Slot {@link #GEAR_HEAD}: accessory, slot {@link #GEAR_ARMOR}: armour. Server side; clients see {@link #getArmor()}. */
    public SimpleContainer getGear() {
        return gear;
    }

    /** Saddlebag contents, usable while the armour carries a {@link DogUpgrade#BAG}. */
    public SimpleContainer getBag() {
        return bag;
    }

    /** The equipped armour as everyone sees it (synced). */
    public ItemStack getArmor() {
        return level().isClientSide ? entityData.get(DATA_ARMOR) : gear.getItem(GEAR_ARMOR);
    }

    public ItemStack getHeadAccessory() {
        return level().isClientSide ? entityData.get(DATA_HEAD) : gear.getItem(GEAR_HEAD);
    }

    public boolean hasUpgrade(DogUpgrade upgrade) {
        return DogArmorItem.hasUpgrade(getArmor(), upgrade);
    }

    /** View of the equipped armour's upgrade slots, for the dog menu. */
    public Container upgradeContainer() {
        return new UpgradeContainer();
    }

    private void onGearChanged() {
        if (level().isClientSide) {
            return;
        }
        entityData.set(DATA_ARMOR, gear.getItem(GEAR_ARMOR).copy());
        entityData.set(DATA_HEAD, gear.getItem(GEAR_HEAD).copy());
        applyLevelStats();
    }

    private void applyEquipmentStats() {
        ItemStack armor = gear.getItem(GEAR_ARMOR);
        DogArmorItem.Tier tier = armor.getItem() instanceof DogArmorItem item ? item.tier() : null;
        modifier(Attributes.ARMOR, MOD_ARMOR, tier != null ? tier.armor() : 0, AttributeModifier.Operation.ADD_VALUE);
        modifier(Attributes.ARMOR_TOUGHNESS, MOD_TOUGHNESS, tier != null ? tier.toughness() : 0, AttributeModifier.Operation.ADD_VALUE);
        modifier(Attributes.ARMOR, MOD_PLATE, hasUpgrade(DogUpgrade.PLATE) ? 3 : 0, AttributeModifier.Operation.ADD_VALUE);
        modifier(Attributes.ATTACK_DAMAGE, MOD_CLAWS, hasUpgrade(DogUpgrade.CLAWS) ? 2 : 0, AttributeModifier.Operation.ADD_VALUE);
        modifier(Attributes.ATTACK_DAMAGE, MOD_JAW, hasUpgrade(DogUpgrade.JAW) ? 2 : 0, AttributeModifier.Operation.ADD_VALUE);
        modifier(Attributes.ATTACK_KNOCKBACK, MOD_JAW_KNOCKBACK, hasUpgrade(DogUpgrade.JAW) ? 0.5 : 0, AttributeModifier.Operation.ADD_VALUE);
        modifier(Attributes.MOVEMENT_SPEED, MOD_SHOES, hasUpgrade(DogUpgrade.RUNNING_SHOES) ? 0.15 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private void modifier(Holder<Attribute> attribute, ResourceLocation id,
                          double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (amount == 0) {
            instance.removeModifier(id);
        } else {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    /** Owner right-clicks with armour or an accessory: put it on, hand back what was there. */
    private boolean tryEquip(Player player, ItemStack stack) {
        int slot = stack.getItem() instanceof DogArmorItem ? GEAR_ARMOR : stack.getItem() instanceof DogAccessoryItem ? GEAR_HEAD : -1;
        if (slot < 0) {
            return false;
        }
        ItemStack old = gear.getItem(slot);
        if (slot == GEAR_ARMOR && DogArmorItem.hasUpgrade(old, DogUpgrade.BAG) && !bag.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.goodboys.empty_bag_first", getDisplayName()), true);
            return true;
        }
        gear.setItem(slot, stack.consumeAndReturn(1, player));
        if (!old.isEmpty() && !player.getInventory().add(old)) {
            player.drop(old, false);
        }
        playSound(slot == GEAR_ARMOR ? SoundEvents.ARMOR_EQUIP_WOLF.value() : SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 1.0F);
        return true;
    }

    private void openMenu(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new DogMenu(id, inventory, this), getDisplayName()),
                    buf -> buf.writeVarInt(getId()));
        }
    }

    private void dropGear() {
        dropCarried(Vec3.ZERO);
        for (int i = 0; i < gear.getContainerSize(); i++) {
            spawnAtLocation(gear.removeItemNoUpdate(i));
        }
        for (int i = 0; i < bag.getContainerSize(); i++) {
            spawnAtLocation(bag.removeItemNoUpdate(i));
        }
        onGearChanged();
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        dropGear();
    }

    /** The armour's upgrade list as a three-slot container; writes straight back into the armour item. */
    private class UpgradeContainer implements Container {
        private ItemStack armor() {
            return gear.getItem(GEAR_ARMOR);
        }

        @Override
        public int getContainerSize() {
            return DogArmorItem.UPGRADE_SLOTS;
        }

        @Override
        public boolean isEmpty() {
            return DogArmorItem.getUpgrades(armor()).stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int slot) {
            return armor().getItem() instanceof DogArmorItem ? DogArmorItem.getUpgrades(armor()).get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack current = getItem(slot);
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = current.split(amount);
            setItem(slot, current);
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack current = getItem(slot);
            setItem(slot, ItemStack.EMPTY);
            return current;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (!(armor().getItem() instanceof DogArmorItem)) {
                return;
            }
            List<ItemStack> upgrades = new ArrayList<>(DogArmorItem.getUpgrades(armor()));
            upgrades.set(slot, stack);
            DogArmorItem.setUpgrades(armor(), upgrades);
            setChanged();
        }

        @Override
        public void setChanged() {
            gear.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            if (armor().getItem() instanceof DogArmorItem) {
                DogArmorItem.setUpgrades(armor(), List.of());
                setChanged();
            }
        }
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
        dropCarried(Vec3.ZERO);
        setChewing(false);
        setResting(false);
        entityData.set(DATA_DOWNED, true);
        downedTicks = 0;
        downedAge = 0;
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
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.0F);
    }

    private void tickDowned() {
        downedAge++;
        if (hasUpgrade(DogUpgrade.LIFE_VEST) && isInWater()) {
            setAirSupply(getMaxAirSupply());
            setDeltaMovement(getDeltaMovement().add(0, 0.04, 0)); // bob back up to the surface
        }
        if (downedAge >= FIELD_KIT_TICKS && getDogLevel() >= DogLevels.HEART_LEVEL && hasUpgrade(DogUpgrade.FIELD_KIT)) {
            DogArmorItem.consumeUpgrade(gear.getItem(GEAR_ARMOR), DogUpgrade.FIELD_KIT);
            gear.setChanged();
            revive();
            if (getOwner() instanceof Player owner) {
                owner.sendSystemMessage(Component.translatable("message.goodboys.field_kit_used", getDisplayName()));
            }
            return;
        }
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
            serverLevel.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() * 0.5, getZ(), 24, 0.1, 0.1, 0.1, 0.12);
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
        applyEquipmentStats();
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
        dropGear(); // the owner's things stay with the owner
        level().broadcastEntityEvent(this, (byte) 6); // smoke puff
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy() && getOwnerUUID() != null && level().getServer() != null) {
            PackData.get(level().getServer()).remove(getOwnerUUID(), getUUID());
        }
        super.remove(reason);
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
        setResting(false);
        setChewing(false);
        restSpot = null;
        wantsRestSpot = false;
        findScent = null;
        if (!getCarried().isEmpty()) {
            dropCarried(Vec3.ZERO);
        }
        if (command == DogCommand.SIT && findFreeRestSpot(this, blockPosition(), 6) != null) {
            wantsRestSpot = true; // a bed or kennel close by: go and lie down there
            setOrderedToSit(false);
            setInSittingPose(false);
            navigation.stop();
            setTarget(null);
            return;
        }
        if (command != DogCommand.SIT) {
            triggerAnim("actions", "bark"); // "on it!"
            playSound(SoundEvents.WOLF_AMBIENT, 0.6F, 1.15F + random.nextFloat() * 0.2F);
        }
        if (command == DogCommand.SIT) {
            setOrderedToSit(true);
            setInSittingPose(true);
            navigation.stop();
            setTarget(null);
            return;
        }
        setOrderedToSit(false);
        setInSittingPose(false);
        if (command == DogCommand.FIND) {
            setTarget(null);
            navigation.stop();
            return; // the search itself starts with startFind (the owner's held item is the scent)
        }
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
            BlockState spot = restSpot != null ? level().getBlockState(restSpot) : null;
            int style = spot != null && spot.getBlock() instanceof DogRestBlock rest ? rest.napStyle() : random.nextInt(NAP_STYLES);
            entityData.set(DATA_NAP_STYLE, style); // a different pose each nap (or the bed's)
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
        return actionTicks > 0 || isInterested() || isDowned() || isResting() || isChewing() || !getCarried().isEmpty() || settleTicks > 0;
    }

    public boolean isShaking() {
        return shakeTicks > 0;
    }

    private void playAction(String animation, int ticks) {
        currentAction = animation;
        triggerAnim("actions", animation);
        actionTicks = ticks;
        navigation.stop();
    }

    private void pickIdleAction() {
        if (isNapping() || isInWater() || !onGround() || getTarget() != null || isInterested() || isExcited()
                || isChewing() || !getCarried().isEmpty() || settleTicks > 0) {
            return;
        }
        if (isInSittingPose()) {
            int roll = random.nextInt(10);
            if (level().isNight() && roll == 0) {
                playAction("howl", 68);
                playSound(SoundEvents.WOLF_HOWL, 1.2F, 0.9F + random.nextFloat() * 0.2F);
            } else if (roll < 5) {
                playAction("scratch", 44);
            } else if (roll < 8) {
                playAction("yawn", 36);
            } else {
                playAction("sneeze", 19);
                playSound(SoundEvents.FOX_SNIFF, 0.8F, 1.6F);
            }
            return;
        }
        if (isResting()) {
            if (random.nextBoolean()) {
                playAction("yawn", 36);
            }
            return;
        }
        if (!navigation.isDone()) {
            return;
        }
        LivingEntity owner = getOwner();
        boolean ownerNear = owner != null && distanceToSqr(owner) < 6 * 6;
        BlockState ground = level().getBlockState(getOnPos());
        boolean soft = ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(Blocks.SNOW_BLOCK);
        int roll = random.nextInt(12);
        if (roll < 3) {
            playAction("sniff", 54);
        } else if (roll < 5) {
            playAction("stretch", 64);
        } else if (roll < 7) {
            playAction("yawn", 36);
        } else if (roll < 8) {
            playAction("sneeze", 19);
            playSound(SoundEvents.FOX_SNIFF, 0.8F, 1.6F);
        } else if (roll < 10 && soft && getMode() != DogMode.GUARD) {
            playAction("dig", 48);
        } else if (roll < 12 && ownerNear && isTame()) {
            playAction("roll_over", 64);
        }
    }

    // ------------------------------------------------------------------ toys, resting, rest spots

    public ItemStack getCarried() {
        return entityData.get(DATA_CARRIED);
    }

    private void setCarried(ItemStack stack) {
        entityData.set(DATA_CARRIED, stack);
    }

    public boolean isResting() {
        return entityData.get(DATA_RESTING);
    }

    public void setResting(boolean resting) {
        entityData.set(DATA_RESTING, resting);
        if (!resting) {
            stillTicks = 0;
        }
    }

    public boolean isChewing() {
        return entityData.get(DATA_CHEWING);
    }

    public void setChewing(boolean chewing) {
        entityData.set(DATA_CHEWING, chewing);
    }

    public void setChewTicks(int ticks) {
        chewTicks = ticks;
    }

    public int getToyCooldown() {
        return toyCooldown;
    }

    public void setToyCooldown(int ticks) {
        toyCooldown = ticks;
    }

    /** Tag on a ball a dog has brought back: it lies there until the owner throws it again. */
    public static final String RETURNED_TAG = "goodboys_returned";

    /** The owner threw a ball: go get it. */
    public void noticeThrow(ItemEntity toy) {
        thrownToy = toy;
    }

    /** The ball the owner threw, once (it is handed over to the toy goal). */
    @Nullable
    public ItemEntity takeThrownToy() {
        ItemEntity toy = thrownToy;
        thrownToy = null;
        return toy != null && toy.isAlive() ? toy : null;
    }

    /** Starts a FIND search for {@code scent} (empty: whatever got dropped nearby). */
    public void startFind(ItemStack scent) {
        findScent = scent.copyWithCount(1);
    }

    public boolean isFinding() {
        return findScent != null;
    }

    public ItemStack getFindScent() {
        return findScent != null ? findScent : ItemStack.EMPTY;
    }

    public void endFind() {
        findScent = null;
    }

    /** Takes a whole stack lying on the ground into the mouth (FIND brings things back in one go). */
    public void pickUpAll(ItemEntity item) {
        setCarried(item.getItem().copy());
        item.discard();
        playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.4F);
    }

    public boolean isFetching() {
        return fetching;
    }

    public void setFetching(boolean fetching) {
        this.fetching = fetching;
    }

    /** Takes one item from a stack lying on the ground into the mouth. */
    public void pickUp(ItemEntity item) {
        ItemStack stack = item.getItem();
        setCarried(stack.copyWithCount(1));
        stack.shrink(1);
        if (stack.isEmpty()) {
            item.discard();
        } else {
            item.setItem(stack);
        }
        playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.4F);
    }

    /** Lets go of what's in the mouth, giving it a little throw. Returns the dropped item, if any. */
    @Nullable
    public ItemEntity dropCarried(Vec3 motion) {
        ItemStack stack = getCarried();
        if (stack.isEmpty() || level().isClientSide) {
            return null;
        }
        setCarried(ItemStack.EMPTY);
        Vec3 mouth = position().add(Vec3.directionFromRotation(0, yBodyRot).scale(0.7)).add(0, 0.45, 0);
        ItemEntity item = new ItemEntity(level(), mouth.x, mouth.y, mouth.z, stack, motion.x, motion.y, motion.z);
        item.setPickUpDelay(10);
        level().addFreshEntity(item);
        return item;
    }

    /** The pounce animation leaves the ground after a moment; the shove forward happens then. */
    public void schedulePounce(int ticks, Vec3 at) {
        pounceIn = ticks;
        pounceAt = at;
    }

    @Nullable
    public BlockPos getRestSpot() {
        return restSpot;
    }

    /** How many times this dog has turned round and lain down on a spot (for tests). */
    public int getSettleCount() {
        return settleCount;
    }

    public boolean wantsRestSpot() {
        return wantsRestSpot;
    }

    /** No bed or kennel to be had: sit down on the spot instead, if that was the order. */
    public void cancelRestSpot(boolean sitInstead) {
        wantsRestSpot = false;
        if (sitInstead) {
            setOrderedToSit(true);
            setInSittingPose(true);
        }
    }

    /** A pet bed or kennel near {@code around} that no other dog is using. */
    @Nullable
    public static BlockPos findFreeRestSpot(Dog dog, BlockPos around, double radius) {
        int r = (int) Math.ceil(radius);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        List<Dog> others = dog.level().getEntitiesOfClass(Dog.class, new AABB(around).inflate(radius + 4), d -> d != dog);
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-r, -2, -r), around.offset(r, 2, r))) {
            BlockState state = dog.level().getBlockState(pos);
            if (!(state.getBlock() instanceof DogRestBlock rest)) {
                continue;
            }
            Vec3 at = rest.restPosition(state, pos);
            boolean taken = others.stream().anyMatch(o -> pos.equals(o.getRestSpot()) || o.position().distanceToSqr(at) < 0.5 * 0.5);
            double dist = dog.distanceToSqr(at);
            if (!taken && dist < bestDist) {
                best = pos.immutable();
                bestDist = dist;
            }
        }
        return best;
    }

    /** Arrived at a bed or kennel: hop into place, turn round once and lie down. */
    public void settleAt(BlockPos spot, boolean auto) {
        BlockState state = level().getBlockState(spot);
        if (!(state.getBlock() instanceof DogRestBlock rest)) {
            return;
        }
        Vec3 at = rest.restPosition(state, spot);
        navigation.stop();
        setPos(at.x, at.y, at.z);
        float yaw = rest.restYaw(state);
        if (!Float.isNaN(yaw)) {
            setYRot(yaw);
            setYBodyRot(yaw);
            setYHeadRot(yaw);
        }
        restSpot = spot.immutable();
        restSpotIsAuto = auto;
        wantsRestSpot = false;
        settleTicks = SETTLE_TICKS;
        settleCount++;
        setResting(false);
        triggerAnim("actions", "settle");
    }

    private void tickToysAndRest() {
        if (toyCooldown > 0) {
            toyCooldown--;
        }
        if (isChewing() && --chewTicks <= 0) {
            setChewing(false);
        }
        if (pounceIn > 0 && --pounceIn == 0 && pounceAt != null && onGround()) {
            Vec3 dir = pounceAt.subtract(position()).multiply(1, 0, 1).normalize();
            setDeltaMovement(dir.scale(0.45).add(0, 0.32, 0));
            pounceAt = null;
        }

        // bed / kennel: settle in, then nap; heal faster while napping there; up again in the morning
        if (settleTicks > 0 && --settleTicks == 0) {
            setOrderedToSit(true);
            setInSittingPose(true);
            sitTicks = NAP_DELAY_TICKS - 1;
        }
        if (restSpot != null) {
            BlockState state = level().getBlockState(restSpot);
            boolean valid = state.getBlock() instanceof DogRestBlock rest && position().distanceToSqr(rest.restPosition(state, restSpot)) < 1.0;
            if (!valid || settleTicks == 0 && !isOrderedToSit()) {
                restSpot = null;
            } else if (isNapping() && getHealth() < getMaxHealth() && ++restHealTicks >= 60) {
                restHealTicks = 0;
                heal(1.0F);
            }
            if (restSpot != null && restSpotIsAuto && level().isDay() && isNapping()) {
                restSpot = null; // morning: back to waiting around
                setOrderedToSit(false);
                setInSittingPose(false);
            }
        }

        // standing about with nothing to do for long enough: lie down for a rest
        boolean still = isTame() && onGround() && navigation.isDone() && !isOrderedToSit() && !isNapping() && getTarget() == null
                && getCarried().isEmpty() && !isExcited() && !isInWater() && getDeltaMovement().horizontalDistanceSqr() < 1.0E-4;
        if (!still) {
            if (isResting() && (!navigation.isDone() || getTarget() != null || isOrderedToSit())) {
                setResting(false);
            }
            stillTicks = 0;
        } else if (!isResting() && ++stillTicks > REST_AFTER_TICKS && random.nextInt(40) == 0) {
            setResting(true);
            restTicks = 600 + random.nextInt(1200);
        } else if (isResting() && --restTicks <= 0) {
            setResting(false);
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
        tickToysAndRest();
        tickShake();
        tickBoat();
        if (isInWater() && hasUpgrade(DogUpgrade.LIFE_VEST)) {
            setAirSupply(getMaxAirSupply());
        }

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
            if ("dig".equals(currentAction) && actionTicks % 3 == 0 && actionTicks > 6 && level() instanceof ServerLevel serverLevel) {
                Vec3 front = position().add(Vec3.directionFromRotation(0, yBodyRot).scale(0.5));
                serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level().getBlockState(getOnPos())),
                        front.x, getY() + 0.1, front.z, 4, 0.15, 0.05, 0.15, 0.1);
            }
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
        return super.isImmobile() || shakeTicks > 0 || isDowned() || settleTicks > 0 || isChewing();
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
            tickClientEffects();
        }
    }

    /** Purely visual: Zzz while napping, stars while knocked out, dust kicked up while running. */
    private void tickClientEffects() {
        Vec3 eye = getEyePosition();
        if (isNapping() && tickCount % 34 == 0) {
            level().addParticle(ModParticles.SLEEP_Z.get(), eye.x + (random.nextDouble() - 0.5) * 0.2, eye.y + 0.25, eye.z + (random.nextDouble() - 0.5) * 0.2, 0, 0, 0);
        }
        if (isDowned() && tickCount % 5 == 0) {
            level().addParticle(ModParticles.DIZZY_STAR.get(), eye.x, eye.y + 0.35 - getBbHeight() * 0.4, eye.z, random.nextDouble() * Mth.TWO_PI, 0, 0);
        }
        double dx = getX() - xo;
        double dz = getZ() - zo;
        if (onGround() && !isInWater() && dx * dx + dz * dz > 0.04 && tickCount % 2 == 0) {
            BlockState below = level().getBlockState(getOnPos());
            if (below.getRenderShape() != RenderShape.INVISIBLE) {
                level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, below).setPos(getOnPos()),
                        getX() + (random.nextDouble() - 0.5) * getBbWidth(), getY() + 0.1, getZ() + (random.nextDouble() - 0.5) * getBbWidth(),
                        -dx * 2, 0.15, -dz * 2);
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
            if (source.is(DamageTypeTags.IS_FIRE) && hasUpgrade(DogUpgrade.FIRE_CLOTH)) {
                amount *= 0.25F;
            }
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && hasUpgrade(DogUpgrade.SPIKES) && !source.is(DamageTypes.THORNS)
                && source.getDirectEntity() instanceof LivingEntity attacker && attacker != this) {
            attacker.hurt(damageSources().thorns(this), 2.0F);
        }
        return hurt;
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
                openMenu(player);
                return InteractionResult.SUCCESS;
            }
        }

        if (isOwnedBy(player) && tryEquip(player, stack)) {
            return InteractionResult.SUCCESS;
        }

        if (isOwnedBy(player) && stack.isEmpty() && !getCarried().isEmpty()) {
            ItemStack toy = getCarried();
            setCarried(ItemStack.EMPTY);
            if (!player.getInventory().add(toy)) {
                player.drop(toy, false);
            }
            playSound(SoundEvents.ITEM_PICKUP, 0.5F, 1.2F);
            return InteractionResult.SUCCESS;
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
        tag.putString("Variant", getVariant().id());
        if (!getCarried().isEmpty()) {
            tag.put("Carried", getCarried().save(registryAccess()));
        }
        if (restSpot != null) {
            tag.putLong("RestSpot", restSpot.asLong());
            tag.putBoolean("RestSpotAuto", restSpotIsAuto);
        }
        tag.putInt("DownedTicks", downedTicks);
        // with slot numbers: SimpleContainer#createTag would lose which slot each item was in
        tag.put("Gear", ContainerHelper.saveAllItems(new CompoundTag(), gear.getItems(), registryAccess()));
        tag.put("Bag", ContainerHelper.saveAllItems(new CompoundTag(), bag.getItems(), registryAccess()));
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
        setCarried(tag.contains("Carried") ? ItemStack.parseOptional(registryAccess(), tag.getCompound("Carried")) : ItemStack.EMPTY);
        restSpot = tag.contains("RestSpot") ? BlockPos.of(tag.getLong("RestSpot")) : null;
        restSpotIsAuto = tag.getBoolean("RestSpotAuto");
        for (DogVariant variant : DogVariant.values()) {
            if (variant.id().equals(tag.getString("Variant"))) {
                setVariant(variant);
            }
        }
        downedTicks = tag.getInt("DownedTicks");
        gear.clearContent();
        bag.clearContent();
        ContainerHelper.loadAllItems(tag.getCompound("Gear"), gear.getItems(), registryAccess());
        ContainerHelper.loadAllItems(tag.getCompound("Bag"), bag.getItems(), registryAccess());
        onGearChanged();
        applyLevelStats();
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Order matters: a later controller overrides the bones it animates.
        controllers.add(new AnimationController<>(this, "body", 5, this::bodyAnimation));
        controllers.add(new AnimationController<>(this, "tail", 5, this::tailAnimation));
        controllers.add(new AnimationController<>(this, "head", 5, this::headAnimation));
        // the jaw closes round whatever is carried
        controllers.add(new AnimationController<>(this, "mouth", 3,
                state -> !getCarried().isEmpty() && !isNapping() && !isChewing() ? state.setAndContinue(CARRY) : PlayState.STOP));
        // eyes blink while awake; asleep or knocked out the body clip keeps them shut
        controllers.add(new AnimationController<>(this, "eyes", 0,
                state -> isNapping() || isDowned() ? PlayState.STOP : state.setAndContinue(BLINK)));
        controllers.add(new AnimationController<>(this, "actions", 3, state -> PlayState.STOP)
                .triggerableAnim("bark", action("bark"))
                .triggerableAnim("land", action("land"))
                .triggerableAnim("shake", action("shake"))
                .triggerableAnim("play_bow", action("play_bow"))
                .triggerableAnim("sniff", action("sniff"))
                .triggerableAnim("stretch", action("stretch"))
                .triggerableAnim("yawn", action("yawn"))
                .triggerableAnim("scratch", action("scratch"))
                .triggerableAnim("sneeze", action("sneeze"))
                .triggerableAnim("dig", action("dig"))
                .triggerableAnim("roll_over", action("roll_over"))
                .triggerableAnim("howl", action("howl"))
                .triggerableAnim("toy_shake", action("toy_shake"))
                .triggerableAnim("toss", action("toss"))
                .triggerableAnim("pounce", action("pounce"))
                .triggerableAnim("settle", action("settle")));
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
        if (isChewing()) {
            return state.setAndContinue(CHEW);
        }
        if (isInSittingPose() || isPassenger()) {
            return state.setAndContinue(isInterested() && isInSittingPose() ? BEG : SIT);
        }
        if (isResting() && !state.isMoving()) {
            return state.setAndContinue(REST);
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
