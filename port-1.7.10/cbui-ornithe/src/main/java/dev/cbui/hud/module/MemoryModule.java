package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;

/** Used / maximum JVM heap in MB, refreshed once per second. The hide and colour rules use the used MB. */
public final class MemoryModule extends ValueModule {
    private int ticks = 20;

    public MemoryModule() { super("Memory", false, Anchor.TOP_RIGHT, -5, 49, "MB", "512/2048", new Range(16384, 64, 0, GREEN, 4096, RED)); }

    @Override public void tick() {
        if (++ticks < 20) return;
        ticks = 0;
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) >> 20, max = rt.maxMemory() >> 20;
        value(used, used + "/" + max);
    }
}
