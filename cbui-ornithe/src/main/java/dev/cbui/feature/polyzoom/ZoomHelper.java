package dev.cbui.feature.polyzoom;

/** Maintains one independently animated FOV multiplier and its scroll refinement. */
final class ZoomHelper {
  private final PolyZoomModule settings;
  private final boolean alternate;

  private double prevInitial, initial, prevTarget;
  private boolean goingIn = true, justSwapped, zoomingLastTick;
  private Transition active = Transition.LINEAR, inactive = Transition.LINEAR;

  private double prevScroll, scroll;
  private int lastTier;

  private boolean resetting;
  private double resetMultiplier;

  ZoomHelper(PolyZoomModule config, boolean secondary) {
    settings = config;
    alternate = secondary;
  }

  private Transition in() { return alternate ? Transition.LINEAR : Transition.of(settings.zoomInTransition.get()); }
  private Transition out() { return alternate ? Transition.LINEAR : Transition.of(settings.zoomOutTransition.get()); }
  private double timeIn() { return alternate ? settings.secondaryZoomInTime.get() : settings.zoomInTime.get(); }
  private double timeOut() { return alternate ? settings.secondaryZoomOutTime.get() : settings.zoomOutTime.get(); }
  private int zoomAmount() { return Math.max(1, Math.round(alternate ? settings.secondaryZoomAmount.get() : settings.initialZoom.get())); }
  private int maxTiers() { return alternate ? 0 : Math.round(settings.scrollStepCount.get()); }
  private double perStep() { return settings.zoomPerStep.get() / 100.0; }

  /** Scrolling out stops at normal FOV. */
  int minTiers() {
    double stepMultiplier = perStep();
    if (alternate || stepMultiplier <= 1.0) return 0;
    int stepsToNormalFov = (int) Math.ceil(Math.log(zoomAmount()) / Math.log(stepMultiplier));
    return -(int) clamp(stepsToNormalFov, 0, maxTiers());
  }

  private double smoothness() { return alternate ? 1.0 : 0.1 + settings.scrollZoomSmoothness.get() * 0.009; }

  void tick(boolean zooming, int tiers, double dt) {
    if (zooming && !zoomingLastTick) resetting = false;
    prevInitial = initial;
    initial = tickTransition(zooming ? 1.0 : 0.0, initial, dt);
    if (justSwapped) {
      justSwapped = false;
      prevInitial = active.inverse(inactive.apply(prevInitial));
    }
    if (!initialIsSmooth()) prevInitial = initial;
    zoomingLastTick = zooming;

    if (Math.abs(tiers) > Math.abs(lastTier)) resetting = false;
    prevScroll = scroll;
    scroll = tickSmooth(maxTiers() > 0 ? (double) tiers / maxTiers() : 0.0, scroll, dt);
    if (!initialIsSmooth()) prevInitial = initial;
    lastTier = tiers;
  }

  double getZoomDivisor(float tickDelta) {
    double t = initialIsSmooth() ? active.apply(lerp(tickDelta, prevInitial, initial)) : initial;
    double base = 1 / lerp(t, 1.0, resetting ? resetMultiplier : 1.0 / zoomAmount());
    double scrollT = resetting ? 0.0 : (smoothness() != 1.0 ? lerp(tickDelta, prevScroll, scroll) : scroll);

    double divisor = base * Math.pow(perStep(), scrollT * maxTiers());
    divisor = clamp(divisor, 1.0, 500.0);

    if (initial == 0.0 && scroll == 0.0) resetting = false;
    if (!resetting) resetMultiplier = 1 / divisor;
    return divisor;
  }

  void reset() {
    if (!resetting && scroll != 0.0) {
      resetting = true;
      scroll = prevScroll = 0.0;
    }
  }

  private double tickTransition(double target, double current, double dt) {
    if (target != current) {
      boolean up = target > current;
      active = up ? in() : out().opposite();
      inactive = up ? out().opposite() : in();
      if ((up ? prevTarget < target : prevTarget > target) && active.hasInverse()) {
        justSwapped = true;
        current = active.inverse(inactive.apply(current));
      }
    }
    prevTarget = target;
    if (active == Transition.INSTANT) return target;

    if (target > current) {
      goingIn = true;
      return Math.min(current + dt / timeIn(), target);
    }
    if (target < current) {
      goingIn = false;
      return Math.max(current - dt / timeOut(), target);
    }
    goingIn = true;
    return target;
  }

  private boolean initialIsSmooth() {
    if (justSwapped || active == Transition.INSTANT) return false;
    return (goingIn ? timeIn() : timeOut()) > 0.0;
  }

  private double tickSmooth(double target, double current, double dt) {
    double s = smoothness();
    if (s == 1.0) return target;
    double step = Math.abs(target - current) * s / 0.05 * dt;
    return target > current ? Math.min(current + step, target) : Math.max(current - step, target);
  }

  static double clamp(double v, double min, double max) { return v < min ? min : (v > max ? max : v); }

  static double lerp(double delta, double start, double end) { return start + delta * (end - start); }
}
