package dev.customcolors.mobs;

import dev.customcolors.Switches;
import dev.customcolors.core.SkinPaths;
import dev.customcolors.core.SkinPick;
import dev.customcolors.pack.OwnTextures;
import dev.customcolors.pack.PackAccess;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * Random mob skins from a pack's {@code mob/} folder. While an entity is being drawn, every
 * {@code textures/entity/} texture its renderer binds is swapped for the alternate chosen by the
 * entity's UUID. Render thread only.
 */
public final class MobSkins {
    private static final ResourceLocation[] NONE = new ResourceLocation[0];

    private static boolean enabled;
    private static Set<String>[] packFiles = emptyFiles();
    /** Base texture -> {base, alternate 2, alternate 3, ...}; NONE when it has no alternates. */
    private static final Map<ResourceLocation, ResourceLocation[]> CHOICES = new HashMap<ResourceLocation, ResourceLocation[]>();
    private static int alternateFiles;
    private static Entity drawing;

    private MobSkins() {
    }

    @SuppressWarnings("unchecked")
    private static Set<String>[] emptyFiles() {
        return new Set[PackAccess.ROOTS.length];
    }

    public static void reload() {
        Set<String>[] files = emptyFiles();
        int count = 0;
        boolean on = Switches.randomMobs();
        if (on) {
            for (String path : PackAccess.listSelected("mob")) {
                for (int r = 0; r < PackAccess.ROOTS.length; r++) {
                    String prefix = PackAccess.ROOTS[r] + "/mob/";
                    if (path.startsWith(prefix) && path.endsWith(".png")) {
                        if (files[r] == null) {
                            files[r] = new HashSet<String>();
                        }
                        files[r].add(path.substring(prefix.length()));
                        count++;
                    }
                }
            }
        }
        packFiles = files;
        alternateFiles = count;
        CHOICES.clear();
        enabled = on && count > 0;
        drawing = null;
    }

    public static int count() {
        return alternateFiles;
    }

    /** Marks the entity being drawn (players are never varied). */
    public static void begin(Entity entity) {
        drawing = enabled && entity != null && !(entity instanceof EntityPlayer) ? entity : null;
    }

    public static void end() {
        drawing = null;
    }

    /** The texture to bind instead of {@code loc} for the entity being drawn. */
    public static ResourceLocation swap(ResourceLocation loc) {
        Entity entity = drawing;
        if (entity == null || loc == null) {
            return loc;
        }
        ResourceLocation[] choices = CHOICES.get(loc);
        if (choices == null) {
            choices = resolve(loc);
            CHOICES.put(loc, choices);
        }
        if (choices.length == 0) {
            return loc;
        }
        UUID id = entity.getUniqueID();
        return choices[SkinPick.pick(id.getMostSignificantBits(), id.getLeastSignificantBits(), choices.length)];
    }

    private static ResourceLocation[] resolve(ResourceLocation loc) {
        if (!"minecraft".equals(loc.getResourceDomain())) {
            return NONE;
        }
        String[] alternates = SkinPaths.alternates(loc.getResourcePath(), PackAccess.ROOTS, packFiles);
        if (alternates.length == 0) {
            return NONE;
        }
        ResourceLocation[] out = new ResourceLocation[alternates.length + 1];
        out[0] = loc;
        for (int i = 0; i < alternates.length; i++) {
            out[i + 1] = PackAccess.at(alternates[i]);
            OwnTextures.note(out[i + 1]);
        }
        return out;
    }
}
