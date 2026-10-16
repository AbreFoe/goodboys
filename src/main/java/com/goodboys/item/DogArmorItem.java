package com.goodboys.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;

/** Dog armour. Its three upgrade slots live on the item itself, so they move with it from dog to dog. */
public class DogArmorItem extends Item {
    public static final int UPGRADE_SLOTS = 3;

    public enum Tier {
        LEATHER(3, 0, 0xFFA0703C),
        IRON(6, 0, 0xFFD8D8D8),
        DIAMOND(9, 2, 0xFF6FE3DA);

        private final int armor;
        private final int toughness;
        private final int tint;

        Tier(int armor, int toughness, int tint) {
            this.armor = armor;
            this.toughness = toughness;
            this.tint = tint;
        }

        public int armor() {
            return armor;
        }

        public int toughness() {
            return toughness;
        }

        /** ARGB colour the grey armour texture is multiplied with. */
        public int tint() {
            return tint;
        }
    }

    private final Tier tier;

    public DogArmorItem(Tier tier, Properties properties) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public Tier tier() {
        return tier;
    }

    public static List<ItemStack> getUpgrades(ItemStack armor) {
        NonNullList<ItemStack> list = NonNullList.withSize(UPGRADE_SLOTS, ItemStack.EMPTY);
        armor.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(list);
        return list;
    }

    public static void setUpgrades(ItemStack armor, List<ItemStack> upgrades) {
        armor.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(upgrades));
    }

    public static boolean hasUpgrade(ItemStack armor, DogUpgrade upgrade) {
        if (!(armor.getItem() instanceof DogArmorItem)) {
            return false;
        }
        for (ItemStack stack : armor.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItems()) {
            if (stack.getItem() instanceof DogUpgradeItem item && item.upgrade() == upgrade) {
                return true;
            }
        }
        return false;
    }

    /** Takes one installed upgrade out of the armour (used up, like the field kit). */
    public static void consumeUpgrade(ItemStack armor, DogUpgrade upgrade) {
        List<ItemStack> upgrades = new ArrayList<>(getUpgrades(armor));
        for (int i = 0; i < upgrades.size(); i++) {
            if (upgrades.get(i).getItem() instanceof DogUpgradeItem item && item.upgrade() == upgrade) {
                upgrades.set(i, ItemStack.EMPTY);
                setUpgrades(armor, upgrades);
                return;
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.goodboys.dog_armor.armor", tier.armor()).withStyle(ChatFormatting.BLUE));
        if (tier.toughness() > 0) {
            tooltip.add(Component.translatable("item.goodboys.dog_armor.toughness", tier.toughness()).withStyle(ChatFormatting.BLUE));
        }
        List<ItemStack> upgrades = getUpgrades(stack);
        long used = upgrades.stream().filter(s -> !s.isEmpty()).count();
        tooltip.add(Component.translatable("item.goodboys.dog_armor.slots", used, UPGRADE_SLOTS).withStyle(ChatFormatting.GRAY));
        for (ItemStack upgrade : upgrades) {
            if (!upgrade.isEmpty()) {
                tooltip.add(Component.literal(" • ").append(upgrade.getHoverName()).withStyle(ChatFormatting.DARK_AQUA));
            }
        }
    }
}
