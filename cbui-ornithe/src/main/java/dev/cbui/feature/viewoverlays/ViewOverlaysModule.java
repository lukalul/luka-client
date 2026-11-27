package dev.cbui.feature.viewoverlays;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.potion.Potion;

/**
 * Knobs for the pictures the game lays over the first-person view: the hand with F1, the arm while invisible, the
 * elder guardian flash, the water ripple, the flames, the block texture when stuck in a wall, the portal swirl and the
 * pumpkin mask. Every default leaves vanilla's picture untouched.
 */
public final class ViewOverlaysModule extends FeatureModule {
  private static final String ARM = "Your arm";
  private static final String CURSE = "Guardian curse";
  private static final String WATER = "Underwater";
  private static final String LAVA = "Lava";
  private static final String FIRE = "Burning";
  private static final String BLOCK = "Inside blocks";
  private static final String PORTAL = "Portal";
  private static final String PUMPKIN = "Pumpkin";

  private final Setting.Toggle handWithoutHud = grouped(ARM, add(new Setting.Toggle(
      "Draw arm when F1 hides HUD", false, "Your hand and held item stay visible while the HUD is hidden.")));
  private final Setting.Slider invisibleArm = grouped(ARM, add(new Setting.Slider(
      "Invisible arm visibility", 0, 0, 100, 1, "How much of your bare arm shows while invisible. 0 is vanilla (hidden).")));
  private final Setting.Slider curseStrength = grouped(CURSE, add(new Setting.Slider(
      "Curse flash strength", 100, 0, 100, 1, "How strongly the elder guardian's face shows. 0 hides it.")));
  private final Setting.Slider curseSize = grouped(CURSE, add(new Setting.Slider(
      "Curse flash size", 1, 0.1f, 2, 0.1f, "Size of the elder guardian's face compared to vanilla.")));
  private final Setting.Toggle clearWater = grouped(WATER, add(new Setting.Toggle(
      "Clear underwater view", false, "Drops the rippling texture over the screen underwater.")));
  private final Setting.Toggle clearWaterFog = grouped(WATER, add(new Setting.Toggle(
      "Clear water", false, "See through water as if it were air: no blue fog or short view distance (and no ripple texture). The water itself still shows.")));
  private final Setting.Toggle clearLava = grouped(LAVA, add(new Setting.Toggle(
      "Clear lava", false, "See through lava as if it were air: no red fog or short view distance. The lava and its surface still show.")));
  private final Setting.Toggle fireproofNoFlames = grouped(FIRE, add(new Setting.Toggle(
      "No flames when fireproof", false, "Skips the flames on screen in creative mode or with fire resistance.")));
  private final Setting.Slider flameStrength = grouped(FIRE, add(new Setting.Slider(
      "Flame strength", 100, 0, 100, 1, "How visible the on-screen flames are. 0 never draws them.")));
  private final Setting.Slider flameDrop = grouped(FIRE, add(new Setting.Slider(
      "Lower the flames", 0, 0, 50, 1, "Pushes the on-screen flames further down.")));
  private final Setting.Slider blockBrightness = grouped(BLOCK, add(new Setting.Slider(
      "Block view brightness", 10, 0, 100, 1, "How light the block texture is when your head is stuck in a block.")));
  private final Setting.Slider portalStrength = grouped(PORTAL, add(new Setting.Slider(
      "Swirl strength", 100, 0, 100, 1, "How strong the purple portal overlay gets. 0 hides it.")));
  private final Setting.Slider pumpkinStrength = grouped(PUMPKIN, add(new Setting.Slider(
      "Pumpkin mask strength", 100, 0, 100, 1, "How visible the pumpkin view is. 0 hides it.")));

  /** True between the moment the arm fade switched blending on and the moment it put it back. */
  private boolean armFadeApplied;

  public ViewOverlaysModule() { super("Overlay Tweaks", true); }

  @Override public String category() { return "VISUALS"; }

  /** The module when it exists and is switched on, otherwise null. */
  public static ViewOverlaysModule active() {
    try {
      ViewOverlaysModule module = Features.get(ViewOverlaysModule.class);
      return module != null && module.enabled() ? module : null;
    } catch (Throwable failure) {
      return null;
    }
  }

  private static float fraction(Setting.Slider slider) {
    return Math.max(0f, Math.min(1f, slider.get() / 100f));
  }

  // ---- hand and arm ----

  /** What the hand pass should see as "HUD hidden": the real value unless the player wants the hand kept. */
  public boolean handPassSeesHiddenHud(boolean hidden) {
    return hidden && !handWithoutHud.get();
  }

  /** What the bare-arm pass should see as "player is invisible". Above 0 % the arm is drawn (faded in by {@link #armFadeOn}). */
  public boolean armPassSeesInvisible(boolean invisible) {
    return invisible && invisibleArm.get() <= 0f;
  }

  /** Alpha for your own first-person arm, or 1 when no fade is needed. */
  private float armAlpha() {
    Entity owner = mc.thePlayer;
    if (owner == null || !owner.isInvisible()) return 1f;
    return fraction(invisibleArm);
  }

  /** Called right before the arm model is drawn; turns blending on with the slider's alpha when the player is invisible. */
  public void armFadeOn() {
    armFadeApplied = false;
    float alpha = armAlpha();
    if (alpha >= 1f) return;
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GlStateManager.color(1f, 1f, 1f, alpha);
    armFadeApplied = true;
  }

  /** Called after the arm model, also when drawing it failed. */
  public void armFadeOff() {
    if (!armFadeApplied) return;
    armFadeApplied = false;
    GlStateManager.color(1f, 1f, 1f, 1f);
    GlStateManager.disableBlend();
  }

  // ---- guardian curse ----

  public boolean skipCurseFlash() { return curseStrength.get() <= 0f; }

  public float curseAlpha(float vanillaAlpha) {
    float strength = fraction(curseStrength);
    return strength >= 1f ? vanillaAlpha : vanillaAlpha * strength;
  }

  public float curseScale(float vanillaScale) {
    float size = curseSize.get();
    return Math.abs(size - 1f) < 1e-3f ? vanillaScale : vanillaScale * size;
  }

  // ---- water ----

  public boolean skipWaterTexture() { return clearWater.get() || clearWaterFog.get(); }

  // ---- clear water and lava ----

  /**
   * The block fog and fog colour should treat the camera as being in: air when Clear water or Clear lava covers the liquid the
   * camera is in, so the game fogs as in the open (vanilla's own air fog, which also follows luka's fog options). The
   * liquid blocks themselves are drawn as always.
   */
  public static Block fogBlock(Block block) {
    if (block == null) return null;
    Material material = block.getMaterial();
    if (material != Material.water && material != Material.lava) return block;
    ViewOverlaysModule module = active();
    if (module == null) return block;
    if (material == Material.water ? module.clearWaterFog.get() : module.clearLava.get()) return Blocks.air;
    return block;
  }

  // ---- fire ----

  /** True when the flames should not be drawn at all this frame. */
  public boolean skipFlames() {
    if (flameStrength.get() <= 0f) return true;
    if (!fireproofNoFlames.get()) return false;
    EntityPlayerSP player = mc.thePlayer;
    if (player == null) return false;
    return player.capabilities.isCreativeMode || player.isPotionActive(Potion.fireResistance);
  }

  public float flameAlpha(float vanillaAlpha) {
    float strength = fraction(flameStrength);
    return strength >= 1f ? vanillaAlpha : vanillaAlpha * strength;
  }

  /** The flame quad is one unit tall; the slider moves it down by that fraction of its height. */
  public float flameHeight(float vanillaY) {
    float drop = Math.max(0f, Math.min(0.5f, flameDrop.get() / 100f));
    return drop <= 0f ? vanillaY : vanillaY - drop;
  }

  // ---- inside a block ----

  /** Brightness for the block texture; returns the vanilla value untouched at the default. */
  public float blockShade(float vanillaShade) {
    float wanted = fraction(blockBrightness);
    return Math.abs(wanted - 0.1f) < 1e-4f ? vanillaShade : wanted;
  }

  // ---- portal and pumpkin ----

  public boolean skipPortal() { return portalStrength.get() <= 0f; }

  public float portalAlpha(float vanillaAlpha) {
    float strength = fraction(portalStrength);
    return strength >= 1f ? vanillaAlpha : vanillaAlpha * strength;
  }

  public boolean skipPumpkin() { return pumpkinStrength.get() <= 0f; }

  public float pumpkinAlpha(float vanillaAlpha) {
    float strength = fraction(pumpkinStrength);
    return strength >= 1f ? vanillaAlpha : vanillaAlpha * strength;
  }
}
