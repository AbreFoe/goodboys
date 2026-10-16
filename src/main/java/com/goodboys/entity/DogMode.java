package com.goodboys.entity;

/** A dog's standing order. Sitting is separate: a dog can be told to sit in any mode and stands back up into it. */
public enum DogMode {
    /** Follows the owner and defends them. */
    FOLLOW(0),
    /** Stays around a spot, pottering about within a few blocks. */
    STAY(2.5),
    /** Patrols around a spot and attacks monsters that come into it. */
    GUARD(8),
    /** Follows the owner and hunts game animals ({@code #goodboys:huntable}) near them. */
    HUNT(0);

    private final double radius;

    DogMode(double radius) {
        this.radius = radius;
    }

    /** How far from its anchor the dog may roam in this mode. */
    public double radius() {
        return radius;
    }

    public boolean followsOwner() {
        return this == FOLLOW || this == HUNT;
    }

    public boolean usesAnchor() {
        return this == STAY || this == GUARD;
    }

    public static DogMode byName(String name) {
        for (DogMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return FOLLOW;
    }
}
