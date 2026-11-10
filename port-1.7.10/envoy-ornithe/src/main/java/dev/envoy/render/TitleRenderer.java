package dev.envoy.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import dev.luka.compat.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Minecraft 1.8 draws server titles itself; 1.7.10 has no title display, so Envoy paints the same centered
 * title and subtitle on the HUD with the same fade logic.
 */
public final class TitleRenderer {
    private static String title;
    private static String subtitle;
    private static int fadeIn = 10;
    private static int stay = 70;
    private static int fadeOut = 20;
    private static int timer;

    public void tick() {
        if (timer > 0) {
            timer--;
            if (timer <= 0) {
                title = null;
                subtitle = null;
            }
        }
    }

    public static void setTimes(int fadeInTicks, int stayTicks, int fadeOutTicks) {
        if (fadeInTicks >= 0) fadeIn = fadeInTicks;
        if (stayTicks >= 0) stay = stayTicks;
        if (fadeOutTicks >= 0) fadeOut = fadeOutTicks;
        if (timer > 0) timer = fadeIn + stay + fadeOut;
    }

    public static void showTitle(String text) {
        title = text;
        timer = fadeIn + stay + fadeOut;
    }

    public static void showSubtitle(String text) {
        subtitle = text;
    }

    public static void clear() {
        title = null;
        subtitle = null;
        timer = 0;
    }

    public void render(float partialTicks) {
        if (timer <= 0 || title == null) return;
        float remaining = timer - partialTicks;
        int alpha = 255;
        if (timer > fadeOut + stay) {
            float elapsed = (float) (fadeIn + stay + fadeOut) - remaining;
            alpha = fadeIn <= 0 ? 255 : (int) (elapsed * 255.0F / fadeIn);
        }
        if (timer <= fadeOut) alpha = fadeOut <= 0 ? 255 : (int) (remaining * 255.0F / fadeOut);
        alpha = Math.max(0, Math.min(255, alpha));
        if (alpha <= 8) return;

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRendererObj;
        ScaledResolution screen = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int color = (alpha << 24) | 0xFFFFFF;
        GlStateManager.pushMatrix();
        GlStateManager.translate(screen.getScaledWidth() / 2.0F, screen.getScaledHeight() / 2.0F, 0.0F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.pushMatrix();
        GlStateManager.scale(4.0F, 4.0F, 4.0F);
        font.drawString(title, -font.getStringWidth(title) / 2, -10, color, true);
        GlStateManager.popMatrix();
        if (subtitle != null) {
            GlStateManager.pushMatrix();
            GlStateManager.scale(2.0F, 2.0F, 2.0F);
            font.drawString(subtitle, -font.getStringWidth(subtitle) / 2, 5, color, true);
            GlStateManager.popMatrix();
        }
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
