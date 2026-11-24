package dev.cbui.feature.leveldigits;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;

/** Item tooltips show enchantment levels and potion strengths in digits rather than Roman numerals. */
public final class LevelDigitsModule extends FeatureModule {
  private final Setting.Toggle digits = add(new Setting.Toggle(
      "Digits instead of numerals", true,
      "Show levels as 1, 2, 3 rather than I, II, III on enchantments and potions"));

  public LevelDigitsModule() {
    super("Numerical Enchantments", true);
  }

  @Override public String category() { return "UTILITY"; }

  /** The module when it exists, is switched on and its option is on, else null. Cheap enough for every tooltip line. */
  public static LevelDigitsModule active() {
    LevelDigitsModule module = Features.get(LevelDigitsModule.class);
    return module != null && module.enabled() && module.digits.get() ? module : null;
  }

  /** Text for an enchantment level lookup, or null to let the language file answer. */
  public String enchantmentLevel(String key) {
    return LevelText.enchantmentLevel(key);
  }

  /** Text for a potion strength lookup, or null to let the language file answer. */
  public String potionStrength(String key) {
    return LevelText.potionStrength(key);
  }
}
