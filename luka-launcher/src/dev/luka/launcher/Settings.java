package dev.luka.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/** The launcher's own settings (username, memory, java), kept next to the game files. */
final class Settings {
  private final File file;
  private final Properties values = new Properties();

  Settings(File file) {
    this.file = file;
    if (file.isFile()) {
      try (InputStream in = new FileInputStream(file)) {
        values.load(in);
      } catch (IOException ignored) {
        // start from defaults
      }
    }
  }

  String username() {
    return values.getProperty("username", System.getProperty("user.name", "Player").replaceAll("[^A-Za-z0-9_]", ""));
  }

  int ramMb() {
    try {
      return Integer.parseInt(values.getProperty("ramMb", "3072"));
    } catch (NumberFormatException e) {
      return 3072;
    }
  }

  /** Where launcher, mod and dependency updates are announced; the player can change or clear it in launcher.properties. */
  String updateUrl() { return System.getProperty("luka.manifest", values.getProperty("updateUrl", "https://client.luka.wtf/update.json")).trim(); }

  /** Which graphics path the game starts with: "normal" (OpenGL) or "vulkan". */
  String renderer() { return "vulkan".equals(values.getProperty("renderer", "normal").trim()) ? "vulkan" : "normal"; }

  /** The newest release whose notes this player has been shown. */
  String seenChangelog() { return values.getProperty("seenChangelog", "").trim(); }

  String syncFolder() { return values.getProperty("syncFolder", "").trim(); }

  /** The client ID open-source Minecraft tools use for Microsoft sign-in; it works through login.live.com. */
  private static final String DEFAULT_MS_CLIENT_ID = "00000000402b5328";

  /** True when the sign-in app only works through login.live.com (see MicrosoftAuth). */
  boolean msLiveFlow() {
    String set = values.getProperty("msLiveFlow");
    return set != null ? Boolean.parseBoolean(set.trim()) : DEFAULT_MS_CLIENT_ID.equals(msClientId());
  }

  String msClientId() {
    return values.getProperty("msClientId", DEFAULT_MS_CLIENT_ID);
  }

  /** What the launcher does once the game is running: "close", "hide" or "keep". */
  String onLaunch() {
    return values.getProperty("onLaunch", "keep");
  }

  boolean viaPrism() {
    return Boolean.parseBoolean(values.getProperty("viaPrism", "false"));
  }

  /** Use Nvidia's faster present method for luka's Java (see NvidiaProfile). */
  boolean nvOptimize() { return Boolean.parseBoolean(values.getProperty("nvOptimize", "true")); }

  /** True while luka's Nvidia profile is in place, so turning the option off knows there is something to remove. */
  boolean nvApplied() { return Boolean.parseBoolean(values.getProperty("nvApplied", "false")); }

  void set(String key, String value) {
    values.setProperty(key, value);
  }

  void save() {
    try {
      file.getParentFile().mkdirs();
      try (OutputStream out = new FileOutputStream(file)) {
        values.store(out, "luka launcher");
      }
    } catch (IOException ignored) {
      // settings are a convenience
    }
  }
}
