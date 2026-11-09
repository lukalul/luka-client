package dev.cbui.hud;

/**
 * Last-writer-wins synchronisation of one value held in two places (CBUI's module and OneConfig's
 * HUD), polled once per tick. Minecraft-free so the plain-Java checks can exercise it.
 */
public final class TwoWaySync {
  public static final int NONE = 0, TO_LOCAL = 1, TO_EXTERNAL = 2;
  private boolean seen;
  private float external, local;

  /**
   * Compares both sides with the values seen on the previous call.
   *
   * @param preferLocalFirst which side wins when the two differ on the very first call
   * @return {@link #TO_LOCAL} when the external value changed (copy it to the local side), {@link
   *     #TO_EXTERNAL} when the local value changed, {@link #NONE} otherwise
   */
  public int step(float externalNow, float localNow, boolean preferLocalFirst) {
    int result = NONE;
    if (!seen) {
      if (!same(externalNow, localNow)) result = preferLocalFirst ? TO_EXTERNAL : TO_LOCAL;
    } else if (!same(externalNow, external)) result = TO_LOCAL;
    else if (!same(localNow, local)) result = TO_EXTERNAL;
    seen = true;
    float settled = result == TO_LOCAL ? externalNow : result == TO_EXTERNAL ? localNow : localNow;
    external = result == NONE ? externalNow : settled;
    local = result == NONE ? localNow : settled;
    return result;
  }

  /** Records the values after the caller applied a clamp or rounding on either side. */
  public void settle(float externalNow, float localNow) {
    seen = true;
    external = externalNow;
    local = localNow;
  }

  private static boolean same(float a, float b) {
    return Math.abs(a - b) < 1e-4f;
  }
}
