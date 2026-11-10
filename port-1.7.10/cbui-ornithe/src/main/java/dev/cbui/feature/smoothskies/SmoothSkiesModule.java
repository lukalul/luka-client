package dev.cbui.feature.smoothskies;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;

/** Sky tweaks: a smoother fade towards the void, and switches for the horizon box, sunset ring and the sky itself. */
public final class SmoothSkiesModule extends FeatureModule {
  /** Eye height below which "Remove Sky in the Void" stops drawing the sky. */
  public static final double VOID_SKY_CUTOFF = -1.5D;

  public final Setting.Toggle lowerSkyVoidDarkness = grouped("Sky", add(new Setting.Toggle(
      "Lower Sky Void Darkness", true,
      "Starts the darkening of the sky at the bottom of the world, so it fades in gradually.")));
  public final Setting.Toggle removeVoidBox = grouped("Sky", add(new Setting.Toggle(
      "Remove Void Box", true, "Hides the dark slab drawn under the horizon when you are looking from below it.")));
  public final Setting.Toggle removeSunriseAndSunsetColoring = grouped("Extras", add(new Setting.Toggle(
      "Remove Sunrise and Sunset Coloring", false,
      "Hides the orange glow at the horizon during sunrise and sunset, along with the fog tint it causes.")));
  public final Setting.Toggle removeSkyInVoid = grouped("Extras", add(new Setting.Toggle(
      "Remove Sky in the Void", false, "Stops drawing the sky once you have fallen below the world.")));

  public SmoothSkiesModule() {
    super("Smooth Skies", true);
  }

  @Override public String category() { return "VISUALS"; }

  /** The enabled module, or null when it is off or unavailable. */
  public static SmoothSkiesModule active() {
    try {
      SmoothSkiesModule module = Features.get(SmoothSkiesModule.class);
      return module != null && module.enabled() ? module : null;
    } catch (Throwable failure) {
      return null;
    }
  }
}
