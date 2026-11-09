package dev.envoy.module.limb;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LimbState {
    public static final int HEAD = 1, TORSO = 2, LEFT_ARM = 3, RIGHT_ARM = 4, LEFT_LEG = 5, RIGHT_LEG = 6;
    public static final int HELMET = 1, CHESTPLATE = 2, LEGGINGS = 3, BOOTS = 4;
    private static final Map<UUID, HiddenParts> PLAYERS = new ConcurrentHashMap<>();
    private LimbState() { }

    public static int bit(int bodyPart) { return 1 << bodyPart; }
    public static void hide(UUID player, int mask) { revise(player, mask, false, true); }
    public static void reset(UUID player, int mask) { revise(player, mask, false, false); }
    public static boolean isEmpty() {
        for (HiddenParts parts : PLAYERS.values()) if (parts.body != 0) return false;
        return true;
    }
    public static int maskOrZero(UUID player) { HiddenParts parts = PLAYERS.get(player); return parts == null ? 0 : parts.body; }
    public static void hideArmor(UUID player, int mask) { revise(player, mask, true, true); }
    public static void resetArmor(UUID player, int mask) { revise(player, mask, true, false); }
    public static int armorMaskOrZero(UUID player) { HiddenParts parts = PLAYERS.get(player); return parts == null ? 0 : parts.armor; }

    private static void revise(UUID player, int mask, boolean armorTarget, boolean add) {
        PLAYERS.compute(player, (id, old) -> {
            int body = old == null ? 0 : old.body;
            int armor = old == null ? 0 : old.armor;
            if (armorTarget) armor = add ? armor | mask : mask == 0 ? 0 : armor & ~mask;
            else body = add ? body | mask : mask == 0 ? 0 : body & ~mask;
            return body == 0 && armor == 0 ? null : new HiddenParts(body, armor);
        });
    }

    private static final class HiddenParts {
        final int body, armor;
        HiddenParts(int body, int armor) { this.body = body; this.armor = armor; }
    }
}
