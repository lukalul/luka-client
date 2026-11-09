package dev.envoy.module.coloredfire;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ColoredFireState {
    private static final Object MUTEX = new Object();
    private static volatile Map<UUID, Integer> palette = Collections.emptyMap();

    private ColoredFireState() {
    }

    public static void override(UUID player, int rgb) {
        synchronized (MUTEX) {
            Map<UUID, Integer> next = new HashMap<UUID, Integer>(palette);
            next.put(player, rgb & 0xFFFFFF);
            palette = Collections.unmodifiableMap(next);
        }
    }

    public static void reset(UUID player) {
        synchronized (MUTEX) {
            Map<UUID, Integer> next = new HashMap<UUID, Integer>(palette);
            next.remove(player);
            palette = Collections.unmodifiableMap(next);
        }
    }

    public static void resetAll() {
        palette = Collections.emptyMap();
    }

    public static boolean isEmpty() {
        return palette.isEmpty();
    }

    public static int colorOrZero(UUID player) {
        Integer value = palette.get(player);
        return value != null ? value.intValue() : 0;
    }
}
