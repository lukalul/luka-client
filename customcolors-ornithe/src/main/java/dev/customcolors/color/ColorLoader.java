package dev.customcolors.color;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.Switches;
import dev.customcolors.core.EggColours;
import dev.customcolors.core.Hex;
import dev.customcolors.core.Palette;
import dev.customcolors.core.PaletteFormat;
import dev.customcolors.mixin.CcPotionCacheAccess;
import dev.customcolors.pack.PackAccess;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import net.minecraft.potion.Potion;

/** Builds the {@link ColorSet} from the selected packs. */
public final class ColorLoader {
    private static volatile ColorSet current = ColorSet.NONE;

    private ColorLoader() {
    }

    public static ColorSet current() {
        return current;
    }

    public static void reload() {
        ColorSet next = ColorSet.NONE;
        if (Switches.colours()) {
            next = new ColorSet();
            loadColormaps(next);
            loadProperties(next);
        }
        current = next;
        SkyTints.reset();
        clearPotionCache();
    }

    public static String summary() {
        ColorSet s = current;
        return "colormaps=" + s.colormapCount() + " colorProperties=" + s.propertyCount();
    }

    private static void loadColormaps(ColorSet s) {
        s.water = colormap("water");
        s.swampGrass = colormap("swampgrass");
        s.swampFoliage = colormap("swampfoliage");
        s.pine = colormap("pine");
        s.birch = colormap("birch");
        s.lavaDrop = colormap("lavadrop");
        s.mycelium = colormap("myceliumparticle");
        s.xpOrb = colormap("xporb");
        s.sky = colormap("sky0");
        s.fog = colormap("fog0");
        s.underwater = colormap("underwater");
        s.underlava = colormap("underlava");
        s.redstone = row(colormap("redstone"), 16);
        int[] shared = row(colormap("stem"), 8);
        int[] pumpkin = row(colormap("pumpkinstem"), 8);
        int[] melon = row(colormap("melonstem"), 8);
        s.pumpkinStem = pumpkin != null ? pumpkin : shared;
        s.melonStem = melon != null ? melon : shared;
    }

    private static int[] row(Palette p, int count) {
        return p == null ? null : p.firstRow(count);
    }

    /** {@code <root>/colormap/<name>.png} with its optional .properties; first usable root wins. */
    static Palette colormap(String name) {
        for (String root : PackAccess.ROOTS) {
            String base = root + "/colormap/" + name;
            Properties props = PackAccess.properties(PackAccess.at(base + ".properties"));
            if (props != null) {
                int kind = PaletteFormat.kind(props);
                if (kind == PaletteFormat.GRID) {
                    CustomColorsMod.LOG.info("customcolors: {}.properties uses format=grid, which is not supported", base);
                    continue;
                }
                int colour = PaletteFormat.colour(props);
                if (colour == PaletteFormat.BAD_COLOUR) {
                    CustomColorsMod.LOG.info("customcolors: {}.properties has an invalid color", base);
                    continue;
                }
                if (kind == PaletteFormat.SINGLE) {
                    return Palette.solid(colour);
                }
            }
            BufferedImage img = PackAccess.image(PackAccess.at(base + ".png"));
            if (img != null) {
                Palette p = Palette.image(PackAccess.pixels(img), img.getWidth(), img.getHeight());
                if (p != null) {
                    return p;
                }
            }
        }
        return null;
    }

    private static void loadProperties(ColorSet s) {
        Properties main = null;
        int lily = -1;
        for (String root : PackAccess.ROOTS) {
            Properties p = PackAccess.properties(PackAccess.at(root + "/color.properties"));
            if (p == null) {
                continue;
            }
            if (main == null) {
                main = p;
            }
            if (lily < 0) {
                lily = Hex.rgb(p.getProperty("lilypad"));
            }
        }
        s.lilyPad = lily;
        if (main == null) {
            return;
        }
        Potion[] types = Potion.potionTypes;
        int[] potions = new int[types.length];
        Arrays.fill(potions, -1);
        for (Potion potion : types) {
            if (potion != null && potion.id >= 0 && potion.id < potions.length) {
                potions[potion.id] = Hex.rgb(main.getProperty(potion.getName()));
            }
        }
        s.potions = potions;
        s.potionWater = Hex.rgb(main.getProperty("potion.water"));
        Map<String, int[]> eggs = EggColours.parse(main);
        s.eggs = eggs;
    }

    /** Vanilla remembers potion colours per item damage value; forget them so a new pack shows at once. */
    private static void clearPotionCache() {
        try {
            Map<?, ?> cache = CcPotionCacheAccess.customcolors$colours();
            if (cache != null) {
                cache.clear();
            }
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("potion colour cache", t);
        }
    }
}
