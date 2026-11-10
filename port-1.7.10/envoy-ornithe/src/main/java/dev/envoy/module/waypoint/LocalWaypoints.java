package dev.envoy.module.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.envoy.Envoy;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent player-owned waypoints, partitioned by server and dimension. */
public final class LocalWaypoints {
    public static final class Waypoint {
        public String id;
        public String name;
        public int x;
        public int y;
        public int z;
        public int colorArgb;
        public boolean visible;
        public int dimension;
        public long createdTime;

        public Waypoint() { }

        public Waypoint(String id, String name, int x, int y, int z, int colorArgb,
                        boolean visible, int dimension, long createdTime) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.colorArgb = colorArgb;
            this.visible = visible;
            this.dimension = dimension;
            this.createdTime = createdTime;
        }

        public Waypoint copy() {
            return new Waypoint(id, name, x, y, z, colorArgb, visible, dimension, createdTime);
        }
    }

    /* Field names are the on-disk JSON schema. */
    private static final class Data {
        Map<String, Map<String, List<Waypoint>>> servers =
            new HashMap<String, Map<String, List<Waypoint>>>();
    }

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static File file;

    private LocalWaypoints() { }

    public static synchronized void init(File configDir) {
        file = new File(new File(configDir, "envoy"), "waypoints.json");
        load();
    }

    public static synchronized void load() {
        data = readStore();
    }

    private static Data readStore() {
        if (file == null || !file.isFile()) return new Data();
        try (FileReader reader = new FileReader(file)) {
            Data parsed = JSON.fromJson(reader, Data.class);
            return parsed == null || parsed.servers == null ? new Data() : parsed;
        } catch (Exception failure) {
            Envoy.LOGGER.warn("[Waypoints] corrupt store; backing it up and starting empty", failure);
            backupBrokenStore();
            return new Data();
        }
    }

    private static void backupBrokenStore() {
        try {
            Files.copy(file.toPath(), new File(file.getPath() + ".bak").toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception failure) {
            Envoy.LOGGER.warn("[Waypoints] could not create backup: {}", failure.toString());
        }
    }

    public static synchronized void save() {
        if (file == null) return;
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        File staging = new File(file.getPath() + ".tmp");
        if (!writeStagingFile(staging)) return;
        try {
            Files.move(staging.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception failure) {
            Envoy.LOGGER.warn("[Waypoints] could not replace store: {}", failure.toString());
        }
    }

    private static boolean writeStagingFile(File staging) {
        try (FileWriter writer = new FileWriter(staging)) {
            JSON.toJson(data, writer);
            return true;
        } catch (Exception failure) {
            Envoy.LOGGER.warn("[Waypoints] could not write: {}", failure.toString());
            return false;
        }
    }

    public static synchronized Waypoint add(String name, int x, int y, int z, int colorArgb) {
        Waypoint created = new Waypoint(UUID.randomUUID().toString(), name, x, y, z, colorArgb,
            true, currentDimension(), System.currentTimeMillis());
        listForCurrentContext(true).add(created);
        save();
        return created.copy();
    }

    public static synchronized void update(Waypoint changed) {
        Waypoint stored = findById(changed.id);
        if (stored == null) return;
        stored.name = changed.name;
        stored.x = changed.x;
        stored.y = changed.y;
        stored.z = changed.z;
        stored.colorArgb = changed.colorArgb;
        stored.visible = changed.visible;
        save();
    }

    public static synchronized boolean remove(String id) {
        List<Waypoint> waypoints = listForCurrentContext(false);
        for (java.util.Iterator<Waypoint> it = waypoints.iterator(); it.hasNext();) {
            if (same(it.next().id, id)) {
                it.remove();
                save();
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean removeByName(String name) {
        Waypoint waypoint = findByName(name);
        return waypoint != null && remove(waypoint.id);
    }

    public static synchronized boolean toggleByName(String name) {
        Waypoint waypoint = findByName(name);
        if (waypoint == null) throw new IllegalArgumentException("No waypoint named " + name);
        waypoint.visible = !waypoint.visible;
        save();
        return waypoint.visible;
    }

    public static synchronized void setVisible(String id, boolean visible) {
        Waypoint waypoint = findById(id);
        if (waypoint == null) return;
        waypoint.visible = visible;
        save();
    }

    /** True when the current server and dimension have at least one waypoint (no copy is made). */
    public static synchronized boolean hasCurrent() {
        return !listForCurrentContext(false).isEmpty();
    }

    public static synchronized List<Waypoint> current() {
        List<Waypoint> snapshot = new ArrayList<Waypoint>();
        for (Waypoint waypoint : listForCurrentContext(false)) snapshot.add(waypoint.copy());
        return snapshot;
    }

    public static synchronized void trimDeaths(int keep) {
        List<Waypoint> deaths = new ArrayList<Waypoint>();
        for (Waypoint waypoint : listForCurrentContext(false)) {
            if (waypoint.name.startsWith("Death (")) deaths.add(waypoint);
        }
        Collections.sort(deaths, new Comparator<Waypoint>() {
            @Override public int compare(Waypoint left, Waypoint right) {
                return Long.compare(left.createdTime, right.createdTime);
            }
        });
        int excess = deaths.size() - Math.max(0, keep);
        for (int i = 0; i < excess; i++) remove(deaths.get(i).id);
    }

    private static Waypoint findById(String id) {
        for (Waypoint waypoint : listForCurrentContext(false)) {
            if (same(waypoint.id, id)) return waypoint;
        }
        return null;
    }

    private static Waypoint findByName(String name) {
        for (Waypoint waypoint : listForCurrentContext(false)) {
            if (waypoint.name.equalsIgnoreCase(name)) return waypoint;
        }
        return null;
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static List<Waypoint> listForCurrentContext(boolean create) {
        String server = currentServer();
        Map<String, List<Waypoint>> dimensions = data.servers.get(server);
        if (dimensions == null) {
            if (!create) return Collections.emptyList();
            dimensions = new HashMap<String, List<Waypoint>>();
            data.servers.put(server, dimensions);
        }
        String dimension = Integer.toString(currentDimension());
        List<Waypoint> waypoints = dimensions.get(dimension);
        if (waypoints == null) {
            if (!create) return Collections.emptyList();
            waypoints = new ArrayList<Waypoint>();
            dimensions.put(dimension, waypoints);
        }
        return waypoints;
    }

    public static String currentServer() {
        Minecraft minecraft = Minecraft.getMinecraft();
        try {
            if (minecraft.isSingleplayer() && minecraft.getIntegratedServer() != null) {
                return "singleplayer:" + minecraft.getIntegratedServer().getFolderName();
            }
            if (minecraft.getCurrentServerData() != null) {
                return "server:" + minecraft.getCurrentServerData().serverIP.toLowerCase();
            }
        } catch (Throwable failure) {
            Envoy.problem("waypoint context", failure);
        }
        return "unknown";
    }

    public static int currentDimension() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft.theWorld == null ? 0 : minecraft.theWorld.provider.dimensionId;
    }
}
