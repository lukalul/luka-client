package dev.cbui.feature.shotchat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/** Anonymous upload of one picture to catbox.moe (public). Blocking: call it from a background thread. */
final class CatboxUpload {
  private static final String API = "https://catbox.moe/user/api.php";

  private CatboxUpload() {}

  /** Uploads the file and returns its public https link. */
  static String upload(File file) throws IOException {
    String boundary = "luka" + UUID.randomUUID().toString().replace("-", "");
    byte[] picture = Files.readAllBytes(file.toPath());
    ByteArrayOutputStream body = new ByteArrayOutputStream(picture.length + 512);
    text(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"reqtype\"\r\n\r\nfileupload\r\n");
    text(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"fileToUpload\"; filename=\""
        + safeName(file.getName()) + "\"\r\nContent-Type: image/png\r\n\r\n");
    body.write(picture);
    text(body, "\r\n--" + boundary + "--\r\n");
    byte[] payload = body.toByteArray();

    HttpURLConnection http = (HttpURLConnection) new URL(API).openConnection();
    try {
      http.setConnectTimeout(10_000);
      http.setReadTimeout(60_000);
      http.setDoOutput(true);
      http.setRequestMethod("POST");
      http.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
      http.setRequestProperty("User-Agent", "luka-client");
      http.setFixedLengthStreamingMode(payload.length);
      try (OutputStream out = http.getOutputStream()) {
        out.write(payload);
      }
      int code = http.getResponseCode();
      if (code != 200) throw new IOException("catbox.moe answered HTTP " + code);
      String answer;
      try (InputStream in = http.getInputStream()) {
        ByteArrayOutputStream got = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        for (int n; (n = in.read(buf)) > 0 && got.size() < 4096; ) got.write(buf, 0, n);
        answer = new String(got.toByteArray(), StandardCharsets.UTF_8).trim();
      }
      if (!answer.startsWith("https://") || answer.indexOf(' ') >= 0 || answer.indexOf('\n') >= 0) {
        throw new IOException("catbox.moe did not return a link");
      }
      return answer;
    } finally {
      http.disconnect();
    }
  }

  private static String safeName(String name) {
    return name.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private static void text(ByteArrayOutputStream out, String s) throws IOException {
    out.write(s.getBytes(StandardCharsets.UTF_8));
  }
}
