package dev.cbui.feature.saturation;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;

/**
 * Saturation: more information on the hunger bar, in the spirit of AppleSkin (written for luka; AppleSkin itself is public
 * domain). A gold outline over the hunger icons shows saturation, a light bar behind them shows exhaustion, and holding
 * food flashes what eating it would add. Drawing is in {@link SaturationHud}, hooked in by the hunger bar mixin.
 */
public final class SaturationModule extends FeatureModule implements Features.Synced {
  static volatile SaturationModule instance;

  final Setting.Toggle saturation = add(new Setting.Toggle(
      "Saturation overlay", true, "A gold outline on the hunger icons: how much saturation is left before hunger drops"));
  final Setting.Toggle exhaustion = add(new Setting.Toggle(
      "Exhaustion underlay", true, "A light bar behind the hunger icons: how close the next saturation (or hunger) point is to being used up. Estimated on your side, servers do not send it"));
  final Setting.Toggle foodValues = add(new Setting.Toggle(
      "Hunger and saturation restored", true, "While holding food, the hunger and saturation eating it would add flash on the bar"));
  final Setting.Toggle healthRestored = add(new Setting.Toggle(
      "Potential health restored", false, "While holding food, the health it could heal back over time flashes on the hearts"));

  public SaturationModule() {
    super("Saturation", true);
    instance = this;
  }

  @Override public String category() { return "HUD"; }

  /** Every client tick: keeps the exhaustion estimate up to date. */
  @Override
  public void sync() {
    if (enabled()) SaturationHud.tick();
  }
}
