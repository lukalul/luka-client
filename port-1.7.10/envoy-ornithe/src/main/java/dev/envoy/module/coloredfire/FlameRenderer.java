package dev.envoy.module.coloredfire;

import org.lwjgl.opengl.GL11;

import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;

/**
 * Draws tinted fire with the grey fire texture: the same quads as the game's own fire on a burning entity and the
 * first-person fire overlay, only the texture and the colour differ. Untinted fire never comes here.
 */
public final class FlameRenderer {
    private FlameRenderer() { }

    /** Fire on a burning entity; false when the grey texture is unavailable. */
    public static boolean drawOnEntity(Entity entity, double x, double y, double z, int rgb) {
        if (!FlameTexture.bind()) return false;
        GlStateManager.disableLighting();
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, (float) z);
        float scale = entity.width * 1.4F;
        GlStateManager.scale(scale, scale, scale);
        float halfWidth = 0.5F;
        float heightLeft = entity.height / scale;
        float bottom = (float) (entity.posY - entity.boundingBox.minY);
        GlStateManager.rotate(-RenderManager.instance.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(0.0F, 0.0F, -0.3F + (float) ((int) heightLeft) * 0.02F);
        tint(rgb, 1.0F);
        float[] tops = {FlameTexture.frameTop(0), FlameTexture.frameTop(1)};
        float frame = FlameTexture.frameHeight();
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        float depth = 0.0F;
        for (int quad = 0; heightLeft > 0.0F; quad++) {
            int layer = quad % 2;
            boolean mirrored = quad / 2 % 2 == 0;
            float left = mirrored ? FlameTexture.maxU(layer) : FlameTexture.minU(layer);
            float right = mirrored ? FlameTexture.minU(layer) : FlameTexture.maxU(layer);
            float top = tops[layer], low = top + frame;
            tessellator.addVertexWithUV(halfWidth, -bottom, depth, right, low);
            tessellator.addVertexWithUV(-halfWidth, -bottom, depth, left, low);
            tessellator.addVertexWithUV(-halfWidth, 1.4F - bottom, depth, left, top);
            tessellator.addVertexWithUV(halfWidth, 1.4F - bottom, depth, right, top);
            heightLeft -= 0.45F;
            bottom -= 0.45F;
            halfWidth *= 0.9F;
            depth += 0.03F;
        }
        tessellator.draw();
        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        return true;
    }

    /** The fire overlay in first person; false when the grey texture is unavailable. */
    public static boolean drawOverlay(int rgb) {
        if (!FlameTexture.bind()) return false;
        tint(rgb, 0.9F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        float top = FlameTexture.frameTop(1), low = top + FlameTexture.frameHeight();
        float left = FlameTexture.minU(1), right = FlameTexture.maxU(1);
        Tessellator tessellator = Tessellator.instance;
        for (int side = 0; side < 2; side++) {
            float direction = side * 2 - 1;
            GlStateManager.pushMatrix();
            GlStateManager.translate(-direction * 0.24F, -0.3F, 0.0F);
            GlStateManager.rotate(direction * 10.0F, 0.0F, 1.0F, 0.0F);
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(-0.5F, -0.5F, -0.5F, right, low);
            tessellator.addVertexWithUV(0.5F, -0.5F, -0.5F, left, low);
            tessellator.addVertexWithUV(0.5F, 0.5F, -0.5F, left, top);
            tessellator.addVertexWithUV(-0.5F, 0.5F, -0.5F, right, top);
            tessellator.draw();
            GlStateManager.popMatrix();
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        return true;
    }

    private static void tint(int rgb, float alpha) {
        GlStateManager.color((rgb >> 16 & 255) / 255.0F, (rgb >> 8 & 255) / 255.0F, (rgb & 255) / 255.0F, alpha);
    }
}
