package dev.hcfopt;

/** Per-frame counters populated at the two final vanilla dispatch points. */
public final class RenderCounters {
    private static int entities;
    private static int tiles;
    private RenderCounters() { }
    static void beginFrame() { entities = 0; tiles = 0; }
    public static void entityRendered() { entities++; }
    public static void tileRendered() { tiles++; }
    static int entities() { return entities; }
    static int tiles() { return tiles; }
}
