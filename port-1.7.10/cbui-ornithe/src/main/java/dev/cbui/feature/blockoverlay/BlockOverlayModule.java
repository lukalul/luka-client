package dev.cbui.feature.blockoverlay;

import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;

/**
 * Block Overlay: the box drawn around the block you look at, replacing vanilla's thin black outline. An outline and a translucent fill,
 * each with its own colour and opacity, an optional rainbow, and an option to show through other blocks.
 */
public final class BlockOverlayModule extends FeatureModule {
  public final Setting.Toggle outline = grouped("Outline", add(new Setting.Toggle("Outline", true, "Draw the edges of the block you are looking at.")));
  public final Setting.Color outlineColor = grouped("Outline", add(new Setting.Color("Outline color", 0xFFFFFFFF, "Colour and opacity of the outline.")));
  public final Setting.Slider width = grouped("Outline", add(new Setting.Slider("Outline width", 2.5f, 1f, 8f, 0.5f, "How thick the outline is, in pixels.")));

  public final Setting.Toggle fill = grouped("Fill", add(new Setting.Toggle("Fill", true, "Tint the block's faces.")));
  public final Setting.Color fillColor = grouped("Fill", add(new Setting.Color("Fill color", 0x33FFFFFF, "Colour and opacity of the fill.")));

  public final Setting.Toggle chroma = grouped("Colours", add(new Setting.Toggle("Chroma", false, "Cycle the colours through the rainbow (keeps each colour's opacity).")));
  public final Setting.Slider chromaSpeed = grouped("Colours", add(new Setting.Slider("Chroma speed", 3f, 1f, 10f, 0.5f, "How fast the rainbow cycles.")));

  public final Setting.Toggle throughWalls = grouped("Options", add(new Setting.Toggle("Show through blocks", false, "Keep the overlay visible when something is in front of the block.")));
  public final Setting.Toggle hideHud = grouped("Options", add(new Setting.Toggle("Hide with the HUD", true, "Do not draw the overlay while the HUD is hidden (F1).")));

  public BlockOverlayModule() { super("Block Overlay", true); }

  @Override public String category() { return "VISUALS"; }

  /** The colour to draw with: the chosen one, or the rainbow at the same opacity. */
  public int resolve(int argb) {
    if (!chroma.get()) return argb;
    double hue = (System.currentTimeMillis() * chromaSpeed.get() / 4000.0) % 1.0;
    return (argb & 0xFF000000) | (java.awt.Color.HSBtoRGB((float) hue, 0.8f, 1f) & 0xFFFFFF);
  }
}
