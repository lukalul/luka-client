package dev.cbui.feature.profiler;

import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;

/**
 * Records what the game spends its time on while you play, and saves a text report. Bind the key on this card (it has no default
 * key); press it to start recording and again to stop and save.
 */
public final class FpsProfilerModule extends FeatureModule {
  public final Setting.Toggle showIndicator = add(new Setting.Toggle(
      "Show recording message", true,
      "While recording, a red PROFILING message is shown at the top of the screen. The profiler measures frame times, GPU time per phase "
          + "(world, entities, particles, GUI) and which mod the game thread is busy in, and saves a report to config/luka-profile-<time>.txt"));

  public FpsProfilerModule() { super("FPS Profiler", true); }
  @Override public String category() { return "UTILITY"; }
}
