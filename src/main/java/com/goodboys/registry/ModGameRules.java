package com.goodboys.registry;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

public final class ModGameRules {
    /**
     * Survival mode for dogs, picked when the world is created (or later with {@code /gamerule}).
     * {@code false}: "Happy Doggo", dogs get knocked out instead of dying.
     * {@code true}: "All The Dogs Go To Heaven", dogs die for good.
     */
    public static final GameRules.Key<GameRules.BooleanValue> PERMADEATH =
            GameRules.register("goodboysPermadeath", GameRules.Category.MOBS, GameRules.BooleanValue.create(false));

    private ModGameRules() {
    }

    /** Touching the class registers the rules; call during mod construction. */
    public static void init() {
    }

    public static boolean permadeath(Level level) {
        return level.getGameRules().getBoolean(PERMADEATH);
    }
}
