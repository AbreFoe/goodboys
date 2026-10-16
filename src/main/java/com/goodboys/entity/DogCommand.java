package com.goodboys.entity;

import java.util.Locale;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Everything the owner can tell a dog from the command menu. Order = order around the wheel, clockwise from the top. */
public enum DogCommand {
    FOLLOW(Items.LEAD),
    STAY(Items.OAK_SIGN),
    SIT(Items.RED_BED),
    GUARD(Items.SHIELD),
    GO_TO(Items.COMPASS),
    ATTACK(Items.IRON_SWORD),
    HUNT(Items.BOW),
    FIND(Items.SPYGLASS, false);

    private final Item icon;
    private final boolean available;

    DogCommand(Item icon) {
        this(icon, true);
    }

    DogCommand(Item icon, boolean available) {
        this.icon = icon;
        this.available = available;
    }

    public Item icon() {
        return icon;
    }

    /** Not every command is implemented yet; the menu shows the rest greyed out. */
    public boolean available() {
        return available;
    }

    public String translationKey() {
        return "gui.goodboys.command." + name().toLowerCase(Locale.ROOT);
    }

    public static DogCommand byId(int id) {
        DogCommand[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }
}
