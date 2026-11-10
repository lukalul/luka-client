package dev.cbui.feature.fovchanger;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.init.Items;

/**
 * Takes over the field-of-view changes vanilla 1.8.9 applies for speed, sprinting, bow drawing, flying and swimming. Either each
 * effect is scaled by a percentage, or (Absolute mode) each state gets an exact FOV in degrees.
 */
public final class FOVChangerModule extends FeatureModule {
  /** Vanilla's FOV (degrees) used while the camera is underwater, before the effect is applied. */
  private static final float VANILLA_WATER_FOV = 70f;
  /** Vanilla multiplies the move speed attribute by this while sprinting. */
  private static final float SPRINT_SPEED_FACTOR = 1.3f;

  public final Setting.Toggle absolute = grouped("Mode", add(new Setting.Toggle(
      "Absolute FOV", false, "Use fixed FOV values for each state instead of scaling vanilla's effects by a percentage.")));

  public final Setting.Slider speed = grouped("Relative effects", add(percent("Speed FOV Effects")));
  public final Setting.Slider sprint = grouped("Relative effects", add(percent("Sprint FOV Effects")));
  public final Setting.Slider aiming = grouped("Relative effects", add(percent("Aiming FOV Effects")));
  public final Setting.Slider flying = grouped("Relative effects", add(percent("Flying FOV Effects")));
  public final Setting.Slider submerged = grouped("Relative effects", add(percent("Submerged FOV Effects")));

  public final Setting.Slider speedFov = grouped("Absolute values", add(degrees("Speed FOV", 85f)));
  public final Setting.Slider sprintFov = grouped("Absolute values", add(degrees("Sprint FOV", 80f)));
  public final Setting.Slider aimingFov = grouped("Absolute values", add(degrees("Aiming FOV", 60f)));
  public final Setting.Slider flyingFov = grouped("Absolute values", add(degrees("Flying FOV", 77f)));
  public final Setting.Slider submergedFov = grouped("Absolute values", add(degrees("Submerged FOV", 60f)));

  public FOVChangerModule() { super("FOVChanger", true); }

  @Override public String category() { return "VISUALS"; }

  private static Setting.Slider percent(String label) {
    return new Setting.Slider(label, 100f, 0f, 100f, .5f, "How much of vanilla's effect is kept, in percent (100 = unchanged, 0 = none).");
  }

  private static Setting.Slider degrees(String label, float initial) {
    return new Setting.Slider(label, initial, 30f, 110f, 1f, "FOV in degrees for this state when Absolute FOV is on.");
  }

  /** The enabled module, or null when it is off or unavailable. */
  public static FOVChangerModule active() {
    try {
      FOVChangerModule module = Features.get(FOVChangerModule.class);
      return module != null && module.enabled() ? module : null;
    } catch (Throwable failure) {
      return null;
    }
  }

  /** What the player is doing that vanilla's FOV modifier reacts to. */
  private static final class Movement {
    final float actual;
    final float walk;
    final boolean sprinting;
    final boolean flying;

    Movement(AbstractClientPlayer player) {
      actual = (float) player.getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue();
      walk = player.capabilities.getWalkSpeed();
      sprinting = player.isSprinting();
      flying = player.capabilities.isFlying;
    }
  }

  /** The FOV multiplier for the player (1 = no change); {@code vanilla} is returned untouched if anything goes wrong. */
  public float playerModifier(AbstractClientPlayer player, float vanilla) {
    try {
      Movement move = new Movement(player);
      float factor = absolute.get() ? absoluteFactor(move) : relativeFactor(move);
      if (move.walk == 0f || Float.isNaN(factor) || Float.isInfinite(factor)) factor = 1f;
      return applyBowDraw(player, factor);
    } catch (Throwable failure) {
      return vanilla;
    }
  }

  private float absoluteFactor(Movement move) {
    float comparable = move.sprinting ? move.actual / SPRINT_SPEED_FACTOR : move.actual;
    if (Math.abs(comparable - move.walk) > 0.0001f) return asMultiplier(speedFov.get());
    if (move.sprinting) return asMultiplier(sprintFov.get());
    if (move.flying) return asMultiplier(flyingFov.get());
    return 1f;
  }

  private float relativeFactor(Movement move) {
    float factor = 1f;
    if (move.flying) factor *= 1f + 0.1f * flying.get() / 100f;
    if (move.actual == move.walk) return factor;
    float kept = speed.get() / 100f;
    if (move.sprinting) {
      float shifted = (move.actual / SPRINT_SPEED_FACTOR - move.walk) * kept;
      float boost = 0.3f * sprint.get() / 100f;
      double blended = (move.walk + shifted) * (1f + boost);
      return factor * (float) ((blended / move.walk + 1d) / 2d);
    }
    float shifted = (move.actual - move.walk) * kept;
    return factor * (((shifted + move.walk) / move.walk + 1f) / 2f);
  }

  private float applyBowDraw(AbstractClientPlayer player, float factor) {
    if (!player.isUsingItem() || player.getItemInUse().getItem() != Items.bow) return factor;
    float draw = Math.min(player.getItemInUseDuration() / 20f, 1f);
    draw *= draw;
    if (absolute.get()) return factor + (asMultiplier(aimingFov.get()) - factor) * draw;
    return factor * (1f - draw * 0.15f * aiming.get() / 100f);
  }

  /** Replacement for the 60-degree constant vanilla uses in the water FOV formula. */
  public float submergedConstant(float vanilla) {
    if (absolute.get()) return VANILLA_WATER_FOV * asMultiplier(submergedFov.get());
    return VANILLA_WATER_FOV - (VANILLA_WATER_FOV - vanilla) * submerged.get() / 100f;
  }

  /** Converts a wanted FOV in degrees into the multiplier vanilla applies to the player's FOV setting. */
  private float asMultiplier(float wantedDegrees) {
    float setting = mc.gameSettings == null ? 70f : mc.gameSettings.fovSetting;
    return setting == 0f ? 1f : wantedDegrees / setting;
  }
}
