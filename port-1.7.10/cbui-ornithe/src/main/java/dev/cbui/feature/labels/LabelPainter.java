package dev.cbui.feature.labels;

import dev.cbui.social.RankIcon;
import dev.luka.compat.Compat;
import dev.luka.compat.GlStateManager;
import java.util.regex.Pattern;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

/** Draws one entity label in the card's style, in the same place and with the same billboard as vanilla. */
final class LabelPainter {
  /** Vanilla's label scale: 1.6 / 60 of a block per font pixel. */
  private static final float BASE_SCALE = 1.6f / 60f;
  /** How far vanilla lowers a sneaking entity's label, in font pixels. */
  private static final float SNEAK_DROP = 9.374999f;
  /** Vanilla's strength for the see-through copy of the text (alpha 0x20). */
  private static final int FAINT_ALPHA = 0x20;
  /** Colour codes, also when a server sent the section sign double-encoded ("Â§"); style codes are left alone. */
  private static final Pattern COLOUR_CODE = Pattern.compile("Â?§[0-9a-fA-F]");
  private static final int ROUND_STEPS = 6;

  private LabelPainter() { }

  static void paint(LabelsModule card, FontRenderer font, float viewPitch, float viewYaw, Entity owner, String raw,
      double x, double y, double z) {
    boolean sneaking = owner.isSneaking();
    String text = card.forceColour.get() ? COLOUR_CODE.matcher(raw).replaceAll("") : raw;
    float scale = BASE_SCALE * card.size.get();
    int width = font.getStringWidth(text);
    int half = width / 2;
    float textX = -width / 2;
    float lift = "deadmau5".equals(raw) ? -10f : 0f;
    boolean throughWalls = !card.walls.get() && !sneaking;
    int colour = card.nameColour.get();

    GlStateManager.pushMatrix();
    try {
      GlStateManager.translate((float) x, (float) y + owner.height + 0.5f, (float) z);
      GL11.glNormal3f(0f, 1f, 0f);
      GlStateManager.rotate(-viewYaw, 0f, 1f, 0f);
      GlStateManager.rotate(viewPitch, 1f, 0f, 0f);
      GlStateManager.scale(-scale, -scale, scale);
      GlStateManager.translate(0f, (sneaking ? SNEAK_DROP : 0f) - card.shift.get(), 0f);
      GlStateManager.disableLighting();
      GlStateManager.depthMask(false);
      if (throughWalls) GlStateManager.disableDepth();
      else GlStateManager.enableDepth();
      GlStateManager.enableBlend();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

      if (card.box.get()) {
        float padX = card.padSides.get(), padY = card.padEnds.get();
        float radius = card.rounded.get() ? card.roundness.get() : 0f;
        box(-half - 1 - padX, -1 + lift - padY, half + 1 + padX, 8 + lift + padY, radius, card.boxColour.get());
      }

      // faint copy first (seen through blocks unless they block labels), then the solid text on top
      Compat.drawString(font, text, textX, lift, faint(colour, card.box.get()), false);
      RankIcon.draw(owner, textX, lift, false);
      if (!sneaking) {
        GlStateManager.enableDepth();
        // still without depth writes: the shadow and the letters share one plane and must not fight over it
        Compat.drawString(font, text, textX, lift, colour, card.shadow.get());
        RankIcon.draw(owner, textX, lift, true);
      }
    } finally {
      GlStateManager.depthMask(true);
      GlStateManager.enableDepth();
      GlStateManager.enableLighting();
      GlStateManager.disableBlend();
      GlStateManager.color(1f, 1f, 1f, 1f);
      GlStateManager.popMatrix();
    }
  }

  /** The see-through copy: vanilla's faint strength, or up to half the text's strength when there is no box behind it. */
  private static int faint(int colour, boolean withBox) {
    int alpha = colour >>> 24;
    int faintAlpha = withBox ? FAINT_ALPHA : Math.max(FAINT_ALPHA, alpha / 2);
    return (faintAlpha << 24) | (colour & 0xFFFFFF);
  }

  /** A flat box in the label plane; corners rounded by {@code radius} (never more than half the box). */
  private static void box(float left, float top, float right, float bottom, float radius, int argb) {
    float r = Math.max(0f, Math.min(radius, Math.min((right - left) / 2f, (bottom - top) / 2f)));
    float red = (argb >> 16 & 255) / 255f, green = (argb >> 8 & 255) / 255f, blue = (argb & 255) / 255f, alpha = (argb >>> 24) / 255f;
    GlStateManager.disableTexture2D();
    Tessellator tessellator = Tessellator.instance;
    tessellator.startDrawingQuads();
    tessellator.setColorRGBA_F(red, green, blue, alpha);
    if (r <= 0f) {
      rect(tessellator, left, top, right, bottom);
    } else {
      rect(tessellator, left + r, top, right - r, bottom);
      rect(tessellator, left, top + r, left + r, bottom - r);
      rect(tessellator, right - r, top + r, right, bottom - r);
      corner(tessellator, left + r, top + r, r, 180);
      corner(tessellator, right - r, top + r, r, 270);
      corner(tessellator, right - r, bottom - r, r, 0);
      corner(tessellator, left + r, bottom - r, r, 90);
    }
    tessellator.draw();
    GlStateManager.enableTexture2D();
  }

  /** Quad wound the same way as vanilla's label box. */
  private static void rect(Tessellator tessellator, float x0, float y0, float x1, float y1) {
    tessellator.addVertex(x0, y0, 0);
    tessellator.addVertex(x0, y1, 0);
    tessellator.addVertex(x1, y1, 0);
    tessellator.addVertex(x1, y0, 0);
  }

  /** A quarter circle as thin triangles (quads with a repeated corner), wound like {@link #rect}. */
  private static void corner(Tessellator tessellator, float cx, float cy, float r, float startDegrees) {
    for (int i = 0; i < ROUND_STEPS; i++) {
      double a0 = Math.toRadians(startDegrees + 90.0 * i / ROUND_STEPS);
      double a1 = Math.toRadians(startDegrees + 90.0 * (i + 1) / ROUND_STEPS);
      float x0 = cx + (float) Math.cos(a0) * r, y0 = cy + (float) Math.sin(a0) * r;
      float x1 = cx + (float) Math.cos(a1) * r, y1 = cy + (float) Math.sin(a1) * r;
      // vanilla's box turns clockwise in these coordinates (negative signed area); keep every piece the same way
      boolean clockwise = (x0 - cx) * (y1 - cy) - (y0 - cy) * (x1 - cx) < 0;
      float ax = clockwise ? x0 : x1, ay = clockwise ? y0 : y1, bx = clockwise ? x1 : x0, by = clockwise ? y1 : y0;
      tessellator.addVertex(cx, cy, 0);
      tessellator.addVertex(ax, ay, 0);
      tessellator.addVertex(bx, by, 0);
      tessellator.addVertex(bx, by, 0);
    }
  }
}
