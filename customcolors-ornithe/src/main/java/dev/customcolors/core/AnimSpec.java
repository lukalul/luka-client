package dev.customcolors.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** The supported keys of one {@code anim/*.properties} file. */
public final class AnimSpec {
    public final String from;
    public final String to;
    public final int x;
    public final int y;
    public final int w;
    public final int h;
    /** Ticks per step when no {@code duration.N} is given (at least 1). */
    public final int ticks;
    /** Frame numbers of the explicit sequence ({@code tile.0}, {@code tile.1}, ...), or null for "all frames". */
    public final int[] tiles;
    /** Ticks of each explicit step, same length as {@link #tiles}; null without tiles. */
    public final int[] tileTicks;

    private AnimSpec(String from, String to, int x, int y, int w, int h, int ticks, int[] tiles, int[] tileTicks) {
        this.from = from;
        this.to = to;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.ticks = ticks;
        this.tiles = tiles;
        this.tileTicks = tileTicks;
    }

    /** Throws IllegalArgumentException with a short reason when the file cannot be used. */
    public static AnimSpec parse(Properties p) {
        String from = text(p, "from");
        String to = text(p, "to");
        if (from == null || to == null) {
            throw new IllegalArgumentException("needs both 'from' and 'to'");
        }
        int x = number(p, "x", 0);
        int y = number(p, "y", 0);
        int w = number(p, "w", -1);
        int h = number(p, "h", -1);
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("'w' and 'h' must be positive");
        }
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("'x' and 'y' must not be negative");
        }
        int ticks = Math.max(1, lenient(p, "duration", 1));
        List<Integer> tileList = new ArrayList<Integer>();
        List<Integer> tickList = new ArrayList<Integer>();
        for (int n = 0; ; n++) {
            String t = text(p, "tile." + n);
            if (t == null) {
                break;
            }
            int frame;
            try {
                frame = Integer.parseInt(t);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad tile." + n);
            }
            tileList.add(frame);
            tickList.add(Math.max(1, lenient(p, "duration." + n, ticks)));
        }
        int[] tiles = null;
        int[] tileTicks = null;
        if (!tileList.isEmpty()) {
            tiles = new int[tileList.size()];
            tileTicks = new int[tileList.size()];
            for (int i = 0; i < tiles.length; i++) {
                tiles[i] = tileList.get(i);
                tileTicks[i] = tickList.get(i);
            }
        }
        return new AnimSpec(from, to, x, y, w, h, ticks, tiles, tileTicks);
    }

    /** The frame of every step for a source with {@code frameCount} frames (numbers wrap around). */
    public int[] steps(int frameCount) {
        if (tiles == null) {
            int[] all = new int[frameCount];
            for (int i = 0; i < frameCount; i++) {
                all[i] = i;
            }
            return all;
        }
        int[] out = new int[tiles.length];
        for (int i = 0; i < tiles.length; i++) {
            out[i] = ((tiles[i] % frameCount) + frameCount) % frameCount;
        }
        return out;
    }

    /** How long each step lasts, matching {@link #steps}. */
    public int[] stepTicks(int frameCount) {
        if (tileTicks != null) {
            return tileTicks.clone();
        }
        int[] out = new int[frameCount];
        java.util.Arrays.fill(out, ticks);
        return out;
    }

    private static String text(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null) {
            return null;
        }
        v = v.trim();
        return v.isEmpty() ? null : v;
    }

    private static int number(Properties p, String key, int fallback) {
        String v = text(p, key);
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad '" + key + "'");
        }
    }

    private static int lenient(Properties p, String key, int fallback) {
        String v = text(p, key);
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
