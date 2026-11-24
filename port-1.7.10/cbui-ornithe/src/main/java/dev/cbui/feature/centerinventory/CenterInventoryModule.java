package dev.cbui.feature.centerinventory;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;

/**
 * Keeps the inventory centred while potion effects are active. Vanilla moves the inventory screen to the right to
 * make room for its effect list on the left (InventoryEffectRenderer); with luka's effect boxes that just looks
 * off-centre. Lives on the settings GENERAL tab (ModuleMenu SETTINGS_HOME), on by default.
 */
public final class CenterInventoryModule extends FeatureModule {
  public CenterInventoryModule() { super("Auto Center Inventory", true); }

  @Override public String category() { return "UTILITY"; }

  /** True when the inventory should stay centred. */
  public static boolean on() {
    try {
      CenterInventoryModule module = Features.get(CenterInventoryModule.class);
      return module != null && module.enabled();
    } catch (Throwable failure) {
      return false;
    }
  }
}
