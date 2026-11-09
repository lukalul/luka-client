package dev.envoy.module.border;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class BorderState {
    public static final class Border {
        private final double[] origin;
        private final double[] destination;
        private final long animationStart;
        private final long animationLength;
        public final int argb;

        private Border(double[] origin, double[] destination, long animationStart, long animationLength, int argb) {
            this.origin = origin;
            this.destination = destination;
            this.animationStart = animationStart;
            this.animationLength = animationLength;
            this.argb = argb;
        }

        public double[] bounds() {
            if (animationLength == 0L) {
                return destination.clone();
            }
            double progress = (System.currentTimeMillis() - animationStart) / (double) animationLength;
            progress = Math.max(0.0D, Math.min(1.0D, progress));
            double[] current = new double[destination.length];
            for (int i = 0; i < current.length; i++) {
                current[i] = origin[i] * (1.0D - progress) + destination[i] * progress;
            }
            return current;
        }
    }

    private static final Object LOCK = new Object();
    private static volatile Map<String, Border> visible = Collections.emptyMap();

    private BorderState() {
    }

    public static void display(String id, double minX, double minZ, double maxX, double maxZ,
                               int argb, boolean animate, int durationTicks) {
        double[] target = {minX, minZ, maxX, maxZ};
        synchronized (LOCK) {
            Border previous = visible.get(id);
            boolean transition = animate && durationTicks > 0 && previous != null;
            double[] start = transition ? previous.bounds() : target.clone();
            long duration = transition ? durationTicks * 50L : 0L;
            Map<String, Border> changed = new HashMap<String, Border>(visible);
            changed.put(id, new Border(start, target, transition ? System.currentTimeMillis() : 0L, duration, argb));
            visible = Collections.unmodifiableMap(changed);
        }
    }

    public static void remove(String id) {
        synchronized (LOCK) {
            Map<String, Border> changed = new HashMap<String, Border>(visible);
            changed.remove(id);
            visible = Collections.unmodifiableMap(changed);
        }
    }

    public static void resetAll() {
        visible = Collections.emptyMap();
    }

    public static Collection<Border> all() {
        return Collections.unmodifiableList(new ArrayList<Border>(visible.values()));
    }

    public static boolean isEmpty() {
        return visible.isEmpty();
    }
}
