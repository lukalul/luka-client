package dev.envoy.module.waypoint;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe state shared by Apollo waypoint handlers and the renderer. */
public final class WaypointState {
    public static final class Waypoint {
        public final String name;
        public final int x;
        public final int y;
        public final int z;
        public final int colorArgb;
        public final boolean showBeam;
        public final boolean highlightBlock;
        public final float lineWidth;
        public final boolean showText;
        public final boolean showDistance;
        public final boolean preventRemoval;
        public volatile boolean hidden;
        public volatile String world;

        public Waypoint(String name, int x, int y, int z, int colorArgb, boolean showBeam,
                        boolean highlightBlock, float lineWidth, boolean showText, boolean showDistance,
                        boolean hidden, boolean preventRemoval) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.colorArgb = colorArgb;
            this.showBeam = showBeam;
            this.highlightBlock = highlightBlock;
            this.lineWidth = lineWidth;
            this.showText = showText;
            this.showDistance = showDistance;
            this.hidden = hidden;
            this.preventRemoval = preventRemoval;
        }
    }

    private static final Map<String, Waypoint> ACTIVE = new ConcurrentHashMap<String, Waypoint>();
    public static volatile String currentWorld;

    private WaypointState() { }

    public static boolean inCurrentWorld(Waypoint waypoint) {
        String required = waypoint.world;
        String actual = currentWorld;
        return isUnspecified(required) || isUnspecified(actual) || required.equals(actual);
    }

    private static boolean isUnspecified(String value) {
        return value == null || value.length() == 0;
    }

    public static void display(Waypoint waypoint) { ACTIVE.put(waypoint.name, waypoint); }

    public static void remove(String name) { ACTIVE.remove(name); }

    public static void resetAll() { ACTIVE.clear(); }

    public static void setHidden(String name, boolean hidden) {
        Waypoint found = ACTIVE.get(name);
        if (found != null) found.hidden = hidden;
    }

    public static Collection<Waypoint> all() { return ACTIVE.values(); }
}
