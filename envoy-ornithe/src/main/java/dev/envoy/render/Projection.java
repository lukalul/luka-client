package dev.envoy.render;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

public final class Projection {
    public static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
    public static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    public static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer WINDOW_POINT = BufferUtils.createFloatBuffer(3);
    private static boolean current;

    private Projection() { }
    public static void newFrame() { current = false; }

    public static void ensure() {
        if (current) return;
        captureFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        captureFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        VIEWPORT.clear(); GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT); VIEWPORT.rewind();
        current = true;
    }

    public static double[] toScreen(double x, double y, double z, ScaledResolution resolution) {
        ensure();
        return projectPoint((float) x, (float) y, (float) z, resolution);
    }

    private static double[] projectPoint(float x, float y, float z, ScaledResolution resolution) {
        WINDOW_POINT.clear();
        if (!GLU.gluProject(x, y, z, MODELVIEW, PROJECTION, VIEWPORT, WINDOW_POINT)) return null;
        float depth = WINDOW_POINT.get(2);
        if (depth < 0F || depth > 1F) return null;
        double horizontal = WINDOW_POINT.get(0) * resolution.getScaledWidth() / VIEWPORT.get(2);
        double vertical = WINDOW_POINT.get(1) * resolution.getScaledHeight() / VIEWPORT.get(3);
        return new double[] { horizontal, resolution.getScaledHeight() - vertical };
    }

    private static void captureFloat(int property, FloatBuffer destination) {
        destination.clear(); GL11.glGetFloat(property, destination); destination.rewind();
    }
}
