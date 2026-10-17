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
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
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
        ServerPlayer owner = TestPlayers.inLevel(helper);
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

    /** A thrown ball is fetched once; the dog then leaves it at the owner's feet instead of bringing it again and again. */
    @GameTest(template = "arena", timeoutTicks = 500)
    public static void returnedBallStaysPut(GameTestHelper helper) {
        ServerPlayer owner = TestPlayers.inLevel(helper);
        owner.moveTo(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
        Dog dog = helper.spawn(ModEntities.DOG.get(), 2, 2, 3);
        dog.tame(owner);
        ItemEntity ball = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(ModItems.TOYS.get(Toy.TENNIS_BALL).get()));
        Vec3 at = helper.absoluteVec(new Vec3(7.5, 2, 7.5));
        ball.moveTo(at.x, at.y, at.z);
        ball.setPickUpDelay(32767);
        helper.getLevel().addFreshEntity(ball);
        dog.noticeThrow(ball);
        int[] pickups = {0};
        boolean[] carrying = {false};
        int[] idle = {0};
        helper.onEachTick(() -> {
            boolean now = !dog.getCarried().isEmpty();
            if (now && !carrying[0]) {
                pickups[0]++;
            }
            carrying[0] = now;
            if (pickups[0] > 0 && !now) {
                idle[0]++;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(pickups[0] == 1, "ball was picked up " + pickups[0] + " times");
            helper.assertTrue(idle[0] > 150, "waiting to see the ball stays put");
        });
    }

    /** FIND with a bone in hand: the bones lying about get brought back in one go. */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "goodboys_find")
    public static void findBringsDroppedItems(GameTestHelper helper) {
        ServerPlayer owner = TestPlayers.inLevel(helper);
        owner.moveTo(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
        Dog dog = helper.spawn(ModEntities.DOG.get(), 2, 2, 3);
        dog.tame(owner);
        ItemEntity bones = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.BONE, 5));
        Vec3 at = helper.absoluteVec(new Vec3(7.5, 2, 7.5));
        bones.moveTo(at.x, at.y, at.z);
        bones.setPickUpDelay(32767);
        helper.getLevel().addFreshEntity(bones);
        dog.command(DogCommand.FIND, null, null);
        dog.startFind(new ItemStack(Items.BONE));
        helper.succeedWhen(() -> {
            helper.assertFalse(dog.isFinding(), "dog is still searching");
            boolean back = helper.getEntities(EntityType.ITEM).stream()
                    .anyMatch(i -> i.getItem().is(Items.BONE) && i.getItem().getCount() == 5 && i.distanceToSqr(owner) < 3.0 * 3.0);
            helper.assertTrue(back, "the bones did not come back to the owner");
        });
    }

    /** FIND with raw iron in hand: the dog goes and stands over the iron ore buried in the floor. */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "goodboys_find")
    public static void findLeadsToOre(GameTestHelper helper) {
        ServerPlayer owner = TestPlayers.inLevel(helper);
        owner.moveTo(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
        Dog dog = helper.spawn(ModEntities.DOG.get(), 2, 2, 2);
        dog.tame(owner);
        BlockPos ore = new BlockPos(7, 1, 6);
        helper.setBlock(ore, Blocks.DEEPSLATE_IRON_ORE);
        BlockPos abs = helper.absolutePos(ore);
        dog.command(DogCommand.FIND, null, null);
        dog.startFind(new ItemStack(Items.RAW_IRON));
        helper.succeedWhen(() -> {
            double dx = dog.getX() - (abs.getX() + 0.5);
            double dz = dog.getZ() - (abs.getZ() + 0.5);
            helper.assertTrue(dx * dx + dz * dz < 2.0 * 2.0, "dog is not standing over the ore");
            helper.assertTrue(dog.isFinding(), "dog stopped pointing");
        });
    }

    /** FIND for something that isn't anywhere near: gives up. */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "goodboys_find")
    public static void findGivesUp(GameTestHelper helper) {
        ServerPlayer owner = TestPlayers.inLevel(helper);
        Dog dog = helper.spawn(ModEntities.DOG.get(), 3, 2, 3);
        dog.tame(owner);
        dog.command(DogCommand.FIND, null, null);
        dog.startFind(new ItemStack(Items.EMERALD));
        helper.runAfterDelay(5, () -> helper.succeedWhen(() -> helper.assertFalse(dog.isFinding(), "dog is still searching")));
    }

    @GameTest(template = "arena", timeoutTicks = 300)
    public static void dogChewsTheChewBone(GameTestHelper helper) {
        ServerPlayer owner = TestPlayers.inLevel(helper);
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

    /** Settles once, then stays asleep there (no turning round again and again). */
    private static void staysSettled(GameTestHelper helper, BlockState spotState, boolean night) {
        if (night) {
            helper.getLevel().setDayTime(18000);
        }
        Dog dog = helper.spawn(ModEntities.DOG.get(), 2, 2, 2);
        dog.tame(helper.makeMockPlayer(GameType.SURVIVAL));
        BlockPos spot = new BlockPos(6, 2, 6);
        helper.setBlock(spot, spotState);
        helper.runAfterDelay(2, () -> {
            dog.command(DogCommand.STAY, null, null);
            if (!night) {
                dog.command(DogCommand.SIT, null, null);
            }
        });
        int[] napTicks = {0};
        helper.onEachTick(() -> {
            if (dog.isNapping()) {
                napTicks[0]++;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(dog.getSettleCount() <= 1, "dog settled " + dog.getSettleCount() + " times");
            helper.assertTrue(napTicks[0] > 200, "dog has not slept long enough yet");
            helper.assertTrue(dog.isNapping(), "dog woke up");
            helper.assertTrue(helper.absolutePos(spot).equals(dog.getRestSpot()), "dog left its spot");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 900)
    public static void kennelNapIsStable(GameTestHelper helper) {
        staysSettled(helper, ModBlocks.KENNEL.get().defaultBlockState(), false);
    }

    @GameTest(template = "arena", timeoutTicks = 1400)
    public static void kennelNightIsStable(GameTestHelper helper) {
        staysSettled(helper, ModBlocks.KENNEL.get().defaultBlockState(), true);
    }

    @GameTest(template = "arena", timeoutTicks = 900)
    public static void kennelEastNapIsStable(GameTestHelper helper) {
        staysSettled(helper, ModBlocks.KENNEL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST), false);
    }

    /** Door on the far side: the dog has to walk round to the doorway. */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void kennelSouthNapIsStable(GameTestHelper helper) {
        staysSettled(helper, ModBlocks.KENNEL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), false);
    }

    @GameTest(template = "arena", timeoutTicks = 900)
    public static void bedNapIsStable(GameTestHelper helper) {
        staysSettled(helper, ModBlocks.DOG_BED.get().defaultBlockState(), false);
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
