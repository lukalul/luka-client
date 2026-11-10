package dev.envoy.module.cooldown;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ItemStack;

public final class CooldownState {
    public static final class Entry {
        public final String name;
        public final long startMillis;
        public final long durationMillis;
        public final ItemStack icon;
        public final int circleStartArgb, circleEndArgb, circleEdgeArgb, textArgb;
        private final long deadlineNanos;

        Entry(String name, long durationMillis, ItemStack icon, int circleStartArgb,
              int circleEndArgb, int circleEdgeArgb, int textArgb) {
            this.name = name; this.durationMillis = durationMillis; this.icon = icon;
            this.circleStartArgb = circleStartArgb; this.circleEndArgb = circleEndArgb;
            this.circleEdgeArgb = circleEdgeArgb; this.textArgb = textArgb;
            this.startMillis = System.currentTimeMillis();
            this.deadlineNanos = System.nanoTime() + durationMillis * 1000000L;
        }

        public long remainingMillis() {
            long nanos = deadlineNanos - System.nanoTime();
            return nanos <= 0 ? 0 : (nanos + 999999L) / 1000000L;
        }
        public float remainingFraction() {
            if (durationMillis <= 0) return 0;
            float ratio = remainingMillis() / (float) durationMillis;
            return ratio < 0 ? 0 : ratio > 1 ? 1 : ratio;
        }
        public boolean isExpired() { return System.nanoTime() >= deadlineNanos; }
    }

    private static final Object LOCK = new Object();
    private static Map<String, Entry> entries = new LinkedHashMap<>();
    private CooldownState() { }

    public static void display(String name, long durationMillis, ItemStack icon,
                               int circleStartArgb, int circleEndArgb, int circleEdgeArgb, int textArgb) {
        Entry replacement = new Entry(name, durationMillis, icon, circleStartArgb, circleEndArgb, circleEdgeArgb, textArgb);
        synchronized (LOCK) {
            Map<String, Entry> copy = new LinkedHashMap<>(entries);
            copy.put(name, replacement);
            entries = copy;
        }
    }
    public static void remove(String name) {
        synchronized (LOCK) {
            if (!entries.containsKey(name)) return;
            Map<String, Entry> copy = new LinkedHashMap<>(entries);
            copy.remove(name); entries = copy;
        }
    }
    public static void resetAll() { synchronized (LOCK) { entries = new LinkedHashMap<>(); } }
    public static List<Entry> active() {
        synchronized (LOCK) {
            List<Entry> alive = new ArrayList<>();
            Map<String, Entry> retained = new LinkedHashMap<>();
            for (Map.Entry<String, Entry> item : entries.entrySet()) {
                if (!item.getValue().isExpired()) { alive.add(item.getValue()); retained.put(item.getKey(), item.getValue()); }
            }
            if (retained.size() != entries.size()) entries = retained;
            return alive;
        }
    }
}
