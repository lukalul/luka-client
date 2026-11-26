package dev.cbui.social;

/**
 * Cleans what Luka Online sends before the game shows it. Names, server addresses and messages are written by other
 * players (or a modified client), so none of it may change how the game draws it: no formatting codes, no line breaks,
 * no invisible or direction-changing characters, nothing wider than its place. No Minecraft classes, so the
 * plain-Java checks test it.
 */
public final class SafeText {
  public static final int MESSAGE_MAX = 200;

  private SafeText() { }

  /**
   * Plain text the game draws safely, at most max characters: no section signs (Minecraft formatting codes: colours,
   * the scrambled style), no control characters or line breaks, no zero-width or right-to-left override characters,
   * no private-use characters or surrogates (emoji), at most one combining mark per letter, single spaces.
   * The service cleans messages the same way; this is the second line, for anything that gets past it.
   */
  public static String safeText(String in, int max) {
    if (in == null || in.isEmpty()) return "";
    StringBuilder out = new StringBuilder(Math.min(in.length(), max + 1));
    boolean space = true; // no leading space
    boolean mark = true; // no combining mark at the start or after a space
    for (int i = 0; i < in.length() && out.length() < max; i++) {
      char c = in.charAt(i);
      if (c == '\u00a7' || Character.isSurrogate(c)) continue;
      int type = Character.getType(c);
      boolean blank = Character.isWhitespace(c) || Character.isSpaceChar(c) || type == Character.CONTROL
          || type == Character.FORMAT || type == Character.PRIVATE_USE || type == Character.UNASSIGNED
          || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR || c == '\ufffd';
      if (blank) {
        if (!space) out.append(' ');
        space = true;
        mark = true;
        continue;
      }
      boolean isMark = type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK;
      if (isMark) {
        if (mark) continue;
        mark = true;
      } else {
        mark = false;
      }
      out.append(c);
      space = false;
    }
    int end = out.length();
    while (end > 0 && out.charAt(end - 1) == ' ') end--;
    return out.substring(0, end);
  }

  /** A Minecraft account name (letters, digits, underscore, at most 16), or "?" when it is anything else. */
  public static String playerName(String in) {
    return in != null && in.matches("[A-Za-z0-9_]{1,16}") ? in : "?";
  }

  /** A server address as friends see it ("is online on ..."): address characters only, at most 80. */
  public static String serverAddress(String in) {
    if (in == null) return "";
    String s = in.replaceAll("[^A-Za-z0-9._:-]", "");
    return s.length() > 80 ? s.substring(0, 80) : s;
  }

  /** A lowercase dashed UUID, or "". */
  public static String uuidOrEmpty(String in) {
    return in != null && in.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") ? in : "";
  }
}
