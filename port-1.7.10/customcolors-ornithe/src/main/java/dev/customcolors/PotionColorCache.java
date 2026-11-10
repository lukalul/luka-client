package dev.customcolors;

import dev.customcolors.mixin.PotionHelperAccessor;

/** The game remembers each potion colour by damage value, so a resource reload has to forget them. */
final class PotionColorCache {
    private PotionColorCache() {
    }

    static void clear() {
        PotionHelperAccessor.customcolors$cache().clear();
    }
}
