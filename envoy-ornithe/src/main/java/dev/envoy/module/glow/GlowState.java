package dev.envoy.module.glow;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class GlowState {
    public static final int DEFAULT_RGB = 0xFFFFFF;
    private static final AtomicReference<Map<UUID, Integer>> SNAPSHOT =
        new AtomicReference<>(Collections.<UUID, Integer>emptyMap());

    private GlowState() {
    }

    public static void override(UUID player, Integer argb) {
        if (player == null) return;
        replaceEntry(player, Integer.valueOf(argb == null ? DEFAULT_RGB : argb.intValue() & 0xFFFFFF));
    }

    public static void reset(UUID player) {
        if (player != null) replaceEntry(player, null);
    }

    public static void resetAll() {
        SNAPSHOT.set(Collections.<UUID, Integer>emptyMap());
    }

    public static boolean isGlowing(UUID player) {
        return SNAPSHOT.get().get(player) != null;
    }

    public static int color(UUID player) {
        Integer value = SNAPSHOT.get().get(player);
        return value == null ? DEFAULT_RGB : value.intValue();
    }

    public static boolean isEmpty() {
        return SNAPSHOT.get().isEmpty();
    }

    private static void replaceEntry(UUID player, Integer color) {
        Map<UUID, Integer> previous;
        Map<UUID, Integer> revised;
        do {
            previous = SNAPSHOT.get();
            if (color == null && !previous.containsKey(player)) return;
            revised = new HashMap<>(previous);
            if (color == null) revised.remove(player);
            else revised.put(player, color);
            revised = Collections.unmodifiableMap(revised);
        } while (!SNAPSHOT.compareAndSet(previous, revised));
    }
}
