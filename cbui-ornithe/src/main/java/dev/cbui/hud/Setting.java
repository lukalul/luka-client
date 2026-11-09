package dev.cbui.hud;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Typed, resettable values shared by the HUD, config and menu. */
public abstract class Setting<T> {
    private final String name;
    protected String tooltipText;
    private final T defaultValue;
    private T value;
    private boolean advanced;
    private String group = "";

    protected Setting(String name, T value, String tooltip) {
        this.name = name;
        this.value = value;
        this.defaultValue = value;
        this.tooltipText = tooltip == null ? "" : tooltip;
    }

    public final String name() { return name; }
    public final String tooltip() { return tooltipText; }
    public final T get() { return value; }
    public void set(T value) { this.value = value; }
    public final void reset() { set(defaultValue); }
    /** Cosmetic detail: hidden behind "More options" in the menu. */
    public final boolean isAdvanced() { return advanced; }
    public final T defaultValue() { return defaultValue; }
    public final void markAdvanced() { advanced = true; }
    /** Heading the menu draws above (and a box around) neighbouring settings with the same group. */
    public final String group() { return group; }
    public final void setGroup(String group) { this.group = group == null ? "" : group; }

    /** A key the game itself saves in options.txt, edited from a mod's card. It is not stored in luka's config. */
    public static final class Key extends Setting<Integer> {
        public interface Binding {
            int get();
            void set(int key);
        }
        private Binding binding;
        public Key(String name, int value, String tooltip) { super(name, value, tooltip); }
        public void bind(Binding binding) { this.binding = binding; super.set(binding.get()); }
        public int code() { return binding == null ? get() : binding.get(); }
        @Override public void set(Integer key) { super.set(key); if (binding != null) binding.set(key); }
    }

    public static final class Toggle extends Setting<Boolean> {
        public Toggle(String name, boolean value) { this(name, value, ""); }
        public Toggle(String name, boolean value, String tooltip) { super(name, value, tooltip); }
    }

    public static final class Text extends Setting<String> {
        public Text(String name, String value) { this(name, value, ""); }
        public Text(String name, String value, String tooltip) { super(name, value, tooltip); }
        @Override public void set(String value) { super.set(value == null ? "" : value); }
    }

    public static final class Slider extends Setting<Float> {
        private final float min, max, step;
        public Slider(String name, float value, float min, float max, float step) { this(name, value, min, max, step, ""); }
        public Slider(String name, float value, float min, float max, float step, String tooltip) {
            super(name, value, tooltip); this.min = min; this.max = max; this.step = step; set(value);
        }
        public float min() { return min; }
        public float max() { return max; }
        public float step() { return step; }
        @Override public void set(Float value) { float v=Math.max(min,Math.min(max,value)); super.set(Math.round(v/step)*step); }
    }

    public static final class Color extends Setting<Integer> {
        public Color(String name, int value) { this(name, value, ""); }
        public Color(String name, int value, String tooltip) { super(name, value, tooltip); }
    }

    public static final class Choice extends Setting<String> {
        private final List<String> choices;
        public Choice(String name, String value, String... choices) {
            super(name, value, ""); this.choices=Collections.unmodifiableList(Arrays.asList(choices));
            if(!this.choices.contains(value))throw new IllegalArgumentException("Unknown default choice: "+value);
        }
        public List<String> choices(){return choices;}
        public void next(){int i=choices.indexOf(get());set(choices.get((i+1)%choices.size()));}
        @Override public void set(String value){if(choices.contains(value))super.set(value);}    }

    /** Adds a hover hint to a choice (its constructor has no tooltip parameter). */
    public static Choice tip(Choice choice, String tooltip) { choice.tooltipText = tooltip; return choice; }
}
