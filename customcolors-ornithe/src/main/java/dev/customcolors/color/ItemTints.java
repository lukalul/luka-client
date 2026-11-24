package dev.customcolors.color;

import java.util.Collection;
import net.minecraft.entity.EntityList;

/** Potion, water-bottle and spawn-egg colours from color.properties. -1 means "keep vanilla". */
public final class ItemTints {
    private ItemTints() {
    }

    public static int potion(int potionId) {
        int[] table = ColorLoader.current().potions;
        return potionId >= 0 && potionId < table.length ? table[potionId] : -1;
    }

    /** Colour of a bottle without effects (water, awkward, mundane, thick). */
    public static int noEffect(Collection<?> effects) {
        if (effects != null && !effects.isEmpty()) {
            return -1;
        }
        return ColorLoader.current().potionWater;
    }

    /** Spawn-egg colour; pass 0 is the shell, any other pass the spots. */
    public static int egg(int entityId, int pass) {
        ColorSet s = ColorLoader.current();
        if (s.eggs.isEmpty()) {
            return -1;
        }
        String name = EntityList.getStringFromID(entityId);
        if (name == null) {
            return -1;
        }
        int[] pair = s.eggs.get(name);
        if (pair == null) {
            return -1;
        }
        return pair[pass == 0 ? 0 : 1];
    }
}
