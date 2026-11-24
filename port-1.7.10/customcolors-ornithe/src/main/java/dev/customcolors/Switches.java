package dev.customcolors;

import net.fabricmc.loader.api.FabricLoader;

/** The on/off state of the mod and of its three switchable parts (JVM properties set by luka's VIDEO tab). */
public final class Switches {
    private static int modState;

    private Switches() {
    }

    /** False when Cera is loaded: Cera colours the world itself, so this mod stays out of the way entirely. */
    public static boolean active() {
        int s = modState;
        if (s == 0) {
            boolean cera;
            try {
                cera = FabricLoader.getInstance().isModLoaded("cera");
            } catch (Throwable t) {
                cera = false;
            }
            s = cera ? 2 : 1;
            modState = s;
        }
        return s == 1;
    }

    /** Colormaps, color.properties, sky/fog colours and light maps. */
    public static boolean colours() {
        return active() && on("luka.customColors");
    }

    public static boolean randomMobs() {
        return active() && on("luka.randomMobs");
    }

    public static boolean hdFonts() {
        return active() && on("luka.hdFonts");
    }

    private static boolean on(String property) {
        return !"false".equals(System.getProperty(property));
    }
}
