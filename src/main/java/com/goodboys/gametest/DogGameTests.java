package com.goodboys.gametest;

import com.goodboys.GoodBoys;
import com.goodboys.entity.Dog;
import com.goodboys.registry.ModEntities;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side checks, run with {@code ./gradlew runGameTestServer}. Uses data/goodboys/structure/arena.nbt. */
@GameTestHolder(GoodBoys.MODID)
@PrefixGameTestTemplate(false)
public class DogGameTests {
    @GameTest(template = "arena")
    public static void dogSpawnsAndLives(GameTestHelper helper) {
        Dog dog = helper.spawn(ModEntities.DOG.get(), 2, 2, 2);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(dog.isAlive(), "dog died");
            helper.assertTrue(dog.onGround(), "dog is not standing on the floor");
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void tamedWolfBecomesDog(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Wolf wolf = helper.spawn(EntityType.WOLF, 2, 2, 2);
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
        helper.setBlock(2, 1, 2, Blocks.WATER);
        Dog dog = helper.spawn(ModEntities.DOG.get(), 2, 1, 2);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(dog.isInWater(), "dog never got wet");
            helper.setBlock(2, 1, 2, Blocks.AIR);
        });
        helper.runAfterDelay(14, () -> helper.succeedWhen(() -> helper.assertTrue(dog.isShaking(), "dog did not shake off the water")));
    }

    @GameTest(template = "arena", timeoutTicks = Dog.NAP_DELAY_TICKS + 100)
    public static void sittingDogNapsAndWakesWhenHit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Dog dog = helper.spawn(ModEntities.DOG.get(), 2, 2, 2);
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
