package dev.cbui.config;

import dev.cbui.Cbui;
import dev.cbui.hud.Setting;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Cera (the resource pack features of the Normal renderer: connected textures, custom sky and colours, ...) keeps its switches in
 * its own config. Video Settings' Quality page reads and writes them here. Reflection, because Cera is only there with Argentum.
 */
final class CeraBridge {
  private static final String[] MODES = {"Off", "Fast", "Fancy"};
  /** Row name, CeraConfig field, tooltip. The Quality page shows them in this order. Better grass and Connected textures are Off/Fast/Fancy. */
  private static final String[][] QUALITY = {
      {"Random mobs", "randomEntities", "Alternate mob skins from the resource pack"},
      {"Better grass", "betterGrass", "Grass block sides use the top texture. Fast: every side. Fancy: only sides that meet more grass"},
      {"HD fonts", "hdFonts", "High resolution font from the resource pack"},
      {"Custom colours", "customColors", "Water, grass, foliage and lightmap colours from the resource pack"},
      {"Connected textures", "connectedTextures", "Glass, bookshelves and other blocks join up where the resource pack supports it"},
      {"Natural textures", "naturalTextures", "Grass, sand and other blocks are turned and flipped at random, so large areas look less repetitive"},
      {"Custom sky", "customSky", "Skies from the resource pack"},
      {"Custom items", "customItems", "Item textures from the resource pack that depend on name, enchantment or damage"},
      {"Custom GUIs", "customGuis", "Chest, furnace and other screen textures from the resource pack"},
      {"Emissive textures", "emissiveTextures", "Parts of blocks and mobs from the resource pack that glow in the dark"},
      {"Animated textures", "animatedTextures", "Texture animations from the resource pack"},
  };
  private static final String DYNAMIC_LIGHTS = "Dynamic lights";

  private static Class<?> cera;
  private static boolean searched, dirty, resources, world;

  private CeraBridge() {}

  private static Object config() {
    if (!searched) {
      searched = true;
      try {
        cera = Class.forName("dev.rdh.cera.Cera");
      } catch (ClassNotFoundException absent) {
        cera = null;
      } catch (Throwable error) {
        Cbui.reportOnce("Cera settings", error);
      }
    }
    if (cera == null) return null;
    try {
      return cera.getField("CONFIG").get(null);
    } catch (Throwable error) {
      Cbui.reportOnce("Cera settings", error);
      return null;
    }
  }

  static boolean present() {
    return config() != null;
  }

  static Setting<?> dynamicLights() {
    return VideoOptions.choice(DYNAMIC_LIGHTS, mode("dynamicLights"), MODES, 0, "Held torches, glowstone, lava buckets and burning mobs light up the blocks around them. Fast updates less often");
  }

  static List<Setting<?>> qualityRows() {
    List<Setting<?>> rows = new ArrayList<>();
    for (String[] row : QUALITY) {
      if (isMode(row[1])) rows.add(VideoOptions.choice(row[0], mode(row[1]), MODES, 2, row[2]));
      else rows.add(VideoOptions.toggle(row[0], bool(row[1]), true, row[2]));
    }
    return rows;
  }

  private static boolean isMode(String field) {
    return field.equals("betterGrass") || field.equals("connectedTextures") || field.equals("dynamicLights");
  }

  private static String field(String row) {
    if (row.equals(DYNAMIC_LIGHTS)) return "dynamicLights";
    for (String[] q : QUALITY) if (q[0].equals(row)) return q[1];
    return null;
  }

  private static int mode(String field) {
    Object config = config();
    if (config == null) return 0;
    try {
      Object value = config.getClass().getField(field).get(config);
      return value instanceof Enum ? ((Enum<?>) value).ordinal() : 0;
    } catch (Throwable error) {
      Cbui.reportOnce("Cera settings", error);
      return 0;
    }
  }

  private static boolean bool(String field) {
    Object config = config();
    if (config == null) return true;
    try {
      return config.getClass().getField(field).getBoolean(config);
    } catch (Throwable error) {
      Cbui.reportOnce("Cera settings", error);
      return true;
    }
  }

  /** Writes a changed Quality or Dynamic lights row to Cera. False when the row is not Cera's or Cera is not installed. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  static boolean apply(Setting<?> s) {
    String name = field(s.name());
    if (name == null) return false;
    Object config = config();
    if (config == null) return false;
    try {
      Field f = config.getClass().getField(name);
      if (f.getType().isEnum()) {
        int index = s instanceof Setting.Choice ? ((Setting.Choice) s).choices().indexOf(s.get()) : 0;
        f.set(config, Enum.valueOf((Class) f.getType(), MODES[Math.max(0, index)].toUpperCase(java.util.Locale.ROOT)));
      } else {
        boolean on = (Boolean) s.get();
        f.setBoolean(config, on);
        // OptiFine's Custom Colors covers the lightmap too; Cera has a switch for each
        if (name.equals("customColors")) config.getClass().getField("customLightmaps").setBoolean(config, on);
      }
      dirty = true;
      if (name.equals("dynamicLights")) world = true;
      else resources = true;
      return true;
    } catch (Throwable error) {
      Cbui.reportOnce("Cera settings", error);
      return true;
    }
  }

  /** Saves Cera's file after changes. Returns 2 when the resources must be reloaded, 1 when only the world must be rebuilt, else 0. */
  static int save() {
    if (!dirty || config() == null) return 0;
    dirty = false;
    int result = resources ? 2 : world ? 1 : 0;
    resources = false;
    world = false;
    try {
      Object storage = cera.getField("CONFIG_STORAGE").get(null);
      if (storage != null) storage.getClass().getMethod("save").invoke(storage);
    } catch (Throwable error) {
      Cbui.reportOnce("Cera settings", error);
    }
    return result;
  }
}
