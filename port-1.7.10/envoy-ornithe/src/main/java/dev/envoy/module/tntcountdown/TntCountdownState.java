package dev.envoy.module.tntcountdown;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class TntCountdownState {
    public static final class Countdown {
        public final int entityId;
        public final UUID uuid;
        public final long startMillis;
        public final long durationMillis;

        public Countdown(int entityId, UUID uuid, long startMillis, long durationMillis) {
            this.entityId = entityId;
            this.uuid = uuid;
            this.startMillis = startMillis;
            this.durationMillis = durationMillis;
        }

        public long remaining(long now) {
            return durationMillis - (now - startMillis);
        }
    }

    private static final AtomicReference<List<Countdown>> TIMERS =
        new AtomicReference<>(Collections.<Countdown>emptyList());

    private TntCountdownState() {
    }

    public static void set(int entityId, UUID uuid, int durationTicks) {
        if ((entityId <= 0 && uuid == null) || durationTicks <= 0) return;
        Countdown replacement = new Countdown(entityId, uuid, System.currentTimeMillis(), durationTicks * 50L);
        List<Countdown> oldList;
        List<Countdown> newList;
        do {
            oldList = TIMERS.get();
            newList = new ArrayList<>(oldList.size() + 1);
            for (Countdown timer : oldList) {
                if (!sameTarget(timer, entityId, uuid)) newList.add(timer);
            }
            newList.add(replacement);
            newList = Collections.unmodifiableList(newList);
        } while (!TIMERS.compareAndSet(oldList, newList));
    }

    public static void remove(Countdown countdown) {
        if (countdown == null) return;
        List<Countdown> oldList;
        List<Countdown> newList;
        do {
            oldList = TIMERS.get();
            if (!oldList.contains(countdown)) return;
            newList = new ArrayList<>(oldList);
            newList.remove(countdown);
            newList = Collections.unmodifiableList(newList);
        } while (!TIMERS.compareAndSet(oldList, newList));
    }

    public static void clear() {
        TIMERS.set(Collections.<Countdown>emptyList());
    }

    public static boolean isEmpty() {
        return TIMERS.get().isEmpty();
    }

    public static Collection<Countdown> all() {
        return TIMERS.get();
    }

    private static boolean sameTarget(Countdown timer, int entityId, UUID uuid) {
        if (entityId > 0) return timer.entityId == entityId;
        return timer.entityId <= 0 && uuid.equals(timer.uuid);
    }
}
