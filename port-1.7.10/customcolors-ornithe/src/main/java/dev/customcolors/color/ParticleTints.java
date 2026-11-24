package dev.customcolors.color;

import dev.customcolors.core.Palette;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/** Particle and experience-orb colours from pack colour lists. -1 means "keep vanilla". */
public final class ParticleTints {
    private ParticleTints() {
    }

    /** Water colormap at a particle's position (rain splashes, water drips). */
    public static int water(World world, double x, double y, double z) {
        Palette p = ColorLoader.current().water;
        if (p == null || world == null) {
            return -1;
        }
        return BlockTints.at(world, MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z), p);
    }

    /** Lava drip colour for a drip that is {@code age} ticks old. */
    public static int lavaDrop(int age) {
        Palette p = ColorLoader.current().lavaDrop;
        return p == null ? -1 : p.entry(age);
    }

    /** A random entry of the mycelium list. */
    public static int mycelium() {
        Palette p = ColorLoader.current().mycelium;
        return p == null ? -1 : p.entry(ThreadLocalRandom.current().nextInt(p.size()));
    }

    /**
     * Experience orb colour: sweeps from the first entry to the last and back, one full cycle in the
     * time of vanilla's own orb pulse (about 628 ms).
     */
    public static int xpOrb(int orbTimer, float partialTicks) {
        Palette p = ColorLoader.current().xpOrb;
        if (p == null) {
            return -1;
        }
        float phase = (orbTimer + partialTicks) / 2.0F;
        return p.along((MathHelper.sin(phase) + 1.0F) * 0.5F);
    }

    public static void paint(EntityFX fx, int rgb) {
        fx.setRBGColorF(((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F);
    }
}
