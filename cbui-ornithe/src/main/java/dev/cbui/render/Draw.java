package dev.cbui.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public final class Draw {
    public static void rect(float left, float top, float right, float bottom, int color) {
        Gui.drawRect((int)left, (int)top, (int)Math.ceil(right), (int)Math.ceil(bottom), color);
    }

    public static void rounded(float left, float top, float right, float bottom, float radius, int color) {
        float r = Math.max(0, Math.min(radius, Math.min((right-left)/2, (bottom-top)/2)));
        if (r < .75f) { rect(left, top, right, bottom, color); return; }
        // one convex triangle fan around the box centre: a single draw call, no per-vertex trigonometry
        int stride = r <= 2.5f ? 3 : r <= 5f ? 2 : 1;
        float cxl = left + r, cxr = right - r, cyt = top + r, cyb = bottom - r;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color((color>>>16&255)/255f, (color>>>8&255)/255f, (color&255)/255f, (color>>>24&255)/255f);
        if (listsOk) {
            try {
                // the shape depends only on size and radius: compiled once into a display list, replayed at (left, top)
                float w = right - left, h = bottom - top;
                PROBE.set(w, h, r);
                Integer id = LISTS.get(PROBE);
                if (id == null) {
                    int fresh = GL11.glGenLists(1);
                    GL11.glNewList(fresh, GL11.GL_COMPILE);
                    try { geometry(0, 0, w, h, r, stride); } finally { GL11.glEndList(); }
                    id = fresh;
                    if (LISTS.size() >= 256) {
                        for (Integer stale : LISTS.values()) GL11.glDeleteLists(stale, 1);
                        LISTS.clear();
                    }
                    LISTS.put(new ShapeKey(w, h, r), id);
                }
                GlStateManager.pushMatrix();
                GlStateManager.translate(left, top, 0);
                GL11.glCallList(id);
                GlStateManager.popMatrix();
                GlStateManager.enableTexture2D();
                GlStateManager.color(1,1,1,1);
                return;
            } catch (Throwable error) {
                listsOk = false;
                dev.cbui.Cbui.reportOnce("shape display lists (switched to immediate drawing)", error);
            }
        }
        geometry(left, top, right, bottom, r, stride);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1,1,1,1);
    }

    private static boolean listsOk = true;
    private static final ShapeKey PROBE = new ShapeKey(0, 0, 0);
    private static final java.util.HashMap<ShapeKey, Integer> LISTS = new java.util.HashMap<ShapeKey, Integer>();

    private static final class ShapeKey {
        float w, h, r;
        ShapeKey(float w, float h, float r) { set(w, h, r); }
        void set(float w, float h, float r) { this.w = w; this.h = h; this.r = r; }
        @Override public int hashCode() { return (Float.floatToIntBits(w) * 31 + Float.floatToIntBits(h)) * 31 + Float.floatToIntBits(r); }
        @Override public boolean equals(Object o) {
            if (!(o instanceof ShapeKey)) return false;
            ShapeKey k = (ShapeKey) o;
            return k.w == w && k.h == h && k.r == r;
        }
    }

    /** Emits and draws the fan for a rounded box (positions only; colour and blend state are set by the caller). */
    private static void geometry(float left, float top, float right, float bottom, float r, int stride) {
        float cxl = left + r, cxr = right - r, cyt = top + r, cyb = bottom - r;
        int n = 0;
        for (int k = 0; k <= 6; k += stride) { RING[n++] = cxl - COS[k]*r; RING[n++] = cyt - SIN[k]*r; }
        for (int k = 0; k <= 6; k += stride) { RING[n++] = cxr + SIN[k]*r; RING[n++] = cyt - COS[k]*r; }
        for (int k = 0; k <= 6; k += stride) { RING[n++] = cxr + COS[k]*r; RING[n++] = cyb + SIN[k]*r; }
        for (int k = 0; k <= 6; k += stride) { RING[n++] = cxl - SIN[k]*r; RING[n++] = cyb + COS[k]*r; }
        float cx = (left+right)/2, cy = (top+bottom)/2;
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        for (int i = 0; i < n; i += 2) {
            int j = (i + 2) % n;
            wr.pos(cx, cy, 0).endVertex();
            wr.pos(RING[j], RING[j+1], 0).endVertex();
            wr.pos(RING[i], RING[i+1], 0).endVertex();
            wr.pos(RING[i], RING[i+1], 0).endVertex();
        }
        Tessellator.getInstance().draw();
    }

    private static final float[] RING = new float[56];
    private static final float[] COS = new float[7], SIN = new float[7];
    static {
        for (int k = 0; k <= 6; k++) { COS[k] = (float) Math.cos(Math.toRadians(k * 15)); SIN[k] = (float) Math.sin(Math.toRadians(k * 15)); }
    }

    /** A whole texture drawn into the box at sub-pixel position, tinted with alpha (smooth, nothing snapped to whole pixels). */
    public static void texture(ResourceLocation texture, float x, float y, float w, float h, float alpha) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1, 1, 1, alpha);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + h, 0).tex(0, 1).endVertex();
        wr.pos(x + w, y + h, 0).tex(1, 1).endVertex();
        wr.pos(x + w, y, 0).tex(1, 0).endVertex();
        wr.pos(x, y, 0).tex(0, 0).endVertex();
        tessellator.draw();
        GlStateManager.color(1, 1, 1, 1);
    }

    /** A box filled with a horizontal gradient from {@code left} to {@code right} (ARGB). */
    public static void gradientH(float x1, float y1, float x2, float y2, int left, int right) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x2, y1, 0).color(right >>> 16 & 255, right >>> 8 & 255, right & 255, right >>> 24 & 255).endVertex();
        wr.pos(x1, y1, 0).color(left >>> 16 & 255, left >>> 8 & 255, left & 255, left >>> 24 & 255).endVertex();
        wr.pos(x1, y2, 0).color(left >>> 16 & 255, left >>> 8 & 255, left & 255, left >>> 24 & 255).endVertex();
        wr.pos(x2, y2, 0).color(right >>> 16 & 255, right >>> 8 & 255, right & 255, right >>> 24 & 255).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    public static int lerpColor(int from, int to, float t) {
        t = Math.max(0, Math.min(1, t));
        int a = (int)((from >>> 24 & 255) + ((to >>> 24 & 255) - (from >>> 24 & 255)) * t);
        int r = (int)((from >>> 16 & 255) + ((to >>> 16 & 255) - (from >>> 16 & 255)) * t);
        int g = (int)((from >>> 8 & 255) + ((to >>> 8 & 255) - (from >>> 8 & 255)) * t);
        int b = (int)((from & 255) + ((to & 255) - (from & 255)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static boolean inside(int mx,int my,float x,float y,float w,float h) { return mx>=x&&mx<x+w&&my>=y&&my<y+h; }
    private Draw() { }
}
