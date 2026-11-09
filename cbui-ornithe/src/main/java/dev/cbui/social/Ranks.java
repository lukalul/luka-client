package dev.cbui.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** The roles a luka player can have, with the colour their icon, name and cape use. The server sends the table; this is the fallback. */
public final class Ranks {
  /** One role. */
  public static final class Rank {
    public final String id, label;
    /** 0xRRGGBB. */
    public final int color;
    public final int priority;

    Rank(String id, String label, int color, int priority) {
      this.id = id;
      this.label = label;
      this.color = color;
      this.priority = priority;
    }
  }

  private static volatile Map<String, Rank> table = defaults();

  private Ranks() {}

  private static Map<String, Rank> defaults() {
    Map<String, Rank> m = new LinkedHashMap<String, Rank>();
    put(m, "owner", "Owner", 0xFFB000, 100);
    put(m, "admin", "Admin", 0xFF4D4D, 90);
    put(m, "developer", "Developer", 0x3DD6F5, 80);
    put(m, "moderator", "Moderator", 0x4DDB6A, 70);
    put(m, "contributor", "Contributor", 0xB26FFF, 60);
    put(m, "creator", "Creator", 0xFF3D8B, 50);
    put(m, "partner", "Partner", 0xFF8A3D, 45);
    put(m, "supporter", "Supporter", 0xF5A6E8, 30);
    put(m, "beta", "Beta tester", 0x8FA8FF, 20);
    put(m, "member", "Member", 0xC9B8F0, 0);
    return m;
  }

  private static void put(Map<String, Rank> m, String id, String label, int color, int priority) {
    m.put(id, new Rank(id, label, color, priority));
  }

  /** The rank with this id; an unknown id counts as a plain member. */
  public static Rank get(String id) {
    Rank r = id == null ? null : table.get(id);
    return r != null ? r : table.get("member");
  }

  /** Replaces the table with the server's (an empty or broken list keeps the current one). */
  static void update(JsonArray array) {
    try {
      Map<String, Rank> m = new LinkedHashMap<String, Rank>();
      for (JsonElement e : array) {
        JsonObject o = e.getAsJsonObject();
        String color = o.get("color").getAsString().replace("#", "");
        put(m, o.get("id").getAsString(), o.get("label").getAsString(), Integer.parseInt(color, 16) & 0xFFFFFF, o.get("priority").getAsInt());
      }
      if (m.containsKey("member")) table = m;
    } catch (RuntimeException ignored) {
      // keep the table we have
    }
  }
}
