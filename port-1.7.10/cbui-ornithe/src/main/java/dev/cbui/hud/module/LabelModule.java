package dev.cbui.hud.module;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;

/**
 * Shared base for single-line text modules. The label is replaced on the tick; the box is at least
 * as wide as the sample text (so it does not jitter as digits change) and is resized only when the
 * label's width changes. The drawn strings and their widths are built when the label or the look
 * changes, never per frame.
 */
abstract class LabelModule extends AbstractHudModule {
  static final String BRACKETS_OFF = "Off", BRACKETS_ALWAYS = "Always", BRACKETS_NO_BOX = "When background is off";

  /**
   * The usual client option: the text in square brackets, like [120 FPS]. "When background is off" draws the plain text
   * (no shadow) when the module has its box, and the bracketed text with a shadow, sized to the text, when it has none.
   * Null on modules whose text the player writes in full (toggle sprint).
   */
  protected final Setting.Choice brackets;
  private String sample, label;
  /** What is drawn for the sample and the label: with brackets when the look asks for them. */
  private String sampleShown, labelShown;
  private int sampleWidth = -1, labelWidth = -1;
  /** The look the strings above were built for. */
  private boolean styled, bracketed, fitText, vanillaFont, noShadow;

  LabelModule(String name, boolean enabled, Anchor anchor, float x, float y, String sample, boolean bracketOption) {
    super(name, enabled, anchor, x, y, 48, 11);
    this.sample = sample;
    brackets = bracketOption
        ? grouped("Text", add(Setting.tip(new Setting.Choice("Brackets", BRACKETS_OFF, BRACKETS_OFF, BRACKETS_ALWAYS, BRACKETS_NO_BOX),
            "Off: no brackets. Always: like [120 FPS]. When background is off: plain text in the box, [120 FPS] without it")))
        : null;
  }

  LabelModule(String name, boolean enabled, Anchor anchor, float x, float y, String sample) {
    this(name, enabled, anchor, x, y, sample, true);
  }

  LabelModule(String name, Anchor anchor, float x, float y, String sample) {
    this(name, false, anchor, x, y, sample);
  }

  /** The text in brackets when the look asks for them (text that already starts with one is left alone). */
  private String wrap(String text) {
    if (text == null || !bracketed || text.startsWith("[")) return text;
    return "[" + text + "]";
  }

  protected final void label(String text) {
    if (text.equals(label) && labelWidth >= 0) return;
    label = text;
    labelWidth = -1;
    resize();
  }

  /** Replaces the sample (the text the box is at least as wide as, and what the HUD editor shows); measured on the next draw. */
  protected final void sample(String text) {
    if (text.equals(sample)) return;
    sample = text;
    sampleWidth = -1;
  }

  /**
   * Reads the look (brackets mode, background, font); true when it changed since the strings were built, which then
   * have to be measured again.
   */
  private boolean style() {
    String mode = brackets == null ? BRACKETS_OFF : brackets.get();
    boolean auto = BRACKETS_NO_BOX.equals(mode), box = background.get();
    boolean b = BRACKETS_ALWAYS.equals(mode) || auto && !box;
    boolean fit = auto && !box;
    boolean font = minecraftFont.get() || dev.cbui.font.LukaFontRenderer.minecraft;
    noShadow = auto && box;
    if (styled && b == bracketed && fit == fitText && font == vanillaFont) return false;
    styled = true;
    bracketed = b;
    fitText = fit;
    vanillaFont = font;
    sampleWidth = labelWidth = -1;
    return true;
  }

  /** Recomputes the box; also call when a setting changes the text's width. */
  protected final void resize() {
    style();
    if (sampleWidth < 0) {
      sampleShown = wrap(sample);
      sampleWidth = textWidth(sampleShown);
    }
    if (label == null) {
      labelShown = sampleShown;
      labelWidth = sampleWidth;
    } else if (labelWidth < 0) {
      labelShown = wrap(label);
      labelWidth = textWidth(labelShown);
    }
    // without a box the text is the module; with one, the sample keeps the box from jittering
    size(fitText ? labelWidth : Math.max(labelWidth, sampleWidth), lineHeight());
  }

  /** Colour of the text this frame: the module's text colour unless a subclass colours by value. */
  protected int labelColor() {
    return textColor.get();
  }

  @Override
  protected void draw(float partialTicks, boolean editor) {
    if (style() || sampleWidth < 0 || labelWidth < 0) resize();
    // the editor shows the sample, except when the box is sized to the live text
    boolean sampled = editor && !fitText || label == null;
    String text = sampled ? sampleShown : labelShown;
    float x = (contentWidth() - (sampled ? sampleWidth : labelWidth)) / 2f;
    int color = labelColor();
    if (noShadow && textShadow.get()) {
      // plain text in its box: no shadow for this one line, the setting itself stays as the player set it
      textShadow.set(false);
      try {
        text(text, x, 0, color);
      } finally {
        textShadow.set(true);
      }
    } else {
      text(text, x, 0, color);
    }
  }
}
