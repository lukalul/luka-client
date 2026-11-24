package dev.subframe;

import java.lang.reflect.Method;

/**
 * Switches the Dynamic FPS mod (if the player has it) off while a replay is open, and back on afterwards. That mod
 * lowers the frame rate when it thinks the game is idle or in the background, which slows a render down and, in the
 * viewer, makes playback stutter. Only its public toggle is used, found by name, so nothing happens without the mod:
 * the 1.8.9 Ornithe port (Polyfrost's "Legacy Dynamic FPS") and the upstream mod.
 */
final class DynamicFps {
    private static final String[] CLASSES = {
            "net.lostluma.dynamic_fps.impl.fabric.DynamicFPSMod",
            "dynamic_fps.impl.DynamicFPSMod",
    };

    /** True while this class switched the mod off and has not switched it back. */
    private static boolean pausedByUs;

    private DynamicFps() {
    }

    /** Switches Dynamic FPS off unless the player already did. */
    static void pause() {
        if (pausedByUs) {
            return;
        }
        Class<?> mod = find();
        if (mod == null) {
            return;
        }
        try {
            if (Boolean.TRUE.equals(mod.getMethod("disabledByUser").invoke(null))) {
                return; // already off: leave it as the player set it
            }
            mod.getMethod("toggleDisabled").invoke(null);
            pausedByUs = true;
            SubFrameMod.LOG.info("[replay] Dynamic FPS switched off while the replay is open");
        } catch (Throwable t) {
            SubFrameMod.LOG.warn("[replay] Could not switch Dynamic FPS off: " + t);
        }
    }

    /** Switches Dynamic FPS back on if {@link #pause} switched it off and it is still off. */
    static void resume() {
        if (!pausedByUs) {
            return;
        }
        pausedByUs = false;
        Class<?> mod = find();
        if (mod == null) {
            return;
        }
        try {
            Method disabled = mod.getMethod("disabledByUser");
            if (Boolean.TRUE.equals(disabled.invoke(null))) {
                mod.getMethod("toggleDisabled").invoke(null);
                SubFrameMod.LOG.info("[replay] Dynamic FPS switched back on");
            }
        } catch (Throwable t) {
            SubFrameMod.LOG.warn("[replay] Could not switch Dynamic FPS back on: " + t);
        }
    }

    private static Class<?> find() {
        for (String name : CLASSES) {
            try {
                return Class.forName(name, false, DynamicFps.class.getClassLoader());
            } catch (Throwable ignored) {
                // not installed under this name
            }
        }
        return null;
    }
}
