package dev.cbui.feature.rendertweaks;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;

/** Visibility and colour controls for lightning bolts, fishing lines and the world border. */
public final class RenderTweaksModule extends FeatureModule {
  public final Setting.Toggle disableLightning = grouped("Lightning Bolt", add(new Setting.Toggle(
      "Disable Lightning", false, "Lightning bolts are not drawn.")));
  public final Setting.Color lightningColor = grouped("Lightning Bolt", add(new Setting.Color(
      "Color", 0x4C72727F, "Colour (with transparency) that lightning bolts are drawn in.")));

  public final Setting.Toggle disableFishingLine = grouped("Fishing Line", add(new Setting.Toggle(
      "Disable Fishing Line", false, "The line between you and your bobber is not drawn.")));
  public final Setting.Slider fishingLineWidthMultiplier = grouped("Fishing Line", add(new Setting.Slider(
      "Width Multiplier", 1f, .1f, 5f, .1f, "Makes the fishing line thinner or thicker.")));
  public final Setting.Color fishingLineColor = grouped("Fishing Line", add(new Setting.Color(
      "Color", 0xFF000000, "Colour of the fishing line.")));


  public RenderTweaksModule() {
    super("Render Tweaks", true);
  }

  @Override public String category() { return "VISUALS"; }

  /** The enabled module, or null when it is off or unavailable. */
  public static RenderTweaksModule active() {
    try {
      RenderTweaksModule module = Features.get(RenderTweaksModule.class);
      return module != null && module.enabled() ? module : null;
    } catch (Throwable failure) {
      return null;
    }
  }

  /** One 8-bit channel of an ARGB colour; {@code shift} is 24 for alpha, 16 red, 8 green, 0 blue. */
  public static int component(int argb, int shift) { return argb >>> shift & 255; }

  /** The same channel scaled to 0..1. */
  public static float channel(int argb, int shift) { return component(argb, shift) / 255F; }

  private static boolean invisible(int argb) { return component(argb, 24) == 0; }

  public boolean hidesLightning() { return disableLightning.get() || invisible(lightningColor.get()); }

  public boolean hidesFishingLine() { return disableFishingLine.get() || invisible(fishingLineColor.get()); }

}
