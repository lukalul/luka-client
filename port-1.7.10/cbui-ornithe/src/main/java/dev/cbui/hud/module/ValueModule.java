package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;

/**
 * A label that shows one number (FPS, CPS, ping, ...). The module hands over the number and its name separately; the
 * player's format puts them together ({value} and {label}), and the number can hide the module or pick the text colour.
 * The text is rebuilt only when the number, its text or the format changes; the hide and colour checks are plain
 * comparisons, so drawing builds nothing.
 */
abstract class ValueModule extends LabelModule {
  static final String HIDE_OFF = "Off", HIDE_AT = "At", HIDE_ABOVE = "Above", HIDE_BELOW = "Below";
  static final int RED = 0xFFFF5555, GREEN = 0xFF55FF55, ORANGE = 0xFFFFAA00;
  private static final String DEFAULT_FORMAT = "{value} {label}";
  /** Numbers this close count as equal (slider values are floats; the numbers shown have at most one decimal). */
  private static final double EPS = 1e-3;

  /** Slider range for the number, and the defaults of the colour rule. */
  static final class Range {
    final float max, step, below, atOrAbove;
    final int belowColor, aboveColor;

    Range(float max, float step, float below, int belowColor, float atOrAbove, int aboveColor) {
      this.max = max;
      this.step = step;
      this.below = below;
      this.belowColor = belowColor;
      this.atOrAbove = atOrAbove;
      this.aboveColor = aboveColor;
    }
  }

  private final String unit, sampleValue;
  private final Setting.Text format;
  private final Setting.Choice hideWhen;
  private final Setting.Slider hideValue;
  private final Setting.Toggle colorByValue;
  private final Setting.Slider below, atOrAbove;
  private final Setting.Color belowColor, aboveColor;

  /** The number the rules use (NaN: none, e.g. no ping in singleplayer) and its text for {value}. */
  private double number = Double.NaN;
  private String valueText;
  private int lastInt = Integer.MIN_VALUE;
  /** The format the label and the sample were built with (compared by reference: it only changes when the player edits it). */
  private String builtFormat, sampleFormat;

  /**
   * @param unit the name of the number for {label}, like "FPS"
   * @param sampleValue a typical number, so the box is wide enough before the first tick and in the HUD editor
   */
  ValueModule(String name, boolean enabled, Anchor anchor, float x, float y, String unit, String sampleValue, Range range) {
    super(name, enabled, anchor, x, y, sampleValue + " " + unit);
    this.unit = unit;
    this.sampleValue = sampleValue;
    format = grouped("Text", adv(new Setting.Text("Format", DEFAULT_FORMAT,
        "How the text reads: {value} is the number, {label} is \"" + unit + "\". Empty: " + DEFAULT_FORMAT)));
    hideWhen = grouped("Hide", adv(Setting.tip(new Setting.Choice("Hide when", HIDE_OFF, HIDE_OFF, HIDE_AT, HIDE_ABOVE, HIDE_BELOW),
        "Hide the module when the number is at, above or below Hide value")));
    hideValue = grouped("Hide", adv(new Setting.Slider("Hide value", 0, 0, range.max, range.step, "The number Hide when compares with")));
    colorByValue = grouped("Color by value", adv(new Setting.Toggle("Color by value", false,
        "Colour the text by the number: one colour below a value, another at or above a second value")));
    below = grouped("Color by value", adv(new Setting.Slider("Below", range.below, 0, range.max, range.step,
        "Numbers under this use Below color")));
    belowColor = grouped("Color by value", adv(new Setting.Color("Below color", range.belowColor, "Text colour for numbers under Below")));
    atOrAbove = grouped("Color by value", adv(new Setting.Slider("At or above", range.atOrAbove, 0, range.max, range.step,
        "Numbers from this up use At or above color")));
    aboveColor = grouped("Color by value", adv(new Setting.Color("At or above color", range.aboveColor,
        "Text colour for numbers at or above At or above")));
  }

  /** Shows a whole number. Cheap to call every tick: nothing is built unless the number or the format changed. */
  protected final void value(int n) {
    if (n == lastInt && valueText != null && format.get() == builtFormat) return;
    lastInt = n;
    number = n;
    valueText = Integer.toString(n);
    rebuild();
  }

  /** Shows {@code text} for {value}; {@code n} is the number the hide and colour rules use (NaN: none). */
  protected final void value(double n, String text) {
    number = n;
    lastInt = Integer.MIN_VALUE;
    if (text.equals(valueText) && format.get() == builtFormat) return;
    valueText = text;
    rebuild();
  }

  /** No number right now (shows {@code text}, like "--", and the hide and colour rules are off). */
  protected final void noValue(String text) {
    value(Double.NaN, text);
  }

  private void rebuild() {
    String f = format.get();
    builtFormat = f;
    String pattern = f.trim().isEmpty() ? DEFAULT_FORMAT : f;
    if (f != sampleFormat) {
      sampleFormat = f;
      sample(fill(pattern, sampleValue));
    }
    if (valueText != null) label(fill(pattern, valueText));
  }

  private String fill(String pattern, String value) {
    StringBuilder out = new StringBuilder(pattern.length() + 16);
    for (int i = 0; i < pattern.length(); ) {
      if (pattern.startsWith("{value}", i)) {
        out.append(value);
        i += 7;
      } else if (pattern.startsWith("{label}", i)) {
        out.append(unit);
        i += 7;
      } else {
        out.append(pattern.charAt(i++));
      }
    }
    return out.toString();
  }

  @Override
  protected boolean nothingToShow() {
    String when = hideWhen.get();
    if (HIDE_OFF.equals(when) || Double.isNaN(number)) return false;
    double x = hideValue.get();
    if (HIDE_AT.equals(when)) return Math.abs(number - x) < EPS;
    if (HIDE_ABOVE.equals(when)) return number > x + EPS;
    return HIDE_BELOW.equals(when) && number < x - EPS;
  }

  @Override
  protected int labelColor() {
    if (!colorByValue.get() || Double.isNaN(number)) return textColor.get();
    if (number < below.get() - EPS) return belowColor.get();
    if (number >= atOrAbove.get() - EPS) return aboveColor.get();
    return textColor.get();
  }

  @Override
  protected void draw(float partialTicks, boolean editor) {
    // the format was edited in the menu (or loaded): rebuild once, before measuring
    if (format.get() != builtFormat) rebuild();
    super.draw(partialTicks, editor);
  }
}
