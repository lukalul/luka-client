package dev.luka.launcher;

import java.io.File;
import java.util.Locale;

/**
 * The operating system the launcher runs on, and the file names that differ between Windows and Linux.
 * Everything else in the launcher asks here instead of assuming Windows. On Windows every answer is what the
 * launcher always used, so Windows installs are unchanged.
 */
final class Platform {
  private Platform() {}

  /** The system's name; -Dluka.os=linux|windows overrides it (tests only). */
  private static String os() {
    return System.getProperty("luka.os", System.getProperty("os.name", "")).toLowerCase(Locale.ROOT);
  }

  static boolean windows() { return os().contains("win"); }

  static boolean linux() { return os().contains("linux"); }

  /** The key of this system in the update list's per-system Java map. */
  static String id() { return windows() ? "windows-x64" : linux() ? "linux-x64" : "other"; }

  /** Mojang's name for this system in library rules. */
  static String ruleName() { return windows() ? "windows" : linux() ? "linux" : "osx"; }

  /** The name Adoptium and Corretto use for this system. */
  static String vendorOs() { return windows() ? "windows" : "linux"; }

  /** The archive Java runtimes come in on this system. */
  static String runtimeArchive() { return windows() ? "zip" : "tar.gz"; }

  /** The Java that starts without a console window (Windows) or the only one there is (Linux). */
  static String javaw() { return windows() ? "javaw.exe" : "java"; }

  /** The Java used for version checks. */
  static String javaCli() { return windows() ? "java.exe" : "java"; }

  static File javaw(File runtime) { return new File(runtime, "bin/" + javaw()); }

  static File javaCli(File runtime) { return new File(runtime, "bin/" + javaCli()); }

  /** The classifier of LWJGL 2's and jinput's native jars. */
  static String nativesClassifier() { return "natives-" + (windows() ? "windows" : "linux"); }
}
