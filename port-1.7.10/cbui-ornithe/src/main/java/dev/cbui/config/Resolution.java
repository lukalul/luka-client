package dev.cbui.config;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/**
 * The Resolution row in Video Settings, on the Vulkan renderer's window (pylon).
 * <ul>
 *   <li>Exclusive fullscreen (Borderless off): the monitor switches to that mode, as Argentum and OptiFine do it.</li>
 *   <li>Borderless: the monitor keeps its own mode. The window reports the chosen size to the game instead
 *       ({@code PylonDisplayMixin}), so the game, its menus and the mouse all work at that size, and the renderer stretches the
 *       picture over the screen. It looks like the same resolution in exclusive fullscreen.</li>
 * </ul>
 * A normal window is not affected. "Native" ({@link GlobalOptions#resolution} empty) is the monitor's own resolution.
 */
public final class Resolution {
  /** Size the borderless window reports to the game, 0 for its real size. Read by PylonDisplayMixin. */
  public static volatile int borderlessWidth, borderlessHeight;

  private static Object pylon;
  private static boolean pylonChecked;
  private static String appliedExclusive;
  private static Method isExclusive;

  private Resolution() {}

  /** True when the game runs on pylon's window (the Vulkan renderer), where this setting works. */
  public static boolean supported() {
    return pylon() != null || isPylonLoaded();
  }

  private static boolean isPylonLoaded() {
    try { return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("pylon"); } catch (Throwable t) { return false; }
  }

  private static Object pylon() {
    if (pylon != null) return pylon;
    try {
      Class<?> c = Class.forName("pl.tomgirl.pylon.window.DisplaySdl");
      Object d = c.getMethod("instance").invoke(null);
      if (d == null || !(Boolean) c.getMethod("isCreated").invoke(d)) return null;
      pylon = d;
      return d;
    } catch (Throwable notPylon) {
      return null;
    }
  }

  /** "Native", then every size the monitor offers, largest first. */
  public static String[] choices() {
    List<String> out = new ArrayList<>();
    out.add("Native");
    try {
      DisplayMode[] modes = Display.getAvailableDisplayModes();
      List<int[]> sizes = new ArrayList<>();
      for (DisplayMode m : modes) {
        if (m.getWidth() < 640 || m.getHeight() < 480) continue;
        boolean seen = false;
        for (int[] s : sizes) if (s[0] == m.getWidth() && s[1] == m.getHeight()) { seen = true; break; }
        if (!seen) sizes.add(new int[] {m.getWidth(), m.getHeight()});
      }
      sizes.sort((a, b) -> a[0] != b[0] ? b[0] - a[0] : b[1] - a[1]);
      for (int[] s : sizes) out.add(s[0] + "x" + s[1]);
    } catch (Throwable ignored) {
      // no list of modes: only Native
    }
    String saved = GlobalOptions.resolution;
    if (saved != null && !saved.isEmpty() && !out.contains(saved)) out.add(saved);
    return out.toArray(new String[0]);
  }

  /** The row's index for the saved setting. */
  public static int index(String[] choices) {
    String saved = GlobalOptions.resolution;
    if (saved == null || saved.isEmpty()) return 0;
    for (int i = 0; i < choices.length; i++) if (choices[i].equals(saved)) return i;
    return 0;
  }

  /** The row was changed. */
  public static void choose(String choice) {
    GlobalOptions.resolution = choice == null || "Native".equals(choice) ? "" : choice;
    frame();
  }

  private static int[] parse(String text) {
    if (text == null || text.isEmpty()) return null;
    int x = text.indexOf('x');
    if (x <= 0) return null;
    try {
      int w = Integer.parseInt(text.substring(0, x).trim()), h = Integer.parseInt(text.substring(x + 1).trim());
      return w >= 320 && h >= 240 ? new int[] {w, h} : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Every frame (and when the setting changes): keeps the window and the game at the chosen resolution. */
  public static void frame() {
    try {
      Minecraft mc = Minecraft.getMinecraft();
      Object display = pylon();
      if (mc == null || display == null) return;
      Class<?> c = display.getClass();
      if (isExclusive == null) isExclusive = c.getMethod("isExclusiveFullscreen");
      int[] size = parse(GlobalOptions.resolution);
      boolean exclusive = (Boolean) isExclusive.invoke(display);
      boolean fullscreen = mc.isFullScreen();

      // exclusive fullscreen: the monitor's mode (pylon picks the highest refresh rate for that size)
      String wantExclusive = size == null ? "" : GlobalOptions.resolution;
      if (!wantExclusive.equals(appliedExclusive)) {
        Method setMode = c.getMethod("setFullscreenDisplayMode", DisplayMode.class);
        setMode.invoke(display, size == null ? null : new DisplayMode(size[0], size[1]));
        appliedExclusive = wantExclusive;
        boolean modeDiffers = size == null || Display.getWidth() != size[0] || Display.getHeight() != size[1];
        if (exclusive && fullscreen && modeDiffers) {
          // enter fullscreen again so the new mode takes effect (also at start-up, when the game went fullscreen before
          // this setting was read)
          Display.setFullscreen(false);
          Display.setFullscreen(true);
        }
      }

      // borderless: the window reports the chosen size while it is borderless fullscreen (PylonDisplayMixin checks that
      // itself, so a switch to a normal window never sees the chosen size); the renderer stretches it over the screen
      borderlessWidth = size == null ? 0 : size[0];
      borderlessHeight = size == null ? 0 : size[1];

      // in fullscreen the game does not follow size changes by itself
      if (fullscreen) {
        int w = Display.getWidth(), h = Display.getHeight();
        if (w > 0 && h > 0 && (w != mc.displayWidth || h != mc.displayHeight))
          ((dev.cbui.mixin.MinecraftResizeInvoker) mc).cbui$resize(w, h);
      }
    } catch (Throwable ignored) {
      // window not ready: next frame
    }
  }
}
