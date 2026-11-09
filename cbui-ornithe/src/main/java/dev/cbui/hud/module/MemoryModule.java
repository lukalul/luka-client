package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;

/** Used / maximum JVM heap in MB, refreshed once per second. */
public final class MemoryModule extends LabelModule {
    private int ticks = 20;

    public MemoryModule() { super("Memory", Anchor.TOP_RIGHT, -5, 49, "512/2048 MB"); }

    @Override public void tick() {
        if (++ticks < 20) return;
        ticks = 0;
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) >> 20, max = rt.maxMemory() >> 20;
        label(used + "/" + max + " MB");
    }
}
