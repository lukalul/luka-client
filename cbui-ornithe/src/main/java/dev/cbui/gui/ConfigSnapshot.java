package dev.cbui.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

/** Minecraft-free rows for settings the menu shows but luka does not own (keybinds other mods register). */
public final class ConfigSnapshot {
    private static final Set<String> REPORTED=new HashSet<>();
    public enum Kind { TOGGLE, SLIDER, NUMBER, TEXT, DROPDOWN, COLOR, KEYBIND, BUTTON, INFO, UNSUPPORTED }
    public enum Display { SHOWN, DISABLED, HIDDEN }

    public interface Accessor {
        Object get() throws Exception;
        boolean set(Object value) throws Exception;
        Display display() throws Exception;
        void save() throws Exception;
        void openFallback() throws Exception;
    }

    public static final class Row {
        public final Kind kind;
        public final String id, label, tooltip;
        public final double min, max, step;
        public final List<String> options;
        public final boolean noAlpha;
        public final Object defaultValue;
        private final Accessor accessor;

        public Row(Kind kind, String id, String label, String tooltip, double min, double max, double step,
                   List<String> options, boolean noAlpha, Object defaultValue, Accessor accessor) {
            this.kind = kind; this.id = clean(id); this.label = KeyText.words(clean(label)); this.tooltip = KeyText.hint(clean(tooltip));
            this.min = min; this.max = max; this.step = step;
            this.options = options == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<>(options));
            this.noAlpha = noAlpha; this.defaultValue = defaultValue; this.accessor = accessor;
        }

        public Object get() { try { return accessor.get(); } catch (Throwable e) { reportOnce("read " + id, e); return null; } }
        public boolean set(Object value) {
            try { boolean changed = accessor.set(value); if (changed) accessor.save(); return changed; }
            catch (Throwable e) { reportOnce("write " + id, e); return false; }
        }
        public boolean setLive(Object value) {
            try { return accessor.set(value); } catch (Throwable e) { reportOnce("write " + id, e); return false; }
        }
        public void save() { try { accessor.save(); } catch (Throwable e) { reportOnce("save " + id, e); } }
        public Display display() { if ((label == null || label.trim().isEmpty()) && kind != Kind.INFO) return Display.HIDDEN; try { return accessor.display(); } catch (Throwable e) { reportOnce("display " + id, e); return Display.HIDDEN; } }
        public boolean reset() { return defaultValue != null && set(defaultValue); }
        public void openFallback() { try { accessor.openFallback(); } catch (Throwable e) { reportOnce("fallback " + id, e); } }
    }

    public static final class Section {
        public final String name;
        public final List<Row> rows;
        public Section(String name, List<Row> rows) { this.name = KeyText.words(clean(name)); this.rows = Collections.unmodifiableList(new ArrayList<>(rows)); }
    }

    public static final class Mod {
        public final String id, name, category;
        public final List<Section> sections;
        private final String search;
        public Mod(String id, String name, String category, List<Section> sections) {
            this.id=clean(id); this.name=clean(name); this.category=clean(category); this.sections=Collections.unmodifiableList(new ArrayList<>(sections));
            StringBuilder text=new StringBuilder(this.name).append(' ').append(this.category);
            for(Section section:sections){text.append(' ').append(section.name);for(Row row:section.rows)text.append(' ').append(row.label).append(' ').append(row.tooltip);}
            search=text.toString().toLowerCase(Locale.ROOT);
        }
        public boolean matches(String query) { return query == null || query.trim().isEmpty() || search.contains(query.trim().toLowerCase(Locale.ROOT)); }
        public int visibleRows() { int count=0; for(Section s:sections) for(Row r:s.rows) if(r.display()!=Display.HIDDEN) count++; return count; }
    }

    public final List<Mod> mods;
    public ConfigSnapshot(List<Mod> mods) { this.mods=Collections.unmodifiableList(new ArrayList<>(mods)); }
    public List<Mod> search(String query) { List<Mod> out=new ArrayList<>(); for(Mod mod:mods)if(mod.matches(query))out.add(mod); return out; }
    public static ConfigSnapshot empty() { return new ConfigSnapshot(Collections.<Mod>emptyList()); }
    private static synchronized void reportOnce(String area,Throwable error){if(!REPORTED.add(area))return;System.err.println("[CBUI] Skipping failing setting row "+area);error.printStackTrace();}
    private static String clean(Object value) { return value == null ? "" : String.valueOf(value); }
}
