package dev.cbui.feature.hideblocks;

import dev.cbui.Cbui;
import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;

/**
 * Blocks that are not drawn, for the settings that hide some: "Hide foliage" (Performance) and "Clear glass" (Video Settings,
 * Quality). Nothing else about the blocks changes: you still stand on them, bump into them and break them. Only blocks you can
 * walk through, and glass, are ever hidden, so this can never be used to look through walls.
 *
 * <p>This was the "Hide Blocks" mod; its card and its own block lists were removed on 2026-11-13 (Hide foliage covers them).
 * It has no card any more and only runs the two settings above. The name stays so the mixins and old configs keep working.
 */
public final class HideBlocksModule extends FeatureModule implements Features.Synced {
  /** The in-game "Hide foliage" setting (Performance tab). */
  private static final String[] FOLIAGE = {"tallgrass", "deadbush", "yellow_flower", "red_flower", "double_plant", "brown_mushroom", "red_mushroom", "vine"};
  private static final String[] GLASS_REGULAR = {"glass", "glass_pane"};
  private static final String[] GLASS_ALL = {"glass", "glass_pane", "stained_glass", "stained_glass_pane"};

  private static volatile boolean[] hidden = new boolean[4096];
  private String applied = "";

  public HideBlocksModule() {
    super("Hide Blocks", false);
  }

  @Override public String category() { return "VISUALS"; }

  /** No card: the settings that use this live in Performance and Video Settings. */
  @Override public boolean visible() { return false; }

  public static boolean hides(Block block) {
    int id = Block.getIdFromBlock(block);
    return id >= 0 && id < 4096 && hidden[id];
  }

  /** True when a full block may be hidden, so the faces next to it have to be drawn. Foliage and glass never are. */
  public static boolean anySolid() { return false; }

  /** Runs every tick, so changing either setting brings the blocks back or hides them. */
  @Override public void sync() {
    try {
      // the Vulkan renderer has its own chunk scheduling and cannot see luka's settings, so it reads this
      System.setProperty("luka.chunkPace", Integer.toString(dev.cbui.config.GlobalOptions.chunkPace));
      boolean foliage = dev.cbui.config.GlobalOptions.hideFoliage;
      int glass = dev.cbui.config.GlobalOptions.clearGlass;
      String signature = foliage + "," + glass;
      if (signature.equals(applied)) return;
      boolean first = applied.isEmpty();
      applied = signature;
      boolean[] table = new boolean[4096];
      if (foliage) for (String name : FOLIAGE) mark(table, Block.getBlockFromName("minecraft:" + name));
      // glass shows what is behind it anyway, so it may be hidden everywhere; it does not hide the faces next to it
      if (glass > 0) for (String name : glass > 1 ? GLASS_ALL : GLASS_REGULAR) mark(table, Block.getBlockFromName("minecraft:" + name));
      boolean any = false;
      for (boolean value : table) any |= value;
      boolean wasAny = false;
      for (boolean value : hidden) wasAny |= value;
      hidden = table;
      if (!(first && !any) && (any || wasAny) && mc.renderGlobal != null && mc.theWorld != null) mc.renderGlobal.loadRenderers();
    } catch (Throwable error) {
      Cbui.reportOnce("hide blocks", error);
    }
  }

  private static void mark(boolean[] table, Block block) {
    if (block == null || block instanceof BlockLiquid) return;
    int id = Block.getIdFromBlock(block);
    if (id > 0 && id < 4096) table[id] = true;
  }

  public static HideBlocksModule get() { return Features.get(HideBlocksModule.class); }
}
