package dev.cbui.config;

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

  private AnimationOptions() {}

  /** Every particle name RenderGlobal.doSpawnParticle knows in 1.7.10, with the words shown on the settings page. */
  public static final String[][] PARTICLES = {
      {"hugeexplosion", "Huge explosion"}, {"largeexplode", "Large explosion"}, {"fireworksSpark", "Firework spark"},
      {"bubble", "Bubble"}, {"suspended", "Suspended"}, {"depthsuspend", "Depth suspend"}, {"townaura", "Town aura"},
      {"crit", "Crit"}, {"magicCrit", "Magic crit"}, {"smoke", "Smoke"}, {"mobSpell", "Mob spell"},
      {"mobSpellAmbient", "Mob spell ambient"}, {"spell", "Spell"}, {"instantSpell", "Instant spell"},
      {"witchMagic", "Witch magic"}, {"note", "Note"}, {"portal", "Portal"}, {"enchantmenttable", "Enchantment table"},
      {"explode", "Explosion"}, {"flame", "Flame"}, {"lava", "Lava"}, {"footstep", "Footstep"}, {"splash", "Splash"},
      {"wake", "Wake"}, {"largesmoke", "Large smoke"}, {"cloud", "Cloud"}, {"reddust", "Redstone"},
      {"snowballpoof", "Snowball"}, {"dripWater", "Drip water"}, {"dripLava", "Drip lava"}, {"snowshovel", "Snow shovel"},
      {"slime", "Slime"}, {"heart", "Heart"}, {"angryVillager", "Angry villager"}, {"happyVillager", "Happy villager"},
      {"iconcrack", "Item crack"}, {"blockcrack", "Block crack"}, {"blockdust", "Block dust"}
  };

  /** 1.7.10 builds the item and block crack names with numbers on the end ("iconcrack_260_0"). */
  private static String baseName(String particle) {
    int cut = particle.indexOf('_');
    return cut > 0 ? particle.substring(0, cut) : particle;
  }

  public static String particleKey(String particle) {
    return "particle." + baseName(particle).toLowerCase(java.util.Locale.ROOT);
  }

  public static boolean particleEnabled(String particle) {
    if (!GlobalOptions.animationEnabled(particleKey(particle))) return false;
    String base = baseName(particle);
    if ("reddust".equals(base) && !GlobalOptions.animationEnabled("redstone_dust")) return false;
    if (("suspended".equals(base) || "depthsuspend".equals(base)) && !GlobalOptions.animationEnabled("void_particles")) return false;
    return !isBreak(base) || GlobalOptions.animationEnabled("item_block_break");
  }

  private static boolean isBreak(String base) {
    return "iconcrack".equals(base) || "blockcrack".equals(base) || "blockdust".equals(base);
  }
}
