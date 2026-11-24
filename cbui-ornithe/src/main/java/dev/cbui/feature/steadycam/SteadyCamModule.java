package dev.cbui.feature.steadycam;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;

/**
 * Separate switches for the movements vanilla adds to the view and the first-person hand: the walking bob, the tilt
 * when hurt and the lag of the held item behind quick turns. Every switch only removes an effect.
 */
public final class SteadyCamModule extends FeatureModule {
  private static final String WALKING = "Walking";
  private static final String HURT = "Getting hurt";
  private static final String TURNING = "Turning";

  private final Setting.Toggle viewBobOff = grouped(WALKING, add(new Setting.Toggle(
      "Camera walking bob off", false, "Keep the view steady while you walk")));
  private final Setting.Toggle handBobOff = grouped(WALKING, add(new Setting.Toggle(
      "Hand walking bob off", false, "Keep the held item steady while you walk")));
  private final Setting.Toggle mapHandStill = grouped(WALKING, add(new Setting.Toggle(
      "Hand still while holding a map", false, "No hand bob while a map is in your hand")));
  private final Setting.Toggle verticalBobOnly = grouped(WALKING, add(new Setting.Toggle(
      "Up-and-down bob only", false, "Remove the side-to-side sway of the bob, for view and hand")));
  private final Setting.Toggle viewHurtOff = grouped(HURT, add(new Setting.Toggle(
      "Camera hurt tilt off", false, "The view does not tilt when you take damage")));
  private final Setting.Toggle handHurtOff = grouped(HURT, add(new Setting.Toggle(
      "Hand hurt tilt off", false, "The held item does not jolt when you take damage")));
  private final Setting.Toggle rigidHand = grouped(TURNING, add(new Setting.Toggle(
      "Hand follows the view rigidly", false, "The held item no longer trails behind quick mouse turns")));

  public SteadyCamModule() {
    super("Shake Tweaks", true);
  }

  @Override public String category() { return "UTILITY"; }

  /** The module when it exists and is switched on, else null. */
  public static SteadyCamModule active() {
    SteadyCamModule module = Features.get(SteadyCamModule.class);
    return module != null && module.enabled() ? module : null;
  }

  /** False when the world pass should skip the walking bob. */
  public boolean keepsViewBob() { return !viewBobOff.get(); }

  /** False when the hand pass should skip the walking bob (always, or only while a map is held). */
  public boolean keepsHandBob() {
    if (handBobOff.get()) return false;
    return !(mapHandStill.get() && holdsMap());
  }

  /** True when the sideways shift and roll of the bob should be zeroed. */
  public boolean verticalBobOnly() { return verticalBobOnly.get(); }

  /** False when the world pass should skip the hurt tilt. */
  public boolean keepsViewHurt() { return !viewHurtOff.get(); }

  /** False when the hand pass should skip the hurt tilt. */
  public boolean keepsHandHurt() { return !handHurtOff.get(); }

  /** False when the held item should not lag behind the view. */
  public boolean keepsHandLag() { return !rigidHand.get(); }

  private boolean holdsMap() {
    EntityPlayerSP player = mc.thePlayer;
    if (player == null) return false;
    ItemStack held = player.getHeldItem();
    return held != null && held.getItem() instanceof ItemMap;
  }
}
