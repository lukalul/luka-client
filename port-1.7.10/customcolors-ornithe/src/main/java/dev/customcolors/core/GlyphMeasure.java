package dev.customcolors.core;

import java.util.Properties;

/**
 * Measures the glyph advances of a high-resolution font sheet in vanilla units (one cell = 8 units).
 * The advance of a glyph is the distance from the cell's left edge to the right edge of its last
 * visible column plus one unit of spacing.
 */
public final class GlyphMeasure {
    /** Advance of a cell with no visible pixel. */
    public static final float BLANK = 2.0F;
    /** Upper bound of any advance (stays just inside the cell). */
    public static final float WIDEST = 7.99F;
    /** Space advance when no letter or digit could be measured. */
    public static final float DEFAULT_SPACE = 4.0F;
    /** Pixels with this alpha or less do not count as visible. */
    private static final int FAINT = 16;

    private GlyphMeasure() {
    }

    /**
     * Returns 256 advances; entry 32 is the space advance. {@code overrides} may be null; its
     * {@code width.N} keys (N 0..255, value 0..8) replace single entries.
     */
    public static float[] advances(int[] argb, int imageWidth, int imageHeight, Properties overrides) {
        float[] out = new float[256];
        boolean[] blank = new boolean[256];
        int cellW = Math.max(1, imageWidth / 16);
        int cellH = Math.max(1, imageHeight / 16);
        float unit = 8.0F / cellW;
        for (int cell = 0; cell < 256; cell++) {
            int left = (cell % 16) * cellW;
            int top = (cell / 16) * cellH;
            int last = -1;
            for (int col = cellW - 1; col >= 0 && last < 0; col--) {
                int x = left + col;
                if (x >= imageWidth) {
                    continue;
                }
                for (int row = 0; row < cellH; row++) {
                    int y = top + row;
                    if (y >= imageHeight) {
                        break;
                    }
                    if (((argb[y * imageWidth + x] >>> 24) & 255) > FAINT) {
                        last = col;
                        break;
                    }
                }
            }
            if (last < 0) {
                blank[cell] = true;
                out[cell] = BLANK;
            } else {
                out[cell] = Math.min(WIDEST, (last + 1) * unit + 1.0F);
            }
        }
        float space = spaceFrom(out, blank);
        if (overrides != null) {
            for (String key : overrides.stringPropertyNames()) {
                if (!key.startsWith("width.")) {
                    continue;
                }
                try {
                    int code = Integer.parseInt(key.substring(6).trim());
                    float value = Float.parseFloat(overrides.getProperty(key).trim());
                    if (code >= 0 && code < 256 && value >= 0.0F && value <= 8.0F) {
                        if (code == 32) {
                            space = value;
                        } else {
                            out[code] = value;
                        }
                    }
                } catch (RuntimeException ignored) {
                    // a bad line is skipped
                }
            }
        }
        out[32] = space;
        return out;
    }

    /** About half the average letter/digit advance plus the one-unit gap; vanilla-shaped fonts land near 4. */
    private static float spaceFrom(float[] advances, boolean[] blank) {
        float sum = 0.0F;
        int n = 0;
        for (int c = '0'; c <= 'z'; c++) {
            boolean letterOrDigit = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
            if (letterOrDigit && !blank[c]) {
                sum += advances[c];
                n++;
            }
        }
        if (n == 0) {
            return DEFAULT_SPACE;
        }
        return (sum / n) * 0.5F + 1.0F;
    }
}
