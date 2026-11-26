package dev.cbui.feature.profiler;

import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;
import net.minecraft.client.settings.KeyBinding;

/**
 * Records what the game spends its time on while you play, and saves a text report. Bind the key on this card (it has no default
 * key); press it to start recording and again to stop and save. The card is in the mods list (it used to sit on Settings >
 * Performance); the key row edits the "Toggle FPS profiler" key that luka registers at start.
 */
public final class FpsProfilerModule extends FeatureModule {
  public final Setting.Toggle showIndicator = add(new Setting.Toggle(
      "Show recording message", true,
      "While recording, a red PROFILING message is shown at the top of the screen. The profiler measures frame times, GPU time per phase "
          + "(world, entities, particles, GUI) and which mod the game thread is busy in, and saves a report to config/luka-profile-<time>.txt"));

  private final Setting.Key keyRow = add(new Setting.Key(
      "Profiler key", 0, "Click, then press the key that starts and stops recording; Escape clears it"));

  public FpsProfilerModule() {
    super("FPS Profiler", true);
    keyRow.bind(new Setting.Key.Binding() {
      @Override public int get() {
        KeyBinding key = key();
        return key == null ? 0 : key.getKeyCode();
      }

      @Override public void set(int code) {
        KeyBinding key = key();
        if (key == null || key.getKeyCode() == code) return;
        key.setKeyCode(code);
        KeyBinding.resetKeyBindingArrayAndHash();
        if (mc.gameSettings != null) mc.gameSettings.saveOptions();
      }
    });
  }

  /** The profiler key luka adds to the game's keys on the first tick, or null before that. */
  private KeyBinding key() {
    if (mc.gameSettings == null || mc.gameSettings.keyBindings == null) return null;
    for (KeyBinding key : mc.gameSettings.keyBindings)
      if ("Toggle FPS profiler".equals(key.getKeyDescription())) return key;
    return null;
  }

  @Override public String category() { return "UTILITY"; }
}
