package dev.cbui.feature.blockoverlay;

import dev.cbui.Cbui;
import dev.cbui.feature.Features;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import dev.luka.compat.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.opengl.GL11;

/** Draws the Block Overlay where vanilla would draw its selection box. */
public final class BlockOverlayRenderer {
  private static boolean failed;

  private BlockOverlayRenderer() {}

  /** True when the overlay replaces vanilla's box (it is drawn here). */
  public static boolean draw(EntityPlayer player, MovingObjectPosition hit, int mode, float partialTicks) {
    if (failed || mode != 0 || hit == null || hit.typeOfHit != net.minecraft.util.MovingObjectPosition$MovingObjectType.BLOCK) return false;
    BlockOverlayModule m = Features.get(BlockOverlayModule.class);
    if (m == null || !m.enabled()) return false;
    Minecraft mc = Minecraft.getMinecraft();
    if (m.hideHud.get() && mc.gameSettings.hideGUI) return true;
    try {
      Block block = mc.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ);
      if (block.getMaterial() == net.minecraft.block.material.Material.air) return true;
      block.setBlockBoundsBasedOnState(mc.theWorld, hit.blockX, hit.blockY, hit.blockZ);
      double dx = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
      double dy = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
      double dz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
      AxisAlignedBB box = block.getSelectedBoundingBoxFromPool(mc.theWorld, hit.blockX, hit.blockY, hit.blockZ).expand(0.002, 0.002, 0.002).getOffsetBoundingBox(-dx, -dy, -dz);
      paint(m, box);
    } catch (Throwable error) {
      failed = true;
      Cbui.reportOnce("block overlay", error);
      return false;
    }
    return true;
  }

  private static void paint(BlockOverlayModule m, AxisAlignedBB b) {
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GlStateManager.disableTexture2D();
    GlStateManager.disableLighting();
    GlStateManager.disableCull();
    GlStateManager.depthMask(false);
    boolean through = m.throughWalls.get();
    if (through) GlStateManager.disableDepth();
    if (m.fill.get()) {
      int c = m.resolve(m.fillColor.get());
      if ((c >>> 24) > 0) {
        GlStateManager.color((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, (c >>> 24) / 255f);
        GL11.glBegin(GL11.GL_QUADS);
        quad(b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ);
        quad(b.minX, b.maxY, b.minZ, b.minX, b.maxY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.maxY, b.minZ);
        quad(b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.minY, b.minZ);
        quad(b.minX, b.minY, b.maxZ, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ);
        quad(b.minX, b.minY, b.minZ, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ);
        quad(b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.minY, b.maxZ);
        GL11.glEnd();
      }
    }
    if (m.outline.get()) {
      int c = m.resolve(m.outlineColor.get());
      if ((c >>> 24) > 0) {
        GlStateManager.color((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, (c >>> 24) / 255f);
        GL11.glLineWidth(m.width.get());
        GL11.glBegin(GL11.GL_LINES);
        double[] x = {b.minX, b.maxX}, y = {b.minY, b.maxY}, z = {b.minZ, b.maxZ};
        for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) {
          line(x[0], y[i], z[j], x[1], y[i], z[j]);
          line(x[i], y[0], z[j], x[i], y[1], z[j]);
          line(x[i], y[j], z[0], x[i], y[j], z[1]);
        }
        GL11.glEnd();
        GL11.glLineWidth(1f);
      }
    }
    if (through) GlStateManager.enableDepth();
    GlStateManager.depthMask(true);
    GlStateManager.enableCull();
    GlStateManager.enableTexture2D();
    GlStateManager.disableBlend();
    GlStateManager.color(1f, 1f, 1f, 1f);
  }

  private static void quad(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4) {
    GL11.glVertex3d(x1, y1, z1);
    GL11.glVertex3d(x2, y2, z2);
    GL11.glVertex3d(x3, y3, z3);
    GL11.glVertex3d(x4, y4, z4);
  }

  private static void line(double x1, double y1, double z1, double x2, double y2, double z2) {
    GL11.glVertex3d(x1, y1, z1);
    GL11.glVertex3d(x2, y2, z2);
  }
}
