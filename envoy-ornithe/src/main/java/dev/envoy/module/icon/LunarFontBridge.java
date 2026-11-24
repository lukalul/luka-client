package dev.envoy.module.icon;

import java.lang.reflect.Method;
import net.minecraft.client.gui.FontRenderer;

/** Optional reflection adapter for Lunar's per-position font alpha holder. */
public final class LunarFontBridge {
    private static final Access ACCESS = discover();

    private LunarFontBridge() { }

    public static Object current(FontRenderer font) {
        return ACCESS.read(font);
    }

    public static int alphaOf(Object holder, float x, float y, int fallback) {
        if (holder == null) return fallback;
        try {
            Method apply = holder.getClass().getMethod("apply", Object.class);
            apply.setAccessible(true);
            Object result = apply.invoke(holder, Float.valueOf(x + y));
            return result instanceof Integer ? ((Integer) result) >>> 24 : fallback;
        } catch (ReflectiveOperationException | SecurityException failure) {
            return fallback;
        }
    }

    public static void suspend(FontRenderer font) {
        ACCESS.write(font, null);
    }

    public static void restore(FontRenderer font, Object holder) {
        if (holder != null) ACCESS.write(font, holder);
    }

    private static Access discover() {
        Method reader = null;
        Method writer = null;
        for (Method candidate : FontRenderer.class.getMethods()) {
            int count = candidate.getParameterTypes().length;
            if (candidate.getName().equals("lunar$getColorHolder") && count == 0) reader = candidate;
            if (candidate.getName().equals("lunar$setColorHolder") && count == 1) writer = candidate;
        }
        return new Access(reader, writer);
    }

    private static final class Access {
        final Method reader, writer;
        Access(Method reader, Method writer) { this.reader = reader; this.writer = writer; }

        Object read(FontRenderer font) {
            if (reader == null || writer == null) return null;
            try { return reader.invoke(font); }
            catch (ReflectiveOperationException | RuntimeException failure) { return null; }
        }

        void write(FontRenderer font, Object value) {
            if (writer == null) return;
            try { writer.invoke(font, new Object[] {value}); }
            catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
    }
}
