package dev.customsky;

import java.util.Properties;
import net.minecraft.util.ResourceLocation;

/**
 * One skybox layer from a {@code sky<i>.properties} file. Fade curve, rotation and path rules are ported from
 * legacy-custom-sky and MCPatcher's Better Skies (both MIT, see LICENSE-customsky.txt).
 */
final class SkyLayer {
    private static final int TICKS_PER_DAY = 24000;
    private static final double TOD_OFFSET = -0.25;

    final ResourceLocation texture;
    final BlendMode blend;
    final boolean rotate;
    final float speed;
    final float axisX, axisY, axisZ;
    private final double a, b, c;

    private SkyLayer(ResourceLocation texture, double a, double b, double c, BlendMode blend, boolean rotate, float speed, float[] axis) {
        this.texture = texture;
        this.a = a;
        this.b = b;
        this.c = c;
        this.blend = blend;
        this.rotate = rotate;
        this.speed = speed;
        this.axisX = axis[0];
        this.axisY = axis[1];
        this.axisZ = axis[2];
    }

    /** Returns null when the file does not describe a valid layer. */
    static SkyLayer parse(Properties props, String propertiesPath, String defaultSource) {
        int startFadeIn = parseTime(props.getProperty("startFadeIn"));
        int endFadeIn = parseTime(props.getProperty("endFadeIn"));
        int endFadeOut = parseTime(props.getProperty("endFadeOut"));
        if (startFadeIn < 0 || endFadeIn < 0 || endFadeOut < 0) return null;
        // There is no need for startFadeOut: it is determined by the other three.
        while (endFadeIn <= startFadeIn) endFadeIn += TICKS_PER_DAY;
        while (endFadeOut <= endFadeIn) endFadeOut += TICKS_PER_DAY;
        if (endFadeOut - startFadeIn >= TICKS_PER_DAY) return null;

        // f(x) = a cos x + b sin x + c, with f(startFadeIn)=0, f(endFadeIn)=1, f(endFadeOut)=0.
        double s0 = normalize(startFadeIn, TOD_OFFSET);
        double s1 = normalize(endFadeIn, TOD_OFFSET);
        double e1 = normalize(endFadeOut, TOD_OFFSET);
        double det = Math.cos(s0) * Math.sin(s1) + Math.cos(e1) * Math.sin(s0) + Math.cos(s1) * Math.sin(e1)
                - Math.cos(s0) * Math.sin(e1) - Math.cos(s1) * Math.sin(s0) - Math.cos(e1) * Math.sin(s1);
        if (det == 0.0) return null;
        double a = (Math.sin(e1) - Math.sin(s0)) / det;
        double b = (Math.cos(s0) - Math.cos(e1)) / det;
        double c = (Math.cos(e1) * Math.sin(s0) - Math.cos(s0) * Math.sin(e1)) / det;

        float speed = parseFloat(props.getProperty("speed"), 1.0F);
        if (speed < 0.0F) return null;
        return new SkyLayer(resolveSource(props.getProperty("source"), propertiesPath, defaultSource), a, b, c,
                BlendMode.parse(props.getProperty("blend")), !"false".equalsIgnoreCase(trim(props.getProperty("rotate"))), speed,
                parseAxis(props.getProperty("axis")));
    }

    /** {@code ns:path} is absolute, {@code ~/} starts at the pack's mcpatcher/optifine folder, {@code ./} next to the file. */
    private static ResourceLocation resolveSource(String raw, String propertiesPath, String defaultSource) {
        raw = trim(raw);
        if (raw == null || raw.isEmpty()) return new ResourceLocation("minecraft", defaultSource);
        if (raw.indexOf(':') >= 0) return new ResourceLocation(raw);
        if (raw.startsWith("~/")) {
            return new ResourceLocation("minecraft", propertiesPath.substring(0, propertiesPath.indexOf('/') + 1) + raw.substring(2));
        }
        if (raw.startsWith("./")) {
            return new ResourceLocation("minecraft", propertiesPath.substring(0, propertiesPath.lastIndexOf('/') + 1) + raw.substring(2));
        }
        return new ResourceLocation("minecraft", raw);
    }

    private static int parseTime(String text) {
        text = trim(text);
        if (text == null) return -1;
        String[] parts = text.split(":");
        if (parts.length < 2 || parts.length > 3) return -1;
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            int second = parts.length == 3 ? Integer.parseInt(parts[2].trim()) : 0;
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59 || second < 0 || second > 59) return -1;
            return (int) Math.round((hour * 3600.0 + minute * 60.0 + second) / 86400.0 * TICKS_PER_DAY) % TICKS_PER_DAY;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static float parseFloat(String text, float fallback) {
        text = trim(text);
        if (text == null) return fallback;
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The file's compass axis (x,y,z) becomes the rotation axis (z,y,-x), normalised because packs write non-unit axes. */
    private static float[] parseAxis(String text) {
        float[] fallback = {1.0F, 0.0F, 0.0F};
        text = trim(text);
        if (text == null) return fallback;
        String[] parts = text.split("\\s+");
        if (parts.length != 3) return fallback;
        float[] xyz = new float[3];
        try {
            for (int i = 0; i < 3; i++) xyz[i] = Float.parseFloat(parts[i]);
        } catch (NumberFormatException e) {
            return fallback;
        }
        float squared = xyz[0] * xyz[0] + xyz[1] * xyz[1] + xyz[2] * xyz[2];
        if (squared < 1.0E-5F) return fallback;
        float inverse = (float) (1.0 / Math.sqrt(squared));
        return new float[] {xyz[2] * inverse, xyz[1] * inverse, -xyz[0] * inverse};
    }

    private static String trim(String text) {
        return text == null ? null : text.trim();
    }

    private static double normalize(double time, double offset) {
        return 2.0 * Math.PI * (time / TICKS_PER_DAY + offset);
    }

    float brightness(double worldTime, float rainStrength) {
        double x = normalize(worldTime, 0.0);
        float value = (1.0F - rainStrength) * (float) (a * Math.cos(x) + b * Math.sin(x) + c);
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
