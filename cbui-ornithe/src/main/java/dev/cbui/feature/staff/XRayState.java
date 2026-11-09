package dev.cbui.feature.staff;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/**
 * Shared state of Lunar's X-Ray staff mod. The mixins on the terrain renderer (see the XRay* mixins) read these volatile fields from the
 * render thread and from the chunk meshing threads; StaffModeModule keeps them up to date. While {@link #active} is false nothing changes.
 */
public final class XRayState {
  /** Ore-like categories, in the order of the "X-Ray blocks" toggles of the Staff Mode card. */
  public static final String[] NAMES = {"Diamond", "Emerald", "Gold", "Iron", "Lapis", "Redstone", "Coal", "Quartz", "Spawners", "Chests", "Lava"};
  public static final int LAVA = 10;

  /** True only while Staff Mode says X-Ray may run. */
  public static volatile boolean active;
  /** Opacity of the ordinary terrain (0.03 - 0.6). */
  public static volatile float opacity = 0.15f;
  /** Bit i set = category i stays visible. */
  public static volatile int mask = (1 << NAMES.length) - 1;

  private static volatile byte[] categoryOf;

  /** Per meshing thread: the block being meshed is an ore-like block forced into the translucent pass (read by Argentum's selectMaterial hook). */
  private static final ThreadLocal<boolean[]> FORCED = new ThreadLocal<boolean[]>() {
    @Override protected boolean[] initialValue() { return new boolean[1]; }
  };

  public static void setForced(boolean forced) {
    FORCED.get()[0] = forced;
  }

  public static boolean isForced() {
    return FORCED.get()[0];
  }

  private XRayState() {}

  /** Category of the block (index into {@link #NAMES}) or -1. Safe on any thread. */
  public static int category(Block block) {
    if (block == null) return -1;
    byte[] table = categoryOf;
    if (table == null) table = build();
    int id = Block.getIdFromBlock(block);
    return id >= 0 && id < table.length ? table[id] : -1;
  }

  /** True if the block is ore-like and its category is switched on. */
  public static boolean isOre(Block block) {
    int c = category(block);
    return c >= 0 && (mask & (1 << c)) != 0;
  }

  /** True if the block is ore-like and X-Ray is active (the cheap check used by the mesher). */
  public static boolean forced(Block block) {
    return active && isOre(block);
  }

  private static synchronized byte[] build() {
    byte[] table = categoryOf;
    if (table != null) return table;
    table = new byte[4096];
    for (int i = 0; i < table.length; i++) table[i] = -1;
    mark(table, Blocks.diamond_ore, 0);
    mark(table, Blocks.emerald_ore, 1);
    mark(table, Blocks.gold_ore, 2);
    mark(table, Blocks.iron_ore, 3);
    mark(table, Blocks.lapis_ore, 4);
    mark(table, Blocks.redstone_ore, 5);
    mark(table, Blocks.lit_redstone_ore, 5);
    mark(table, Blocks.coal_ore, 6);
    mark(table, Blocks.quartz_ore, 7);
    mark(table, Blocks.mob_spawner, 8);
    mark(table, Blocks.chest, 9);
    mark(table, Blocks.trapped_chest, 9);
    mark(table, Blocks.ender_chest, 9);
    mark(table, Blocks.lava, LAVA);
    mark(table, Blocks.flowing_lava, LAVA);
    categoryOf = table;
    return table;
  }

  private static void mark(byte[] table, Block block, int category) {
    int id = Block.getIdFromBlock(block);
    if (id >= 0 && id < table.length) table[id] = (byte) category;
  }
}
