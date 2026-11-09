package dev.envoy.module.lightning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public final class LightningState {
    public static final long LIFETIME_MS = 900L;
    private static final ConcurrentLinkedQueue<Bolt> pending = new ConcurrentLinkedQueue<Bolt>();

    private LightningState() { }

    public static void add(double x, double y, double z, int mainArgb, int coreArgb) {
        pending.offer(new Bolt(x, y, z, mainArgb, coreArgb,
            ThreadLocalRandom.current().nextLong(), System.currentTimeMillis()));
    }

    public static List<Bolt> active() {
        long now = System.currentTimeMillis();
        List<Bolt> result = new ArrayList<Bolt>();
        for (Iterator<Bolt> iterator = pending.iterator(); iterator.hasNext();) {
            Bolt bolt = iterator.next();
            if (now - bolt.spawnTime >= LIFETIME_MS) pending.remove(bolt); else result.add(bolt);
        }
        return Collections.unmodifiableList(result);
    }

    public static boolean isEmpty() {
        return pending.isEmpty();
    }

    public static final class Bolt {
        public final double x;
        public final double y;
        public final double z;
        public final float mainRed;
        public final float mainGreen;
        public final float mainBlue;
        public final float coreRed;
        public final float coreGreen;
        public final float coreBlue;
        public final long seed;
        public final long spawnTime;

        private Bolt(double x, double y, double z, int mainArgb, int coreArgb, long seed, long spawnTime) {
            this.x = x; this.y = y; this.z = z;
            float[] main = rgb(mainArgb);
            float[] core = rgb(coreArgb);
            this.mainRed = main[0]; this.mainGreen = main[1]; this.mainBlue = main[2];
            this.coreRed = core[0]; this.coreGreen = core[1]; this.coreBlue = core[2];
            this.seed = seed; this.spawnTime = spawnTime;
        }

        public float alpha(long now) {
            float elapsed = Math.max(0L, now - spawnTime) / (float) LIFETIME_MS;
            return elapsed <= 0.4F ? 1.0F : Math.max(0.0F, (1.0F - elapsed) / 0.6F);
        }

        private static float[] rgb(int argb) {
            return new float[] {((argb >>> 16) & 255) / 255.0F, ((argb >>> 8) & 255) / 255.0F,
                (argb & 255) / 255.0F};
        }
    }
}
