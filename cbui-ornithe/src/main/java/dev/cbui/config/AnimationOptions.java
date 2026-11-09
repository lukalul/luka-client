package dev.cbui.config;

import net.minecraft.util.EnumParticleTypes;

/** Stable config keys and display names used by both the GUI and rendering mixins. */
public final class AnimationOptions {
  public static final String[] OTHER_KEYS = {"water_animation", "lava_animation", "fire_animation",
      "portal_animation", "redstone_dust", "rain_snow", "sky_clouds", "void_particles",
      "item_block_break", "damage_numbers", "sun_moon", "stars", "terrain_fog", "fluid_fog"};
  public static final String[] OTHER_NAMES = {"Water animation", "Lava animation", "Fire animation",
      "Portal animation", "Redstone dust", "Rain / snow", "Sky / clouds drift", "Void particles",
      "Item / block break particles", "Damage numbers", "Sun and moon", "Stars", "Distance fog", "Fog in water and lava"};

  /** Set each frame by the fog hook: the distance fog row is off and the camera is not in a liquid. */
  public static volatile boolean distanceFogCleared;
  /** Set by the same hook while the Fog start or Fog end rows differ from vanilla: the distances the terrain should use. */
  public static volatile boolean fogAdjusted;
  public static volatile float fogStartNow, fogEndNow;

  private AnimationOptions() {}

  public static String particleKey(EnumParticleTypes type) {
    return "particle." + type.name().toLowerCase(java.util.Locale.ROOT);
  }

  public static boolean particleEnabled(EnumParticleTypes type) {
    if (!GlobalOptions.animationEnabled(particleKey(type))) return false;
    if (type == EnumParticleTypes.REDSTONE && !GlobalOptions.animationEnabled("redstone_dust")) return false;
    if (type == EnumParticleTypes.SUSPENDED_DEPTH && !GlobalOptions.animationEnabled("void_particles")) return false;
    return !isBreak(type) || GlobalOptions.animationEnabled("item_block_break");
  }

  private static boolean isBreak(EnumParticleTypes type) {
    return type == EnumParticleTypes.ITEM_CRACK || type == EnumParticleTypes.BLOCK_CRACK
        || type == EnumParticleTypes.BLOCK_DUST || type == EnumParticleTypes.ITEM_TAKE;
  }

  public static String readable(EnumParticleTypes type) {
    String[] words = type.name().toLowerCase(java.util.Locale.ROOT).split("_");
    StringBuilder out = new StringBuilder();
    for (String word : words) {
      if (out.length() > 0) out.append(' ');
      out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
    }
    return out.toString().replace("Crit Magic", "Magic crit").replace("Spell Mob", "Mob spell")
        .replace("Villager Angry", "Angry villager").replace("Villager Happy", "Happy villager");
  }
}
