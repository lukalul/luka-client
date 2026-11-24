package dev.cbui.feature.skyclock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cbui.Cbui;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * The player's approximate position from a free IP geolocation service over HTTPS, looked up on a background thread.
 * Asked again once per local calendar day; the last good answer stays in use until a new one arrives. On failure it
 * waits about 30 seconds before the next try and reports the problem once.
 */
final class RoughLocation {
  private static final String[] SERVICES = {"https://ipapi.co/json/", "https://ipwho.is/"};
  private static final long RETRY_MS = 30_000L;

  private volatile double[] latLon;
  private volatile boolean busy;
  private volatile long nextTryAt;
  private volatile long fetchedForDay = Long.MIN_VALUE;

  /** Latitude and longitude in degrees, or null while unknown. */
  double[] latLon() {
    return latLon;
  }

  /** Starts a lookup when none is known for this local day and the retry pause is over. Never blocks. */
  void refresh(long localDay) {
    if (busy || fetchedForDay == localDay) return;
    long now = System.currentTimeMillis();
    if (now < nextTryAt) return;
    busy = true;
    Thread worker = new Thread(() -> lookUp(localDay), "luka sky location");
    worker.setDaemon(true);
    worker.start();
  }

  private void lookUp(long localDay) {
    try {
      Exception last = null;
      for (String service : SERVICES) {
        try {
          double[] found = parse(get(service));
          if (found != null) {
            latLon = found;
            fetchedForDay = localDay;
            return;
          }
        } catch (Exception e) {
          last = e;
        }
      }
      nextTryAt = System.currentTimeMillis() + RETRY_MS;
      Cbui.reportOnce("Time Changer location lookup", last != null ? last : new IOException("no position in the answer"));
    } catch (Throwable error) {
      nextTryAt = System.currentTimeMillis() + RETRY_MS;
      Cbui.reportOnce("Time Changer location lookup", error);
    } finally {
      busy = false;
    }
  }

  private static String get(String address) throws IOException {
    HttpURLConnection http = (HttpURLConnection) new URL(address).openConnection();
    try {
      http.setConnectTimeout(10_000);
      http.setReadTimeout(15_000);
      http.setRequestProperty("User-Agent", "luka-client");
      http.setRequestProperty("Accept", "application/json");
      int code = http.getResponseCode();
      if (code != 200) throw new IOException("location service answered HTTP " + code);
      try (InputStream in = http.getInputStream()) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        for (int n; (n = in.read(buf)) > 0 && body.size() < 65536; ) body.write(buf, 0, n);
        return new String(body.toByteArray(), StandardCharsets.UTF_8);
      }
    } finally {
      http.disconnect();
    }
  }

  /** Reads "latitude" and "longitude" from the answer; null when either is missing or out of range. */
  static double[] parse(String json) {
    JsonElement root = new JsonParser().parse(json);
    if (!root.isJsonObject()) return null;
    JsonObject o = root.getAsJsonObject();
    if (!o.has("latitude") || !o.has("longitude")) return null;
    double lat = o.get("latitude").getAsDouble();
    double lon = o.get("longitude").getAsDouble();
    if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) return null;
    return new double[] {lat, lon};
  }
}
