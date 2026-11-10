package dev.envoy.render;

import dev.envoy.module.vignette.VignetteState;
import dev.envoy.platform.RenderGameOverlayEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public final class VignetteRenderer {
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        ResourceLocation image = VignetteState.texture();
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || image == null) return;
        Minecraft game = Minecraft.getMinecraft();
        ScaledResolution screen = new ScaledResolution(game, game.displayWidth, game.displayHeight);
        beginOverlay(VignetteState.opacity());
        game.getTextureManager().bindTexture(image);
        paint(screen.getScaledWidth(), screen.getScaledHeight());
        endOverlay();
    }

    private static void beginOverlay(float opacity) {
        GlStateManager.disableDepth(); GlStateManager.depthMask(false); GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableAlpha(); GlStateManager.color(1F, 1F, 1F, opacity);
    }

    private static void paint(int width, int height) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(GL11.GL_QUADS);
        corner(tessellator, 0, height, 0, 1); corner(tessellator, width, height, 1, 1);
        corner(tessellator, width, 0, 1, 0); corner(tessellator, 0, 0, 0, 0);
        tessellator.draw();
    }

    private static void corner(Tessellator tessellator, double x, double y, double u, double v) {
        tessellator.addVertexWithUV(x, y, -90D, u, v);
    }

    private static void endOverlay() {
        GlStateManager.depthMask(true); GlStateManager.enableDepth(); GlStateManager.enableAlpha();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }
}
