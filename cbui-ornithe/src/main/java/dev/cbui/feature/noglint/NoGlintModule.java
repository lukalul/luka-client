package dev.cbui.feature.noglint;

import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;

/**
 * Turns the enchantment shine off. Every enchanted item is otherwise drawn twice (the item, then the moving shine over it),
 * so in fights full of enchanted armour and swords this saves a lot of drawing. The hooks are the Glint distance ones
 * ({@link dev.cbui.render.GlintDistance#skip()}).
 */
public final class NoGlintModule extends FeatureModule {
  private static volatile NoGlintModule instance;

  public final Setting.Toggle hand = add(new Setting.Toggle("Hand and inventory", true,
      "No shine on the item in your hand, the hotbar and items in menus"));
  public final Setting.Toggle world = add(new Setting.Toggle("Players, armour and dropped items", true,
      "No shine on armour and held items of every player (you in third person too), mobs, item frames and items on the ground. This is the part that saves FPS"));

  public NoGlintModule() {
    super("No Glint", false);
    instance = this;
  }

  /** The module when it is on, else null. */
  public static NoGlintModule active() {
    NoGlintModule m = instance;
    return m != null && m.enabled() ? m : null;
  }
}
