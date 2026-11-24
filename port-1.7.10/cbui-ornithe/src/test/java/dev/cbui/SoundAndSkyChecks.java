package dev.cbui;

import dev.cbui.feature.skyclock.SkyMath;
import dev.cbui.feature.skyclock.SunTimes;
import dev.cbui.feature.soundmixer.NameTable;
import dev.cbui.feature.soundmixer.RuleLines;
import dev.cbui.feature.soundmixer.SoundGroups;
import java.util.Arrays;

/** Plain-Java checks for the Sound Tweaks matching rules and the Time Changer sun and moon maths. */
final class SoundAndSkyChecks {
  private SoundAndSkyChecks() {}

  static void run() {
    soundRules();
    sunAndMoon();
  }

  private static void soundRules() {
    NameTable<Float> rules = RuleLines.parse(Arrays.asList(
        "# a comment=5",
        "",
        "minecraft:Random.Explode = 50%",
        "mob.zombie.*=250",
        "mob.*=10",
        "mob.zombie.say=30",
        "no equals sign",
        "note.harp=loud",
        "a*b=5",
        "=40",
        "dig.*=-20"));
    same(50f, rules.find("random.explode"), "namespace, case and % are accepted");
    same(200f, rules.find("mob.zombie.hurt"), "percent clamped to 200");
    same(30f, rules.find("MOB.ZOMBIE.SAY"), "exact name beats a prefix");
    same(10f, rules.find("mob.cow.say"), "shorter prefix still matches");
    same(0f, rules.find("dig.stone"), "percent clamped to 0");
    same(null, rules.find("note.harp"), "a malformed line is ignored");
    truth(rules.size() == 5, "only the five good lines are rules (got " + rules.size() + ")");

    NameTable<String> groups = SoundGroups.table();
    same(SoundGroups.VILLAGERS, groups.find("mob.villager.idle"), "villagers beat creature voices");
    same(SoundGroups.BOSSES, groups.find("mob.wither.shoot"), "bosses beat creature voices");
    same(SoundGroups.ENDERMEN, groups.find("mob.endermen.portal"), "endermen beat creature voices");
    same(SoundGroups.CREATURES, groups.find("mob.zombie.say"), "zombie voice is a creature voice");
    same(SoundGroups.MOBS_HURT, groups.find("mob.zombie.hurt"), "zombie hurt is a mob getting hurt");
    same(SoundGroups.DING, groups.find("random.successful_hit"), "arrow ding");
    same(SoundGroups.FIRE, groups.find("item.fireCharge.use"), "1.8.9-only fire charge");
    same(SoundGroups.PLAYERS_HURT, groups.find("game.player.hurt.fall.big"), "player falls");
    same(null, groups.find("some.modded.sound"), "an unknown sound is in no group");
    String[] labels = SoundGroups.labels();
    truth(labels.length == 29 && new java.util.HashSet<String>(Arrays.asList(labels)).size() == 29, "29 unique slider labels");

    NameTable<Float> oneRule = RuleLines.parse(Arrays.asList("mob.villager.*=0"));
    float villager = SoundGroups.percentFor(oneRule, groups, label -> 150, "mob.villager.idle");
    float cow = SoundGroups.percentFor(oneRule, groups, label -> 150, "mob.cow.say");
    float unknown = SoundGroups.percentFor(oneRule, groups, label -> 150, "x.y");
    truth(villager == 0f && cow == 150f && unknown == 100f, "rules beat sliders, sliders apply, unknown stays 100");
  }

  private static void sunAndMoon() {
    long day = java.time.LocalDate.of(2024, 6, 21).toEpochDay();
    SunTimes london = SunTimes.forDay(day, 51.5074, -0.1278);
    long midnight = day * SunTimes.DAY_MS;
    near(midnight + minutes(3, 43), london.rise, "London sunrise on the solstice");
    near(midnight + minutes(20, 21), london.set, "London sunset on the solstice");
    truth(SunTimes.forDay(day, 78.2, 15.6).polar == 1, "polar day in Svalbard in June");
    truth(SunTimes.forDay(java.time.LocalDate.of(2024, 12, 21).toEpochDay(), 78.2, 15.6).polar == -1, "polar night in December");
    truth(SkyMath.tickForSun(london, london.noon) == SkyMath.NOON_TICK, "solar noon is the game's noon");
    truth(Math.abs(SkyMath.tickForSun(london, london.set) - SkyMath.EVENING_HORIZON) < 1.5, "sunset on the western horizon");
    truth(SkyMath.tickForSun(london, london.noon + SunTimes.DAY_MS / 2) == SkyMath.MIDNIGHT_TICK, "solar midnight is the game's midnight");
    truth(SkyMath.hourToTick(6) == 0 && SkyMath.hourToTick(12) == 6000 && SkyMath.hourToTick(0) == 18000, "hour slider mapping");
    truth(SkyMath.realMoonPhase(java.time.Instant.parse("2024-01-25T18:00:00Z").toEpochMilli()) == 0, "full moon 2024-01-25");
    truth(SkyMath.realMoonPhase(java.time.Instant.parse("2024-01-11T12:00:00Z").toEpochMilli()) == 4, "new moon 2024-01-11");
  }

  private static long minutes(int h, int m) {
    return (h * 60L + m) * 60_000L;
  }

  private static void near(long expected, long actual, String what) {
    truth(Math.abs(expected - actual) <= 120_000L, what + " within two minutes (off by " + (actual - expected) / 1000 + " s)");
  }

  private static void same(Object expected, Object actual, String what) {
    truth(expected == null ? actual == null : expected.equals(actual), what + ": expected " + expected + ", got " + actual);
  }

  private static void truth(boolean ok, String what) {
    if (!ok) throw new AssertionError(what);
  }
}
