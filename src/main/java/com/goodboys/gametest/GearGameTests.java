package com.goodboys.gametest;

import java.util.ArrayList;
import java.util.List;

import com.goodboys.GoodBoys;
import com.goodboys.entity.Dog;
import com.goodboys.entity.DogLevels;
import com.goodboys.entity.DogVariant;
import com.goodboys.entity.WolfConversion;
import com.goodboys.item.DogAccessoryItem.Accessory;
import com.goodboys.item.DogArmorItem;
import com.goodboys.item.DogUpgrade;
import com.goodboys.registry.ModEntities;
import com.goodboys.registry.ModItems;
import com.goodboys.registry.ModRecipes;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.WolfVariants;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dog armour, upgrades, accessories and the workbench. Uses data/goodboys/structure/arena.nbt (spawn at y=2). */
@GameTestHolder(GoodBoys.MODID)
@PrefixGameTestTemplate(false)
public class GearGameTests {
    private static Dog tamedDog(GameTestHelper helper, int x, int z) {
        Dog dog = helper.spawn(ModEntities.DOG.get(), x, 2, z);
        dog.tame(helper.makeMockPlayer(GameType.SURVIVAL));
        return dog;
    }

    private static ItemStack armor(DogArmorItem item, DogUpgrade... upgrades) {
        ItemStack stack = new ItemStack(item);
        List<ItemStack> list = new ArrayList<>();
        for (DogUpgrade upgrade : upgrades) {
            list.add(new ItemStack(ModItems.UPGRADES.get(upgrade).get()));
        }
        DogArmorItem.setUpgrades(stack, list);
        return stack;
    }

    @GameTest(template = "arena")
    public static void armorAndUpgradesChangeStats(GameTestHelper helper) {
        Dog dog = tamedDog(helper, 4, 4);
        double baseAttack = dog.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double baseSpeed = dog.getAttributeValue(Attributes.MOVEMENT_SPEED);
        dog.getGear().setItem(Dog.GEAR_ARMOR, armor(ModItems.IRON_DOG_ARMOR.get(), DogUpgrade.PLATE, DogUpgrade.CLAWS, DogUpgrade.RUNNING_SHOES));
        helper.assertTrue(dog.getAttributeValue(Attributes.ARMOR) == 9.0, "iron armour + plate should give 9 armour, got " + dog.getAttributeValue(Attributes.ARMOR));
        helper.assertTrue(dog.getAttributeValue(Attributes.ATTACK_DAMAGE) == baseAttack + 2, "claws did not add damage");
        helper.assertTrue(dog.getAttributeValue(Attributes.MOVEMENT_SPEED) > baseSpeed * 1.14, "running shoes did not speed the dog up");
        helper.assertTrue(dog.hasUpgrade(DogUpgrade.CLAWS) && !dog.hasUpgrade(DogUpgrade.BAG), "upgrade lookup is wrong");

        dog.getGear().setItem(Dog.GEAR_ARMOR, ItemStack.EMPTY);
        helper.assertTrue(dog.getAttributeValue(Attributes.ARMOR) == 0.0, "armour stats stayed after taking it off");
        helper.assertTrue(dog.getAttributeValue(Attributes.ATTACK_DAMAGE) == baseAttack, "claw damage stayed after taking it off");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void fireClothAndSpikes(GameTestHelper helper) {
        Dog plain = tamedDog(helper, 2, 4);
        Dog clothed = tamedDog(helper, 6, 4);
        clothed.getGear().setItem(Dog.GEAR_ARMOR, armor(ModItems.LEATHER_DOG_ARMOR.get(), DogUpgrade.FIRE_CLOTH, DogUpgrade.SPIKES));
        float before = clothed.getHealth();
        plain.hurt(helper.getLevel().damageSources().inFire(), 4.0F);
        clothed.hurt(helper.getLevel().damageSources().inFire(), 4.0F);
        float plainLoss = plain.getMaxHealth() - plain.getHealth();
        float clothLoss = before - clothed.getHealth();
        helper.assertTrue(clothLoss < plainLoss, "fireproof cloth did not reduce fire damage (" + clothLoss + " vs " + plainLoss + ")");

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 4, 2, 6);
        zombie.setNoAi(true);
        float zombieBefore = zombie.getHealth();
        helper.runAfterDelay(15, () -> { // after the fire hit's invulnerability frames
            clothed.hurt(helper.getLevel().damageSources().mobAttack(zombie), 2.0F);
            helper.assertTrue(zombie.getHealth() < zombieBefore, "spikes did not hurt the attacker");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 600)
    public static void fieldKitGetsAHeartyDogBackUp(GameTestHelper helper) {
        Dog dog = tamedDog(helper, 4, 4);
        dog.addExperience(DogLevels.totalXpFor(DogLevels.HEART_LEVEL));
        dog.getGear().setItem(Dog.GEAR_ARMOR, armor(ModItems.IRON_DOG_ARMOR.get(), DogUpgrade.FIELD_KIT));
        dog.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
        helper.assertTrue(dog.isDowned(), "dog did not get knocked out");
        helper.succeedWhen(() -> {
            helper.assertFalse(dog.isDowned(), "field kit did not get the dog up");
            helper.assertFalse(dog.hasUpgrade(DogUpgrade.FIELD_KIT), "field kit was not used up");
        });
    }

    @GameTest(template = "arena")
    public static void gearIsSavedWithTheDog(GameTestHelper helper) {
        Dog dog = tamedDog(helper, 4, 4);
        dog.getGear().setItem(Dog.GEAR_ARMOR, armor(ModItems.DIAMOND_DOG_ARMOR.get(), DogUpgrade.BAG, DogUpgrade.TRACKER));
        dog.getGear().setItem(Dog.GEAR_HEAD, new ItemStack(ModItems.ACCESSORIES.get(Accessory.TOP_HAT).get()));
        dog.getBag().setItem(3, new ItemStack(Items.BONE, 7));

        CompoundTag tag = new CompoundTag();
        dog.saveWithoutId(tag);
        Dog copy = ModEntities.DOG.get().create(helper.getLevel());
        copy.load(tag);
        helper.assertTrue(copy.getGear().getItem(Dog.GEAR_ARMOR).is(ModItems.DIAMOND_DOG_ARMOR.get()), "armour was not saved");
        helper.assertTrue(copy.hasUpgrade(DogUpgrade.BAG) && copy.hasUpgrade(DogUpgrade.TRACKER), "upgrades were not saved");
        helper.assertTrue(copy.getBag().getItem(3).getCount() == 7, "saddlebag contents were not saved");
        helper.assertTrue(copy.getAttributeValue(Attributes.ARMOR) == 9.0, "loaded dog has the wrong armour value");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void coatColourIsKeptAndComesFromTheWolf(GameTestHelper helper) {
        Dog dog = tamedDog(helper, 4, 4);
        dog.setVariant(DogVariant.BLACK);
        CompoundTag tag = new CompoundTag();
        dog.saveWithoutId(tag);
        Dog copy = ModEntities.DOG.get().create(helper.getLevel());
        copy.load(tag);
        helper.assertTrue(copy.getVariant() == DogVariant.BLACK, "coat colour was not saved");

        Wolf wolf = helper.spawn(EntityType.WOLF, 2, 2, 2);
        wolf.setVariant(helper.getLevel().registryAccess().registryOrThrow(Registries.WOLF_VARIANT).getHolderOrThrow(WolfVariants.SNOWY));
        Dog converted = WolfConversion.convert(wolf);
        helper.assertTrue(converted != null && converted.getVariant() == DogVariant.WHITE, "a snowy wolf should become a white shepherd");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void releasedDogDropsItsGear(GameTestHelper helper) {
        Dog dog = tamedDog(helper, 4, 4);
        dog.getGear().setItem(Dog.GEAR_ARMOR, armor(ModItems.LEATHER_DOG_ARMOR.get(), DogUpgrade.BAG));
        dog.getBag().setItem(0, new ItemStack(Items.BONE));
        dog.release();
        helper.assertTrue(dog.getGear().isEmpty() && dog.getBag().isEmpty(), "released dog kept the owner's gear");
        helper.assertTrue(helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                .anyMatch(s -> s.is(ModItems.LEATHER_DOG_ARMOR.get())), "the armour was not dropped");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void workbenchKnowsItsRecipes(GameTestHelper helper) {
        ItemStack i = new ItemStack(Items.IRON_INGOT);
        ItemStack l = new ItemStack(Items.LEATHER);
        ItemStack e = ItemStack.EMPTY;
        CraftingInput input = CraftingInput.of(3, 3, List.of(e, e, i, i, l, i, i, e, i));
        ItemStack result = helper.getLevel().getRecipeManager()
                .getRecipeFor(ModRecipes.DOG_WORKBENCH.get(), input, helper.getLevel())
                .map(r -> r.value().assemble(input, helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(result.is(ModItems.IRON_DOG_ARMOR.get()), "iron dog armour recipe not found, got " + result);
        int count = helper.getLevel().getRecipeManager().getAllRecipesFor(ModRecipes.DOG_WORKBENCH.get()).size();
        helper.assertTrue(count == 19, "expected 19 workbench recipes, found " + count);
        helper.succeed();
    }
}
