package dev.cbui.hud.module;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;

/**
 * Shared base for single-line text modules. The label is replaced on the tick; the box is at least
 * as wide as the sample text (so it does not jitter as digits change) and is resized only when the
 * label's width changes.
 */
abstract class LabelModule extends AbstractHudModule {
  private final String sample;
  private String label;
  private int measured = -1, measuredSample = -1;

  LabelModule(String name, boolean enabled, Anchor anchor, float x, float y, String sample) {
    super(name, enabled, anchor, x, y, 48, 11);
    this.sample = sample;
  }

  LabelModule(String name, Anchor anchor, float x, float y, String sample) {
    this(name, false, anchor, x, y, sample);
  }

  private int lastInt = Integer.MIN_VALUE;
  private String lastSuffix;

  /** {@code value + suffix}, rebuilt only when the number changes (no per-tick string building). */
  protected final void label(int value, String suffix) {
    if (value == lastInt && suffix == lastSuffix && measured >= 0) return;
    lastInt = value;
    lastSuffix = suffix;
    label(value + suffix);
  }

  protected final void label(String text) {
    if (text.equals(label) && measured >= 0) return;
    label = text;
    resize();
  }

  /** Recomputes the box; also call when a setting changes the text's width. */
  protected final void resize() {
    if (measuredSample < 0) measuredSample = textWidth(sample);
    measured = label == null ? measuredSample : textWidth(label);
    size(Math.max(measured, measuredSample), lineHeight());
  }

  protected String shown(boolean editor) {
    return editor || label == null ? sample : label;
  }

  @Override
  protected void draw(float partialTicks, boolean editor) {
    if (measured < 0) resize();
    centered(shown(editor), 0);
  }
}
