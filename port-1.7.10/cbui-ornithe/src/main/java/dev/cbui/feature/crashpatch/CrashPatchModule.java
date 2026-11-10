package dev.cbui.feature.crashpatch;

import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;

public final class CrashPatchModule extends FeatureModule {
  public final Setting.Toggle catchCrashes = grouped("Patches", add(new Setting.Toggle("Catch crashes during gameplay", true, "Catch crashes whilst in-game, and prevent the game from closing")));
  public final Setting.Slider leaveLimit = grouped("Limits", add(new Setting.Slider("World Leave Limit", 1f, 1f, 20f, 1f, "Leave the world once this many crashes happened (1 = always leave the world). Out of memory always leaves the world")));
  public final Setting.Slider crashLimit = grouped("Limits", add(new Setting.Slider("Crash Limit", 5f, 1f, 20f, 1f, "After this many crashes since the game started it crashes normally. It is also normal after 3 crashes within 10 seconds")));
  public final Setting.Choice uploadMethod = grouped("Logs", add(Setting.tip(new Setting.Choice("Log uploader", "hst.sh", "hst.sh", "mclo.gs (Aternos)"), "The site the crash report is uploaded to when you press Upload report")));
  private static volatile CrashPatchModule registered;
  public CrashPatchModule() { super("CrashPatch", true); registered = this; }
  @Override public String category() { return "UTILITY"; }
  public static CrashPatchModule enabledInstance() {
    CrashPatchModule candidate = registered;
    return candidate != null && candidate.enabled() ? candidate : null;
  }
}
