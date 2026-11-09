package dev.customcolors;

import java.awt.image.BufferedImage;
import net.minecraft.util.BlockPos;
import net.minecraft.world.biome.BiomeGenBase;

/** A colour lookup: one fixed colour, or a temperature/humidity image. Adapted from MCPatcher's ColorMap (see NOTICE). */
final class ColorMap {
    private final int[] map;
    private final int width, height, fixed;
    private final float maxX, maxY;

    private ColorMap(int[] map, int width, int height, int fixed) {
        this.map = map;
        this.width = width;
        this.height = height;
        this.fixed = fixed;
        this.maxX = width - 1.0F;
        this.maxY = height - 1.0F;
    }

    static ColorMap fixed(int color) {
        return new ColorMap(null, 0, 0, color & 0xFFFFFF);
    }

    static ColorMap image(BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight();
        int[] map = new int[width * height];
        image.getRGB(0, 0, width, height, map, 0, width);
        for (int i = 0; i < map.length; i++) map[i] &= 0xFFFFFF;
        return new ColorMap(map, width, height, 0);
    }

    int sample(BiomeGenBase biome, BlockPos pos) {
        if (map == null) return fixed;
        float temperature = clamp(biome.getFloatTemperature(pos));
        float rainfall = clamp(biome.getFloatRainfall());
        return rgb(maxX * (1.0F - temperature), maxY * (1.0F - temperature * rainfall));
    }

    private int rgb(float x, float y) {
        x = Math.max(0.0F, Math.min(maxX, x));
        y = Math.max(0.0F, Math.min(maxY, y));
        int x0 = (int) x, y0 = (int) y;
        int dx = (int) (256.0F * (x - x0)), dy = (int) (256.0F * (y - y0));
        int x1 = Math.min(x0 + 1, width - 1), y1 = Math.min(y0 + 1, height - 1);
        int top = lerp(map[y0 * width + x0], map[y0 * width + x1], dx);
        int bottom = lerp(map[y1 * width + x0], map[y1 * width + x1], dx);
        return lerp(top, bottom, dy);
    }

    private static int lerp(int a, int b, int amount) {
        if (amount == 0) return a;
        int inverse = 256 - amount;
        int r = ((a >> 16 & 0xFF) * inverse + (b >> 16 & 0xFF) * amount) >> 8;
        int g = ((a >> 8 & 0xFF) * inverse + (b >> 8 & 0xFF) * amount) >> 8;
        int blue = ((a & 0xFF) * inverse + (b & 0xFF) * amount) >> 8;
        return r << 16 | g << 8 | blue;
    }

    /** Every pixel, reading across, for tables of any length. */
    int[] all() {
        return map == null ? null : map.clone();
    }

    /** The first row of an image, used for tables indexed by age or redstone power. */
    int[] row(int entries) {
        if (map == null || width < entries) return null;
        int[] out = new int[entries];
        System.arraycopy(map, 0, out, 0, entries);
        return out;
    }

    static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
