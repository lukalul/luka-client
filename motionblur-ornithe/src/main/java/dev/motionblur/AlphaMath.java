package dev.motionblur;

/** Pure motion-blur weight math, kept independent of Minecraft for executable checks. */
public final class AlphaMath {
    public static final double REFERENCE_DT = 1.0 / 60.0;
    public static final double MAX_EFFECTIVE_ALPHA = 0.999;
    public static final double MAX_DT = 0.25;
    public static final double MIN_DT = 0.0001;

    /** Largest share of the previous frame that V2 and V3 keep. */
    public static final double MAX_WEIGHT_V2 = 0.99;
    public static final double MAX_WEIGHT_V3 = 0.675;

    private AlphaMath() { }

    /**
     * How much of the previous (blurred) frame is kept when one new frame is blended in, for a blur LEVEL from 1 to 10 (0 = off).
     * These are the same per-frame weights Lunar uses:
     * <ul>
     *   <li>V1 phosphor: the history is dimmed by 0.69 (level 1) up to 0.99 (level 10) before the brighter pixel wins</li>
     *   <li>V2 blend: {@code level / 10}, never above 0.99</li>
     *   <li>V3 capped blend: {@code 0.95 * level / 10}, never above 0.675</li>
     * </ul>
     * The weight is applied once per frame, so the blur is subtler the higher the frame rate (a trail of the same weight lasts
     * fewer milliseconds at 300 FPS than at 60). {@link #keep} can scale it to frame time instead.
     */
    public static double frameWeight(int level, int mode) {
        int l = clamp(level, 0, 10);
        if (l == 0) return 0.0;
        switch (mode) {
            case 0: return 0.69 + 0.30 * (l - 1) / 9.0;
            case 1: return Math.min(MAX_WEIGHT_V2, l / 10.0);
            default: return Math.min(MAX_WEIGHT_V3, 0.95 * l / 10.0);
        }
    }

    /**
     * The share of the previous frame to keep for this blend. By default (Lunar's way) that is just the per-frame weight;
     * with {@code frameRateIndependent} it is raised to {@code dt / (1/60)}, so the trail lasts the same real time at any FPS.
     */
    public static double keep(double weight, double dtSeconds, boolean frameRateIndependent) {
        if (frameRateIndependent) return retention(weight, dtSeconds);
        return clamp(weight, 0.0, MAX_EFFECTIVE_ALPHA);
    }

    /** {@code weight^(dt / (1/60))}: the factor kept per frame so the fade takes the same real time at any frame rate. */
    public static double retention(double weight, double dtSeconds) {
        double safeWeight = clamp(weight, 0.0, 0.99);
        double safeDt = clamp(dtSeconds, MIN_DT, MAX_DT);
        return Math.min(MAX_EFFECTIVE_ALPHA, Math.pow(safeWeight, safeDt / REFERENCE_DT));
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
