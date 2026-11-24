package dev.customcolors.light;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.Switches;
import dev.customcolors.core.LightPalette;
import dev.customcolors.pack.PackAccess;
import java.awt.image.BufferedImage;
import net.minecraft.world.World;

/** Pack light maps ({@code lightmap/world-1.png}, {@code world0.png}, {@code world1.png}). */
public final class LightmapPainter {
    /** Index = dimension id + 1. */
    private static volatile LightPalette[] byDimension = new LightPalette[3];

    private LightmapPainter() {
    }

    public static void reload() {
        LightPalette[] next = new LightPalette[3];
        if (Switches.colours()) {
            for (int dim = -1; dim <= 1; dim++) {
                next[dim + 1] = load(dim);
            }
        }
        byDimension = next;
    }

    private static LightPalette load(int dim) {
        for (String root : PackAccess.ROOTS) {
            String path = root + "/lightmap/world" + dim + ".png";
            BufferedImage img = PackAccess.image(PackAccess.at(path));
            if (img == null) {
                continue;
            }
            if (!LightPalette.validHeight(img.getHeight())) {
                CustomColorsMod.LOG.info("customcolors: {} is {} pixels high; light maps must be 32 or 64", path,
                    img.getHeight());
                continue;
            }
            LightPalette p = LightPalette.of(PackAccess.pixels(img), img.getWidth(), img.getHeight());
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    public static int count() {
        int n = 0;
        for (LightPalette p : byDimension) {
            if (p != null) {
                n++;
            }
        }
        return n;
    }

    /**
     * Fills vanilla's light table from the pack light map of the world's dimension.
     * Returns false (vanilla runs) when that dimension has none.
     */
    public static boolean paint(int[] table, World world, float torchFlicker, float nightVision, float gamma) {
        int dim = world.provider.dimensionId;
        if (dim < -1 || dim > 1) {
            return false;
        }
        LightPalette p = byDimension[dim + 1];
        if (p == null) {
            return false;
        }
        float sun = world.getSunBrightness(1.0F);
        float daylight = (sun - 0.2F) / 0.8F;
        boolean flash = world.lastLightningBolt > 0;
        float flicker = 0.5F + torchFlicker;
        p.fill(table, daylight, flash, flicker, nightVision, gamma);
        return true;
    }
}
