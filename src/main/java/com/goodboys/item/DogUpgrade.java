package com.goodboys.item;

import java.util.Locale;

/** Things that go into the three upgrade slots of a dog's armour. */
public enum DogUpgrade {
    /** Saddlebags: nine slots of dog inventory. */
    BAG,
    /** Extra armour plate: +3 armour. */
    PLATE,
    /** Shows the dog's health and whereabouts on the owner's screen. */
    TRACKER,
    /** Iron claws: +2 attack damage. */
    CLAWS,
    /** Steel jaw: +2 attack damage and a harder shove. */
    JAW,
    /** Fireproof cloth: fire and lava hurt a quarter as much. */
    FIRE_CLOTH,
    /** Field kit: a dog with the Dog's Heart perk (level {@code DogLevels.HEART_LEVEL}) gets itself back up from a knockout. Used up. */
    FIELD_KIT,
    /** Spikes: whoever bites or hits the dog gets hurt too. */
    SPIKES,
    /** Life vest: never runs out of air, floats when knocked out in water. */
    LIFE_VEST,
    /** Running shoes: +15% speed. */
    RUNNING_SHOES;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
