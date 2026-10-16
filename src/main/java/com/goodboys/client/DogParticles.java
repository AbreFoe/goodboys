package com.goodboys.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Client particles for dogs. */
final class DogParticles {
    private DogParticles() {
    }

    /** Floats up and sideways in a lazy wave, grows a little and fades out. */
    static class SleepZ extends TextureSheetParticle {
        private final float phase;

        SleepZ(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z);
            pickSprite(sprites);
            lifetime = 46;
            gravity = 0;
            hasPhysics = false;
            quadSize = 0.08F;
            phase = random.nextFloat() * Mth.TWO_PI;
            yd = 0.012;
        }

        @Override
        public void tick() {
            super.tick();
            float t = age / (float) lifetime;
            xd = Mth.sin(phase + age * 0.2F) * 0.006;
            quadSize = 0.08F + 0.07F * t;
            alpha = t < 0.15F ? t / 0.15F : 1 - (t - 0.15F) / 0.85F;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /** Orbits the point it was spawned at; xSpeed carries the starting angle. */
    static class DizzyStar extends TextureSheetParticle {
        private final double cx, cy, cz;
        private float angle;

        DizzyStar(ClientLevel level, double x, double y, double z, double startAngle, SpriteSet sprites) {
            super(level, x, y, z);
            pickSprite(sprites);
            cx = x;
            cy = y;
            cz = z;
            angle = (float) startAngle;
            lifetime = 16;
            gravity = 0;
            hasPhysics = false;
            quadSize = 0.06F;
            tick();
        }

        @Override
        public void tick() {
            xo = this.x;
            yo = this.y;
            zo = this.z;
            if (age++ >= lifetime) {
                remove();
                return;
            }
            angle += 0.35F;
            setPos(cx + Mth.cos(angle) * 0.32, cy + Mth.sin(angle * 2) * 0.04, cz + Mth.sin(angle) * 0.32);
            float t = age / (float) lifetime;
            alpha = t > 0.7F ? 1 - (t - 0.7F) / 0.3F : 1;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    record SleepProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xs, double ys, double zs) {
            return new SleepZ(level, x, y, z, sprites);
        }
    }

    record StarProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xs, double ys, double zs) {
            return new DizzyStar(level, x, y, z, xs, sprites);
        }
    }
}
