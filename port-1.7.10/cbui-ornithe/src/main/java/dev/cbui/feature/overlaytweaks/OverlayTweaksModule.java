package dev.cbui.feature.overlaytweaks;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import dev.luka.compat.GlStateManager;
import net.minecraft.potion.Potion;

/** Adjusts the things drawn over the view: your hand, fire, water, pumpkin, portal, suffocation and elder guardian effects. */
public final class OverlayTweaksModule extends FeatureModule {
  public final Setting.Toggle keepHand = grouped("Hand", add(new Setting.Toggle(
      "Keep Hand in Hidden HUD", false, "Your hand and held item stay on screen when F1 hides the HUD.")));
  public final Setting.Slider handInvisibilityOpacity = grouped("Hand", add(new Setting.Slider(
      "Hand Invisibility Opacity", 0f, 0f, 100f, 1f,
      "While invisible, still draw your hand at this opacity. 0 keeps it hidden.")));
  public final Setting.Slider elderGuardianOpacity = grouped("Elder Guardian", add(new Setting.Slider(
      "Elder Guardian Opacity", 100f, 0f, 100f, 1f,
      "How visible the elder guardian's jump-scare is.")));
  public final Setting.Slider elderGuardianScale = grouped("Elder Guardian", add(new Setting.Slider(
      "Elder Guardian Scale", 1f, .1f, 2f, .1f,
      "How large the elder guardian's jump-scare is.")));
  public final Setting.Toggle removeWaterOverlay = grouped("Liquids", add(new Setting.Toggle(
      "Remove Water Overlay", false, "Hides the wavy texture drawn over the screen underwater.")));
  public final Setting.Toggle removeFireOverlay = grouped("Fire", add(new Setting.Toggle(
      "Remove Fire Overlay When Resistant", false,
      "Hides the burning view when creative mode or fire resistance means you cannot be hurt by it.")));
  public final Setting.Slider fireOverlayHeight = grouped("Fire", add(new Setting.Slider(
      "Fire Overlay Height", 0f, -.5f, 0f, .01f, "Moves the burning view lower on the screen.")));
  public final Setting.Slider fireOverlayOpacity = grouped("Fire", add(new Setting.Slider(
      "Fire Overlay Opacity", 100f, 0f, 100f, 1f, "How see-through the burning view is.")));
  public final Setting.Slider suffocationOverlayBrightness = grouped("Environmental", add(new Setting.Slider(
      "Suffocation Overlay Brightness", 10f, 0f, 100f, 1f,
      "How bright the block texture is when you are stuck inside a block.")));
  public final Setting.Slider netherPortalOpacity = grouped("Environmental", add(new Setting.Slider(
      "Nether Portal Opacity", 100f, 0f, 100f, 1f,
      "Strongest the purple portal swirl can get.")));
  public final Setting.Slider equipableOpacity = grouped("Equippables", add(new Setting.Slider(
      "Equippable Overlay Opacity", 100f, 0f, 100f, 1f,
      "How visible things worn on the head, like a pumpkin, are on the screen.")));

  /** Render thread only: true while the hand-invisibility fade has switched blending on for the first-person arm. */
  public static volatile boolean armBlend;

  public OverlayTweaksModule() {
    super("Overlay Tweaks", true);
  }

  @Override public String category() { return "VISUALS"; }

  /** The enabled module, or null when it is off or unavailable. */
  public static OverlayTweaksModule active() {
    try {
      OverlayTweaksModule module = Features.get(OverlayTweaksModule.class);
      return module != null && module.enabled() ? module : null;
    } catch (Throwable failure) {
      return null;
    }
  }

  /** A 0-100 slider as a 0..1 fraction. */
  private static float fraction(Setting.Slider slider) { return slider.get() / 100F; }

  public float pumpkinAlpha() { return fraction(equipableOpacity); }

  public float portalAlphaScale() { return fraction(netherPortalOpacity); }

  public float guardianAlphaScale() { return fraction(elderGuardianOpacity); }

  public float suffocationBrightness() { return fraction(suffocationOverlayBrightness); }

  /** The opacity multiplier for the fire overlay, or null when it should stay at vanilla's own value. */
  public Float fireAlpha() {
    return fireOverlayOpacity.get() >= 100F ? null : fraction(fireOverlayOpacity);
  }

  /** True when the burning-view overlay should not be drawn at all for this player. */
  public boolean hidesFire(EntityPlayerSP player) {
    if (fireOverlayOpacity.get() <= 0F) return true;
    if (!removeFireOverlay.get() || player == null) return false;
    return player.capabilities.isCreativeMode || player.isPotionActive(Potion.fireResistance);
  }

  public boolean showsInvisibleHand() { return handInvisibilityOpacity.get() > 0F; }

  /** Called just before an arm model is drawn: fades your own arm if you are invisible and a fade is configured. */
  public static void beginArmFade(AbstractClientPlayer player) {
    try {
      OverlayTweaksModule module = active();
      if (module == null || !player.isInvisible() || player != Minecraft.getMinecraft().thePlayer) return;
      float alpha = fraction(module.handInvisibilityOpacity);
      if (alpha <= 0F || alpha >= 1F) return;
      GlStateManager.enableBlend();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
      GlStateManager.color(1F, 1F, 1F, alpha);
      armBlend = true;
    } catch (Throwable failure) {
      // draw the arm normally
    }
  }

  /** Called after an arm is drawn: undoes whatever {@link #beginArmFade} switched on. */
  public static void endArmFade() {
    try {
      if (!armBlend) return;
      armBlend = false;
      GlStateManager.disableBlend();
      GlStateManager.color(1F, 1F, 1F, 1F);
    } catch (Throwable failure) {
      // nothing to undo
    }
  }
}
