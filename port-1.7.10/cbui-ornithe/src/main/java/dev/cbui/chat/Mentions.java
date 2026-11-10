package dev.cbui.chat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the player's name in chat text and colours it, leaving the rest of the line's formatting as it was. */
public final class Mentions {
  private static final String HIGHLIGHT = "§e";
  private Mentions() { }

  /** Matches the whole name in any case; it also matches straight after a colour code, but not inside a longer name. */
  public static Pattern pattern(String name) {
    return Pattern.compile("(?:(?<=§.)|(?<![A-Za-z0-9_]))" + Pattern.quote(name) + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
  }

  /** True when someone else wrote the name. A name that is only there as the sender of the line ("name: hi", "<name> hi") does not count. */
  public static boolean mentioned(String plain, Pattern name) {
    Matcher m = name.matcher(plain);
    if (!m.find()) return false;
    return !sender(plain, m.start(), m.end()) || m.find();
  }

  private static boolean sender(String text, int start, int end) {
    if (end >= text.length()) return false;
    char next = text.charAt(end);
    if (next == ':' || next == '>') return true;
    if (next == ']') return start <= 1;
    return text.startsWith(" »", end);
  }

  /** Returns the text with each match highlighted. {@code base} holds the codes of the surrounding style, which are restored after each match. */
  public static String highlight(String text, Pattern name, String base) {
    Matcher m = name.matcher(text);
    StringBuilder out = null;
    int at = 0;
    while (m.find()) {
      if (out == null) out = new StringBuilder();
      out.append(text, at, m.start()).append(HIGHLIGHT).append(m.group()).append("§r").append(base).append(activeCodes(text, m.start()));
      at = m.end();
    }
    return out == null ? text : out.append(text, at, text.length()).toString();
  }

  /** The formatting codes still in effect at a position: the last colour, and any styles after it. */
  private static String activeCodes(String text, int end) {
    StringBuilder codes = new StringBuilder();
    for (int i = 0; i + 1 < end; i++) {
      if (text.charAt(i) != '§') continue;
      char code = Character.toLowerCase(text.charAt(++i));
      if (code == 'r') codes.setLength(0);
      else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) { codes.setLength(0); codes.append('§').append(code); }
      else codes.append('§').append(code);
    }
    return codes.toString();
  }
}
