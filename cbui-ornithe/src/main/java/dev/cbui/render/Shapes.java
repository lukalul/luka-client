package dev.cbui.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/** Extra HUD shapes that {@link Draw} does not provide: a rounded outline (one batched draw). */
public final class Shapes {
  private static final int STEPS = 6; // segments per quarter circle
  private static final float[] COS = new float[STEPS + 1], SIN = new float[STEPS + 1];
  private static final float[] OUTER = new float[(STEPS + 1) * 8], INNER = new float[(STEPS + 1) * 8];

  static {
    for (int k = 0; k <= STEPS; k++) {
      double a = Math.toRadians(90.0 * k / STEPS);
      COS[k] = (float) Math.cos(a);
      SIN[k] = (float) Math.sin(a);
    }
  }

  /** GL cull state when the overlay started this frame; a glIsEnabled per box stalled the pipeline. */
  public static boolean overlayCull;

  private static boolean overlayStale = true;

  /** Marks a new overlay pass; the cull state is read only if an outline is actually drawn (a GL read makes the game wait for the driver). */
  public static void captureOverlayState() { overlayStale = true; }

  /** A {@code thickness}-wide border just inside the rounded box. */
  public static void roundedOutline(
      float left, float top, float right, float bottom, float radius, float thickness, int color) {
    if (right - left <= 0 || bottom - top <= 0 || thickness <= 0) return;
    float r = Math.max(0, Math.min(radius, Math.min((right - left) / 2, (bottom - top) / 2)));
    float ri = Math.max(0, r - thickness);
    int n = ring(OUTER, left, top, right, bottom, r);
    ring(INNER, left + thickness, top + thickness, right - thickness, bottom - thickness, ri);
    if (overlayStale) {
      overlayStale = false;
      overlayCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    }
    boolean cull = overlayCull;
    GlStateManager.disableCull();
    GlStateManager.disableTexture2D();
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GlStateManager.color(
        (color >>> 16 & 255) / 255f, (color >>> 8 & 255) / 255f, (color & 255) / 255f, (color >>> 24 & 255) / 255f);
    WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
    wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
    for (int i = 0; i < n; i += 2) {
      int j = (i + 2) % n;
      wr.pos(OUTER[i], OUTER[i + 1], 0).endVertex();
      wr.pos(INNER[i], INNER[i + 1], 0).endVertex();
      wr.pos(INNER[j], INNER[j + 1], 0).endVertex();
      wr.pos(OUTER[j], OUTER[j + 1], 0).endVertex();
    }
    Tessellator.getInstance().draw();
    GlStateManager.enableTexture2D();
    GlStateManager.color(1, 1, 1, 1);
    if (cull) GlStateManager.enableCull();
  }

  /** Clockwise ring of a rounded box starting at the left end of the top-left corner. */
  private static int ring(float[] out, float l, float t, float r, float b, float rad) {
    int n = 0;
    float cxl = l + rad, cxr = r - rad, cyt = t + rad, cyb = b - rad;
    for (int k = 0; k <= STEPS; k++) { out[n++] = cxl - COS[k] * rad; out[n++] = cyt - SIN[k] * rad; }
    for (int k = 0; k <= STEPS; k++) { out[n++] = cxr + SIN[k] * rad; out[n++] = cyt - COS[k] * rad; }
    for (int k = 0; k <= STEPS; k++) { out[n++] = cxr + COS[k] * rad; out[n++] = cyb + SIN[k] * rad; }
    for (int k = 0; k <= STEPS; k++) { out[n++] = cxl - SIN[k] * rad; out[n++] = cyb + COS[k] * rad; }
    return n;
  }

  private Shapes() {}
}
