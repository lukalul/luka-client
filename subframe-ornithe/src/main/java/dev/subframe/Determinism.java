package dev.subframe;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import dev.subframe.mixin.EntityAccessor;

import java.util.Random;

/**
 * Makes the client's random numbers reproducible. While recording or replaying, every client-side entity's
 * Random and the client world's Random are seeded from the recording's master seed and the entity id (ids
 * come from the server, so they match in the replay). Particles are handled separately in ParticleState.
 */
final class Determinism {
    static volatile boolean active;
    static volatile long seed;

    static long mix(long a, long b) {
        long z = a + b * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    static void reseed(Entity e, long s) {
        Random r = ((EntityAccessor) e).subframe$getRandom();
        r.setSeed(s);
    }

    static void onEntityJoin(Entity entity, World world) {
        try {
            if (active && world.isRemote) {
                reseed(entity, mix(seed, entity.getEntityId()));
            }
        } catch (Throwable t) {
            SubFrameMod.problem("onEntityJoin", t);
        }
    }

    static void onWorldLoad(World world) {
        try {
            if (active && world.isRemote) {
                world.rand.setSeed(mix(seed, 0x574F524C44L));
            }
        } catch (Throwable t) {
            SubFrameMod.problem("onWorldLoad", t);
        }
    }
}
