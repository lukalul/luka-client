package dev.customcolors;

import net.minecraft.world.World;

/** Public entry point for the mixin; Lightmaps itself stays package-private. */
public final class LightmapBridge {
    private LightmapBridge() {
    }

    public static boolean compute(World world, int[] output, float torchFlicker, float nightVision) {
        return Lightmaps.compute(world, output, torchFlicker, nightVision);
    }
}
