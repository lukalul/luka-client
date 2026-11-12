package dev.cbui.render;

import dev.cbui.config.GlobalOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

/** "Glint distance" setting: the enchantment shine is skipped on other players, mobs and dropped items farther away than this. */
public final class GlintDistance {
  private static Entity current;

  private GlintDistance() {}

  public static void begin(Entity entity) {
    current = entity;
  }

  public static void end() {
    current = null;
  }

  /** Only while an entity is being drawn (armour on 1.7.10 asks isItemEnchanted, which the game also uses outside drawing). */
  public static boolean skipInWorld() {
    return current != null && skip();
  }

  /** The hand and every GUI have no entity being drawn around them, so they always keep their shine. */
  public static boolean skip() {
    Entity entity = current;
    // the No Glint mod: no shine at all (in the hand and menus, or on everything drawn in the world)
    dev.cbui.feature.noglint.NoGlintModule off = dev.cbui.feature.noglint.NoGlintModule.active();
    if (off != null && (entity == null ? off.hand.get() : off.world.get())) return true;
    int blocks = GlobalOptions.glintDistance;
    if (blocks >= 64 || entity == null) return false;
    Entity viewer = Minecraft.getMinecraft().getRenderViewEntity();
    return viewer != null && entity != viewer && entity.getDistanceSqToEntity(viewer) > (double) blocks * blocks;
  }
}
