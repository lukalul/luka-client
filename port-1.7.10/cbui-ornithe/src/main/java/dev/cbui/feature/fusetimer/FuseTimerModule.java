package dev.cbui.feature.fusetimer;

import dev.cbui.Cbui;
import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Locale;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.item.EntityTNTPrimed;
import org.lwjgl.opengl.GL11;

/**
 * A floating label over every primed TNT that counts down to the explosion. The label is drawn here with the look of
 * a vanilla name tag, not through vanilla's entity-label routine, so name-tag settings never reach it.
 */
public final class FuseTimerModule extends FeatureModule {
  private static final String UNIT_S = "s (seconds)";
  private static final String UNIT_T = "t (game ticks)";
  private static final String UNIT_MS = "ms (milliseconds)";

  private static final String LAYOUT_PLAIN = "2.50";
  private static final String LAYOUT_SUFFIX = "2.50 + unit";
  private static final String LAYOUT_BRACKETS = "[2.50]";
  private static final String LAYOUT_BRACKETS_SUFFIX = "[2.50 + unit]";
  private static final String LAYOUT_NAMED = "TNT 2.50";
  private static final String LAYOUT_FUSE = "Fuse: 2.50 + unit";

  /** Vanilla's fuse for a freshly lit block, which is also where the colour fade starts. */
  private static final int VANILLA_FUSE = 80;
  private static final double MAX_DISTANCE_SQ = 64.0 * 64.0;
  private static final float NAMETAG_SCALE = 0.016666668f * 1.6f;

  private final Setting.Choice unit = add(Setting.tip(new Setting.Choice(
      "Show time in", UNIT_S, UNIT_S, UNIT_T, UNIT_MS), "Unit of the countdown number"));
  private final Setting.Choice layout = add(Setting.tip(new Setting.Choice(
      "Text layout", LAYOUT_PLAIN, LAYOUT_PLAIN, LAYOUT_SUFFIX, LAYOUT_BRACKETS, LAYOUT_BRACKETS_SUFFIX,
      LAYOUT_NAMED, LAYOUT_FUSE), "How the label is worded. \"unit\" stands for the unit picked above"));
  private final Setting.Slider decimals = add(new Setting.Slider(
      "Decimals", 2, 0, 3, 1, "Digits after the decimal point"));
  private final Setting.Slider correction = add(new Setting.Slider(
      "Fuse correction (ticks)", 0, -80, 80, 1, "Match servers whose TNT burns longer or shorter"));
  private final Setting.Color startColour = grouped("Colours", add(new Setting.Color(
      "Colour at start", 0xFF00FF00, "Colour while plenty of time is left")));
  private final Setting.Color endColour = grouped("Colours", add(new Setting.Color(
      "Colour at end", 0xFFFF0000, "Colour just before it explodes")));
  private final Setting.Slider size = add(new Setting.Slider(
      "Label size", 1, 0.5f, 3, 0.1f, "1 is the normal nametag size"));

  /** Label text per remaining tick count, valid for the cached unit, layout, decimals and locale below. */
  private String[] texts = new String[0];
  private String cachedUnit = "", cachedLayout = "";
  private int cachedDigits = -1;
  private Locale cachedLocale;
  private NumberFormat format;
  /** Set when drawing failed once; the label then stays off until the module is switched off and on. */
  private boolean broken;

  public FuseTimerModule() { super("TNTCountdown", true); }

  @Override public String category() { return "VISUALS"; }

  @Override
  public void enabled(boolean value) {
    super.enabled(value);
    if (!value) broken = false;
  }

  /** The module when it exists and is switched on, otherwise null. */
  public static FuseTimerModule active() {
    try {
      FuseTimerModule module = Features.get(FuseTimerModule.class);
      return module != null && module.enabled() && !module.broken ? module : null;
    } catch (Throwable failure) {
      return null;
    }
  }

  private int ticksLeft(EntityTNTPrimed tnt) {
    return tnt.fuse + Math.round(correction.get());
  }

  private String unitSuffix() {
    String chosen = unit.get();
    if (UNIT_T.equals(chosen)) return "t";
    if (UNIT_MS.equals(chosen)) return "ms";
    return "s";
  }

  private double valueOf(int ticks) {
    String chosen = unit.get();
    if (UNIT_T.equals(chosen)) return ticks;
    if (UNIT_MS.equals(chosen)) return ticks * 50.0;
    return ticks / 20.0;
  }

  /** Text for this many ticks; strings are made once per shown value and kept until a text setting changes. */
  String textFor(int ticks) {
    int digits = Math.max(0, Math.min(3, Math.round(decimals.get())));
    String shownUnit = unit.get(), shownLayout = layout.get();
    Locale locale = Locale.getDefault();
    if (format == null || digits != cachedDigits || !shownUnit.equals(cachedUnit) || !shownLayout.equals(cachedLayout)
        || !locale.equals(cachedLocale)) {
      cachedDigits = digits;
      cachedUnit = shownUnit;
      cachedLayout = shownLayout;
      cachedLocale = locale;
      Arrays.fill(texts, null);
      format = NumberFormat.getNumberInstance(Locale.getDefault());
      format.setGroupingUsed(false);
      format.setMinimumFractionDigits(digits);
      format.setMaximumFractionDigits(digits);
    }
    if (ticks >= texts.length) texts = Arrays.copyOf(texts, Math.max(ticks + 1, texts.length * 2));
    String text = texts[ticks];
    if (text == null) {
      text = wrap(format.format(valueOf(ticks)));
      texts[ticks] = text;
    }
    return text;
  }

  private String wrap(String number) {
    String chosen = layout.get();
    if (LAYOUT_SUFFIX.equals(chosen)) return number + unitSuffix();
    if (LAYOUT_BRACKETS.equals(chosen)) return "[" + number + "]";
    if (LAYOUT_BRACKETS_SUFFIX.equals(chosen)) return "[" + number + unitSuffix() + "]";
    if (LAYOUT_NAMED.equals(chosen)) return "TNT " + number;
    if (LAYOUT_FUSE.equals(chosen)) return "Fuse: " + number + unitSuffix();
    return number;
  }

  /** RGB for this many ticks: the start colour until the last 4 s (plus the correction), then a blend to the end colour. */
  int colourFor(int ticks) {
    int window = VANILLA_FUSE + Math.round(correction.get());
    int from = startColour.get(), to = endColour.get();
    if (window <= 0 || ticks <= 0) return to & 0xFFFFFF;
    if (ticks >= window) return from & 0xFFFFFF;
    float t = ticks / (float) window; // 1 at the start of the fade, 0 at the end
    int r = mix((to >> 16) & 0xFF, (from >> 16) & 0xFF, t);
    int g = mix((to >> 8) & 0xFF, (from >> 8) & 0xFF, t);
    int b = mix(to & 0xFF, from & 0xFF, t);
    return (r << 16) | (g << 8) | b;
  }

  private static int mix(int a, int b, float t) {
    return Math.max(0, Math.min(255, Math.round(a + (b - a) * t)));
  }

  /** Draws the countdown over one primed TNT; x, y, z are its position relative to the camera. */
  public void drawLabel(EntityTNTPrimed tnt, double x, double y, double z) {
    int ticks = ticksLeft(tnt);
    if (ticks <= 0 || x * x + y * y + z * z > MAX_DISTANCE_SQ) return;
    String text = textFor(ticks);
    int rgb = colourFor(ticks);
    RenderManager view = RenderManager.instance;
    FontRenderer font = view.getFontRenderer();
    if (font == null) font = mc.fontRendererObj;
    float scale = NAMETAG_SCALE * size.get();
    int half = font.getStringWidth(text) / 2;

    GlStateManager.pushMatrix();
    try {
      GlStateManager.translate((float) x, (float) y + tnt.height + 0.5f, (float) z);
      GL11.glNormal3f(0f, 1f, 0f);
      GlStateManager.rotate(-view.playerViewY, 0f, 1f, 0f);
      GlStateManager.rotate(view.playerViewX, 1f, 0f, 0f);
      GlStateManager.scale(-scale, -scale, scale);
      GlStateManager.disableLighting();
      GlStateManager.depthMask(false);
      GlStateManager.disableDepth();
      GlStateManager.enableBlend();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

      GlStateManager.disableTexture2D();
      Tessellator box = Tessellator.instance;
      box.startDrawingQuads();
      box.setColorRGBA_F(0f, 0f, 0f, 0.25f);
      box.addVertex(-half - 1, -1, 0);
      box.addVertex(-half - 1, 8, 0);
      box.addVertex(half + 1, 8, 0);
      box.addVertex(half + 1, -1, 0);
      box.draw();
      GlStateManager.enableTexture2D();

      // Faint copy that shows through blocks, then the solid copy in front, as on a name tag.
      font.drawString(text, -half, 0, 0x20000000 | rgb);
      GlStateManager.enableDepth();
      GlStateManager.depthMask(true);
      font.drawString(text, -half, 0, 0xFF000000 | rgb);
    } catch (Throwable error) {
      broken = true;
      Cbui.reportOnce("TNTCountdown label", error);
    } finally {
      GlStateManager.enableTexture2D();
      GlStateManager.enableDepth();
      GlStateManager.depthMask(true);
      GlStateManager.enableLighting();
      GlStateManager.disableBlend();
      GlStateManager.color(1f, 1f, 1f, 1f);
      GlStateManager.popMatrix();
    }
  }
}
