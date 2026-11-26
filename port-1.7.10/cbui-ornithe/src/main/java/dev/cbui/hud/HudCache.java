package dev.cbui.hud;

import dev.cbui.config.GlobalOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import dev.luka.compat.VertexBuffer;
import dev.luka.compat.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;

/**
 * Draws the luka HUD into an offscreen picture a fixed number of times per second and pastes that picture on every other frame.
 * At 2000 FPS the HUD boxes (text, item icons, rounded backgrounds) were about a tenth of the whole frame; pasting one texture is almost free.
 */
public final class HudCache {
  private static Framebuffer fbo;
  private static int width, height;
  private static long lastPaint;
  private static boolean broken;
  // Blending the whole screen every frame costs more than a typical HUD does. Only the tiles a box touched get pasted.
  private static final int TILE = 64;
  private static final float MARGIN = 4f;
  private static boolean[] tiles;
  private static int cols, rows;
  /** Set by anything that changes how the HUD looks at once (editor closed, layout loaded): the next frame repaints. */
  public static volatile boolean dirty = true;

  private HudCache() {}

  /** True when the HUD should go through the cache this frame. */
  public static boolean active() {
    int hz = GlobalOptions.hudRefresh;
    // not while SubFrame renders a replay (luka.rendering): every video frame must show the HUD of that very frame, and
    // the refresh interval is wall-clock time, so a fast render would reuse an older picture
    return !broken && hz > 0 && hz < 250 && OpenGlHelper.isFramebufferEnabled() && System.getProperty("luka.rendering") == null;
  }

  /** Paints the HUD with {@code paint} when it is due, then pastes the cached picture. Call where the HUD is normally drawn. */
  public static void draw(Runnable paint) {
    Minecraft mc = Minecraft.getMinecraft();
    int w = mc.displayWidth, h = mc.displayHeight;
    if (w <= 0 || h <= 0) return;
    try {
      if (fbo == null || w != width || h != height) {
        if (fbo != null) fbo.deleteFramebuffer();
        fbo = new Framebuffer(w, h, true);
        fbo.setFramebufferColor(0f, 0f, 0f, 0f);
        fbo.setFramebufferFilter(GL11.GL_NEAREST);
        width = w;
        height = h;
        cols = (w + TILE - 1) / TILE;
        rows = (h + TILE - 1) / TILE;
        tiles = new boolean[cols * rows];
        dirty = true;
      }
      long now = System.nanoTime();
      long interval = 1_000_000_000L / Math.max(1, GlobalOptions.hudRefresh);
      if (dirty || now - lastPaint >= interval) {
        dirty = false;
        lastPaint = now;
        java.util.Arrays.fill(tiles, false);
        fbo.framebufferClear();
        fbo.bindFramebuffer(true);
        try {
          paint.run();
        } finally {
          mc.getFramebuffer().bindFramebuffer(true);
        }
      }
      paste(mc);
    } catch (Throwable error) {
      broken = true; // fall back to drawing the HUD directly from now on
      dev.cbui.Cbui.reportOnce("HUD cache (drawing directly instead)", error);
      try { mc.getFramebuffer().bindFramebuffer(true); } catch (Throwable ignored) { }
      paint.run();
    }
  }

  /** A box was painted at these GUI coordinates: its tiles have to be pasted. */
  static void mark(float[] box, ScaledResolution r) {
    if (tiles == null) return;
    float perUnit = width / (float) r.getScaledWidth_double();
    int x0 = clamp((int) Math.floor((box[0] - MARGIN) * perUnit / TILE), cols);
    int y0 = clamp((int) Math.floor((box[1] - MARGIN) * perUnit / TILE), rows);
    int x1 = clamp((int) Math.floor((box[0] + box[2] + MARGIN) * perUnit / TILE), cols);
    int y1 = clamp((int) Math.floor((box[1] + box[3] + MARGIN) * perUnit / TILE), rows);
    for (int y = y0; y <= y1; y++)
      for (int x = x0; x <= x1; x++) tiles[y * cols + x] = true;
  }

  private static int clamp(int tile, int count) {
    return Math.max(0, Math.min(count - 1, tile));
  }

  /** The cached picture where boxes are, already premultiplied by its alpha. */
  private static void paste(Minecraft mc) {
    ScaledResolution r = dev.luka.compat.Compat.scaled(mc);
    double sw = r.getScaledWidth_double(), sh = r.getScaledHeight_double();
    GlStateManager.enableTexture2D();
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GlStateManager.disableAlpha();
    GlStateManager.disableDepth();
    GlStateManager.color(1f, 1f, 1f, 1f);
    fbo.bindFramebufferTexture();
    Tessellator tess = Tessellator.instance;
    VertexBuffer wr = dev.luka.compat.VertexBuffer.INSTANCE;
    wr.begin(7, DefaultVertexFormats.POSITION_TEX);
    for (int row = 0; row < rows; row++) {
      for (int col = 0; col < cols; col++) {
        if (!tiles[row * cols + col]) continue;
        int start = col;
        while (col + 1 < cols && tiles[row * cols + col + 1]) col++;
        // runs never overlap, so nothing is blended twice
        double u0 = Math.min(1.0, start * TILE / (double) width), u1 = Math.min(1.0, (col + 1) * TILE / (double) width);
        double v0 = Math.min(1.0, row * TILE / (double) height), v1 = Math.min(1.0, (row + 1) * TILE / (double) height);
        wr.pos(u0 * sw, v1 * sh, 0).tex(u0, 1 - v1).endVertex();
        wr.pos(u1 * sw, v1 * sh, 0).tex(u1, 1 - v1).endVertex();
        wr.pos(u1 * sw, v0 * sh, 0).tex(u1, 1 - v0).endVertex();
        wr.pos(u0 * sw, v0 * sh, 0).tex(u0, 1 - v0).endVertex();
      }
    }
    tess.draw();
    fbo.unbindFramebufferTexture();
    GlStateManager.enableDepth();
    GlStateManager.enableAlpha();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
  }
}
