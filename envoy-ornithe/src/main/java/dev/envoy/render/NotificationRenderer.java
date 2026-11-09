package dev.envoy.render;

import java.util.List;

import dev.envoy.module.nametag.AdventureText.Run;
import dev.envoy.module.notification.NotificationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import dev.envoy.platform.RenderGameOverlayEvent;

public final class NotificationRenderer {

    private static final int MARGIN = 5;
    private static final int PADDING = 4;
    private static final int ICON_SIZE = 16;
    private static final int GAP = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int BG_ALPHA = 0xC0;
    private static final long FADE_IN_MS = 200L;
    private static final long FADE_OUT_MS = 400L;

    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || NotificationState.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRendererObj;
        ScaledResolution resolution = new ScaledResolution(mc);
        int screenWidth = resolution.getScaledWidth();
        long now = System.currentTimeMillis();

        int y = MARGIN;
        for (NotificationState.Notification notification : NotificationState.all()) {
            long remaining = notification.remaining(now);
            if (remaining <= 0L) {
                NotificationState.remove(notification);
                continue;
            }
            float opacity = opacity(notification.elapsed(now), remaining);
            Card card = Card.measure(font, notification, screenWidth, y);
            card.draw(mc, font, notification, opacity);
            y = card.bottom() + GAP;
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static float opacity(long elapsed, long remaining) {
        float in = elapsed >= FADE_IN_MS ? 1.0F : elapsed / (float) FADE_IN_MS;
        float out = remaining >= FADE_OUT_MS ? 1.0F : remaining / (float) FADE_OUT_MS;
        return Math.max(0.0F, Math.min(in, out));
    }

    private static void drawIcon(Minecraft mc, NotificationState.Notification notification, int x, int y, float fade) {
        if (notification.itemIcon != null) {
            RenderItem renderItem = mc.getRenderItem();
            GlStateManager.enableRescaleNormal();
            RenderHelper.enableGUIStandardItemLighting();
            renderItem.renderItemIntoGUI(notification.itemIcon, x, y);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
        } else if (notification.textureIcon != null) {
            mc.getTextureManager().bindTexture(notification.textureIcon);
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, fade);
            Gui.drawScaledCustomSizeModalRect(x, y, 0.0F, 0.0F, 16, 16, ICON_SIZE, ICON_SIZE, 16.0F, 16.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static int lineWidth(FontRenderer font, List<Run> runs) {
        int width = 0;
        for (Run run : runs) {
            width += font.getStringWidth(run.display);
        }
        return width;
    }

    private static void drawLine(FontRenderer font, List<Run> runs, int x, int y, int alpha) {
        int cursor = x;
        for (Run run : runs) {
            int color = (alpha << 24) | (run.rgb & 0xFFFFFF);
            font.drawStringWithShadow(run.display, cursor, y, color);
            cursor += font.getStringWidth(run.display);
        }
    }

    private static final class Card {
        private final int x, y, width, height, textHeight, iconSpace;

        private Card(int x, int y, int width, int height, int textHeight, int iconSpace) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            this.textHeight = textHeight; this.iconSpace = iconSpace;
        }

        private static Card measure(FontRenderer font, NotificationState.Notification note, int screenWidth, int y) {
            boolean icon = note.itemIcon != null || note.textureIcon != null;
            int widest = 0;
            for (List<Run> line : note.titleLines) widest = Math.max(widest, lineWidth(font, line));
            for (List<Run> line : note.descriptionLines) widest = Math.max(widest, lineWidth(font, line));
            int textHeight = (note.titleLines.size() + note.descriptionLines.size()) * LINE_HEIGHT;
            int iconSpace = icon ? ICON_SIZE + PADDING : 0;
            int width = PADDING * 2 + iconSpace + widest;
            int height = PADDING * 2 + Math.max(icon ? ICON_SIZE : 0, textHeight);
            return new Card(screenWidth - MARGIN - width, y, width, height, textHeight, iconSpace);
        }

        private void draw(Minecraft mc, FontRenderer font, NotificationState.Notification note, float opacity) {
            Gui.drawRect(this.x, this.y, this.x + this.width, this.y + this.height,
                ((int) (BG_ALPHA * opacity)) << 24);
            if (this.iconSpace != 0) {
                drawIcon(mc, note, this.x + PADDING, this.y + (this.height - ICON_SIZE) / 2, opacity);
            }
            int cursorY = this.y + (this.height - this.textHeight) / 2;
            int textX = this.x + PADDING + this.iconSpace;
            int alpha = (int) (255.0F * opacity);
            for (List<Run> line : note.titleLines) { drawLine(font, line, textX, cursorY, alpha); cursorY += LINE_HEIGHT; }
            for (List<Run> line : note.descriptionLines) { drawLine(font, line, textX, cursorY, alpha); cursorY += LINE_HEIGHT; }
        }

        private int bottom() { return this.y + this.height; }
    }
}
