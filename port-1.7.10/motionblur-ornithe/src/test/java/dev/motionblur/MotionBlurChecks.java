package dev.motionblur;

import java.io.File;
import java.nio.file.Files;

/** Executable checks that intentionally need neither Minecraft nor an OpenGL context. */
public final class MotionBlurChecks {
    private MotionBlurChecks() { }

    public static void main(String[] args) throws Exception {
        checkLevelWeights();
        checkFrameRateIndependentMode();
        checkClamping();
        checkStateMachine();
        checkConfigRoundTrip();
        System.out.println("MotionBlur plain-Java checks: ALL PASSED");
    }

    /** The weights per frame at the reference frame rate, level by level. */
    private static void checkLevelWeights() {
        for (int level = 1; level <= 10; level++) {
            require(Math.abs(AlphaMath.frameWeight(level, 1) - Math.min(0.99, level / 10.0)) < 1e-12, "V2 weight at level " + level);
            require(Math.abs(AlphaMath.frameWeight(level, 2) - Math.min(0.675, 0.95 * level / 10.0)) < 1e-12, "V3 weight at level " + level);
            double v1 = AlphaMath.frameWeight(level, 0);
            require(Math.abs(v1 - (0.69 + 0.03 * level)) < 1e-12, "V1 weight at level " + level);
        }
        require(AlphaMath.frameWeight(0, 1) == 0.0 && AlphaMath.frameWeight(0, 2) == 0.0, "level 0 is off");
        require(Math.abs(AlphaMath.frameWeight(5, 1) - 0.5) < 1e-12, "level 5 in V2 keeps half the previous frame");
        require(Math.abs(AlphaMath.frameWeight(5, 2) - 0.475) < 1e-12, "level 5 in V3 keeps 0.475");
        require(AlphaMath.frameWeight(10, 2) == AlphaMath.MAX_WEIGHT_V3 && AlphaMath.frameWeight(8, 2) == AlphaMath.MAX_WEIGHT_V3, "V3 cap");
        require(AlphaMath.frameWeight(10, 1) == AlphaMath.MAX_WEIGHT_V2, "V2 cap");
        for (int mode = 0; mode < 3; mode++)
            for (int level = 1; level < 10; level++)
                require(AlphaMath.frameWeight(level, mode) <= AlphaMath.frameWeight(level + 1, mode) + 1e-12, "weight never falls as the level rises (mode " + mode + ")");
        System.out.println("weights: V1/V2/V3 formulas for levels 1-10");
    }

    /** The history fades at the same real-time speed at every frame rate; at the reference frame rate the weight applies as it is. */
    private static void checkFrameRateIndependentMode() {
        int ref = AlphaMath.DEFAULT_REFERENCE_FPS;
        require(ref == 240, "the reference is 240 FPS");
        for (int mode = 0; mode < 3; mode++) {
            for (int level = 1; level <= 10; level++) {
                double w = AlphaMath.frameWeight(level, mode);
                require(Math.abs(AlphaMath.retention(w, 1.0 / 240, ref) - w) < 1e-12, "240 FPS keeps the weight (mode " + mode + ", level " + level + ")");
                require(Math.abs(AlphaMath.retention(w, 1.0 / 120, ref) - w * w) < 1e-12, "120 FPS keeps the weight squared (mode " + mode + ", level " + level + ")");
                require(Math.abs(AlphaMath.retention(w, 1.0 / 480, ref) - Math.sqrt(w)) < 1e-12, "480 FPS keeps the square root (mode " + mode + ", level " + level + ")");
            }
            double base = AlphaMath.frameWeight(6, mode);
            double expected = Math.pow(base, 0.25 * ref);
            for (int fps : new int[] {30, 60, 144, 240, 500, 1000, 2000}) {
                int frames = (int) Math.round(0.25 * fps);
                double dt = 0.25 / frames, kept = 1.0;
                for (int i = 0; i < frames; i++) kept *= AlphaMath.retention(base, dt, ref);
                require(Math.abs(kept - expected) < 1e-9, "mode " + mode + " fade drift at " + fps + " FPS");
            }
        }
        // the decay the rig measures through the real pass at 240 FPS (percent of white after 1, 2, 3 black frames)
        require(close(Math.pow(AlphaMath.frameWeight(5, 2), 1), 0.475) && close(Math.pow(AlphaMath.frameWeight(5, 2), 3), 0.1072), "V3 level 5 decay");
        require(close(Math.pow(AlphaMath.frameWeight(10, 2), 2), 0.4556), "V3 level 10 decay");
        require(close(Math.pow(AlphaMath.frameWeight(5, 1), 3), 0.125), "V2 level 5 decay");
        require(close(AlphaMath.frameWeight(5, 0), 0.84) && close(Math.pow(AlphaMath.frameWeight(5, 0), 2), 0.7056) && close(Math.pow(AlphaMath.frameWeight(5, 0), 3), 0.5927), "V1 level 5 decay");
        require(close(AlphaMath.retention(AlphaMath.frameWeight(5, 2), 1.0 / 120, ref), 0.2256), "V3 level 5 at 120 FPS");
        // another reference frame rate, clamped to 60..1000
        require(Math.abs(AlphaMath.retention(0.5, 1.0 / 60, 60) - 0.5) < 1e-12, "a 60 FPS reference keeps the weight at 60 FPS");
        require(AlphaMath.referenceFps(10) == 60 && AlphaMath.referenceFps(5000) == 1000 && AlphaMath.referenceFps(240) == 240, "reference clamp");
        require(Math.abs(AlphaMath.retention(0.5, 1.0 / 240, 5000) - AlphaMath.retention(0.5, 1.0 / 240, 1000)) < 1e-12, "reference clamped in retention");
        // the first blend and frame skip: one frame (or N frames) at the reference
        require(Math.abs(AlphaMath.referenceDt(240, 1) - 1.0 / 240) < 1e-15 && Math.abs(AlphaMath.referenceDt(240, 3) - 3.0 / 240) < 1e-15, "reference dt");
        require(AlphaMath.retention(0.9, 1.0 / 1000, ref) > 0.97, "at 1000 FPS a strong blur keeps almost all of the previous frame");
        System.out.println("time-scaled mode: weight as is at 240 FPS, squared at 120, square root at 480; same real-time fade at 30-2000 FPS");
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) < 5e-4;
    }

    private static void checkClamping() {
        int ref = AlphaMath.DEFAULT_REFERENCE_FPS;
        require(AlphaMath.retention(2.0, 99.0, ref) < 1.0, "weight must never reach one");
        require(AlphaMath.retention(Double.NaN, 1.0, ref) == 0.0, "NaN weight clamp");
        require(AlphaMath.retention(0.5, -1.0, ref) == AlphaMath.retention(0.5, AlphaMath.MIN_DT, ref), "negative dt clamp");
        double capped = AlphaMath.retention(0.5, AlphaMath.MAX_DT, ref);
        require(AlphaMath.retention(0.5, 100.0, ref) == capped, "dt upper clamp");
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
            require(fresh.level == 5, "a new config starts at level 5");
            require(fresh.referenceFps == 240, "a new config uses the 240 FPS reference");
            MotionBlurConfig.Data written = new MotionBlurConfig.Data();
            written.enabled = true;
            written.level = 7;
            written.blurMenus = true;
            written.frameSkip = 4;
            written.toggleKey = 48;
            MotionBlurConfig.save(file, written);
            MotionBlurConfig.Data read = MotionBlurConfig.load(file);
            require(read.enabled == written.enabled, "config enabled");
            require(read.level == written.level, "config level");
            require(read.blurMenus == written.blurMenus, "config menus");
            require(read.frameSkip == written.frameSkip, "config frame skip");
            require(read.toggleKey == written.toggleKey, "config key");
            require(read.referenceFps == 240, "config reference FPS");
            written.referenceFps = 144;
            MotionBlurConfig.save(file, written);
            require(MotionBlurConfig.load(file).referenceFps == 144, "config reference FPS round trip");
            // a config saved before this change: no referenceFps key, its own level
            Files.write(file.toPath(), "{ \"enabled\": true, \"level\": 3, \"mode\": 2 }".getBytes("UTF-8"));
            MotionBlurConfig.Data old = MotionBlurConfig.load(file);
            require(old.level == 3 && old.referenceFps == 240, "an existing config keeps its level and gets the 240 FPS reference");
            Files.write(file.toPath(), "{ \"referenceFps\": 20 }".getBytes("UTF-8"));
            require(MotionBlurConfig.load(file).referenceFps == 60, "reference FPS clamped to 60");
            Files.write(file.toPath(), "{ \"referenceFps\": 4000 }".getBytes("UTF-8"));
            require(MotionBlurConfig.load(file).referenceFps == 1000, "reference FPS clamped to 1000");
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
