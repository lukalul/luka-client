package dev.motionblur;

/** The single settings object read by the renderer, key binding, JSON, and OneConfig bridge. */
public final class MotionBlurSettings {
    /** Deliberately first and volatile: the render hook's fast path is one read and branch. */
    public static volatile boolean enabled;
    /** Blur level, 1 (faint) to 10 (long smear); 0 is off. */
    public static volatile int level = 5;
    public static volatile boolean blurMenus = true;
    public static volatile int frameSkip = 1;
    public static volatile int toggleKey;
    public static final int MODE_PHOSPHOR = 0, MODE_BLEND = 1, MODE_CAPPED = 2;
    /** V1 phosphor, V2 blend, V3 capped blend (see BlurRenderer). */
    public static volatile int mode = MODE_CAPPED;
    /** Between-tick smoothing adapted from framejob (see META-INF/LICENSE.framejob). */
    public static volatile boolean smoothSwings = true;
    public static volatile boolean smoothFov = true;
    public static volatile boolean smoothHandSway = true;
    /** Only the first-person hand and held item are blurred; the world stays sharp (see HandBlur). */
    public static volatile boolean handOnly;
    /** The frame rate at which a level's weight applies unscaled (config file only, 60..1000, see AlphaMath). */
    public static volatile int referenceFps = AlphaMath.DEFAULT_REFERENCE_FPS;

    private MotionBlurSettings() { }

    public static MotionBlurConfig.Data snapshot() {
        MotionBlurConfig.Data data = new MotionBlurConfig.Data();
        data.enabled = enabled;
        data.level = level;
        data.blurMenus = blurMenus;
        data.frameSkip = frameSkip;
        data.toggleKey = toggleKey;
        data.mode = mode;
        data.smoothSwings = smoothSwings;
        data.smoothFov = smoothFov;
        data.smoothHandSway = smoothHandSway;
        data.handOnly = handOnly;
        data.referenceFps = referenceFps;
        data.sanitize();
        return data;
    }

    public static void apply(MotionBlurConfig.Data data) {
        data.sanitize();
        level = data.level;
        blurMenus = data.blurMenus;
        frameSkip = data.frameSkip;
        toggleKey = data.toggleKey;
        mode = data.mode;
        smoothSwings = data.smoothSwings;
        smoothFov = data.smoothFov;
        smoothHandSway = data.smoothHandSway;
        handOnly = data.handOnly;
        referenceFps = data.referenceFps;
        MotionBlur.setEnabled(data.enabled);
    }
}
