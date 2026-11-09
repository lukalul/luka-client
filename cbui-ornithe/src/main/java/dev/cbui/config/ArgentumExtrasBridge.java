package dev.cbui.config;

import dev.cbui.Cbui;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Argentum Extras keeps its own switches for the sky, weather, fog and vignette. On the OpenGL renderer the video tab has to set those
 * too, or a row showing ON would still have nothing to draw. Reflection, because the mod is optional and absent on the Vulkan renderer.
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
      case "redstone_dust": return "redstoneParticles";
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
}
