package dev.cbui.config;

import dev.cbui.hud.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

/** The VIDEO tab: every picture setting in one list. Rows that only exist on the Vulkan renderer are left out on the other. */
public final class VideoOptions {
  /** Row name, system property read by the texture code at its next resource reload. */
  private static final String[][] PACK_FEATURES = {
      {"Connected textures", "luka.connectedTextures", "Glass, bookshelves and other blocks join up where the resource pack supports it"},
  };
  private static final boolean PACK_ROWS = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("astatine");
  private static final String[] DETAIL_KEYS = {"rain_snow", "sun_moon", "stars"};
  private static final String[] ANIMATION_KEYS = {"water_animation", "lava_animation", "fire_animation", "portal_animation",
      "redstone_dust", "void_particles", "item_block_break", "damage_numbers"};

  private VideoOptions() {}

  public static List<Setting<?>> build() {
    Minecraft mc = Minecraft.getMinecraft();
    GameSettings g = mc.gameSettings;
    List<Setting<?>> list = new ArrayList<>();
    add(list, "General", slider("Render distance", g.renderDistanceChunks, 2, 32, 1, "How many chunks you can see. The biggest setting for FPS. Servers can limit it"));
    add(list, "General", slider("Max FPS", g.limitFramerate, 10, 260, 10, "Frame rate cap. 260 means unlimited"));
    add(list, "General", toggle("VSync", g.enableVsync, false, "Matches the monitor refresh rate. Stops tearing but adds input delay"));
    add(list, "General", toggle("Fullscreen", mc.isFullScreen(), false, "Exclusive fullscreen. Borderless is in the General tab"));
    if (Resolution.supported()) {
      String[] sizes = Resolution.choices();
      add(list, "General", choice("Resolution", Resolution.index(sizes), sizes, 0, "Fullscreen: the monitor switches to this resolution. Borderless: the game is drawn at this resolution and stretched over the screen, so it looks the same. Native is your monitor's own"));
    }
    add(list, "General", choice("GUI scale", g.guiScale, new String[] {"Auto", "Small", "Normal", "Large"}, 0, "Size of menus and the hotbar"));
    add(list, "General", slider("Brightness", Math.round(g.gammaSetting * 100f), 0, 100, 5, "Vanilla gamma. The Fullbright mod goes further"));
    add(list, "General", slider("FOV", g.fovSetting, 30, 110, 1, "Field of view"));
    add(list, "General", toggle("View bobbing", g.viewBobbing, true, "The camera sways as you walk"));

    add(list, "Details", choice("Graphics", g.fancyGraphics ? 1 : 0, new String[] {"Fast", "Fancy"}, 1, "Fancy adds transparent leaves, entity shadows and the vignette"));
    add(list, "Details", choice("Smooth lighting", g.ambientOcclusion, new String[] {"Off", "Minimum", "Maximum"}, 2, "Soft shadows in block corners. Off is fastest"));
    add(list, "Details", choice("Leaves", GlobalOptions.leavesMode, new String[] {"Follow graphics", "Fast", "Fancy"}, 0, "Solid or see-through leaves, separate from Graphics. Fast is much quicker in forests"));
    add(list, "Details", toggle("Vignette", GlobalOptions.vignette, true, "The dark edges of the screen on Fancy graphics"));
    add(list, "Details", toggle("Held item names", g.heldItemTooltips, true, "The item name above the hotbar when you switch"));
    for (String key : DETAIL_KEYS) add(list, "Details", animationRow(key));

    add(list, "Fog", animationRow("terrain_fog"));
    add(list, "Fog", slider("Fog start", GlobalOptions.fogStart, 0, 100, 5, "How far away distance fog begins, as a percent of your render distance. Vanilla is 75"));
    add(list, "Fog", slider("Fog end", GlobalOptions.fogEnd, 25, 200, 5, "Where distance fog becomes solid. Higher pushes it past your render distance. Vanilla is 100"));
    add(list, "Fog", animationRow("fluid_fog"));

    // 1.7.10 has one clouds switch; fast or fancy clouds follow Graphics
    add(list, "Clouds", choice("Clouds", g.clouds ? 1 : 0, new String[] {"Off", "On"}, 1, "Off is fastest. Their shape follows Graphics"));
    add(list, "Clouds", slider("Cloud height", GlobalOptions.cloudHeight, 64, 256, 4, "How high the clouds sit. Vanilla is 128"));
    add(list, "Clouds", animationRow("sky_clouds"));

    add(list, "Quality", slider("Mipmap levels", g.mipmapLevels, 0, 4, 1, "Smooths distant textures. 0 is sharpest and fastest"));
    add(list, "Quality", choice("Clear glass", GlobalOptions.clearGlass, new String[] {"Off", "Regular", "All"}, 0, "Glass is not drawn, frame included, so you see straight through it. Regular: normal glass and panes. All: stained glass too"));
    if (PACK_ROWS)
      for (String[] feature : PACK_FEATURES) add(list, "Quality", toggle(feature[0], !"false".equals(System.getProperty(feature[1])), true, feature[2]));

    add(list, "Particle types", choice("Particles", g.particleSetting, new String[] {"All", "Decreased", "Minimal"}, 0, "How many particles are spawned"));
    for (String key : ANIMATION_KEYS) add(list, "Animations", animationRow(key));
    for (String[] particle : AnimationOptions.PARTICLES)
      add(list, "Particle types", toggle(particle[1], GlobalOptions.animationEnabled(AnimationOptions.particleKey(particle[0])), true, "Turn off to stop this particle from appearing"));
    return list;
  }

  /** True when the row belongs to this tab's list of per-effect switches. Applies it if so. */
  static boolean applyAnimation(Setting<?> s) {
    String[] keys = AnimationOptions.OTHER_KEYS, names = AnimationOptions.OTHER_NAMES;
    for (int i = 0; i < names.length; i++) {
      if (names[i].equals(s.name())) {
        GlobalOptions.animationEnabled(keys[i], (Boolean) s.get());
        return true;
      }
    }
    return false;
  }

  /** True when the row switches one particle type. Applies it if so. */
  static boolean applyParticle(Setting<?> s) {
    if (!(s instanceof Setting.Toggle) || !"Particle types".equals(s.group())) return false;
    for (String[] particle : AnimationOptions.PARTICLES) {
      if (particle[1].equals(s.name())) {
        GlobalOptions.animationEnabled(AnimationOptions.particleKey(particle[0]), (Boolean) s.get());
        return true;
      }
    }
    return false;
  }

  /** True when the row is a resource pack feature. It takes effect at the next resource reload, which the caller schedules. */
  static boolean applyPackFeature(Setting<?> s) {
    for (String[] feature : PACK_FEATURES) {
      if (feature[0].equals(s.name())) {
        boolean on = (Boolean) s.get();
        System.setProperty(feature[1], on ? "true" : "false");
        GlobalOptions.packFeatures.put(feature[1], on);
        return true;
      }
    }
    return false;
  }

  static void applyVignette(boolean on) {
    GlobalOptions.vignette = on;
  }

  /** Once the game is up: the texture code reads these at resource load, which ran before the settings were known. */
  public static void startup() {
    Migrations.run();
    boolean anyOff = false;
    for (java.util.Map.Entry<String, Boolean> e : GlobalOptions.packFeatures.entrySet()) {
      System.setProperty(e.getKey(), e.getValue() ? "true" : "false");
      anyOff |= !e.getValue();
    }
    if (anyOff && PACK_ROWS) Minecraft.getMinecraft().scheduleResourcesRefresh();
  }

  private static Setting<?> animationRow(String key) {
    String[] keys = AnimationOptions.OTHER_KEYS, names = AnimationOptions.OTHER_NAMES;
    for (int i = 0; i < keys.length; i++) {
      if (keys[i].equals(key)) return toggle(names[i], GlobalOptions.animationEnabled(key), true, "Turn off to skip this effect");
    }
    throw new IllegalArgumentException(key);
  }

  private static void add(List<Setting<?>> list, String group, Setting<?> s) {
    s.setGroup(group);
    list.add(s);
  }

  private static Setting.Slider slider(String name, float v, float min, float max, float step, String tip) {
    return new Setting.Slider(name, v, min, max, step, tip);
  }

  private static Setting.Toggle toggle(String name, boolean v, boolean def, String tip) {
    Setting.Toggle t = new Setting.Toggle(name, def, tip);
    t.set(v);
    return t;
  }

  private static Setting.Choice choice(String name, int index, String[] names, int def, String tip) {
    Setting.Choice c = Setting.tip(new Setting.Choice(name, names[def], names), tip);
    c.set(names[Math.max(0, Math.min(names.length - 1, index))]);
    return c;
  }
}
