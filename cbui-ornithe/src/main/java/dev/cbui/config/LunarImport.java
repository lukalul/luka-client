package dev.cbui.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;
import dev.cbui.hud.HudManager;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Copies a Lunar Client profile's mod switches and HUD positions onto luka's modules. Reads only this player's own
 * Lunar settings on this PC ({@code ~/.lunarclient/settings/game/<profile>/mods.json} and {@code profile_manager.json});
 * nothing is sent anywhere, and Lunar's files are never written. Lunar saves only what differs from its defaults, so a
 * mod Lunar has no entry for keeps luka's setting.
 *
 * Positions: Lunar stores an anchor name and an offset in GUI pixels, measured to the element's corner (or edge) nearest
 * that anchor; luka stores an anchor and an offset from a box sitting 2 px inside that corner. The two boxes are not the
 * same size, so a converted element lands where its Lunar counterpart was by that corner, which is close enough to fine
 * tune in the HUD editor.
 */
public final class LunarImport {
  /** Lunar mod id -> luka module name. HUD elements first, then switch-only features. */
  private static final Map<String, String> MODULES = new LinkedHashMap<>();
  /** Lunar mods whose position lives on a child entry, not on the mod itself. */
  private static final Map<String, String> POSITION_CHILD = new LinkedHashMap<>();

  static {
    MODULES.put("FPS", "FPS");
    MODULES.put("CPS", "CPS");
    MODULES.put("KEYSTROKES", "Keystrokes");
    MODULES.put("COORDINATES", "Coordinates");
    MODULES.put("ARMORSTATUS", "Armour status");
    MODULES.put("POTION_EFFECTS", "Potion status");
    MODULES.put("DIRECTION_HUD", "Direction");
    MODULES.put("TOGGLE_SNEAK", "Toggle sprint");
    MODULES.put("POTION_COUNTER", "Potion counter");
    MODULES.put("ITEM_COUNTER", "Item counter");
    MODULES.put("PING", "Ping");
    MODULES.put("MEMORY", "Memory");
    MODULES.put("CLOCK", "Clock");
    MODULES.put("COMBO", "Combo");
    MODULES.put("REACH_DISPLAY", "Reach");
    MODULES.put("COOLDOWNS", "Cooldowns");
    MODULES.put("SERVER_ADDRESS", "Server address");
    MODULES.put("SCOREBOARD", "Scoreboard");
    MODULES.put("TEAM_VIEW", "Team HUD");
    MODULES.put("PACK_DISPLAY", "Resource packs");
    MODULES.put("MENU_BLUR", "GUI blur");
    MODULES.put("FREELOOK", "Freelook");
    MODULES.put("ZOOM", "Zoom");
    MODULES.put("FOV", "FOVChanger");
    MODULES.put("HURT_CAM", "BetterHurtCam");
    MODULES.put("CROSSHAIR", "Crosshair");
    MODULES.put("HITBOX", "Hitboxes");
    MODULES.put("BLOCK_OUTLINE", "Block Overlay");
    MODULES.put("TIME_CHANGER", "Time Changer");
    MODULES.put("DAMAGE_TINT", "DamageTint");
    MODULES.put("NAMETAG", "Nametags");
    MODULES.put("CHAT", "Chat");
    MODULES.put("OVERLAY_MOD", "Overlay Tweaks");
    MODULES.put("TNT_COUNTDOWN", "TNTCountdown");
    MODULES.put("SOUND_CHANGER", "SoundTweaks");
    MODULES.put("SCREENSHOT", "Screenshot Message Enhancer");
    MODULES.put("MOTION_BLUR", "Motion Blur");
    POSITION_CHILD.put("TOGGLE_SNEAK", "TOGGLE_SNEAK_HUD_CHILD");
    POSITION_CHILD.put("PING", "PING_HUD");
  }

  /** luka modules whose off switch hides the thing entirely (not "back to vanilla" as in Lunar). */
  private static final java.util.Set<String> HIDES_WHEN_OFF =
      new java.util.HashSet<String>(java.util.Arrays.asList("Chat", "Scoreboard"));

  private LunarImport() {}

  /** Lunar's per-profile settings folder, or null when Lunar Client is not installed for this user. */
  public static File settingsDir() {
    File dir = new File(System.getProperty("user.home"), ".lunarclient/settings/game");
    return dir.isDirectory() ? dir : null;
  }

  /** {folder, display name} for every Lunar profile that has a mods.json; the active one first. */
  public static List<String[]> profiles() {
    File dir = settingsDir();
    if (dir == null) return Collections.emptyList();
    List<String[]> out = new ArrayList<>();
    try {
      JsonElement root = read(new File(dir, "profile_manager.json"));
      if (root != null && root.isJsonArray()) {
        for (JsonElement e : root.getAsJsonArray()) {
          if (!e.isJsonObject()) continue;
          JsonObject p = e.getAsJsonObject();
          String folder = str(p, "name");
          if (folder == null || !safeFolder(folder) || !new File(new File(dir, folder), "mods.json").isFile()) continue;
          String display = str(p, "displayName");
          String[] entry = {folder, display == null || display.isEmpty() ? folder : display};
          if (p.has("active") && p.get("active").getAsBoolean()) out.add(0, entry);
          else out.add(entry);
        }
      }
    } catch (Exception unreadable) {
      // fall through to the folders themselves
    }
    if (out.isEmpty()) {
      File[] folders = dir.listFiles(File::isDirectory);
      if (folders != null)
        for (File f : folders) if (new File(f, "mods.json").isFile()) out.add(new String[] {f.getName(), f.getName()});
    }
    return out;
  }

  /** Applies the Lunar profile in {@code folder} to the live modules. Returns how many modules changed. */
  public static int apply(String folder, HudManager hud) throws Exception {
    File dir = settingsDir();
    if (dir == null || !safeFolder(folder)) throw new IllegalStateException("Lunar Client settings not found");
    JsonElement root = read(new File(new File(dir, folder), "mods.json"));
    if (root == null || !root.isJsonObject()) throw new IllegalStateException("Lunar profile has no mods.json");
    JsonObject mods = root.getAsJsonObject();
    int changed = 0;
    for (Map.Entry<String, String> m : MODULES.entrySet()) {
      JsonElement e = mods.get(m.getKey());
      if (e == null || !e.isJsonObject()) continue;
      AbstractHudModule module = hud.find(m.getValue());
      if (module == null) continue;
      JsonObject lunar = e.getAsJsonObject();
      boolean touched = false;
      if (lunar.has("enabled") && lunar.get("enabled").isJsonPrimitive()) {
        boolean on = lunar.get("enabled").getAsBoolean();
        // Lunar's Chat/Scoreboard mod off means vanilla chat/sidebar; luka's off hides them completely, so keep them on
        if (on || !HIDES_WHEN_OFF.contains(m.getValue())) {
          module.enabled(on);
          touched = true;
        }
      }
      JsonObject place = lunar.has("position") ? lunar : null;
      String child = POSITION_CHILD.get(m.getKey());
      if (place == null && child != null && lunar.has(child) && lunar.get(child).isJsonObject()
          && lunar.getAsJsonObject(child).has("position")) place = lunar.getAsJsonObject(child);
      if (place != null && module.hasHud() && placeLike(module, place)) touched = true;
      if (touched) changed++;
    }
    return changed;
  }

  /** Moves the module to where the Lunar entry {position, x, y} put its element. */
  static boolean placeLike(AbstractHudModule module, JsonObject place) {
    String where = str(place, "position");
    if (where == null) return false;
    float lx = num(place, "x"), ly = num(place, "y");
    float w = module.width() * module.scale(), h = module.height() * module.scale();
    float[] r = convert(where, lx, ly, w, h);
    if (r == null) return false;
    module.anchor(Anchor.values()[(int) r[0]]);
    module.offset(r[1], r[2]);
    return true;
  }

  /**
   * {anchor ordinal, luka offset x, luka offset y} for a Lunar anchor name and offset, given luka's box size (GUI units).
   * Null for an anchor name this does not know.
   */
  static float[] convert(String where, float lx, float ly, float w, float h) {
    switch (where.toLowerCase(java.util.Locale.ROOT).replace("_", "")) {
      case "topleft": return new float[] {Anchor.TOP_LEFT.ordinal(), lx - 2, ly - 2};
      case "topcenter": return new float[] {Anchor.TOP_CENTER.ordinal(), lx, ly - 2};
      case "topright": return new float[] {Anchor.TOP_RIGHT.ordinal(), lx + 2, ly - 2};
      case "middleleft": return new float[] {Anchor.CENTER_LEFT.ordinal(), lx - 2, ly + h / 2};
      case "middle": case "center": case "middlecenter": return new float[] {Anchor.CENTER.ordinal(), lx, ly + h / 2};
      case "middleright": return new float[] {Anchor.CENTER_RIGHT.ordinal(), lx + 2, ly + h / 2};
      case "bottomleft": return new float[] {Anchor.BOTTOM_LEFT.ordinal(), lx - 2, ly + 2};
      case "bottomcenter": return new float[] {Anchor.BOTTOM_CENTER.ordinal(), lx, ly + 2};
      // left of the hotbar: Lunar measures the element's right edge from the screen centre
      case "bottomcenterleft": return new float[] {Anchor.BOTTOM_CENTER.ordinal(), lx - w / 2, ly + 2};
      // right of the hotbar: the element's left edge from the screen centre
      case "bottomcenterright": return new float[] {Anchor.BOTTOM_CENTER.ordinal(), lx + w / 2, ly + 2};
      case "bottomright": return new float[] {Anchor.BOTTOM_RIGHT.ordinal(), lx + 2, ly + 2};
      default: return null;
    }
  }

  /** A profile folder name from Lunar's own list: never a path. */
  private static boolean safeFolder(String name) {
    return !name.isEmpty() && !name.contains("/") && !name.contains("\\") && !name.contains("..") && name.length() <= 64;
  }

  private static JsonElement read(File file) throws Exception {
    if (!file.isFile() || file.length() > 8L * 1024 * 1024) return null;
    try (Reader r = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
      return new JsonParser().parse(r);
    }
  }

  private static String str(JsonObject o, String key) {
    JsonElement e = o.get(key);
    return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
  }

  private static float num(JsonObject o, String key) {
    JsonElement e = o.get(key);
    try {
      return e != null && e.isJsonPrimitive() ? e.getAsFloat() : 0f;
    } catch (NumberFormatException notNumber) {
      return 0f;
    }
  }
}
