package dev.cbui.feature.numericalenchantments;

import dev.cbui.feature.Features;
import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;

/** Shows enchantment levels and potion strengths as ordinary numbers instead of roman numerals. */
public final class NumericalEnchantmentsModule extends FeatureModule {
  public final Setting.Toggle useArabic = add(new Setting.Toggle("Use Arabic Numbers", true,
      "Writes levels as 2, 3, 4 instead of II, III, IV on enchanted items and potions."));

  public NumericalEnchantmentsModule() { super("Numerical Enchantments", true); }

  @Override public String category() { return "UTILITY"; }

  /** True while levels should be printed as plain numbers. */
  public boolean arabic() { return enabled() && useArabic.get(); }

  /** Whether the tooltip hooks should substitute a number right now; any failure means "leave vanilla alone". */
  public static boolean substituting() {
    try {
      NumericalEnchantmentsModule module = Features.get(NumericalEnchantmentsModule.class);
      return module != null && module.arabic();
    } catch (Throwable failure) {
      return false;
    }
  }
}
