package dev.envoy.module.beam;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class BeamState {
    public static final class Beam {
        public final int x;
        public final int y;
        public final int z;
        public final int argb;

        public Beam(int x, int y, int z, int argb) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.argb = argb;
        }
    }

    private static final AtomicReference<Map<String, Beam>> SNAPSHOT =
        new AtomicReference<Map<String, Beam>>(Collections.<String, Beam>emptyMap());

    private BeamState() {
    }

    public static void display(String id, Beam beam) {
        revise(id, beam);
    }

    public static void remove(String id) {
        revise(id, null);
    }

    public static void resetAll() {
        SNAPSHOT.set(Collections.<String, Beam>emptyMap());
    }

    public static Collection<Beam> all() {
        return Collections.unmodifiableList(new ArrayList<Beam>(SNAPSHOT.get().values()));
    }

    public static boolean isEmpty() {
        return SNAPSHOT.get().isEmpty();
    }

    private static void revise(String id, Beam replacement) {
        Map<String, Beam> before;
        Map<String, Beam> after;
        do {
            before = SNAPSHOT.get();
            after = new LinkedHashMap<String, Beam>(before);
            if (replacement == null) {
                after.remove(id);
            } else {
                after.put(id, replacement);
            }
            after = Collections.unmodifiableMap(after);
        } while (!SNAPSHOT.compareAndSet(before, after));
    }
}
