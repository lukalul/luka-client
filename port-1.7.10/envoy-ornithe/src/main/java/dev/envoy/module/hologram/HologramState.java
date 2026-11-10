package dev.envoy.module.hologram;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.envoy.module.nametag.AdventureText.Run;

public final class HologramState {
    public static final class Hologram {
        public final double x;
        public final double y;
        public final double z;
        public final List<List<Run>> lines;
        public final boolean showThroughWalls;
        public final boolean showShadow;
        public final boolean showBackground;

        public Hologram(double x, double y, double z, List<List<Run>> lines,
                        boolean showThroughWalls, boolean showShadow, boolean showBackground) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lines = freeze(lines);
            this.showThroughWalls = showThroughWalls;
            this.showShadow = showShadow;
            this.showBackground = showBackground;
        }

        private static List<List<Run>> freeze(List<List<Run>> source) {
            List<List<Run>> copy = new ArrayList<>(source.size());
            for (List<Run> line : source) copy.add(Collections.unmodifiableList(new ArrayList<>(line)));
            return Collections.unmodifiableList(copy);
        }
    }

    private static final AtomicReference<Map<String, Hologram>> VISIBLE =
        new AtomicReference<>(Collections.<String, Hologram>emptyMap());

    private HologramState() {
    }

    public static void display(String id, Hologram hologram) {
        revise(id, hologram);
    }

    public static void remove(String id) {
        revise(id, null);
    }

    public static void resetAll() {
        VISIBLE.set(Collections.<String, Hologram>emptyMap());
    }

    public static Collection<Hologram> all() {
        return VISIBLE.get().values();
    }

    public static boolean isEmpty() {
        return VISIBLE.get().isEmpty();
    }

    private static void revise(String id, Hologram replacement) {
        if (id == null) return;
        Map<String, Hologram> oldMap;
        Map<String, Hologram> newMap;
        do {
            oldMap = VISIBLE.get();
            if (replacement == null && !oldMap.containsKey(id)) return;
            newMap = new LinkedHashMap<>(oldMap);
            if (replacement == null) newMap.remove(id);
            else newMap.put(id, replacement);
            newMap = Collections.unmodifiableMap(newMap);
        } while (!VISIBLE.compareAndSet(oldMap, newMap));
    }
}
