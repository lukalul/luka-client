package dev.cbui.feature.worldfx;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.world.border.EnumBorderStatus;
import org.lwjgl.opengl.GL11;

/**
 * Look of three world effects: lightning bolts, a cast fishing rod and the world border. With the defaults every one
 * of them is drawn exactly as vanilla draws it.
 */
public final class WorldEffectsModule extends FeatureModule {
  private static final String BOLT = "Lightning";
  private static final String ROD = "Fishing rod";
  private static final String STILL = "Border (still)";
  private static final String CLOSING = "Border (closing)";
  private static final String WIDENING = "Border (widening)";

  /** Vanilla's bolt tint (0.45, 0.45, 0.5 at 0.3 alpha) as the nearest ARGB value. */
  static final int VANILLA_BOLT = 0x4C72727F;
  static final int VANILLA_LINE = 0xFF000000;
  static final int VANILLA_STILL = 0xFF20A0FF;
  static final int VANILLA_CLOSING = 0xFFFF3030;
  static final int VANILLA_WIDENING = 0xFF40FF80;

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
  private final Setting.Toggle hideStill = grouped(STILL, add(new Setting.Toggle(
      "Hide still border", false, "Do not draw the border while it is not moving.")));
  private final Setting.Color stillColour = grouped(STILL, add(new Setting.Color(
      "Still border colour", VANILLA_STILL, "Tint of a border that is not moving.")));
  private final Setting.Toggle hideClosing = grouped(CLOSING, add(new Setting.Toggle(
      "Hide closing border", false, "Do not draw the border while it shrinks.")));
  private final Setting.Color closingColour = grouped(CLOSING, add(new Setting.Color(
      "Closing border colour", VANILLA_CLOSING, "Tint of a shrinking border.")));
  private final Setting.Toggle hideWidening = grouped(WIDENING, add(new Setting.Toggle(
      "Hide widening border", false, "Do not draw the border while it grows.")));
  private final Setting.Color wideningColour = grouped(WIDENING, add(new Setting.Color(
      "Widening border colour", VANILLA_WIDENING, "Tint of a growing border.")));

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
  public float boltChannel(int channel) { return channel(boltColour.get(), channel); }

  private static float channel(int argb, int channel) {
    int shift = channel == 0 ? 16 : channel == 1 ? 8 : channel == 2 ? 0 : 24;
    return ((argb >>> shift) & 0xFF) / 255f;
  }

  // ---- fishing rod ----

  public boolean skipCast() { return hideCast.get(); }

  public boolean lineIsVanilla() { return lineColour.get() == VANILLA_LINE; }

  /** Colour of the fishing line as 0..255 channels: 0 red, 1 green, 2 blue, 3 alpha. */
  public int lineChannel(int channel) {
    int argb = lineColour.get();
    int shift = channel == 0 ? 16 : channel == 1 ? 8 : channel == 2 ? 0 : 24;
    return (argb >>> shift) & 0xFF;
  }

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

  // ---- world border ----

  private int borderColourFor(EnumBorderStatus status) {
    if (status == EnumBorderStatus.SHRINKING) return closingColour.get();
    if (status == EnumBorderStatus.GROWING) return wideningColour.get();
    return stillColour.get();
  }

  private boolean borderHiddenFor(EnumBorderStatus status) {
    if (status == EnumBorderStatus.SHRINKING) return hideClosing.get();
    if (status == EnumBorderStatus.GROWING) return hideWidening.get();
    return hideStill.get();
  }

  private static int vanillaBorder(EnumBorderStatus status) {
    if (status == EnumBorderStatus.SHRINKING) return VANILLA_CLOSING;
    if (status == EnumBorderStatus.GROWING) return VANILLA_WIDENING;
    return VANILLA_STILL;
  }

  public boolean skipBorder(EnumBorderStatus status) {
    return borderHiddenFor(status) || alphaOf(borderColourFor(status)) == 0;
  }

  public boolean borderIsVanilla(EnumBorderStatus status) {
    return borderColourFor(status) == vanillaBorder(status);
  }

  /** Channel of the border tint for this state: 0 red, 1 green, 2 blue, 3 alpha (multiplies vanilla's distance fade). */
  public float borderChannel(EnumBorderStatus status, int channel) {
    return channel(borderColourFor(status), channel);
  }
}
