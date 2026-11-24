package dev.customcolors.core;

/**
 * One colour table from a pack: either a single colour, or the pixels of an image (RGB, alpha dropped).
 * Images are read three ways: as a temperature/rainfall map, as a list of entries (row by row) and as
 * the entries of their first row.
 */
public final class Palette {
    private final int[] rgb;
    private final int width;
    private final int height;
    private final boolean solid;

    private Palette(int[] rgb, int width, int height, boolean solid) {
        this.rgb = rgb;
        this.width = width;
        this.height = height;
        this.solid = solid;
    }

    public static Palette solid(int rgb) {
        return new Palette(new int[] {rgb & 0xFFFFFF}, 1, 1, true);
    }

    /** Copies the image pixels; returns null for an empty or inconsistent image. */
    public static Palette image(int[] argb, int width, int height) {
        if (argb == null || width <= 0 || height <= 0 || argb.length < width * height) {
            return null;
        }
        int[] copy = new int[width * height];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = argb[i] & 0xFFFFFF;
        }
        return new Palette(copy, width, height, false);
    }

    public boolean isSolid() {
        return solid;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Number of entries when the image is read as a list. */
    public int size() {
        return rgb.length;
    }

    /** Entry i of the list (pixels row by row), clamped to the list. */
    public int entry(int i) {
        if (i < 0) {
            i = 0;
        } else if (i >= rgb.length) {
            i = rgb.length - 1;
        }
        return rgb[i];
    }

    /**
     * Colour for a biome climate. Temperature grows toward the left edge, rainfall (scaled by temperature)
     * toward the top; the bottom-right corner is temperature 0, rainfall 0. Blended between pixels.
     */
    public int climate(float temperature, float rainfall) {
        if (solid) {
            return rgb[0];
        }
        float t = clamp01(temperature);
        float r = clamp01(rainfall) * t;
        float fx = (1.0F - t) * (width - 1);
        float fy = (1.0F - r) * (height - 1);
        int x0 = (int) fx;
        int y0 = (int) fy;
        int x1 = Math.min(x0 + 1, width - 1);
        int y1 = Math.min(y0 + 1, height - 1);
        float ax = fx - x0;
        float ay = fy - y0;
        int top = mix(rgb[y0 * width + x0], rgb[y0 * width + x1], ax);
        int bottom = mix(rgb[y1 * width + x0], rgb[y1 * width + x1], ax);
        return mix(top, bottom, ay);
    }

    /** A point along the whole list, 0 = first entry, 1 = last, blended between neighbours. */
    public int along(float position) {
        if (rgb.length == 1) {
            return rgb[0];
        }
        float f = clamp01(position) * (rgb.length - 1);
        int i0 = (int) f;
        int i1 = Math.min(i0 + 1, rgb.length - 1);
        return mix(rgb[i0], rgb[i1], f - i0);
    }

    /**
     * The first {@code count} pixels of the first row, or null when the image is narrower than that.
     * A single-colour palette fills every slot with its colour.
     */
    public int[] firstRow(int count) {
        int[] out = new int[count];
        if (solid) {
            java.util.Arrays.fill(out, rgb[0]);
            return out;
        }
        if (width < count) {
            return null;
        }
        System.arraycopy(rgb, 0, out, 0, count);
        return out;
    }

    public static int mix(int a, int b, float t) {
        if (t <= 0.0F) {
            return a;
        }
        if (t >= 1.0F) {
            return b;
        }
        int ar = (a >> 16) & 255;
        int ag = (a >> 8) & 255;
        int ab = a & 255;
        int r = ar + Math.round((((b >> 16) & 255) - ar) * t);
        int g = ag + Math.round((((b >> 8) & 255) - ag) * t);
        int bl = ab + Math.round(((b & 255) - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }
}
