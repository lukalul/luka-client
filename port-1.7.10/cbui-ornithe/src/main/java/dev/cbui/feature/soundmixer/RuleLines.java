package dev.cbui.feature.soundmixer;

import java.util.List;

/**
 * Reads the player's rules file: one {@code name=percent} or {@code start*=percent} per line. A leading
 * {@code minecraft:} and a trailing {@code %} are allowed, case does not matter, percentages are held to 0..200,
 * and comment lines (starting with {@code #}) or lines that do not parse are skipped.
 */
public final class RuleLines {
  public static final float MAX_PERCENT = 200f;

  /** The text written when the rules file is missing. */
  public static final String EXPLANATION =
      "# luka Sound Tweaks - volume rules for single sounds\n"
          + "#\n"
          + "# Write one rule per line, as  sound.name=percent  (0 is silent, 100 unchanged, 200 the loudest).\n"
          + "# A name that ends in * covers every sound whose name begins with the text before it.\n"
          + "# Rules win over the sliders in the menu. A full name wins over a * rule, and a longer * rule over a\n"
          + "# shorter one. Lines starting with # are ignored. Changes are read a few seconds after saving.\n"
          + "#\n"
          + "# Examples (remove the # in front to use one):\n"
          + "# random.levelup=40\n"
          + "# mob.zombie.*=0\n"
          + "# note.*=150%\n";

  private RuleLines() {}

  public static NameTable<Float> parse(List<String> lines) {
    NameTable<Float> table = new NameTable<Float>();
    if (lines == null) return table;
    for (String raw : lines) {
      if (raw == null) continue;
      String line = raw.trim();
      if (line.isEmpty() || line.startsWith("#")) continue;
      int eq = line.indexOf('=');
      if (eq <= 0) continue;
      String name = line.substring(0, eq).trim();
      Float percent = percent(line.substring(eq + 1));
      if (percent == null || name.isEmpty()) continue;
      if (name.endsWith("*")) {
        String start = name.substring(0, name.length() - 1);
        if (start.indexOf('*') >= 0) continue;
        table.startingWith(start, percent);
      } else {
        if (name.indexOf('*') >= 0) continue;
        table.whole(name, percent);
      }
    }
    return table;
  }

  /** The number in "75", "75%" or " 75 % ", held to 0..200; null when it is not a number. */
  static Float percent(String text) {
    String s = text.trim();
    if (s.endsWith("%")) s = s.substring(0, s.length() - 1).trim();
    if (s.isEmpty()) return null;
    float value;
    try {
      value = Float.parseFloat(s);
    } catch (NumberFormatException notNumber) {
      return null;
    }
    if (Float.isNaN(value)) return null;
    return Math.max(0f, Math.min(MAX_PERCENT, value));
  }
}
