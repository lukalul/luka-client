package dev.motionblur;

/** Pure motion-blur weight math, kept independent of Minecraft for executable checks. */
public final class AlphaMath {
    /**
     * The frame rate at which a level's weight is applied as it is, once per frame. The client the owner compares with applies
     * the weights once per frame with no time scaling and is played at about 240 FPS, so 240 matches its look. Other frame
     * rates scale the weight so the fade takes the same real time. config/motionblur.json "referenceFps" (no menu entry).
     */
    public static final int DEFAULT_REFERENCE_FPS = 240;
    public static final int MIN_REFERENCE_FPS = 60;
    public static final int MAX_REFERENCE_FPS = 1000;
    public static final double MAX_EFFECTIVE_ALPHA = 0.999;
    public static final double MAX_DT = 0.25;
    public static final double MIN_DT = 0.0001;

    /** Largest share of the previous frame that V2 and V3 keep. */
    public static final double MAX_WEIGHT_V2 = 0.99;
    public static final double MAX_WEIGHT_V3 = 0.675;

    private AlphaMath() { }

    /**
     * How much of the previous (blurred) frame is kept when one new frame is blended in, for a blur LEVEL from 1 to 10 (0 = off).
     * The weights per frame at the reference frame rate, which {@link #retention} scales to the real frame time:
     * <ul>
     *   <li>V1 phosphor: {@code 0.69 + 0.03 * level} (0.72 at level 1 up to 0.99 at level 10), the history dimmed by it before the brighter pixel wins</li>
     *   <li>V2 blend: {@code level / 10}, never above 0.99</li>
     *   <li>V3 capped blend: {@code 0.95 * level / 10}, never above 0.675</li>
     * </ul>
     */
    public static double frameWeight(int level, int mode) {
        int l = clamp(level, 0, 10);
        if (l == 0) return 0.0;
        switch (mode) {
            case 0: return 0.69 + 0.03 * l;
            case 1: return Math.min(MAX_WEIGHT_V2, l / 10.0);
            default: return Math.min(MAX_WEIGHT_V3, 0.95 * l / 10.0);
        }
    }

    /**
     * {@code weight^(dt * referenceFps)}: the factor kept per blended frame so the fade takes the same real time at any frame
     * rate. At exactly {@code referenceFps} it is the weight itself.
     */
    public static double retention(double weight, double dtSeconds, int referenceFps) {
        double safeWeight = clamp(weight, 0.0, 0.99);
        double safeDt = clamp(dtSeconds, MIN_DT, MAX_DT);
        return Math.min(MAX_EFFECTIVE_ALPHA, Math.pow(safeWeight, safeDt * referenceFps(referenceFps)));
    }

    /** The reference frame rate clamped to 60..1000. */
    public static int referenceFps(int fps) {
        return clamp(fps, MIN_REFERENCE_FPS, MAX_REFERENCE_FPS);
    }

    /** The seconds one blended frame stands for when no real time is known: one frame (or one frame-skip interval) at the reference. */
    public static double referenceDt(int referenceFps, int frameSkip) {
        return clamp(frameSkip, 1, 10) / (double) referenceFps(referenceFps);
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
