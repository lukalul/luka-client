package dev.customcolors;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

/** Custom lightmaps ({@code lightmap/world<N>.png}). Adapted from MCPatcher's Lightmap (see NOTICE). */
final class Lightmaps {
    private static final int SIZE = 16;
    private static final Map<Integer, Lightmaps> LOADED = new HashMap<>();

    private final int width;
    private final boolean customNightVision;
    private final int[] map;
    private final float[] sun = new float[3 * SIZE], torch = new float[3 * SIZE];
    private final float[] sunNight = new float[3 * SIZE], torchNight = new float[3 * SIZE];
    private final float[] rgb = new float[3];

    private Lightmaps(BufferedImage image) {
        width = image.getWidth();
        customNightVision = image.getHeight() == 4 * SIZE;
        map = new int[width * image.getHeight()];
        image.getRGB(0, 0, width, image.getHeight(), map, 0, width);
    }

    static void clear() {
        LOADED.clear();
    }

    /** Returns false when the image has the wrong height. */
    static boolean put(int world, BufferedImage image) {
        if (image.getHeight() != 2 * SIZE && image.getHeight() != 4 * SIZE) return false;
        LOADED.put(world, new Lightmaps(image));
        return true;
    }

    static boolean compute(World world, int[] output, float torchFlicker, float nightVision) {
        if (world == null || LOADED.isEmpty()) return false;
        Lightmaps lightmap = LOADED.get(world.provider.getDimensionId());
        if (lightmap == null) return false;
        lightmap.fill(world, output, torchFlicker, nightVision);
        return true;
    }

    private void fill(World world, int[] output, float torchFlicker, float nightVision) {
        float sunIndex = ColorMap.clamp(world.getLastLightningBolt() > 0 ? 1.0F : 7.0F / 6.0F * (world.getSunBrightness(1.0F) - 0.2F)) * (width - 1);
        float torchIndex = ColorMap.clamp(torchFlicker + 0.5F) * (width - 1);
        float gamma = ColorMap.clamp(Minecraft.getMinecraft().gameSettings.gammaSetting);
        for (int i = 0; i < SIZE; i++) {
            interpolate(i * width, sunIndex, sun, 3 * i);
            interpolate((i + SIZE) * width, torchIndex, torch, 3 * i);
            if (customNightVision && nightVision > 0.0F) {
                interpolate((i + 2 * SIZE) * width, sunIndex, sunNight, 3 * i);
                interpolate((i + 3 * SIZE) * width, torchIndex, torchNight, 3 * i);
            }
        }
        for (int s = 0; s < SIZE; s++) {
            for (int t = 0; t < SIZE; t++) {
                for (int k = 0; k < 3; k++) rgb[k] = ColorMap.clamp(sun[3 * s + k] + torch[3 * t + k]);
                if (nightVision > 0.0F) {
                    if (customNightVision) {
                        for (int k = 0; k < 3; k++) {
                            rgb[k] = ColorMap.clamp((1.0F - nightVision) * rgb[k] + nightVision * (sunNight[3 * s + k] + torchNight[3 * t + k]));
                        }
                    } else {
                        float largest = Math.max(Math.max(rgb[0], rgb[1]), rgb[2]);
                        if (largest > 0.0F) {
                            float multiplier = (1.0F - nightVision) + nightVision / largest;
                            for (int k = 0; k < 3; k++) rgb[k] = ColorMap.clamp(rgb[k] * multiplier);
                        }
                    }
                }
                if (gamma != 0.0F) {
                    for (int k = 0; k < 3; k++) {
                        float inverse = 1.0F - rgb[k];
                        rgb[k] = gamma * (1.0F - inverse * inverse * inverse * inverse) + (1.0F - gamma) * rgb[k];
                    }
                }
                output[s * SIZE + t] = 0xFF000000 | (int) (rgb[0] * 255.0F) << 16 | (int) (rgb[1] * 255.0F) << 8 | (int) (rgb[2] * 255.0F);
            }
        }
    }

    private void interpolate(int row, float x, float[] out, int at) {
        int x0 = (int) Math.floor(x), x1 = (int) Math.ceil(x);
        float f = x - x0, g = 1.0F - f;
        int a = map[row + x0], b = map[row + x1];
        for (int k = 0; k < 3; k++) {
            int shift = 16 - 8 * k;
            out[at + k] = (g * (a >> shift & 0xFF) + f * (b >> shift & 0xFF)) / 255.0F;
        }
    }
}
