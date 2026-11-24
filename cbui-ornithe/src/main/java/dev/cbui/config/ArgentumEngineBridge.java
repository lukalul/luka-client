package dev.cbui.config;

import dev.cbui.Cbui;
import dev.cbui.hud.Setting;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * A few of Argentum's own engine options (Normal renderer, 1.8.9), shown on Video Settings' Performance page now that Argentum's
 * screen is no longer reachable. Reflection on {@code dev.rdh.argentum.impl.Argentum.CONFIG}, saved through its CONFIG_STORAGE.
 */
final class ArgentumEngineBridge {
  /**
   * Row name, ArgentumConfig field, "reload" when Argentum marks it as needing a renderer reload, tooltip. Sliders: min, max,
   * step follow (the thread maximum is the processor count).
   */
  private static final String[][] ROWS = {
      {"Chunk builders", "chunkBuilderThreads", "reload", "Threads that build chunk meshes. Auto picks a number from your processor. More loads chunks faster but competes with the game for the CPU"},
      {"Defer chunk updates", "deferChunkUpdates", "", "Block changes near you are rebuilt on a worker thread instead of before the next frame. Smoother, but a placed or broken block can show a frame late"},
      {"Block face culling", "blockFaceCulling", "reload", "Block faces that point away from the camera are not drawn. Saves GPU time"},
      {"Particle culling", "particleCulling", "", "Particles outside the view are not drawn"},
      {"Smart animations", "animateOnlyVisibleTextures", "", "Only animates textures that are on screen (water, lava, fire, portals). Saves time with many animated blocks around"},
      {"Translucency sorting", "translucencySorting", "reload", "Sorts water and stained glass so overlapping see-through faces draw in the right order. Off is faster but can look wrong"},
      {"Chunk fade-in", "chunkFadeInDuration", "reload", "New chunks fade in over this time instead of popping in. Off shows them at once"},
  };

  private static Class<?> argentum;
  private static boolean searched, dirty, reload;

  private ArgentumEngineBridge() {}

  private static Object config() {
    if (!searched) {
      searched = true;
      try {
        argentum = Class.forName("dev.rdh.argentum.impl.Argentum");
      } catch (ClassNotFoundException onVulkan) {
        argentum = null;
      } catch (Throwable error) {
        Cbui.reportOnce("Argentum settings", error);
      }
    }
    if (argentum == null) return null;
    try {
      return argentum.getField("CONFIG").get(null);
    } catch (Throwable error) {
      Cbui.reportOnce("Argentum settings", error);
      return null;
    }
  }

  /** The Performance page rows, empty when Argentum is not installed. */
  static List<Setting<?>> rows() {
    List<Setting<?>> rows = new ArrayList<>();
    Object config = config();
    if (config == null) return rows;
    for (String[] row : ROWS) {
      try {
        Field f = config.getClass().getField(row[1]);
        if (f.getType() == boolean.class) {
          rows.add(VideoOptions.toggle(row[0], f.getBoolean(config), true, row[3]));
        } else if (row[1].equals("chunkBuilderThreads")) {
          int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
          rows.add(VideoOptions.slider(row[0], f.getInt(config), 0, cores, 1, row[3]));
        } else {
          rows.add(VideoOptions.slider(row[0], f.getInt(config), 0, 2000, 100, row[3]));
        }
      } catch (Throwable error) {
        Cbui.reportOnce("Argentum settings", error);
      }
    }
    return rows;
  }

  /** 0 when the row is not Argentum's, 1 when applied, 2 when applied and Argentum wants the world rebuilt. */
  static int apply(Setting<?> s) {
    String[] row = null;
    for (String[] r : ROWS) if (r[0].equals(s.name()) && "Engine".equals(s.group())) row = r;
    if (row == null) return 0;
    Object config = config();
    if (config == null) return 0;
    try {
      Field f = config.getClass().getField(row[1]);
      if (f.getType() == boolean.class) f.setBoolean(config, (Boolean) s.get());
      else f.setInt(config, Math.round((Float) s.get()));
      dirty = true;
      if (row[2].equals("reload")) reload = true;
    } catch (Throwable error) {
      Cbui.reportOnce("Argentum settings", error);
    }
    return row[2].equals("reload") ? 2 : 1;
  }

  /** Writes Argentum's file after changes. Returns 1 when a change needs the world rebuilt, else 0. */
  static int save() {
    if (!dirty || config() == null) return 0;
    dirty = false;
    int result = reload ? 1 : 0;
    reload = false;
    try {
      Object storage = argentum.getField("CONFIG_STORAGE").get(null);
      if (storage != null) storage.getClass().getMethod("save").invoke(storage);
    } catch (Throwable error) {
      Cbui.reportOnce("Argentum settings", error);
    }
    return result;
  }
}
