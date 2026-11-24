package dev.cbui.feature.soundmixer;

/**
 * Which slider each vanilla sound belongs to, built from the sound event names of 1.8.9 and 1.7.10. The values are
 * the slider labels. Full names are used where a sound must leave its family (a zombie's hurt sound belongs with the
 * other hurt sounds, not with zombie voices); the narrow creature groups are longer starts than "mob.", so they win
 * over Creature voices.
 */
public final class SoundGroups {
  public static final String PLAYERS_HURT = "Players getting hurt";
  public static final String MOBS_HURT = "Mobs getting hurt";
  public static final String BOW = "Bow and arrow";
  public static final String DING = "Arrow hit ding";
  public static final String BLASTS = "Blasts and fuses";
  public static final String STEPS = "Footsteps";
  public static final String DIGGING = "Mining and building";
  public static final String LIQUIDS = "Water and lava";
  public static final String FIRE = "Fire and sizzling";
  public static final String WEATHER = "Weather and caves";
  public static final String PORTALS = "Nether portals";
  public static final String CONTAINERS = "Chests and doors";
  public static final String REDSTONE = "Redstone parts";
  public static final String MENU = "Menu clicks";
  public static final String ANVILS = "Anvils";
  public static final String EATING = "Eating and drinking";
  public static final String POTIONS = "Splash potions";
  public static final String PICKUPS = "Item pickups";
  public static final String BROKEN = "Broken tools";
  public static final String XP = "Experience";
  public static final String FIREWORKS = "Fireworks";
  public static final String MINECARTS = "Minecarts";
  public static final String NOTES = "Note blocks";
  public static final String CREATURES = "Creature voices";
  public static final String VILLAGERS = "Villagers";
  public static final String ENDERMEN = "Endermen";
  public static final String BOSSES = "Bosses";
  public static final String MUSIC = "Background music";
  public static final String RECORDS = "Jukebox records";

  /** Hurt and death sounds of ordinary mobs (bosses keep theirs in Bosses). */
  private static final String[] MOB_PAIN = {
      "mob.bat.death", "mob.bat.hurt", "mob.blaze.death", "mob.blaze.hit", "mob.cat.hitt", "mob.chicken.hurt",
      "mob.cow.hurt", "mob.creeper.death", "mob.endermen.death", "mob.endermen.hit", "mob.ghast.death",
      "mob.ghast.scream", "mob.guardian.death", "mob.guardian.hit", "mob.guardian.elder.death",
      "mob.guardian.elder.hit", "mob.guardian.land.death", "mob.guardian.land.hit", "mob.horse.death",
      "mob.horse.hit", "mob.horse.donkey.death", "mob.horse.donkey.hit", "mob.horse.skeleton.death",
      "mob.horse.skeleton.hit", "mob.horse.zombie.death", "mob.horse.zombie.hit", "mob.irongolem.death",
      "mob.irongolem.hit", "mob.magmacube.big", "mob.magmacube.small", "mob.pig.death", "mob.rabbit.death",
      "mob.rabbit.hurt", "mob.silverfish.hit", "mob.silverfish.kill", "mob.skeleton.death", "mob.skeleton.hurt",
      "mob.slime.big", "mob.slime.small", "mob.spider.death", "mob.villager.death", "mob.villager.hit",
      "mob.wolf.death", "mob.wolf.hurt", "mob.zombie.death", "mob.zombie.hurt", "mob.zombiepig.zpigdeath",
      "mob.zombiepig.zpighurt",
  };

  private SoundGroups() {}

  /**
   * The volume in percent for a sound: a player rule wins, then the slider of the sound's group, else 100.
   * {@code sliderPercent} gives a slider's value from its label.
   */
  public static float percentFor(NameTable<Float> rules, NameTable<String> groups,
      java.util.function.ToDoubleFunction<String> sliderPercent, String name) {
    Float rule = rules.find(name);
    if (rule != null) return rule;
    String label = groups.find(name);
    return label == null ? 100f : (float) sliderPercent.applyAsDouble(label);
  }

  /** Every slider label, in menu order. */
  public static String[] labels() {
    return new String[] {
        PLAYERS_HURT, MOBS_HURT, BOW, DING, BLASTS,
        STEPS, DIGGING, LIQUIDS, FIRE, WEATHER, PORTALS,
        CONTAINERS, REDSTONE, MENU, ANVILS, EATING, POTIONS, PICKUPS, BROKEN, XP, FIREWORKS, MINECARTS, NOTES,
        CREATURES, VILLAGERS, ENDERMEN, BOSSES,
        MUSIC, RECORDS,
    };
  }

  /** The built-in name to slider-label table. */
  public static NameTable<String> table() {
    NameTable<String> t = new NameTable<String>();
    // combat
    t.startingWith("game.player.", PLAYERS_HURT);
    t.startingWith("game.hostile.", MOBS_HURT);
    t.startingWith("game.neutral.", MOBS_HURT);
    for (String name : MOB_PAIN) t.whole(name, MOBS_HURT);
    t.whole("random.bow", BOW);
    t.whole("random.bowhit", BOW);
    t.whole("random.successful_hit", DING);
    t.whole("random.explode", BLASTS);
    t.whole("game.tnt.primed", BLASTS);
    t.whole("creeper.primed", BLASTS);
    // world
    t.startingWith("step.", STEPS);
    t.startingWith("dig.", DIGGING);
    t.startingWith("liquid.", LIQUIDS);
    t.whole("random.splash", LIQUIDS);
    t.startingWith("fire.", FIRE);
    t.whole("item.firecharge.use", FIRE);
    t.whole("random.fizz", FIRE);
    t.startingWith("ambient.", WEATHER);
    t.startingWith("portal.", PORTALS);
    // blocks and items
    t.whole("random.chestopen", CONTAINERS);
    t.whole("random.chestclosed", CONTAINERS);
    t.whole("random.door_open", CONTAINERS);
    t.whole("random.door_close", CONTAINERS);
    t.startingWith("tile.piston.", REDSTONE);
    t.whole("random.click", REDSTONE);
    t.whole("random.wood_click", REDSTONE);
    t.whole("gui.button.press", MENU);
    t.startingWith("random.anvil_", ANVILS);
    t.whole("random.eat", EATING);
    t.whole("random.drink", EATING);
    t.whole("random.burp", EATING);
    t.whole("game.potion.smash", POTIONS);
    t.whole("random.pop", PICKUPS);
    t.whole("random.break", BROKEN);
    t.whole("random.orb", XP);
    t.whole("random.levelup", XP);
    t.startingWith("fireworks.", FIREWORKS);
    t.startingWith("minecart.", MINECARTS);
    t.startingWith("note.", NOTES);
    // creatures: the narrower families are longer starts, so they beat "mob."
    t.startingWith("mob.", CREATURES);
    t.startingWith("mob.villager.", VILLAGERS);
    t.startingWith("mob.endermen.", ENDERMEN);
    t.startingWith("mob.enderdragon.", BOSSES);
    t.startingWith("mob.wither.", BOSSES);
    // music
    t.startingWith("music.", MUSIC);
    t.startingWith("records.", RECORDS);
    return t;
  }
}
