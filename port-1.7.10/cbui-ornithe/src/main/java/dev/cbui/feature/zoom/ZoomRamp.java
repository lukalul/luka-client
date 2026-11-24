package dev.cbui.feature.zoom;

/**
 * One animated value between 0 (no zoom) and 1 (full zoom), driven by the client tick counter. A new target starts from
 * whatever the value is at that moment, so turning round half way never jumps.
 */
final class ZoomRamp {
  /** Curve names in menu order; the index is what {@link #shape} works with. */
  static final String[] CURVES = {
      "Instant", "Linear",
      "Sine in", "Sine out", "Sine in-out",
      "Quad in", "Quad out", "Quad in-out",
      "Cubic in", "Cubic out", "Cubic in-out",
      "Expo in", "Expo out", "Expo in-out"};

  private float from, to;
  private long startTick;
  private float lengthTicks;
  private int curve = 1;

  /** Value at tick {@code tick} plus {@code partial} of the next one. */
  float at(long tick, float partial) {
    if (lengthTicks <= 0f) return to;
    float progress = (tick - startTick + partial) / lengthTicks;
    if (progress >= 1f) return to;
    if (progress <= 0f) return from;
    return from + (to - from) * shape(curve, progress);
  }

  float target() {
    return to;
  }

  boolean settled(long tick) {
    return lengthTicks <= 0f || tick - startTick >= lengthTicks;
  }

  /** Heads for {@code goal}; a full 0-to-1 trip takes {@code seconds}, a shorter one proportionally less. */
  void aim(long tick, float goal, float seconds, String curveName) {
    float now = at(tick, 0f);
    int shapeIndex = indexOf(curveName);
    from = now;
    to = goal;
    startTick = tick;
    curve = shapeIndex;
    float span = Math.abs(goal - now);
    lengthTicks = shapeIndex == 0 ? 0f : seconds * 20f * span;
    if (lengthTicks < 0.5f) lengthTicks = 0f;
  }

  /** Drops any motion and sits at {@code value}. */
  void jump(float value) {
    from = to = value;
    lengthTicks = 0f;
  }

  private static int indexOf(String name) {
    for (int i = 0; i < CURVES.length; i++) if (CURVES[i].equals(name)) return i;
    return 1;
  }

  /** Maps linear progress t (0..1) through the chosen easing curve. */
  static float shape(int curve, float t) {
    double x = t;
    double y;
    if (curve == 0) y = 1;
    else if (curve == 1) y = x;
    else if (curve == 2) y = 1 - Math.cos(x * Math.PI / 2);
    else if (curve == 3) y = Math.sin(x * Math.PI / 2);
    else if (curve == 4) y = (1 - Math.cos(Math.PI * x)) / 2;
    else if (curve == 5) y = x * x;
    else if (curve == 6) y = 1 - (1 - x) * (1 - x);
    else if (curve == 7) y = x < 0.5 ? 2 * x * x : 1 - Math.pow(2 - 2 * x, 2) / 2;
    else if (curve == 8) y = x * x * x;
    else if (curve == 9) y = 1 - Math.pow(1 - x, 3);
    else if (curve == 10) y = x < 0.5 ? 4 * x * x * x : 1 - Math.pow(2 - 2 * x, 3) / 2;
    else if (curve == 11) y = expoIn(x);
    else if (curve == 12) y = 1 - expoIn(1 - x);
    else if (curve == 13) y = x < 0.5 ? expoIn(2 * x) / 2 : 1 - expoIn(2 - 2 * x) / 2;
    else y = x;
    return (float) y;
  }

  /** Exponential rise, pinned to 0 and 1 at the ends. */
  private static double expoIn(double x) {
    return x <= 0 ? 0 : x >= 1 ? 1 : Math.pow(2, 10 * x - 10);
  }
}
