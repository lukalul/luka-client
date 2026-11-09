package dev.envoy.module.nametag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NametagState {
    private static final Map<UUID, List<List<AdventureText.Run>>> OVERRIDES = new ConcurrentHashMap<UUID, List<List<AdventureText.Run>>>();
    private NametagState() { }

    public static void override(UUID player, List<List<AdventureText.Run>> lines) {
        OVERRIDES.put(player, freeze(lines));
    }
    public static void reset(UUID player) { OVERRIDES.remove(player); }
    public static void resetAll() { OVERRIDES.clear(); }
    public static List<List<AdventureText.Run>> get(UUID player) { return OVERRIDES.get(player); }

    private static List<List<AdventureText.Run>> freeze(List<List<AdventureText.Run>> lines) {
        List<List<AdventureText.Run>> copy = new ArrayList<List<AdventureText.Run>>(lines.size());
        for (List<AdventureText.Run> line : lines) {
            copy.add(Collections.unmodifiableList(new ArrayList<AdventureText.Run>(line)));
        }
        return Collections.unmodifiableList(copy);
    }
}
