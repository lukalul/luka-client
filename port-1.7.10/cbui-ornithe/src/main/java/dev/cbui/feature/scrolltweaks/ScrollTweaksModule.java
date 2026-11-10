package dev.cbui.feature.scrolltweaks;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;

/** Options for how the mouse wheel moves the hotbar selection. */
public final class ScrollTweaksModule extends FeatureModule {
  /** Returned by {@link #slotAfterWheel} when none of the options apply and vanilla should do its normal thing. */
  public static final int LEAVE_TO_VANILLA = -1;

  public final Setting.Toggle disableScroll = grouped("Scrolling", add(new Setting.Toggle(
      "Disable Scrolling", false, "The wheel no longer changes the selected hotbar slot.")));
  public final Setting.Toggle reverseScroll = grouped("Scrolling", add(new Setting.Toggle(
      "Reverse Scroll Direction", false, "Scrolling up moves down the hotbar and the other way round.")));
  public final Setting.Toggle preventOverflowScroll = grouped("Scrolling", add(new Setting.Toggle(
      "Prevent Overflow Scrolling", false, "The selection stays on the first or last slot instead of jumping to the other end.")));

  public ScrollTweaksModule() { super("Scroll Tweaks", true); }

  @Override public String category() { return "UTILITY"; }

  /** The enabled module, or null when it is off or unavailable. */
  public static ScrollTweaksModule active() {
    try {
      ScrollTweaksModule module = Features.get(ScrollTweaksModule.class);
      return module != null && module.enabled() ? module : null;
    } catch (Throwable failure) {
      return null;
    }
  }

  /** Slot (0-8) the selection should move to after one wheel notch, or {@link #LEAVE_TO_VANILLA}. */
  public int slotAfterWheel(int currentSlot, int notch) {
    if (disableScroll.get()) return currentSlot;
    boolean flipped = reverseScroll.get(), clamped = preventOverflowScroll.get();
    if (!flipped && !clamped) return LEAVE_TO_VANILLA;
    int direction = Integer.signum(notch);
    int target = currentSlot - (flipped ? -direction : direction);
    return clamped ? Math.max(0, Math.min(8, target)) : Math.floorMod(target, 9);
  }
}
