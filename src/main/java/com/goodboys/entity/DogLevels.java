package com.goodboys.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/** Dog progression numbers in one place: experience curve, rewards and what each level is worth. */
public final class DogLevels {
    public static final int MAX_LEVEL = 25;

    /** Experience for keeping the owner company, granted every {@link #COMPANION_INTERVAL} ticks. */
    public static final int COMPANION_XP = 2;
    public static final int COMPANION_INTERVAL = 20 * 60;

    public static final double UNTAMED_HEALTH = 8.0;
    public static final double TAMED_HEALTH = 30.0;
    private static final double HEALTH_PER_LEVEL = 1.0;
    public static final double BASE_ATTACK = 4.0;
    private static final double ATTACK_PER_LEVEL = 0.25;
    public static final double BASE_SPEED = 0.3;
    private static final double SPEED_PER_LEVEL = 0.004;

    private DogLevels() {
    }

    /** Experience needed to go from {@code level} to the next one. */
    public static int xpToNext(int level) {
        return level >= MAX_LEVEL ? 0 : 8 + 4 * level;
    }

    /** Total experience from level 1 to {@code level}. */
    public static int totalXpFor(int level) {
        int total = 0;
        for (int l = 1; l < Math.min(level, MAX_LEVEL); l++) {
            total += xpToNext(l);
        }
        return total;
    }

    /** What a kill is worth: monsters by toughness, game animals less, other players a flat bounty. */
    public static int killReward(LivingEntity killed) {
        if (killed instanceof Player) {
            return 5;
        }
        float health = killed.getMaxHealth();
        if (killed instanceof Enemy) {
            return Math.max(1, Mth.ceil(health / 2.0F));
        }
        if (killed instanceof Animal) {
            return Math.max(1, Mth.floor(health / 4.0F));
        }
        return 1;
    }

    public static double maxHealth(int level, boolean tamed) {
        return tamed ? TAMED_HEALTH + (level - 1) * HEALTH_PER_LEVEL : UNTAMED_HEALTH;
    }

    public static double attack(int level) {
        return BASE_ATTACK + (level - 1) * ATTACK_PER_LEVEL;
    }

    public static double speed(int level) {
        return BASE_SPEED + (level - 1) * SPEED_PER_LEVEL;
    }
}
