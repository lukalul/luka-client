package dev.cbui.feature.polyzoom;

import java.util.Locale;

/** Converts the saved animation style into a normalized camera-motion curve. */
enum Transition {
  INSTANT, LINEAR, EASE_IN_SINE, EASE_OUT_SINE, EASE_IN_OUT_SINE, EASE_IN_QUAD, EASE_OUT_QUAD, EASE_IN_OUT_QUAD,
  EASE_IN_CUBIC, EASE_OUT_CUBIC, EASE_IN_OUT_CUBIC, EASE_IN_EXP, EASE_OUT_EXP, EASE_IN_OUT_EXP;

  /** Parses the option string; unknown names fall back to ease_out_exp rather than break rendering. */
  static Transition of(String name) {
    try { return valueOf(name.toUpperCase(Locale.ROOT)); }
    catch (Throwable e) { return EASE_OUT_EXP; }
  }

  double apply(double t) {
    t = Math.max(0.0, Math.min(1.0, t));
    switch (this) {
      case EASE_IN_SINE: return 1 - Math.cos(t * Math.PI / 2);
      case EASE_OUT_SINE: return Math.sin(t * Math.PI / 2);
      case EASE_IN_OUT_SINE: return -(Math.cos(Math.PI * t) - 1) / 2;
      case EASE_IN_QUAD: return t * t;
      case EASE_OUT_QUAD: return t * (2 - t);
      case EASE_IN_OUT_QUAD: return t < 0.5 ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t);
      case EASE_IN_CUBIC: return t * t * t;
      case EASE_OUT_CUBIC: { double u = 1 - t; return 1 - u * u * u; }
      case EASE_IN_OUT_CUBIC: { double u = t < .5 ? t : 1 - t; double v = 4 * u * u * u; return t < .5 ? v : 1 - v; }
      case EASE_IN_EXP: return exp(10 * t);
      case EASE_OUT_EXP: return 1 - exp(10 - 10 * t);
      case EASE_IN_OUT_EXP: return t < 0.5 ? exp(20 * t) / 2 : 1 - exp(20 - 20 * t) / 2;
      default: return t;
    }
  }

  boolean hasInverse() {
    switch (this) {
      case EASE_IN_SINE: case EASE_OUT_SINE: case EASE_IN_QUAD: case EASE_OUT_QUAD:
      case EASE_IN_CUBIC: case EASE_OUT_CUBIC: case EASE_IN_EXP: case EASE_OUT_EXP: return true;
      default: return false;
    }
  }

  double inverse(double x) {
    switch (this) {
      case EASE_IN_SINE: return Math.acos(1 - x) * 2 / Math.PI;
      case EASE_OUT_SINE: return Math.asin(x) * 2 / Math.PI;
      case EASE_IN_QUAD: return Math.sqrt(x);
      case EASE_OUT_QUAD: return 1 - Math.sqrt(1 - x);
      case EASE_IN_CUBIC: return Math.pow(x, 1 / 3.0);
      case EASE_OUT_CUBIC: return 1 - Math.pow(1 - x, 1 / 3.0);
      case EASE_IN_EXP: return log(1023 * x + 1);
      case EASE_OUT_EXP: return 1 - log(1023 * (1 - x) + 1);
      default: throw new UnsupportedOperationException();
    }
  }

  /** The matching curve for the reverse direction (ease_in <-> ease_out); symmetric and plain curves map to themselves. */
  Transition opposite() {
    String n = name();
    if (n.startsWith("EASE_IN_OUT")) return this;
    if (n.startsWith("EASE_IN_")) return values()[ordinal() + 1];
    if (n.startsWith("EASE_OUT_")) return values()[ordinal() - 1];
    return this;
  }

  private static double exp(double e) { return (Math.pow(2, e) - 1) / 1023; }
  private static double log(double v) { return Math.log(v) / (10 * Math.log(2)); }
}
