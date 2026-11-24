package dev.cbui.feature.hotbarwheel;

/**
 * Where the hotbar selection lands after one mouse-wheel event. Kept free of game classes so the plain-Java checks
 * can exercise every combination of options.
 */
public final class WheelStep {
  /** Number of hotbar slots (indices 0..8). */
  public static final int SLOTS = 9;

  private WheelStep() {}

  /**
   * @param slot    the selected slot before the event, 0..8
   * @param delta   the raw wheel delta; positive is wheel up, which normally walks towards slot 0
   * @param locked  the wheel never changes the slot
   * @param flipped wheel up walks towards slot 8 instead
   * @param clamped stop at slot 0 / 8 instead of wrapping to the other end
   * @return the slot after the event; at most one slot away from {@code slot}, whatever the size of {@code delta}
   */
  public static int apply(int slot, int delta, boolean locked, boolean flipped, boolean clamped) {
    if (locked || delta == 0) return slot;
    int step = delta > 0 ? -1 : 1;
    if (flipped) step = -step;
    int target = slot + step;
    if (clamped) {
      if (target < 0) return 0;
      if (target >= SLOTS) return SLOTS - 1;
      return target;
    }
    return ((target % SLOTS) + SLOTS) % SLOTS;
  }
}
