package dev.customcolors.core;

/** Chooses a skin number from an entity's UUID, so the choice never changes while the count stays the same. */
public final class SkinPick {
    private SkinPick() {
    }

    /** 0 .. count-1; textures with the same count get the same number for the same entity. */
    public static int pick(long most, long least, int count) {
        if (count <= 1) {
            return 0;
        }
        long h = most * 0x6B2D22D1EC8AC423L ^ least;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (int) ((h >>> 1) % count);
    }
}
