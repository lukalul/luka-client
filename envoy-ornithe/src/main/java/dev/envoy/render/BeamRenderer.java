package dev.envoy.render;

import dev.envoy.module.beam.BeamState;
import dev.envoy.platform.RenderWorldLastEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

public final class BeamRenderer {
    private static final double HEIGHT = 256.0D;
    private static final Layer[] LAYERS = { new Layer(0.15D, 0.60F), new Layer(0.35D, 0.15F) };

    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft game = Minecraft.getMinecraft();
        if (BeamState.isEmpty() || game.theWorld == null || game.getRenderViewEntity() == null) return;
        configure(true);
        Tessellator batch = Tessellator.getInstance();
        WorldRenderer vertices = batch.getWorldRenderer();
        vertices.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        RenderManager camera = game.getRenderManager();
        for (BeamState.Beam beam : BeamState.all()) emit(vertices, beam, camera);
        batch.draw();
        configure(false);
    }

    private static void configure(boolean entering) {
        if (entering) {
            GlStateManager.disableTexture2D(); GlStateManager.disableLighting(); GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.depthMask(false); GlStateManager.disableDepth();
        } else {
            GlStateManager.enableDepth(); GlStateManager.depthMask(true);
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.disableBlend(); GlStateManager.enableCull(); GlStateManager.enableTexture2D();
            GlStateManager.color(1F, 1F, 1F, 1F);
        }
    }

    private static void emit(WorldRenderer out, BeamState.Beam beam, RenderManager camera) {
        double x = beam.x + 0.5D - camera.viewerPosX;
        double y = beam.y - camera.viewerPosY;
        double z = beam.z + 0.5D - camera.viewerPosZ;
        float red = channel(beam.argb, 16), green = channel(beam.argb, 8), blue = channel(beam.argb, 0);
        for (Layer layer : LAYERS) {
            verticalFace(out, x - layer.radius, z, x + layer.radius, z, y, red, green, blue, layer.alpha);
            verticalFace(out, x, z - layer.radius, x, z + layer.radius, y, red, green, blue, layer.alpha);
        }
    }

    private static void verticalFace(WorldRenderer out, double x0, double z0, double x1, double z1,
                                     double bottom, float red, float green, float blue, float alpha) {
        out.pos(x0, bottom, z0).color(red, green, blue, alpha).endVertex();
        out.pos(x1, bottom, z1).color(red, green, blue, alpha).endVertex();
        out.pos(x1, bottom + HEIGHT, z1).color(red, green, blue, alpha).endVertex();
        out.pos(x0, bottom + HEIGHT, z0).color(red, green, blue, alpha).endVertex();
    }

    private static float channel(int color, int shift) { return ((color >> shift) & 255) / 255F; }

    private static final class Layer {
        final double radius; final float alpha;
        Layer(double radius, float alpha) { this.radius = radius; this.alpha = alpha; }
    }
}
