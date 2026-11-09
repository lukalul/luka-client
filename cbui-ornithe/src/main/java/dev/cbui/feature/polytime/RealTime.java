package dev.cbui.feature.polytime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cbui.Cbui;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Supplies local solar time and lunar phase data. A coarse network location is fetched in the background and the
 * astronomical values are calculated locally with NOAA equations.
 */
final class RealTime {
  private static final long RETRY_MS = 30_000L;
  private static final AtomicBoolean fetching = new AtomicBoolean();
  /** {sunrise, sunset, noon, nadir} in local hours, or {-1} sun always up / {-2} sun always down. */
  private static volatile double[] sun;
  private static volatile long sunExpires;
  private static volatile long nextAttempt;
  private static boolean failureReported;
  private static int cachedPhase;
  private static long lastPhaseUpdate;

  private RealTime() {}

  /** Current real time mapped onto the game day (hours 0..24), or -1 while the location is not known yet. */
  static float gameHours() {
    double[] d = sun;
    if (d == null) {
      populate();
      return -1f;
    }
    if (System.currentTimeMillis() >= sunExpires) populate(); // keep yesterday's data until the refresh lands
    if (d[0] == -1) return 6f;
    if (d[0] == -2) return 18f;
    float now = irlHours();
    float rise = (float) d[0], set = (float) d[1], noon = (float) d[2], nadir = (float) d[3];
    if (within(now, rise, noon)) return map(now, rise, noon, 5f, 12f);
    if (within(now, noon, set)) return map(now, noon, set, 12f, 19f);
    if (within(now, set, nadir)) return map(now, set, nadir, 19f, 24f);
    return map(now, nadir, rise, 0f, 5f);
  }

  /** Vanilla moon phase number (0 full, 1 waning gibbous, 2 last quarter, 3 waning crescent, 4 new, 5..7 waxing) for today. */
  static int lunarPhase() {
    long now = System.currentTimeMillis();
    if (now - lastPhaseUpdate >= 3_600_000L) {
      lastPhaseUpdate = now;
      double jd = now / 86400000.0 + 2440587.5;
      double age = ((jd - 2451550.1) / 29.530588853) % 1.0;
      if (age < 0) age += 1;
      int fromNew = (int) Math.round(age * 8) % 8; // 0 new, 2 first quarter, 4 full, 6 last quarter
      cachedPhase = (fromNew + 4) % 8;
    }
    return cachedPhase;
  }

  private static float irlHours() {
    Calendar c = Calendar.getInstance();
    return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f + c.get(Calendar.SECOND) / 3600f + c.get(Calendar.MILLISECOND) / 3600000f;
  }

  private static boolean within(float now, float start, float end) {
    if (end < start) {
      end += 24f;
      if (now < start) now += 24f;
    }
    return now >= start && now <= end;
  }

  private static float map(float now, float start, float end, float gameStart, float gameEnd) {
    if (end < start) {
      end += 24f;
      if (now < start) now += 24f;
    }
    if (end == start) return gameStart;
    return (now - start) * (gameEnd - gameStart) / (end - start) + gameStart;
  }

  /** Starts a background fetch of today's sun times unless one is running or it failed recently. */
  static void populate() {
    if (System.currentTimeMillis() < nextAttempt) return;
    if (!fetching.compareAndSet(false, true)) return;
    try {
      Thread t = new Thread(RealTime::fetch, "luka-polytime-location");
      t.setDaemon(true);
      t.start();
    } catch (Throwable error) {
      fail(error);
      fetching.set(false);
    }
  }

  private static void fetch() {
    try {
      HttpURLConnection c = (HttpURLConnection) new URL("http://ip-api.com/json/").openConnection();
      c.setConnectTimeout(5000);
      c.setReadTimeout(5000);
      JsonObject json;
      try (InputStreamReader in = new InputStreamReader(c.getInputStream(), "UTF-8")) {
        json = new JsonParser().parse(in).getAsJsonObject();
      }
      if (!json.has("lat") || !json.has("lon")) throw new IllegalStateException("ip-api.com returned no location");
      double[] data = compute(json.get("lat").getAsDouble(), json.get("lon").getAsDouble());
      sunExpires = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
      sun = data;
      nextAttempt = 0L;
    } catch (Throwable error) {
      fail(error);
    } finally {
      fetching.set(false);
    }
  }

  private static void fail(Throwable error) {
    nextAttempt = System.currentTimeMillis() + RETRY_MS;
    if (sun == null && !failureReported) {
      failureReported = true;
      Cbui.reportOnce("PolyTime: could not get your location for IRL time, using server time until it works (retrying every 30s)", error);
    }
  }

  /** NOAA solar equations: {sunrise, sunset, noon, nadir} in local clock hours for today. */
  static double[] compute(double lat, double lon) {
    ZonedDateTime now = ZonedDateTime.now();
    double jd = now.toLocalDate().toEpochDay() + 2440587.5 + 0.5; // local date at 12:00 UTC
    double t = (jd - 2451545.0) / 36525.0;
    double l0 = (280.46646 + t * (36000.76983 + t * 0.0003032)) % 360.0;
    double m = 357.52911 + t * (35999.05029 - 0.0001537 * t);
    double e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);
    double mr = Math.toRadians(m);
    double c = Math.sin(mr) * (1.914602 - t * (0.004817 + 0.000014 * t)) + Math.sin(2 * mr) * (0.019993 - 0.000101 * t) + Math.sin(3 * mr) * 0.000289;
    double omega = 125.04 - 1934.136 * t;
    double lambda = l0 + c - 0.00569 - 0.00478 * Math.sin(Math.toRadians(omega));
    double seconds = 21.448 - t * (46.815 + t * (0.00059 - t * 0.001813));
    double obliq = 23.0 + (26.0 + seconds / 60.0) / 60.0 + 0.00256 * Math.cos(Math.toRadians(omega));
    double decl = Math.asin(Math.sin(Math.toRadians(obliq)) * Math.sin(Math.toRadians(lambda)));
    double y = Math.pow(Math.tan(Math.toRadians(obliq) / 2), 2);
    double l0r = Math.toRadians(l0);
    double eq = 4 * Math.toDegrees(y * Math.sin(2 * l0r) - 2 * e * Math.sin(mr) + 4 * e * y * Math.sin(mr) * Math.cos(2 * l0r)
        - 0.5 * y * y * Math.sin(4 * l0r) - 1.25 * e * e * Math.sin(2 * mr)); // minutes
    double latr = Math.toRadians(lat);
    double cosH = Math.cos(Math.toRadians(90.833)) / (Math.cos(latr) * Math.cos(decl)) - Math.tan(latr) * Math.tan(decl);
    if (cosH > 1) return new double[] {-2};
    if (cosH < -1) return new double[] {-1};
    double ha = Math.toDegrees(Math.acos(cosH));
    double noonUtc = 720 - 4 * lon - eq; // minutes after 00:00 UTC
    double offset = now.getOffset().getTotalSeconds() / 60.0;
    return new double[] {
      hours(noonUtc - 4 * ha + offset), hours(noonUtc + 4 * ha + offset), hours(noonUtc + offset), hours(noonUtc + 720 + offset)
    };
  }

  private static double hours(double minutes) {
    double h = (minutes / 60.0) % 24.0;
    return h < 0 ? h + 24.0 : h;
  }
}
