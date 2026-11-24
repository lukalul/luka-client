package dev.cbui.config;

import dev.cbui.hud.Setting;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.EnumParticleTypes;

/**
 * Every picture setting of Video Settings, on both renderers. The group of a row is the page it is shown on (Main, Quality,
 * Details, Engine on the Performance page, Animations, Particle types, Other). Rows a renderer does not have are left out.
 */
public final class VideoOptions {
  /** Row name, system property read by the sky, colour and texture code at its next resource reload (Vulkan renderer). */
  private static final String[][] PACK_FEATURES = {
      {"Random mobs", "luka.randomMobs", "Alternate mob skins from the resource pack"},
      {"HD fonts", "luka.hdFonts", "High resolution font from the resource pack"},
      {"Custom colours", "luka.customColors", "Water, grass, foliage and lightmap colours from the resource pack"},
      {"Connected textures", "luka.connectedTextures", "Glass, bookshelves and other blocks join up where the resource pack supports it"},
      {"Custom sky", "luka.customSky", "Skies from the resource pack"},
  };
  private static final String[] ANIMATION_KEYS = {"water_animation", "lava_animation", "fire_animation", "portal_animation",
      "redstone_dust", "void_particles", "item_block_break", "damage_numbers"};
  private static final boolean PACK_ROWS = FabricLoader.getInstance().isModLoaded("astatine");

  private VideoOptions() {}

  public static List<Setting<?>> build() {
    Minecraft mc = Minecraft.getMinecraft();
    GameSettings g = mc.gameSettings;
    List<Setting<?>> list = new ArrayList<>();
    add(list, "Main", choice("Graphics", g.fancyGraphics ? 1 : 0, new String[] {"Fast", "Fancy"}, 1, "Fancy adds transparent leaves, entity shadows and the vignette"));
    add(list, "Main", slider("Render distance", g.renderDistanceChunks, 2, 32, 1, "How many chunks you can see. The biggest setting for FPS. Servers can limit it"));
    add(list, "Main", choice("Smooth lighting", g.ambientOcclusion, new String[] {"Off", "Minimum", "Maximum"}, 2, "Soft shadows in block corners. Off is fastest"));
    add(list, "Main", slider("Max FPS", g.limitFramerate, 10, 260, 10, "Frame rate cap. 260 means unlimited"));
    add(list, "Main", toggle("VSync", g.enableVsync, false, "Matches the monitor refresh rate. Stops tearing but adds input delay"));
    add(list, "Main", toggle("View bobbing", g.viewBobbing, true, "The camera sways as you walk"));
    add(list, "Main", choice("GUI scale", g.guiScale, new String[] {"Auto", "Small", "Normal", "Large"}, 0, "Size of menus and the hotbar"));
    add(list, "Main", slider("Brightness", Math.round(g.gammaSetting * 100f), 0, 100, 5, "Vanilla gamma. The Fullbright mod goes further"));
    add(list, "Main", toggle("Block alternatives", g.allowBlockAlternatives, true, "Random rotated block variants from resource packs"));
    if (CeraBridge.present()) add(list, "Main", CeraBridge.dynamicLights());

    add(list, "Quality", slider("Mipmap levels", g.mipmapLevels, 0, 4, 1, "Smooths distant textures. 0 is sharpest and fastest"));
    if (PACK_ROWS)
      add(list, "Quality", choice("Anisotropic filtering", Integer.numberOfTrailingZeros(GlobalOptions.anisotropy), new String[] {"Off", "2x", "4x", "8x", "16x"}, 0, "Keeps ground textures sharp at shallow angles. Needs Mipmap levels above 0"));
    add(list, "Quality", toggle("FXAA", PACK_ROWS ? GlobalOptions.fxaa : ArgentumExtrasBridge.on("fxaa"), false, "Antialiasing: smooths jagged edges. Costs a little FPS and slightly softens text"));
    add(list, "Quality", choice("Clear glass", GlobalOptions.clearGlass, new String[] {"Off", "Regular", "All"}, 0, "Glass is not drawn, frame included, so you see straight through it. Regular: normal glass and panes. All: stained glass too"));
    if (PACK_ROWS) {
      add(list, "Quality", toggle("Better grass", GlobalOptions.betterGrass, false, "The sides of grass blocks are fully green, like the top. Same speed"));
      for (String[] feature : PACK_FEATURES) add(list, "Quality", toggle(feature[0], !"false".equals(System.getProperty(feature[1])), true, feature[2]));
    } else if (CeraBridge.present()) {
      for (Setting<?> row : CeraBridge.qualityRows()) add(list, "Quality", row);
    }

    add(list, "Details", choice("Clouds", g.clouds, new String[] {"Off", "Fast", "Fancy"}, 2, "Off is fastest"));
    add(list, "Details", slider("Cloud height", GlobalOptions.cloudHeight, 64, 256, 4, "How high the clouds sit. Vanilla is 128"));
    extras(list, "Cloud distance");
    extras(list, "Cloud fog");
    add(list, "Details", choice("Leaves", GlobalOptions.leavesMode, new String[] {"Follow graphics", "Fast", "Fancy"}, 0, "Solid or see-through leaves, separate from Graphics. Fast is much quicker in forests"));
    add(list, "Details", animationRow("rain_snow"));
    extras(list, "Weather distance");
    extras(list, "Sky");
    add(list, "Details", animationRow("sky_clouds"));
    add(list, "Details", animationRow("stars"));
    add(list, "Details", animationRow("sun_moon"));
    add(list, "Details", animationRow("terrain_fog"));
    add(list, "Details", slider("Fog start", GlobalOptions.fogStart, 0, 100, 5, "How far away distance fog begins, as a percent of your render distance. Vanilla is 75"));
    add(list, "Details", slider("Fog end", GlobalOptions.fogEnd, 25, 200, 5, "Where distance fog becomes solid. Higher pushes it past your render distance. Vanilla is 100"));
    add(list, "Details", animationRow("fluid_fog"));
    add(list, "Details", toggle("Held item names", g.heldItemTooltips, true, "The item name above the hotbar when you switch"));
    add(list, "Details", toggle("Entity shadows", g.entityShadows, true, "The round shadow under players, mobs and items. Off saves a little in crowds"));
    extras(list, "Shadow distance");
    add(list, "Details", toggle("Vignette", GlobalOptions.vignette && ArgentumExtrasBridge.on("vignette"), true, "The dark edges of the screen on Fancy graphics"));
    extras(list, "Leashes");
    extras(list, "Portal distortion");

    for (Setting<?> row : ArgentumEngineBridge.rows()) add(list, "Engine", row);

    for (String key : ANIMATION_KEYS) add(list, "Animations", animationRow(key));
    for (EnumParticleTypes type : EnumParticleTypes.values())
      add(list, "Particle types", toggle(AnimationOptions.readable(type), GlobalOptions.animationEnabled(AnimationOptions.particleKey(type)), true, "Turn off to stop this particle from appearing"));

    add(list, "Other", toggle("Fullscreen", mc.isFullScreen(), false, "Exclusive fullscreen. Borderless is in the General tab of the luka menu"));
    if (Resolution.supported()) {
      String[] sizes = Resolution.choices();
      add(list, "Other", choice("Resolution", Resolution.index(sizes), sizes, 0, "Fullscreen: the monitor switches to this resolution. Borderless: the game is drawn at this resolution and stretched over the screen, so it looks the same. Native is your monitor's own"));
    }
    add(list, "Other", toggle("Advanced tooltips", g.advancedItemTooltips, false, "Item tooltips also show the item id and durability (F3+H)"));
    extras(list, "Steady F3");
    extras(list, "F3 refresh");
    extras(list, "F3 scale");
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

  /** True when the row switches one particle type. Applies it if so. */
  static boolean applyParticle(Setting<?> s) {
    if (!(s instanceof Setting.Toggle) || !"Particle types".equals(s.group())) return false;
    for (EnumParticleTypes type : EnumParticleTypes.values()) {
      if (AnimationOptions.readable(type).equals(s.name())) {
        GlobalOptions.animationEnabled(AnimationOptions.particleKey(type), (Boolean) s.get());
        return true;
      }
    }
    return false;
  }

  /** True when the row is a resource pack feature. It takes effect at the next resource reload, which the caller schedules. */
  static boolean applyPackFeature(Setting<?> s) {
    if (!PACK_ROWS) return false;
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

  /** Cera's rows (Normal renderer): true when the row was one of them. */
  static boolean applyCera(Setting<?> s) {
    return CeraBridge.apply(s);
  }

  /** Argentum's engine rows (Normal renderer): 0 when the row is not one of them, 1 when applied, 2 when applied and the world must be rebuilt. */
  static int applyEngine(Setting<?> s) {
    return ArgentumEngineBridge.apply(s);
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

  /** Saves the renderer mods' own settings. Returns 1 when the world must be rebuilt, 2 when the resources must be reloaded now. */
  static int saveRendererSettings() {
    ArgentumExtrasBridge.save();
    int engine = ArgentumEngineBridge.save();
    int cera = CeraBridge.save();
    return Math.max(engine, cera);
  }

  /** Once the game is up: the sky, colour and texture code read these at resource load, which ran before the settings were known. */
  public static void startup() {
    OptimalPreset.firstLaunch();
    holdParticleLevel();
    Migrations.run();
    // Argentum's screen is gone: Argentum Extras switches that luka also has are held neutral so luka's settings decide alone
    if (ArgentumExtrasBridge.neutralize() && dev.cbui.Cbui.config() != null && dev.cbui.Cbui.hud() != null)
      dev.cbui.Cbui.config().save(dev.cbui.Cbui.hud());
    System.setProperty("luka.fxaa", GlobalOptions.fxaa ? "true" : "false");
    boolean anyOff = GlobalOptions.anisotropy > 1;
    if (anyOff) System.setProperty("luka.anisotropy", Integer.toString(GlobalOptions.anisotropy));
    for (java.util.Map.Entry<String, Boolean> e : GlobalOptions.packFeatures.entrySet()) {
      System.setProperty(e.getKey(), e.getValue() ? "true" : "false");
      anyOff |= !e.getValue();
    }
    if (anyOff && PACK_ROWS) Minecraft.getMinecraft().scheduleResourcesRefresh();
  }

  /**
   * Vanilla's particle level (All / Decreased / Minimal) has no row any more: it is held at All, so the per-type switches on
   * the Animations page are the only control (Minimal would also hide the crits those switches keep).
   */
  static void holdParticleLevel() {
    GameSettings g = Minecraft.getMinecraft().gameSettings;
    if (g != null && g.particleSetting != 0) {
      g.particleSetting = 0;
      g.saveOptions();
    }
  }

  private static Setting<?> animationRow(String key) {
    String[] keys = AnimationOptions.OTHER_KEYS, names = AnimationOptions.OTHER_NAMES;
    for (int i = 0; i < keys.length; i++) {
      if (keys[i].equals(key)) return toggle(names[i], GlobalOptions.animationEnabled(key) && ArgentumExtrasBridge.on(key), true, "Turn off to skip this effect");
    }
    throw new IllegalArgumentException(key);
  }

  /** An Argentum Extras row (Normal renderer only); its page is the group of the rows around it. */
  private static void extras(List<Setting<?>> list, String name) {
    Setting<?> row = ArgentumExtrasBridge.settingRow(name);
    if (row != null) add(list, list.get(list.size() - 1).group(), row);
  }

  /** True when the row is one of Argentum Extras' own switches. Applies it if so. */
  static boolean applyExtras(Setting<?> s) {
    return ArgentumExtrasBridge.applyRow(s);
  }

  private static void add(List<Setting<?>> list, String group, Setting<?> s) {
    s.setGroup(group);
    list.add(s);
  }

  static Setting.Slider slider(String name, float v, float min, float max, float step, String tip) {
    return new Setting.Slider(name, v, min, max, step, tip);
  }

  static Setting.Toggle toggle(String name, boolean v, boolean def, String tip) {
    Setting.Toggle t = new Setting.Toggle(name, def, tip);
    t.set(v);
    return t;
  }

  static Setting.Choice choice(String name, int index, String[] names, int def, String tip) {
    Setting.Choice c = Setting.tip(new Setting.Choice(name, names[def], names), tip);
    c.set(names[Math.max(0, Math.min(names.length - 1, index))]);
    return c;
  }
}
