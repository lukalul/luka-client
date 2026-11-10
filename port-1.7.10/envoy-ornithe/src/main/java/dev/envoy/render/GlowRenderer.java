package dev.envoy.render;

import dev.envoy.Envoy;
import dev.envoy.EnvoySettings;
import dev.envoy.module.glow.GlowState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public final class GlowRenderer {
    private static OutlineShader shader;
    private static GlowFramebuffer target;
    private static int targetWidth = -1, targetHeight = -1;
    private static boolean hasPixels;
    public static Entity renderTarget;
    public static boolean isRenderingOutline;

    private GlowRenderer() { }

    public static void renderEntitiesToFramebuffer(Entity renderViewEntity, ICamera camera, float partialTicks) {
        Minecraft game = Minecraft.getMinecraft();
        if (game.theWorld == null || GlowState.isEmpty() || !prepare(game)) return;
        beginCapture(game);
        try {
            for (Object candidate : game.theWorld.loadedEntityList) {
                Entity entity = (Entity) candidate;
                if (!eligible(game, entity, renderViewEntity)) continue;
                renderTarget = entity;
                hasPixels = true;
                RenderManager.instance.renderEntitySimple(entity, partialTicks);
            }
        } finally {
            finishCapture(game);
        }
    }

    public static void displayFramebuffer() {
        if (!hasPixels || shader == null || target == null) { hasPixels = false; return; }
        Minecraft game = Minecraft.getMinecraft();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        pushScreenMatrices(game.displayWidth, game.displayHeight);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG);
            GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            shader.bind();
            try {
                GL20.glUniform1i(shader.getUniform("texture"), 0);
                int resolution = shader.getUniform("resolution");
                if (resolution >= 0) GL20.glUniform2f(resolution, game.displayWidth, game.displayHeight);
                drawTexture(target.getFramebufferTexture(), game.displayWidth, game.displayHeight);
            } finally { shader.unbind(); }
        } finally {
            popScreenMatrices(); GL11.glPopAttrib(); hasPixels = false;
        }
    }

    private static boolean prepare(Minecraft game) {
        if (shader == null) {
            try { shader = new OutlineShader("shaders/program/outline_vertex.vsh", "shaders/program/outline_fragment.fsh"); }
            catch (Exception failure) {
                Envoy.LOGGER.error("[Glow] could not load the outline shader", failure); return false;
            }
        }
        if (target == null || targetWidth != game.displayWidth || targetHeight != game.displayHeight) {
            if (target != null) target.cleanup();
            target = new GlowFramebuffer(game.displayWidth, game.displayHeight);
            targetWidth = game.displayWidth; targetHeight = game.displayHeight;
        }
        return true;
    }

    private static boolean eligible(Minecraft game, Entity entity, Entity viewer) {
        if (!(entity instanceof EntityPlayer)) return false;
        if (entity == viewer && (!EnvoySettings.renderSelf || game.gameSettings.thirdPersonView == 0)) return false;
        EntityPlayer player = (EntityPlayer) entity;
        return !player.isInvisible() && GlowState.isGlowing(player.getUniqueID());
    }

    private static void beginCapture(Minecraft game) {
        GlStateManager.depthFunc(GL11.GL_ALWAYS); GlStateManager.disableFog(); target.bindFramebuffer();
        RenderHelper.disableStandardItemLighting();
        isRenderingOutline = true;
    }

    private static void finishCapture(Minecraft game) {
        isRenderingOutline = false; renderTarget = null;
        RenderHelper.enableStandardItemLighting(); GlStateManager.depthMask(false); GlStateManager.enableLighting();
        GlStateManager.depthMask(true); target.unbindFramebuffer(); GlStateManager.enableFog();
        GlStateManager.enableBlend(); GlStateManager.enableColorMaterial(); GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableDepth(); GlStateManager.enableAlpha();
    }

    private static void pushScreenMatrices(int width, int height) {
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity();
        GL11.glOrtho(0, width, height, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity();
    }

    private static void popScreenMatrices() {
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
    }

    private static void drawTexture(int texture, int width, int height) {
        GL11.glViewport(0, 0, width, height); GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D); GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture); GL11.glColor4f(1, 1, 1, 1);
        GL11.glBegin(GL11.GL_QUADS);
        vertex(0, 1, 0, 0); vertex(1, 1, width, 0); vertex(1, 0, width, height); vertex(0, 0, 0, height);
        GL11.glEnd();
    }

    private static void vertex(float u, float v, float x, float y) {
        GL11.glTexCoord2f(u, v); GL11.glVertex2f(x, y);
    }
}
