package dev.cbui.feature.crosshair;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * Crosshair pictures kept in {@code <game dir>/luka/crosshair}: loading them (and noticing when the player edits
 * them), and copying them out of another crosshair mod's config file.
 */
final class CrosshairArt {
  /** File names per profile slot (Default, Ally, Foe). They are player data on disk; keep them. */
  static final String[] FILES = {"normal", "friendly", "enemy"};
  private static final String[] IMAGE_KEYS = {"normal_image", "friendly_image", "enemy_image"};
  private static final String[] STROKE_KEYS = {"normal_circles", "friendly_circles", "enemy_circles"};
  private static final int MAX_SIDE = 256;
  /** The folder's name before 2026-11-22; moved to the new name once so the player's pictures stay. */
  private static final String OLD_FOLDER = "polycrosshair";
  private static final int MAX_DEPTH = 6;

  /** A square picture, one pixel per canvas cell. */
  static final class Picture {
    final int side;
    final int[] argb;

    Picture(int side, int[] argb) {
      this.side = side;
      this.argb = argb;
    }
  }

  /** What an import found. */
  static final class Found {
    final boolean[] copied = new boolean[FILES.length];
    File source;

    int count() {
      int n = 0;
      for (boolean b : copied) if (b) n++;
      return n;
    }
  }

  private final File gameDir;
  private final Picture[] pictures = new Picture[FILES.length];
  private final long[] seen = new long[FILES.length];
  private boolean loaded;
  private int version;

  CrosshairArt(File gameDir) {
    this.gameDir = gameDir;
  }

  File folder() {
    File luka = new File(gameDir, "luka"), folder = new File(luka, "crosshair"), old = new File(luka, OLD_FOLDER);
    if (!folder.exists() && old.isDirectory() && !old.renameTo(folder)) return old;
    return folder;
  }

  /** The picture of a profile slot, or null when there is none (or it is unreadable). */
  Picture picture(int slot) {
    if (!loaded) rescan();
    return pictures[slot];
  }

  /** Changes whenever any picture changes, so cached textures know to rebuild. */
  int version() {
    return version;
  }

  /** Re-reads every file whose size or time changed. Cheap when nothing changed. */
  void rescan() {
    loaded = true;
    File folder = folder();
    for (int i = 0; i < FILES.length; i++) {
      File file = new File(folder, FILES[i] + ".png");
      long mark = file.isFile() ? file.lastModified() * 31L + file.length() : -1L;
      if (mark == seen[i]) continue;
      seen[i] = mark;
      pictures[i] = mark == -1L ? null : read(file);
      version++;
    }
  }

  private static Picture read(File file) {
    try {
      BufferedImage image = ImageIO.read(file);
      return toPicture(image);
    } catch (Throwable error) {
      return null;
    }
  }

  private static Picture toPicture(BufferedImage image) {
    if (image == null) return null;
    int side = image.getWidth();
    if (side < 1 || side > MAX_SIDE || image.getHeight() != side) return null;
    int[] argb = image.getRGB(0, 0, side, side, null, 0, side);
    return new Picture(side, argb);
  }

  /** Looks for another crosshair mod's config file and copies its drawings here. Returns null when nothing usable was found. */
  Found importDrawings() {
    List<File> candidates = new ArrayList<File>();
    Set<String> visited = new HashSet<String>();
    String[] roots = {"config", "OneConfig", "oneconfig"};
    for (String root : roots) collect(new File(gameDir, root), 0, candidates, visited);
    File[] top = gameDir.listFiles();
    if (top != null) {
      Arrays.sort(top);
      for (File file : top) if (file.isFile() && isConfigName(file)) addOnce(file, candidates, visited);
    }
    for (File candidate : candidates) {
      Found found = tryFile(candidate);
      if (found != null) return found;
    }
    return null;
  }

  private static boolean isConfigName(File file) {
    return file.getName().equalsIgnoreCase("polycrosshair.json");
  }

  private static void collect(File dir, int depth, List<File> out, Set<String> visited) {
    if (depth > MAX_DEPTH || !dir.isDirectory()) return;
    if (!visited.add(key(dir))) return;
    File[] children = dir.listFiles();
    if (children == null) return;
    Arrays.sort(children);
    for (File child : children) if (child.isFile() && isConfigName(child)) addOnce(child, out, visited);
    for (File child : children) if (child.isDirectory()) collect(child, depth + 1, out, visited);
  }

  private static void addOnce(File file, List<File> out, Set<String> visited) {
    if (visited.add(key(file))) out.add(file);
  }

  private static String key(File file) {
    try {
      return file.getCanonicalPath().toLowerCase(java.util.Locale.ROOT);
    } catch (Throwable error) {
      return file.getAbsolutePath().toLowerCase(java.util.Locale.ROOT);
    }
  }

  private Found tryFile(File file) {
    String[] images = new String[FILES.length];
    String[] strokes = new String[FILES.length];
    try {
      Reader reader = new InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8);
      try {
        JsonElement root = new JsonParser().parse(reader);
        gather(root, images, strokes);
      } finally {
        reader.close();
      }
    } catch (Throwable error) {
      return null;
    }
    byte[][] png = new byte[FILES.length][];
    boolean any = false;
    for (int i = 0; i < FILES.length; i++) {
      png[i] = decode(images[i]);
      if (png[i] != null) any = true;
    }
    if (!any) return null;
    Found found = new Found();
    found.source = file;
    File folder = folder();
    try {
      if (!folder.isDirectory() && !folder.mkdirs()) return null;
      for (int i = 0; i < FILES.length; i++) {
        if (png[i] == null) continue;
        Files.write(new File(folder, FILES[i] + ".png").toPath(), png[i]);
        File shapes = new File(folder, FILES[i] + ".shapes");
        String text = strokes[i] == null ? "" : strokes[i].trim();
        if (!text.isEmpty()) Files.write(shapes.toPath(), text.getBytes(StandardCharsets.UTF_8));
        else if (shapes.isFile() && !shapes.delete()) shapes.deleteOnExit();
        found.copied[i] = true;
      }
    } catch (Throwable error) {
      return found.count() > 0 ? found : null;
    }
    rescan();
    return found;
  }

  /** Walks the whole tree and keeps the first string value of each wanted key. */
  private static void gather(JsonElement element, String[] images, String[] strokes) {
    if (element == null) return;
    if (element.isJsonObject()) {
      for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
        JsonElement value = entry.getValue();
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
          int image = indexOf(IMAGE_KEYS, entry.getKey());
          if (image >= 0 && images[image] == null) images[image] = value.getAsString();
          int stroke = indexOf(STROKE_KEYS, entry.getKey());
          if (stroke >= 0 && strokes[stroke] == null) strokes[stroke] = value.getAsString();
        } else {
          gather(value, images, strokes);
        }
      }
    } else if (element.isJsonArray()) {
      for (JsonElement child : element.getAsJsonArray()) gather(child, images, strokes);
    }
  }

  private static int indexOf(String[] keys, String key) {
    for (int i = 0; i < keys.length; i++) if (keys[i].equals(key)) return i;
    return -1;
  }

  /** Base64 text to PNG bytes, only when it really is a square picture of an allowed size. */
  private static byte[] decode(String text) {
    if (text == null) return null;
    String body = text.trim();
    int comma = body.indexOf("base64,");
    if (comma >= 0) body = body.substring(comma + 7);
    if (body.isEmpty()) return null;
    try {
      byte[] bytes = Base64.getMimeDecoder().decode(body);
      return toPicture(ImageIO.read(new ByteArrayInputStream(bytes))) != null ? bytes : null;
    } catch (Throwable error) {
      return null;
    }
  }
}
