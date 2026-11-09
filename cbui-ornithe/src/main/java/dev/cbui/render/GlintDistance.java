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

  /** The hand and every GUI have no entity being drawn around them, so they always keep their shine. */
  public static boolean skip() {
    int blocks = GlobalOptions.glintDistance;
    Entity entity = current;
    if (blocks >= 64 || entity == null) return false;
    Entity viewer = Minecraft.getMinecraft().getRenderViewEntity();
    return viewer != null && entity != viewer && entity.getDistanceSqToEntity(viewer) > (double) blocks * blocks;
  }
}
