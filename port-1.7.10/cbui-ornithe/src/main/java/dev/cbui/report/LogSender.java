package dev.cbui.report;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * "Send logs" from the game (luka's title screen): the same zip as the launcher's SEND LOGS, of the parts the player
 * ticked, sent to api.luka.wtf (/v1/report/logs, source=game), which hands it to luka's team on Discord. Nothing is
 * sent unless the player presses SEND. No Minecraft classes here: the screen passes the game folder and the names.
 */
public final class LogSender {
  public static final String MINECRAFT = "1.7.10";

  /** What the player can choose (all ticked to start with). report.json always goes, smaller without SYSTEM. */
  public enum Part {
    GAME_LOG("Game log (last 3 sessions, without chat)"),
    CRASHES("Crash reports (last week)"),
    PROFILER("FPS Profiler results"),
    RENDERS("Replay render logs"),
    LAUNCHER_LOG("Launcher log"),
    SETTINGS("Video and replay settings"),
    SYSTEM("System and mod list"),
    DEBUG("Debug session (GPU, CPU, FPS)");

    public final String label;

    Part(String label) {
      this.label = label;
    }
  }

  public static final int MAX_ZIP = 7 * 1024 * 1024;
  private static final int MAX_TEXT = 2 * 1024 * 1024;
  private static final long WEEK_MS = 7L * 24 * 3600 * 1000;

  private final File game;
  /** The luka launcher's folder (launcher.log, the update list), or null without the launcher. */
  private final File lukaHome;
  private final List<String> included = new ArrayList<String>();

  public LogSender(File game) {
    this.game = game;
    String home = System.getProperty("luka.home");
    this.lukaHome = home == null || home.isEmpty() ? null : new File(home);
  }

  public List<String> included() {
    return included;
  }

  /** Builds the zip. note: what the player wrote; gpu: the graphics card's name; mods: "id version" of the loaded mods. */
  public byte[] build(String note, Set<Part> parts, String renderer, String gpu, List<String> mods) throws IOException {
    included.clear();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ZipOutputStream zip = new ZipOutputStream(bytes);
    try {
      if (parts.contains(Part.GAME_LOG)) {
        addText(zip, "game/latest.log", new File(game, "logs/latest.log"), true);
        for (File f : newest(new File(game, "logs"), ".log.gz", null, 2, 0)) addFile(zip, "game/" + f.getName(), f);
      }
      if (parts.contains(Part.CRASHES)) {
        for (File f : newest(new File(game, "crash-reports"), ".txt", null, 3, WEEK_MS)) addText(zip, "crash-reports/" + f.getName(), f, false);
        for (File f : newest(game, ".log", "hs_err_pid", 2, WEEK_MS)) addText(zip, "java-crashes/" + f.getName(), f, false);
      }
      if (parts.contains(Part.PROFILER)) {
        for (File f : newest(new File(game, "config"), ".txt", "luka-profile-", 3, 0)) addText(zip, "fps-profiler/" + f.getName(), f, false);
      }
      if (parts.contains(Part.RENDERS)) {
        for (File f : newest(new File(game, "subframe/logs"), ".log", null, 5, 0)) addText(zip, "replay-renders/" + f.getName(), f, false);
        addText(zip, "replay-editor/status.properties", new File(game, "subframe/.editor-job/status.properties"), false);
        addText(zip, "replay-editor/job.properties", new File(game, "subframe/.editor-job/job.properties"), false);
      }
      if (parts.contains(Part.SETTINGS)) {
        addText(zip, "settings/options.txt", new File(game, "options.txt"), false);
        addText(zip, "settings/subframe.properties", new File(game, "config/subframe.properties"), false);
      }
      if (parts.contains(Part.LAUNCHER_LOG) && lukaHome != null) addText(zip, "launcher/launcher.log", new File(lukaHome, "launcher.log"), false);
      if (parts.contains(Part.DEBUG)) {
        // debug mode (launcher SEND LOGS > "Record a debug session first"): the game's side (DebugRecorder) and the launcher's
        for (File f : newest(new File(game, "logs"), ".csv", "luka-debug-", 1, WEEK_MS)) addText(zip, "debug/" + f.getName(), f, false);
        File session = lukaHome == null ? null : newestDebugSession(new File(lukaHome, "debug"));
        if (session != null) {
          addText(zip, "debug/system.csv", new File(session, "system.csv"), false);
          addText(zip, "debug/system-info.txt", new File(session, "system-info.txt"), false);
        }
      }
      putText(zip, "report.json", reportJson(note, parts, renderer, gpu, mods));
    } finally {
      zip.close();
    }
    byte[] out = bytes.toByteArray();
    if (out.length > MAX_ZIP) throw new IOException("the logs are too large to send (" + (out.length >> 20) + " MB)");
    return out;
  }

  /** Sends the zip; returns the report id to quote. */
  public String send(byte[] zip) throws IOException {
    String base = System.getProperty("luka.api", "https://api.luka.wtf");
    String query = "installId=" + URLEncoder.encode(installId(), "UTF-8") + "&version=" + URLEncoder.encode(lukaVersion(), "UTF-8")
        + "&source=game";
    HttpURLConnection c = (HttpURLConnection) new URL(base + "/v1/report/logs?" + query).openConnection();
    c.setRequestMethod("POST");
    c.setConnectTimeout(15000);
    c.setReadTimeout(60000);
    c.setDoOutput(true);
    c.setRequestProperty("Content-Type", "application/zip");
    c.setFixedLengthStreamingMode(zip.length);
    OutputStream o = c.getOutputStream();
    try {
      o.write(zip);
    } finally {
      o.close();
    }
    int code = c.getResponseCode();
    String body = read(code < 400 ? c.getInputStream() : c.getErrorStream());
    if (code != 200) {
      String error = field(body, "error");
      throw new IOException(error.isEmpty() ? "the service answered " + code : error);
    }
    String id = field(body, "report");
    if (id.isEmpty()) throw new IOException("the service gave no report id");
    return id;
  }

  /** The launcher's per-install id (-Dluka.installId); without the launcher, one kept in the game's config folder. */
  private String installId() {
    String id = System.getProperty("luka.installId", "").trim().toLowerCase(Locale.ROOT);
    if (id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) return id;
    File file = new File(game, "config/luka-install-id.txt");
    try {
      if (file.isFile()) {
        String kept = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
        if (kept.matches("[0-9a-f-]{36}")) return kept;
      }
      String made = UUID.randomUUID().toString();
      file.getParentFile().mkdirs();
      Files.write(file.toPath(), made.getBytes(StandardCharsets.UTF_8));
      return made;
    } catch (IOException e) {
      return UUID.randomUUID().toString();
    }
  }

  /** The newest luka version in the launcher's update list ("## 1.6.5"), or "" without the launcher. */
  private String lukaVersion() {
    if (lukaHome == null) return "";
    try {
      String m = new String(Files.readAllBytes(new File(lukaHome, "manifest.json").toPath()), StandardCharsets.UTF_8);
      java.util.regex.Matcher v = java.util.regex.Pattern.compile("## ([0-9][0-9.]*)").matcher(m);
      return v.find() ? v.group(1) : "";
    } catch (IOException | RuntimeException e) {
      return "";
    }
  }

  private String reportJson(String note, Set<Part> parts, String renderer, String gpu, List<String> mods) {
    boolean system = parts.contains(Part.SYSTEM);
    SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
    iso.setTimeZone(TimeZone.getTimeZone("UTC"));
    StringBuilder j = new StringBuilder("{\n");
    kv(j, "created", iso.format(new Date()));
    kv(j, "source", "game");
    kv(j, "luka", lukaVersion());
    kv(j, "minecraft", MINECRAFT);
    kv(j, "renderer", renderer);
    if (system) {
      kv(j, "os", System.getProperty("os.name", "") + " " + System.getProperty("os.version", "") + " " + System.getProperty("os.arch", ""));
      kv(j, "gameJava", System.getProperty("java.version", ""));
      String cpu = System.getenv("PROCESSOR_IDENTIFIER");
      kv(j, "hardware", (cpu == null ? "" : cpu) + " | " + gpu);
      kv(j, "gameRamMb", Long.toString(Runtime.getRuntime().maxMemory() >> 20));
    }
    kv(j, "note", note == null ? "" : note.trim());
    j.append("  \"parts\": [");
    int k = 0;
    for (Part p : parts) j.append(k++ == 0 ? "" : ", ").append(quote(p.name().toLowerCase(Locale.ROOT)));
    j.append("],\n  \"mods\": [");
    if (system) {
      for (int i = 0; i < mods.size(); i++) j.append(i == 0 ? "" : ", ").append(quote(mods.get(i)));
    }
    j.append("],\n  \"files\": [");
    for (int i = 0; i < included.size(); i++) j.append(i == 0 ? "" : ", ").append(quote(included.get(i)));
    j.append("]\n}\n");
    return j.toString();
  }

  /** The newest files in dir ending in suffix (and starting with prefix, when given), at most count, none older than maxAgeMs (0: any age). */
  /** The launcher's newest debug/session-<time> folder of the last week, or null. */
  private static File newestDebugSession(File dir) {
    File[] sessions = dir.listFiles(f -> f.isDirectory() && f.getName().startsWith("session-"));
    if (sessions == null || sessions.length == 0) return null;
    File best = sessions[0];
    for (File f : sessions) if (f.getName().compareTo(best.getName()) > 0) best = f;
    return System.currentTimeMillis() - best.lastModified() > WEEK_MS ? null : best;
  }

  private static List<File> newest(File dir, final String suffix, final String prefix, int count, long maxAgeMs) {
    File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(suffix) && (prefix == null || f.getName().startsWith(prefix)));
    List<File> out = new ArrayList<File>();
    if (files == null) return out;
    Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
    long now = System.currentTimeMillis();
    for (File f : files) {
      if (out.size() >= count) break;
      if (maxAgeMs > 0 && now - f.lastModified() > maxAgeMs) continue;
      out.add(f);
    }
    return out;
  }

  private void addText(ZipOutputStream zip, String name, File f, boolean withoutChat) throws IOException {
    if (!f.isFile()) return;
    String text = new String(tail(f, MAX_TEXT), StandardCharsets.UTF_8);
    if (withoutChat) text = withoutChat(text);
    putText(zip, name, text);
    included.add(name);
  }

  /** A gzipped older game log: unpacked, game chat and sign-in secrets taken out, packed again. */
  private void addFile(ZipOutputStream zip, String name, File f) throws IOException {
    if (!f.isFile() || f.length() > MAX_TEXT) return;
    byte[] data = LogScrub.gzipText(java.nio.file.Files.readAllBytes(f.toPath()), new LogScrub.TextEdit() {
      @Override public String apply(String text) {
        return LogScrub.clean(withoutChat(text));
      }
    });
    zip.putNextEntry(new ZipEntry(name));
    zip.write(data);
    zip.closeEntry();
    included.add(name);
  }


  private static void putText(ZipOutputStream zip, String name, String text) throws IOException {
    text = LogScrub.clean(text);
    zip.putNextEntry(new ZipEntry(name));
    zip.write(text.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  /** Game chat (what players wrote) stays private: those log lines become a marker. */
  public static String withoutChat(String log) {
    StringBuilder out = new StringBuilder(log.length());
    int removed = 0;
    for (String line : log.split("\r?\n", -1)) {
      if (line.contains("[CHAT]")) {
        removed++;
        continue;
      }
      out.append(line).append('\n');
    }
    if (removed > 0) out.append("[luka: ").append(removed).append(" lines of game chat left out]\n");
    return out.toString();
  }

  private static byte[] tail(File f, int max) throws IOException {
    RandomAccessFile r = new RandomAccessFile(f, "r");
    try {
      long len = r.length();
      int n = (int) Math.min(len, max);
      byte[] b = new byte[n];
      r.seek(len - n);
      r.readFully(b);
      return b;
    } finally {
      r.close();
    }
  }

  private static void kv(StringBuilder j, String key, String value) {
    j.append("  ").append(quote(key)).append(": ").append(quote(value)).append(",\n");
  }

  static String quote(String s) {
    StringBuilder q = new StringBuilder("\"");
    for (char ch : s.toCharArray()) {
      if (ch == '"' || ch == '\\') q.append('\\').append(ch);
      else if (ch < 0x20) q.append(String.format("\\u%04x", (int) ch));
      else q.append(ch);
    }
    return q.append('"').toString();
  }

  private static String read(InputStream in) throws IOException {
    if (in == null) return "";
    try {
      ByteArrayOutputStream b = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
      return new String(b.toByteArray(), StandardCharsets.UTF_8);
    } finally {
      in.close();
    }
  }

  static String field(String json, String name) {
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
    return m.find() ? m.group(1) : "";
  }

  /** All parts ticked. */
  public static Set<Part> all() {
    return EnumSet.allOf(Part.class);
  }
}
