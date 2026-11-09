package dev.envoy.gui;

import dev.envoy.Envoy;
import java.lang.reflect.InvocationTargetException;

/** Keeps the optional OneConfig integration out of Envoy's linkage surface. */
final class OneConfigBridge {
    private static final String UI_CLASS = "org.polyfrost.oneconfig.api.ui.v1.OneConfigUI";
    private static final String ENTRYPOINT_CLASS = "dev.envoy.oneconfig.EnvoyOneConfigEntrypoint";

    private OneConfigBridge() { }

    static boolean open() {
        ClassLoader loader = OneConfigBridge.class.getClassLoader();
        try {
            Class<?> entrypoint = Class.forName(ENTRYPOINT_CLASS, false, loader);
            Object configuration = entrypoint.getMethod("getConfig").invoke(null);
            if (configuration == null) return false;
            Class.forName(UI_CLASS, false, loader).getMethod("open", Object.class).invoke(null, configuration);
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (InvocationTargetException failure) {
            report(failure.getCause() == null ? failure : failure.getCause());
        } catch (Throwable failure) {
            report(failure);
        }
        return false;
    }

    private static void report(Throwable failure) {
        Envoy.problem("open OneConfig", failure);
    }
}
