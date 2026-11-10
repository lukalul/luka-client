package dev.envoy.render;

import java.util.ArrayList;
import java.util.List;
import dev.envoy.module.marker.MarkerState;
import dev.envoy.platform.RenderWorldLastEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public final class MarkerRenderer {
    private static final double HEIGHT_OFFSET = 0.5D;
    private static final double HOVER_DISTANCE = 28.0D;
    private static final int ROW_STEP = 10;

    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft game = Minecraft.getMinecraft();
        Entity observer = game.renderViewEntity;
        if (observer == null || MarkerState.all().isEmpty()) return;
        ScaledResolution screen = new ScaledResolution(game, game.displayWidth, game.displayHeight);
        List<ScreenMarker> batch = collect(observer, RenderManager.instance, screen);
        if (batch.isEmpty()) return;
        enterGui(screen);
        try {
            for (ScreenMarker marker : batch) paint(game, marker);
        } finally {
            leaveGui();
        }
    }

    private static List<ScreenMarker> collect(Entity observer, RenderManager camera, ScaledResolution screen) {
        List<ScreenMarker> result = new ArrayList<ScreenMarker>();
        double middleX = screen.getScaledWidth() * 0.5D, middleY = screen.getScaledHeight() * 0.5D;
        for (MarkerState.Marker source : MarkerState.all()) {
            double[] point = Projection.toScreen(source.x - camera.viewerPosX,
                source.y + HEIGHT_OFFSET - camera.viewerPosY, source.z - camera.viewerPosZ, screen);
            if (point == null) continue;
            double dx = point[0] - middleX, dy = point[1] - middleY;
            boolean focused = dx * dx + dy * dy < HOVER_DISTANCE * HOVER_DISTANCE;
            int metres = (int) Math.round(observer.getDistance(source.x, source.y, source.z));
            result.add(new ScreenMarker((int) point[0], (int) point[1], metres, focused, source));
        }
        return result;
    }

    private static void paint(Minecraft game, ScreenMarker entry) {
        MarkerState.Marker marker = entry.source;
        int radius = Math.max(4, Math.round(marker.scale * 5.0F));
        diamond(entry.x, entry.y, radius + 1, 0xFF000000);
        diamond(entry.x, entry.y, radius, solid(marker.iconArgb));
        TextColumn column = new TextColumn(game.fontRendererObj, entry.x, entry.y + radius + 4, marker.textShadow);
        if (visible(marker.showDescription, entry.focused)) description(game, column, marker);
        if (visible(marker.showOwner, entry.focused) && !marker.ownerName.isEmpty()) {
            if (marker.ownerDisplay == MarkerState.OWNER_HEAD) column.head(game, marker.ownerName, marker.ownerName, 0xFFFFFFFF);
            else column.text(marker.ownerName, 0xFFFFFFFF);
        }
        if (visible(marker.showCoordinates, entry.focused))
            column.text((int) marker.x + ", " + (int) marker.y + ", " + (int) marker.z, 0xFFAAAAAA);
        if (visible(marker.showDistance, entry.focused)) column.text("(" + entry.metres + "m)", 0xFFFFFFFF);
    }

    private static void description(Minecraft game, TextColumn column, MarkerState.Marker marker) {
        MarkerState.Target target = marker.target;
        int color = solid(marker.iconArgb);
        if (target.type == MarkerState.TARGET_PLAYER) {
            column.head(game, target.playerName, target.playerName == null ? marker.id : target.playerName, color);
        } else if ((target.type == MarkerState.TARGET_ITEM || target.type == MarkerState.TARGET_BLOCK) && target.item != null) {
            column.item(game, target.item, marker.id, color);
        } else column.text(marker.id, color);
    }

    private static void diamond(int x, int y, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int reach = radius - Math.abs(row);
            Gui.drawRect(x - reach, y + row, x + reach + 1, y + row + 1, color);
        }
    }

    private static boolean visible(int condition, boolean focused) {
        return condition == MarkerState.COND_ALWAYS || (condition != MarkerState.COND_NEVER && focused);
    }

    private static int solid(int argb) { return argb | 0xFF000000; }

    private static void enterGui(ScaledResolution screen) {
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, screen.getScaledWidth(), screen.getScaledHeight(), 0.0D, -1.0D, 1.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity();
        GlStateManager.disableDepth(); GlStateManager.disableLighting();
    }

    private static void leaveGui() {
        GlStateManager.enableDepth(); GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
    }

    private static final class ScreenMarker {
        final int x, y, metres; final boolean focused; final MarkerState.Marker source;
        ScreenMarker(int x, int y, int metres, boolean focused, MarkerState.Marker source) {
            this.x = x; this.y = y; this.metres = metres; this.focused = focused; this.source = source;
        }
    }

    private static final class TextColumn {
        private final FontRenderer font; private final int center; private final boolean shadow; private int y;
        TextColumn(FontRenderer font, int center, int y, boolean shadow) {
            this.font = font; this.center = center; this.y = y; this.shadow = shadow;
        }
        void text(String value, int color) { draw(value, center - font.getStringWidth(value) / 2, color); y += ROW_STEP; }
        void head(Minecraft game, String skinName, String value, int color) {
            int size = font.FONT_HEIGHT, left = center - (size + 2 + font.getStringWidth(value)) / 2;
            ResourceLocation skin = AbstractClientPlayer.getLocationSkin(skinName == null ? "" : skinName);
            game.getTextureManager().bindTexture(skin); GlStateManager.color(1, 1, 1, 1); GlStateManager.enableBlend();
            Gui.drawScaledCustomSizeModalRect(left, y, 8, 8, 8, 8, size, size, 64, 32); // 1.7.10 skins are 64x32
            Gui.drawScaledCustomSizeModalRect(left, y, 40, 8, 8, 8, size, size, 64, 32);
            draw(value, left + size + 2, color); y += ROW_STEP;
        }
        void item(Minecraft game, ItemStack stack, String value, int color) {
            int size = font.FONT_HEIGHT + 2, left = center - (size + 2 + font.getStringWidth(value)) / 2;
            GlStateManager.pushMatrix(); GlStateManager.translate(left, y - 1, 0); float scale = size / 16.0F;
            GlStateManager.scale(scale, scale, 1); GlStateManager.enableDepth(); GlStateManager.enableRescaleNormal();
            RenderHelper.enableGUIStandardItemLighting(); dev.luka.compat.Mc17.drawItem(game, stack, 0, 0);
            RenderHelper.disableStandardItemLighting(); GlStateManager.disableRescaleNormal(); GlStateManager.disableDepth(); GlStateManager.popMatrix();
            draw(value, left + size + 2, color); y += ROW_STEP;
        }
        private void draw(String value, int x, int color) {
            if (shadow) font.drawStringWithShadow(value, x, y, color); else font.drawString(value, x, y, color);
        }
    }
}
