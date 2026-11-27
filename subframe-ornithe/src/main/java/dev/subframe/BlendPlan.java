package dev.subframe;

import java.util.Locale;

/**
 * Frame blending: which subframes each output frame averages, and how much each counts.
 *
 * <p>Subframes are drawn on one even grid of moments, {@code perFrame} per output frame interval. Output frame k averages the
 * {@code n} grid moments that end at its end time, oldest first, with {@link #weights}. {@code n} is "Subframes per frame" and
 * the window spans {@code intensity} output frame intervals (the shutter: 0.5 = half the gap to the next frame, 1 = all of it,
 * 2 = two frames' worth, so neighbouring frames share subframes). The weightings are the ones frame-blending tools offer
 * (same shapes as f0e/blur's: equal, ascending, descending, pyramid, gaussian, gaussian_sym, gaussian_reverse).
 *
 * <p>Pure Java, no Minecraft classes, so it can be checked on its own.
 */
final class BlendPlan {
    static final String[] WEIGHTINGS = {"equal", "ascending", "descending", "pyramid", "gaussian", "gaussian_sym", "gaussian_reverse"};
    static final double MIN_INTENSITY = 0.05, MAX_INTENSITY = 2.0;

    /** Subframes blended into each output frame. */
    final int n;
    /** Grid moments per output frame interval. */
    final int perFrame;
    /** Weight of each of the n subframes, oldest first; they add up to 1. */
    final float[] weights;
    final String weighting;

    BlendPlan(int subframes, double intensity, String weighting) {
        n = Math.max(1, subframes);
        perFrame = Math.max(1, (int) Math.round(n / clampIntensity(intensity)));
        this.weighting = normalizeName(weighting);
        weights = weights(this.weighting, n);
    }

    static double clampIntensity(double intensity) {
        if (Double.isNaN(intensity)) {
            return 0.5;
        }
        return Math.max(MIN_INTENSITY, Math.min(MAX_INTENSITY, intensity));
    }

    /** A known weighting name ("Gaussian sym" and "gaussian-sym" work too); anything else is equal. */
    static String normalizeName(String name) {
        if (name == null) {
            return "equal";
        }
        String s = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (String w : WEIGHTINGS) {
            if (w.equals(s)) {
                return w;
            }
        }
        return "equal";
    }

    /** The name shown in the menu: "Gaussian sym". */
    static String label(String name) {
        String s = normalizeName(name).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String nextWeighting(String name) {
        String s = normalizeName(name);
        for (int i = 0; i < WEIGHTINGS.length; i++) {
            if (WEIGHTINGS[i].equals(s)) {
                return WEIGHTINGS[(i + 1) % WEIGHTINGS.length];
            }
        }
        return WEIGHTINGS[0];
    }

    /** The intensity the grid really gives (n / perFrame), close to the one asked for. */
    double effectiveIntensity() {
        return n / (double) perFrame;
    }

    /** How many output frames one subframe can count in at most: the number of frames being blended at once. */
    int overlap() {
        return (n + perFrame - 1) / perFrame;
    }

    /** Grid index of the oldest subframe of output frame k (may be below 0 for the first frames when intensity is over 1). */
    long first(long k) {
        return (k + 1) * perFrame - n;
    }

    /** Grid index just past the newest subframe of output frame k. */
    long end(long k) {
        return (k + 1) * perFrame;
    }

    /** The moment of grid index j: the middle of its slice, measured from the start of the rendered range. */
    double time(long j, double start, double frameDur) {
        return start + (j + 0.5) * frameDur / perFrame;
    }

    /** What grid index j counts for in output frame k (0 when it is outside that frame's window). */
    float weight(long j, long k) {
        long i = j - first(k);
        return i < 0 || i >= n ? 0f : weights[(int) i];
    }

    /** The weights of n subframes, oldest first, adding up to 1. */
    static float[] weights(String weighting, int n) {
        double[] w = new double[n];
        if (n == 1) {
            return new float[]{1f};
        }
        double half = (n - 1) / 2.0;
        String name = normalizeName(weighting);
        for (int i = 0; i < n; i++) {
            switch (name) {
                case "ascending":
                    w[i] = i + 1;
                    break;
                case "descending":
                    w[i] = n - i;
                    break;
                case "pyramid":
                    w[i] = half - Math.abs(i - half) + 1;
                    break;
                case "gaussian":
                    // x from 0 to 2, bell centred on 2 (std 1): the newest subframe counts most
                    w[i] = gauss(2.0 * i / (n - 1), 2.0);
                    break;
                case "gaussian_reverse":
                    w[i] = gauss(2.0 * (n - 1 - i) / (n - 1), 2.0);
                    break;
                case "gaussian_sym":
                    // x from -2 to 2, bell centred on 0: the middle subframe counts most
                    w[i] = gauss(-2.0 + 4.0 * i / (n - 1), 0.0);
                    break;
                default:
                    w[i] = 1;
                    break;
            }
        }
        double sum = 0;
        for (double v : w) {
            sum += v;
        }
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            out[i] = (float) (w[i] / sum);
        }
        return out;
    }

    private static double gauss(double x, double mean) {
        return Math.exp(-((x - mean) * (x - mean)) / 2.0);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%d subframes, intensity %.2f, %s weighting (%d per frame interval, %d frame(s) blended at once)",
                n, effectiveIntensity(), weighting, perFrame, overlap());
    }
}
