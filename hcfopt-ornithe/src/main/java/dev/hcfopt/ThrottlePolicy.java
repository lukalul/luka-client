package dev.hcfopt;

/** Pure deterministic throttle math, kept independent of Minecraft for plain-Java verification. */
public final class ThrottlePolicy {
    private ThrottlePolicy() { }

    public static int interval(double distanceSquared, double nearDistance) {
        double nearSquared = nearDistance * nearDistance;
        if (distanceSquared <= nearSquared) return 1;
        if (distanceSquared <= nearSquared * 4.0) return 2;
        if (distanceSquared <= nearSquared * 16.0) return 4;
        return 8;
    }

    public static boolean shouldRun(int stableId, int tick, double distanceSquared, double nearDistance) {
        int interval = interval(distanceSquared, nearDistance);
        return interval == 1 || Math.floorMod(stableId + tick, interval) == 0;
    }
}
