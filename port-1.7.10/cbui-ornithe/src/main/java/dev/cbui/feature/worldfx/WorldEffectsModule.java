package dev.cbui.feature.worldfx;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import dev.luka.compat.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Look of two world effects: lightning bolts and a cast fishing rod. With the defaults both are drawn exactly as
 * vanilla draws them. (1.7.10 has no world border, so there are no border settings.)
 */
public final class WorldEffectsModule extends FeatureModule {
  private static final String BOLT = "Lightning";
  private static final String ROD = "Fishing rod";

  /** Vanilla's bolt tint (0.45, 0.45, 0.5 at 0.3 alpha) as the nearest ARGB value. */
  static final int VANILLA_BOLT = 0x4C72727F;
  static final int VANILLA_LINE = 0xFF000000;

  private final Setting.Toggle hideBolts = grouped(BOLT, add(new Setting.Toggle(
      "Hide bolts", false, "Lightning strikes leave no visible bolt.")));
  private final Setting.Color boltColour = grouped(BOLT, add(new Setting.Color(
      "Bolt colour", VANILLA_BOLT, "Tint and see-through level of lightning bolts.")));
  private final Setting.Toggle hideCast = grouped(ROD, add(new Setting.Toggle(
      "Hide bobber and line", false, "Nothing of a cast rod is drawn.")));
  private final Setting.Slider lineWidth = grouped(ROD, add(new Setting.Slider(
      "Line thickness", 1, 0.1f, 5, 0.1f, "How thick the fishing line is drawn.")));
  private final Setting.Color lineColour = grouped(ROD, add(new Setting.Color(
      "Line colour", VANILLA_LINE, "Colour of the fishing line.")));

  /** What the line pass changed, so {@link #afterLine} undoes exactly that. */
  private boolean lineWidened, lineBlended;

  public WorldEffectsModule() { super("Render Tweaks", true); }

  @Override public String category() { return "VISUALS"; }

  /** The module when it exists and is switched on, otherwise null. */
  public static WorldEffectsModule active() {
    try {
      WorldEffectsModule module = Features.get(WorldEffectsModule.class);
      return module != null && module.enabled() ? module : null;
    } catch (Throwable failure) {
      return null;
    }
  }

  private static int alphaOf(int argb) { return argb >>> 24; }

  // ---- lightning ----

  public boolean skipBolt() {
    return hideBolts.get() || alphaOf(boltColour.get()) == 0;
  }

  /** True when the bolt keeps vanilla's own vertex colour (the setting is still at its default). */
  public boolean boltIsVanilla() { return boltColour.get() == VANILLA_BOLT; }

  /** Channel of the bolt colour: 0 red, 1 green, 2 blue, 3 alpha, each 0..1. */
  public float boltChannel(int channel) {
    int shift = channel == 0 ? 16 : channel == 1 ? 8 : channel == 2 ? 0 : 24;
    return ((boltColour.get() >>> shift) & 0xFF) / 255f;
  }

  // ---- fishing rod ----

  public boolean skipCast() { return hideCast.get(); }

  public boolean lineIsVanilla() { return lineColour.get() == VANILLA_LINE; }

  /** Line colour without alpha (0xRRGGBB). */
  public int lineRgb() { return lineColour.get() & 0xFFFFFF; }

  /** Line alpha, 0..255. */
  public int lineAlpha() { return alphaOf(lineColour.get()); }

  /** Right before the line is drawn: thickness, and blending when the line colour is see-through. */
  public void beforeLine() {
    lineWidened = false;
    lineBlended = false;
    float width = lineWidth.get();
    if (Math.abs(width - 1f) > 1e-3f) {
      GL11.glLineWidth(Math.max(0.1f, width));
      lineWidened = true;
    }
    if (alphaOf(lineColour.get()) < 255) {
      GlStateManager.enableBlend();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
      GlStateManager.depthMask(false);
      lineBlended = true;
    }
  }

  /** Right after the line was drawn (also when drawing it failed): puts back what {@link #beforeLine} changed. */
  public void afterLine() {
    if (lineWidened) GL11.glLineWidth(1f);
    if (lineBlended) {
      GlStateManager.depthMask(true);
      GlStateManager.disableBlend();
    }
    lineWidened = false;
    lineBlended = false;
  }
}
