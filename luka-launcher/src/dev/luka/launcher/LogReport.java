package dev.luka.launcher;

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
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * "Send logs": the logs luka's team needs to look into a problem, zipped and sent to api.luka.wtf (/v1/report/logs),
 * which hands them to luka's team on Discord. Only when the player presses the button; nothing is sent otherwise.
 *
 * <p>What goes in: report.json (luka and Minecraft version, renderer, operating system, Java, processor and graphics
 * card names, memory, the mod file names, the player's note), the game log of this and the two sessions before (lines
 * of game chat taken out), crash reports and Java crash files of the last week, the newest FPS profiler results,
 * replay render logs and the replay editor's render status, the launcher log, and the game's video options. No
 * account data, passwords or tokens (the launcher keeps those in other files, which are never read here).
 */
final class LogReport {
  /** What the player can choose to send (all ticked to start with). report.json always goes, smaller without SYSTEM. */
  enum Part {
    GAME_LOG("Game log (last 3 sessions, without chat)"),
    CRASHES("Crash reports (last week)"),
    PROFILER("FPS Profiler results"),
    RENDERS("Replay render logs and the editor's last render"),
    LAUNCHER_LOG("Launcher log"),
    SETTINGS("Video and replay settings"),
    SYSTEM("System and mod list (OS, Java, CPU and GPU names, memory, mod files)"),
    DEBUG("Debug session (GPU, CPU and FPS over time)");

    final String label;

    Part(String label) {
      this.label = label;
    }
  }

  /** The API refuses more; the zip is kept a little under it. */
  static final int MAX_ZIP = 7 * 1024 * 1024;
  private static final int MAX_TEXT = 2 * 1024 * 1024;
  private static final long WEEK_MS = 7L * 24 * 3600 * 1000;

  private final File root;
  private final Settings settings;
  private final String launcherVersion;
  private final String hardware;
  private final List<String> included = new ArrayList<>();

  LogReport(File root, Settings settings, String launcherVersion, String hardware) {
    this.root = root;
    this.settings = settings;
    this.launcherVersion = launcherVersion;
    this.hardware = hardware == null ? "" : hardware;
  }

  /** The file names that went into the last zip (shown to the player). */
  List<String> included() {
    return included;
  }

  /** Builds the zip with every part. */
  byte[] build(String note) throws IOException {
    return build(note, java.util.EnumSet.allOf(Part.class));
  }

  /** Builds the zip. note: what the player wrote about the problem (may be empty); parts: what they chose to send. */
  byte[] build(String note, java.util.Set<Part> parts) throws IOException {
    included.clear();
    GameVersion version = settings.version();
    File home = version.home(root);
    File game = new File(home, "game");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      if (parts.contains(Part.GAME_LOG)) {
        // this session and the two before (older sessions are gzipped by the game)
        addText(zip, "game/latest.log", new File(game, "logs/latest.log"), true);
        for (File f : newest(new File(game, "logs"), n -> n.endsWith(".log.gz"), 2, 0)) addFile(zip, "game/" + f.getName(), f);
      }
      if (parts.contains(Part.CRASHES)) {
        for (File f : newest(new File(game, "crash-reports"), n -> n.endsWith(".txt"), 3, WEEK_MS)) addText(zip, "crash-reports/" + f.getName(), f, false);
        for (File f : newest(game, n -> n.startsWith("hs_err_pid") && n.endsWith(".log"), 2, WEEK_MS)) addText(zip, "java-crashes/" + f.getName(), f, false);
      }
      if (parts.contains(Part.PROFILER)) {
        // luka menu > Settings > Performance > FPS Profiler
        for (File f : newest(new File(game, "config"), n -> n.startsWith("luka-profile-") && n.endsWith(".txt"), 3, 0)) addText(zip, "fps-profiler/" + f.getName(), f, false);
      }
      if (parts.contains(Part.RENDERS)) {
        for (File f : newest(new File(game, "subframe/logs"), n -> n.endsWith(".log"), 5, 0)) addText(zip, "replay-renders/" + f.getName(), f, false);
        addText(zip, "replay-editor/status.properties", new File(game, "subframe/.editor-job/status.properties"), false);
        addText(zip, "replay-editor/job.properties", new File(game, "subframe/.editor-job/job.properties"), false);
        addText(zip, "replay-editor/render-game.properties", new File(game, "subframe/.editor-job/server/server.properties"), false);
      }
      if (parts.contains(Part.SETTINGS)) {
        addText(zip, "settings/options.txt", new File(game, "options.txt"), false);
        addText(zip, "settings/subframe.properties", new File(game, "config/subframe.properties"), false);
      }
      if (parts.contains(Part.LAUNCHER_LOG)) addText(zip, "launcher/launcher.log", new File(root, "launcher.log"), false);
      if (parts.contains(Part.DEBUG)) {
        // SEND LOGS > "Record a debug session first": the launcher's side (DebugSession) and the game's (cbui DebugRecorder)
        File session = newestDebugSession(new File(root, "debug"));
        if (session != null) {
          addText(zip, "debug/system.csv", new File(session, "system.csv"), false);
          addText(zip, "debug/system-info.txt", new File(session, "system-info.txt"), false);
        }
        for (File f : newest(new File(game, "logs"), n -> n.startsWith("luka-debug-") && n.endsWith(".csv"), 1, WEEK_MS)) addText(zip, "debug/" + f.getName(), f, false);
      }
      putText(zip, "report.json", reportJson(note, game, home, parts));
    }
    byte[] out = bytes.toByteArray();
    if (out.length > MAX_ZIP) throw new IOException("the logs are too large to send (" + (out.length >> 20) + " MB)");
    return out;
  }

  /** Sends the zip; returns the report id the service answers with (to tell luka's team which one is yours). */
  static String send(String apiBase, String installId, String lukaVersion, byte[] zip) throws IOException {
    String query = "installId=" + URLEncoder.encode(installId, "UTF-8") + "&version=" + URLEncoder.encode(lukaVersion, "UTF-8")
        + "&source=launcher";
    HttpURLConnection c = (HttpURLConnection) new URL(apiBase + "/v1/report/logs?" + query).openConnection();
    c.setRequestMethod("POST");
    c.setConnectTimeout(15_000);
    c.setReadTimeout(60_000);
    c.setDoOutput(true);
    c.setRequestProperty("Content-Type", "application/zip");
    c.setFixedLengthStreamingMode(zip.length);
    try (OutputStream o = c.getOutputStream()) {
      o.write(zip);
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

  // ---- building ----

  private String reportJson(String note, File game, File home, java.util.Set<Part> parts) {
    boolean system = parts.contains(Part.SYSTEM);
    StringBuilder j = new StringBuilder("{\n");
    SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
    iso.setTimeZone(TimeZone.getTimeZone("UTC"));
    kv(j, "created", iso.format(new Date()));
    kv(j, "source", "launcher");
    kv(j, "launcher", launcherVersion);
    kv(j, "minecraft", settings.version().id);
    kv(j, "renderer", settings.renderer());
    kv(j, "luka", lukaVersion(root));
    if (system) {
      kv(j, "os", System.getProperty("os.name", "") + " " + System.getProperty("os.version", "") + " " + System.getProperty("os.arch", ""));
      kv(j, "launcherJava", System.getProperty("java.version", ""));
      kv(j, "hardware", hardware);
      kv(j, "memoryMb", Long.toString(totalMemoryMb()));
      kv(j, "gameRamMb", Integer.toString(settings.ramMb()));
    }
    kv(j, "note", note == null ? "" : note.trim());
    j.append("  \"parts\": [");
    int k = 0;
    for (Part p : parts) j.append(k++ == 0 ? "" : ", ").append(quote(p.name().toLowerCase(Locale.ROOT)));
    j.append("],\n  \"mods\": [");
    List<String> mods = new ArrayList<>();
    if (system) {
      mods.addAll(names(new File(game, "mods")));
      mods.addAll(names(new File(home, "renderers/" + settings.renderer())));
    }
    for (int i = 0; i < mods.size(); i++) j.append(i == 0 ? "" : ", ").append(quote(mods.get(i)));
    j.append("],\n  \"files\": [");
    for (int i = 0; i < included.size(); i++) j.append(i == 0 ? "" : ", ").append(quote(included.get(i)));
    j.append("]\n}\n");
    return j.toString();
  }

  /** The newest luka version in the installed update list's changelog ("## 1.6.5"), or "" when unknown. */
  static String lukaVersion(File root) {
    try {
      String m = new String(Files.readAllBytes(new File(root, "manifest.json").toPath()), StandardCharsets.UTF_8);
      java.util.regex.Matcher v = java.util.regex.Pattern.compile("## ([0-9][0-9.]*)").matcher(m);
      return v.find() ? v.group(1) : "";
    } catch (IOException | RuntimeException e) {
      return "";
    }
  }

  private static List<String> names(File dir) {
    List<String> out = new ArrayList<>();
    String[] list = dir.list();
    if (list != null) {
      Arrays.sort(list);
      for (String n : list) if (n.endsWith(".jar")) out.add(n);
    }
    return out;
  }

  private static long totalMemoryMb() {
    try {
      Object os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
      java.lang.reflect.Method m = os.getClass().getMethod("getTotalPhysicalMemorySize");
      m.setAccessible(true);
      return ((Number) m.invoke(os)).longValue() >> 20;
    } catch (Throwable t) {
      return 0;
    }
  }

  /** The newest debug/session-<time> folder of the last week, or null. */
  private static File newestDebugSession(File dir) {
    File[] sessions = dir.listFiles(f -> f.isDirectory() && f.getName().startsWith("session-"));
    if (sessions == null || sessions.length == 0) return null;
    Arrays.sort(sessions, Comparator.comparing(File::getName).reversed());
    return System.currentTimeMillis() - sessions[0].lastModified() > WEEK_MS ? null : sessions[0];
  }

  private interface NameTest {
    boolean test(String name);
  }

  /** The newest files in dir whose name passes, at most count, none older than maxAgeMs (0: any age). */
  private static List<File> newest(File dir, NameTest test, int count, long maxAgeMs) {
    File[] files = dir.listFiles(f -> f.isFile() && test.test(f.getName()));
    List<File> out = new ArrayList<>();
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

  /** A text file, its last MAX_TEXT bytes; withoutChat: lines of game chat taken out. */
  private void addText(ZipOutputStream zip, String name, File f, boolean withoutChat) throws IOException {
    if (!f.isFile()) return;
    String text = new String(tail(f, MAX_TEXT), StandardCharsets.UTF_8);
    if (withoutChat) text = withoutChat(text);
    putText(zip, name, text);
    included.add(name);
  }

  private void addFile(ZipOutputStream zip, String name, File f) throws IOException {
    if (!f.isFile() || f.length() > MAX_TEXT) return;
    zip.putNextEntry(new ZipEntry(name));
    zip.write(Files.readAllBytes(f.toPath()));
    zip.closeEntry();
    included.add(name);
  }

  private static void putText(ZipOutputStream zip, String name, String text) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(text.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  /** Game chat (what players wrote) stays private: those log lines become a marker. */
  static String withoutChat(String log) {
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
    try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
      long len = r.length();
      int n = (int) Math.min(len, max);
      byte[] b = new byte[n];
      r.seek(len - n);
      r.readFully(b);
      return b;
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
    try (InputStream i = in) {
      ByteArrayOutputStream b = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      for (int n; (n = i.read(buf)) > 0; ) b.write(buf, 0, n);
      return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }
  }

  /** A string field of a small flat JSON answer ({"ok":true,"report":"ABC"}). */
  static String field(String json, String name) {
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
    return m.find() ? m.group(1) : "";
  }
}
