package com.goodboys.gametest;

import com.goodboys.GoodBoys;
import com.goodboys.entity.Dog;
import com.goodboys.entity.DogCommand;
import com.goodboys.entity.DogMode;
import com.goodboys.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side checks, run with {@code ./gradlew runGameTestServer}. Uses data/goodboys/structure/arena.nbt. */
@GameTestHolder(GoodBoys.MODID)
@PrefixGameTestTemplate(false)
public class DogGameTests {
    @GameTest(template = "arena")
    public static void dogSpawnsAndLives(GameTestHelper helper) {
        Dog dog = helper.spawn(ModEntities.DOG.get(), 4, 2, 4);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(dog.isAlive(), "dog died");
            helper.assertTrue(dog.onGround(), "dog is not standing on the floor");
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void tamedWolfBecomesDog(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Wolf wolf = helper.spawn(EntityType.WOLF, 4, 2, 4);
        wolf.tame(player);
        helper.succeedWhen(() -> {
            helper.assertEntityNotPresent(EntityType.WOLF);
            Dog dog = helper.findOneEntity(ModEntities.DOG.get());
            helper.assertTrue(dog.isTame(), "converted dog is not tame");
            helper.assertTrue(player.getUUID().equals(dog.getOwnerUUID()), "converted dog lost its owner");
        });
    }

    @GameTest(template = "arena")
    public static void dogShakesOffWaterOnLand(GameTestHelper helper) {
        helper.setBlock(4, 2, 4, Blocks.WATER);
        Dog dog = helper.spawn(ModEntities.DOG.get(), 4, 2, 4);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(dog.isInWater(), "dog never got wet");
            helper.setBlock(4, 2, 4, Blocks.AIR);
        });
        helper.runAfterDelay(14, () -> helper.succeedWhen(() -> helper.assertTrue(dog.isShaking(), "dog did not shake off the water")));
    }

    private static Dog tamedDog(GameTestHelper helper, int x, int y, int z) {
        Dog dog = helper.spawn(ModEntities.DOG.get(), x, y, z);
        dog.tame(helper.makeMockPlayer(GameType.SURVIVAL));
        return dog;
    }

    @GameTest(template = "arena", timeoutTicks = 260)
    public static void stayKeepsDogAroundItsSpot(GameTestHelper helper) {
        Dog dog = tamedDog(helper, 4, 2, 4);
        helper.runAfterDelay(5, () -> dog.command(DogCommand.STAY, null, null));
        helper.runAfterDelay(240, () -> {
            helper.assertTrue(dog.getMode() == DogMode.STAY, "dog forgot it was told to stay");
            BlockPos anchor = dog.getAnchor();
            helper.assertTrue(anchor != null && dog.distanceToSqr(anchor.getCenter()) < 4.5 * 4.5, "dog wandered off its spot");
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void guardAttacksMonsterButNotCreeper(GameTestHelper helper) {
        Dog dog = tamedDog(helper, 3, 2, 3);
        helper.runAfterDelay(5, () -> {
            dog.command(DogCommand.GUARD, null, null);
            helper.spawn(EntityType.CREEPER, 5, 2, 3).setNoAi(true);
            helper.spawn(EntityType.HUSK, 5, 2, 5).setNoAi(true);
        });
        helper.succeedWhen(() -> helper.assertTrue(dog.getTarget() instanceof Husk, "guard did not go for the husk"));
    }

    @GameTest(template = "arena")
    public static void huntGoesForGame(GameTestHelper helper) {
        // hunting dogs stay with their owner, so this one needs an owner standing in the world
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        owner.moveTo(helper.absoluteVec(new Vec3(2.5, 2, 2.5)));
        Dog dog = helper.spawn(ModEntities.DOG.get(), 3, 2, 3);
        dog.tame(owner);
        helper.runAfterDelay(5, () -> {
            dog.command(DogCommand.HUNT, null, null);
            helper.spawn(EntityType.PIG, 5, 2, 5).setNoAi(true);
        });
        helper.succeedWhen(() -> helper.assertTrue(dog.getTarget() instanceof Pig, "hunting dog ignored the pig"));
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void goToWalksToTheSpot(GameTestHelper helper) {
        Dog dog = tamedDog(helper, 2, 2, 2);
        BlockPos spot = helper.absolutePos(new BlockPos(6, 2, 6));
        helper.runAfterDelay(5, () -> dog.command(DogCommand.GO_TO, spot, null));
        helper.succeedWhen(() -> helper.assertTrue(dog.distanceToSqr(spot.getCenter()) < 2.0 * 2.0, "dog did not walk to the spot"));
    }

    @GameTest(template = "arena", timeoutTicks = Dog.NAP_DELAY_TICKS + 100)
    public static void sittingDogNapsAndWakesWhenHit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Dog dog = helper.spawn(ModEntities.DOG.get(), 4, 2, 4);
        dog.tame(player);
        dog.setOrderedToSit(true);
        helper.runAfterDelay(Dog.NAP_DELAY_TICKS / 2, () -> helper.assertFalse(dog.isNapping(), "fell asleep too early"));
        helper.runAfterDelay(Dog.NAP_DELAY_TICKS + 20, () -> {
            helper.assertTrue(dog.isNapping(), "dog did not fall asleep while sitting");
            helper.assertTrue(dog.getNapStyle() >= 0 && dog.getNapStyle() < Dog.NAP_STYLES, "unknown sleeping pose");
            dog.hurt(helper.getLevel().damageSources().generic(), 1.0F);
            helper.assertFalse(dog.isNapping(), "dog kept sleeping after being hit");
            helper.assertFalse(dog.isOrderedToSit(), "dog kept sitting after being hit");
            helper.succeed();
        });
    }
}
