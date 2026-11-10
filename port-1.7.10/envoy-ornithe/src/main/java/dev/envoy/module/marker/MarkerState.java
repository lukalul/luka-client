package dev.envoy.module.marker;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.item.ItemStack;

/** Thread-safe marker registry shared by network, GUI, and render code. */
public final class MarkerState {
    public static final int TYPE_NORMAL = 1, TYPE_DANGER = 2, TYPE_INFO = 3, TYPE_INTEREST = 4;
    public static final int COND_UNSPECIFIED = 0, COND_NEVER = 1, COND_HOVER = 2, COND_ALWAYS = 3;
    public static final int OWNER_HEAD = 1, OWNER_NAME = 2;
    public static final int TARGET_NONE = 0, TARGET_ITEM = 1, TARGET_BLOCK = 2, TARGET_ENTITY = 3, TARGET_PLAYER = 4;

    private static final Map<String, Marker> ACTIVE = new ConcurrentHashMap<>();

    private MarkerState() { }
    public static void display(Marker marker) { ACTIVE.put(marker.id, marker); }
    public static void remove(String id) { ACTIVE.remove(id); }
    public static void resetAll() { ACTIVE.clear(); }
    public static Collection<Marker> all() { return ACTIVE.values(); }

    public static final class Target {
        public final int type;
        public final ItemStack item;
        public final String entityType;
        public final UUID playerId;
        public final String playerName;

        public Target(int type, ItemStack item, String entityType, UUID playerId, String playerName) {
            this.type = type;
            this.item = item;
            this.entityType = entityType;
            this.playerId = playerId;
            this.playerName = playerName;
        }

        public static Target none() { return new Target(TARGET_NONE, null, null, null, null); }
    }

    public static final class Marker {
        public final String id, ownerName;
        public final double x, y, z;
        public final int type, iconArgb;
        public final Target target;
        public final int showOwner, showCoordinates, showDistance, showDescription, ownerDisplay;
        public final boolean textShadow;
        public final float scale;

        public Marker(String id, String ownerName, double x, double y, double z, int type, int iconArgb,
                      Target target, int showOwner, int showCoordinates, int showDistance, int showDescription,
                      int ownerDisplay, boolean textShadow, float scale) {
            this.id = id; this.ownerName = ownerName;
            this.x = x; this.y = y; this.z = z;
            this.type = type; this.iconArgb = iconArgb; this.target = target;
            this.showOwner = showOwner; this.showCoordinates = showCoordinates;
            this.showDistance = showDistance; this.showDescription = showDescription;
            this.ownerDisplay = ownerDisplay; this.textShadow = textShadow; this.scale = scale;
        }
    }
}
