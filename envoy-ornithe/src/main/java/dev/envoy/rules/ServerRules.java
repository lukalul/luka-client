package dev.envoy.rules;

import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Thread-safe aggregate of restrictions advertised by the current server. */
public final class ServerRules {
    private static final Map<String, Boolean> BADLION_DISABLED = new LinkedHashMap<String, Boolean>();
    private static final Set<String> BADLION_LIMITED = new LinkedHashSet<String>();
    private static final Map<String, Apollo> APOLLO = new LinkedHashMap<String, Apollo>();
    private static List<String> whitelist = Collections.emptyList();
    private static boolean autoTextSeen;
    private static boolean autoTextDisabled;
    private static long revision;

    private ServerRules() { }

    public static synchronized void reset() {
        if (BADLION_DISABLED.isEmpty() && BADLION_LIMITED.isEmpty() && APOLLO.isEmpty() && !autoTextSeen) return;
        BADLION_DISABLED.clear(); BADLION_LIMITED.clear(); APOLLO.clear(); whitelist = Collections.emptyList();
        autoTextSeen = false; autoTextDisabled = false; revision++;
    }

    static synchronized void updateBadlion(Map<String, Boolean> disabled, Set<String> limited, boolean seenAutoText,
                                            boolean disabledAutoText, List<String> commands) {
        Map<String, Boolean> copy = new LinkedHashMap<String, Boolean>(disabled);
        List<String> list = Collections.unmodifiableList(new ArrayList<String>(commands));
        if (BADLION_DISABLED.equals(copy) && BADLION_LIMITED.equals(limited) && autoTextSeen == seenAutoText && autoTextDisabled == disabledAutoText
                && whitelist.equals(list)) return;
        BADLION_DISABLED.clear(); BADLION_DISABLED.putAll(copy);
        BADLION_LIMITED.clear(); BADLION_LIMITED.addAll(limited);
        autoTextSeen = seenAutoText; autoTextDisabled = disabledAutoText; whitelist = list; revision++;
    }

    public static synchronized void updateApollo(String module, boolean enable, Map<String, Value> properties) {
        String id = lower(module);
        Apollo next = new Apollo(enable, new LinkedHashMap<String, Value>(properties));
        if (next.equals(APOLLO.get(id))) return;
        APOLLO.put(id, next); revision++;
    }

    public static synchronized void resetApollo() {
        if (APOLLO.isEmpty()) return;
        APOLLO.clear(); revision++;
    }

    /** Letters and digits only, lower case: "Armor Status", "armor_status" and "ArmorStatus" are the same mod. */
    static String norm(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) out.append(c);
        }
        return out.toString();
    }

    /** Every mod the server has switched off, as normalized ids (Badlion's disabled mods and Apollo mod_setting "<mod>.enabled"=false). */
    public static synchronized Set<String> disabledMods() {
        Set<String> out = new LinkedHashSet<String>();
        for (Map.Entry<String, Boolean> e : BADLION_DISABLED.entrySet()) if (Boolean.TRUE.equals(e.getValue())) out.add(norm(e.getKey()));
        Apollo settings = APOLLO.get("mod_setting");
        if (settings != null) {
            for (Map.Entry<String, Value> e : settings.properties.entrySet()) {
                String key = e.getKey();
                Boolean value = bool(e.getValue());
                if (!Boolean.FALSE.equals(value)) continue;
                if (key.endsWith(".enabled")) key = key.substring(0, key.length() - ".enabled".length());
                else if (key.indexOf('.') >= 0) continue; // some other setting of a mod, not the mod itself
                out.add(norm(key));
            }
        }
        out.remove("");
        return out;
    }

    public static synchronized boolean modDisabled(String id) {
        String key = lower(id);
        if (disabledMods().contains(norm(id))) return true;
        if (Boolean.TRUE.equals(BADLION_DISABLED.get(key))) return true;
        Apollo settings = APOLLO.get("mod_setting");
        if (settings != null) {
            Boolean direct = bool(settings.properties.get(key));
            Boolean enabled = bool(settings.properties.get(key + ".enabled"));
            if (Boolean.FALSE.equals(direct) || Boolean.FALSE.equals(enabled)) return true;
        }
        return false;
    }

    public static synchronized boolean macrosBlocked(String text) {
        String input = text == null ? "" : text.trim();
        if (autoTextSeen) {
            if (autoTextDisabled) return true;
            if (!whitelist.isEmpty()) {
                String first = firstWord(input);
                boolean allowed = false;
                for (String command : whitelist) if (first.equalsIgnoreCase(command)) { allowed = true; break; }
                if (!allowed) return true;
            }
        }
        Apollo auto = APOLLO.get("auto_text_hotkey");
        if (auto == null) return false;
        if (!auto.enable || trueValue(auto, "block-text-inputs")) return true;
        if (!input.startsWith("/") && trueValue(auto, "block-chat-message-text-inputs")) return true;
        Value blocked = auto.properties.get("blocked-text-inputs");
        if (blocked != null && blocked.getKindCase() == Value.KindCase.LIST_VALUE) {
            String lower = input.toLowerCase(Locale.ROOT);
            for (Value prefix : blocked.getListValue().getValuesList())
                if (prefix.getKindCase() == Value.KindCase.STRING_VALUE
                        && lower.startsWith(prefix.getStringValue().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public static synchronized String macroBlockReason() {
        if (autoTextSeen && autoTextDisabled) return "Macros are disabled";
        if (autoTextSeen && !whitelist.isEmpty()) return "Only approved commands may be sent";
        Apollo auto = APOLLO.get("auto_text_hotkey");
        if (auto != null && !auto.enable) return "Macros are disabled";
        return "This macro is restricted";
    }

    public static synchronized List<String> summary() {
        List<String> out = new ArrayList<String>();
        addMod(out, "freelook", "Freelook"); addMod(out, "minimap", "MiniMap");
        addMod(out, "schematica", "Schematica"); addMod(out, "togglesprint", "ToggleSprint");
        addMod(out, "togglesneak", "ToggleSneak"); addMod(out, "animations", "Animations");
        if (!modDisabled("togglesprint") && BADLION_LIMITED.contains("togglesprint")) out.add("ToggleSprint (limited)");
        if (!modDisabled("togglesneak") && BADLION_LIMITED.contains("togglesneak")) out.add("ToggleSneak (limited)");
        java.util.Set<String> listed = new java.util.HashSet<String>();
        for (String known : new String[] {"freelook", "minimap", "schematica", "togglesprint", "togglesneak", "animations"}) listed.add(known);
        for (String mod : disabledMods()) if (!listed.contains(mod)) out.add(mod);
        if (autoTextSeen && autoTextDisabled) out.add("Macros");
        else if (autoTextSeen && !whitelist.isEmpty()) out.add("Macros: only " + join(whitelist));
        else {
            Apollo auto = APOLLO.get("auto_text_hotkey");
            if (auto != null && (!auto.enable || trueValue(auto, "block-text-inputs"))) out.add("Macros");
            else if (auto != null && (trueValue(auto, "block-chat-message-text-inputs") || auto.properties.containsKey("blocked-text-inputs"))) out.add("Some macros");
        }
        return Collections.unmodifiableList(out);
    }

    public static synchronized long version() { return revision; }

    public static synchronized Object serverRule(String key) {
        Apollo rules = APOLLO.get("server_rule");
        return rules == null ? null : javaValue(rules.properties.get(key));
    }

    private static void addMod(List<String> out, String id, String label) { if (modDisabled(id)) out.add(label); }
    private static boolean trueValue(Apollo a, String key) { return Boolean.TRUE.equals(bool(a.properties.get(key))); }
    private static Boolean bool(Value value) { return value != null && value.getKindCase() == Value.KindCase.BOOL_VALUE ? value.getBoolValue() : null; }
    private static String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("perspective", "freelook"); }
    private static String firstWord(String text) { int space = text.indexOf(' '); return space < 0 ? text : text.substring(0, space); }
    private static String join(List<String> values) { StringBuilder s = new StringBuilder(); for (String v : values) { if (s.length() > 0) s.append(' '); s.append(v); } return s.toString(); }
    private static Object javaValue(Value v) {
        if (v == null) return null;
        switch (v.getKindCase()) {
            case BOOL_VALUE: return v.getBoolValue(); case NUMBER_VALUE: return v.getNumberValue();
            case STRING_VALUE: return v.getStringValue(); case NULL_VALUE: return null;
            case LIST_VALUE: List<Object> list = new ArrayList<Object>(); for (Value x : v.getListValue().getValuesList()) list.add(javaValue(x)); return Collections.unmodifiableList(list);
            case STRUCT_VALUE: Map<String,Object> map = new LinkedHashMap<String,Object>(); for (Map.Entry<String,Value> e : v.getStructValue().getFieldsMap().entrySet()) map.put(e.getKey(), javaValue(e.getValue())); return Collections.unmodifiableMap(map);
            default: return null;
        }
    }

    private static final class Apollo {
        final boolean enable; final Map<String, Value> properties;
        Apollo(boolean enable, Map<String, Value> properties) { this.enable = enable; this.properties = properties; }
        @Override public boolean equals(Object o) { return o instanceof Apollo && enable == ((Apollo)o).enable && properties.equals(((Apollo)o).properties); }
        @Override public int hashCode() { return properties.hashCode() * 31 + (enable ? 1 : 0); }
    }
}
