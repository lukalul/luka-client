package dev.luka.launcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Takes sign-in secrets out of log text: Minecraft's "(Session ID is token:...:...)" start-up line, --accessToken on
 * command lines (Java crash files list the whole command line), "accessToken"/"refresh_token"/... values in any
 * key=value or JSON form, "Bearer ..." headers and JWTs (Microsoft/Xbox/Minecraft tokens). Used on everything SEND LOGS
 * sends, and (launcher) on the game's own log files before each start, so the player never has their session ID
 * sitting in a log either. The same file exists in the launcher and in cbui (both versions); keep them identical.
 */
public final class LogScrub {
  private static final String HIDDEN = "<hidden by luka>";
  private static final Pattern SESSION_LINE = Pattern.compile("\\(Session ID is [^)\\r\\n]*\\)");
  private static final Pattern TOKEN_TRIPLE = Pattern.compile("token:[^:\\s)]{2,}:[0-9a-fA-F-]{32,36}");
  private static final Pattern ARG = Pattern.compile(
      "(--(?:accessToken|session|clientId|xuid)[ =])(?!<hidden)(\\S+)");
  private static final Pattern KEY_VALUE = Pattern.compile(
      "(?i)(\"?(?:access_?token|refresh_?token|id_?token|session_?id|client_?token|auth_?token|xbl_?token|xsts_?token"
          + "|minecraft_?token|authorization|password|passwd|secret|api_?key)\"?\\s*[:=]\\s*\"?)(?!<hidden)(?:Bearer\\s+)?"
          + "(?!<hidden)([^\\s\"',;}&]{6,})");
  private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]{12,}");
  private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}");
  private static final Pattern XBL = Pattern.compile("XBL3\\.0 x=[^;\\s]+;(?!<hidden)[^\\s\"']+");

  private LogScrub() {
  }

  /** text with every secret replaced; unchanged when there is none. */
  public static String clean(String text) {
    if (text == null || text.isEmpty()) return text;
    String s = SESSION_LINE.matcher(text).replaceAll("(Session ID " + Matcher.quoteReplacement(HIDDEN) + ")");
    s = TOKEN_TRIPLE.matcher(s).replaceAll(Matcher.quoteReplacement(HIDDEN));
    s = ARG.matcher(s).replaceAll("$1" + Matcher.quoteReplacement(HIDDEN));
    s = KEY_VALUE.matcher(s).replaceAll("$1" + Matcher.quoteReplacement(HIDDEN));
    s = BEARER.matcher(s).replaceAll("$1" + Matcher.quoteReplacement(HIDDEN));
    s = XBL.matcher(s).replaceAll(Matcher.quoteReplacement("XBL3.0 " + HIDDEN));
    s = JWT.matcher(s).replaceAll(Matcher.quoteReplacement(HIDDEN));
    return s;
  }

  /** A gzipped log: unpacked, passed through edit, packed again. */
  public static byte[] gzipText(byte[] gz, TextEdit edit) throws IOException {
    String text;
    InputStream in = new GZIPInputStream(new ByteArrayInputStream(gz));
    try {
      text = new String(readAll(in), StandardCharsets.UTF_8);
    } finally {
      in.close();
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    OutputStream out = new GZIPOutputStream(bytes);
    try {
      out.write(edit.apply(text).getBytes(StandardCharsets.UTF_8));
    } finally {
      out.close();
    }
    return bytes.toByteArray();
  }

  /** Rewrites a log file on disk (plain or .gz) when it holds a secret. Returns true when it changed. */
  public static boolean scrubFile(File f) {
    try {
      if (!f.isFile() || f.length() > 64L * 1024 * 1024) return false;
      byte[] raw = Files.readAllBytes(f.toPath());
      boolean gz = f.getName().endsWith(".gz");
      String text;
      if (gz) {
        InputStream in = new GZIPInputStream(new ByteArrayInputStream(raw));
        try {
          text = new String(readAll(in), StandardCharsets.UTF_8);
        } finally {
          in.close();
        }
      } else {
        text = new String(raw, StandardCharsets.UTF_8);
      }
      String cleaned = clean(text);
      if (cleaned.equals(text)) return false;
      byte[] out;
      if (gz) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        OutputStream z = new GZIPOutputStream(bytes);
        try {
          z.write(cleaned.getBytes(StandardCharsets.UTF_8));
        } finally {
          z.close();
        }
        out = bytes.toByteArray();
      } else {
        out = cleaned.getBytes(StandardCharsets.UTF_8);
      }
      File tmp = new File(f.getPath() + ".luka-tmp");
      Files.write(tmp.toPath(), out);
      Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      return true;
    } catch (Throwable e) {
      return false;
    }
  }

  /** Every log the game folder can hold: logs/ (plain and .gz), crash-reports/, Java crash files. */
  public static int scrubGameFolder(File game) {
    int changed = 0;
    changed += scrubAll(new File(game, "logs"), ".log", ".log.gz");
    changed += scrubAll(new File(game, "crash-reports"), ".txt");
    File[] files = game.listFiles();
    if (files != null) {
      for (File f : files) {
        String n = f.getName();
        if (n.startsWith("hs_err_pid") && n.endsWith(".log") && scrubFile(f)) changed++;
      }
    }
    return changed;
  }

  private static int scrubAll(File dir, String... suffixes) {
    int changed = 0;
    File[] files = dir.listFiles();
    if (files == null) return 0;
    for (File f : files) {
      for (String suffix : suffixes) {
        if (f.getName().endsWith(suffix)) {
          if (scrubFile(f)) changed++;
          break;
        }
      }
    }
    return changed;
  }

  private static byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int n;
    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    return out.toByteArray();
  }

  /** What gzipText does to the unpacked text. */
  public interface TextEdit {
    String apply(String text);
  }
}
