package dev.subframe;

import dev.subframe.mixin.RenderGlobalAccessor;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * In 1.7.10 chunks are built on the client thread by RenderGlobal.updateRenderers, a few per call, and the game leaves
 * the rest of the list for the following frames. Offline rendering does not run in the live game's time, so it pumps
 * that method until the list is empty before it counts a frame as finished.
 */
final class ChunkWait {
    private boolean broken;
    private boolean ignoreToUpdate;
    private int lastListSize = -1;
    private int sameListPolls;

    private static List<?> toUpdate() {
        return ((RenderGlobalAccessor) Minecraft.getMinecraft().renderGlobal).subframe$getWorldRenderersToUpdate();
    }

    /** Builds some of the waiting chunks. */
    void pump() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity != null) {
            mc.renderGlobal.updateRenderers(mc.renderViewEntity, false);
        }
    }

    /** True when the chunk list has not changed over many polls: it holds entries the game never builds, so waiting on it is pointless. */
    boolean stalled() {
        if (ignoreToUpdate || broken) {
            return false;
        }
        try {
            int size = toUpdate().size();
            if (size != lastListSize) {
                lastListSize = size;
                sameListPolls = 0;
                return false;
            }
            return ++sameListPolls > 300;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Stop waiting for the rest of this render. */
    void disable() {
        broken = true;
    }

    /**
     * Called when a wait timed out. While the list is still shrinking a big scene is just taking its time, and the next round
     * carries on. Otherwise stop trusting the list, and if that was already done stop waiting altogether.
     */
    void giveUp() {
        if (sameListPolls < 20) {
            return;
        }
        if (!ignoreToUpdate) {
            ignoreToUpdate = true;
            return;
        }
        broken = true;
    }

    /** What the stuck chunk list looks like and whether one more update pass changes it (for the render log). */
    String probe() {
        try {
            List<?> list = toUpdate();
            int before = list.size();
            String first = list.isEmpty() ? "-" : String.valueOf(list.get(0).getClass().getName());
            pump();
            return "probe: worldRenderersToUpdate " + before + " -> " + list.size() + " after one more update pass; first=" + first
                    + "; renderGlobal=" + Minecraft.getMinecraft().renderGlobal.getClass().getName();
        } catch (Throwable t) {
            return "probe failed: " + t;
        }
    }

    /** Queue size, for the render log when the wait for chunks does not end. */
    String describe() {
        try {
            return "worldRenderersToUpdate=" + toUpdate().size() + " broken=" + broken;
        } catch (Throwable t) {
            return "unavailable: " + t;
        }
    }

    /**
     * The Vulkan renderer builds terrain itself, on its own threads, and leaves the vanilla chunk list empty. Its F3 chunk
     * line says where it is: "C: drawn/total,compiled D: distance, B: building, Q: queued, M: memory". "Q" also counts
     * sections out of view, which it builds only once they come into view, so the wait uses "B" (sections being built)
     * and "compiled" (sections finished). Vanilla's line has neither, so this stays off on the normal renderer.
     */
    private static final java.util.regex.Pattern VULKAN_TERRAIN = java.util.regex.Pattern.compile("C: \\d+/\\d+,(\\d+) D: -?\\d+, B: (\\d+), Q: (\\d+)");
    private boolean vulkanOff;
    private int vulkanTimeouts;
    private int vulkanCompiled = -1, vulkanQueued = -1;
    private int lastBuilding = -1;
    private long buildingSince;

    /** Sections the Vulkan renderer is building right now, or -1 when it is not the renderer (or stopped being waited for). */
    int vulkanPending() {
        if (vulkanOff) {
            return -1;
        }
        try {
            String line = Minecraft.getMinecraft().renderGlobal.getDebugInfoRenders();
            if (line == null || line.isEmpty()) {
                // Vulkan fills the line in only after it was first asked for (vanilla's is never empty): count it as
                // still building until the next draws publish the real numbers
                return 1;
            }
            java.util.regex.Matcher m = VULKAN_TERRAIN.matcher(line);
            if (!m.find()) {
                return -1;
            }
            vulkanCompiled = Integer.parseInt(m.group(1));
            vulkanQueued = Integer.parseInt(m.group(3));
            return Integer.parseInt(m.group(2));
        } catch (Throwable t) {
            vulkanOff = true;
            return -1;
        }
    }

    /** Sections finished so far, as of the last vulkanPending() call. */
    int vulkanCompiled() {
        return vulkanCompiled;
    }

    /** Sections queued anywhere in view distance, as of the last vulkanPending() call (for the render log). */
    int vulkanQueued() {
        return vulkanQueued;
    }

    /** True when the number of sections being built has not moved for half a second: something is stuck, stop waiting. */
    boolean vulkanStalled(int building) {
        long now = System.nanoTime();
        if (building != lastBuilding) {
            lastBuilding = building;
            buildingSince = now;
            return false;
        }
        return now - buildingSince > 500_000_000L;
    }

    /** A frame's wait for Vulkan terrain timed out; after a few, stop waiting for it in this render. */
    boolean vulkanTimedOut() {
        if (++vulkanTimeouts >= 3) {
            vulkanOff = true;
        }
        return vulkanOff;
    }

    /** True when no chunk is waiting to be built. */
    boolean idle() {
        if (broken) {
            return true;
        }
        try {
            return ignoreToUpdate || toUpdate().isEmpty();
        } catch (Throwable t) {
            SubFrameMod.LOG.warn("Cannot inspect the chunk list, not waiting for chunks: " + t);
            broken = true;
            return true;
        }
    }
}
