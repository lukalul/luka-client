package dev.hcfopt;

import net.minecraft.client.Minecraft;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.EmptyChunk;

/**
 * Light updates of the client world, done a little each frame instead of at once.
 *
 * <p>While new land arrives the server keeps sending block changes (trees, lakes, ores placed after a chunk was
 * sent). The client relights around every changed block right away, a flood fill each, and one packet with many
 * changes costs 10 to 20 ms on the game thread. Here each request is remembered once (repeats for the same block
 * and light type merge) and the queue is worked off at the start of each frame within a small time budget; a large
 * backlog gets a larger budget so the light never falls far behind. The client only uses light for drawing, and a
 * finished update re-meshes the sections it touched, as before. -Dluka.lightqueue=false gives the game's behaviour.
 */
public final class LightQueue {
    public static boolean enabled = !"false".equalsIgnoreCase(System.getProperty("luka.lightqueue"));

    private static final long BUDGET_NS = 300_000L;
    private static final long BACKLOG_BUDGET_NS = 2_000_000L;
    private static final int BACKLOG = 2048;
    private static final int MIN_PER_FRAME = 8;

    private static final int CAPACITY = 1 << 16;           // ring of pending requests
    private static final long[] ring = new long[CAPACITY];
    private static int head;
    private static int size;
    private static final LongSet pending = new LongSet(1 << 17);

    private static World world;
    private static boolean draining;

    private LightQueue() { }

    /** True when the update was queued and the caller must not run it now. */
    public static boolean defer(World target, EnumSkyBlock type, int x, int y, int z) {
        if (!enabled || draining || target == null || !target.isRemote) return false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || target != mc.theWorld) return false;   // a preview or other client world: now, as before
        if (y < 0 || y > 255) return false;
        if (target != world) {
            clear();
            world = target;
        }
        if (size >= CAPACITY) return false;                 // full: run it now, as the game would
        long key = pack(type, x, y, z);
        if (!pending.add(key)) return true;                 // already waiting
        ring[(head + size) & (CAPACITY - 1)] = key;
        size++;
        return true;
    }

    /** Works off queued updates; called at the start of every frame. */
    public static void drain() {
        if (size == 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        World current = mc == null ? null : mc.theWorld;
        if (current == null || current != world) {
            clear();
            return;
        }
        long budget = size > BACKLOG ? BACKLOG_BUDGET_NS : BUDGET_NS;
        long start = System.nanoTime();
        int done = 0;
        draining = true;
        try {
            while (size > 0 && (done < MIN_PER_FRAME || System.nanoTime() - start < budget)) {
                long key = ring[head];
                head = (head + 1) & (CAPACITY - 1);
                size--;
                pending.remove(key);
                // the chunk unloaded meanwhile: relighting against the empty placeholder would darken its neighbours
                if (current.getChunkProvider().provideChunk(x(key) >> 4, z(key) >> 4) instanceof EmptyChunk) continue;
                current.updateLightByType(type(key), x(key), y(key), z(key));
                done++;
            }
        } catch (Throwable t) {
            clear();
            enabled = false;
            HcfOptMod.problemOnce("light-queue", t);
        } finally {
            draining = false;
        }
        if (size == 0) world = null;   // nothing waiting: do not hold on to the world
    }

    public static int pendingCount() {
        return size;
    }

    private static void clear() {
        head = 0;
        size = 0;
        pending.clear();
        world = null;
    }

    // type: 1 bit, y: 8 bits, x and z: 27 bits each (coordinates up to +-67 million)
    private static long pack(EnumSkyBlock type, int x, int y, int z) {
        return ((long) (type == EnumSkyBlock.Sky ? 1 : 0) << 62) | ((long) (y & 0xFF) << 54)
                | ((long) (x & 0x7FFFFFF) << 27) | (long) (z & 0x7FFFFFF);
    }

    private static EnumSkyBlock type(long key) {
        return ((key >>> 62) & 1) == 1 ? EnumSkyBlock.Sky : EnumSkyBlock.Block;
    }

    private static int y(long key) {
        return (int) ((key >>> 54) & 0xFF);
    }

    private static int x(long key) {
        return ((int) ((key >>> 27) & 0x7FFFFFF) << 5) >> 5;
    }

    private static int z(long key) {
        return ((int) (key & 0x7FFFFFF) << 5) >> 5;
    }

    /** Open-addressing set of longs; keys never have the top bit set, so -1 marks a free slot. */
    private static final class LongSet {
        private static final long FREE = -1L;
        private final long[] slots;
        private final int mask;
        private int used;

        LongSet(int capacity) {
            slots = new long[capacity];
            mask = capacity - 1;
            java.util.Arrays.fill(slots, FREE);
        }

        boolean add(long key) {
            int i = mix(key) & mask;
            while (true) {
                long k = slots[i];
                if (k == FREE) {
                    slots[i] = key;
                    used++;
                    return true;
                }
                if (k == key) return false;
                i = (i + 1) & mask;
            }
        }

        void remove(long key) {
            int i = mix(key) & mask;
            while (true) {
                long k = slots[i];
                if (k == FREE) return;
                if (k == key) break;
                i = (i + 1) & mask;
            }
            // backward-shift deletion keeps every probe chain unbroken
            int gap = i;
            int j = i;
            while (true) {
                j = (j + 1) & mask;
                long k = slots[j];
                if (k == FREE) break;
                int home = mix(k) & mask;
                boolean between = gap <= j ? (gap < home && home <= j) : (gap < home || home <= j);
                if (!between) {
                    slots[gap] = k;
                    gap = j;
                }
            }
            slots[gap] = FREE;
            used--;
        }

        void clear() {
            if (used == 0) return;
            java.util.Arrays.fill(slots, FREE);
            used = 0;
        }

        private static int mix(long key) {
            long h = key * 0x9E3779B97F4A7C15L;
            return (int) (h ^ (h >>> 32));
        }
    }
}
