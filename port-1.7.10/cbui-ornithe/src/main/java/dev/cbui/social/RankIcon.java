package dev.cbui.social;

import dev.cbui.Cbui;
import dev.cbui.feature.Features;
import dev.cbui.feature.online.LukaOnlineModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import dev.luka.compat.VertexBuffer;
import dev.luka.compat.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

/** The small luka mark drawn left of a luka player's nametag, tinted with the colour of their rank. */
public final class RankIcon {
  private static final ResourceLocation LOGO = new ResourceLocation("cbui", "logo_white.png");

  private RankIcon() {}

  /** The rank colour (0xRRGGBB) of this entity if it is a luka player whose icon should be shown, else -1. */
  public static int colorFor(Entity owner) {
    try {
      if (!(owner instanceof AbstractClientPlayer)) return -1;
      LukaOnlineModule module = Features.get(LukaOnlineModule.class);
      if (module == null || !module.enabled() || !module.showIcons()) return -1;
      LukaNet.Info info = LukaNet.info(owner.getUniqueID());
      return info == null ? -1 : Ranks.get(info.rank).color;
    } catch (Throwable error) {
      Cbui.reportOnce("rank icon", error);
      return -1;
    }
  }

  /** The mark tinted with a colour (0xRRGGBB), for screens: {@code size} units square at (x, y). */
  public static void blit(float x, float y, float size, int rgb, float alpha) {
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    Tessellator tess = Tessellator.instance;
    VertexBuffer wr = dev.luka.compat.VertexBuffer.INSTANCE;
    Minecraft.getMinecraft().getTextureManager().bindTexture(LOGO);
    GlStateManager.color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, alpha);
    wr.begin(7, DefaultVertexFormats.POSITION_TEX);
    wr.pos(x, y + size, 0).tex(0, 1).endVertex();
    wr.pos(x + size, y + size, 0).tex(1, 1).endVertex();
    wr.pos(x + size, y, 0).tex(1, 0).endVertex();
    wr.pos(x, y, 0).tex(0, 0).endVertex();
    tess.draw();
    GlStateManager.color(1f, 1f, 1f, 1f);
  }

  /**
   * Draws the icon in nametag space: its box ends 1 unit left of where the name text starts. {@code solid} is the depth-tested second pass of
   * the label (full colour); the first pass is the faint see-through one.
   */
  public static void draw(Entity owner, float textX, float textY, boolean solid) {
    int rgb = colorFor(owner);
    if (rgb < 0) return;
    float x0 = textX - 11f, y0 = textY - 1f;
    float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
    float fill = solid ? 1f : 0.35f;
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    Tessellator tess = Tessellator.instance;
    VertexBuffer wr = dev.luka.compat.VertexBuffer.INSTANCE;
    // a badge in the rank's colour with the dark luka mark on it: readable at nametag size and the colour says the rank at a glance
    GlStateManager.disableTexture2D();
    GlStateManager.color(r, g, b, fill);
    wr.begin(7, DefaultVertexFormats.POSITION);
    wr.pos(x0, y0 + 10, 0).endVertex();
    wr.pos(x0 + 10, y0 + 10, 0).endVertex();
    wr.pos(x0 + 10, y0, 0).endVertex();
    wr.pos(x0, y0, 0).endVertex();
    tess.draw();
    GlStateManager.enableTexture2D();
    Minecraft.getMinecraft().getTextureManager().bindTexture(LOGO);
    GlStateManager.color(0.08f, 0.06f, 0.11f, solid ? 0.95f : 0.4f);
    wr.begin(7, DefaultVertexFormats.POSITION_TEX);
    wr.pos(x0 + 0.5f, y0 + 9.5f, 0).tex(0, 1).endVertex();
    wr.pos(x0 + 9.5f, y0 + 9.5f, 0).tex(1, 1).endVertex();
    wr.pos(x0 + 9.5f, y0 + 0.5f, 0).tex(1, 0).endVertex();
    wr.pos(x0 + 0.5f, y0 + 0.5f, 0).tex(0, 0).endVertex();
    tess.draw();
    GlStateManager.color(1f, 1f, 1f, 1f);
  }
}
