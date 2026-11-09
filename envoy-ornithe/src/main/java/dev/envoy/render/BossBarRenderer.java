package dev.envoy.render;

import dev.envoy.module.model.ModelRegistry;
import dev.envoy.platform.RenderGameOverlayEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.boss.BossStatus;

public final class BossBarRenderer {
    private static final int WIDTH = 182;
    private static final int HEIGHT = 5;

    public void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.BOSSHEALTH || !ModelRegistry.hasBossBars()
            || BossStatus.bossName == null || BossStatus.statusBarTime <= 0) return;
        ModelRegistry.BossBarEntry style = ModelRegistry.findForBossBar(BossStatus.bossName);
        if (style == null) return;
        event.setCanceled(true); BossStatus.statusBarTime--;
        drawBar(style, Minecraft.getMinecraft());
    }

    private static void drawBar(ModelRegistry.BossBarEntry style, Minecraft game) {
        int middle = new ScaledResolution(game).getScaledWidth() / 2;
        int left = middle - WIDTH / 2;
        int fill = clamp((int) (BossStatus.healthScale * (WIDTH + 1)), 0, WIDTH);
        GlStateManager.enableBlend();
        if (style.texturePath == null) drawSolid(style, left, fill);
        else drawTextured(style, game, left, fill);
        drawDividers(style, left);
        if (!style.hideName) drawName(game.fontRendererObj, middle, style.y, style.textColor);
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private static void drawSolid(ModelRegistry.BossBarEntry style, int left, int fill) {
        Gui.drawRect(left, style.y, left + WIDTH, style.y + HEIGHT, style.backgroundColor);
        if (fill != 0) Gui.drawRect(left, style.y, left + fill, style.y + HEIGHT, style.color);
    }

    private static void drawTextured(ModelRegistry.BossBarEntry style, Minecraft game, int left, int fill) {
        game.getTextureManager().bindTexture(ModelRegistry.resource(style.texturePath)); tint(style.color);
        Gui.drawScaledCustomSizeModalRect(left, style.y, 0F, 0F, WIDTH, HEIGHT, WIDTH, HEIGHT, WIDTH, 10);
        if (fill != 0) Gui.drawScaledCustomSizeModalRect(left, style.y, 0F, HEIGHT, fill, HEIGHT,
            fill, HEIGHT, WIDTH, 10);
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private static void drawDividers(ModelRegistry.BossBarEntry style, int left) {
        for (int part = 1; part < style.notches; part++) {
            int x = left + WIDTH * part / style.notches;
            Gui.drawRect(x, style.y, x + 1, style.y + HEIGHT, 0xFF000000);
        }
    }

    private static void drawName(FontRenderer font, int middle, int y, int color) {
        String name = BossStatus.bossName;
        font.drawStringWithShadow(name, middle - font.getStringWidth(name) / 2F, y - 10F, color);
    }

    private static void tint(int argb) {
        GlStateManager.color(((argb >> 16) & 255) / 255F, ((argb >> 8) & 255) / 255F,
            (argb & 255) / 255F, ((argb >>> 24) & 255) / 255F);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
