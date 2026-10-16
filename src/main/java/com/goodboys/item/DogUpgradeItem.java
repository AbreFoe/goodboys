package com.goodboys.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class DogUpgradeItem extends Item {
    private final DogUpgrade upgrade;

    public DogUpgradeItem(DogUpgrade upgrade, Properties properties) {
        super(properties);
        this.upgrade = upgrade;
    }

    public DogUpgrade upgrade() {
        return upgrade;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.goodboys." + upgrade.id() + ".info").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.goodboys.upgrade.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
