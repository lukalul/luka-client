package dev.cbui.feature.hotbarwheel;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;

/**
 * Changes what one turn of the mouse wheel does to the hotbar selection while playing. With every option off the
 * game's own wheel handling runs untouched.
 */
public final class HotbarWheelModule extends FeatureModule {
  private static final String GROUP = "Mouse wheel";

  private final Setting.Toggle locked = grouped(GROUP, add(new Setting.Toggle(
      "Wheel does not change slot", false, "Turning the wheel leaves the selected hotbar slot alone")));
  private final Setting.Toggle flipped = grouped(GROUP, add(new Setting.Toggle(
      "Flip wheel direction", false, "Wheel up goes right along the hotbar, wheel down goes left")));
  private final Setting.Toggle clamped = grouped(GROUP, add(new Setting.Toggle(
      "Stop at the ends", false, "Stay on the first or last slot instead of wrapping to the other side")));

  public HotbarWheelModule() {
    super("Scroll Tweaks", true);
  }

  @Override public String category() { return "UTILITY"; }

  /** The module when it exists and is switched on, else null. */
  public static HotbarWheelModule active() {
    HotbarWheelModule module = Features.get(HotbarWheelModule.class);
    return module != null && module.enabled() ? module : null;
  }

  /** True when at least one option changes the wheel; false means the vanilla step should run. */
  public boolean replacesVanilla() {
    return locked.get() || flipped.get() || clamped.get();
  }

  /** The slot to select after a wheel event with this delta. */
  public int slotAfter(int slot, int delta) {
    return WheelStep.apply(slot, delta, locked.get(), flipped.get(), clamped.get());
  }
}
