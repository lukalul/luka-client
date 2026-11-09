package dev.cbui.hud;

import java.util.Arrays;

/** Pure-logic cooldown timers measured in game ticks (no Minecraft types, unit-testable). Fixed slots, no allocation after construction. */
public final class TimerBook {
  private final long[] end;

  public TimerBook(int slots) {
    end = new long[slots];
    Arrays.fill(end, Long.MIN_VALUE);
  }

  public void start(int slot, long now, int durationTicks) { end[slot] = now + Math.max(0, durationTicks); }

  public void clear(int slot) { end[slot] = Long.MIN_VALUE; }

  public void clearAll() { Arrays.fill(end, Long.MIN_VALUE); }

  /** Ticks left (0 when inactive or expired). */
  public int remaining(int slot, long now) {
    if (end[slot] == Long.MIN_VALUE) return 0;
    long left = end[slot] - now;
    return left <= 0 ? 0 : (int) left;
  }

  public boolean active(int slot, long now) { return remaining(slot, now) > 0; }

  public int activeCount(long now) {
    int n = 0;
    for (int i = 0; i < end.length; i++) if (active(i, now)) n++;
    return n;
  }

  /** Display seconds: one decimal under 10 s ("9.5s"), whole seconds rounded up from there ("12s"). */
  public static String format(int remainingTicks) {
    if (remainingTicks < 200) {
      int tenths = (remainingTicks + 1) / 2;
      return (tenths / 10) + "." + (tenths % 10) + "s";
    }
    return ((remainingTicks + 19) / 20) + "s";
  }
}
