package dev.cbui.config;

/** Client-wide switches that are not tied to one HUD module (set on the menu's settings tab). */
public final class GlobalOptions {
  /** Hides the achievement toast ("Press E to open your inventory", unlocks, ...). */
  public static volatile boolean hideAchievements;
  /** False removes the drop shadow from all vanilla-font text (chat, hotbar counts, tooltips, nametags and the HUD). */
  public static volatile boolean textShadow = true;

  /** The colours picked last, newest first (the palette's "recent" row). */
  public static final java.util.List<Integer> recentColors = new java.util.concurrent.CopyOnWriteArrayList<>();

  public static void pushRecentColor(int argb) {
    recentColors.remove(Integer.valueOf(argb));
    recentColors.add(0, argb);
    while (recentColors.size() > 8) recentColors.remove(recentColors.size() - 1);
  }

  /** Grass block sides use the top texture (Vulkan renderer; Cera has its own on the other one). */
  public static volatile boolean betterGrass;
  /** FXAA on the Vulkan renderer. Argentum Extras keeps its own switch on the other one. */
  public static volatile boolean fxaa;
  /** Anisotropic filtering level on the Vulkan renderer, 1 is off. */
  public static volatile int anisotropy = 1;
  /** The Leaves row of the video tab: 0 follows Graphics, 1 fast, 2 fancy. */
  public static volatile int leavesMode;
  /** Clear glass: 0 off, 1 normal glass and panes, 2 also stained glass. Those blocks are not drawn in the world. */
  public static volatile int clearGlass;
  /** Video Settings Resolution, "WIDTHxHEIGHT"; empty is the monitor's own (see Resolution). */
  public static volatile String resolution = "";
  /** Where distance fog starts and ends, in percent of the render distance. Vanilla is 75 and 100. */
  public static volatile int fogStart = 75, fogEnd = 100;
  /** Height of the clouds. Vanilla is 128. */
  public static volatile int cloudHeight = 128;
  /** The dark screen edges on Fancy graphics. */
  public static volatile boolean vignette = true;
  /** Resource pack features switched in the video tab, by the system property that carries them. */
  public static final java.util.Map<String, Boolean> packFeatures = new java.util.concurrent.ConcurrentHashMap<String, Boolean>();
  /** Runs the game at Windows' Above normal process priority. */
  public static volatile boolean highPriority = true;
  /** Cap the frame rate at 30 while the window is not focused. */
  public static volatile boolean unfocusedLimit = true;
  /** Performance tab: frame rate caps for the main menu, pause menu, containers and HUD editor (0 = off). See FpsContexts. */
  public static volatile int menuFps, pauseFps, containerFps, hudEditorFps;
  /** Chunk meshing skips tall grass, flowers, ferns, dead bushes, vines and mushrooms. */
  public static volatile boolean hideFoliage;
  /** Performance tab: arrows stuck in players and mobs are not drawn. */
  public static volatile boolean hideStuckArrows;
  /** Performance tab: arrows lying in blocks are not drawn. */
  public static volatile boolean hideGroundArrows;
  /** Performance tab: arrows in the air are not drawn. */
  public static volatile boolean hideFlyingArrows;
  /** Performance tab: ambient block effects (drips, flames, void particles) try 100 spots a tick instead of 1,000. */
  public static volatile boolean fewerAmbientEffects;
  /** Performance tab: false lets new particles move through blocks without collision checks. */
  public static volatile boolean particleCollisions = true;
  /** Players and mobs fully behind solid blocks are not drawn; their names still are. */
  public static volatile boolean entityCulling = true;
  /** Borderless fullscreen window. */
  public static volatile boolean borderless;

  /**
   * Lazy chunk loading: 0 off, 1 low, 2 medium, 3 high. With Argentum (Celeritas) it scales how many chunks are built
   * at once and caps how many finished chunks are shown per frame; with vanilla chunk rendering, chunk updates run on
   * every 1st, 2nd, 4th or 8th frame.
   */
  public static volatile int chunkPace;
  /** Block entities (chests, portals, spawners...) are drawn only within this many blocks; 64 is vanilla. */
  public static volatile int tileDistance = 64;
  /** Players and mobs farther than this many blocks are drawn without armour, held items and other layers; 128 or more is off. */
  public static volatile int layerDistance = 128;
  /** Dropped items and experience orbs farther than this many blocks are not drawn; 64 or more is vanilla. */
  public static volatile int itemDistance = 64;
  /** Names above players and mobs are drawn only within this many blocks; 64 or more is vanilla. */
  public static volatile int nametagDistance = 64;
  /** The enchantment shine on other players, mobs and dropped items stops beyond this. 64 is vanilla (off), 0 removes it. */
  public static volatile int glintDistance = 64;
  /** The luka HUD is redrawn this many times a second and reused in between; 250 redraws it every frame. */
  public static volatile int hudRefresh = 120;

  /** Per-effect animation switches. Missing config values deliberately default to vanilla (on). */
  public static final java.util.Set<String> disabledAnimations =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

  public static boolean animationEnabled(String key) { return !disabledAnimations.contains(key); }

  public static void animationEnabled(String key, boolean enabled) {
    if (enabled) disabledAnimations.remove(key); else disabledAnimations.add(key);
  }

  public static int chunkPaceEvery() {
    return 1 << Math.max(0, Math.min(3, chunkPace));
  }

  /** Finished chunks shown per frame under lazy chunk loading; 0 means no limit. */
  public static int lazyUploadsPerFrame() {
    switch (chunkPace) {
      case 1: return 16;
      case 2: return 8;
      case 3: return 3;
      default: return 0;
    }
  }

  private GlobalOptions() {}
}
