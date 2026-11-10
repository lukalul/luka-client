package dev.bakeplus;

/** Intentionally tiny: these fields can also be changed by another client-side mod at runtime. */
public final class BakePlusConfig {
    public static boolean staticEndPortals = true;
    public static boolean staticBeacons = true;
    public static boolean staticEnchantingTables = true;
    public static boolean staticMobSpawners = true;

    private BakePlusConfig() {
    }
}
