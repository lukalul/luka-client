package dev.cbui.config;

import dev.cbui.hud.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.opengl.Display;

/** The performance tab: the vanilla video options that cost the most frame time, read from and written to the game settings. */
public final class PerfOptions {
  private static boolean reload, refreshResources, save;
  private static int pendingMipmap = -1;

  private PerfOptions() {}

  public static List<Setting<?>> build() {
    GameSettings g = Minecraft.getMinecraft().gameSettings;
    List<Setting<?>> list = new ArrayList<>();
    // the picture settings are in the VIDEO tab (VideoOptions), this one is for what only trades look for speed
    list.add(grp("Frame rate", toggle("Limit FPS when unfocused", GlobalOptions.unfocusedLimit, true, "Drops to 30 FPS while the game window is in the background so it stops using your GPU")));
    list.add(grp("Frame rate", toggle("Above normal priority", GlobalOptions.highPriority, true, "Asks Windows to favour the game over ordinary background programs. Removes nearly all stutter over 4 ms in a crowd. Voice chat and recording keep working")));
    list.add(grp("Frame rate", slider("HUD refresh rate", GlobalOptions.hudRefresh, 30, 250, 10, "How many times a second the luka HUD boxes are redrawn; in between the last picture is reused, which saves a lot at high FPS. 250 redraws every frame. Default 120")));
    list.add(grp("World", slider("Block entity distance", GlobalOptions.tileDistance, 8, 64, 4, "How far away chests, portals, spawners and other block entities are still drawn, in blocks. Lower is faster")));
    list.add(grp("World", slider("Entity detail distance", GlobalOptions.layerDistance, 16, 128, 8, "Players and mobs farther than this (in blocks) are drawn without armour, held items and capes. Their bodies stay. Lower is faster in crowds. 128 is off. Default 48")));
    list.add(grp("World", slider("Dropped item distance", GlobalOptions.itemDistance, 8, 64, 4, "Dropped items and experience orbs farther than this (in blocks) are not drawn. Lower is faster when loot piles up. 64 is vanilla (off). Default 48")));
    list.add(grp("World", slider("Nametag distance", GlobalOptions.nametagDistance, 8, 64, 4, "Names above players and mobs are drawn only within this many blocks. Lower is faster in crowds. 64 is vanilla (off). Default 48")));
    list.add(grp("World", slider("Glint distance", GlobalOptions.glintDistance, 0, 64, 8, "The enchantment shine on other players, mobs and dropped items is drawn only within this many blocks. Worth up to 20% in a crowd. Your own hand and inventory always keep it. 0 removes it, 64 is vanilla (off)")));
    list.add(grp("World", choice("Lazy chunk loading", GlobalOptions.chunkPace, new String[] {"Off", "Low", "Medium", "High"}, 0, "Spreads the work of loading new chunks over more frames. Higher keeps FPS steadier when you join a server, teleport or fly fast, but chunks appear a little slower. Block changes near you are never delayed")));
    list.add(grp("World", toggle("Entity culling", GlobalOptions.entityCulling, true, "Players, mobs and dropped items completely behind solid blocks are not drawn, which saves a lot when a base or wall hides a crowd or loot. Names still show through walls as usual")));
    list.add(grp("World", toggle("Hide foliage", GlobalOptions.hideFoliage, false, "Does not draw tall grass, ferns, flowers, dead bushes, vines or mushrooms. Fewer things to draw, and it is easier to see players and items. Leaves stay (they follow Graphics in Video Settings)")));
    return list;
  }

  private static Setting.Slider slider(String name, float v, float min, float max, float step, String tip) {
    Setting.Slider s = new Setting.Slider(name, v, min, max, step, tip);
    return s;
  }

  private static Setting.Toggle toggle(String name, boolean v, boolean def, String tip) {
    Setting.Toggle t = new Setting.Toggle(name, def, tip);
    t.set(v);
    return t;
  }

  private static Setting<?> grp(String group, Setting<?> s) {
    s.setGroup(group);
    return s;
  }

  private static Setting.Choice choice(String name, int index, String[] names, int def, String tip) {
    Setting.Choice c = Setting.tip(new Setting.Choice(name, names[def], names), tip);
    c.set(names[Math.max(0, Math.min(names.length - 1, index))]);
    return c;
  }

  /** Writes one changed row to the game settings. */
  public static void apply(Setting<?> s) {
    Minecraft mc = Minecraft.getMinecraft();
    GameSettings g = mc.gameSettings;
    String n = s.name();
    if (VideoOptions.applyAnimation(s)) return;
    if (VideoOptions.applyParticle(s)) return;
    if (VideoOptions.applyPackFeature(s)) { refreshResources = true; return; }
    save = true;
    if (n.equals("Max FPS")) g.limitFramerate = Math.round((Float) s.get());
    else if (n.equals("VSync")) {
      g.enableVsync = (Boolean) s.get();
      Display.setVSyncEnabled(g.enableVsync);
    } else if (n.equals("Fullscreen")) {
      if (mc.isFullScreen() != (Boolean) s.get()) mc.toggleFullscreen();
    } else if (n.equals("GUI scale")) {
      g.guiScale = ((Setting.Choice) s).choices().indexOf(s.get());
      net.minecraft.client.gui.ScaledResolution r = new net.minecraft.client.gui.ScaledResolution(mc);
      if (mc.currentScreen != null) mc.currentScreen.setWorldAndResolution(mc, r.getScaledWidth(), r.getScaledHeight());
    } else if (n.equals("Brightness")) g.gammaSetting = (Float) s.get() / 100f;
    else if (n.equals("FOV")) g.fovSetting = (Float) s.get();
    else if (n.equals("View bobbing")) g.viewBobbing = (Boolean) s.get();
    else if (n.equals("Leaves")) {
      GlobalOptions.leavesMode = ((Setting.Choice) s).choices().indexOf(s.get());
      reload = true;
    } else if (n.equals("Clear glass")) {
      GlobalOptions.clearGlass = ((Setting.Choice) s).choices().indexOf(s.get());
      // the Hide blocks tick sees the change and rebuilds the world
    } else if (n.equals("Vignette")) VideoOptions.applyVignette((Boolean) s.get());
    else if (n.equals("Held item names")) g.heldItemTooltips = (Boolean) s.get();
    else if (n.equals("Block alternatives")) {
      g.allowBlockAlternatives = (Boolean) s.get();
      reload = true;
    } else if (n.equals("Mipmap levels")) pendingMipmap = Math.round((Float) s.get());
    else if (n.equals("FXAA")) VideoOptions.applyFxaa((Boolean) s.get());
    else if (n.equals("Better grass")) {
      GlobalOptions.betterGrass = (Boolean) s.get();
      reload = true;
    }
    else if (n.equals("Anisotropic filtering")) {
      VideoOptions.applyAnisotropy(1 << ((Setting.Choice) s).choices().indexOf(s.get()));
      refreshResources = true;
    }
    else if (n.equals("Entity culling")) {
      GlobalOptions.entityCulling = (Boolean) s.get();
    } else if (n.equals("Hide foliage")) {
      GlobalOptions.hideFoliage = (Boolean) s.get();
      reload = true;
    } else if (n.equals("Above normal priority")) {
      GlobalOptions.highPriority = (Boolean) s.get();
      dev.cbui.render.GamePriority.apply(GlobalOptions.highPriority);
    } else if (n.equals("Limit FPS when unfocused")) GlobalOptions.unfocusedLimit = (Boolean) s.get();
    else if (n.equals("Render distance")) {
      g.renderDistanceChunks = Math.round((Float) s.get());
      reload = true;
    } else if (n.equals("Block entity distance")) GlobalOptions.tileDistance = Math.round((Float) s.get());
    else if (n.equals("HUD refresh rate")) { GlobalOptions.hudRefresh = Math.round((Float) s.get()); dev.cbui.hud.HudCache.dirty = true; }
    else if (n.equals("Nametag distance")) GlobalOptions.nametagDistance = Math.round((Float) s.get());
    else if (n.equals("Glint distance")) GlobalOptions.glintDistance = Math.round((Float) s.get());
    else if (n.equals("Dropped item distance")) GlobalOptions.itemDistance = Math.round((Float) s.get());
    else if (n.equals("Entity detail distance")) GlobalOptions.layerDistance = Math.round((Float) s.get());
    else if (n.equals("Lazy chunk loading")) GlobalOptions.chunkPace = ((Setting.Choice) s).choices().indexOf(s.get());
    else if (n.equals("Fog start")) GlobalOptions.fogStart = Math.round((Float) s.get());
    else if (n.equals("Fog end")) GlobalOptions.fogEnd = Math.round((Float) s.get());
    else if (n.equals("Cloud height")) GlobalOptions.cloudHeight = Math.round((Float) s.get());
    else if (n.equals("Clouds")) g.clouds = ((Setting.Choice) s).choices().indexOf(s.get());
    else if (n.equals("Graphics")) {
      g.fancyGraphics = "Fancy".equals(s.get());
      reload = true;
    } else if (n.equals("Smooth lighting")) {
      g.ambientOcclusion = ((Setting.Choice) s).choices().indexOf(s.get());
      reload = true;
    } else if (n.equals("Particles")) g.particleSetting = ((Setting.Choice) s).choices().indexOf(s.get());
    else if (n.equals("Entity shadows")) g.entityShadows = (Boolean) s.get();
  }

  /** Rebuilds chunk renderers once after a slider drag ends instead of on every step. */
  /**
   * Clear glass from outside the settings screens (the Resource Packs screen calls this by reflection): 0 off, 1 normal glass
   * and panes, 2 also stained glass. Saved at once; the world is rebuilt so the glass goes or comes back.
   */
  public static void setClearGlass(int mode) {
    GlobalOptions.clearGlass = Math.max(0, Math.min(2, mode));
    // the Hide blocks tick sees the change and rebuilds the world
    if (dev.cbui.Cbui.config() != null && dev.cbui.Cbui.hud() != null) dev.cbui.Cbui.config().save(dev.cbui.Cbui.hud());
  }

  public static void flush() {
    Minecraft mc = Minecraft.getMinecraft();
    GameSettings g = mc.gameSettings;
    VideoOptions.saveRendererSettings();
    if (pendingMipmap >= 0 && pendingMipmap != g.mipmapLevels) g.setOptionFloatValue(GameSettings.Options.MIPMAP_LEVELS, pendingMipmap);
    pendingMipmap = -1;
    if (refreshResources) {
      refreshResources = false;
      mc.scheduleResourcesRefresh();
    }
    if (reload) {
      reload = false;
      if (mc.renderGlobal != null) mc.renderGlobal.loadRenderers();
      save = true;
    }
    if (save) {
      save = false;
      g.saveOptions();
    }
  }
}
