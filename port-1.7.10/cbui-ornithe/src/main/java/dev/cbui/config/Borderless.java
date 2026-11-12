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
    if (want && pendingEnable) {
      pendingEnable = false;
      if (!mc.isFullScreen()) pylonToggle(mc);
      applied = true;
    } else if (!want && applied) {
      applied = false;
      // leave the borderless fullscreen this class entered; the next F11 is exclusive again
      if (mc.isFullScreen()) pylonToggle(mc);
    }
    pylonNudge(display, mc);
  }


  // ---- Vulkan window on Windows: 1 px taller than the monitor. SDL's borderless fullscreen covers the monitor exactly,
  // and Windows and screen-capture tools take a window like that for exclusive fullscreen: Discord's whole-screen share
  // then captures through a path that holds the game's frames back (FPS drops to a fraction). Shifted up 1 px and 1 px
  // taller it still covers the whole screen and the taskbar, like the OpenGL window's borderless, and is not taken for
  // exclusive fullscreen. Off with -Dluka.borderless.exact=true.
  private static final boolean NUDGE = System.getProperty("os.name", "").toLowerCase().contains("win")
      && !Boolean.getBoolean("luka.borderless.exact");
  private static Method nudgeIsExclusive, nudgeHandle, nudgeProps, nudgePointer, nudgeGetRect, nudgeSetPos, nudgeMonitor,
      nudgeMonitorInfo, nudgeRectCalloc, nudgeInfoCalloc;
  private static long nudgeLast, nudgeWindowStart;
  private static int nudgeCount;
  private static boolean nudgeOff;

  private static void pylonNudge(Object display, Minecraft mc) {
    if (!NUDGE || nudgeOff || !want) return;
    long now = System.nanoTime();
    if (now - nudgeLast < 250_000_000L) return;
    nudgeLast = now;
    Object rect = null, info = null;
    try {
      if (nudgeHandle == null) {
        Class<?> c = display.getClass();
        nudgeIsExclusive = c.getMethod("isExclusiveFullscreen");
        nudgeHandle = c.getMethod("getHandle");
        nudgeProps = Class.forName("org.lwjgl.sdl.SDLVideo").getMethod("SDL_GetWindowProperties", long.class);
        nudgePointer = Class.forName("org.lwjgl.sdl.SDLProperties").getMethod("SDL_GetPointerProperty", int.class, CharSequence.class, long.class);
        Class<?> user32 = Class.forName("org.lwjgl.system.windows.User32");
        Class<?> rectClass = Class.forName("org.lwjgl.system.windows.RECT");
        Class<?> infoClass = Class.forName("org.lwjgl.system.windows.MONITORINFOEX");
        nudgeGetRect = user32.getMethod("GetWindowRect", java.nio.IntBuffer.class, long.class, rectClass);
        nudgeSetPos = user32.getMethod("SetWindowPos", java.nio.IntBuffer.class, long.class, long.class, int.class, int.class, int.class, int.class, int.class);
        nudgeMonitor = user32.getMethod("MonitorFromWindow", long.class, int.class);
        nudgeMonitorInfo = user32.getMethod("GetMonitorInfo", long.class, infoClass);
        nudgeRectCalloc = rectClass.getMethod("calloc");
        nudgeInfoCalloc = infoClass.getMethod("calloc");
      }
      if (!mc.isFullScreen() || (Boolean) nudgeIsExclusive.invoke(display)) return;
      long handle = (Long) nudgeHandle.invoke(display);
      if (handle == 0) return;
      int props = (Integer) nudgeProps.invoke(null, handle);
      long hwnd = (Long) nudgePointer.invoke(null, props, "SDL.window.win32.hwnd", 0L);
      if (hwnd == 0) return;
      long monitor = (Long) nudgeMonitor.invoke(null, hwnd, 2); // MONITOR_DEFAULTTONEAREST
      rect = nudgeRectCalloc.invoke(null);
      info = nudgeInfoCalloc.invoke(null);
      info.getClass().getMethod("cbSize", int.class).invoke(info, info.getClass().getField("SIZEOF").getInt(null));
      if (!(Boolean) nudgeMonitorInfo.invoke(null, monitor, info)) return;
      if (!(Boolean) nudgeGetRect.invoke(null, null, hwnd, rect)) return;
      Object m = info.getClass().getMethod("rcMonitor").invoke(info);
      int ml = side(m, "left"), mt = side(m, "top"), mr = side(m, "right"), mb = side(m, "bottom");
      int wl = side(rect, "left"), wt = side(rect, "top"), wr = side(rect, "right"), wb = side(rect, "bottom");
      // only a window that covers the monitor exactly is changed: anything else is not SDL's borderless fullscreen
      if (wl != ml || wt != mt || wr != mr || wb != mb) return;
      if (now - nudgeWindowStart > 10_000_000_000L) {
        nudgeWindowStart = now;
        nudgeCount = 0;
      }
      if (++nudgeCount > 8) {
        // something puts the exact size back each time: stop instead of fighting over the window
        nudgeOff = true;
        org.apache.logging.log4j.LogManager.getLogger("luka").info("Borderless: the window keeps returning to the exact monitor size; leaving it");
        return;
      }
      // SWP_NOZORDER | SWP_NOACTIVATE | SWP_NOOWNERZORDER
      nudgeSetPos.invoke(null, null, hwnd, 0L, ml, mt - 1, mr - ml, mb - mt + 1, 0x0004 | 0x0010 | 0x0200);
    } catch (Throwable ignored) {
      // not on pylon's SDL window, or LWJGL without these calls: leave the window as it is
      nudgeOff = true;
    } finally {
      free(rect);
      free(info);
    }
  }

  private static int side(Object rect, String name) throws Exception {
    return (Integer) rect.getClass().getMethod(name).invoke(rect);
  }

  private static void free(Object struct) {
    if (struct == null) return;
    try { struct.getClass().getMethod("free").invoke(struct); } catch (Throwable ignored) { }
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
