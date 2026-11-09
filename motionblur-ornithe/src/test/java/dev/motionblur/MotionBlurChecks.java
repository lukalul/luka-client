package dev.motionblur;

import java.io.File;
import java.nio.file.Files;

/** Executable checks that intentionally need neither Minecraft nor an OpenGL context. */
public final class MotionBlurChecks {
    private MotionBlurChecks() { }

    public static void main(String[] args) throws Exception {
        checkLunarWeights();
        checkPerFrameIsRateDependent();
        checkFrameRateIndependentMode();
        checkClamping();
        checkStateMachine();
        checkConfigRoundTrip();
        System.out.println("MotionBlur plain-Java checks: ALL PASSED");
    }

    /** The per-frame weights Lunar uses, level by level. */
    private static void checkLunarWeights() {
        for (int level = 1; level <= 10; level++) {
            require(Math.abs(AlphaMath.frameWeight(level, 1) - Math.min(0.99, level / 10.0)) < 1e-12, "V2 weight at level " + level);
            require(Math.abs(AlphaMath.frameWeight(level, 2) - Math.min(0.675, 0.95 * level / 10.0)) < 1e-12, "V3 weight at level " + level);
            double v1 = AlphaMath.frameWeight(level, 0);
            require(v1 >= 0.69 - 1e-12 && v1 <= 0.99 + 1e-12, "V1 factor range at level " + level);
        }
        require(AlphaMath.frameWeight(0, 1) == 0.0 && AlphaMath.frameWeight(0, 2) == 0.0, "level 0 is off");
        require(Math.abs(AlphaMath.frameWeight(5, 1) - 0.5) < 1e-12, "Lunar default level 5 in V2 keeps half the previous frame");
        require(Math.abs(AlphaMath.frameWeight(5, 2) - 0.475) < 1e-12, "Lunar default level 5 in V3 keeps 0.475");
        require(AlphaMath.frameWeight(10, 2) == AlphaMath.MAX_WEIGHT_V3 && AlphaMath.frameWeight(8, 2) == AlphaMath.MAX_WEIGHT_V3, "V3 cap");
        require(AlphaMath.frameWeight(10, 1) == AlphaMath.MAX_WEIGHT_V2, "V2 cap");
        for (int mode = 0; mode < 3; mode++)
            for (int level = 1; level < 10; level++)
                require(AlphaMath.frameWeight(level, mode) <= AlphaMath.frameWeight(level + 1, mode) + 1e-12, "weight never falls as the level rises (mode " + mode + ")");
        System.out.println("weights: V1/V2/V3 match Lunar's per-frame formulas for levels 1-10");
    }

    /** Default behaviour: the weight per frame does not depend on the frame time, so the trail gets shorter in real time as FPS rises. */
    private static void checkPerFrameIsRateDependent() {
        double weight = AlphaMath.frameWeight(3, 2); // V3 level 3 = 0.285
        double previous = Double.MAX_VALUE;
        for (int fps : new int[] {30, 60, 144, 360, 1000}) {
            double dt = 1.0 / fps;
            double keep = AlphaMath.keep(weight, dt, false);
            require(Math.abs(keep - weight) < 1e-12, "per-frame weight must not depend on dt (" + fps + " FPS)");
            // the trail's real-time time constant: -dt / ln(weight)
            double tauMs = -dt / Math.log(weight) * 1000.0;
            require(tauMs < previous, "the real-time trail must shrink as FPS rises");
            previous = tauMs;
            System.out.printf("per-frame (Lunar) V3 level 3 at %4d FPS: keep=%.3f trail time constant=%.1f ms%n", fps, keep, tauMs);
        }
        // the reason for the change: at 240 FPS the trail must stay well under one 60 FPS frame
        double tau240 = -(1.0 / 240) / Math.log(AlphaMath.frameWeight(3, 2)) * 1000.0;
        require(tau240 < 5.0, "V3 level 3 at 240 FPS must be crisp (time constant " + tau240 + " ms)");
    }

    /** Opt-in behaviour: the history fades at the same real-time speed at every frame rate. */
    private static void checkFrameRateIndependentMode() {
        for (int mode = 0; mode < 3; mode++) {
            double base = AlphaMath.frameWeight(6, mode);
            double expected = Math.pow(base, 0.25 / AlphaMath.REFERENCE_DT);
            for (int fps : new int[] {30, 60, 144, 500, 1000, 2000}) {
                int frames = (int) Math.round(0.25 * fps);
                double dt = 0.25 / frames, kept = 1.0;
                for (int i = 0; i < frames; i++) kept *= AlphaMath.keep(base, dt, true);
                require(Math.abs(kept - expected) < 1e-9, "mode " + mode + " fade drift at " + fps + " FPS");
            }
            require(Math.abs(AlphaMath.keep(base, AlphaMath.REFERENCE_DT, true) - base) < 1e-12, "at 60 FPS both modes agree");
        }
        require(AlphaMath.keep(0.9, 1.0 / 1000, true) > 0.98, "at 1000 FPS a strong time-scaled blur keeps almost all of the previous frame");
        System.out.println("time-scaled mode: same real-time fade at 30-2000 FPS for V1/V2/V3, equal to per-frame at 60 FPS");
    }

    private static void checkClamping() {
        require(AlphaMath.retention(2.0, 99.0) < 1.0, "weight must never reach one");
        require(AlphaMath.retention(Double.NaN, 1.0) == 0.0, "NaN weight clamp");
        require(AlphaMath.retention(0.5, -1.0) == AlphaMath.retention(0.5, AlphaMath.MIN_DT), "negative dt clamp");
        double capped = AlphaMath.retention(0.5, AlphaMath.MAX_DT);
        require(AlphaMath.retention(0.5, 100.0) == capped, "dt upper clamp");
        require(AlphaMath.keep(5.0, 0.01, false) <= AlphaMath.MAX_EFFECTIVE_ALPHA, "per-frame weight is capped below one");
        require(AlphaMath.keep(Double.NaN, 0.01, false) == 0.0, "per-frame NaN clamp");
        System.out.printf("clamps: maxDt=%.2fs maxEffective=%.3f%n", AlphaMath.MAX_DT, AlphaMath.MAX_EFFECTIVE_ALPHA);
    }

    private static void checkStateMachine() {
        BlurState state = new BlurState();
        Object world = new Object();
        require(state.next(800, 600, world, true, 1) == BlurState.Action.OFF, "off state");
        state.setEnabled(true);
        require(state.next(800, 600, world, true, 1) == BlurState.Action.CAPTURE, "enable clears history");
        require(state.next(800, 600, world, true, 1) == BlurState.Action.BLEND, "normal blend");
        require(state.next(1024, 768, world, true, 1) == BlurState.Action.CAPTURE, "resize capture");
        require(state.next(1024, 768, new Object(), true, 1) == BlurState.Action.CAPTURE, "world change capture");
        require(state.next(1024, 768, world, false, 1) == BlurState.Action.SKIP, "menu skip");
        require(!state.hasHistory(), "menu skip clears history");
        require(state.next(1024, 768, world, true, 3) == BlurState.Action.CAPTURE, "resume capture");
        require(state.next(1024, 768, world, true, 3) == BlurState.Action.SKIP, "frame skip 2");
        require(state.next(1024, 768, world, true, 3) == BlurState.Action.BLEND, "frame skip 3");
        state.setEnabled(false);
        require(state.next(1024, 768, world, true, 1) == BlurState.Action.OFF, "switch off");
        System.out.println("state: off/on/history/menu/world-change/resize/frame-skip passed");
    }

    private static void checkConfigRoundTrip() throws Exception {
        File directory = Files.createTempDirectory("motionblur-check-").toFile();
        File file = new File(directory, "motionblur.json");
        try {
            MotionBlurConfig.Data fresh = MotionBlurConfig.load(file);
            require(!fresh.frameRateIndependent, "a config without the key defaults to Lunar-style per-frame blur");
            MotionBlurConfig.Data written = new MotionBlurConfig.Data();
            written.enabled = true;
            written.level = 7;
            written.blurMenus = true;
            written.frameSkip = 4;
            written.toggleKey = 48;
            written.frameRateIndependent = true;
            MotionBlurConfig.save(file, written);
            MotionBlurConfig.Data read = MotionBlurConfig.load(file);
            require(read.enabled == written.enabled, "config enabled");
            require(read.level == written.level, "config level");
            require(read.blurMenus == written.blurMenus, "config menus");
            require(read.frameSkip == written.frameSkip, "config frame skip");
            require(read.toggleKey == written.toggleKey, "config key");
            require(read.frameRateIndependent, "config frame-rate-independent flag");
            System.out.println("config: JSON round trip passed");
        } finally {
            if (file.exists() && !file.delete()) file.deleteOnExit();
            if (!directory.delete()) directory.deleteOnExit();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
