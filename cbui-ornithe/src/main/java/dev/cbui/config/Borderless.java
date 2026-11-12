package dev.cbui.config;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;

/**
 * Borderless fullscreen: drops the window frame and stretches the window over the primary monitor (GLFW, via reflection).
 * It follows real fullscreen without the player having to leave it first: switching borderless on while the game is fullscreen leaves
 * fullscreen and then applies it; pressing F11 (or the Video Settings switch) while borderless is on goes to real fullscreen, and leaving
 * fullscreen puts the borderless window back.
 */
public final class Borderless {
  private static int[] saved;
  /** The borderless geometry is on the window right now. */
  private static boolean applied;
  private static boolean want;
  /** Borderless was just switched on: it still has to take effect (after leaving fullscreen if needed). */
  private static boolean pendingEnable;
  private static boolean wasFullscreen;
  private static long lastToggle;
  /** The player chose a normal window with F11 while the setting is on; F11 again brings the borderless window back. */
  private static boolean windowed;
  /** Set while this class itself calls the game's fullscreen toggle, so the F11 hook lets that call through. */
  public static volatile boolean internalToggle;

  private Borderless() {}

  // ---- Vulkan renderer: the window is pylon's SDL window, not GLFW. SDL has borderless fullscreen itself
  // ("fullscreen desktop"): pylon uses it for the game's fullscreen unless its exclusive mode is on. So there borderless
  // means the game's fullscreen in that mode, F11 switches between it and a normal window, and with the setting off
  // F11 gives exclusive fullscreen as in vanilla.
  private static Object pylonDisplay;
  private static Method pylonSetExclusive;
  private static Boolean pylonExclusive;

  /** pylon's window, once it exists; null when the game does not run on pylon. */
  private static Object pylon() {
    if (pylonDisplay != null) return pylonDisplay;
    try {
      Class<?> c = Class.forName("pl.tomgirl.pylon.window.DisplaySdl");
      Object d = c.getMethod("instance").invoke(null);
      if (d == null || !(Boolean) c.getMethod("isCreated").invoke(d)) return null;
      pylonSetExclusive = c.getMethod("setExclusiveFullscreen", boolean.class);
      pylonDisplay = d;
      return d;
    } catch (Throwable notPylon) {
      return null;
    }
  }

  private static void pylonExclusive(Object display, boolean exclusive) throws Exception {
    if (pylonExclusive != null && pylonExclusive == exclusive) return;
    pylonSetExclusive.invoke(display, exclusive);
    pylonExclusive = exclusive;
  }

  private static void pylonToggle(Minecraft mc) {
    internalToggle = true;
    try { mc.toggleFullscreen(); } finally { internalToggle = false; }
  }

  private static void pylonSync(Object display, Minecraft mc) throws Exception {
    pylonExclusive(display, !want);
    // at start-up (the saved setting is applied when the window is made) the game's picture buffer does not exist yet and
    // its fullscreen switch fails halfway ("Couldn't toggle fullscreen"): wait for it, the per-frame tick tries again
    if (want && pendingEnable && mc.getFramebuffer() == null) return;
    if (want && pendingEnable) {
      pendingEnable = false;
      if (!mc.isFullScreen()) pylonToggle(mc);
      applied = true;
    } else if (!want && applied) {
      applied = false;
      // leave the borderless fullscreen this class entered; the next F11 is exclusive again
      if (mc.isFullScreen()) pylonToggle(mc);
    }
  }

  /** The borderless setting changed (menu switch, or the saved setting at start-up). */
  public static void apply(boolean on) {
    want = on;
    pendingEnable = on;
    windowed = false;
    sync();
  }

  /**
   * F11 with the setting on. In real fullscreen it leaves fullscreen (the borderless window returns by itself); otherwise it switches
   * between the borderless window and a normal window. False when the game should handle F11 itself.
   */
  public static boolean userToggle() {
    try {
      Minecraft mc = Minecraft.getMinecraft();
      if (pylon() != null) return false;   // the game's own F11 already switches borderless fullscreen and a window
      if (mc.isFullScreen()) return false;
      if (applied) {
        setBorderless(false);
        applied = false;
        windowed = true;
      } else {
        windowed = false;
        applied = setBorderless(true);
        pendingEnable = !applied;
      }
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  /** Once per frame. */
  public static void tick() {
    sync();
  }

  private static void sync() {
    try {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc == null) return;
      Object display = pylon();
      if (display != null) {
        pylonSync(display, mc);
        return;
      }
      boolean fullscreen = mc.isFullScreen();
      if (!want) {
        if (applied) {
          setBorderless(false);
          applied = false;
        }
        wasFullscreen = fullscreen;
        return;
      }
      if (pendingEnable) {
        if (fullscreen) {
          // leave real fullscreen first; the window is restyled on a later frame once it is windowed
          long now = System.currentTimeMillis();
          if (now - lastToggle > 1000) {
            lastToggle = now;
            internalToggle = true;
            try { mc.toggleFullscreen(); } finally { internalToggle = false; }
          }
          return;
        }
        applied = setBorderless(true);
        pendingEnable = !applied;
        wasFullscreen = false;
        return;
      }
      if (fullscreen != wasFullscreen) {
        wasFullscreen = fullscreen;
        if (fullscreen) {
          // the player chose real fullscreen (F11 or Video Settings): leave it alone, the borderless frame is gone with it
          applied = false;
          saved = null;
        } else {
          windowed = false;
          applied = setBorderless(true);
        }
      }
    } catch (Throwable ignored) {
      // no window yet, or not running on GLFW: try again next frame
    }
  }

  private static boolean setBorderless(boolean on) {
    try {
      long window = (Long) Class.forName("org.lwjgl.opengl.Display").getMethod("getHandle").invoke(null);
      if (window == 0) return false;
      Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
      Method attrib = glfw.getMethod("glfwSetWindowAttrib", long.class, int.class, int.class);
      Method setPos = glfw.getMethod("glfwSetWindowPos", long.class, int.class, int.class);
      Method setSize = glfw.getMethod("glfwSetWindowSize", long.class, int.class, int.class);
      if (on) {
        long monitor = (Long) glfw.getMethod("glfwGetPrimaryMonitor").invoke(null);
        Object mode = glfw.getMethod("glfwGetVideoMode", long.class).invoke(null, monitor);
        int w = (Integer) mode.getClass().getMethod("width").invoke(mode);
        int h = (Integer) mode.getClass().getMethod("height").invoke(mode);
        if (saved == null) saved = windowRect(glfw, window);
        attrib.invoke(null, window, 0x20005, 0); // GLFW_DECORATED
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
          // 1 px taller than the monitor and shifted up 1 px. It still covers the taskbar,
          // but is not an exact monitor match, so Windows/Discord do not treat it as exclusive fullscreen.
          setPos.invoke(null, window, 0, -1);
          setSize.invoke(null, window, w, h + 1);
        } else {
          // Linux has no exclusive fullscreen to avoid: cover the monitor exactly (on Wayland the position is
          // left to the compositor and only the size applies)
          // left to the compositor and only the size applies). The primary monitor need not start at 0,0.
          int[] mx = new int[1], my = new int[1];
          try {
            glfw.getMethod("glfwGetMonitorPos", long.class, int[].class, int[].class).invoke(null, monitor, mx, my);
          } catch (Throwable ignored) {
            mx[0] = 0;
            my[0] = 0;
          }
          setPos.invoke(null, window, mx[0], my[0]);
          setSize.invoke(null, window, w, h);
        }
      } else {
        attrib.invoke(null, window, 0x20005, 1);
        int[] r = saved != null ? saved : new int[] {100, 100, 1280, 720};
        saved = null;
        setSize.invoke(null, window, r[2], r[3]);
        setPos.invoke(null, window, r[0], r[1]);
      }
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static int[] windowRect(Class<?> glfw, long window) {
    try {
      int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
      glfw.getMethod("glfwGetWindowPos", long.class, int[].class, int[].class).invoke(null, window, x, y);
      glfw.getMethod("glfwGetWindowSize", long.class, int[].class, int[].class).invoke(null, window, w, h);
      return new int[] {x[0], y[0], w[0], h[0]};
    } catch (Throwable t) {
      return null;
    }
  }
}
