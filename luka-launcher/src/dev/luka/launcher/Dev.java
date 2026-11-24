package dev.luka.launcher;

import java.net.URL;

/**
 * Switches for local tests of the launcher against a stand-in download server and API on this PC
 * (luka-launcher/test/encmods). They never apply to players: Luka.exe and luka.sh always start the launcher with
 * -Dluka.app, and with it set every switch here is ignored. They also take loopback addresses only.
 *
 * <ul>
 *   <li>{@code -Dluka.devApi=http://127.0.0.1:PORT}: the luka API instead of
 *       api.luka.wtf. The local API must run with ALLOW_UNVERIFIED, since the test account cannot make a Mojang proof.</li>
 *   <li>{@code -Dluka.devManifestKey=<base64 Ed25519 public key>}: a test key the update list may be signed with
 *       instead of the release key, taken only while the list itself comes from a loopback address.</li>
 * </ul>
 */
final class Dev {
  static final String API = "https://api.luka.wtf";

  private Dev() {}

  /** True only for a launcher started by hand (no -Dluka.app), never for an installed one. */
  private static boolean allowed() {
    return System.getProperty("luka.app") == null;
  }

  static boolean loopback(String address) {
    try {
      String host = new URL(address).getHost();
      return "127.0.0.1".equals(host) || "localhost".equals(host) || "[::1]".equals(host);
    } catch (Exception bad) {
      return false;
    }
  }

  private static String local(String property) {
    if (!allowed()) return null;
    String value = System.getProperty(property);
    if (value == null || value.trim().isEmpty() || !loopback(value.trim())) return null;
    value = value.trim();
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  /** The luka API's base address. */
  static String api() {
    String local = local("luka.devApi");
    return local != null ? local : API;
  }

  /** True while a local test API stands in: sign-in then skips the Mojang proof the test account cannot make. */
  static boolean localApi() {
    return local("luka.devApi") != null;
  }

  /** The extra public key a loopback update list may be signed with, or null. */
  static String manifestKey(String manifestUrl) {
    if (!allowed() || manifestUrl == null || !loopback(manifestUrl.trim())) return null;
    String key = System.getProperty("luka.devManifestKey");
    return key == null || key.trim().isEmpty() ? null : key.trim();
  }
}
