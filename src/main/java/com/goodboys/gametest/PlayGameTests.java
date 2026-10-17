package com.goodboys.gametest;

import com.goodboys.GoodBoys;
import com.goodboys.block.DogRestBlock;
import com.goodboys.entity.Dog;
import com.goodboys.entity.DogCommand;
import com.goodboys.item.DogToyItem.Toy;
import com.goodboys.registry.ModBlocks;
import com.goodboys.registry.ModEntities;
import com.goodboys.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Toys and furniture. Arena floor is at relative y=1, spawn at y=2. */
@GameTestHolder(GoodBoys.MODID)
@PrefixGameTestTemplate(false)
public class PlayGameTests {
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void dogFetchesTheBall(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        owner.moveTo(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
        Dog dog = helper.spawn(ModEntities.DOG.get(), 3, 2, 3);
        dog.tame(owner);
        ItemEntity ball = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(ModItems.TOYS.get(Toy.TENNIS_BALL).get()));
        Vec3 at = helper.absoluteVec(new Vec3(7.5, 2, 7.5));
        ball.moveTo(at.x, at.y, at.z);
        ball.setPickUpDelay(32767);
        helper.getLevel().addFreshEntity(ball);
        boolean[] picked = {false};
        helper.onEachTick(() -> picked[0] |= !dog.getCarried().isEmpty());
        helper.succeedWhen(() -> {
            helper.assertTrue(picked[0], "dog never picked the ball up");
            helper.assertTrue(dog.getCarried().isEmpty(), "dog is still holding the ball");
            boolean nearOwner = helper.getEntities(EntityType.ITEM).stream()
                    .anyMatch(i -> i.getItem().is(ModItems.TOYS.get(Toy.TENNIS_BALL).get()) && i.distanceToSqr(owner) < 3.5 * 3.5);
            helper.assertTrue(nearOwner, "the ball did not come back to the owner");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 300)
    public static void dogChewsTheChewBone(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        owner.moveTo(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
        Dog dog = helper.spawn(ModEntities.DOG.get(), 3, 2, 3);
        dog.tame(owner);
        helper.spawnItem(ModItems.TOYS.get(Toy.CHEW_BONE).get(), 6, 2, 6);
        helper.succeedWhen(() -> helper.assertTrue(dog.isChewing(), "dog did not lie down to chew"));
    }

    private static void sitsInRestSpot(GameTestHelper helper, BlockState spotState) {
        Dog dog = helper.spawn(ModEntities.DOG.get(), 2, 2, 2);
        dog.tame(helper.makeMockPlayer(GameType.SURVIVAL));
        BlockPos spot = new BlockPos(6, 2, 6);
        helper.setBlock(spot, spotState);
        BlockPos abs = helper.absolutePos(spot);
        helper.runAfterDelay(2, () -> {
            dog.command(DogCommand.STAY, null, null);
            dog.command(DogCommand.SIT, null, null);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(abs.equals(dog.getRestSpot()), "dog did not take the rest spot");
            helper.assertTrue(dog.isNapping(), "dog is not asleep on its spot");
            Vec3 restAt = ((DogRestBlock) spotState.getBlock()).restPosition(spotState, abs);
            helper.assertTrue(dog.position().distanceToSqr(restAt) < 0.5, "dog is not lying on the spot");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 300)
    public static void sitNearBedNapsOnIt(GameTestHelper helper) {
        sitsInRestSpot(helper, ModBlocks.DOG_BED.get().defaultBlockState());
    }

    @GameTest(template = "arena", timeoutTicks = 300)
    public static void sitNearKennelNapsInIt(GameTestHelper helper) {
        sitsInRestSpot(helper, ModBlocks.KENNEL.get().defaultBlockState());
    }
}
