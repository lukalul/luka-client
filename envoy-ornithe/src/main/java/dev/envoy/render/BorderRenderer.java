package dev.envoy.render;

import dev.envoy.module.border.BorderState;
import dev.envoy.platform.RenderWorldLastEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public final class BorderRenderer {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/misc/forcefield.png");
    private static final double WORLD_BOTTOM = 0D;
    private static final double WORLD_TOP = 256D;

    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft game = Minecraft.getMinecraft();
        if (BorderState.isEmpty() || game.theWorld == null || game.getRenderViewEntity() == null) return;
        RenderManager camera = game.getRenderManager();
        double reach = game.gameSettings.renderDistanceChunks * 16D;
        float drift = (Minecraft.getSystemTime() % 3000L) / 3000F;
        enter(game);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer out = tessellator.getWorldRenderer();
        for (BorderState.Border border : BorderState.all()) {
            tint(border.argb);
            out.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            emitPerimeter(out, border.bounds(), camera, reach, drift);
            tessellator.draw();
        }
        leave();
    }

    private static void emitPerimeter(WorldRenderer out, double[] bounds, RenderManager camera,
                                      double reach, float drift) {
        strip(out, Axis.X, bounds[0], bounds[2], bounds[1], camera, reach, drift);
        strip(out, Axis.X, bounds[0], bounds[2], bounds[3], camera, reach, drift);
        strip(out, Axis.Z, bounds[1], bounds[3], bounds[0], camera, reach, drift);
        strip(out, Axis.Z, bounds[1], bounds[3], bounds[2], camera, reach, drift);
    }

    private static void strip(WorldRenderer out, Axis axis, double start, double end, double fixed,
                              RenderManager camera, double reach, float drift) {
        double eyeAlong = axis == Axis.X ? camera.viewerPosX : camera.viewerPosZ;
        double low = Math.max(start, eyeAlong - reach);
        double high = Math.min(end, eyeAlong + reach);
        float textureOffset = 0F;
        for (double cursor = low; cursor < high; cursor += 1D) {
            double length = Math.min(1D, high - cursor);
            segment(out, axis, cursor, cursor + length, fixed, camera, drift + textureOffset,
                drift + textureOffset + (float) length * 0.5F, drift);
            textureOffset += 0.5F;
        }
    }

    private static void segment(WorldRenderer out, Axis axis, double a, double b, double fixed,
                                RenderManager camera, float u0, float u1, float v) {
        double ax = axis == Axis.X ? a - camera.viewerPosX : fixed - camera.viewerPosX;
        double az = axis == Axis.Z ? a - camera.viewerPosZ : fixed - camera.viewerPosZ;
        double bx = axis == Axis.X ? b - camera.viewerPosX : fixed - camera.viewerPosX;
        double bz = axis == Axis.Z ? b - camera.viewerPosZ : fixed - camera.viewerPosZ;
        double top = WORLD_TOP - camera.viewerPosY, bottom = WORLD_BOTTOM - camera.viewerPosY;
        out.pos(ax, top, az).tex(u0, v).endVertex();
        out.pos(bx, top, bz).tex(u1, v).endVertex();
        out.pos(bx, bottom, bz).tex(u1, v + 128F).endVertex();
        out.pos(ax, bottom, az).tex(u0, v + 128F).endVertex();
    }

    private static void enter(Minecraft game) {
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit); GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
        game.getTextureManager().bindTexture(TEXTURE); GlStateManager.depthMask(false);
        GlStateManager.doPolygonOffset(-3F, -3F); GlStateManager.enablePolygonOffset();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F); GlStateManager.enableAlpha(); GlStateManager.disableCull();
    }

    private static void leave() {
        GlStateManager.enableCull(); GlStateManager.doPolygonOffset(0F, 0F); GlStateManager.disablePolygonOffset();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableBlend(); GlStateManager.depthMask(true); GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private static void tint(int argb) {
        int alpha = (argb >>> 24) & 255;
        GlStateManager.color(((argb >> 16) & 255) / 255F, ((argb >> 8) & 255) / 255F,
            (argb & 255) / 255F, alpha == 0 ? 1F : alpha / 255F);
    }

    private enum Axis { X, Z }
}
