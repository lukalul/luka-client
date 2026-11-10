package dev.envoy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/** Persistent values shared by Envoy's screens and renderers. */
public final class EnvoyConfig {
    public enum CooldownDirection {
        RIGHT, LEFT, DOWN, UP;

        public CooldownDirection next() {
            CooldownDirection[] directions = values();
            return directions[(ordinal() + 1) % directions.length];
        }

        public String translationKey() {
            return "envoy.direction." + name().toLowerCase();
        }
    }

    public static float cooldownX = 0.5F;
    public static float cooldownY = 0.72F;
    public static CooldownDirection cooldownDirection = CooldownDirection.RIGHT;
    public static final float DEFAULT_COOLDOWN_X = 0.5F;
    public static final float DEFAULT_COOLDOWN_Y = 0.72F;

    private interface ValueBinding { String key(); String current(); void accept(String encoded); }
    private interface BooleanSource { boolean get(); }
    private interface BooleanSink { void set(boolean value); }
    private interface IntSource { int get(); }
    private interface IntSink { void set(int value); }
    private interface FloatSource { float get(); }
    private interface FloatSink { void set(float value); }
    private interface TextSource { String get(); }
    private interface TextSink { void set(String value); }

    private static File configFile;

    private EnvoyConfig() { }

    public static void init(File configDir) {
        configFile = new File(configDir, "envoy.properties");
        load();
    }

    public static void load() {
        Properties source = readFile();
        if (source == null) return;
        for (ValueBinding binding : bindings()) {
            String encoded = source.getProperty(binding.key());
            if (encoded != null) binding.accept(encoded);
        }
    }

    public static void save() {
        File destination = configFile;
        if (destination == null) return;
        Properties snapshot = new Properties();
        for (ValueBinding binding : bindings()) snapshot.setProperty(binding.key(), binding.current());
        File directory = destination.getParentFile();
        if (directory != null) directory.mkdirs();
        try (FileOutputStream stream = new FileOutputStream(destination)) {
            snapshot.store(stream, "Envoy configuration");
        } catch (Exception failure) {
            Envoy.LOGGER.warn("[Config] could not write: {}", failure.toString());
        }
    }

    private static Properties readFile() {
        File source = configFile;
        if (source == null || !source.exists()) return null;
        Properties result = new Properties();
        try (FileInputStream stream = new FileInputStream(source)) {
            result.load(stream);
            return result;
        } catch (Exception failure) {
            Envoy.LOGGER.warn("[Config] could not read: {}", failure.toString());
            return null;
        }
    }

    private static ValueBinding[] bindings() {
        return new ValueBinding[] {
            decimal("cooldownX", () -> cooldownX, value -> cooldownX = value),
            decimal("cooldownY", () -> cooldownY, value -> cooldownY = value), direction(),
            flag("renderSelf", () -> EnvoySettings.renderSelf, value -> EnvoySettings.renderSelf = value),
            flag("waypoints", () -> EnvoySettings.waypoints, value -> EnvoySettings.waypoints = value),
            flag("markers", () -> EnvoySettings.markers, value -> EnvoySettings.markers = value),
            flag("nametags", () -> EnvoySettings.nametags, value -> EnvoySettings.nametags = value),
            flag("glow", () -> EnvoySettings.glow, value -> EnvoySettings.glow = value),
            flag("teamView", () -> EnvoySettings.teamView, value -> EnvoySettings.teamView = value),
            flag("cooldowns", () -> EnvoySettings.cooldowns, value -> EnvoySettings.cooldowns = value),
            flag("holograms", () -> EnvoySettings.holograms, value -> EnvoySettings.holograms = value),
            flag("beams", () -> EnvoySettings.beams, value -> EnvoySettings.beams = value),
            flag("border", () -> EnvoySettings.border, value -> EnvoySettings.border = value),
            flag("notifications", () -> EnvoySettings.notifications, value -> EnvoySettings.notifications = value),
            flag("coloredFire", () -> EnvoySettings.coloredFire, value -> EnvoySettings.coloredFire = value),
            flag("limbs", () -> EnvoySettings.limbs, value -> EnvoySettings.limbs = value),
            flag("vignette", () -> EnvoySettings.vignette, value -> EnvoySettings.vignette = value),
            flag("customModels", () -> EnvoySettings.customModels, value -> EnvoySettings.customModels = value),
            flag("chat", () -> EnvoySettings.chat, value -> EnvoySettings.chat = value),
            flag("lightning", () -> EnvoySettings.lightning, value -> EnvoySettings.lightning = value),
            flag("bossBars", () -> EnvoySettings.bossBars, value -> EnvoySettings.bossBars = value),
            flag("disableMissPenalty", () -> EnvoySettings.disableMissPenalty, value -> EnvoySettings.disableMissPenalty = value),
            flag("disableBlockMissPenalty", () -> EnvoySettings.disableBlockMissPenalty, value -> EnvoySettings.disableBlockMissPenalty = value),
            flag("allowDigAndUse", () -> EnvoySettings.allowDigAndUse, value -> EnvoySettings.allowDigAndUse = value),
            flag("waypointBeam", () -> EnvoySettings.waypointBeam, value -> EnvoySettings.waypointBeam = value),
            flag("waypointName", () -> EnvoySettings.waypointName, value -> EnvoySettings.waypointName = value),
            flag("waypointDistance", () -> EnvoySettings.waypointDistance, value -> EnvoySettings.waypointDistance = value),
            decimal("waypointTextScale", () -> EnvoySettings.waypointTextScale, value -> EnvoySettings.waypointTextScale = value),
            integer("waypointMaxDistance", () -> EnvoySettings.waypointMaxDistance, value -> EnvoySettings.waypointMaxDistance = value),
            decimal("waypointBeamOpacity", () -> EnvoySettings.waypointBeamOpacity, value -> EnvoySettings.waypointBeamOpacity = value),
            integer("waypointKeyCode", () -> EnvoySettings.waypointKeyCode, value -> EnvoySettings.waypointKeyCode = value),
            integer("waypointManageKeyCode", () -> EnvoySettings.waypointManageKeyCode, value -> EnvoySettings.waypointManageKeyCode = value),
            flag("deathWaypoints", () -> EnvoySettings.deathWaypoints, value -> EnvoySettings.deathWaypoints = value),
            integer("deathWaypointCount", () -> EnvoySettings.deathWaypointCount, value -> EnvoySettings.deathWaypointCount = value),
            integer("defaultWaypointColor", () -> EnvoySettings.defaultWaypointColor, value -> EnvoySettings.defaultWaypointColor = value)
        };
    }

    private static ValueBinding flag(String key, final BooleanSource source, final BooleanSink sink) {
        return binding(key, () -> Boolean.toString(source.get()), encoded -> sink.set(Boolean.parseBoolean(encoded)));
    }

    private static ValueBinding integer(String key, final IntSource source, final IntSink sink) {
        return binding(key, () -> Integer.toString(source.get()), encoded -> {
            try { sink.set(Integer.parseInt(encoded)); } catch (NumberFormatException ignored) { }
        });
    }

    private static ValueBinding decimal(String key, final FloatSource source, final FloatSink sink) {
        return binding(key, () -> Float.toString(source.get()), encoded -> {
            try { sink.set(Float.parseFloat(encoded)); } catch (NumberFormatException ignored) { }
        });
    }

    private static ValueBinding direction() {
        return binding("cooldownDirection", () -> cooldownDirection.name(), encoded -> {
            try { cooldownDirection = CooldownDirection.valueOf(encoded); } catch (IllegalArgumentException ignored) { }
        });
    }

    private static ValueBinding binding(final String key, final TextSource source, final TextSink sink) {
        return new ValueBinding() {
            public String key() { return key; }
            public String current() { return source.get(); }
            public void accept(String encoded) { sink.set(encoded); }
        };
    }
}
