package dev.luka.compat;

import net.minecraft.client.renderer.Tessellator;

/**
 * Minecraft 1.8's chained vertex calls ({@code pos(..).tex(..).color(..).endVertex()}) on 1.7.10's Tessellator.
 * Drawing still goes through {@code Tessellator.instance.draw()}.
 */
public final class VertexBuffer {
  public static final VertexBuffer INSTANCE = new VertexBuffer();

  private double x, y, z;
  private double u, v;
  private boolean hasTex, hasColor;
  private int r, g, b, a;

  private VertexBuffer() {}

  public VertexBuffer begin(int mode, int format) {
    Tessellator.instance.startDrawing(mode);
    hasTex = false;
    hasColor = false;
    return this;
  }

  public VertexBuffer pos(double x, double y, double z) {
    this.x = x;
    this.y = y;
    this.z = z;
    return this;
  }

  public VertexBuffer tex(double u, double v) {
    this.u = u;
    this.v = v;
    hasTex = true;
    return this;
  }

  public VertexBuffer color(int r, int g, int b, int a) {
    this.r = r;
    this.g = g;
    this.b = b;
    this.a = a;
    hasColor = true;
    return this;
  }

  public VertexBuffer color(float r, float g, float b, float a) {
    return color((int) (r * 255F), (int) (g * 255F), (int) (b * 255F), (int) (a * 255F));
  }

  public void endVertex() {
    Tessellator tessellator = Tessellator.instance;
    if (hasColor) tessellator.setColorRGBA(r, g, b, a);
    if (hasTex) tessellator.setTextureUV(u, v);
    tessellator.addVertex(x, y, z);
  }

  public void setTranslation(double x, double y, double z) {
    Tessellator.instance.setTranslation(x, y, z);
  }

  /** Ends the current primitive and throws its vertices away (1.8's {@code finishDrawing} followed by no draw). */
  public void finishDrawing() {
    Compat.discardTessellator();
  }

  public void reset() { }
}
