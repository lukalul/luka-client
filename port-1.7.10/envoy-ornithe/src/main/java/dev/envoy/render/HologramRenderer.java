package dev.envoy.render;

import dev.envoy.module.hologram.HologramState;
import dev.envoy.module.nametag.AdventureText.Run;
import dev.envoy.platform.RenderWorldLastEvent;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import org.lwjgl.opengl.GL11;

public final class HologramRenderer {
    private static final double LIMIT_SQUARED = 128D * 128D;
    private static final float SCALE = 0.02666667F;

    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft game = Minecraft.getMinecraft();
        if (HologramState.isEmpty() || game.theWorld == null || game.renderViewEntity == null) return;
        RenderManager camera = RenderManager.instance;
        for (HologramState.Hologram hologram : HologramState.all()) {
            RelativePoint point = RelativePoint.from(hologram, camera);
            if (point.distanceSquared() <= LIMIT_SQUARED) paint(game.fontRendererObj, camera, hologram, point);
        }
    }

    private static void paint(FontRenderer font, RenderManager camera, HologramState.Hologram hologram,
                              RelativePoint point) {
        enterTextSpace(camera, point, hologram.showThroughWalls);
        int stride = font.FONT_HEIGHT + 1;
        int y = -(hologram.lines.size() * stride) / 2;
        for (List<Run> line : hologram.lines) {
            drawLine(font, line, y, hologram.showBackground, hologram.showShadow);
            y += stride;
        }
        leaveTextSpace(hologram.showThroughWalls);
    }

    private static void drawLine(FontRenderer font, List<Run> runs, int y, boolean background, boolean shadow) {
        int width = 0;
        for (Run run : runs) width += font.getStringWidth(run.display);
        int x = -width / 2;
        if (background) rectangle(x - 1, y - 1, width + 2, font.FONT_HEIGHT + 1);
        for (Run run : runs) {
            int color = 0xFF000000 | run.rgb;
            if (shadow) font.drawStringWithShadow(run.display, x, y, color);
            else font.drawString(run.display, x, y, run.rgb);
            x += font.getStringWidth(run.display);
        }
    }

    private static void enterTextSpace(RenderManager camera, RelativePoint point, boolean throughWalls) {
        GL11.glPushMatrix(); GL11.glTranslated(point.x, point.y, point.z); GL11.glNormal3f(0F, 1F, 0F);
        GL11.glRotatef(-camera.playerViewY, 0F, 1F, 0F); GL11.glRotatef(camera.playerViewX, 1F, 0F, 0F);
        GL11.glScalef(-SCALE, -SCALE, SCALE); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (throughWalls) { GL11.glEnable(GL11.GL_DEPTH_TEST); GlStateManager.depthFunc(GL11.GL_ALWAYS); }
    }

    private static void leaveTextSpace(boolean throughWalls) {
        if (throughWalls) GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true); GL11.glDisable(GL11.GL_BLEND);
        GlStateManager.color(1F, 1F, 1F, 1F); GL11.glPopMatrix();
    }

    private static void rectangle(int x, int y, int width, int height) {
        GlStateManager.disableTexture2D(); GlStateManager.color(0F, 0F, 0F, 0.25F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y + height); GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x + width, y); GL11.glVertex2f(x, y); GL11.glEnd();
        GlStateManager.enableTexture2D(); GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private static final class RelativePoint {
        final double x, y, z;
        RelativePoint(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        static RelativePoint from(HologramState.Hologram value, RenderManager camera) {
            return new RelativePoint(value.x - camera.viewerPosX, value.y - camera.viewerPosY,
                value.z - camera.viewerPosZ);
        }
        double distanceSquared() { return x * x + y * y + z * z; }
    }
}
