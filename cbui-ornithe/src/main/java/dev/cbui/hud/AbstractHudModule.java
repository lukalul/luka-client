package dev.cbui.hud;

import dev.cbui.font.Fonts;
import net.minecraft.client.gui.FontRenderer;
import dev.cbui.render.Draw;
import dev.cbui.render.Shapes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

/**
 * One HUD element (CPS, FPS, keystrokes...): a named box with an anchor on the screen, an offset, a
 * scale and a list of editable settings.
 *
 * <p>Subclasses report the size of their <em>content</em> with {@link #size} and draw it in content
 * coordinates (0,0 to content width/height). This class adds the shared box frame:
 * padding, a flat rounded translucent background, an optional border, and the text helpers that
 * give every module the same font, colour and shadow. {@link #renderBox} draws the whole box at the
 * current origin, so both CBUI's native path and OneConfig's HUD renderer (which applies its own
 * position and scale) draw exactly the same thing.
 */
public abstract class AbstractHudModule {
  public static final float MIN_SCALE = .5f, MAX_SCALE = 2f;
  protected final Minecraft mc = Minecraft.getMinecraft();
  private final String name;
  private final List<Setting<?>> settings = new ArrayList<>();
  /** Settings every module shares; they come after a module's own settings in the menu. */
  private final java.util.Set<Setting<?>> common =
      java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Setting<?>, Boolean>());
  private boolean enabled;
  /** False hides the box on the HUD while the module itself stays on (it keeps working, just isn't drawn). */
  private boolean hudVisible = true;
  private Anchor anchor;
  private float offsetX, offsetY, contentWidth, contentHeight, scale = 1;
  private final Anchor defaultAnchor;
  private final float defaultX, defaultY;
  protected final Setting.Toggle background =
      grouped("Background", add(new Setting.Toggle("Background", true, "A dark box behind the module")));
  protected final Setting.Color backgroundColor =
      grouped("Background", adv(new Setting.Color("Background color", 0x6F000000, "Colour and see-through amount of the box")));
  protected final Setting.Slider radius =
      grouped("Background", adv(new Setting.Slider("Corner radius", 2, 0, 8, .5f, "How round the corners of the box are")));
  protected final Setting.Slider padding =
      grouped("Background", adv(new Setting.Slider("Padding", 4, 0, 10, .5f, "Space between the text and the edge of the box")));
  protected final Setting.Toggle border = grouped("Border", adv(new Setting.Toggle("Border", false, "A thin outline around the box")));
  protected final Setting.Color borderColor = grouped("Border", adv(new Setting.Color("Border color", 0x9F8641D5, "Colour of the outline")));
  protected final Setting.Toggle chromaBorder =
      grouped("Border", adv(new Setting.Toggle("Chroma border", false, "Cycle the outline through rainbow colours")));
  protected final Setting.Color textColor = grouped("Text", add(new Setting.Color("Text color", 0xFFFFFFFF, "Colour of the text")));
  protected final Setting.Toggle chromaText =
      grouped("Text", add(new Setting.Toggle("Chroma text", false, "Cycle the text through rainbow colours")));
  protected final Setting.Slider chromaSpeed =
      grouped("Text", adv(new Setting.Slider("Chroma speed", 4, 1, 10, 1, "How fast the rainbow moves (higher is faster)")));
  protected final Setting.Toggle textShadow = grouped("Text", adv(new Setting.Toggle("Text shadow", true, "A dark shadow behind the letters so they stay readable")));
  protected final Setting.Toggle minecraftFont =
      grouped("Text", adv(new Setting.Toggle("Minecraft font", true, "On: the blocky Minecraft font. Off: the smooth Roboto font")));

  /** Puts a setting under a menu heading; returns it so declarations stay one expression. */
  protected static <S extends Setting<?>> S grouped(String group, S setting) {
    setting.setGroup(group);
    return setting;
  }

  protected AbstractHudModule(
      String name, boolean enabled, Anchor anchor, float x, float y, float width, float height) {
    this.name = name;
    this.enabled = enabled;
    this.anchor = this.defaultAnchor = anchor;
    this.offsetX = this.defaultX = x;
    this.offsetY = this.defaultY = y;
    this.contentWidth = width;
    this.contentHeight = height;
    common.addAll(settings);
  }

  protected final <T extends Setting<?>> T add(T setting) {
    settings.add(setting);
    return setting;
  }

  /** A setting that only matters for fine tuning: shown under "More options". */
  protected final <T extends Setting<?>> T adv(T setting) {
    setting.markAdvanced();
    return add(setting);
  }

  /** What the menu shows first: this module's own settings, then the shared basics (background, text colour). */
  public List<Setting<?>> basicSettings() {
    List<Setting<?>> out = new ArrayList<>();
    for (Setting<?> s : settings()) if (!s.isAdvanced() && !common.contains(s)) out.add(s);
    // a module without a HUD box has no use for the box's background/border/text settings
    if (hasHud()) for (Setting<?> s : settings()) if (!s.isAdvanced() && common.contains(s)) out.add(s);
    return out;
  }

  /** Fine-tuning settings, revealed by "More options". */
  public List<Setting<?>> advancedSettings() {
    List<Setting<?>> out = new ArrayList<>();
    for (Setting<?> s : settings()) if (s.isAdvanced() && (hasHud() || !common.contains(s))) out.add(s);
    return out;
  }

  /**
   * Called once per client tick while the module is enabled; compute values here, not in {@link
   * #draw}.
   */
  public void tick() {}

  /** Native path: places, scales and draws the box using CBUI's anchor/offset layout. */
  public final void render(ScaledResolution resolution, float partialTicks, boolean editor) {
    float[] b = bounds(resolution);
    if (!editor
        && (b[0] >= resolution.getScaledWidth()
            || b[1] >= resolution.getScaledHeight()
            || b[0] + b[2] <= 0
            || b[1] + b[3] <= 0)) return;
    renderAt(b[0], b[1], scale, partialTicks, editor);
  }

  /** Draws the box with its top-left corner at (x, y) in GUI units at the given scale. */
  public final void renderAt(float x, float y, float scale, float partialTicks, boolean editor) {
    GlStateManager.pushMatrix();
    try {
      GlStateManager.translate(x, y, 0);
      GlStateManager.scale(scale, scale, 1);
      renderBox(partialTicks, editor);
    } finally {
      GlStateManager.popMatrix();
    }
  }

  /** Draws background, border and content with the box's top-left corner at the current origin. */
  public final void renderBox(float partialTicks, boolean editor) {
    float w = width(), h = height();
    if (w <= 0 || h <= 0) return;
    if (!editor && (!hudVisible || nothingToShow())) return;
    GlStateManager.color(1, 1, 1, 1);
    GlStateManager.enableBlend();
    if (background.get()) Draw.rounded(0, 0, w, h, radius.get(), backgroundColor.get());
    if (border.get())
      Shapes.roundedOutline(
          0, 0, w, h, radius.get(), 1, chromaBorder.get() ? chroma(borderColor.get(), 0, 0) : borderColor.get());
    float p = pad();
    GlStateManager.pushMatrix();
    try {
      GlStateManager.translate(p, p, 0);
      draw(partialTicks, editor);
    } finally {
      GlStateManager.popMatrix();
      GlStateManager.color(1, 1, 1, 1);
    }
  }

  /** Draws the content; (0,0) is the content's top-left corner, inside the padding. */
  protected abstract void draw(float partialTicks, boolean editor);

  /** True when the module has no content right now (e.g. no active potion effects): the box is then not drawn outside the editor. */
  protected boolean nothingToShow() {
    return false;
  }

  /** False for switches without an on-screen box (e.g. GUI blur): not shown in any HUD editor. */
  public boolean hasHud() {
    return true;
  }

  /** False for modules whose own artwork fills the box edge to edge (keystrokes, scoreboard). */
  protected boolean padded() {
    return true;
  }

  protected final float pad() {
    return padded() ? padding.get() : 0;
  }

  public float[] bounds(ScaledResolution r) {
    return LayoutMath.bounds(
        anchor, offsetX, offsetY, width(), height(), scale, r.getScaledWidth(), r.getScaledHeight());
  }

  public String name() {
    return name;
  }

  /** The player's own switch (what is saved and shown in the menu). */
  public boolean userEnabled() {
    return enabled;
  }

  private long blockedVersion = -2;
  private boolean blocked;

  /** True while the server has switched this mod off (Badlion / Lunar server APIs); the player's own setting is kept. */
  public boolean serverBlocked() {
    long version = dev.cbui.server.ServerRulesBridge.cachedVersion();
    if (version != blockedVersion) {
      blockedVersion = version;
      blocked = dev.cbui.server.ServerRulesBridge.moduleBlocked(name);
    }
    return blocked;
  }

  /** Whether the module is in effect: the player's switch, unless the server has disabled this mod. */
  public boolean enabled() {
    return enabled && !serverBlocked();
  }

  public void enabled(boolean value) {
    enabled = value;
  }

  public boolean hudVisible() {
    return hudVisible;
  }

  public void hudVisible(boolean value) {
    hudVisible = value;
  }

  /** Re-anchors to the screen region (3x3 grid) the box centre is in, keeping it exactly where it is. */
  public void reanchor(ScaledResolution r) {
    float[] b = bounds(r);
    int sw = r.getScaledWidth(), sh = r.getScaledHeight();
    float cx = b[0] + b[2] / 2, cy = b[1] + b[3] / 2;
    int col = cx < sw / 3f ? 0 : cx < sw * 2f / 3f ? 1 : 2;
    int row = cy < sh / 3f ? 0 : cy < sh * 2f / 3f ? 1 : 2;
    Anchor target = Anchor.values()[row * 3 + col];
    if (target == anchor) return;
    float[] base = LayoutMath.bounds(target, 0, 0, width(), height(), scale, sw, sh);
    anchor = target;
    offsetX = b[0] - base[0];
    offsetY = b[1] - base[1];
  }

  public Anchor anchor() {
    return anchor;
  }

  public void anchor(Anchor value) {
    anchor = value;
  }

  public float offsetX() {
    return offsetX;
  }

  public float offsetY() {
    return offsetY;
  }

  public void offset(float x, float y) {
    offsetX = x;
    offsetY = y;
  }

  /** Unscaled box width: content plus padding on both sides. */
  public float width() {
    return contentWidth <= 0 ? 0 : contentWidth + 2 * pad();
  }

  /** Unscaled box height: content plus padding on both sides. */
  public float height() {
    return contentHeight <= 0 ? 0 : contentHeight + 2 * pad();
  }

  protected final float contentWidth() {
    return contentWidth;
  }

  protected final float contentHeight() {
    return contentHeight;
  }

  /** Sets the content size (the box adds the padding). */
  protected void size(float w, float h) {
    contentWidth = w;
    contentHeight = h;
  }

  public float scale() {
    return scale;
  }

  public void scale(float value) {
    scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
  }

  public List<Setting<?>> settings() {
    return Collections.unmodifiableList(settings);
  }

  public void resetLayout() {
    anchor = defaultAnchor;
    offsetX = defaultX;
    offsetY = defaultY;
    scale = 1f;
  }

  public void resetSettings() {
    for (Setting<?> setting : settings) setting.reset();
  }

  // ---- shared text helpers: one font, one colour, one shadow rule for every module ----

  /** Rainbow colour for the current time (and position, so neighbouring rows are offset); keeps the alpha of {@code argb}. */
  protected final int chroma(int argb, float x, float y) {
    float period = 12000f / chromaSpeed.get();
    float hue = ((System.currentTimeMillis() % 1000000L) / period + (x + y) * 0.004f) % 1f;
    int alpha = argb >>> 24 == 0 ? 0xFF : argb >>> 24;
    return (alpha << 24) | (java.awt.Color.HSBtoRGB(hue, 0.75f, 1f) & 0xFFFFFF);
  }

  protected final float text(String text, float x, float y, int color) {
    if (chromaText.get() && color == textColor.get()) color = chroma(color, x, y);
    if (useVanilla()) {
      FontRenderer font = mc.fontRendererObj;
      return textShadow.get() ? font.drawStringWithShadow(text, x, y, color) : font.drawString(text, x, y, color, false);
    }
    return textShadow.get()
        ? Fonts.regular().drawStringWithShadow(text, x, y, color)
        : Fonts.regular().drawString(text, x, y, color);
  }

  private boolean useVanilla() {
    return minecraftFont.get() && mc.fontRendererObj != null;
  }

  protected final int textWidth(String text) {
    if (useVanilla()) return mc.fontRendererObj.getStringWidth(text);
    return Fonts.regular().getStringWidth(text);
  }

  /** Height of one text row (the shadow fits inside it). */
  protected final int lineHeight() {
    if (useVanilla()) return mc.fontRendererObj.FONT_HEIGHT;
    return Fonts.regular().lineHeight();
  }

  /** Distance between the tops of two text rows. */
  protected final int lineStep() {
    return lineHeight() + 1;
  }

  /** Content height of {@code lines} stacked text rows. */
  protected final float linesHeight(int lines) {
    return lines <= 0 ? 0 : lines * lineHeight() + (lines - 1) * (lineStep() - lineHeight());
  }

  /** One line of text centered horizontally in the content area. */
  protected void centered(String text, float y) {
    text(text, (contentWidth - textWidth(text)) / 2f, y, textColor.get());
  }
}
