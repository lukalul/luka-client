package dev.cbui.feature.skyclock;

/**
 * Sunrise, solar noon and sunset for one day at one place, from the NOAA solar calculator equations (Meeus-based
 * low-precision solar position: mean longitude and anomaly, equation of centre, apparent longitude, obliquity,
 * declination and equation of time, with the standard 90.833 degree zenith for rise and set). All times are UTC
 * epoch milliseconds. Pure Java, no game classes.
 */
public final class SunTimes {
  public static final long DAY_MS = 86_400_000L;
  private static final double ZENITH_DEG = 90.833;

  /** 0 = ordinary day, 1 = the sun never sets (polar day), -1 = it never rises (polar night). */
  public final int polar;
  public final long rise;
  public final long noon;
  public final long set;
  /** The UTC calendar day these times belong to (days since 1970-01-01). */
  public final long day;

  private SunTimes(long day, int polar, long rise, long noon, long set) {
    this.day = day;
    this.polar = polar;
    this.rise = rise;
    this.noon = noon;
    this.set = set;
  }

  /**
   * Times for UTC day {@code day} (days since 1970-01-01) at latitude {@code lat} (north positive) and longitude
   * {@code lon} (east positive), in degrees.
   */
  public static SunTimes forDay(long day, double lat, double lon) {
    double midnightJd = day + 2440587.5;
    // first pass at an estimate of local noon, second pass at the noon found by the first
    double noonMin = 720 - 4 * lon;
    Solar s = Solar.at(midnightJd + noonMin / 1440.0);
    noonMin = 720 - 4 * lon - s.equationOfTime;
    s = Solar.at(midnightJd + noonMin / 1440.0);
    noonMin = 720 - 4 * lon - s.equationOfTime;

    double latR = Math.toRadians(lat);
    double cosHourAngle = Math.cos(Math.toRadians(ZENITH_DEG)) / (Math.cos(latR) * Math.cos(s.declination))
        - Math.tan(latR) * Math.tan(s.declination);
    long base = day * DAY_MS;
    long noon = base + Math.round(noonMin * 60_000.0);
    if (cosHourAngle > 1) return new SunTimes(day, -1, noon, noon, noon);
    if (cosHourAngle < -1) return new SunTimes(day, 1, noon, noon, noon);
    double halfDayMin = 4 * Math.toDegrees(Math.acos(cosHourAngle));
    return new SunTimes(day, 0, noon - Math.round(halfDayMin * 60_000.0), noon, noon + Math.round(halfDayMin * 60_000.0));
  }

  /** The sun's declination (radians) and the equation of time (minutes) at a Julian day. */
  static final class Solar {
    final double declination;
    final double equationOfTime;

    private Solar(double declination, double equationOfTime) {
      this.declination = declination;
      this.equationOfTime = equationOfTime;
    }

    static Solar at(double jd) {
      double t = (jd - 2451545.0) / 36525.0; // Julian centuries since J2000.0
      double meanLong = mod360(280.46646 + t * (36000.76983 + t * 0.0003032));
      double meanAnom = 357.52911 + t * (35999.05029 - 0.0001537 * t);
      double ecc = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);
      double m = Math.toRadians(meanAnom);
      double centre = Math.sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t))
          + Math.sin(2 * m) * (0.019993 - 0.000101 * t)
          + Math.sin(3 * m) * 0.000289;
      double trueLong = meanLong + centre;
      double omega = Math.toRadians(125.04 - 1934.136 * t);
      double apparentLong = trueLong - 0.00569 - 0.00478 * Math.sin(omega);
      double meanObliquity = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60;
      double obliquity = Math.toRadians(meanObliquity + 0.00256 * Math.cos(omega));
      double declination = Math.asin(Math.sin(obliquity) * Math.sin(Math.toRadians(apparentLong)));
      double y = Math.tan(obliquity / 2);
      y *= y;
      double l = Math.toRadians(meanLong);
      double eot = y * Math.sin(2 * l) - 2 * ecc * Math.sin(m) + 4 * ecc * y * Math.sin(m) * Math.cos(2 * l)
          - 0.5 * y * y * Math.sin(4 * l) - 1.25 * ecc * ecc * Math.sin(2 * m);
      return new Solar(declination, 4 * Math.toDegrees(eot));
    }
  }

  private static double mod360(double deg) {
    double r = deg % 360;
    return r < 0 ? r + 360 : r;
  }
}
