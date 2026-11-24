package dev.cbui.feature.crosshair;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * Turns a profile into a picture at the screen's own resolution and draws it at the screen centre, in whatever 2D
 * projection is current. Pictures are cached per profile and only rebuilt when something that shapes them changes.
 */
final class CrosshairPainter {
  /** GL blend factors: the inverting blend the game uses for its crosshair, and normal alpha blending. */
  private static final int ONE_MINUS_DST_COLOR = 775;
  private static final int ONE_MINUS_SRC_COLOR = 769;
  private static final int SRC_ALPHA = 770;
  private static final int ONE_MINUS_SRC_ALPHA = 771;
  private static final int QUADS = 7;
  private static final int ICON = 16;
  /** Index of "Imported art" in the shape list. */
  private static final int IMPORTED = 5;

  private final Minecraft mc = Minecraft.getMinecraft();
  private final CrosshairArt art;
  private final Cached[] cache = {new Cached(), new Cached(), new Cached()};

  /** One profile's cached textures: the shape, and the outline around it (drawn without inversion). */
  static final class Cached {
    long key = Long.MIN_VALUE;
    DynamicTexture shapeTexture;
    DynamicTexture outlineTexture;
    ResourceLocation shape;
    ResourceLocation outline;
    int side;
    /** Where the canvas centre sits inside the texture, in pixels. */
    float centreX;
    float centreY;
  }

  CrosshairPainter(CrosshairArt art) {
    this.art = art;
  }

  // ---------------------------------------------------------------- profiles

  void drawProfile(CrosshairSet p, boolean lowHealth, float partialTicks) {
    ScaledResolution resolution = new ScaledResolution(mc);
    int scale = resolution.getScaleFactor();
    CrosshairArt.Picture picture = IMPORTED == p.shapeIndex() ? art.picture(p.slot) : null;
    int cells = picture != null ? picture.side : p.canvasCells();
    float cell = p.ownSize.get() ? p.size.get() : scale;
    if (!p.exact.get()) cell = Math.max(1F, Math.round(cell));

    // Put the canvas so its cells fall on whole screen pixels, as close to the exact centre as that allows.
    float canvasPx = cells * cell;
    float centreX = (float) Math.floor((mc.displayWidth - canvasPx) / 2F) + canvasPx / 2F;
    float centreY = (float) Math.floor((mc.displayHeight - canvasPx) / 2F) + canvasPx / 2F;
    float fracX = centreX - (float) Math.floor(centreX);
    float fracY = centreY - (float) Math.floor(centreY);

    Cached cached = cache[p.slot];
    long key = keyOf(p, picture != null ? art.version() : -1, cells, cell, fracX, fracY, lowHealth);
    if (cached.key != key || cached.shape == null) {
      build(cached, p, picture, cells, cell, fracX, fracY, lowHealth);
      cached.key = key;
    }

    int left = (int) Math.floor(centreX) - (int) Math.floor(cached.centreX);
    int top = (int) Math.floor(centreY) - (int) Math.floor(cached.centreY);
    float apart = bowSpread(p, cell, partialTicks);

    GlStateManager.enableBlend();
    GlStateManager.enableTexture2D();
    GlStateManager.disableAlpha();
    if (p.invert.get()) {
      GlStateManager.tryBlendFuncSeparate(ONE_MINUS_DST_COLOR, ONE_MINUS_SRC_COLOR, 1, 0);
      colour(p.invertTint.get());
    } else {
      GlStateManager.tryBlendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, 1, 0);
      GlStateManager.color(1F, 1F, 1F, 1F);
    }
    texturedPieces(cached, cached.shape, left, top, apart, scale);
    if (cached.outline != null) {
      GlStateManager.tryBlendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, 1, 0);
      GlStateManager.color(1F, 1F, 1F, 1F);
      texturedPieces(cached, cached.outline, left, top, apart, scale);
    }
    restore();
  }

  /** How far each quarter is pushed out along its axes, in screen pixels, while a bow is drawn. */
  private float bowSpread(CrosshairSet p, float cell, float partialTicks) {
    if (!p.bowSpread.get()) return 0F;
    EntityPlayerSP player = mc.thePlayer;
    if (player == null) return 0F;
    ItemStack using = player.getItemInUse();
    if (using == null || !(using.getItem() instanceof ItemBow)) return 0F;
    float charge = Math.min(1F, (player.getItemInUseDuration() + partialTicks) / 20F);
    return p.spread.get() * cell * (1F - charge) * 0.70710677F;
  }

  /** Draws the texture whole, or as four quarters split at the canvas centre and pushed apart diagonally. */
  private void texturedPieces(Cached pic, ResourceLocation texture, int left, int top, float apart, int scale) {
    mc.getTextureManager().bindTexture(texture);
    float side = pic.side;
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer buffer = tessellator.getWorldRenderer();
    buffer.begin(QUADS, DefaultVertexFormats.POSITION_TEX);
    if (apart <= 0F) {
      quad(buffer, left, top, left + side, top + side, 0F, 0F, 1F, 1F, scale);
    } else {
      float cx = pic.centreX, cy = pic.centreY;
      float u = cx / side, v = cy / side;
      quad(buffer, left - apart, top - apart, left + cx - apart, top + cy - apart, 0F, 0F, u, v, scale);
      quad(buffer, left + cx + apart, top - apart, left + side + apart, top + cy - apart, u, 0F, 1F, v, scale);
      quad(buffer, left - apart, top + cy + apart, left + cx - apart, top + side + apart, 0F, v, u, 1F, scale);
      quad(buffer, left + cx + apart, top + cy + apart, left + side + apart, top + side + apart, u, v, 1F, 1F, scale);
    }
    tessellator.draw();
  }

  /** A textured rectangle given in screen pixels, written in GUI units. */
  private static void quad(WorldRenderer buffer, float x0, float y0, float x1, float y1,
      float u0, float v0, float u1, float v1, int scale) {
    double l = x0 / (double) scale, t = y0 / (double) scale, r = x1 / (double) scale, b = y1 / (double) scale;
    buffer.pos(l, b, 0).tex(u0, v1).endVertex();
    buffer.pos(r, b, 0).tex(u1, v1).endVertex();
    buffer.pos(r, t, 0).tex(u1, v0).endVertex();
    buffer.pos(l, t, 0).tex(u0, v0).endVertex();
  }

  // ---------------------------------------------------------------- the game's own crosshair, restyled

  void drawPlain(boolean snap, boolean invert, int tint, int solid, boolean ownSize, float size) {
    ScaledResolution resolution = new ScaledResolution(mc);
    int scale = resolution.getScaleFactor();
    double pixel = ownSize ? size : scale;
    double centreX, centreY;
    if (snap) {
      centreX = mc.displayWidth / 2 + 0.5;
      centreY = mc.displayHeight / 2 + 0.5;
    } else {
      centreX = (resolution.getScaledWidth() / 2 + 0.5) * scale;
      centreY = (resolution.getScaledHeight() / 2 + 0.5) * scale;
    }
    // The icon's middle pixel is its eighth one (index 7), as the game draws it.
    double left = centreX - 7.5 * pixel, top = centreY - 7.5 * pixel;
    if (snap) {
      left = Math.round(left);
      top = Math.round(top);
    }
    mc.getTextureManager().bindTexture(Gui.icons);
    GlStateManager.enableBlend();
    GlStateManager.enableTexture2D();
    GlStateManager.enableAlpha();
    if (invert) {
      GlStateManager.tryBlendFuncSeparate(ONE_MINUS_DST_COLOR, ONE_MINUS_SRC_COLOR, 1, 0);
      colour(tint);
    } else {
      GlStateManager.tryBlendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, 1, 0);
      colour(solid);
    }
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer buffer = tessellator.getWorldRenderer();
    buffer.begin(QUADS, DefaultVertexFormats.POSITION_TEX);
    float end = ICON / 256F;
    quad(buffer, (float) left, (float) top, (float) (left + ICON * pixel), (float) (top + ICON * pixel), 0F, 0F, end, end, scale);
    tessellator.draw();
    restore();
  }

  // ---------------------------------------------------------------- shared

  /** What the game expects after its crosshair: white colour, normal blending, the icon sheet bound. */
  void restore() {
    GlStateManager.color(1F, 1F, 1F, 1F);
    GlStateManager.tryBlendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, 1, 0);
    GlStateManager.enableAlpha();
    mc.getTextureManager().bindTexture(Gui.icons);
  }

  private static void colour(int argb) {
    GlStateManager.color((argb >> 16 & 255) / 255F, (argb >> 8 & 255) / 255F, (argb & 255) / 255F, (argb >>> 24) / 255F);
  }

  /** Deletes a profile's old textures before new ones replace them, so none leak. */
  private void free(Cached pic) {
    if (pic.shape != null) mc.getTextureManager().deleteTexture(pic.shape);
    if (pic.outline != null) mc.getTextureManager().deleteTexture(pic.outline);
    pic.shape = null;
    pic.outline = null;
    pic.shapeTexture = null;
    pic.outlineTexture = null;
  }

  private static long keyOf(CrosshairSet p, int artVersion, int cells, float cell, float fracX, float fracY,
      boolean lowHealth) {
    long h = 0x9E3779B97F4A7C15L;
    h = mix(h, p.shapeIndex());
    h = mix(h, artVersion);
    h = mix(h, cells);
    h = mix(h, Float.floatToIntBits(cell));
    h = mix(h, Float.floatToIntBits(fracX));
    h = mix(h, Float.floatToIntBits(fracY));
    h = mix(h, p.colour.get());
    h = mix(h, Float.floatToIntBits(p.reach.get()));
    h = mix(h, Float.floatToIntBits(p.lineWidth.get()));
    h = mix(h, Float.floatToIntBits(p.gap.get()));
    h = mix(h, p.keepMiddle.get() ? 1 : 0);
    h = mix(h, Float.floatToIntBits(p.turn.get()));
    h = mix(h, p.border.get() ? 1 : 0);
    h = mix(h, Float.floatToIntBits(p.borderWidth.get()));
    h = mix(h, p.borderColour.get());
    h = mix(h, p.invert.get() ? 1 : 0);
    h = mix(h, lowHealth ? p.lowHealthColour.get() : 0x5A5A5A5A);
    h = mix(h, lowHealth ? 1 : 0);
    return h;
  }

  private static long mix(long h, int value) {
    h ^= value & 0xFFFFFFFFL;
    h *= 0xBF58476D1CE4E5B9L;
    return h ^ h >>> 31;
  }

  // ---------------------------------------------------------------- building pictures

  private void build(Cached pic, CrosshairSet p, CrosshairArt.Picture picture, int cells, float cell,
      float fracX, float fracY, boolean lowHealth) {
    int[] grid = picture != null ? picture.argb.clone() : CrosshairShapes.grid(p, cells);
    if (lowHealth) CrosshairShapes.overlay(grid, p.lowHealthColour.get());

    double angle = Math.toRadians(p.turn.get() % 360F);
    double cos = Math.cos(angle), sin = Math.sin(angle);
    boolean rightAngle = Math.abs(sin) < 1e-6 || Math.abs(cos) < 1e-6;
    if (rightAngle) {
      cos = Math.round(cos);
      sin = Math.round(sin);
    }
    double canvasPx = cells * (double) cell;
    double extent = rightAngle ? canvasPx : canvasPx * (Math.abs(cos) + Math.abs(sin));
    int outlinePx = p.border.get() && p.borderWidth.get() > 0F ? Math.max(1, Math.round(p.borderWidth.get() * cell)) : 0;
    int side = (int) Math.ceil(extent) + 2 * outlinePx + 4;
    float cx = side / 2 + fracX, cy = side / 2 + fracY;
    int samples = rightAngle && cell == (float) Math.floor(cell) ? 1 : 3;

    int[] shape = new int[side * side];
    float[] cover = new float[side * side];
    double half = cells / 2.0;
    for (int y = 0; y < side; y++) {
      for (int x = 0; x < side; x++) {
        float a = 0F, r = 0F, g = 0F, b = 0F;
        for (int sy = 0; sy < samples; sy++) {
          for (int sx = 0; sx < samples; sx++) {
            double px = x + (sx + 0.5) / samples - cx, py = y + (sy + 0.5) / samples - cy;
            // Undo the clockwise turn to find which canvas cell this sample shows.
            double ux = px * cos + py * sin, uy = -px * sin + py * cos;
            int gx = (int) Math.floor(ux / cell + half), gy = (int) Math.floor(uy / cell + half);
            if (gx < 0 || gy < 0 || gx >= cells || gy >= cells) continue;
            int c = grid[gy * cells + gx];
            float ca = (c >>> 24) / 255F;
            if (ca <= 0F) continue;
            a += ca;
            r += (c >> 16 & 255) * ca;
            g += (c >> 8 & 255) * ca;
            b += (c & 255) * ca;
          }
        }
        if (a <= 0F) continue;
        int i = y * side + x;
        float alpha = a / (samples * samples);
        cover[i] = alpha;
        float keep = p.invert.get() ? alpha : 1F; // the inverting blend reads colour only, so fold alpha in
        shape[i] = Math.round(alpha * 255F) << 24 | Math.round(r / a * keep) << 16 | Math.round(g / a * keep) << 8
            | Math.round(b / a * keep);
      }
    }

    free(pic);
    pic.side = side;
    pic.centreX = cx;
    pic.centreY = cy;
    pic.shapeTexture = upload(shape, side);
    pic.shape = mc.getTextureManager().getDynamicTextureLocation("luka_crosshair", pic.shapeTexture);
    if (outlinePx > 0) {
      int[] outline = CrosshairShapes.outline(cover, side, outlinePx, p.borderColour.get());
      pic.outlineTexture = upload(outline, side);
      pic.outline = mc.getTextureManager().getDynamicTextureLocation("luka_crosshair_outline", pic.outlineTexture);
    }
  }

  private static DynamicTexture upload(int[] pixels, int side) {
    DynamicTexture texture = new DynamicTexture(side, side);
    System.arraycopy(pixels, 0, texture.getTextureData(), 0, pixels.length);
    texture.updateDynamicTexture();
    return texture;
  }
}
