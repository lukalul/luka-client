package dev.cbui.config;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

/**
 * 2026-11-22: four cards got plain names (Zoom, Time Changer, Hitboxes, Crosshair) and their keys were renamed with
 * them. Settings saved under the old names are moved to the new ones when they are read, so whether a card is on, its
 * HUD place, its settings, its hold key and its bound keys carry over. Minecraft-free, so the checks can run it.
 */
public final class CardRenames {
  /** {old card name, new card name}. */
  static final String[][] CARDS = {
    {"PolyZoom", "Zoom"},
    {"PolyTime", "Time Changer"},
    {"PolyHitbox", "Hitboxes"},
    {"PolyCrosshair", "Crosshair"}
  };

  /** {old key description, new key description}, as written in options.txt ("key_<description>:<code>"). */
  static final String[][] KEYS = {
    {"PolyZoom: magnify", "Zoom: magnify"},
    {"PolyZoom: cinematic zoom", "Zoom: cinematic zoom"},
    {"PolyZoom: step closer", "Zoom: step closer"},
    {"PolyZoom: step back", "Zoom: step back"},
    {"PolyTime - push clock ahead", "Time Changer - push clock ahead"},
    {"PolyTime - pull clock back", "Time Changer - pull clock back"},
    {"Flip hitboxes (PolyHitbox)", "Flip hitboxes"}
  };

  private CardRenames() {}

  /** Moves each old-named entry of an object keyed by card name (cbui.json "modules") to its new name. */
  public static void moveCards(JsonObject byCard) {
    if (byCard == null) return;
    for (String[] rename : CARDS)
      if (byCard.has(rename[0]) && !byCard.has(rename[1])) byCard.add(rename[1], byCard.remove(rename[0]));
  }

  /** The same for a plain map keyed by card name (hold keys). */
  public static <V> void moveCards(Map<String, V> byCard) {
    if (byCard == null) return;
    for (String[] rename : CARDS)
      if (byCard.containsKey(rename[0]) && !byCard.containsKey(rename[1])) byCard.put(rename[1], byCard.remove(rename[0]));
  }

  /** The description a key had before the rename, or null when it was not renamed. */
  public static String oldKey(String description) {
    for (String[] rename : KEYS) if (rename[1].equals(description)) return rename[0];
    return null;
  }

  /** The description a renamed key has now, or null when {@code description} is not an old one. */
  public static String newKey(String description) {
    for (String[] rename : KEYS) if (rename[0].equals(description)) return rename[1];
    return null;
  }

  /**
   * The key code saved in options.txt lines for {@code description}, falling back to the line of its old description;
   * null when neither is there (or the line is damaged).
   */
  public static Integer savedKeyCode(List<String> optionsLines, String description) {
    Integer code = keyCode(optionsLines, description);
    String old = code == null ? oldKey(description) : null;
    return old == null ? code : keyCode(optionsLines, old);
  }

  private static Integer keyCode(List<String> lines, String description) {
    String prefix = "key_" + description + ":";
    Integer found = null;
    for (String line : lines) {
      if (!line.startsWith(prefix)) continue;
      try {
        found = Integer.valueOf(line.substring(prefix.length()).trim());
      } catch (NumberFormatException ignored) {
        // a damaged line counts as missing
      }
    }
    return found;
  }
}
