package com.goodboys.registry;

import com.goodboys.GoodBoys;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, GoodBoys.MODID);

    /** A "Z" that drifts up from a sleeping dog. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SLEEP_Z = PARTICLES.register("sleep_z", () -> new SimpleParticleType(false));
    /** Little stars circling a knocked-out dog's head. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DIZZY_STAR = PARTICLES.register("dizzy_star", () -> new SimpleParticleType(false));

    private ModParticles() {
    }

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
