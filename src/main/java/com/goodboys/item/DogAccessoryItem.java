package com.goodboys.item;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Purely for looks: goes in the dog's head slot. Each one shows a bone named {@code acc_<id>} on the model. */
public class DogAccessoryItem extends Item {
    public enum Accessory {
        TOP_HAT, PARTY_HAT, FLOWER_CROWN, SUNGLASSES, BOW, BEANIE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String bone() {
            return "acc_" + id();
        }
    }

    private final Accessory accessory;

    public DogAccessoryItem(Accessory accessory, Properties properties) {
        super(properties.stacksTo(1));
        this.accessory = accessory;
    }

    public Accessory accessory() {
        return accessory;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.goodboys.accessory.hint").withStyle(ChatFormatting.GRAY));
    }
}
