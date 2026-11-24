package dev.envoy;

import java.util.LinkedHashSet;
import java.util.Set;

/** Resolves whether Envoy is hosted inside Lunar Client, once per process. */
public final class LunarHost {
    private static final String OVERRIDE_PROPERTY = "envoy.lunar";
    private static final String[] SIGNATURES = {
        "com.lunarclient.apollo.ApolloManager",
        "com.moonsworth.lunar.client.Lunar"
    };

    private static Boolean cachedAnswer;

    private LunarHost() { }

    public static boolean isLunar() {
        synchronized (LunarHost.class) {
            if (cachedAnswer != null) return cachedAnswer.booleanValue();
            boolean answer = resolveHost();
            cachedAnswer = Boolean.valueOf(answer);
            Envoy.LOGGER.info(answer
                ? "[Envoy] Lunar Client detected: running the Lunar profile (icons, boss bars)"
                : "[Envoy] standalone Ornithe client: running the full profile");
            return answer;
        }
    }

    private static boolean resolveHost() {
        String override = System.getProperty(OVERRIDE_PROPERTY);
        if (override != null) return Boolean.parseBoolean(override);
        for (ClassLoader loader : candidateLoaders()) {
            for (String signature : SIGNATURES) {
                if (isVisible(signature, loader)) return true;
            }
        }
        return false;
    }

    private static Set<ClassLoader> candidateLoaders() {
        Set<ClassLoader> result = new LinkedHashSet<ClassLoader>();
        result.add(LunarHost.class.getClassLoader());
        result.add(Thread.currentThread().getContextClassLoader());
        result.add(ClassLoader.getSystemClassLoader());
        result.remove(null);
        return result;
    }

    private static boolean isVisible(String className, ClassLoader loader) {
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (Throwable unavailable) {
            return false;
        }
    }
}
