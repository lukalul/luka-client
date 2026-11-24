package dev.cbui.report;

import dev.cbui.Cbui;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reports internal errors (via {@link Cbui#reportOnce}) and real game crashes (via feature.crashguard.CrashRecovery) to
 * api.luka.wtf, tagged with the launcher's per-install id - never a Minecraft account. Filtered so known-benign
 * noise (a flaky network call, an optional mod that is not installed) never gets reported, and deduplicated per
 * session so one recurring failure sends one report, not a flood. Every public method is best-effort: nothing
 * here may ever throw into its caller or block the render/tick thread.
 */
public final class CrashReporter {
  private static final String DEFAULT_API = "https://api.luka.wtf";
  private static final String PATH = "/v1/report/crash";
  private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

  // substrings that mean "not worth a report": the player's own network, or an optional integration that is
  // simply not installed. Checked against the area, the exception's toString() and its cause's toString().
  private static final String[] SKIP_IF_CONTAINS = {
      "UnknownHostException", "SocketTimeoutException", "ConnectException", "NoRouteToHostException",
      "SSLHandshakeException", "ClassNotFoundException", "NoClassDefFoundError",
  };

  private CrashReporter() {}

  public static void report(String area, Throwable error) {
    try {
      if (error == null || shouldSkip(area, error)) return;
      String installId = System.getProperty("luka.installId");
      if (installId == null || installId.isEmpty()) return; // not launched through luka's own launcher
      String key = area + '|' + error.getClass().getName();
      if (!REPORTED.add(key)) return; // one report per distinct (area, error type) per session
      String message = String.valueOf(error.getMessage());
      String stack = stackTraceOf(error);
      sendAsync(installId, area, message, stack);
    } catch (Throwable ignored) {
      // reporting a crash must never itself cause one
    }
  }

  private static boolean shouldSkip(String area, Throwable error) {
    if (matches(area) || matches(error.toString())) return true;
    Throwable cause = error.getCause();
    return cause != null && cause != error && matches(cause.toString());
  }

  private static boolean matches(String text) {
    if (text == null) return false;
    for (String bad : SKIP_IF_CONTAINS) if (text.contains(bad)) return true;
    return false;
  }

  private static String stackTraceOf(Throwable error) {
    StringWriter buffer = new StringWriter();
    error.printStackTrace(new PrintWriter(buffer));
    String text = buffer.toString();
    return text.length() > 4000 ? text.substring(0, 4000) : text;
  }

  private static void sendAsync(String installId, String area, String message, String stack) {
    Thread thread = new Thread(() -> {
      try { send(installId, area, message, stack); } catch (Throwable ignored) { }
    }, "luka-crash-report");
    thread.setDaemon(true);
    thread.start();
  }

  private static void send(String installId, String area, String message, String stack) throws IOException {
    StringBuilder body = new StringBuilder();
    body.append("{\"installId\":\"").append(installId).append('"')
        .append(",\"area\":\"").append(escape(area)).append('"')
        .append(",\"message\":\"").append(escape(message)).append('"')
        .append(",\"stack\":\"").append(escape(stack)).append('"')
        .append(",\"version\":\"").append(escape(Cbui.VERSION)).append('"')
        .append('}');
    byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
    HttpURLConnection connection = (HttpURLConnection) new URL(apiBase() + PATH).openConnection();
    connection.setConnectTimeout(6000);
    connection.setReadTimeout(6000);
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
    connection.setRequestProperty("User-Agent", "luka/" + Cbui.VERSION);
    try (OutputStream out = connection.getOutputStream()) { out.write(data); }
    connection.getResponseCode();
    connection.disconnect();
  }

  private static String apiBase() {
    String base = System.getProperty("luka.api");
    if (base == null || base.isEmpty()) base = System.getenv("LUKA_API");
    if (base == null || base.isEmpty()) base = DEFAULT_API;
    while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    return base;
  }

  private static String escape(String text) {
    if (text == null) return "";
    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '\\': out.append("\\\\"); break;
        case '"': out.append("\\\""); break;
        case '\n': out.append("\\n"); break;
        case '\r': out.append("\\r"); break;
        case '\t': out.append("\\t"); break;
        default:
          if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
          else out.append(c);
      }
    }
    return out.toString();
  }
}
