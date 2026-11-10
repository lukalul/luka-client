package dev.envoy.render;

import java.io.File;
import java.io.FileWriter;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

public final class LightmapGuard {
    private static final int BLEND_SRC_RGB = 0x80C9, BLEND_DST_RGB = 0x80C8;
    private static final int BLEND_SRC_ALPHA = 0x80CB, BLEND_DST_ALPHA = 0x80CA;
    private static final IntBuffer INTEGER = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer FLOAT = BufferUtils.createFloatBuffer(16);
    private static Snapshot saved;
    private static boolean disabled;
    private static int firstPersonSamples, thirdPersonSamples;
    private static long previousSampleAt;

    private LightmapGuard() { }

    public static void begin() {
        saved = null;
        if (disabled) return;
        try { recordDiagnostic(); } catch (Throwable ignored) { }
        try {
            Snapshot state = Snapshot.read();
            applyOverlayState(state);
            saved = state;
        } catch (Throwable incompatibleDriver) {
            disabled = true;
        }
    }

    public static void end() {
        Snapshot state = saved;
        saved = null;
        if (state == null) return;
        try { state.restore(); }
        catch (Throwable incompatibleDriver) { disabled = true; }
    }

    private static void applyOverlayState(Snapshot state) {
        GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP); GL11.glDisable(GL11.GL_COLOR_MATERIAL);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F); GL11.glColorMask(true, true, true, true);
        for (int index = 1; index <= 2; index++) {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + index);
            if (state.texture2d[index]) GlStateManager.disableTexture2D();
        }
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GlStateManager.color(1, 1, 1, 1);
    }

    private static int integer(int name) {
        INTEGER.clear(); GL11.glGetInteger(name, INTEGER); return INTEGER.get(0);
    }

    private static float decimal(int name) {
        FLOAT.clear(); GL11.glGetFloat(name, FLOAT); return FLOAT.get(0);
    }

    private static void recordDiagnostic() throws Exception {
        long now = System.currentTimeMillis();
        if (now - previousSampleAt < 1500) return;
        boolean thirdPerson = Minecraft.getMinecraft().gameSettings.thirdPersonView != 0;
        if (thirdPerson ? thirdPersonSamples >= 6 : firstPersonSamples >= 3) return;
        previousSampleAt = now;
        if (thirdPerson) thirdPersonSamples++; else firstPersonSamples++;

        StringBuilder line = new StringBuilder(thirdPerson ? "THIRD PERSON" : "FIRST PERSON");
        line.append(" | lighting=").append(GL11.glIsEnabled(GL11.GL_LIGHTING));
        line.append(" fog=").append(GL11.glIsEnabled(GL11.GL_FOG));
        line.append(" alphaTest=").append(GL11.glIsEnabled(GL11.GL_ALPHA_TEST));
        line.append("(func ").append(Integer.toHexString(integer(GL11.GL_ALPHA_TEST_FUNC)));
        line.append(" ref ").append(decimal(GL11.GL_ALPHA_TEST_REF)).append(')');
        line.append(" blend=").append(GL11.glIsEnabled(GL11.GL_BLEND));
        line.append(" func rgb ").append(Integer.toHexString(integer(BLEND_SRC_RGB))).append('/');
        line.append(Integer.toHexString(integer(BLEND_DST_RGB))).append(" a ");
        line.append(Integer.toHexString(integer(BLEND_SRC_ALPHA))).append('/');
        line.append(Integer.toHexString(integer(BLEND_DST_ALPHA)));
        line.append(" depth=").append(GL11.glIsEnabled(GL11.GL_DEPTH_TEST));
        line.append(" cull=").append(GL11.glIsEnabled(GL11.GL_CULL_FACE));
        line.append(" colorMaterial=").append(GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL));
        line.append(" logicOp=").append(GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP));
        appendTextureState(line);
        File output = new File(Minecraft.getMinecraft().mcDataDir, "config/envoy-gl-debug.txt");
        FileWriter writer = new FileWriter(output, true);
        try { writer.write(line.append(System.lineSeparator()).toString()); }
        finally { writer.close(); }
    }

    private static void appendTextureState(StringBuilder line) {
        try {
            int active = integer(GL13.GL_ACTIVE_TEXTURE);
            for (int index = 0; index < 4; index++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + index);
                line.append(" unit").append(index).append('=').append(GL11.glIsEnabled(GL11.GL_TEXTURE_2D));
                try { line.append("(env ").append(Integer.toHexString(textureEnvironment())).append(')'); }
                catch (Throwable ignored) { line.append("(env ?)"); }
            }
            GL13.glActiveTexture(active);
            FLOAT.clear(); GL11.glGetFloat(GL11.GL_CURRENT_COLOR, FLOAT);
            line.append(" color=").append(FLOAT.get(0)).append(',').append(FLOAT.get(1)).append(',');
            line.append(FLOAT.get(2)).append(',').append(FLOAT.get(3));
        } catch (Throwable failure) {
            line.append(" (texture state unreadable: ").append(failure).append(')');
        }
    }

    private static int textureEnvironment() {
        INTEGER.clear(); GL11.glGetTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, INTEGER);
        return INTEGER.get(0);
    }

    private static final class Snapshot {
        final boolean lighting, fog, logicOp, colorMaterial;
        final boolean[] texture2d = new boolean[3];
        final int sourceRgb, destinationRgb, sourceAlpha, destinationAlpha, alphaFunction;
        final float alphaReference;

        private Snapshot() {
            lighting = GL11.glIsEnabled(GL11.GL_LIGHTING); fog = GL11.glIsEnabled(GL11.GL_FOG);
            logicOp = GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP);
            colorMaterial = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL);
            sourceRgb = integer(BLEND_SRC_RGB); destinationRgb = integer(BLEND_DST_RGB);
            sourceAlpha = integer(BLEND_SRC_ALPHA); destinationAlpha = integer(BLEND_DST_ALPHA);
            alphaFunction = integer(GL11.GL_ALPHA_TEST_FUNC); alphaReference = decimal(GL11.GL_ALPHA_TEST_REF);
            for (int index = 1; index <= 2; index++) {
                GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + index);
                texture2d[index] = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            }
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        }

        static Snapshot read() { return new Snapshot(); }

        void restore() {
            set(GL11.GL_LIGHTING, lighting); set(GL11.GL_FOG, fog);
            set(GL11.GL_COLOR_LOGIC_OP, logicOp); set(GL11.GL_COLOR_MATERIAL, colorMaterial);
            GL14.glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            GL11.glAlphaFunc(alphaFunction, alphaReference);
            for (int index = 1; index <= 2; index++) if (texture2d[index]) {
                GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + index);
                GlStateManager.enableTexture2D();
            }
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        }

        private static void set(int capability, boolean enabled) {
            if (enabled) GL11.glEnable(capability); else GL11.glDisable(capability);
        }
    }
}
