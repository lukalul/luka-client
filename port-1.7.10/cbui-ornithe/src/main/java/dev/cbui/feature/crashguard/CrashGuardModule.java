package dev.cbui.feature.crashguard;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;

/**
 * Crash Patch: an error during play opens a recovery screen instead of closing the game. Lives on the GENERAL
 * settings tab. The catching itself is in {@link CrashRecovery}; this class holds the switches and limits.
 */
public final class CrashGuardModule extends FeatureModule {
  static final String SITE_HST = "hst.sh";
  static final String SITE_MCLOGS = "mclo.gs";

  private final Setting.Toggle survive = add(new Setting.Toggle("Survive crashes in game", true,
      "Show a recovery screen instead of closing when something breaks mid-game"));
  private final Setting.Slider leaveFrom = add(new Setting.Slider("Leave the world from crash no.", 1, 1, 20, 1,
      "From this crash of the session on, the world is closed first; 1 = always. Out of memory and singleplayer crashes always close it"));
  private final Setting.Slider giveUpAt = add(new Setting.Slider("Give up at crash no.", 5, 1, 20, 1,
      "This crash of the session closes the game the normal way; so do several crashes within seconds"));
  private final Setting.Choice pasteSite = add(Setting.tip(new Setting.Choice("Paste site", SITE_HST, SITE_HST, SITE_MCLOGS),
      "Where \"Share report online\" uploads the report"));

  public CrashGuardModule() {
    super("CrashPatch", true);
  }

  @Override
  public String category() {
    return "UTILITY";
  }

  /** The module instance, switched on or not (the crash path runs outside the normal tick). */
  public static CrashGuardModule instance() {
    return Features.get(CrashGuardModule.class);
  }

  /** Whether crashes should be caught right now. */
  boolean catching() {
    return enabled() && survive.get();
  }

  int leaveFromCrash() {
    return Math.round(leaveFrom.get());
  }

  int giveUpAtCrash() {
    return Math.round(giveUpAt.get());
  }

  String pasteSite() {
    return pasteSite.get();
  }
}
