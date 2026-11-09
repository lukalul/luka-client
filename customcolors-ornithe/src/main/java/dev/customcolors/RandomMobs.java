package dev.customcolors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * Random mob textures: textures/entity/cow/cow.png can have mcpatcher/mob/cow/cow2.png, cow3.png and so on.
 * Each mob keeps one variant for life, picked from its UUID, and an overlay with the same number of variants lands on the same one.
 */
public final class RandomMobs {
    private static final String PREFIX = "textures/entity/";
    private static final String[] ROOTS = {"mcpatcher/mob/", "optifine/mob/"};
    private static final ResourceLocation[] NONE = new ResourceLocation[0];
    private static final Map<ResourceLocation, ResourceLocation[]> VARIANTS = new HashMap<>();
    static boolean enabled;
    private static Entity current;

    private RandomMobs() {
    }

    public static void begin(Entity entity) {
        current = enabled && !(entity instanceof EntityPlayer) ? entity : null;
    }

    public static void end() {
        current = null;
    }

    static void clear() {
        VARIANTS.clear();
    }

    public static ResourceLocation choose(ResourceLocation base) {
        Entity entity = current;
        if (entity == null || base == null) return base;
        ResourceLocation[] variants = VARIANTS.get(base);
        if (variants == null) {
            variants = find(base);
            VARIANTS.put(base, variants);
        }
        if (variants.length == 0) return base;
        long id = entity.getUniqueID().getLeastSignificantBits();
        int pick = (int) Math.floorMod(id ^ (id >>> 32), (long) (variants.length + 1));
        return pick == 0 ? base : variants[pick - 1];
    }

    private static ResourceLocation[] find(ResourceLocation base) {
        String path = base.getResourcePath();
        if (!path.startsWith(PREFIX) || !path.endsWith(".png")) return NONE;
        String stem = path.substring(PREFIX.length(), path.length() - 4);
        IResourceManager manager = Minecraft.getMinecraft().getResourceManager();
        for (String root : ROOTS) {
            List<ResourceLocation> found = new ArrayList<>();
            for (int number = 2; ; number++) {
                ResourceLocation candidate = new ResourceLocation(base.getResourceDomain(), root + flat(stem, number, manager, root, base));
                if (!exists(manager, candidate)) break;
                found.add(candidate);
            }
            if (!found.isEmpty()) return found.toArray(NONE);
        }
        return NONE;
    }

    /** Packs keep a variant either in the same sub-folder as the original (cow/cow2.png) or directly under mob/ (bat2.png works for both). */
    private static String flat(String stem, int number, IResourceManager manager, String root, ResourceLocation base) {
        String nested = stem + number + ".png";
        if (exists(manager, new ResourceLocation(base.getResourceDomain(), root + nested))) return nested;
        return stem.substring(stem.lastIndexOf('/') + 1) + number + ".png";
    }

    private static boolean exists(IResourceManager manager, ResourceLocation location) {
        try {
            IResource resource = manager.getResource(location);
            resource.getInputStream().close();
            return true;
        } catch (IOException | RuntimeException missing) {
            return false;
        }
    }
}
