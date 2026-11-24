package dev.cbui.feature.viewangle;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import java.util.UUID;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * Card "FOVChanger": decides how far movement, sprinting, flying, bow drawing and water widen or narrow the view.
 * Either keeps a share of each vanilla change, or swaps each state for an exact angle in degrees.
 */
public final class ViewAngleModule extends FeatureModule {
  /** Id of the speed bonus the game adds to the movement attribute while sprinting. */
  private static final UUID SPRINT_BONUS = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
  /** Vanilla's FOV numbers: flying adds a tenth, a full bow draw takes 15 % off, water scales by 60/70. */
  private static final float FLY_GAIN = 0.1f, BOW_CUT = 0.15f, WATER_REF = 70f, WATER_VANILLA = 60f;

  private final Setting.Toggle exact = grouped("Mode", add(new Setting.Toggle("Use fixed angles", false,
      "Set an exact angle for each state instead of scaling vanilla's changes")));

  private final Setting.Slider keepSpeed = share("Speed change strength",
      "Share of vanilla's FOV change from speed and slowness effects that is kept");
  private final Setting.Slider keepSprint = share("Sprint change strength", "Share of the sprinting FOV change that is kept");
  private final Setting.Slider keepBow = share("Bow draw strength", "Share of the bow zoom-in that is kept");
  private final Setting.Slider keepFlight = share("Flight change strength", "Share of the flying FOV change that is kept");
  private final Setting.Slider keepWater = share("Underwater change strength", "Share of the underwater narrowing that is kept");

  private final Setting.Slider angleSpeed = angle("Angle when sped up", 85, "View angle while a speed change is active (fixed angles only)");
  private final Setting.Slider angleSprint = angle("Angle when sprinting", 80, "View angle while sprinting (fixed angles only)");
  private final Setting.Slider angleBow = angle("Angle at full bow draw", 60, "View angle once the bow is fully drawn (fixed angles only)");
  private final Setting.Slider angleFlight = angle("Angle when flying", 77, "View angle while flying (fixed angles only)");
  private final Setting.Slider angleWater = angle("Angle under water", 60, "View angle with the camera under water (fixed angles only)");

  public ViewAngleModule() {
    super("FOVChanger", true);
  }

  private Setting.Slider share(String label, String tip) {
    return grouped("Strength of each change", add(new Setting.Slider(label, 100, 0, 100, 0.5f, tip)));
  }

  private Setting.Slider angle(String label, float value, String tip) {
    return grouped("Fixed angles", add(new Setting.Slider(label, value, 30, 110, 1, tip)));
  }

  @Override
  public String category() {
    return "VISUALS";
  }

  /** The module while it should act, else null. */
  public static ViewAngleModule active() {
    ViewAngleModule module = Features.get(ViewAngleModule.class);
    return module != null && module.enabled() ? module : null;
  }

  /** The multiplier vanilla's player FOV method should return for this player. */
  public float playerMultiplier(EntityPlayer player, float vanilla) {
    float walk = player.capabilities.getWalkSpeed();
    IAttributeInstance speedAttr = player.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
    if (speedAttr == null) return vanilla;
    double total = speedAttr.getAttributeValue();
    double sprintFactor = 1;
    AttributeModifier bonus = speedAttr.getModifier(SPRINT_BONUS);
    if (bonus != null && bonus.getOperation() == 2) sprintFactor = 1 + bonus.getAmount();
    double ratio = walk == 0f ? 1 : total / sprintFactor / walk;
    float draw = bowDraw(player);
    return exact.get() ? fixedMultiplier(player, ratio, sprintFactor > 1 || player.isSprinting(), draw)
        : scaledMultiplier(player, walk, ratio, sprintFactor, draw);
  }

  private float scaledMultiplier(EntityPlayer player, float walk, double ratio, double sprintFactor, float draw) {
    double out = 1;
    if (player.capabilities.isFlying) out *= 1 + FLY_GAIN * part(keepFlight);
    double effects = 1 + part(keepSpeed) * (ratio - 1);
    double sprint = 1 + part(keepSprint) * (sprintFactor - 1);
    out *= (effects * sprint + 1) / 2;
    if (walk == 0f || Double.isNaN(out) || Double.isInfinite(out)) out = 1;
    out *= 1 - draw * BOW_CUT * part(keepBow);
    return (float) out;
  }

  private float fixedMultiplier(EntityPlayer player, double ratio, boolean sprinting, float draw) {
    float own = mc.gameSettings.fovSetting;
    if (!(own > 0f)) return 1f;
    float target = own;
    if (Math.abs(ratio - 1) > 1.0E-4) target = angleSpeed.get();
    else if (sprinting) target = angleSprint.get();
    else if (player.capabilities.isFlying) target = angleFlight.get();
    target += (angleBow.get() - target) * draw;
    float out = target / own;
    return Float.isNaN(out) || Float.isInfinite(out) ? 1f : out;
  }

  /** How far the bow is drawn, eased like vanilla (0 = not drawing, 1 = full). */
  private static float bowDraw(EntityPlayer player) {
    if (!player.isUsingItem()) return 0f;
    ItemStack using = player.getItemInUse();
    if (using == null || using.getItem() != Items.bow) return 0f;
    float charge = player.getItemInUseDuration() / 20f;
    return charge >= 1f ? 1f : charge * charge;
  }

  /**
   * Replaces the 60 in vanilla's underwater step (fov * 60 / 70). Strength mode keeps a share of the narrowing; fixed
   * mode makes the water angle the base the other states scale from.
   */
  public float waterNumerator(float vanilla) {
    if (exact.get()) {
      float own = mc.gameSettings.fovSetting;
      return own > 0f ? WATER_REF * angleWater.get() / own : vanilla;
    }
    return WATER_REF - (WATER_REF - WATER_VANILLA) * part(keepWater);
  }

  private static float part(Setting.Slider slider) {
    return slider.get() / 100f;
  }
}
