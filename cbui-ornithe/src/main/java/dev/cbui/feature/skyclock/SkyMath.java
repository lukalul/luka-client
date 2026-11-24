package dev.cbui.feature.skyclock;

/**
 * Conversions between real time and the game's day ticks. Pure Java, no game classes.
 *
 * <p>Game day ticks: 0 = 6:00, 6000 = noon (sun highest), 12000 = 18:00, 18000 = midnight. The game eases the sun's
 * angle, so it actually touches the horizon a little before tick 0 and a little after tick 12000; those two ticks are
 * worked out once from the game's own angle formula.
 */
public final class SkyMath {
  public static final int DAY_TICKS = 24000;
  public static final int NOON_TICK = 6000;
  public static final int MIDNIGHT_TICK = 18000;
  /** Tick (after noon) where the game sun reaches the western horizon. */
  public static final double EVENING_HORIZON = solveAngle(0.25, 12000, 14000);
  /** Tick (before 6:00, as a negative number) where the game sun reaches the eastern horizon. */
  public static final double MORNING_HORIZON = solveAngle(0.75, 22000, 24000) - DAY_TICKS;

  /** Average length of the moon's cycle of phases, in days. */
  private static final double SYNODIC_MONTH = 29.530588853;
  /** A known new moon: 2000-01-06 18:14 UTC, as epoch milliseconds. */
  private static final long KNOWN_NEW_MOON = 947_182_440_000L;

  private SkyMath() {}

  /** The game's sun angle for a tick of the day (0..1; 0 = highest, 0.25 and 0.75 = horizon). */
  static double celestialAngle(double tickOfDay) {
    double f = tickOfDay / DAY_TICKS - 0.25;
    if (f < 0) f += 1;
    if (f > 1) f -= 1;
    double eased = 1 - (Math.cos(f * Math.PI) + 1) / 2;
    return f + (eased - f) / 3;
  }

  private static double solveAngle(double angle, double lo, double hi) {
    // the angle grows steadily over each half of the day, so halving the interval finds the tick
    for (int i = 0; i < 60; i++) {
      double mid = (lo + hi) / 2;
      if (celestialAngle(mid) < angle) lo = mid;
      else hi = mid;
    }
    return (lo + hi) / 2;
  }

  /** Clock hour (0..24) to a tick of the day. */
  public static int hourToTick(double hour) {
    int tick = (int) Math.round((hour - 6) * 1000);
    return Math.floorMod(tick, DAY_TICKS);
  }

  /**
   * The tick of the day for real time {@code now} given that day's sun: sunrise, solar noon, sunset and the two solar
   * midnights land on the game's horizon, noon and midnight ticks, with even steps in between.
   */
  public static int tickForSun(SunTimes sun, long now) {
    if (sun.polar > 0) return NOON_TICK;
    if (sun.polar < 0) return MIDNIGHT_TICK;
    long half = SunTimes.DAY_MS / 2;
    double tick;
    if (now < sun.rise) tick = lerp(sun.noon - half, MIDNIGHT_TICK - DAY_TICKS, sun.rise, MORNING_HORIZON, now);
    else if (now < sun.noon) tick = lerp(sun.rise, MORNING_HORIZON, sun.noon, NOON_TICK, now);
    else if (now < sun.set) tick = lerp(sun.noon, NOON_TICK, sun.set, EVENING_HORIZON, now);
    else tick = lerp(sun.set, EVENING_HORIZON, sun.noon + half, MIDNIGHT_TICK, now);
    return Math.floorMod((int) Math.floor(tick), DAY_TICKS);
  }

  private static double lerp(long t0, double v0, long t1, double v1, long t) {
    if (t1 <= t0) return v1;
    double f = (double) (t - t0) / (t1 - t0);
    f = Math.max(0, Math.min(1, f));
    return v0 + (v1 - v0) * f;
  }

  /** The UTC day whose solar day (from one solar midnight to the next) contains {@code now} at longitude lon. */
  public static long solarDay(long now, double lon) {
    return Math.floorDiv(now + Math.round(lon / 360.0 * SunTimes.DAY_MS), SunTimes.DAY_MS);
  }

  /** Today's real moon phase in the game's numbering: 0 full, 1-3 waning, 4 new, 5-7 waxing. */
  public static int realMoonPhase(long now) {
    double age = ((now - KNOWN_NEW_MOON) / (double) SunTimes.DAY_MS) % SYNODIC_MONTH;
    if (age < 0) age += SYNODIC_MONTH;
    int eighth = (int) Math.floor(age / SYNODIC_MONTH * 8 + 0.5) & 7; // 0 new, 2 first quarter, 4 full, 6 last quarter
    return (eighth + 4) & 7;
  }
}
