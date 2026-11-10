package dev.envoy.rules;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

/** Reader for badlion:mods and badlion:modapi restriction JSON. */
public final class BadlionRules {
    private BadlionRules() { }

    @SuppressWarnings("unchecked")
    public static void parse(byte[] payload) {
        Object rootValue = SimpleJson.parse(new String(payload, StandardCharsets.UTF_8));
        if (!(rootValue instanceof Map)) throw new IllegalArgumentException("Badlion rules are not an object");
        Map<String,Object> root = (Map<String,Object>) rootValue;
        Object wrapped = root.get("modsDisallowed");
        Map<String,Object> mods = wrapped instanceof Map ? (Map<String,Object>) wrapped : root;
        Map<String,Boolean> disabled = new LinkedHashMap<String,Boolean>();
        Set<String> limited = new LinkedHashSet<String>();
        boolean autoSeen = false, autoDisabled = false;
        List<String> commands = new ArrayList<String>();
        for (Map.Entry<String,Object> entry : mods.entrySet()) {
            if (!(entry.getValue() instanceof Map)) continue;
            String id = entry.getKey().toLowerCase(Locale.ROOT);
            if ("perspective".equals(id)) id = "freelook";
            Map<String,Object> config = (Map<String,Object>) entry.getValue();
            boolean off = Boolean.TRUE.equals(config.get("disabled"));
            disabled.put(id, off);
            Object extra = config.get("extra_data");
            if (!off && extra instanceof Map && containsDisabled((Map<String,Object>)extra)) limited.add(id);
            if ("autotext".equals(id)) {
                autoSeen = true; autoDisabled = off;
                Object settings = config.get("settings");
                if (settings instanceof Map) {
                    Object list = ((Map<String,Object>) settings).get("whitelistedCommands");
                    if (list instanceof List) for (Object value : (List<Object>) list) if (value instanceof String) commands.add((String)value);
                }
            }
        }
        ServerRules.updateBadlion(disabled, limited, autoSeen, autoDisabled, commands);
    }

    @SuppressWarnings("unchecked") private static boolean containsDisabled(Map<String,Object> map) {
        for (Object value : map.values()) {
            if (value instanceof Map) {
                Map<String,Object> nested = (Map<String,Object>)value;
                if (Boolean.TRUE.equals(nested.get("disabled")) || containsDisabled(nested)) return true;
            }
        }
        return false;
    }
}
