package dev.cbui.feature.shaketweaks;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;

/** Individual switches for the camera and hand movement effects (walking bob, hurt tilt, hand sway). */
public final class ShakeTweaksModule extends FeatureModule {
  public final Setting.Toggle disableScreenBobbing = add(new Setting.Toggle(
      "Disable Screen Bobbing", false, "The view no longer rocks while you walk."));
  public final Setting.Toggle disableHandBobbing = add(new Setting.Toggle(
      "Disable Hand Bobbing", false, "The held item no longer rocks while you walk."));
  public final Setting.Toggle disableHorizontalBobbing = add(new Setting.Toggle(
      "Disable Horizontal Bobbing", false,
      "Keeps the up-and-down bob but removes the sideways sway, for both the view and the hand."));
  public final Setting.Toggle disableMapBobbing = add(new Setting.Toggle(
      "Disable Map Bobbing", false,
      "Stops the hand bobbing only while a map is held. Has no effect if hand bobbing is already disabled."));
  public final Setting.Toggle disableHandDamage = add(new Setting.Toggle(
      "Disable Hand Damage Tilt", false, "The held item no longer jolts when you are hurt."));
  public final Setting.Toggle disableScreenDamage = add(new Setting.Toggle(
      "Disable Screen Damage Tilt", false, "The view no longer tilts when you are hurt."));
  public final Setting.Toggle disableHandViewSway = add(new Setting.Toggle(
      "Disable Hand View Sway", false, "The held item stays put instead of lagging behind mouse movement."));

  public ShakeTweaksModule() { super("Shake Tweaks", true); }

  @Override public String category() { return "UTILITY"; }

  /** The enabled module, or null when it is off or unavailable. */
  public static ShakeTweaksModule active() {
    try {
      ShakeTweaksModule module = Features.get(ShakeTweaksModule.class);
      return module != null && module.enabled() ? module : null;
    } catch (Throwable failure) {
      return null;
    }
  }

  public boolean allowScreenBobbing() { return !disableScreenBobbing.get(); }

  public boolean allowScreenDamageTilt() { return !disableScreenDamage.get(); }

  public boolean allowHandDamageTilt() { return !disableHandDamage.get(); }

  public boolean allowHandViewSway() { return !disableHandViewSway.get(); }

  public boolean flattenSidewaysBob() { return disableHorizontalBobbing.get(); }

  /** Hand bobbing is blocked outright, or only while a map is in hand when the map option is on. */
  public boolean allowHandBobbing() {
    if (disableHandBobbing.get()) return false;
    if (!disableMapBobbing.get()) return true;
    Minecraft mc = Minecraft.getMinecraft();
    ItemStack held = mc.thePlayer == null ? null : mc.thePlayer.getHeldItem();
    return held == null || !(held.getItem() instanceof ItemMap);
  }
}
