package dev.cbui.config;

import dev.cbui.Cbui;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Argentum Extras keeps its own switches for the sky, weather, fog and vignette. On the OpenGL renderer the video tab has to set those
 * too, or a row showing ON would still have nothing to draw. The switches it has that luka does not are Video Settings rows of their
 * own (ROWS); the rest are held neutral at every start (neutralize), so luka's own settings decide. Reflection, because the mod is optional and absent on the Vulkan renderer.
 */
final class ArgentumExtrasBridge {
  private static boolean searched, dirty;
  private static Object config;
  private static Object storage;

  private ArgentumExtrasBridge() {}

  private static boolean ready() {
    if (!searched) {
      searched = true;
      try {
        Class<?> extras = Class.forName("dev.rdh.argentum.extras.ArgentumExtras");
        config = extras.getField("CONFIG").get(null);
        Field saved = extras.getDeclaredField("CONFIG_STORAGE");
        saved.setAccessible(true);
        storage = saved.get(null);
      } catch (ClassNotFoundException onVulkan) {
        config = null;
      } catch (Throwable error) {
        config = null;
        Cbui.reportOnce("Argentum Extras settings", error);
      }
    }
    return config != null;
  }

  private static String field(String key) {
    switch (key) {
      case "sun_moon": return "sunAndMoon";
      case "stars": return "stars";
      case "terrain_fog": return "terrainFogDensity";
      case "fluid_fog": return "fluidFogDensity";
      case "rain_snow": return "weatherDensity";
      case "vignette": return "vignetteStrength";
      case "fxaa": return "fxaa";
      default: return null;
    }
  }

  /** False only when Argentum Extras has this effect switched off. */
  static boolean on(String key) {
    String name = field(key);
    if (name == null || !ready()) return true;
    try {
      Field f = config.getClass().getField(name);
      return f.getType() == boolean.class ? f.getBoolean(config) : f.getInt(config) > 0;
    } catch (Throwable error) {
      Cbui.reportOnce("Argentum Extras settings", error);
      return true;
    }
  }

  static void set(String key, boolean on) {
    String name = field(key);
    if (name == null || !ready()) return;
    try {
      Field f = config.getClass().getField(name);
      if (f.getType() == boolean.class) f.setBoolean(config, on);
      else f.setInt(config, on ? 100 : 0);
      dirty = true;
    } catch (Throwable error) {
      Cbui.reportOnce("Argentum Extras settings", error);
    }
  }

  static void save() {
    if (!dirty || !ready() || storage == null) return;
    dirty = false;
    try {
      Method write = storage.getClass().getMethod("save");
      write.invoke(storage);
    } catch (Throwable error) {
      Cbui.reportOnce("Argentum Extras settings", error);
    }
  }

  /**
   * Switches only Argentum Extras has (1.8.9 Normal), shown as Video Settings rows: row name, field, "bool" or slider
   * "min,max,step", tooltip. Everything else it has is either already a luka setting or held neutral by {@link #neutralize}.
   */
  private static final String[][] ROWS = {
      {"Sky", "sky", "bool", "The sky dome and the sunrise colours. Off leaves only the fog colour"},
      {"Cloud distance", "cloudRenderDistance", "0,1536,96", "How far the clouds reach from you, in blocks. Vanilla follows the render distance"},
      {"Cloud fog", "cloudFog", "bool", "Distance fog also fades out the far clouds"},
      {"Weather distance", "weatherRenderDistance", "0,15,1", "How far away rain and snow are drawn, in blocks. Vanilla is 5 on Fast and 10 on Fancy graphics"},
      {"Shadow distance", "entityShadowDistance", "8,128,8", "How far away the round entity shadows are drawn, in blocks"},
      {"Leashes", "leashes", "bool", "The lead between a leashed mob and whatever holds it"},
      {"Portal distortion", "portalDistortionStrength", "0,100,5", "How strongly the screen warps inside a nether portal or with nausea. 0 turns the warp off"},
      {"Steady F3", "steadyDebugHud", "bool", "The F3 screen refreshes its costly lines less often; its FPS number stays live"},
      {"F3 refresh", "debugHudRefreshIntervalMs", "0,1000,50", "How often Steady F3 refreshes its lines, in milliseconds. 0 is every frame"},
      {"F3 scale", "debugHudScale", "0,4,1", "Size of the F3 screen by itself. 0 follows GUI scale"},
  };

  private static String[] row(String name) {
    for (String[] r : ROWS) if (r[0].equals(name)) return r;
    return null;
  }

  /** The Video Settings row for one Argentum Extras switch, or null when Argentum Extras is not installed. */
  static dev.cbui.hud.Setting<?> settingRow(String name) {
    String[] r = row(name);
    if (r == null || !ready()) return null;
    try {
      Field f = config.getClass().getField(r[1]);
      if (r[2].equals("bool")) return VideoOptions.toggle(r[0], f.getBoolean(config), true, r[3]);
      String[] range = r[2].split(",");
      return VideoOptions.slider(r[0], f.getInt(config), Float.parseFloat(range[0]), Float.parseFloat(range[1]), Float.parseFloat(range[2]), r[3]);
    } catch (Throwable error) {
      Cbui.reportOnce("Argentum Extras settings", error);
      return null;
    }
  }

  /** Writes a changed Argentum Extras row. False when the row is not one of them. */
  static boolean applyRow(dev.cbui.hud.Setting<?> s) {
    String[] r = row(s.name());
    if (r == null || !ready()) return false;
    try {
      Field f = config.getClass().getField(r[1]);
      if (r[2].equals("bool")) f.setBoolean(config, (Boolean) s.get());
      else f.setInt(config, Math.round((Float) s.get()));
      dirty = true;
    } catch (Throwable error) {
      Cbui.reportOnce("Argentum Extras settings", error);
    }
    return true;
  }

  /**
   * Held on: the particle groups (luka's per-type switches decide); armour, held items, worn heads, capes, ears (Entity detail
   * distance, luka capes); armour glint (Glint distance, No Glint); stuck arrows (Hide stuck arrows); name tags (Nametags card,
   * Nametag distance); lower sky (Smooth Skies).
   */
  private static final String[] ALWAYS_ON = {"explosionParticles", "spellParticles", "portalParticles", "smokeAndFlameParticles",
      "redstoneParticles", "waterParticles", "miscellaneousParticles",
      "armor", "heldItems", "wornHeads", "capes", "playerEars", "armorGlint", "stuckArrows", "nameTags", "secondNameTagLayer", "lowerSky"};
  /** Held off: FPS HUD (luka's FPS module), text shadows off (luka's Text shadow), smooth / scaled outlines (Block Overlay card). */
  private static final String[] ALWAYS_OFF = {"fpsHud", "disableTextShadows", "smoothBlockOutlines", "scaledBlockOutlineWidth"};
  /** Held at 100 %: dynamic FOV (FOV Changer), view bobbing (View bobbing, Shake Tweaks), hurt camera (Better Hurt Cam, Shake Tweaks). */
  private static final String[] FULL_STRENGTH = {"dynamicFovStrength", "viewBobbingStrength", "hurtCameraStrength"};
  /** luka switch, Argentum Extras field: kept in step, so the effect shows exactly when luka's row is ON. */
  private static final String[][] MIRRORED = {{"sun_moon", "sunAndMoon"}, {"stars", "stars"}, {"rain_snow", "weatherDensity"},
      {"terrain_fog", "terrainFogDensity"}, {"fluid_fog", "fluidFogDensity"}, {"vignette", "vignetteStrength"}};

  /**
   * At every start: each Argentum Extras switch that luka already has a setting for is put where it changes nothing, so luka's
   * setting alone decides (Argentum's screen, where they could be changed, can no longer be reached). What the player saw stays:
   * a cloud height offset is added to luka's Cloud height, a cloud speed of 0 turns Sky / clouds drift off, and an effect that was
   * off on either side stays off. Saves Argentum Extras' file if anything moved; true when a luka setting changed.
   */
  static boolean neutralize() {
    if (!ready()) return false;
    boolean lukaChanged = false;
    Class<?> c = config.getClass();
    try {
      for (String name : ALWAYS_ON) dirty |= setBool(c.getField(name), true);
      for (String name : ALWAYS_OFF) dirty |= setBool(c.getField(name), false);
      for (String name : FULL_STRENGTH) dirty |= setInt(c.getField(name), 100);
      Field width = c.getField("blockOutlineWidth");
      if (width.getFloat(config) != 2.0F) {
        width.setFloat(config, 2.0F);
        dirty = true;
      }
      // leaf quality: Fast forces fast leaves and Solid opaque ones over luka's Leaves row; Enclosed (what luka ships) and Fancy
      // keep luka's Fast/Fancy choice
      Field leaves = c.getField("leafQuality");
      Object quality = leaves.get(config);
      String q = quality == null ? "" : ((Enum<?>) quality).name();
      if (!q.equals("ENCLOSED") && !q.equals("FANCY")) {
        leaves.set(config, enumValue(leaves.getType(), "ENCLOSED"));
        dirty = true;
      }
      Field offset = c.getField("cloudHeightOffset");
      int shift = offset.getInt(config);
      if (shift != 0) {
        GlobalOptions.cloudHeight = Math.max(64, Math.min(256, GlobalOptions.cloudHeight + shift));
        offset.setInt(config, 0);
        dirty = true;
        lukaChanged = true;
      }
      Field speed = c.getField("cloudSpeed");
      if (speed.getInt(config) != 100) {
        if (speed.getInt(config) == 0 && GlobalOptions.animationEnabled("sky_clouds")) {
          GlobalOptions.animationEnabled("sky_clouds", false);
          lukaChanged = true;
        }
        speed.setInt(config, 100);
        dirty = true;
      }
      for (String[] pair : MIRRORED) {
        Field f = c.getField(pair[1]);
        boolean flag = f.getType() == boolean.class;
        boolean extrasOn = flag ? f.getBoolean(config) : f.getInt(config) > 0;
        boolean vignette = pair[0].equals("vignette");
        boolean lukaOn = vignette ? GlobalOptions.vignette : GlobalOptions.animationEnabled(pair[0]);
        boolean on = lukaOn && extrasOn;
        if (on != lukaOn) {
          if (vignette) GlobalOptions.vignette = on;
          else GlobalOptions.animationEnabled(pair[0], on);
          lukaChanged = true;
        }
        dirty |= flag ? setBool(f, on) : setInt(f, on ? 100 : 0);
      }
    } catch (Throwable error) {
      Cbui.reportOnce("Argentum Extras settings", error);
    }
    save();
    return lukaChanged;
  }

  private static boolean setBool(Field f, boolean value) throws IllegalAccessException {
    if (f.getBoolean(config) == value) return false;
    f.setBoolean(config, value);
    return true;
  }

  private static boolean setInt(Field f, int value) throws IllegalAccessException {
    if (f.getInt(config) == value) return false;
    f.setInt(config, value);
    return true;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Object enumValue(Class<?> type, String name) {
    return Enum.valueOf((Class) type, name);
  }
}
