package dev.envoy.render;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import dev.envoy.module.team.TeamState;
import dev.envoy.platform.RenderWorldLastEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public final class TeamRenderer {
    private static final double ABOVE_PLAYER = 2.9D, NAME_AIM_COSINE = 0.99D;
    private static final int TEXT_COLOR = 0x78FFFFFF;

    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft game = Minecraft.getMinecraft();
        Entity viewer = game.getRenderViewEntity();
        if (viewer == null || TeamState.members().isEmpty()) return;
        ScaledResolution screen = new ScaledResolution(game);
        List<TeamPin> pins = locate(game, viewer, event.partialTicks, screen);
        if (pins.isEmpty()) return;
        beginOverlay(screen);
        try { for (TeamPin pin : pins) renderPin(game.fontRendererObj, pin); }
        finally { finishOverlay(); }
    }

    private static List<TeamPin> locate(Minecraft game, Entity viewer, float delta, ScaledResolution screen) {
        List<TeamPin> pins = new ArrayList<TeamPin>();
        RenderManager camera = game.getRenderManager();
        Vec3 eye = viewer.getPositionEyes(delta), gaze = viewer.getLook(delta);
        UUID self = game.thePlayer.getUniqueID();
        for (TeamState.Member member : TeamState.members()) {
            if (self.equals(member.uuid)) continue;
            EntityPlayer player = player(game, member.uuid);
            double x = player == null ? member.x : interpolate(player.lastTickPosX, player.posX, delta);
            double y = player == null ? member.y : interpolate(player.lastTickPosY, player.posY, delta);
            double z = player == null ? member.z : interpolate(player.lastTickPosZ, player.posZ, delta);
            double[] point = Projection.toScreen(x - camera.viewerPosX, y + ABOVE_PLAYER - camera.viewerPosY,
                z - camera.viewerPosZ, screen);
            if (point == null) continue;
            Vec3 direction = new Vec3(x - eye.xCoord, y + ABOVE_PLAYER - eye.yCoord, z - eye.zCoord).normalize();
            String label = gaze.dotProduct(direction) > NAME_AIM_COSINE ? name(game, member.uuid, player) : null;
            int color = (member.markerArgb >>> 24) == 0 ? member.markerArgb | 0xFF000000 : member.markerArgb;
            pins.add(new TeamPin(point[0], point[1], color, (int) Math.round(viewer.getDistance(x, y, z)), label));
        }
        return pins;
    }

    private static double interpolate(double previous, double current, float delta) {
        return previous + (current - previous) * delta;
    }

    private static EntityPlayer player(Minecraft game, UUID id) {
        if (game.theWorld != null) for (EntityPlayer candidate : game.theWorld.playerEntities)
            if (id.equals(candidate.getUniqueID())) return candidate;
        return null;
    }

    private static String name(Minecraft game, UUID id, EntityPlayer player) {
        if (player != null) return player.getDisplayName().getFormattedText();
        if (game.getNetHandler() == null) return null;
        NetworkPlayerInfo info = game.getNetHandler().getPlayerInfo(id);
        return info == null ? null : game.ingameGUI.getTabList().getPlayerName(info);
    }

    private static void renderPin(FontRenderer font, TeamPin pin) {
        // a small arrow pointing down at the team mate (its tip stays where the old, bigger one had it)
        triangle(pin.x - 4, pin.y - 6, pin.x + 4, pin.y - 6, pin.x, pin.y + 1, 0xB0000000);
        triangle(pin.x - 3, pin.y - 5, pin.x + 3, pin.y - 5, pin.x, pin.y - 0.5, pin.color);
        if (pin.metres <= 50) return;
        GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        String distance = "(" + pin.metres + "m)"; float y = (float) pin.y + 2;
        font.drawStringWithShadow(distance, (float) pin.x - font.getStringWidth(distance) / 2.0F, y, TEXT_COLOR);
        if (pin.name != null) font.drawStringWithShadow(pin.name,
            (float) pin.x - font.getStringWidth(pin.name) / 2.0F, y + font.FONT_HEIGHT + 1, TEXT_COLOR);
        GlStateManager.disableBlend();
    }

    private static void triangle(double ax, double ay, double bx, double by, double cx, double cy, int color) {
        float a = (color >>> 24) / 255.0F, r = ((color >>> 16) & 255) / 255.0F;
        float g = ((color >>> 8) & 255) / 255.0F, b = (color & 255) / 255.0F;
        GlStateManager.disableTexture2D(); GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Tessellator tessellator = Tessellator.getInstance(); WorldRenderer vertices = tessellator.getWorldRenderer();
        vertices.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        vertices.pos(ax, ay, 0).color(r, g, b, a).endVertex();
        vertices.pos(cx, cy, 0).color(r, g, b, a).endVertex();
        vertices.pos(bx, by, 0).color(r, g, b, a).endVertex(); tessellator.draw();
        GlStateManager.disableBlend(); GlStateManager.enableTexture2D(); GlStateManager.color(1, 1, 1, 1);
    }

    private static void beginOverlay(ScaledResolution screen) {
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity();
        GL11.glOrtho(0, screen.getScaledWidth(), screen.getScaledHeight(), 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity();
        GlStateManager.disableDepth(); GlStateManager.disableLighting();
    }

    private static void finishOverlay() {
        GlStateManager.enableDepth(); GlStateManager.color(1, 1, 1, 1);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
    }

    private static final class TeamPin {
        final double x, y; final int color, metres; final String name;
        TeamPin(double x, double y, int color, int metres, String name) {
            this.x = x; this.y = y; this.color = color; this.metres = metres; this.name = name;
        }
    }
}
