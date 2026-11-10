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

/** The small luka mark drawn left of a luka player's nametag, in the launcher logo's purple. */
public final class RankIcon {
  private static final ResourceLocation LOGO = new ResourceLocation("cbui", "logo_white.png");
  /** The same mark scaled down cleanly for nametag size (the big one loses its thin ring when the game shrinks it). */
  private static final ResourceLocation SMALL_LOGO = new ResourceLocation("cbui", "logo_white_40.png");
  /** The launcher logo's purple, #9655F5. */
  private static final float PURPLE_R = 150 / 255f, PURPLE_G = 85 / 255f, PURPLE_B = 245 / 255f;

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
    float fill = solid ? 1f : 0.35f;
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    Tessellator tess = Tessellator.instance;
    VertexBuffer wr = dev.luka.compat.VertexBuffer.INSTANCE;
    // the luka mark alone, in the launcher logo's purple, on the nametag's own background (no badge behind it)
    Minecraft.getMinecraft().getTextureManager().bindTexture(SMALL_LOGO);
    GlStateManager.color(PURPLE_R, PURPLE_G, PURPLE_B, fill);
    wr.begin(7, DefaultVertexFormats.POSITION_TEX);
    wr.pos(x0, y0 + 10, 0).tex(0, 1).endVertex();
    wr.pos(x0 + 10, y0 + 10, 0).tex(1, 1).endVertex();
    wr.pos(x0 + 10, y0, 0).tex(1, 0).endVertex();
    wr.pos(x0, y0, 0).tex(0, 0).endVertex();
    tess.draw();
    GlStateManager.color(1f, 1f, 1f, 1f);
  }
}
