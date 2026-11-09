package dev.cbui.feature.soundtweaks;

import dev.cbui.Cbui;
import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.util.ResourceLocation;

public final class SoundTweaksModule extends FeatureModule {
  public final Setting.Toggle removeCap = add(new Setting.Toggle("Remove Volume Cap", false,
      "Lets sounds go past Minecraft's 100% volume limit. Sounds already playing keep their loudness until they restart."));

  private final List<VolumeBand> bands = new ArrayList<>();
  private final Map<ResourceLocation, Float> resolved = new HashMap<>();
  private volatile List<Rule> customRules = Collections.emptyList();
  private final File rulesFile;
  private int settingsFingerprint;
  private long observedWriteTime = Long.MIN_VALUE;
  private int pollCountdown;

  public SoundTweaksModule() {
    super("SoundTweaks", true);
    rulesFile = new File(mc.mcDataDir, "config/luka-soundtweaks.txt");
    register("Explosions", "Explosion sounds.", "random.explode");
    register("TNT and creeper fuse", "The hiss of primed TNT and creepers.", "game.tnt.primed", "creeper.primed");
    register("Footsteps", "Walking, all surfaces.", "step.");
    register("Block breaking", "Digging and breaking blocks.", "dig.");
    register("Player hurt and death", "Player damage, falls, drowning splash and death.", "game.player.");
    register("Mob hurt and death", "Hostile and neutral mob damage sounds.", "game.hostile.", "game.neutral.");
    register("Bow and arrows", "Shooting and arrow hits.", "random.bow", "random.bowhit");
    register("Hit sound", "The ding when you hit something with an arrow or attack.", "random.successful_hit");
    register("Eating and drinking", "Eating, drinking and burping.", "random.eat", "random.drink", "random.burp");
    register("Potions", "Splash potions breaking and fizzing.", "game.potion.smash", "random.fizz");
    register("Chests and doors", "Opening and closing chests and doors.", "random.chestopen", "random.chestclosed", "random.door_open", "random.door_close");
    register("Clicks and buttons", "Menu buttons, levers and clicks.", "random.click", "random.wood_click", "gui.button.press");
    register("Experience", "Orbs and level ups.", "random.orb", "random.levelup");
    register("Item pop and break", "Item pickup pop and tools breaking.", "random.pop", "random.break");
    register("Anvil", "Anvil use, landing and breaking.", "random.anvil_break", "random.anvil_land", "random.anvil_use");
    register("Water and lava", "Splashes, swimming and liquid ambience.", "random.splash", "liquid.");
    register("Fire", "Fire crackle, igniting and fire charges.", "fire.", "item.fireCharge.use");
    register("Fireworks", "Fireworks launch, blast and twinkle.", "fireworks.");
    register("Pistons", "Piston extend and retract.", "tile.piston.");
    register("Portals", "Nether portal sounds.", "portal.");
    register("Ambient and weather", "Cave noises, rain and thunder.", "ambient.");
    register("Mobs", "All other mob noises.", "mob.");
    register("Endermen and dragon", "Endermen and the ender dragon (overrides Mobs).", "mob.endermen.", "mob.enderdragon.");
    register("Villagers", "Villager talking and trading (overrides Mobs).", "mob.villager.");
    register("Minecarts", "Minecart rolling.", "minecart.");
    register("Note blocks", "Note block instruments.", "note.");
    register("Music", "Background music.", "music.");
    register("Music discs", "Jukebox records.", "records.");
    settingsFingerprint = fingerprint();
  }

  private void register(String label, String help, String... patterns) {
    Setting.Slider slider = grouped("Sound volumes", new Setting.Slider(label, 100f, 0f, 200f, 5f, help));
    add(slider);
    bands.add(new VolumeBand(slider, patterns));
  }

  @Override public String category() { return "UTILITY"; }

  public synchronized float volumeMultiplier(ResourceLocation location) {
    if (location == null) return 1f;
    Float known = resolved.get(location);
    if (known != null) return known;
    float value = resolvePath(location.getResourcePath());
    if (resolved.size() >= 2048) resolved.clear();
    resolved.put(location, value);
    return value;
  }

  private float resolvePath(String path) {
    Match custom = bestRule(path, customRules);
    if (custom != null) return custom.multiplier;
    Match best = null;
    for (VolumeBand band : bands) {
      int specificity = band.specificity(path);
      if (specificity >= 0 && (best == null || specificity > best.specificity))
        best = new Match(specificity, band.slider.get() / 100f);
    }
    return best == null ? 1f : best.multiplier;
  }

  private static Match bestRule(String path, List<Rule> candidates) {
    Match best = null;
    for (Rule rule : candidates) {
      int specificity = rule.specificity(path);
      if (specificity >= 0 && (best == null || specificity > best.specificity))
        best = new Match(specificity, rule.multiplier);
    }
    return best;
  }

  @Override public void tick() {
    try {
      int current = fingerprint();
      if (current != settingsFingerprint) {
        settingsFingerprint = current;
        clearResolved();
      }
      if (--pollCountdown <= 0) {
        pollCountdown = 100;
        refreshRules();
      }
    } catch (Throwable failure) {
      Cbui.reportOnce("SoundTweaks tick", failure);
    }
  }

  private int fingerprint() {
    int hash = 1;
    for (VolumeBand band : bands) hash = 31 * hash + Float.floatToIntBits(band.slider.get());
    return hash;
  }

  private synchronized void clearResolved() { resolved.clear(); }

  private void refreshRules() {
    if (!rulesFile.isFile()) {
      if (observedWriteTime != -1L) {
        observedWriteTime = -1L;
        customRules = Collections.emptyList();
        clearResolved();
        createRulesTemplate();
      }
      return;
    }
    long writeTime = rulesFile.lastModified();
    if (writeTime == observedWriteTime) return;
    try {
      List<Rule> parsed = new ArrayList<>();
      for (String raw : Files.readAllLines(rulesFile.toPath(), StandardCharsets.UTF_8)) {
        Rule rule = Rule.parse(raw);
        if (rule != null) parsed.add(rule);
      }
      customRules = Collections.unmodifiableList(parsed);
      observedWriteTime = writeTime;
      clearResolved();
    } catch (Throwable failure) {
      Cbui.reportOnce("SoundTweaks rules file", failure);
    }
  }

  private void createRulesTemplate() {
    try {
      File parent = rulesFile.getParentFile();
      if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return;
      List<String> lines = new ArrayList<>();
      lines.add("# luka SoundTweaks: per-sound volume, in percent (0-200). One rule per line.");
      lines.add("# sound.id=percent          exact sound, e.g.  random.explode=50");
      lines.add("# prefix.*=percent          every sound starting with the prefix, e.g.  mob.zombie.*=0  (mute zombies)");
      lines.add("# These rules win over the sliders in the menu. Changes are picked up within a few seconds.");
      Files.write(rulesFile.toPath(), lines, StandardCharsets.UTF_8);
      observedWriteTime = rulesFile.lastModified();
    } catch (Throwable ignored) { }
  }

  private static final class VolumeBand {
    private final Setting.Slider slider;
    private final String[] patterns;
    private VolumeBand(Setting.Slider slider, String[] patterns) { this.slider = slider; this.patterns = patterns.clone(); }
    private int specificity(String path) {
      int winner = -1;
      for (String pattern : patterns) {
        boolean prefix = pattern.endsWith(".");
        if (prefix ? path.startsWith(pattern) : path.equals(pattern))
          winner = Math.max(winner, prefix ? pattern.length() : Integer.MAX_VALUE);
      }
      return winner;
    }
  }

  private static final class Rule {
    private final String value;
    private final boolean prefix;
    private final float multiplier;
    private Rule(String value, boolean prefix, float multiplier) { this.value = value; this.prefix = prefix; this.multiplier = multiplier; }
    private int specificity(String path) {
      if (prefix) return path.startsWith(value) ? value.length() : -1;
      return path.equals(value) ? Integer.MAX_VALUE : -1;
    }
    private static Rule parse(String raw) {
      String line = raw.trim();
      int separator = line.indexOf('=');
      if (line.isEmpty() || line.startsWith("#") || separator <= 0) return null;
      String key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
      if (key.startsWith("minecraft:")) key = key.substring(10);
      boolean prefix = key.endsWith("*");
      if (prefix) key = key.substring(0, key.length() - 1);
      try {
        float percentage = Float.parseFloat(line.substring(separator + 1).trim().replace("%", ""));
        return new Rule(key, prefix, Math.max(0f, Math.min(200f, percentage)) / 100f);
      } catch (NumberFormatException ignored) { return null; }
    }
  }

  private static final class Match {
    private final int specificity;
    private final float multiplier;
    private Match(int specificity, float multiplier) { this.specificity = specificity; this.multiplier = multiplier; }
  }
}
