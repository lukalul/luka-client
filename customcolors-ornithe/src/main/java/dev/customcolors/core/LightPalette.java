package dev.customcolors.core;

/**
 * A pack light map image: 16 rows of sky light and 16 rows of block light (plus, in a 64-row image,
 * the same two halves for night vision). Turns the image into vanilla's 16x16 light table.
 * Not thread safe: only the render thread fills tables.
 */
public final class LightPalette {
    private final int[] rgb;
    private final int width;
    private final int height;
    private final float[] sky = new float[48];
    private final float[] torch = new float[48];
    private final float[] nvSky = new float[48];
    private final float[] nvTorch = new float[48];

    private LightPalette(int[] rgb, int width, int height) {
        this.rgb = rgb;
        this.width = width;
        this.height = height;
    }

    /** Null unless the image is 32 or 64 pixels high and at least one pixel wide. */
    public static LightPalette of(int[] argb, int width, int height) {
        if (argb == null || width < 1 || (height != 32 && height != 64) || argb.length < width * height) {
            return null;
        }
        int[] copy = new int[width * height];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = argb[i] & 0xFFFFFF;
        }
        return new LightPalette(copy, width, height);
    }

    public static boolean validHeight(int height) {
        return height == 32 || height == 64;
    }

    public int width() {
        return width;
    }

    public boolean hasNightVisionRows() {
        return height == 64;
    }

    /**
     * Fills {@code out} (256 ARGB entries, index = sky level * 16 + block level).
     *
     * @param daylight    0 = deepest night (left edge) .. 1 = full day; ignored during a lightning flash
     * @param lightning   true while a lightning flash lights the sky (uses the far-right column)
     * @param flicker     0..1 position of the torch column (0.5 = steady)
     * @param nightVision vanilla night-vision strength 0..1
     * @param gamma       vanilla brightness setting 0 (Moody) .. 1 (Bright)
     */
    public void fill(int[] out, float daylight, boolean lightning, float flicker, float nightVision, float gamma) {
        float skyX;
        if (lightning || width == 1) {
            skyX = width - 1;
        } else {
            skyX = Palette.clamp01(daylight) * (width - 2);
        }
        float torchX = Palette.clamp01(flicker) * (width - 1);
        boolean nvRows = nightVision > 0.0F && height == 64;
        for (int level = 0; level < 16; level++) {
            sample(level, skyX, sky, level * 3);
            sample(16 + level, torchX, torch, level * 3);
            if (nvRows) {
                sample(32 + level, skyX, nvSky, level * 3);
                sample(48 + level, torchX, nvTorch, level * 3);
            }
        }
        for (int s = 0; s < 16; s++) {
            for (int t = 0; t < 16; t++) {
                float r = Math.min(1.0F, sky[s * 3] + torch[t * 3]);
                float g = Math.min(1.0F, sky[s * 3 + 1] + torch[t * 3 + 1]);
                float b = Math.min(1.0F, sky[s * 3 + 2] + torch[t * 3 + 2]);
                if (nightVision > 0.0F) {
                    if (nvRows) {
                        float nr = Math.min(1.0F, nvSky[s * 3] + nvTorch[t * 3]);
                        float ng = Math.min(1.0F, nvSky[s * 3 + 1] + nvTorch[t * 3 + 1]);
                        float nb = Math.min(1.0F, nvSky[s * 3 + 2] + nvTorch[t * 3 + 2]);
                        r += (nr - r) * nightVision;
                        g += (ng - g) * nightVision;
                        b += (nb - b) * nightVision;
                    } else {
                        float top = Math.max(r, Math.max(g, b));
                        if (top > 0.0F) {
                            float k = 1.0F / top;
                            r += (r * k - r) * nightVision;
                            g += (g * k - g) * nightVision;
                            b += (b * k - b) * nightVision;
                        }
                    }
                }
                if (gamma > 0.0F) {
                    r = brighten(r, gamma);
                    g = brighten(g, gamma);
                    b = brighten(b, gamma);
                }
                out[s * 16 + t] = 0xFF000000 | (channel(r) << 16) | (channel(g) << 8) | channel(b);
            }
        }
    }

    private void sample(int row, float x, float[] dst, int at) {
        int x0 = (int) x;
        int x1 = Math.min(x0 + 1, width - 1);
        int c = Palette.mix(rgb[row * width + x0], rgb[row * width + x1], x - x0);
        dst[at] = ((c >> 16) & 255) / 255.0F;
        dst[at + 1] = ((c >> 8) & 255) / 255.0F;
        dst[at + 2] = (c & 255) / 255.0F;
    }

    /** Vanilla's brightness curve: lifts dark values strongly, keeps full ones full. */
    private static float brighten(float v, float gamma) {
        float inv = 1.0F - Palette.clamp01(v);
        float lifted = 1.0F - inv * inv * inv * inv;
        return v * (1.0F - gamma) + lifted * gamma;
    }

    private static int channel(float v) {
        int c = (int) (Palette.clamp01(v) * 255.0F + 0.5F);
        return c > 255 ? 255 : c;
    }
}
