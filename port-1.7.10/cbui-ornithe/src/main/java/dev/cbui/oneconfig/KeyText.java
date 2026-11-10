package dev.cbui.oneconfig;

import java.util.regex.Pattern;

/** Turns untranslated language keys ("shake-tweaks.disable-hand-shake") into words ("Disable hand shake"). */
public final class KeyText {
  private static final Pattern KEY = Pattern.compile("[a-z0-9_\\-]+(\\.[a-z0-9_\\-]+)+");

  private KeyText() {}

  public static boolean looksLikeKey(String text) {
    return text != null && KEY.matcher(text).matches();
  }

  /** The words of a key's last meaningful part; the text unchanged when it is not a key. */
  public static String words(String text) {
    if (!looksLikeKey(text)) return text;
    String[] parts = text.split("\\.");
    int i = parts.length - 1;
    while (i > 0 && (parts[i].equals("name") || parts[i].equals("title") || parts[i].equals("label"))) i--;
    String last = parts[i].replace('-', ' ').replace('_', ' ').trim();
    if (last.isEmpty()) return text;
    return Character.toUpperCase(last.charAt(0)) + last.substring(1);
  }

  /** A hover hint that is still a raw key is worse than none. */
  public static String hint(String text) {
    return looksLikeKey(text) ? "" : text;
  }
}
