package dev.hcfopt;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Pure sample collector and CSV/summary writer. */
public final class BenchmarkStats {
    public static final class Frame {
        final long nanos;
        final long tickNanos;
        final int entities;
        final int tiles;
        Frame(long nanos, long tickNanos, int entities, int tiles) {
            this.nanos = nanos; this.tickNanos = tickNanos; this.entities = entities; this.tiles = tiles;
        }
    }

    public static final class Window {
        public final String name;
        private final List<Frame> frames = new ArrayList<Frame>();
        private final List<Long> ticks = new ArrayList<Long>();
        Window(String name) { this.name = name; }
        public void frame(long nanos, long latestTickNanos, int entities, int tiles) { frames.add(new Frame(nanos, latestTickNanos, entities, tiles)); }
        public void tick(long nanos) { ticks.add(nanos); }
        public int frameCount() { return frames.size(); }

        public String summary() {
            if (frames.isEmpty()) return name + ": no frames";
            List<Long> times = new ArrayList<Long>();
            long sum = 0, worst = 0, entitySum = 0, tileSum = 0;
            for (Frame f : frames) {
                times.add(f.nanos); sum += f.nanos; worst = Math.max(worst, f.nanos);
                entitySum += f.entities; tileSum += f.tiles;
            }
            Collections.sort(times);
            double seconds = sum / 1.0e9;
            double avgFps = frames.size() / seconds;
            double low1 = fpsAt(times, 0.99);
            double low01 = fpsAt(times, 0.999);
            long tickSum = 0;
            for (Long tick : ticks) tickSum += tick;
            double meanTickMs = ticks.isEmpty() ? 0.0 : tickSum / 1.0e6 / ticks.size();
            return String.format(Locale.ROOT,
                    "%s: avg %.2f FPS, 1%% low %.2f, 0.1%% low %.2f, worst %.3f ms, mean client tick %.3f ms, rendered entities %.1f, tile entities %.1f (%d frames)",
                    name, avgFps, low1, low01, worst / 1.0e6, meanTickMs,
                    entitySum / (double)frames.size(), tileSum / (double)frames.size(), frames.size());
        }

        private static double fpsAt(List<Long> sorted, double quantile) {
            int index = Math.min(sorted.size() - 1, Math.max(0, (int)Math.ceil(sorted.size() * quantile) - 1));
            return 1.0e9 / Math.max(1L, sorted.get(index));
        }
    }

    private final List<Window> windows = new ArrayList<Window>();
    public Window addWindow(String name) { Window w = new Window(name); windows.add(w); return w; }
    public List<Window> windows() { return Collections.unmodifiableList(windows); }

    public void write(File csv, File summary) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(csv))) {
            out.println("window,frame,frame_ms,fps,latest_client_tick_ms,rendered_entities,rendered_tile_entities");
            for (Window window : windows) {
                for (int i = 0; i < window.frames.size(); i++) {
                    Frame f = window.frames.get(i);
                    out.printf(Locale.ROOT, "%s,%d,%.6f,%.3f,%.6f,%d,%d%n", window.name, i,
                            f.nanos / 1.0e6, 1.0e9 / Math.max(1L, f.nanos), f.tickNanos / 1.0e6, f.entities, f.tiles);
                }
            }
        }
        try (PrintWriter out = new PrintWriter(new FileWriter(summary))) {
            for (Window window : windows) out.println(window.summary());
        }
    }
}
