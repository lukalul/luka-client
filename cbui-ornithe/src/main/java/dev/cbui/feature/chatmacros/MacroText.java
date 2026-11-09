package dev.cbui.feature.chatmacros;

/** Pure text expansion used by Chat Macros and its plain-Java checks. */
public final class MacroText {
  public static String expand(
      String text, int x, int y, int z, String name, float health) {
    if (text == null || text.isEmpty()) return "";
    String result = placeholders(text, x, y, z, name, health);
    StringBuilder clean = new StringBuilder(Math.min(100, result.length()));
    for (int i = 0; i < result.length() && clean.length() < 100; i++) {
      char c = result.charAt(i);
      if (c == '\u00a7') { if (i + 1 < result.length()) i++; continue; }
      if (Character.isISOControl(c)) continue;
      if (Character.isHighSurrogate(c) && i + 1 < result.length() && Character.isLowSurrogate(result.charAt(i + 1))) {
        if (clean.length() + 2 > 100) break;
        clean.append(c).append(result.charAt(++i));
      } else if (!Character.isSurrogate(c)) clean.append(c);
    }
    String value = clean.toString();
    return value.trim().isEmpty() || value.trim().equals("/") ? "" : value;
  }

  private static String placeholders(String text, int x, int y, int z, String name, float health) {
    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < text.length();) {
      int end = text.charAt(i) == '{' ? text.indexOf('}', i + 1) : -1;
      String value = null;
      if (end > i && (i == 0 || text.charAt(i - 1) != '{')) {
        String key = text.substring(i + 1, end);
        if (key.equals("x")) value = Integer.toString(x); else if (key.equals("y")) value = Integer.toString(y);
        else if (key.equals("z")) value = Integer.toString(z); else if (key.equals("name")) value = name == null ? "" : name;
        else if (key.equals("health")) value = formatHealth(health);
      }
      if (value == null) out.append(text.charAt(i++)); else { out.append(value); i = end + 1; }
    }
    return out.toString();
  }

  private static String formatHealth(float health) {
    if (health == (long) health) return Long.toString((long) health);
    return Float.toString(health);
  }

  private MacroText() {}
}
