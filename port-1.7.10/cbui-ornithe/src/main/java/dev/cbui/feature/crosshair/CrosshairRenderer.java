package dev.cbui.feature.crosshair;

/**
 * Fixed entry point that subframe finds by reflection (and the obfuscation keep-rules name). It only hands over to
 * the crosshair module and holds no logic of its own.
 */
public final class CrosshairRenderer {
  private CrosshairRenderer() {}

  /**
   * Draws luka's crosshair at the screen centre in the current 2D projection when the module wants to. Returns true
   * when luka took charge (drew it or hid it on purpose), false when the caller should draw its own. Never throws.
   */
  public static boolean render(boolean vanillaWouldShow) {
    try {
      return CrosshairModule.takeOver(vanillaWouldShow, 0F);
    } catch (Throwable error) {
      return false;
    }
  }
}
