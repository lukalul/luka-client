package dev.cbui.config;

import dev.cbui.hud.Setting;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

/** The VIDEO tab: every picture setting in one list, on both renderers. Rows that only exist on the Vulkan renderer are left out on the other. */
public final class VideoOptions {
  /** Row name, system property read by the sky, colour and texture code at its next resource reload. */
  private static final String[][] PACK_FEATURES = {
      {"Connected textures", "luka.connectedTextures", "Glass, bookshelves and other blocks join up where the resource pack supports it"},
      {"Custom sky", "luka.customSky", "Skies from the resource pack"},
      {"Custom colours", "luka.customColors", "Water, grass, foliage and lightmap colours from the resource pack"},
      {"Random mobs", "luka.randomMobs", "Alternate mob skins from the resource pack"},
      {"HD fonts", "luka.hdFonts", "High resolution font from the resource pack"},
  };
  private static final String[] DETAIL_KEYS = {"rain_snow", "sky_clouds", "sun_moon", "stars", "terrain_fog", "fluid_fog"};
  private static final String[] ANIMATION_KEYS = {"water_animation", "lava_animation", "fire_animation", "portal_animation",
      "redstone_dust", "void_particles", "item_block_break", "damage_numbers"};
  private static final boolean PACK_ROWS = FabricLoader.getInstance().isModLoaded("astatine");

  private VideoOptions() {}

  public static List<Setting<?>> build() {
    Minecraft mc = Minecraft.getMinecraft();
    GameSettings g = mc.gameSettings;
    List<Setting<?>> list = new ArrayList<>();
    add(list, "General", slider("Render distance", g.renderDistanceChunks, 2, 32, 1, "How many chunks you can see. The biggest setting for FPS. Servers can limit it"));
    add(list, "General", slider("Max FPS", g.limitFramerate, 10, 260, 10, "Frame rate cap. 260 means unlimited"));
    add(list, "General", toggle("VSync", g.enableVsync, false, "Matches the monitor refresh rate. Stops tearing but adds input delay"));
    add(list, "General", toggle("Fullscreen", mc.isFullScreen(), false, "Exclusive fullscreen. Borderless is in the General tab"));
    add(list, "General", choice("GUI scale", g.guiScale, new String[] {"Auto", "Small", "Normal", "Large"}, 0, "Size of menus and the hotbar"));
    add(list, "General", slider("Brightness", Math.round(g.gammaSetting * 100f), 0, 100, 5, "Vanilla gamma. The Fullbright mod goes further"));
    add(list, "General", slider("FOV", g.fovSetting, 30, 110, 1, "Field of view"));
    add(list, "General", toggle("View bobbing", g.viewBobbing, true, "The camera sways as you walk"));

    add(list, "Details", choice("Graphics", g.fancyGraphics ? 1 : 0, new String[] {"Fast", "Fancy"}, 1, "Fancy adds transparent leaves, entity shadows and the vignette"));
    add(list, "Details", choice("Smooth lighting", g.ambientOcclusion, new String[] {"Off", "Minimum", "Maximum"}, 2, "Soft shadows in block corners. Off is fastest"));
    add(list, "Details", choice("Clouds", g.clouds, new String[] {"Off", "Fast", "Fancy"}, 2, "Off is fastest"));
    add(list, "Details", choice("Leaves", GlobalOptions.leavesMode, new String[] {"Follow graphics", "Fast", "Fancy"}, 0, "Solid or see-through leaves, separate from Graphics. Fast is much quicker in forests"));
    add(list, "Details", toggle("Entity shadows", g.entityShadows, true, "The round shadow under players, mobs and items"));
    add(list, "Details", toggle("Vignette", GlobalOptions.vignette && ArgentumExtrasBridge.on("vignette"), true, "The dark edges of the screen on Fancy graphics"));
    add(list, "Details", toggle("Held item names", g.heldItemTooltips, true, "The item name above the hotbar when you switch"));
    add(list, "Details", toggle("Block alternatives", g.allowBlockAlternatives, true, "Random rotated block variants from resource packs"));
    for (String key : DETAIL_KEYS) add(list, "Details", animationRow(key));

    add(list, "Quality", toggle("FXAA", PACK_ROWS ? GlobalOptions.fxaa : ArgentumExtrasBridge.on("fxaa"), false, "Smooths jagged edges. Costs a little FPS and slightly softens text"));
    add(list, "Quality", slider("Mipmap levels", g.mipmapLevels, 0, 4, 1, "Smooths distant textures. 0 is sharpest and fastest"));
    if (PACK_ROWS)
      add(list, "Quality", toggle("Better grass", GlobalOptions.betterGrass, false, "The sides of grass blocks are fully green, like the top. Same speed"));
    if (PACK_ROWS)
      add(list, "Quality", choice("Anisotropic filtering", Integer.numberOfTrailingZeros(GlobalOptions.anisotropy), new String[] {"Off", "2x", "4x", "8x", "16x"}, 0, "Keeps ground textures sharp at shallow angles. Needs Mipmap levels above 0"));
    if (PACK_ROWS)
      for (String[] feature : PACK_FEATURES) add(list, "Quality", toggle(feature[0], !"false".equals(System.getProperty(feature[1])), true, feature[2]));

    add(list, "Animations", choice("Particles", g.particleSetting, new String[] {"All", "Decreased", "Minimal"}, 0, "How many particles are spawned"));
    for (String key : ANIMATION_KEYS) add(list, "Animations", animationRow(key));
    return list;
  }

  /** True when the row belongs to this tab's list of per-effect switches. Applies it if so. */
  static boolean applyAnimation(Setting<?> s) {
    String[] keys = AnimationOptions.OTHER_KEYS, names = AnimationOptions.OTHER_NAMES;
    for (int i = 0; i < names.length; i++) {
      if (names[i].equals(s.name())) {
        GlobalOptions.animationEnabled(keys[i], (Boolean) s.get());
        ArgentumExtrasBridge.set(keys[i], (Boolean) s.get());
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

  /** The level is read when the block textures are next built, which the caller schedules. */
  static void applyAnisotropy(int level) {
    GlobalOptions.anisotropy = level;
    System.setProperty("luka.anisotropy", Integer.toString(level));
  }

  static void applyFxaa(boolean on) {
    GlobalOptions.fxaa = on;
    System.setProperty("luka.fxaa", on ? "true" : "false");
    ArgentumExtrasBridge.set("fxaa", on);
  }

  static void applyVignette(boolean on) {
    GlobalOptions.vignette = on;
    ArgentumExtrasBridge.set("vignette", on);
  }

  static void saveRendererSettings() {
    ArgentumExtrasBridge.save();
  }

  /** Once the game is up: the sky, colour and texture code read these at resource load, which ran before the settings were known. */
  public static void startup() {
    System.setProperty("luka.fxaa", GlobalOptions.fxaa ? "true" : "false");
    boolean anyOff = GlobalOptions.anisotropy > 1;
    if (anyOff) System.setProperty("luka.anisotropy", Integer.toString(GlobalOptions.anisotropy));
    for (java.util.Map.Entry<String, Boolean> e : GlobalOptions.packFeatures.entrySet()) {
      System.setProperty(e.getKey(), e.getValue() ? "true" : "false");
      anyOff |= !e.getValue();
    }
    if (anyOff && PACK_ROWS) Minecraft.getMinecraft().scheduleResourcesRefresh();
  }

  private static Setting<?> animationRow(String key) {
    String[] keys = AnimationOptions.OTHER_KEYS, names = AnimationOptions.OTHER_NAMES;
    for (int i = 0; i < keys.length; i++) {
      if (keys[i].equals(key)) return toggle(names[i], GlobalOptions.animationEnabled(key) && ArgentumExtrasBridge.on(key), true, "Turn off to skip this effect");
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
