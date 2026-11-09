package dev.luka.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small HTTP and JSON helpers (the answers we read are flat enough for key lookups). */
final class Http {
  private Http() {}

  static String postJson(String url, String json, String bearer) throws IOException {
    return send(url, "application/json", json, bearer);
  }

  static String postForm(String url, Map<String, String> fields) throws IOException {
    StringBuilder body = new StringBuilder();
    for (Map.Entry<String, String> e : fields.entrySet()) {
      if (body.length() > 0) body.append('&');
      body.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(e.getValue(), "UTF-8"));
    }
    return send(url, "application/x-www-form-urlencoded", body.toString(), null);
  }

  static String get(String url, String bearer) throws IOException {
    return send(url, null, null, bearer);
  }

  /** Tries up to three times when the connection is reset or the TLS handshake is cut (some networks do that now and then). */
  private static String send(String url, String type, String body, String bearer) throws IOException {
    IOException last = null;
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        return sendOnce(url, type, body, bearer);
      } catch (java.net.SocketException | javax.net.ssl.SSLException | java.net.SocketTimeoutException e) {
        last = e;
        try {
          Thread.sleep(700L * (attempt + 1));
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    throw last;
  }

  private static String sendOnce(String url, String type, String body, String bearer) throws IOException {
    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
    c.setConnectTimeout(15000);
    c.setReadTimeout(30000);
    c.setRequestProperty("Accept", "application/json");
    c.setRequestProperty("User-Agent", "luka-launcher/1.0");
    // Xbox answers 400 without this header
    if (url.contains("xboxlive.com")) c.setRequestProperty("x-xbl-contract-version", "1");
    if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
    if (body != null) {
      c.setDoOutput(true);
      c.setRequestMethod("POST");
      c.setRequestProperty("Content-Type", type);
      try (OutputStream out = c.getOutputStream()) {
        out.write(body.getBytes(StandardCharsets.UTF_8));
      }
    }
    int code = c.getResponseCode();
    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
    String text = in == null ? "" : read(in);
    if (code >= 400 && !(url.contains("oauth2") && text.contains("authorization_pending")) && !text.contains("slow_down")) {
      throw new IOException("HTTP " + code + " from " + url + ": " + text);
    }
    return text;
  }

  private static String read(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int n;
    while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
    in.close();
    return new String(out.toByteArray(), StandardCharsets.UTF_8);
  }

  /** The first string value of "key" anywhere in the JSON text, or null. (Scanned by hand: a regex with a repeated group overflows the stack on a long token.) */
  static String str(String json, String key) {
    String needle = "\"" + key + "\"";
    int from = 0;
    while (true) {
      int at = json.indexOf(needle, from);
      if (at < 0) return null;
      int i = at + needle.length();
      while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
      if (i < json.length() && json.charAt(i) == ':') {
        i++;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i < json.length() && json.charAt(i) == '"') {
          StringBuilder out = new StringBuilder();
          for (i++; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
              char n = json.charAt(++i);
              if (n == 'n') out.append('\n');
              else if (n == 't') out.append('\t');
              else if (n == 'u' && i + 4 < json.length()) {
                out.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                i += 4;
              } else out.append(n);
            } else if (c == '"') {
              return out.toString();
            } else {
              out.append(c);
            }
          }
          return null;
        }
      }
      from = at + needle.length();
    }
  }

  static int integer(String json, String key, int fallback) {
    Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json);
    return m.find() ? Integer.parseInt(m.group(1)) : fallback;
  }
}
