package dev.cbui.feature.oldanimations;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;

/** Helpers for the 1.7 enchantment glint (kept outside the mixin package so the sprite subclass can load normally). */
public final class LegacyGlint {
  /** Colour the 1.7 glint is drawn with on models. */
  public static final int GLINT_COLOR = 0xFF61309B;

  /** A sprite that maps a quad's texture pixels to a plain 0..1 range, so the glint texture is laid over the item itself. */
  private static final class ItemSpace extends TextureAtlasSprite {
    ItemSpace() { super("cbui:legacy_glint"); }
    @Override public float getInterpolatedU(double u) { return (float) (-u / 16.0D); }
    @Override public float getInterpolatedV(double v) { return (float) (v / 16.0D); }
  }

  private static TextureAtlasSprite sprite;
  private static final Map<IBakedModel, IBakedModel> MODELS = new WeakHashMap<IBakedModel, IBakedModel>();

  private LegacyGlint() {}

  /** The same model with item-space texture coordinates. */
  public static synchronized IBakedModel itemSpaceModel(IBakedModel model) {
    IBakedModel out = MODELS.get(model);
    if (out == null) {
      if (sprite == null) sprite = new ItemSpace();
      out = new SimpleBakedModel.Builder(model, sprite).makeBakedModel();
      MODELS.put(model, out);
    }
    return out;
  }

  public static float scroll(long time, int layer) {
    long period = layer == 0 ? 3000L : 4873L;
    return (float) (time % period) / (float) period;
  }

  /** The flat glint overlay drawn over an item in a GUI slot (x, y is the slot's top left, as given to renderItemIntoGUI). */
  public static void drawGuiGlint(net.minecraft.client.renderer.texture.TextureManager textures,
      net.minecraft.util.ResourceLocation glint, float zLevel, int x, int y) {
    textures.bindTexture(glint);
    GlStateManager.depthFunc(518);
    GlStateManager.disableLighting();
    GlStateManager.depthMask(false);
    GlStateManager.enableAlpha();
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(772, 1, 0, 0);
    GlStateManager.color(0.5019608f, 0.2509804f, 0.8f, 1f);
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer buffer = tessellator.getWorldRenderer();
    double x0 = x - 2, y0 = y - 2, x1 = x0 + 20.0D, y1 = y0 + 20.0D;
    double z = (double) (100.0f + zLevel);
    float size = 0.078125f;
    long now = net.minecraft.client.Minecraft.getSystemTime();
    for (int layer = 0; layer < 2; layer++) {
      float u = scroll(now, layer);
      float skew = (layer == 0 ? 4.0f : -1.0f) * size;
      buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
      buffer.pos(x0, y1, z).tex((double) (u + skew), (double) size).endVertex();
      buffer.pos(x1, y1, z).tex((double) (u + size + skew), (double) size).endVertex();
      buffer.pos(x1, y0, z).tex((double) (u + size), 0.0D).endVertex();
      buffer.pos(x0, y0, z).tex((double) u, 0.0D).endVertex();
      tessellator.draw();
    }
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GlStateManager.color(1f, 1f, 1f, 1f);
    GlStateManager.disableAlpha();
    GlStateManager.depthMask(true);
    GlStateManager.depthFunc(515);
    textures.bindTexture(net.minecraft.client.renderer.texture.TextureMap.locationBlocksTexture);
  }
}
