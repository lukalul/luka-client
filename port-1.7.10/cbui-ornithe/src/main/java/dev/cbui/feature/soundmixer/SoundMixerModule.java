package dev.cbui.feature.soundmixer;

import dev.cbui.Cbui;
import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import java.io.File;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.audio.ISound;
import net.minecraft.util.ResourceLocation;

/**
 * A volume mixer on top of vanilla's sound categories: one slider per kind of sound (0..200 %), plus a rules file for
 * single sounds. Lives on the GENERAL settings tab. The factor for a sound name is looked up when the sound starts,
 * so it is cached per name and the cache is dropped whenever a slider or the rules change.
 */
public final class SoundMixerModule extends FeatureModule {
  private static final String RULES_PATH = "config" + File.separator + "luka-soundtweaks.txt";
  /** Client ticks between two looks at the rules file (about five seconds). */
  private static final int RULES_POLL_TICKS = 100;

  private final Map<String, Setting.Slider> byLabel = new HashMap<String, Setting.Slider>();
  private final Setting.Slider[] sliders;

  private final Setting.Toggle louder = grouped("Limits", add(new Setting.Toggle("Allow above 100 %", false,
      "Lets boosted sounds go past vanilla's loudest level. Restart a sound to hear the change.")));

  {
    slider("Combat", SoundGroups.PLAYERS_HURT, "Damage, fall and death sounds of players.");
    slider("Combat", SoundGroups.MOBS_HURT, "Damage and death sounds of animals and monsters.");
    slider("Combat", SoundGroups.BOW, "Drawing and releasing a bow, arrows landing.");
    slider("Combat", SoundGroups.DING, "The chime when your arrow strikes a target.");
    slider("Combat", SoundGroups.BLASTS, "Explosions and the hiss before TNT or a creeper goes off.");
    slider("World", SoundGroups.STEPS, "Walking and running on any surface.");
    slider("World", SoundGroups.DIGGING, "Breaking and placing blocks.");
    slider("World", SoundGroups.LIQUIDS, "Splashing, swimming and flowing liquids.");
    slider("World", SoundGroups.FIRE, "Flames, lighting fires, fire charges, things being put out.");
    slider("World", SoundGroups.WEATHER, "Rain, thunder and eerie cave noises.");
    slider("World", SoundGroups.PORTALS, "Portal hum and the travel whoosh.");
    slider("Blocks and items", SoundGroups.CONTAINERS, "Opening and shutting containers and doors.");
    slider("Blocks and items", SoundGroups.REDSTONE, "Pistons, levers, buttons and pressure plates.");
    slider("Blocks and items", SoundGroups.MENU, "Clicking buttons in menus.");
    slider("Blocks and items", SoundGroups.ANVILS, "Using, dropping and breaking anvils.");
    slider("Blocks and items", SoundGroups.EATING, "Munching, gulping and the burp after.");
    slider("Blocks and items", SoundGroups.POTIONS, "Thrown potions shattering.");
    slider("Blocks and items", SoundGroups.PICKUPS, "The pop when you collect an item.");
    slider("Blocks and items", SoundGroups.BROKEN, "A tool or piece of armour wearing out.");
    slider("Blocks and items", SoundGroups.XP, "XP orbs and levelling up.");
    slider("Blocks and items", SoundGroups.FIREWORKS, "Launch, burst and crackle.");
    slider("Blocks and items", SoundGroups.MINECARTS, "Rolling carts.");
    slider("Blocks and items", SoundGroups.NOTES, "Every note block instrument.");
    slider("Creatures", SoundGroups.CREATURES, "Idle, attack and step noises of all animals and monsters.");
    slider("Creatures", SoundGroups.VILLAGERS, "Villager chatter and trading; overrides Creature voices.");
    slider("Creatures", SoundGroups.ENDERMEN, "Endermen noises; overrides Creature voices.");
    slider("Creatures", SoundGroups.BOSSES, "Ender dragon and wither; overrides Creature voices.");
    slider("Music", SoundGroups.MUSIC, "The game's soundtrack.");
    slider("Music", SoundGroups.RECORDS, "Music discs.");
    sliders = byLabel.values().toArray(new Setting.Slider[0]);
  }

  private final NameTable<String> groups = SoundGroups.table();
  private volatile NameTable<Float> rules = new NameTable<Float>();
  private int rulesVersion;
  private final ConcurrentHashMap<String, Float> factors = new ConcurrentHashMap<String, Float>();
  private int factorsStamp;

  private final LoudSources loud = new LoudSources();
  private int ticksToRulesCheck;
  private long rulesSeenTime = Long.MIN_VALUE;
  private long rulesSeenSize = -1;

  public SoundMixerModule() {
    super("SoundTweaks", true);
  }

  @Override
  public String category() {
    return "UTILITY";
  }

  /** The module while it should change sounds, else null. */
  public static SoundMixerModule active() {
    SoundMixerModule module = Features.get(SoundMixerModule.class);
    return module != null && module.enabled() ? module : null;
  }

  private void slider(String heading, String label, String tooltip) {
    Setting.Slider s = grouped(heading, add(new Setting.Slider(label, 100, 0, 200, 5, tooltip)));
    byLabel.put(label, s);
  }

  /** Whether vanilla's full-volume ceiling is lifted. */
  public boolean liftCeiling() {
    return louder.get();
  }

  /** The volume multiplier for a sound (1 = unchanged). */
  public float factor(ISound sound) {
    ResourceLocation where = sound == null ? null : sound.getSoundLocation();
    String name = where == null ? null : where.getResourcePath();
    if (name == null) return 1f;
    int stamp = stamp();
    if (stamp != factorsStamp) {
      factors.clear();
      factorsStamp = stamp;
    }
    Float cached = factors.get(name);
    if (cached != null) return cached;
    float f = computeFactor(name);
    factors.put(name, f);
    return f;
  }

  private float computeFactor(String name) {
    return SoundGroups.percentFor(rules, groups, this::sliderPercent, name) / 100f;
  }

  private double sliderPercent(String label) {
    Setting.Slider slider = byLabel.get(label);
    return slider == null ? 100 : slider.get();
  }

  /** A number that changes whenever a slider or the rules change. */
  private int stamp() {
    int h = rulesVersion;
    for (Setting.Slider s : sliders) h = h * 31 + Float.floatToIntBits(s.get());
    return h;
  }

  /** Called with every finished volume; above 1 the source is made louder on the next tick. */
  public void volumeWorkedOut(Object soundManager, ISound sound, float volume) {
    if (louder.get()) loud.noteVolume(soundManager, sound, volume);
  }

  @Override
  public void tick() {
    if (--ticksToRulesCheck <= 0) {
      ticksToRulesCheck = RULES_POLL_TICKS;
      try {
        checkRules();
      } catch (Throwable error) {
        Cbui.reportOnce("SoundTweaks rules file", error);
      }
    }
    try {
      if (!louder.get()) loud.clear();
      else loud.apply();
    } catch (Throwable error) {
      loud.clear();
      Cbui.reportOnce("SoundTweaks loud sounds", error);
    }
  }

  private void checkRules() throws IOException {
    File file = new File(mc.mcDataDir, RULES_PATH);
    if (!file.isFile()) {
      if (!rules.isEmpty()) {
        rules = new NameTable<Float>();
        rulesVersion++;
      }
      rulesSeenTime = Long.MIN_VALUE;
      rulesSeenSize = -1;
      File dir = file.getParentFile();
      if (dir != null && !dir.isDirectory()) dir.mkdirs();
      Files.write(file.toPath(), RuleLines.EXPLANATION.getBytes(StandardCharsets.UTF_8));
      return;
    }
    long time = file.lastModified();
    long size = file.length();
    if (time == rulesSeenTime && size == rulesSeenSize) return;
    rulesSeenTime = time;
    rulesSeenSize = size;
    List<String> lines;
    try {
      lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
    } catch (MalformedInputException notUtf8) {
      lines = Files.readAllLines(file.toPath(), StandardCharsets.ISO_8859_1);
    }
    rules = RuleLines.parse(lines);
    rulesVersion++;
  }
}
