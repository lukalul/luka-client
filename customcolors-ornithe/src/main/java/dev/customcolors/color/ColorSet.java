package dev.customcolors.color;

import dev.customcolors.core.Palette;
import java.util.Collections;
import java.util.Map;

/** Everything the current packs supply for colours; replaced as a whole on every resource reload. */
public final class ColorSet {
    public static final ColorSet NONE = new ColorSet();

    Palette water;
    Palette swampGrass;
    Palette swampFoliage;
    Palette pine;
    Palette birch;
    Palette lavaDrop;
    Palette mycelium;
    Palette xpOrb;
    Palette sky;
    Palette fog;
    Palette underwater;
    Palette underlava;
    /** Wire colour per power level 0..15. */
    int[] redstone;
    /** Stem colour per growth stage 0..7. */
    int[] pumpkinStem;
    int[] melonStem;
    /** Liquid colour per potion id, -1 = vanilla. */
    int[] potions = new int[0];
    int potionWater = -1;
    Map<String, int[]> eggs = Collections.emptyMap();
    int lilyPad = -1;

    /** Number of colormaps that are present (for the summary line). */
    int colormapCount() {
        Object[] all = {water, swampGrass, swampFoliage, pine, birch, lavaDrop, mycelium, xpOrb, sky, fog,
            underwater, underlava, redstone, pumpkinStem, melonStem};
        int n = 0;
        for (Object o : all) {
            if (o != null) {
                n++;
            }
        }
        return n;
    }

    int propertyCount() {
        int n = eggs.size() + (potionWater >= 0 ? 1 : 0) + (lilyPad >= 0 ? 1 : 0);
        for (int p : potions) {
            if (p >= 0) {
                n++;
            }
        }
        return n;
    }
}
