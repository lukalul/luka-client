package dev.cbui.server;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Optional, fail-open reflection bridge to Envoy server restrictions. */
public final class ServerRulesBridge {
    private static final String TYPE = "dev.envoy.rules.ServerRules";
    private ServerRulesBridge() { }

    private static volatile long cachedVersion = -1;
    private static volatile Set<String> disabled = Collections.emptySet();
    private static final Map<String, String[]> ALIASES = new HashMap<String, String[]>();
    static {
        // luka module (normalized name) -> the ids other clients' server APIs (Badlion, Apollo) use for the same mod
        alias("freelook", "perspective", "freelook");
        alias("polyzoom", "zoom", "polyzoom");
        alias("motionblur", "motionblur");
        alias("togglesneak", "togglesneak", "sneak");
        alias("alwayssprint", "togglesprint", "sprint", "alwayssprint");
        alias("potionstatus", "potioneffects", "potionstatus", "effects");
        alias("armourstatus", "armorstatus", "armourstatus", "armor");
        alias("potioncounter", "potioncounter", "potcounter");
        alias("coordinates", "coords", "coordinates", "xyz", "position");
        alias("cps", "cps", "cpscounter", "clickspersecond");
        alias("fps", "fps", "fpsdisplay");
        alias("direction", "direction", "compass");
        alias("clock", "clock", "time");
        alias("itemcounter", "itemcounter", "itemcount");
        alias("pearlcooldown", "pearlcooldown", "enderpearlcooldown");
        alias("servertnttimer", "tnttimer", "tntcountdown");
        alias("tntcountdown", "tntcountdown", "tnttimer");
        alias("fovchanger", "fovchanger", "fov", "fovmodifier");
        alias("polyhitbox", "hitboxes", "hitbox", "polyhitbox");
        alias("polycrosshair", "crosshair", "polycrosshair");
        alias("oldanimations", "animations", "oldanimations", "1.7animations");
        alias("combat17feel", "animations", "legacycombat", "oldcombat");
        alias("blockoverlay", "blockoverlay", "blockhighlight");
        alias("waypoints", "waypoints", "waypoint");
        alias("teamview", "teamview", "teammates");
        alias("nametags", "nametags", "nametag");
        alias("fullbright", "fullbright", "gamma", "brightness");
        alias("subframe", "replaymod", "replay", "freecam");
        alias("chatmacros", "autotext", "macros", "chatmacros");
        alias("polytime", "timechanger", "time", "polytime");
        alias("damagetint", "damagetint", "hurtcolor");
        alias("betterhurtcam", "hurtcam", "betterhurtcam");
        alias("smoothskies", "smoothskies");
        alias("soundtweaks", "soundtweaks", "sounds");
        alias("keystrokes", "keystrokes", "keystroke");
        alias("bossbars", "bossbar", "bossbars");
        alias("limbs", "limbs", "limb");
        alias("markers", "markers", "marker");
        alias("beams", "beams", "beam");
        alias("holograms", "holograms", "hologram");
        alias("coloredfire", "coloredfire", "colouredfire");
        alias("worldborder", "worldborder", "border");
        alias("autoreconnect", "autoreconnect", "reconnect");
    }

    private static void alias(String lukaModule, String... ids) { ALIASES.put(lukaModule, ids); }

    private static String norm(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) out.append(c);
        }
        return out.toString();
    }

    /** Once per tick: re-reads the set of mods the server has disabled when the server's rules changed. Cheap otherwise. */
    public static void refresh() {
        long version = version();
        if (version == cachedVersion) return;
        Object value = call("disabledMods", new Class<?>[0]);
        Set<String> next = new HashSet<String>();
        if (value instanceof Set) for (Object o : (Set<?>) value) if (o != null) next.add(String.valueOf(o));
        disabled = next;
        cachedVersion = version;
    }

    /** Changes whenever the server's rules change; modules cache their blocked state against it. */
    public static long cachedVersion() { return cachedVersion; }

    /** True when the server disabled the mod this luka module corresponds to: same name as the server's id, or one of its known aliases. */
    public static boolean moduleBlocked(String moduleName) {
        Set<String> ids = disabled;
        if (ids.isEmpty() || moduleName == null) return false;
        String key = norm(moduleName);
        if (ids.contains(key)) return true;
        String[] aliases = ALIASES.get(key);
        if (aliases != null) for (String id : aliases) if (ids.contains(norm(id))) return true;
        return false;
    }

    public static boolean modDisabled(String id) { Object v = call("modDisabled", new Class<?>[] {String.class}, id); return v instanceof Boolean && (Boolean)v; }
    public static boolean macrosBlocked(String text) { Object v = call("macrosBlocked", new Class<?>[] {String.class}, text); return v instanceof Boolean && (Boolean)v; }
    public static String macroBlockReason() { Object v = call("macroBlockReason", new Class<?>[0]); return v instanceof String ? (String)v : ""; }
    @SuppressWarnings("unchecked") public static List<String> summary() { Object v = call("summary", new Class<?>[0]); return v instanceof List ? (List<String>)v : Collections.<String>emptyList(); }
    public static long version() { Object v = call("version", new Class<?>[0]); return v instanceof Number ? ((Number)v).longValue() : 0L; }

    private static Object call(String name, Class<?>[] parameters, Object... args) {
        try { Class<?> type = Class.forName(TYPE); Method method = type.getMethod(name, parameters); return method.invoke(null, args); }
        catch (Throwable ignored) { return null; }
    }
}
