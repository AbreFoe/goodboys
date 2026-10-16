package com.goodboys.entity;

import java.util.Locale;

import com.goodboys.GoodBoys;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.WolfVariant;
import net.minecraft.world.entity.animal.WolfVariants;

/** Coat colours. Each one is a texture recoloured from the shepherd (same pattern, different palette). */
public enum DogVariant {
    SHEPHERD(50, "dog"),
    SABLE(20, "dog_sable"),
    BLACK(15, "dog_black"),
    WHITE(15, "dog_white");

    private final int weight;
    private final ResourceLocation texture;

    DogVariant(int weight, String texture) {
        this.weight = weight;
        this.texture = GoodBoys.id("textures/entity/" + texture + ".png");
    }

    public ResourceLocation texture() {
        return texture;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DogVariant byId(int id) {
        DogVariant[] all = values();
        return all[Math.floorMod(id, all.length)];
    }

    public static DogVariant random(RandomSource random) {
        int total = 0;
        for (DogVariant v : values()) {
            total += v.weight;
        }
        int roll = random.nextInt(total);
        for (DogVariant v : values()) {
            roll -= v.weight;
            if (roll < 0) {
                return v;
            }
        }
        return SHEPHERD;
    }

    /** A tamed vanilla wolf keeps something like its coat when it becomes a dog. */
    public static DogVariant fromWolf(Holder<WolfVariant> wolf) {
        if (wolf.is(WolfVariants.PALE) || wolf.is(WolfVariants.SNOWY)) {
            return WHITE;
        }
        if (wolf.is(WolfVariants.BLACK)) {
            return BLACK;
        }
        if (wolf.is(WolfVariants.ASHEN) || wolf.is(WolfVariants.STRIPED) || wolf.is(WolfVariants.WOODS)) {
            return SABLE;
        }
        return SHEPHERD;
    }
}
