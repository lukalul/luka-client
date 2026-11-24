package dev.luka.launcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Linux only: gives the launcher (and the game it starts) the desktop's own mouse cursor size and theme.
 *
 * <p>Java has no Wayland toolkit, so the launcher window is an X11 window (XWayland on Wayland desktops). Its cursor is
 * drawn by libXcursor, which takes the size from XCURSOR_SIZE, else the X resource Xcursor.size, else a guess from Xft.dpi
 * or the screen height (height / 48: 30 px on a 1440p screen, 45 px on 4K). The desktop's own setting (GNOME's
 * cursor-size, KDE's cursorSize ...) only reaches X11 programs through those two. A session that sets neither gives the
 * pointer a different size, and theme, whenever it is over the launcher; the rest of the desktop is unchanged.
 *
 * <p>The environment of a running Java cannot change, so when something is missing the launcher starts itself again
 * once with the desktop's values added. The game inherits them, so its X11 and SDL windows match too. Nothing is
 * changed when the session already sets the value (environment or X resources), when that cannot be checked (no xrdb
 * or xprop), or with LUKA_CURSOR_FIX=0.
 */
final class LinuxCursor {
  private LinuxCursor() {}

  private static final String GUARD = "luka.cursorEnv";

  /**
   * Starts the launcher again with the desktop's cursor size and theme when the session is missing them. True when
   * the new launcher has started and this one should end without opening a window.
   */
  static boolean relaunchIfNeeded(String[] args) {
    if (!Platform.linux() || System.getProperty(GUARD) != null || "0".equals(System.getenv("LUKA_CURSOR_FIX"))) return false;
    String app = System.getProperty("luka.app"); // set by luka.sh; without it this is a dev run
    if (app == null) return false;
    File boot = new File(app, "luka-boot.jar");
    if (!boot.isFile()) return false;
    Map<String, String> env;
    try {
      env = missing();
    } catch (RuntimeException e) {
      return false;
    }
    if (env.isEmpty()) return false;
    List<String> command = new ArrayList<>();
    command.add(Platform.javaCli(new File(System.getProperty("java.home"))).getPath());
    command.add("-Xshare:auto");
    command.add("-D" + GUARD + "=1");
    for (String key : new String[] {"luka.home", "luka.app", "luka.manifest", "luka.updated"}) {
      String value = System.getProperty(key);
      if (value != null) command.add("-D" + key + "=" + value);
    }
    command.add("-cp");
    command.add(boot.getPath());
    command.add("dev.luka.launcher.Boot");
    for (String arg : args) command.add(arg);
    ProcessBuilder builder = new ProcessBuilder(command).directory(new File(app)).inheritIO();
    builder.environment().putAll(env);
    try {
      builder.start();
    } catch (Exception e) {
      return false; // carry on with the cursor as it is
    }
    System.err.println("luka: using the desktop's cursor " + env + " (the session does not give it to X11 programs)");
    return true;
  }

  /** The XCURSOR_* variables to add: the desktop's values for those the session sets neither in the environment nor as X resources. */
  static Map<String, String> missing() {
    Map<String, String> out = new LinkedHashMap<>();
    boolean needSize = blank(System.getenv("XCURSOR_SIZE"));
    boolean needTheme = blank(System.getenv("XCURSOR_THEME"));
    if (!needSize && !needTheme) return out;
    String resources = xResources();
    if (resources == null) return out; // can't tell what X programs already get: leave it alone
    if (hasResource(resources, "Xcursor.size")) needSize = false;
    if (hasResource(resources, "Xcursor.theme")) needTheme = false;
    if (!needSize && !needTheme) return out;
    String[] desktop = desktopCursor();
    if (needSize && desktop[0] != null) out.put("XCURSOR_SIZE", desktop[0]);
    if (needTheme && desktop[1] != null) out.put("XCURSOR_THEME", desktop[1]);
    return out;
  }

  /** The X server's resource database (what libXcursor reads), or null when it cannot be read. */
  private static String xResources() {
    if (blank(System.getenv("DISPLAY"))) return null;
    String query = run("xrdb", "-query");
    if (query != null) return query;
    String prop = run("xprop", "-root", "-notype", "RESOURCE_MANAGER");
    if (prop == null) return null;
    // xprop prints one quoted string with \n escapes, or "not found" when the property is unset
    return prop.replace("\\n", "\n").replace("\\t", "\t");
  }

  private static boolean hasResource(String resources, String name) {
    for (String line : resources.split("\n")) {
      String l = line.trim();
      if (l.startsWith("RESOURCE_MANAGER")) l = l.substring(l.indexOf('"') + 1);
      int colon = l.indexOf(':');
      if (colon > 0 && l.substring(0, colon).trim().equalsIgnoreCase(name) && !l.substring(colon + 1).trim().isEmpty()) return true;
    }
    return false;
  }

  /** {size, theme} from the running desktop's own settings; either may be null. */
  private static String[] desktopCursor() {
    String desktop = String.valueOf(System.getenv("XDG_CURRENT_DESKTOP")).toUpperCase(Locale.ROOT);
    String size;
    String theme;
    if (desktop.contains("KDE")) {
      size = kde("cursorSize");
      theme = kde("cursorTheme");
    } else if (desktop.contains("XFCE")) {
      size = run("xfconf-query", "-c", "xsettings", "-p", "/Gtk/CursorThemeSize");
      theme = run("xfconf-query", "-c", "xsettings", "-p", "/Gtk/CursorThemeName");
    } else if (desktop.contains("MATE")) {
      size = run("gsettings", "get", "org.mate.peripherals-mouse", "cursor-size");
      theme = run("gsettings", "get", "org.mate.peripherals-mouse", "cursor-theme");
    } else if (desktop.contains("CINNAMON")) {
      size = run("gsettings", "get", "org.cinnamon.desktop.interface", "cursor-size");
      theme = run("gsettings", "get", "org.cinnamon.desktop.interface", "cursor-theme");
    } else {
      // GNOME, Budgie, Pantheon, Unity, and most other desktops' GTK settings
      size = run("gsettings", "get", "org.gnome.desktop.interface", "cursor-size");
      theme = run("gsettings", "get", "org.gnome.desktop.interface", "cursor-theme");
    }
    return new String[] {cleanSize(size), cleanTheme(theme)};
  }

  private static String kde(String key) {
    for (String tool : new String[] {"kreadconfig6", "kreadconfig5"}) {
      String value = run(tool, "--file", "kcminputrc", "--group", "Mouse", "--key", key);
      if (value != null) return value;
    }
    return null;
  }

  private static String cleanSize(String value) {
    if (value == null) return null;
    String v = value.trim();
    if (v.startsWith("int32 ")) v = v.substring(6).trim();
    if (!v.matches("[0-9]{1,3}")) return null;
    int size = Integer.parseInt(v);
    return size >= 8 && size <= 512 ? String.valueOf(size) : null;
  }

  private static String cleanTheme(String value) {
    if (value == null) return null;
    String v = value.trim();
    if (v.length() >= 2 && v.charAt(0) == '\'' && v.charAt(v.length() - 1) == '\'') v = v.substring(1, v.length() - 1);
    return v.matches("[A-Za-z0-9._+ -]{1,100}") ? v : null;
  }

  private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }

  /** A command's output, or null when it is missing, fails or takes more than two seconds. */
  private static String run(String... command) {
    try {
      final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      process.getOutputStream().close();
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      Thread reader = new Thread(() -> {
        byte[] buffer = new byte[8192];
        try (InputStream in = process.getInputStream()) {
          for (int n; (n = in.read(buffer)) > 0; ) synchronized (out) { out.write(buffer, 0, n); }
        } catch (Exception ignored) {
          // the process was ended
        }
      }, "luka-cursor-probe");
      reader.setDaemon(true);
      reader.start();
      if (!process.waitFor(2, TimeUnit.SECONDS)) {
        process.destroy();
        return null;
      }
      reader.join(1000);
      if (process.exitValue() != 0) return null;
      synchronized (out) { return out.toString("UTF-8").trim(); }
    } catch (Exception missing) {
      return null;
    }
  }
}
