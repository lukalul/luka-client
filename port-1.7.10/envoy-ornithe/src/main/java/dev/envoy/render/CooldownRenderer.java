package dev.envoy.render;

import java.util.List;

import org.lwjgl.opengl.GL11;

import dev.envoy.EnvoyConfig;
import dev.envoy.module.cooldown.CooldownState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import dev.envoy.platform.RenderGameOverlayEvent;

/**
 * Draws the Apollo cooldowns in the HUD: a "track" ring ({@code circleEnd})
 * covered by a progress arc ({@code circleStart}) proportional to the remaining time (which empties
 * out as the animation), edge rings ({@code circleEdge}), the item icon (with glint) in the center,
 * and the remaining time as text ({@code textColor}).
 *
 * <p>Layout: ~34px cell, item at 1.4×, ring
 * ~11.5 to 15.</p>
 */
public final class CooldownRenderer {

    private static final int SLOT_SPACING = 34;
    private static final float RADIUS_OUTER = 15.0F;
    private static final float RADIUS_INNER = 11.5F;
    private static final float ITEM_SCALE = 1.4F;
    private static final int SEGMENTS = 48;

    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }

        List<CooldownState.Entry> cooldowns = CooldownState.active();
        if (cooldowns.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int anchorX = Math.round(EnvoyConfig.cooldownX * resolution.getScaledWidth());
        int anchorY = Math.round(EnvoyConfig.cooldownY * resolution.getScaledHeight());

        for (int i = 0; i < cooldowns.size(); i++) {
            int[] pos = slotPosition(anchorX, anchorY, i);
            renderCooldown(mc, cooldowns.get(i), pos[0], pos[1]);
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static int[] slotPosition(int anchorX, int anchorY, int index) {
        int offset = index * SLOT_SPACING;
        switch (EnvoyConfig.cooldownDirection) {
            case LEFT:
                return new int[] {anchorX - offset, anchorY};
            case DOWN:
                return new int[] {anchorX, anchorY + offset};
            case UP:
                return new int[] {anchorX, anchorY - offset};
            case RIGHT:
            default:
                return new int[] {anchorX + offset, anchorY};
        }
    }

    public static void renderPreview(int cx, int cy) {
        int start = 0x99595959;
        int end = 0xFFE5E5E5;
        int edge = 0xFF000000;
        float fraction = 0.6F;
        fillArc(cx, cy, RADIUS_OUTER, 0.0F, 0.0F, 360.0F, 0x55000000);
        fillArc(cx, cy, RADIUS_OUTER, RADIUS_INNER, 0.0F, 360.0F, end);
        fillArc(cx, cy, RADIUS_OUTER, RADIUS_INNER, -90.0F, 360.0F * fraction, start);
        fillArc(cx, cy, RADIUS_OUTER, RADIUS_OUTER - 0.8F, 0.0F, 360.0F, edge);
        fillArc(cx, cy, RADIUS_INNER + 0.8F, RADIUS_INNER, 0.0F, 360.0F, edge);
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        String label = "3.0";
        font.drawStringWithShadow(label, (int) (cx - font.getStringWidth(label) / 2.0F), (int) (cy + RADIUS_OUTER + 2.0F), 0xFFFFFFFF);
    }

    private void renderCooldown(Minecraft mc, CooldownState.Entry entry, float cx, float cy) {
        Ring ring = new Ring(cx, cy);
        ring.paint(RADIUS_OUTER, 0.0F, 360.0F, 0x55000000);
        ring.paint(RADIUS_INNER, 0.0F, 360.0F, entry.circleEndArgb);
        ring.paint(RADIUS_INNER, -90.0F, 360.0F * entry.remainingFraction(), entry.circleStartArgb);
        ring.paint(RADIUS_OUTER - 0.8F, 0.0F, 360.0F, entry.circleEdgeArgb);
        new Ring(cx, cy, RADIUS_INNER + 0.8F).paint(RADIUS_INNER, 0.0F, 360.0F, entry.circleEdgeArgb);

        if (entry.icon != null) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(cx, cy, 0.0F);
            GlStateManager.scale(ITEM_SCALE, ITEM_SCALE, 1.0F);
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableBlend();
            RenderHelper.enableGUIStandardItemLighting();
            dev.luka.compat.Mc17.drawItemAndEffect(mc, entry.icon, -8, -8);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.popMatrix();
        }

        FontRenderer font = mc.fontRendererObj;
        String label = formatRemaining(entry.remainingMillis());
        float textX = cx - font.getStringWidth(label) / 2.0F;
        font.drawStringWithShadow(label, (int) textX, (int) (cy + RADIUS_OUTER + 2.0F), entry.textArgb);
    }

    private static String formatRemaining(long millis) {
        if (millis >= 3_600_000L) {
            return (millis / 3_600_000L) + "h" + ((millis % 3_600_000L) / 60_000L) + "m";
        }
        if (millis >= 60_000L) {
            return (millis / 60_000L) + "m" + ((millis % 60_000L) / 1_000L) + "s";
        }
        float seconds = millis / 1000.0F;
        if (seconds >= 10.0F) {
            return Math.round(seconds) + "s";
        }
        return String.format("%.1f", seconds);
    }

    private static void fillArc(float cx, float cy, float rOuter, float rInner,
                                float startDeg, float sweepDeg, int argb) {
        if (sweepDeg <= 0.0F) {
            return;
        }
        int alpha = (argb >>> 24) & 0xFF;
        float a = alpha == 0 ? 1.0F : alpha / 255.0F;
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.color(r, g, b, a);

        int steps = Math.max(1, Math.round(SEGMENTS * (sweepDeg / 360.0F)));
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(startDeg + sweepDeg * (i / (double) steps));
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            GL11.glVertex2f(cx + cos * rOuter, cy + sin * rOuter);
            GL11.glVertex2f(cx + cos * rInner, cy + sin * rInner);
        }
        GL11.glEnd();

        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static final class Ring {
        private final float x;
        private final float y;
        private final float outer;

        private Ring(float x, float y) {
            this(x, y, RADIUS_OUTER);
        }

        private Ring(float x, float y, float outer) {
            this.x = x;
            this.y = y;
            this.outer = outer;
        }

        private void paint(float inner, float start, float sweep, int color) {
            fillArc(this.x, this.y, this.outer, inner, start, sweep, color);
        }
    }
}
