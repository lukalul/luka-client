package dev.cbui.feature.damagetint;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/** Chooses the colour (and fade) of the red flash a hurt entity gets. */
public final class DamageTintModule extends FeatureModule {
  public final Setting.Color color = add(new Setting.Color(
      "Damage Tint Color", 0x4DFF0000, "Colour and opacity of the flash on a hurt entity."));
  public final Setting.Toggle fade = add(new Setting.Toggle(
      "Fade Out Damage Tint", false, "The flash gets weaker as the hurt animation runs out."));
  public final Setting.Slider fadeDuration = add(new Setting.Slider(
      "Fade Duration (ticks)", 10f, 1f, 10f, 1f, "How many ticks the fade is spread over."));
  public final Setting.Toggle fadeDeath = add(new Setting.Toggle(
      "Fade Out Dead Entities", false, "Also fade the flash while an entity plays its death animation."));

  public final Setting.Toggle damageTypeColors = grouped("Damage Type Colors", add(new Setting.Toggle(
      "Separate Colors Per Damage Type", false, "Use a different flash colour depending on how the damage was dealt.")));
  public final Setting.Color melee = grouped("Damage Type Colors", add(new Setting.Color(
      "Melee Damage Color", 0x4DFF0000, "Flash for normal melee hits and any damage that cannot be told apart in 1.8.")));
  public final Setting.Color mace = grouped("Damage Type Colors", add(new Setting.Color(
      "Mace Damage Color", 0x4DFF0000, "Flash for mace hits, on versions that report them.")));
  public final Setting.Color ranged = grouped("Damage Type Colors", add(new Setting.Color(
      "Ranged Damage Color", 0x4DFF0000, "Flash for ranged hits, on versions that report them.")));
  public final Setting.Color explosion = grouped("Damage Type Colors", add(new Setting.Color(
      "Explosion Damage Color", 0x4DFF0000, "Flash for explosions, on versions that report them.")));
  public final Setting.Color magic = grouped("Damage Type Colors", add(new Setting.Color(
      "Magic Damage Color", 0x4DFF0000, "Flash for magic damage, on versions that report it.")));
  public final Setting.Color crit = grouped("Damage Type Colors", add(new Setting.Color(
      "Critical Hit Color", 0x66CFA62B, "Flash for hits that show the critical-hit sparkles.")));
  public final Setting.Color block = grouped("Damage Type Colors", add(new Setting.Color(
      "Blocked Hit Color", 0x99000000, "Flash when a player is hit while holding up a sword block.")));

  public DamageTintModule() { super("DamageTint", true); }

  @Override public String category() { return "VISUALS"; }

  /** The enabled module, or null when it is off or unavailable. */
  public static DamageTintModule active() {
    try {
      DamageTintModule module = Features.get(DamageTintModule.class);
      return module != null && module.enabled() ? module : null;
    } catch (Throwable failure) {
      return null;
    }
  }

  /** One 8-bit channel of an ARGB colour scaled to 0..1; {@code shift} is 24 for alpha, 16 red, 8 green, 0 blue. */
  public static float channel(int argb, int shift) { return (argb >>> shift & 255) / 255f; }

  private static float clamp01(float value) { return value < 0f ? 0f : value > 1f ? 1f : value; }

  /** The recorded damage variant of an entity, or {@link DamageTintState#OTHER} when it carries none. */
  public static int variantOf(Entity entity) {
    return entity instanceof DamageTintState ? ((DamageTintState) entity).cbui$getDamageVariant() : DamageTintState.OTHER;
  }

  public int colorFor(int variant) {
    if (!damageTypeColors.get()) return color.get();
    switch (variant) {
      case DamageTintState.CRIT: return crit.get();
      case DamageTintState.BLOCK: return block.get();
      case DamageTintState.MACE: return mace.get();
      case DamageTintState.RANGED: return ranged.get();
      case DamageTintState.EXPLOSION: return explosion.get();
      case DamageTintState.MAGIC: return magic.get();
      default: return melee.get();
    }
  }

  /** Opacity for a normal living entity this frame: the colour's own alpha, reduced by the fade rules when they are on. */
  public float alphaFor(int argb, EntityLivingBase entity) {
    float alpha = channel(argb, 24);
    if (!fade.get()) return alpha;
    float span = fadeDuration.get();
    if (entity.deathTime > 0) return fadeDeath.get() ? alpha * clamp01(1f - entity.deathTime / span) : alpha;
    return alpha * clamp01(entity.hurtTime / span);
  }

  /** Opacity for the ender dragon, whose renderer only knows about the hurt animation. */
  public float dragonAlphaFor(int argb, int hurtTicks) {
    float alpha = channel(argb, 24);
    return fade.get() ? alpha * clamp01(hurtTicks / fadeDuration.get()) : alpha;
  }
}
