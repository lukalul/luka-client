package dev.cbui.feature.crashguard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Uploads a crash report to a public paste site through its documented API, over HTTPS. Blocking: call it from a
 * background thread only.
 */
final class PasteSites {
  private static final int CONNECT_MS = 10_000;
  private static final int READ_MS = 30_000;

  private PasteSites() {}

  /** Posts the text and returns the public link. */
  static String upload(String site, String text) throws IOException {
    if (CrashGuardModule.SITE_MCLOGS.equals(site)) return mclogs(text);
    return hastebin(text);
  }

  /** hst.sh is a hastebin server: the raw text goes to /documents and the answer names the new key. */
  private static String hastebin(String text) throws IOException {
    String answer = post("https://hst.sh/documents", "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8));
    String key = field(answer, "key");
    if (key == null || !key.matches("[A-Za-z0-9]+")) throw new IOException("hst.sh gave no key");
    return "https://hst.sh/" + key;
  }

  /** mclo.gs takes the text as the form field "content" and answers with the link. */
  private static String mclogs(String text) throws IOException {
    byte[] form = ("content=" + URLEncoder.encode(text, "UTF-8")).getBytes(StandardCharsets.UTF_8);
    String answer = post("https://api.mclo.gs/1/log", "application/x-www-form-urlencoded", form);
    String link = field(answer, "url");
    if (link == null || !link.startsWith("https://")) {
      String error = field(answer, "error");
      throw new IOException(error != null ? "mclo.gs: " + error : "mclo.gs gave no link");
    }
    return link;
  }

  private static String field(String json, String name) {
    try {
      JsonElement root = new JsonParser().parse(json);
      if (!root.isJsonObject()) return null;
      JsonObject o = root.getAsJsonObject();
      return o.has(name) && o.get(name).isJsonPrimitive() ? o.get(name).getAsString() : null;
    } catch (RuntimeException notJson) {
      return null;
    }
  }

  private static String post(String address, String type, byte[] body) throws IOException {
    HttpURLConnection http = (HttpURLConnection) new URL(address).openConnection();
    try {
      http.setConnectTimeout(CONNECT_MS);
      http.setReadTimeout(READ_MS);
      http.setDoOutput(true);
      http.setRequestMethod("POST");
      http.setRequestProperty("Content-Type", type);
      http.setRequestProperty("User-Agent", "luka-client");
      http.setFixedLengthStreamingMode(body.length);
      try (OutputStream out = http.getOutputStream()) {
        out.write(body);
      }
      int code = http.getResponseCode();
      InputStream in = code >= 400 ? http.getErrorStream() : http.getInputStream();
      String answer = in == null ? "" : read(in);
      if (code >= 400 && field(answer, "error") == null) throw new IOException("HTTP " + code);
      return answer;
    } finally {
      http.disconnect();
    }
  }

  private static String read(InputStream in) throws IOException {
    try (InputStream stream = in) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      for (int n; (n = stream.read(buf)) > 0 && out.size() < 1 << 20; ) out.write(buf, 0, n);
      return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
  }
}
