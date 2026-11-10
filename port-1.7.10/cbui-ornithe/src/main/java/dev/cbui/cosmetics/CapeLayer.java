package dev.cbui.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import dev.luka.compat.GlStateManager;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

/**
 * Draws the chosen cape on the local player, the way the game draws a cape (same swing and sneak movement), with a model whose
 * texture grid is the 22 x 17 cape area, so a cape sheet of any resolution (44, 88, 176, 704 pixels wide...) maps onto it.
 */
public final class CapeLayer {
  private static final ModelBase BASE = new ModelBase() { };
  private static ModelRenderer cape;

  private CapeLayer() {}

  // The cape box (-5..5, 0..16, -1..0 in sixteenths) as plain GL quads, with the same texture layout the model box uses (22 x 17 grid).
  // Mods that capture or batch ModelRenderer drawing never see it.
  private static final float S = 0.0625F, GW = 22f, GH = 17f;

  private static void quad(float nx, float ny, float nz, float[][] v, float u1, float v1, float u2, float v2) {
    org.lwjgl.opengl.GL11.glNormal3f(nx, ny, nz);
    float[][] uv = {{u2, v1}, {u1, v1}, {u1, v2}, {u2, v2}};
    for (int i = 0; i < 4; i++) {
      org.lwjgl.opengl.GL11.glTexCoord2f(uv[i][0] / GW, uv[i][1] / GH);
      org.lwjgl.opengl.GL11.glVertex3f(v[i][0] * S, v[i][1] * S, v[i][2] * S);
    }
  }

  private static void drawBox() {
    float x = -5f, y = 0f, z = -1f, x1 = 5f, y1 = 16f, z1 = 0f;
    float[] v0 = {x, y, z}, v1 = {x1, y, z}, v2 = {x1, y1, z}, v3 = {x, y1, z}, v4 = {x, y, z1}, v5 = {x1, y, z1}, v6 = {x1, y1, z1}, v7 = {x, y1, z1};
    float dx = 10f, dy = 16f, dz = 1f;
    org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);
    quad(1, 0, 0, new float[][] {v5, v1, v2, v6}, dz + dx, dz, dz + dx + dz, dz + dy);
    quad(-1, 0, 0, new float[][] {v0, v4, v7, v3}, 0, dz, dz, dz + dy);
    quad(0, -1, 0, new float[][] {v5, v4, v0, v1}, dz, 0, dz + dx, dz);
    quad(0, 1, 0, new float[][] {v2, v3, v7, v6}, dz + dx, dz, dz + dx + dx, 0);
    quad(0, 0, -1, new float[][] {v1, v0, v3, v2}, dz, dz, dz + dx, dz + dy);
    quad(0, 0, 1, new float[][] {v4, v5, v6, v7}, dz + dx + dz, dz, dz + dx + dz + dx, dz + dy);
    org.lwjgl.opengl.GL11.glEnd();
  }

  /** True when the cape was drawn (the game's own cape layer is skipped). */
  public static boolean render(AbstractClientPlayer player, float partialTicks) {
    Minecraft mc = Minecraft.getMinecraft();
    if (player.isInvisible() || player.getHideCape()) return false;
    // your own cape is the one chosen in Cosmetics; failing that (and for every other luka player) the one luka's service gives their rank
    ResourceLocation texture = player == mc.thePlayer ? Capes.current() : null;
    if (texture == null) texture = dev.cbui.social.RemoteCapes.textureFor(player.getUniqueID());
    if (texture == null) return false;
    if (cape == null) {
      cape = new ModelRenderer(BASE, 0, 0);
      cape.setTextureSize(22, 17);
      cape.addBox(-5.0F, 0.0F, -1.0F, 10, 16, 1);
    }
    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    mc.getTextureManager().bindTexture(texture);
    GlStateManager.pushMatrix();
    GlStateManager.translate(0.0F, 0.0F, 0.125F);
    double d0 = player.f_99890177 + (player.f_22466577 - player.f_99890177) * (double) partialTicks
        - (player.prevPosX + (player.posX - player.prevPosX) * (double) partialTicks);
    double d1 = player.f_62909408 + (player.f_60272612 - player.f_62909408) * (double) partialTicks
        - (player.prevPosY + (player.posY - player.prevPosY) * (double) partialTicks);
    double d2 = player.f_49011524 + (player.f_22361063 - player.f_49011524) * (double) partialTicks
        - (player.prevPosZ + (player.posZ - player.prevPosZ) * (double) partialTicks);
    float f = player.prevRenderYawOffset + (player.renderYawOffset - player.prevRenderYawOffset) * partialTicks;
    double d3 = (double) MathHelper.sin(f * (float) Math.PI / 180.0F);
    double d4 = (double) (-MathHelper.cos(f * (float) Math.PI / 180.0F));
    float f1 = (float) d1 * 10.0F;
    f1 = MathHelper.clamp_float(f1, -6.0F, 32.0F);
    float f2 = (float) (d0 * d3 + d2 * d4) * 100.0F;
    float f3 = (float) (d0 * d4 - d2 * d3) * 100.0F;
    if (f2 < 0.0F) f2 = 0.0F;
    float f4 = player.prevCameraYaw + (player.cameraYaw - player.prevCameraYaw) * partialTicks;
    f1 += MathHelper.sin((player.prevDistanceWalkedModified + (player.distanceWalkedModified - player.prevDistanceWalkedModified) * partialTicks) * 6.0F) * 32.0F * f4;
    if (player.isSneaking()) f1 += 25.0F;
    GlStateManager.rotate(6.0F + f2 / 2.0F + f1, 1.0F, 0.0F, 0.0F);
    GlStateManager.rotate(f3 / 2.0F, 0.0F, 0.0F, 1.0F);
    GlStateManager.rotate(-f3 / 2.0F, 0.0F, 1.0F, 0.0F);
    GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
    if (Capes.useModel) {
      cape.render(0.0625F);
    } else {
      drawBox();
    }
    GlStateManager.popMatrix();
    return true;
  }
}
