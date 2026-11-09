package dev.subframe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;

/**
 * Chunk meshes are built on background threads, so how many are ready when a frame draws depends on wall
 * time. Offline rendering does not run in the live game's time, so it has to wait until the builders are idle.
 */
final class ChunkWait {
    private static final String[] TO_UPDATE = {"chunksToUpdate", "field_175009_l"};
    private static final String[] DISPATCHER = {"renderDispatcher", "field_174995_M"};
    private static final String[] UPDATES = {"queueChunkUpdates", "field_178519_d"};
    private static final String[] UPLOADS = {"queueChunkUploads", "field_178524_h"};
    private static final String[] FREE = {"queueFreeRenderBuilders", "field_178520_e"};

    private final int totalBuilders;
    private boolean broken;
    private boolean ignoreToUpdate;
    private int lastListSize = -1;
    private int sameListPolls;

    ChunkWait() {
        totalBuilders = freeBuilders();
    }

    private static int freeBuilders() {
        try {
            RenderGlobal rg = Minecraft.getMinecraft().renderGlobal;
            ChunkRenderDispatcher d = Reflect.get(RenderGlobal.class, rg, DISPATCHER);
            BlockingQueue<RegionRenderCacheBuilder> free =
                    Reflect.get(ChunkRenderDispatcher.class, d, FREE);
            return free.size();
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * True when the builders and upload queues are idle and RenderGlobal's list has not changed over many polls, i.e.
     * it holds entries the game never schedules (empty or edge chunks in some clients), so waiting on it is pointless.
     */
    boolean stalled() {
        if (ignoreToUpdate || broken) {
            return false;
        }
        try {
            RenderGlobal rg = Minecraft.getMinecraft().renderGlobal;
            ChunkRenderDispatcher d = Reflect.get(RenderGlobal.class, rg, DISPATCHER);
            Collection<?> toUpdate = Reflect.get(RenderGlobal.class, rg, TO_UPDATE);
            Collection<?> updates = Reflect.get(ChunkRenderDispatcher.class, d, UPDATES);
            Collection<?> uploads = Reflect.get(ChunkRenderDispatcher.class, d, UPLOADS);
            boolean quiet = updates.isEmpty() && uploads.isEmpty() && freeBuilders() >= totalBuilders;
            if (!quiet || toUpdate.size() != lastListSize) {
                lastListSize = toUpdate.size();
                sameListPolls = 0;
                return false;
            }
            return ++sameListPolls > 300;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Stop waiting for the rest of this render: the builders never report idle (another client replaced the chunk code). */
    void disable() {
        broken = true;
    }

    /**
     * Called when waiting timed out. If the builders and upload queues are idle and only RenderGlobal's own list never
     * drains (another client schedules chunks differently), keep waiting for real builds but ignore that list;
     * otherwise stop waiting.
     */
    void giveUp() {
        try {
            RenderGlobal rg = Minecraft.getMinecraft().renderGlobal;
            ChunkRenderDispatcher d = Reflect.get(RenderGlobal.class, rg, DISPATCHER);
            Collection<?> updates = Reflect.get(ChunkRenderDispatcher.class, d, UPDATES);
            Collection<?> uploads = Reflect.get(ChunkRenderDispatcher.class, d, UPLOADS);
            if (updates.isEmpty() && uploads.isEmpty() && freeBuilders() >= totalBuilders && !ignoreToUpdate) {
                ignoreToUpdate = true;
                return;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        broken = true;
    }

    /** What the stuck chunk list looks like and whether one more update pass changes it (for the render log). */
    String probe() {
        try {
            RenderGlobal rg = Minecraft.getMinecraft().renderGlobal;
            Collection<?> toUpdate = Reflect.get(RenderGlobal.class, rg, TO_UPDATE);
            int before = toUpdate.size();
            String first = "-";
            if (!toUpdate.isEmpty()) {
                Object o = toUpdate.iterator().next();
                if (o instanceof net.minecraft.client.renderer.chunk.RenderChunk) {
                    net.minecraft.client.renderer.chunk.RenderChunk rc = (net.minecraft.client.renderer.chunk.RenderChunk) o;
                    first = rc.getPosition() + " needsUpdate=" + rc.isNeedsUpdate() + " class=" + o.getClass().getName();
                } else {
                    first = String.valueOf(o.getClass().getName());
                }
            }
            rg.updateChunks(System.nanoTime() + 50_000_000L);
            ChunkRenderDispatcher d = Reflect.get(RenderGlobal.class, rg, DISPATCHER);
            return "probe: chunksToUpdate " + before + " -> " + toUpdate.size() + " after one more update pass; first=" + first
                    + "; renderGlobal=" + rg.getClass().getName() + " dispatcher=" + d.getClass().getName()
                    + " queueUpdates=" + ((Collection<?>) Reflect.get(ChunkRenderDispatcher.class, d, UPDATES)).size();
        } catch (Throwable t) {
            return "probe failed: " + t;
        }
    }

    /** Queue sizes, for the render log when the wait for chunk builders does not end. */
    String describe() {
        try {
            RenderGlobal rg = Minecraft.getMinecraft().renderGlobal;
            ChunkRenderDispatcher d = Reflect.get(RenderGlobal.class, rg, DISPATCHER);
            Collection<?> toUpdate = Reflect.get(RenderGlobal.class, rg, TO_UPDATE);
            Collection<?> updates = Reflect.get(ChunkRenderDispatcher.class, d, UPDATES);
            Collection<?> uploads = Reflect.get(ChunkRenderDispatcher.class, d, UPLOADS);
            return "chunksToUpdate=" + toUpdate.size() + " queuedUpdates=" + updates.size() + " queuedUploads=" + uploads.size()
                    + " freeBuilders=" + freeBuilders() + "/" + totalBuilders + " broken=" + broken;
        } catch (Throwable t) {
            return "unavailable: " + t;
        }
    }

    /** True when nothing is queued, nothing is waiting to upload and every builder is free. */
    boolean idle() {
        if (broken || totalBuilders <= 0) {
            return true;
        }
        try {
            RenderGlobal rg = Minecraft.getMinecraft().renderGlobal;
            ChunkRenderDispatcher d = Reflect.get(RenderGlobal.class, rg, DISPATCHER);
            Collection<?> toUpdate = Reflect.get(RenderGlobal.class, rg, TO_UPDATE);
            Collection<?> updates = Reflect.get(ChunkRenderDispatcher.class, d, UPDATES);
            Collection<?> uploads = Reflect.get(ChunkRenderDispatcher.class, d, UPLOADS);
            return (ignoreToUpdate || toUpdate.isEmpty()) && updates.isEmpty() && uploads.isEmpty() && freeBuilders() >= totalBuilders;
        } catch (Throwable t) {
            SubFrameMod.LOG.warn("Cannot inspect the chunk builder queues, not waiting for chunks: " + t);
            broken = true;
            return true;
        }
    }
}
