package dev.envoy.render;

import dev.envoy.module.tntcountdown.TntCountdownState;
import dev.envoy.platform.RenderWorldLastEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

public final class TntCountdownRenderer {
    private static final long EXPIRY_GRACE = 600L;
    private static final float SCALE = 0.02666667F;

    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft game = Minecraft.getMinecraft();
        if (TntCountdownState.isEmpty() || game.theWorld == null || game.renderViewEntity == null) return;
        long clock = System.currentTimeMillis();
        for (TntCountdownState.Countdown timer : TntCountdownState.all()) process(game, event.partialTicks, timer, clock);
    }

    private static void process(Minecraft game, float partialTicks, TntCountdownState.Countdown timer, long clock) {
        long left = timer.remaining(clock);
        if (left < -EXPIRY_GRACE) { TntCountdownState.remove(timer); return; }
        Entity target = locate(game, timer);
        if (target == null || target.isDead) {
            if (left < 0L) TntCountdownState.remove(timer);
            return;
        }
        RenderManager camera = RenderManager.instance;
        Position position = Position.above(target, camera, partialTicks);
        if (position.distanceSquared() <= 4096D) renderLabel(game.fontRendererObj, camera, position, left,
            timer.durationMillis);
    }

    private static Entity locate(Minecraft game, TntCountdownState.Countdown timer) {
        if (timer.entityId > 0) return game.theWorld.getEntityByID(timer.entityId);
        if (timer.uuid == null) return null;
        for (Object candidate : game.theWorld.loadedEntityList) {
            Entity entity = (Entity) candidate;
            if (timer.uuid.equals(entity.getUniqueID())) return entity;
        }
        return null;
    }

    private static void renderLabel(FontRenderer font, RenderManager camera, Position position, long left, long total) {
        String text = String.format("%.1f", Math.max(0L, left) / 1000D);
        double ratio = total <= 0L ? 0D : Math.max(0L, left) / (double) total;
        int color = ratio > 0.5D ? 0xFFFFFF : ratio > 0.2D ? 0xFFFF55 : 0xFF5555;
        GL11.glPushMatrix(); GL11.glTranslated(position.x, position.y, position.z); GL11.glNormal3f(0F, 1F, 0F);
        GL11.glRotatef(-camera.playerViewY, 0F, 1F, 0F); GL11.glRotatef(camera.playerViewX, 1F, 0F, 0F);
        GL11.glScalef(-SCALE, -SCALE, SCALE); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        font.drawStringWithShadow(text, -font.getStringWidth(text) / 2, 0, 0xFF000000 | color);
        GL11.glDepthMask(true); GL11.glEnable(GL11.GL_LIGHTING); GL11.glColor4f(1F, 1F, 1F, 1F); GL11.glPopMatrix();
    }

    private static final class Position {
        final double x, y, z;
        Position(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        static Position above(Entity entity, RenderManager camera, float partialTicks) {
            return new Position(interpolate(entity.lastTickPosX, entity.posX, partialTicks) - camera.viewerPosX,
                interpolate(entity.lastTickPosY, entity.posY, partialTicks) - camera.viewerPosY + entity.height + 0.6D,
                interpolate(entity.lastTickPosZ, entity.posZ, partialTicks) - camera.viewerPosZ);
        }
        double distanceSquared() { return x * x + y * y + z * z; }
        private static double interpolate(double before, double after, float partialTicks) {
            return before + (after - before) * partialTicks;
        }
    }
}
