package dev.cbui.feature.oldanimations;

import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;

/**
 * 1.7 style animations (port of the Old Animations mod): the 1.7 enchantment glint, health flashing, smooth sneaking, block hitting,
 * 1.7 item angles in first and third person and "always swing". Every option of the original is its own switch; the card's own
 * on/off switch is the original's "Enabled" master option. The work is done by mixins that call {@link OldAnimationsHooks}.
 */
public final class OldAnimationsModule extends FeatureModule {
  public final Setting.Toggle glintRendering = grouped("Animations",
      add(new Setting.Toggle("Glint Rendering", true, "Enchanted items use the 1.7 glint.")));
  public final Setting.Toggle healthFlashing = grouped("Animations",
      add(new Setting.Toggle("Health Flashing", true, "Your hearts blink the way they did in 1.7 after you take damage.")));
  public final Setting.Toggle smoothSneaking = grouped("Animations",
      add(new Setting.Toggle("Smooth Sneaking", true, "Your camera lowers gradually when you sneak, and sneaking players use their 1.7 pose.")));
  public final Setting.Toggle longerUnsneak = grouped("Animations",
      add(new Setting.Toggle("Longer Unsneak", true, "When you stop sneaking, your camera takes a few ticks to rise back to standing height (needs Smooth Sneaking).")));
  public final Setting.Toggle blockHitting = grouped("Items",
      add(new Setting.Toggle("Block Hitting", true, "Swings your arm even if you're using an item.")));
  public final Setting.Toggle firstPersonTransforms = grouped("Items",
      add(new Setting.Toggle("Item Transforms", true, "Held items use their 1.7 angle and size in first person.")));
  public final Setting.Toggle thirdPersonTransforms = grouped("Items",
      add(new Setting.Toggle("Third Person Item Transforms", true, "Players and mobs hold items the way they did in 1.7.")));
  public final Setting.Toggle alwaysSwing = grouped("Miscellaneous",
      add(new Setting.Toggle("Always Swing", false, "Your arm swings on every left click, even when vanilla 1.8 would skip the swing.")));

  private static volatile OldAnimationsModule instance;

  public OldAnimationsModule() {
    super("Old Animations", true);
    instance = this;
  }

  @Override public String category() { return "VISUALS"; }

  /** The module when it exists and is switched on, otherwise null. Cheap enough for per-frame use. */
  public static OldAnimationsModule active() {
    OldAnimationsModule m = instance;
    return m != null && m.enabled() ? m : null;
  }

  /** The module only when it is on and the given option is on as well. */
  public static OldAnimationsModule active(Setting.Toggle option) {
    OldAnimationsModule m = active();
    return m != null && option.get() ? m : null;
  }
}
