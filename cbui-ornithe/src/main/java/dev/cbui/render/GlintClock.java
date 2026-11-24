package dev.cbui.render;

import java.lang.reflect.Method;

/**
 * Clock for the enchantment shine. In a SubFrame replay or render it is the recorded time (SubFrameMod.glintMillis),
 * so the shine moves at its real speed in high-frame-rate videos; otherwise the game's wall clock.
 */
public final class GlintClock {
  private static Method subFrame;
  private static boolean looked;

  private GlintClock() {
  }

  public static long now() {
    if (!looked) {
      looked = true;
      try { subFrame = Class.forName("dev.subframe.SubFrameMod").getMethod("glintMillis"); } catch (Throwable noSubFrame) { }
    }
    if (subFrame != null) {
      try { return (Long) subFrame.invoke(null); } catch (Throwable ignored) { }
    }
    return net.minecraft.client.Minecraft.getSystemTime();
  }
}
