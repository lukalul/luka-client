package dev.cbui.feature;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;

/**
 * A luka feature that has no HUD box (freelook, FOV changer, scroll tweaks...): it appears as its own card in the mods menu with an
 * on/off switch and its own settings, which are saved with the rest of luka's config. Subclasses add settings with {@code add(...)}
 * (or {@code grouped("Heading", add(...))}) and read them from mixins through {@link Features#get(Class)}.
 */
public abstract class FeatureModule extends AbstractHudModule {
  protected FeatureModule(String name, boolean enabledByDefault) {
    super(name, enabledByDefault, Anchor.TOP_LEFT, 0, 0, 0, 0);
  }

  @Override public final boolean hasHud() { return false; }

  @Override protected final void draw(float partialTicks, boolean editor) { }

  /** Lets another module put one of its settings on this card (used to merge cards). */
  public final void adopt(dev.cbui.hud.Setting<?> setting) { add(setting); }

  /** False hides the card from the mods menu (e.g. a staff card while not in staff mode). The module keeps working. */
  public boolean visible() { return true; }

  /** Menu tab the card is listed under: "UTILITY", "VISUALS" or "HUD". */
  public String category() { return "VISUALS"; }
}
