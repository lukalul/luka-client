package dev.customcolors.pack;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

/**
 * Pack textures this mod made the game load (HD font sheets, unicode pages, mob alternates). They are
 * dropped from the texture manager before it reloads, so a pack that is gone leaves no broken texture.
 */
public final class OwnTextures {
    private static final Set<ResourceLocation> HANDED_OUT = new HashSet<ResourceLocation>();

    private OwnTextures() {
    }

    public static synchronized void note(ResourceLocation loc) {
        HANDED_OUT.add(loc);
    }

    public static synchronized void forget(TextureManager textures) {
        for (ResourceLocation loc : HANDED_OUT) {
            if (textures.getTexture(loc) != null) {
                textures.deleteTexture(loc);
            }
        }
        HANDED_OUT.clear();
    }
}
