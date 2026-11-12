package dev.cbui.feature.rawinput;

import dev.cbui.feature.FeatureModule;

/**
 * Raw input. luka's window (pylon, SDL3) turns the camera with SDL's relative mouse mode, which on Windows reads raw input
 * on its own thread with fractional movement: high polling rate mice (1000 to 8000 Hz) lose no movement and do not cost
 * frame rate, unlike the old LWJGL 2 mouse code. On: the mouse moves the camera exactly as the device reports it.
 * Off: Windows' pointer speed and "Enhance pointer precision" (mouse acceleration) are applied first, through SDL's
 * relative system scale.
 */
public final class RawInputModule extends FeatureModule {
  private static volatile RawInputModule instance;
  /** What SDL was last told: true raw, false with Windows pointer settings (null before the first frame). Public for checks. */
  public static volatile Boolean applied;
  private static boolean unavailable;

  public RawInputModule() {
    super("Raw Input", true);
    instance = this;
  }

  /** Once per frame: hands the setting to SDL when it changed. */
  public static void sync() {
    if (unavailable) return;
    RawInputModule m = instance;
    boolean raw = m == null || m.enabled();
    if (applied != null && applied == raw) return;
    try {
      Class<?> hints = Class.forName("org.lwjgl.sdl.SDLHints");
      String name = (String) hints.getField("SDL_HINT_MOUSE_RELATIVE_SYSTEM_SCALE").get(null);
      hints.getMethod("SDL_SetHint", CharSequence.class, CharSequence.class).invoke(null, name, raw ? "0" : "1");
      applied = raw;
    } catch (Throwable notSdl) {
      unavailable = true; // not running on pylon's SDL window
    }
  }
}
