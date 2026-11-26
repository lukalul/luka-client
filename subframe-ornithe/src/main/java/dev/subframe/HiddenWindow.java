package dev.subframe;

import java.lang.reflect.Method;

/**
 * Minimises the game window for the launcher's replay renders on Linux ({@code -Dsubframe.hideWindow=true}). On
 * Windows the launcher runs the game on a hidden desktop instead (luka-hidden.exe), which Linux has no equivalent of.
 * A render draws into its own framebuffer and keeps its full speed while the window is minimised, as on Windows.
 * The window is minimised once, as soon as it exists: a player who brings it back keeps it.
 * Normal renderer on 1.8.9 (legacy-lwjgl3, a GLFW window): hidden outright (no window, no taskbar entry; drawing goes on).
 * Normal renderer with LWJGL 2 (1.7.10): its own X11 iconify call (also under XWayland). Vulkan renderer: pylon's SDL window.
 */
final class HiddenWindow {
    static final String PROPERTY = "subframe.hideWindow";
    private static final boolean WANTED = Boolean.getBoolean(PROPERTY);
    private static boolean done;
    private static int tries;

    private HiddenWindow() {
    }

    /** Called from the splash screen and every frame; does nothing once the window was minimised (or it gave up). */
    static void poll() {
        if (!WANTED || done) {
            return;
        }
        try {
            if (hideGlfw()) {
                done = true;
                SubFrameMod.LOG.info("[render] game window hidden for the render");
            } else if (minimiseSdl() || minimiseLwjgl2()) {
                done = true;
                SubFrameMod.LOG.info("[render] game window minimised for the render");
            } else if (++tries > 600) {
                done = true;
                SubFrameMod.LOG.warn("[render] could not minimise the game window: no window found");
            }
        } catch (Throwable t) {
            done = true;
            SubFrameMod.LOG.warn("[render] could not minimise the game window: " + t);
        }
    }

    /**
     * legacy-lwjgl3's window (a GLFW window; its Display class keeps the handle in a static field): hidden with GLFW.
     * False when the game does not run on it, or the window is not created yet.
     */
    private static boolean hideGlfw() throws Exception {
        Class<?> glfw;
        java.lang.reflect.Field handleField;
        try {
            glfw = Class.forName("org.lwjgl.glfw.GLFW");
            handleField = org.lwjgl.opengl.Display.class.getDeclaredField("handle");
        } catch (ClassNotFoundException | NoSuchFieldException e) {
            return false;
        }
        handleField.setAccessible(true);
        long handle = handleField.getLong(null);
        if (handle == 0L) {
            return false;
        }
        glfw.getMethod("glfwHideWindow", long.class).invoke(null, handle);
        return true;
    }

    /** The Vulkan renderer's window (pylon, SDL3). False when pylon is not there or its window is not created yet. */
    private static boolean minimiseSdl() throws Exception {
        Class<?> sdlDisplay;
        try {
            sdlDisplay = Class.forName("pl.tomgirl.pylon.window.DisplaySdl");
        } catch (ClassNotFoundException e) {
            return false;
        }
        Object display = sdlDisplay.getMethod("instance").invoke(null);
        if (display == null || !(Boolean) sdlDisplay.getMethod("isCreated").invoke(display)) {
            return false;
        }
        long handle = (Long) sdlDisplay.getMethod("getHandle").invoke(display);
        if (handle == 0L) {
            return false;
        }
        Class<?> video = Class.forName("org.lwjgl.sdl.SDLVideo", true, sdlDisplay.getClassLoader());
        video.getMethod("SDL_MinimizeWindow", long.class).invoke(null, handle);
        return true;
    }

    /** The Normal renderer's window (LWJGL 2 on X11). False when it is not a created LWJGL 2 Linux window. */
    private static boolean minimiseLwjgl2() throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux")
                || !org.lwjgl.opengl.Display.isCreated()) {
            return false;
        }
        Class<?> linux;
        try {
            linux = Class.forName("org.lwjgl.opengl.LinuxDisplay");
        } catch (ClassNotFoundException e) {
            return false;
        }
        Method lock = linux.getDeclaredMethod("lockAWT");
        Method unlock = linux.getDeclaredMethod("unlockAWT");
        Method getDisplay = linux.getDeclaredMethod("getDisplay");
        Method getWindow = linux.getDeclaredMethod("getWindow");
        Method getScreen = linux.getDeclaredMethod("getDefaultScreen");
        Method iconify = linux.getDeclaredMethod("nIconifyWindow", long.class, long.class, int.class);
        for (Method m : new Method[]{lock, unlock, getDisplay, getWindow, getScreen, iconify}) {
            m.setAccessible(true);
        }
        lock.invoke(null);
        try {
            long window = (Long) getWindow.invoke(null);
            if (window == 0L) {
                return false;
            }
            iconify.invoke(null, getDisplay.invoke(null), window, getScreen.invoke(null));
        } finally {
            unlock.invoke(null);
        }
        return true;
    }
}
