package dev.customcolors.core;

import java.util.Properties;

/** Reads the few keys of a colormap's .properties file that luka honours: {@code format} and {@code color}. */
public final class PaletteFormat {
    public static final int CLIMATE = 0;
    public static final int SINGLE = 1;
    public static final int GRID = 2;
    /** Returned by {@link #colour} when the {@code color} value is not valid hex. */
    public static final int BAD_COLOUR = -1;

    private PaletteFormat() {
    }

    /** {@link #CLIMATE} (default and unknown values), {@link #SINGLE} or {@link #GRID}. */
    public static int kind(Properties props) {
        if (props == null) {
            return CLIMATE;
        }
        String v = props.getProperty("format");
        if (v == null) {
            return CLIMATE;
        }
        v = v.trim().toLowerCase(java.util.Locale.ROOT);
        if (v.equals("fixed") || v.equals("0")) {
            return SINGLE;
        }
        if (v.equals("grid") || v.equals("2")) {
            return GRID;
        }
        return CLIMATE;
    }

    /** The {@code color} value (white when absent) or {@link #BAD_COLOUR}. */
    public static int colour(Properties props) {
        String v = props == null ? null : props.getProperty("color");
        if (v == null) {
            return 0xFFFFFF;
        }
        int c = Hex.rgb(v);
        return c < 0 ? BAD_COLOUR : c;
    }
}
