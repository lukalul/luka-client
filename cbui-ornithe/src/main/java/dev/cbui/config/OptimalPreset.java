package dev.cbui.config;

import dev.cbui.Cbui;
import java.io.File;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.EnumParticleTypes;
import org.lwjgl.opengl.Display;

/**
 * luka's optimal video settings. A new player (no config/cbui.json before this launch) gets them once; Video Settings > Other >
 * Reset video settings puts them back. FOV, GUI scale, brightness, view bobbing, mipmaps, fog, sky, weather, keys and the HUD are
 * left alone.
 */
public final class OptimalPreset {
  /** The only particles left on: hits (crit, sharpness) and potion effects (swirls on players, splash bursts). */
  private static final Set<EnumParticleTypes> KEEP = EnumSet.of(EnumParticleTypes.CRIT, EnumParticleTypes.CRIT_MAGIC,
      EnumParticleTypes.SPELL, EnumParticleTypes.SPELL_INSTANT, EnumParticleTypes.SPELL_MOB, EnumParticleTypes.SPELL_MOB_AMBIENT);

  private static Boolean hadConfig;
  private static boolean decided;

  private OptimalPreset() {}

  private static File configFile() {
    return new File(Minecraft.getMinecraft().mcDataDir, "config/cbui.json");
  }

  /** Before the game starts loading: notes whether this game folder already had luka settings. Only the first call counts. */
  public static void noticeConfig() {
    if (hadConfig != null) return;
    try {
      hadConfig = configFile().isFile();
    } catch (Throwable error) {
      hadConfig = Boolean.TRUE;
      Cbui.reportOnce("optimal settings", error);
    }
  }

  /** Once the game has started (and again on the first tick, in case that hook did not run): a new player gets the preset, once. */
  public static void firstLaunch() {
    if (decided) return;
    noticeConfig();
    decided = true;
    if (hadConfig) return;
    try {
      apply();
      System.out.println("[CBUI] new player: optimal video settings applied");
    } catch (Throwable error) {
      Cbui.reportOnce("optimal settings", error);
    }
  }

  /** Sets the preset, saves options.txt and cbui.json, and rebuilds the world when one is loaded. */
  public static void apply() {
    Minecraft mc = Minecraft.getMinecraft();
    GameSettings g = mc.gameSettings;
    if (g == null) return;
    boolean rebuild = g.fancyGraphics || g.ambientOcclusion != 0 || g.renderDistanceChunks != 8 || GlobalOptions.leavesMode != 0;

    g.particleSetting = 0;
    for (EnumParticleTypes type : EnumParticleTypes.values()) GlobalOptions.animationEnabled(AnimationOptions.particleKey(type), KEEP.contains(type));
    GlobalOptions.animationEnabled("item_block_break", false);
    GlobalOptions.animationEnabled("void_particles", false);
    GlobalOptions.animationEnabled("redstone_dust", false);
    GlobalOptions.fewerAmbientEffects = true;
    GlobalOptions.particleCollisions = false;

    g.fancyGraphics = false;
    g.ambientOcclusion = 0;
    g.clouds = 0;
    g.renderDistanceChunks = 8;
    g.entityShadows = false;
    g.limitFramerate = 260;
    GlobalOptions.leavesMode = 0;
    g.enableVsync = false;
    try {
      Display.setVSyncEnabled(false);
    } catch (Throwable noWindowYet) {
      // the window reads the saved option when it is made
    }

    g.saveOptions();
    if (rebuild && mc.theWorld != null && mc.renderGlobal != null) mc.renderGlobal.loadRenderers();
    if (Cbui.config() != null && Cbui.hud() != null) Cbui.config().save(Cbui.hud());
  }
}
