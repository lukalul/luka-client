package dev.cbui.feature.polycrosshair;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;

final class CrosshairImport {
  private static final String[] PROFILES = {"normal", "friendly", "enemy"};
  private CrosshairImport() {}

  static int run(File outDir) {
    try {
      for (File candidate : locateConfigs(Minecraft.getMinecraft().mcDataDir)) {
        JsonObject config = load(candidate);
        if (config == null) continue;
        int count = export(config, outDir);
        if (count != 0) return count;
      }
    } catch (Throwable problem) {
      dev.cbui.Cbui.reportOnce("crosshair import", problem);
    }
    return 0;
  }

  private static int export(JsonObject config, File destination) throws java.io.IOException {
    int count = 0;
    for (String profile : PROFILES) {
      String encoded = stringValue(config, profile + "_image");
      Pixels art = Pixels.fromBase64(encoded);
      if (art == null) continue;
      if (!destination.isDirectory() && !destination.mkdirs()) return count;
      ImageIO.write(art.toImage(), "png", new File(destination, profile + ".png"));
      File shapeFile = new File(destination, profile + ".shapes");
      String shapes = stringValue(config, profile + "_circles");
      if (shapes == null || shapes.isEmpty()) Files.deleteIfExists(shapeFile.toPath());
      else Files.write(shapeFile.toPath(), shapes.getBytes(StandardCharsets.UTF_8));
      count++;
    }
    return count;
  }

  private static List<File> locateConfigs(File gameDir) {
    List<File> found = new ArrayList<>();
    scan(new File(gameDir, "config"), 6, found);
    scan(new File(gameDir, "OneConfig"), 6, found);
    scan(new File(gameDir, "oneconfig"), 6, found);
    File direct = new File(gameDir, "polycrosshair.json");
    if (direct.isFile()) found.add(direct);
    return found;
  }

  private static void scan(File root, int depth, List<File> output) {
    if (!root.isDirectory()) return;
    Deque<SearchNode> pending = new ArrayDeque<>();
    pending.add(new SearchNode(root, 0));
    while (!pending.isEmpty()) {
      SearchNode node = pending.removeFirst();
      File[] children = node.file.listFiles();
      if (children == null) continue;
      for (File child : children) {
        if (child.isFile() && child.getName().equalsIgnoreCase("polycrosshair.json")) output.add(child);
        else if (child.isDirectory() && node.level + 1 < depth) pending.addLast(new SearchNode(child, node.level + 1));
      }
    }
  }

  private static JsonObject load(File file) {
    try {
      JsonElement root = new JsonParser().parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
      return root.isJsonObject() ? root.getAsJsonObject() : null;
    } catch (Exception ignored) { return null; }
  }

  private static String stringValue(JsonElement root, String wanted) {
    Deque<JsonElement> pending = new ArrayDeque<>();
    pending.add(root);
    while (!pending.isEmpty()) {
      JsonElement current = pending.removeFirst();
      if (current.isJsonObject()) {
        JsonObject object = current.getAsJsonObject();
        JsonElement match = object.get(wanted);
        if (match != null && match.isJsonPrimitive()) return match.getAsString();
        for (Map.Entry<String, JsonElement> member : object.entrySet()) pending.addLast(member.getValue());
      } else if (current.isJsonArray()) {
        for (JsonElement child : current.getAsJsonArray()) pending.addLast(child);
      }
    }
    return null;
  }

  private static final class SearchNode {
    final File file; final int level;
    SearchNode(File file, int level) { this.file = file; this.level = level; }
  }
}
