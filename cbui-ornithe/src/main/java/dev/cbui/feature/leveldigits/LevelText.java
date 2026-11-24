package dev.cbui.feature.leveldigits;

/**
 * Turns the language keys vanilla looks up for a level into plain digits. Free of game classes so the plain-Java
 * checks can run it.
 */
public final class LevelText {
  /** Key prefix of an enchantment level ("enchantment.level.3"); the suffix is the level itself. */
  public static final String ENCHANTMENT_PREFIX = "enchantment.level.";
  /** Key prefix of a potion strength ("potion.potency.1"); the suffix is the amplifier, one below the strength. */
  public static final String POTION_PREFIX = "potion.potency.";

  private LevelText() {}

  /** Digits for an enchantment level key, or null when the key is something else. */
  public static String enchantmentLevel(String key) {
    int level = suffix(key, ENCHANTMENT_PREFIX);
    return level == Integer.MIN_VALUE ? null : Integer.toString(level);
  }

  /** Digits for a potion strength key (amplifier + 1), or null when the key is something else. */
  public static String potionStrength(String key) {
    int amplifier = suffix(key, POTION_PREFIX);
    if (amplifier == Integer.MIN_VALUE || amplifier == Integer.MAX_VALUE) return null;
    return Integer.toString(amplifier + 1);
  }

  private static int suffix(String key, String prefix) {
    if (key == null || !key.startsWith(prefix) || key.length() == prefix.length()) return Integer.MIN_VALUE;
    try {
      return Integer.parseInt(key.substring(prefix.length()));
    } catch (NumberFormatException notANumber) {
      return Integer.MIN_VALUE;
    }
  }
}
