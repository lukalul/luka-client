package dev.customcolors.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Spawn-egg keys of color.properties: {@code egg.shell.<Entity>}, {@code egg.spots.<Entity>}, {@code egg.<Entity>}. */
public final class EggColours {
    private EggColours() {
    }

    /** Entity string id -> {shell, spots}, each -1 when not given. Invalid values are skipped. */
    public static Map<String, int[]> parse(Properties props) {
        Map<String, int[]> out = new HashMap<String, int[]>();
        if (props == null) {
            return out;
        }
        Map<String, Integer> shortForm = new HashMap<String, Integer>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("egg.")) {
                continue;
            }
            int colour = Hex.rgb(props.getProperty(key));
            if (colour < 0) {
                continue;
            }
            if (key.startsWith("egg.shell.")) {
                slot(out, key.substring(10))[0] = colour;
            } else if (key.startsWith("egg.spots.")) {
                slot(out, key.substring(10))[1] = colour;
            } else {
                shortForm.put(key.substring(4), colour);
            }
        }
        for (Map.Entry<String, Integer> e : shortForm.entrySet()) {
            int[] s = slot(out, e.getKey());
            if (s[0] < 0) {
                s[0] = e.getValue();
            }
        }
        out.remove("");
        return out;
    }

    private static int[] slot(Map<String, int[]> map, String entity) {
        int[] s = map.get(entity);
        if (s == null) {
            s = new int[] {-1, -1};
            map.put(entity, s);
        }
        return s;
    }
}
