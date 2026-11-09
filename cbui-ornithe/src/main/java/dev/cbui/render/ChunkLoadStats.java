package dev.cbui.render;

import net.minecraft.client.Minecraft;

/** Counters for measuring chunk loading (read by the test rig through callstatic; costs two field writes per shown chunk). */
public final class ChunkLoadStats {
  private static volatile long start;
  /** When the most recent finished chunk was taken off Celeritas's results queue. */
  public static volatile long lastShown;
  public static volatile int shown;

  private ChunkLoadStats() {}

  private static java.lang.reflect.Method output;
  private static boolean outputBroken;

  /** Called by the Celeritas mixin for each finished job taken this frame; counts chunk builds, not translucency re-sorts. */
  public static void onShown(Object result) {
    if (!outputBroken) {
      try {
        if (output == null) output = result.getClass().getMethod("output");
        Object out = output.invoke(result);
        if (out == null || !out.getClass().getSimpleName().contains("BuildOutput")) return;
      } catch (Throwable t) {
        outputBroken = true;
      }
    }
    shown++;
    lastShown = System.nanoTime();
  }

  /** Test rig: throws away every chunk mesh and rebuilds them all (what joining a server does to the renderer). */
  public static String reload() {
    shown = 0;
    lastShown = 0;
    start = System.nanoTime();
    Minecraft.getMinecraft().renderGlobal.loadRenderers();
    return "reloaded";
  }

  /** Test rig: resets the counters before a teleport. */
  public static String mark() {
    shown = 0;
    lastShown = 0;
    start = System.nanoTime();
    return "marked";
  }

  /** Test rig: chunks shown since the last reload/mark, and how long after it the last one appeared. */
  public static String report() {
    long last = lastShown;
    double ms = last == 0 ? 0 : (last - start) / 1e6;
    return "shown=" + shown + " lastShownMs=" + String.format(java.util.Locale.ROOT, "%.0f", ms);
  }
}
