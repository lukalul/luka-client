package dev.hcfopt;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import org.lwjgl.opengl.GL11;

/**
 * Fancy clouds as one cached mesh instead of 64 tiles tessellated twice every frame. The mesh is built around the
 * origin; the caller supplies the sub-cell offset and the height through the matrix, so it only changes with the
 * texture cell, the colour bytes, which horizontal faces are visible and the cloud shape. It is a VertexBuffer when
 * the game draws with VBOs and a display list otherwise. On any failure the cache turns itself off and the game's
 * own clouds are drawn.
 */
public final class CloudMeshCache {
    /** Kill switch: false leaves vanilla cloud drawing alone. Also set by -Dluka.cloudcache=false. */
    public static boolean enabled = !"false".equalsIgnoreCase(System.getProperty("luka.cloudcache"));

    private static final float UV_STEP = 0.00390625F;
    private static final float INSET = 9.765625E-4F;
    private static final float THICKNESS = 4.0F;
    private static final float ALPHA = 0.8F;
    private static final int CELL = 8;
    private static final int REACH = 4;
    private static final int STRIDE = 28;

    private static VertexBuffer buffer;
    private static int list;
    private static boolean built;
    private static boolean keyVbo;
    private static int keyCellX;
    private static int keyCellZ;
    private static int keyBottomColour;
    private static int keyTopColour;
    private static int keyXColour;
    private static int keyZColour;
    private static boolean keyBottom;
    private static boolean keyTop;
    private static int keyShape;

    private CloudMeshCache() { }

    /**
     * Argentum draws clouds with its own renderer and Cera changes their colours; with either loaded the game's
     * cloud code is theirs to change, so the cache stays out of the way.
     */
    private static final boolean OTHER_CLOUDS = otherClouds();

    private static boolean otherClouds() {
        try {
            FabricLoader loader = FabricLoader.getInstance();
            return loader.isModLoaded("argentum") || loader.isModLoaded("cera");
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean active() {
        if (!enabled || OTHER_CLOUDS) return false;
        HcfOptConfig config = HcfOptMod.config();
        return config.masterEnabled && config.cloudMeshCache;
    }

    /**
     * Makes the mesh for these inputs current, rebuilding it when one of them changed. False after a failure; the
     * cache is then off and the caller draws the game's clouds. Changes no GL state that the draw depends on.
     */
    public static boolean prepare(int cellX, int cellZ, float red, float green, float blue, boolean bottom, boolean top,
                                  boolean vbo) {
        if (!enabled) return false;
        int bottomColour = pack(red * 0.7F, green * 0.7F, blue * 0.7F);
        int topColour = pack(red, green, blue);
        int xColour = pack(red * 0.9F, green * 0.9F, blue * 0.9F);
        int zColour = pack(red * 0.8F, green * 0.8F, blue * 0.8F);
        // inside the layer both sets of faces show and the inner faces can be seen: the game's mesh there
        boolean[] grid = bottom && top ? null : CloudShape.grid();
        int shape = grid == null ? -1 : CloudShape.generation();
        if (built && vbo == keyVbo && cellX == keyCellX && cellZ == keyCellZ && bottom == keyBottom && top == keyTop
                && bottomColour == keyBottomColour && topColour == keyTopColour
                && xColour == keyXColour && zColour == keyZColour && shape == keyShape) {
            return true;
        }

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        boolean listOpen = false;
        boolean drawing = false;
        try {
            built = false;
            if (vbo) {
                if (list > 0) {
                    GL11.glDeleteLists(list, 1);
                    list = 0;
                }
                if (buffer == null) buffer = new VertexBuffer(DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
            } else {
                if (buffer != null) {
                    buffer.deleteGlBuffers();
                    buffer = null;
                }
                if (list == 0) {
                    list = GL11.glGenLists(1);
                    if (list <= 0) throw new IllegalStateException("No display list available");
                }
                GL11.glNewList(list, GL11.GL_COMPILE);
                listOpen = true;
            }
            renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
            drawing = true;
            Mesh mesh = new Mesh(renderer);
            if (grid != null) {
                tessellateShaped(mesh, grid, cellX, cellZ, red, green, blue, bottom, top);
            } else {
                tessellate(mesh, cellX, cellZ, red, green, blue, bottom, top);
            }
            drawing = false;
            if (vbo) {
                renderer.finishDrawing();
                renderer.reset();
                buffer.bufferData(renderer.getByteBuffer());
            } else {
                tessellator.draw();
                listOpen = false;
                GL11.glEndList();
            }
        } catch (Throwable t) {
            if (drawing) {
                try {
                    renderer.finishDrawing();
                    renderer.reset();
                } catch (Throwable ignored) {
                }
            }
            if (listOpen) {
                try {
                    GL11.glEndList();
                } catch (Throwable ignored) {
                }
            }
            fail(t);
            return false;
        }

        keyVbo = vbo;
        keyCellX = cellX;
        keyCellZ = cellZ;
        keyBottom = bottom;
        keyTop = top;
        keyBottomColour = bottomColour;
        keyTopColour = topColour;
        keyXColour = xColour;
        keyZColour = zColour;
        keyShape = shape;
        built = true;
        return true;
    }

    /** Draws the current mesh with the GL state the caller set, the way the game draws its own vertex buffers. */
    public static void draw() {
        if (!built) return;
        if (!keyVbo) {
            GlStateManager.callList(list);
            return;
        }
        buffer.bindBuffer();
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glVertexPointer(3, GL11.GL_FLOAT, STRIDE, 0L);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glTexCoordPointer(2, GL11.GL_FLOAT, STRIDE, 12L);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, STRIDE, 20L);
        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glNormalPointer(GL11.GL_BYTE, STRIDE, 24L);
        buffer.drawArrays(GL11.GL_QUADS);
        buffer.unbindBuffer();
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
    }

    /** Turns the cache off for the rest of the session and frees what it held. */
    public static void fail(Throwable t) {
        enabled = false;
        built = false;
        int old = list;
        list = 0;
        if (old > 0) {
            try {
                GL11.glDeleteLists(old, 1);
            } catch (Throwable ignored) {
            }
        }
        VertexBuffer oldBuffer = buffer;
        buffer = null;
        if (oldBuffer != null) {
            try {
                oldBuffer.deleteGlBuffers();
            } catch (Throwable ignored) {
            }
        }
        HcfOptMod.problemOnce("cloud-mesh", t);
    }

    /** 1.7.10's Tessellator calls on top of 1.8.9's WorldRenderer: colour and normal stay set until changed. */
    private static final class Mesh {
        private final WorldRenderer renderer;
        private float red, green, blue, alpha;
        private float nx, ny, nz;

        Mesh(WorldRenderer renderer) {
            this.renderer = renderer;
        }

        void setColorRGBA_F(float red, float green, float blue, float alpha) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
        }

        void setNormal(float x, float y, float z) {
            nx = x;
            ny = y;
            nz = z;
        }

        void addVertexWithUV(double x, double y, double z, double u, double v) {
            renderer.pos(x, y, z).tex(u, v).color(red, green, blue, alpha).normal(nx, ny, nz).endVertex();
        }
    }

    private static void tessellate(Mesh t, int cellX, int cellZ, float red, float green, float blue,
                                   boolean bottom, boolean top) {
        float uBase = (float) cellX * UV_STEP;
        float vBase = (float) cellZ * UV_STEP;
        float yLow = 0.0F;
        float yHigh = THICKNESS;
        float yTop = THICKNESS - INSET;

        for (int gx = -REACH + 1; gx <= REACH; gx++) {
            for (int gz = -REACH + 1; gz <= REACH; gz++) {
                float x0 = (float) (gx * CELL);
                float z0 = (float) (gz * CELL);
                float x1 = x0 + (float) CELL;
                float z1 = z0 + (float) CELL;
                float u0 = (x0 + 0.0F) * UV_STEP + uBase;
                float u1 = (x0 + (float) CELL) * UV_STEP + uBase;
                float v0 = (z0 + 0.0F) * UV_STEP + vBase;
                float v1 = (z0 + (float) CELL) * UV_STEP + vBase;

                if (bottom) {
                    t.setColorRGBA_F(red * 0.7F, green * 0.7F, blue * 0.7F, ALPHA);
                    t.setNormal(0.0F, -1.0F, 0.0F);
                    t.addVertexWithUV(x0, yLow, z1, u0, v1);
                    t.addVertexWithUV(x1, yLow, z1, u1, v1);
                    t.addVertexWithUV(x1, yLow, z0, u1, v0);
                    t.addVertexWithUV(x0, yLow, z0, u0, v0);
                }

                if (top) {
                    t.setColorRGBA_F(red, green, blue, ALPHA);
                    t.setNormal(0.0F, 1.0F, 0.0F);
                    t.addVertexWithUV(x0, yTop, z1, u0, v1);
                    t.addVertexWithUV(x1, yTop, z1, u1, v1);
                    t.addVertexWithUV(x1, yTop, z0, u1, v0);
                    t.addVertexWithUV(x0, yTop, z0, u0, v0);
                }

                t.setColorRGBA_F(red * 0.9F, green * 0.9F, blue * 0.9F, ALPHA);

                if (gx > -1) {
                    t.setNormal(-1.0F, 0.0F, 0.0F);
                    for (int j = 0; j < CELL; j++) {
                        float x = x0 + (float) j + 0.0F;
                        float u = (x0 + (float) j + 0.5F) * UV_STEP + uBase;
                        t.addVertexWithUV(x, yLow, z1, u, v1);
                        t.addVertexWithUV(x, yHigh, z1, u, v1);
                        t.addVertexWithUV(x, yHigh, z0, u, v0);
                        t.addVertexWithUV(x, yLow, z0, u, v0);
                    }
                }

                if (gx <= 1) {
                    t.setNormal(1.0F, 0.0F, 0.0F);
                    for (int j = 0; j < CELL; j++) {
                        float x = x0 + (float) j + 1.0F - INSET;
                        float u = (x0 + (float) j + 0.5F) * UV_STEP + uBase;
                        t.addVertexWithUV(x, yLow, z1, u, v1);
                        t.addVertexWithUV(x, yHigh, z1, u, v1);
                        t.addVertexWithUV(x, yHigh, z0, u, v0);
                        t.addVertexWithUV(x, yLow, z0, u, v0);
                    }
                }

                t.setColorRGBA_F(red * 0.8F, green * 0.8F, blue * 0.8F, ALPHA);

                if (gz > -1) {
                    t.setNormal(0.0F, 0.0F, -1.0F);
                    for (int j = 0; j < CELL; j++) {
                        float z = z0 + (float) j + 0.0F;
                        float v = (z0 + (float) j + 0.5F) * UV_STEP + vBase;
                        t.addVertexWithUV(x0, yHigh, z, u0, v);
                        t.addVertexWithUV(x1, yHigh, z, u1, v);
                        t.addVertexWithUV(x1, yLow, z, u1, v);
                        t.addVertexWithUV(x0, yLow, z, u0, v);
                    }
                }

                if (gz <= 1) {
                    t.setNormal(0.0F, 0.0F, 1.0F);
                    for (int j = 0; j < CELL; j++) {
                        float z = z0 + (float) j + 1.0F - INSET;
                        float v = (z0 + (float) j + 0.5F) * UV_STEP + vBase;
                        t.addVertexWithUV(x0, yHigh, z, u0, v);
                        t.addVertexWithUV(x1, yHigh, z, u1, v);
                        t.addVertexWithUV(x1, yLow, z, u1, v);
                        t.addVertexWithUV(x0, yLow, z, u0, v);
                    }
                }
            }
        }
    }

    /**
     * The same faces as {@link #tessellate}, cut down to the ones that can show: tops and bottoms only over cloud
     * cells, sides only where a cloud cell borders an empty one. Seen from outside the layer the picture is the
     * game's: every face left out is either fully transparent (thrown away by the alpha test) or lies between two
     * cloud cells, behind the outer face that the depth pass keeps. Runs of cells are merged into one quad.
     */
    private static void tessellateShaped(Mesh t, boolean[] grid, int cellX, int cellZ, float red, float green,
                                         float blue, boolean bottom, boolean top) {
        float uBase = (float) cellX * UV_STEP;
        float vBase = (float) cellZ * UV_STEP;
        float yLow = 0.0F;
        float yHigh = THICKNESS;
        float yTop = THICKNESS - INSET;
        int lo = (-REACH + 1) * CELL;
        int hi = (REACH + 1) * CELL;

        if (bottom || top) {
            for (int z = lo; z < hi; z++) {
                int x = lo;
                while (x < hi) {
                    if (!cloud(grid, cellX, cellZ, x, z)) { x++; continue; }
                    int end = x + 1;
                    while (end < hi && cloud(grid, cellX, cellZ, end, z)) end++;
                    float x0 = x, x1 = end, z0 = z, z1 = z + 1;
                    float u0 = x0 * UV_STEP + uBase, u1 = x1 * UV_STEP + uBase;
                    float v0 = z0 * UV_STEP + vBase, v1 = z1 * UV_STEP + vBase;
                    if (bottom) {
                        t.setColorRGBA_F(red * 0.7F, green * 0.7F, blue * 0.7F, ALPHA);
                        t.setNormal(0.0F, -1.0F, 0.0F);
                        t.addVertexWithUV(x0, yLow, z1, u0, v1);
                        t.addVertexWithUV(x1, yLow, z1, u1, v1);
                        t.addVertexWithUV(x1, yLow, z0, u1, v0);
                        t.addVertexWithUV(x0, yLow, z0, u0, v0);
                    }
                    if (top) {
                        t.setColorRGBA_F(red, green, blue, ALPHA);
                        t.setNormal(0.0F, 1.0F, 0.0F);
                        t.addVertexWithUV(x0, yTop, z1, u0, v1);
                        t.addVertexWithUV(x1, yTop, z1, u1, v1);
                        t.addVertexWithUV(x1, yTop, z0, u1, v0);
                        t.addVertexWithUV(x0, yTop, z0, u0, v0);
                    }
                    x = end;
                }
            }
        }

        // faces across x: one line per cell column; the game draws the west faces only east of the viewer's
        // tile row (gx > -1) and the east faces only up to gx <= 1, so does this
        t.setColorRGBA_F(red * 0.9F, green * 0.9F, blue * 0.9F, ALPHA);
        for (int x = lo; x < hi; x++) {
            int gx = Math.floorDiv(x, CELL);
            for (int side = 0; side < 2; side++) {
                boolean west = side == 0;
                if (west ? gx <= -1 : gx > 1) continue;
                int dx = west ? -1 : 1;
                float px = west ? (float) x : (float) x + 1.0F - INSET;
                float u = ((float) x + 0.5F) * UV_STEP + uBase;
                t.setNormal(west ? -1.0F : 1.0F, 0.0F, 0.0F);
                int z = lo;
                while (z < hi) {
                    if (!edge(grid, cellX, cellZ, x, z, dx, 0, lo, hi)) { z++; continue; }
                    int end = z + 1;
                    while (end < hi && edge(grid, cellX, cellZ, x, end, dx, 0, lo, hi)) end++;
                    float z0 = z, z1 = end;
                    float v0 = z0 * UV_STEP + vBase, v1 = z1 * UV_STEP + vBase;
                    t.addVertexWithUV(px, yLow, z1, u, v1);
                    t.addVertexWithUV(px, yHigh, z1, u, v1);
                    t.addVertexWithUV(px, yHigh, z0, u, v0);
                    t.addVertexWithUV(px, yLow, z0, u, v0);
                    z = end;
                }
            }
        }

        // faces across z, the same way
        t.setColorRGBA_F(red * 0.8F, green * 0.8F, blue * 0.8F, ALPHA);
        for (int z = lo; z < hi; z++) {
            int gz = Math.floorDiv(z, CELL);
            for (int side = 0; side < 2; side++) {
                boolean north = side == 0;
                if (north ? gz <= -1 : gz > 1) continue;
                int dz = north ? -1 : 1;
                float pz = north ? (float) z : (float) z + 1.0F - INSET;
                float v = ((float) z + 0.5F) * UV_STEP + vBase;
                t.setNormal(0.0F, 0.0F, north ? -1.0F : 1.0F);
                int x = lo;
                while (x < hi) {
                    if (!edge(grid, cellX, cellZ, x, z, 0, dz, lo, hi)) { x++; continue; }
                    int end = x + 1;
                    while (end < hi && edge(grid, cellX, cellZ, end, z, 0, dz, lo, hi)) end++;
                    float x0 = x, x1 = end;
                    float u0 = x0 * UV_STEP + uBase, u1 = x1 * UV_STEP + uBase;
                    t.addVertexWithUV(x0, yHigh, pz, u0, v);
                    t.addVertexWithUV(x1, yHigh, pz, u1, v);
                    t.addVertexWithUV(x1, yLow, pz, u1, v);
                    t.addVertexWithUV(x0, yLow, pz, u0, v);
                    x = end;
                }
            }
        }
    }

    private static boolean cloud(boolean[] grid, int cellX, int cellZ, int x, int z) {
        return grid[((cellX + x) & (CloudShape.SIZE - 1)) + ((cellZ + z) & (CloudShape.SIZE - 1)) * CloudShape.SIZE];
    }

    /** A cloud cell whose neighbour in (dx, dz) is empty or outside the drawn area: its face there can show. */
    private static boolean edge(boolean[] grid, int cellX, int cellZ, int x, int z, int dx, int dz, int lo, int hi) {
        if (!cloud(grid, cellX, cellZ, x, z)) return false;
        int nx = x + dx, nz = z + dz;
        if (nx < lo || nx >= hi || nz < lo || nz >= hi) return true;
        return !cloud(grid, cellX, cellZ, nx, nz);
    }

    /** The colour bytes the Tessellator stores for these components; equal bytes mean an identical mesh. */
    private static int pack(float red, float green, float blue) {
        return channel(red) << 16 | channel(green) << 8 | channel(blue);
    }

    private static int channel(float value) {
        int byteValue = (int) (value * 255.0F);
        return byteValue > 255 ? 255 : (byteValue < 0 ? 0 : byteValue);
    }
}
