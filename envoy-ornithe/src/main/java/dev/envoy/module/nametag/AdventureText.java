package dev.envoy.module.nametag;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdventureText {
    private static final Map<String, Integer> COLORS = createColors();
    private AdventureText() { }

    public static final class Run {
        public final String display;
        public final int rgb;
        public Run(String display, int rgb) { this.display = display; this.rgb = rgb; }
    }

    public static List<Run> parse(String json) {
        List<Run> result = new ArrayList<Run>();
        try {
            flatten(new JsonParser().parse(json), Format.DEFAULT, result);
        } catch (RuntimeException malformed) {
            result.clear();
            result.add(new Run(json, 0xFFFFFF));
        }
        return result.isEmpty() ? Collections.<Run>emptyList() : result;
    }

    private static void flatten(JsonElement element, Format inherited, List<Run> result) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonPrimitive()) {
            emit(element.getAsString(), inherited, result);
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) flatten(child, inherited, result);
            return;
        }
        JsonObject object = element.getAsJsonObject();
        Format local = inherited.with(object);
        if (object.has("text")) emit(object.get("text").getAsString(), local, result);
        if (object.has("extra") && object.get("extra").isJsonArray()) {
            for (JsonElement child : object.getAsJsonArray("extra")) flatten(child, local, result);
        }
    }

    private static void emit(String text, Format format, List<Run> result) {
        if (!text.isEmpty()) result.add(new Run(format.prefix() + text, format.rgb));
    }

    private static Integer color(String name) {
        if (name == null || name.isEmpty()) return null;
        if (name.charAt(0) != '#') return COLORS.get(name.toLowerCase(Locale.ROOT));
        try { return Integer.valueOf(Integer.parseInt(name.substring(1), 16) & 0xFFFFFF); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static Map<String, Integer> createColors() {
        Map<String, Integer> colors = new HashMap<String, Integer>();
        String[] names = {"black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple","gold","gray",
            "dark_gray","blue","green","aqua","red","light_purple","yellow","white"};
        int[] values = {0x000000,0x0000AA,0x00AA00,0x00AAAA,0xAA0000,0xAA00AA,0xFFAA00,0xAAAAAA,
            0x555555,0x5555FF,0x55FF55,0x55FFFF,0xFF5555,0xFF55FF,0xFFFF55,0xFFFFFF};
        for (int i = 0; i < names.length; i++) colors.put(names[i], values[i]);
        return Collections.unmodifiableMap(colors);
    }

    private static final class Format {
        static final Format DEFAULT = new Format(0xFFFFFF, false, false, false, false, false);
        final int rgb;
        final boolean bold, italic, underlined, strikethrough, obfuscated;
        Format(int rgb, boolean bold, boolean italic, boolean underlined, boolean strikethrough, boolean obfuscated) {
            this.rgb = rgb; this.bold = bold; this.italic = italic; this.underlined = underlined;
            this.strikethrough = strikethrough; this.obfuscated = obfuscated;
        }
        Format with(JsonObject object) {
            Integer selected = object.has("color") ? color(object.get("color").getAsString()) : null;
            return new Format(selected == null ? rgb : selected,
                flag(object, "bold", bold), flag(object, "italic", italic), flag(object, "underlined", underlined),
                flag(object, "strikethrough", strikethrough), flag(object, "obfuscated", obfuscated));
        }
        String prefix() {
            StringBuilder value = new StringBuilder();
            if (obfuscated) value.append('\u00A7').append('k');
            if (bold) value.append('\u00A7').append('l');
            if (strikethrough) value.append('\u00A7').append('m');
            if (underlined) value.append('\u00A7').append('n');
            if (italic) value.append('\u00A7').append('o');
            return value.toString();
        }
        private static boolean flag(JsonObject object, String key, boolean inherited) {
            return object.has(key) ? object.get(key).getAsBoolean() : inherited;
        }
    }
}
