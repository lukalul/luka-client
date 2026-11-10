package dev.cbui.feature.fullbright;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;

/**
 * The on/off switch for the Fullbright mod (every block and entity drawn at full light, with normal colours). There is no key for it:
 * use this card's switch. The Fullbright mod reads its state through reflection, so luka still starts without it.
 */
public final class FullbrightModule extends FeatureModule implements Features.Synced {
  public FullbrightModule() { super("Fullbright", true); }
  @Override public String category() { return "VISUALS"; }

  @Override
  public void sync() {
    boolean on = enabled();
    try {
      // compared with the mod's own value every tick: it reads its saved file on its first tick, after this card first applied its state
      Class<?> fullbright = Class.forName("dev.fullbright.Fullbright");
      if (fullbright.getField("enabled").getBoolean(null) == on) return;
      fullbright.getField("enabled").setBoolean(null, on);
      Class.forName("dev.fullbright.Fullbright$LightmapRefresh").getField("request").setBoolean(null, true);
    } catch (Throwable ignored) {
      // the Fullbright mod is not installed
    }
  }
}
