package dev.cbui.gui;

/** Small frame-rate-independent fade (no external code). */
public final class UiFade {
  private final long duration;
  private float value;
  private long last;

  public UiFade(long duration) {
    this.duration = Math.max(1, duration);
  }

  public float update(boolean forward, long now) {
    if (last == 0) last = now;
    float step = Math.min(1f, (now - last) / (float) duration);
    last = now;
    value = forward ? Math.min(1f, value + step) : Math.max(0f, value - step);
    return value * value * (3f - 2f * value);
  }

  public float value() {
    return value;
  }

  public void reset() {
    value = 0;
    last = 0;
  }
}
