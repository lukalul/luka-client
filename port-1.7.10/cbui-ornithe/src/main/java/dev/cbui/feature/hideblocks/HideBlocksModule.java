package dev.cbui.feature.hideblocks;

import dev.cbui.Cbui;
import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;

/**
 * Stops chosen blocks being drawn, for FPS. Nothing else about them changes: you still stand on them, bump into them and break them.
 * On a server only blocks you can walk through can be hidden, so this can never be used to look through walls.
 * In singleplayer config/luka-hidden-blocks.txt (one block name per line, e.g. minecraft:stone) can hide anything.
 */
public final class HideBlocksModule extends FeatureModule implements Features.Synced {
  private static final String[][] GROUPS = {
    {"Grass and ferns", "tallgrass", "deadbush"},
    {"Flowers", "yellow_flower", "red_flower", "double_plant"},
    {"Mushrooms", "brown_mushroom", "red_mushroom"},
    {"Crops and reeds", "wheat", "carrots", "potatoes", "nether_wart", "reeds", "melon_stem", "pumpkin_stem"},
    {"Torches", "torch"},
    {"Redstone", "redstone_wire", "redstone_torch", "unlit_redstone_torch", "unpowered_repeater", "powered_repeater",
        "unpowered_comparator", "powered_comparator", "lever", "stone_button", "wooden_button", "tripwire", "tripwire_hook"},
    {"Rails", "rail", "golden_rail", "detector_rail", "activator_rail"},
    {"Carpets and snow", "carpet", "snow_layer"},
    {"Vines and lily pads", "vine", "waterlily"},
  };
  /** The in-game "Hide foliage" setting (Performance tab). */
  private static final String[] FOLIAGE = {"tallgrass", "deadbush", "yellow_flower", "red_flower", "double_plant", "brown_mushroom", "red_mushroom", "vine"};
  private static final boolean[] DEFAULTS = {true, true, true, false, false, false, false, false, false};

  private static volatile boolean[] hidden = new boolean[4096];
  private static volatile boolean anySolid;

  private final List<Setting.Toggle> toggles = new ArrayList<>();
  private final Setting.Toggle everything = grouped("Singleplayer", add(new Setting.Toggle(
      "Everything", false, "Hides every block, including the ground. Singleplayer only: on a server this would be seeing through walls.")));
  private String applied = "";

  public HideBlocksModule() {
    super("Hide Blocks", false);
    for (int i = 0; i < GROUPS.length; i++) {
      toggles.add(grouped("Blocks", add(new Setting.Toggle(GROUPS[i][0], DEFAULTS[i], "These blocks are not drawn while this mod is on."))));
    }
  }

  @Override public String category() { return "VISUALS"; }

  public static boolean hides(Block block) {
    int id = Block.getIdFromBlock(block);
    return id >= 0 && id < 4096 && hidden[id];
  }

  /** True when a full block may be hidden, so the faces next to it have to be drawn. */
  public static boolean anySolid() { return anySolid; }

  /** Runs every tick, even while the card is off, so turning it off brings the blocks back. */
  @Override public void sync() {
    try {
      // the Vulkan renderer has its own chunk scheduling and cannot see luka's settings, so it reads this
      System.setProperty("luka.chunkPace", Integer.toString(dev.cbui.config.GlobalOptions.chunkPace));
      boolean on = enabled();
      boolean foliage = dev.cbui.config.GlobalOptions.hideFoliage;
      boolean singleplayer = mc.isSingleplayer();
      StringBuilder key = new StringBuilder().append(on).append(singleplayer).append(foliage);
      for (Setting.Toggle toggle : toggles) key.append(toggle.get() ? '1' : '0');
      key.append(everything.get());
      File custom = new File(mc.mcDataDir, "config/luka-hidden-blocks.txt");
      if (on && singleplayer && custom.isFile()) key.append(custom.lastModified());
      String signature = key.toString();
      if (signature.equals(applied)) return;
      boolean first = applied.isEmpty();
      applied = signature;
      boolean[] table = new boolean[4096];
      boolean solid = false;
      if (foliage) for (String name : FOLIAGE) mark(table, Block.getBlockFromName("minecraft:" + name), singleplayer);
      if (on) {
        for (int i = 0; i < GROUPS.length; i++) {
          if (!toggles.get(i).get()) continue;
          for (int b = 1; b < GROUPS[i].length; b++) solid |= mark(table, Block.getBlockFromName("minecraft:" + GROUPS[i][b]), singleplayer);
        }
        if (singleplayer && everything.get()) {
          for (Object block : Block.blockRegistry) solid |= mark(table, (Block) block, true);
        }
        if (singleplayer && custom.isFile()) {
          for (String line : Files.readAllLines(custom.toPath(), StandardCharsets.UTF_8)) {
            String name = line.trim();
            if (!name.isEmpty() && !name.startsWith("#")) solid |= mark(table, Block.getBlockFromName(name), true);
          }
        }
      }
      boolean any = false;
      for (boolean value : table) any |= value;
      boolean wasAny = false;
      for (boolean value : hidden) wasAny |= value;
      hidden = table;
      anySolid = solid;
      if (!(first && !any) && (any || wasAny) && mc.renderGlobal != null && mc.theWorld != null) mc.renderGlobal.loadRenderers();
    } catch (Throwable error) {
      Cbui.reportOnce("hide blocks", error);
    }
  }

  /** Returns true if the block marked is one you could not walk through. */
  private static boolean mark(boolean[] table, Block block, boolean anything) {
    if (block == null) return false;
    boolean solid = block.getMaterial().blocksMovement();
    if (!anything && (solid || block instanceof BlockLiquid)) return false;
    int id = Block.getIdFromBlock(block);
    if (id <= 0 || id >= 4096) return false;
    table[id] = true;
    return solid;
  }

  public static HideBlocksModule get() { return Features.get(HideBlocksModule.class); }
}
