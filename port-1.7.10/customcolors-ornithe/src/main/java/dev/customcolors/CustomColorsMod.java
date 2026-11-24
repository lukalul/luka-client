package dev.customcolors;

import dev.customcolors.anim.TextureAnimator;
import dev.customcolors.color.ColorLoader;
import dev.customcolors.font.FontPack;
import dev.customcolors.light.LightmapPainter;
import dev.customcolors.mobs.MobSkins;
import dev.customcolors.pack.OwnTextures;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Start-up, per-tick work and resource reloads of Custom Colors and Fonts. */
public final class CustomColorsMod implements IResourceManagerReloadListener {
    public static final Logger LOG = LogManager.getLogger("customcolors");
    private static final Set<String> REPORTED = new HashSet<String>();
    private static boolean started;

    private CustomColorsMod() {
    }

    /** Called once the game has finished starting; registering the listener runs the first load. */
    public static void start(Minecraft mc) {
        if (started || !Switches.active()) {
            if (!Switches.active()) {
                LOG.info("customcolors: Cera is loaded, staying inactive");
            }
            return;
        }
        started = true;
        IResourceManager manager = mc.getResourceManager();
        if (manager instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) manager).registerReloadListener(new CustomColorsMod());
        }
    }

    public static void tick() {
        if (started) {
            TextureAnimator.tick();
        }
    }

    /** Before the texture manager reloads: drop pack textures this mod introduced. */
    public static void beforeTextureReload(TextureManager textures) {
        if (started) {
            OwnTextures.forget(textures);
        }
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        step("colours", 0);
        step("light maps", 1);
        step("fonts", 2);
        step("animations", 3);
        step("random mobs", 4);
        LOG.info("customcolors: {}", summary());
    }

    private static void step(String what, int which) {
        try {
            if (which == 0) {
                ColorLoader.reload();
            } else if (which == 1) {
                LightmapPainter.reload();
            } else if (which == 2) {
                FontPack.reset();
            } else if (which == 3) {
                TextureAnimator.reload();
            } else {
                MobSkins.reload();
            }
        } catch (Throwable t) {
            LOG.warn("customcolors: could not load pack {}: {}", what, t.toString());
        }
    }

    /** One line naming what the selected packs supply (for test rigs: callstatic). */
    public static String summary() {
        if (!Switches.active()) {
            return "customcolors inactive (Cera)";
        }
        return "customcolors " + ColorLoader.summary() + " lightmaps=" + LightmapPainter.count()
            + " unicodePages=" + FontPack.pageCount() + " animations=" + TextureAnimator.count()
            + " mobSkinFiles=" + MobSkins.count() + " colours=" + Switches.colours()
            + " randomMobs=" + Switches.randomMobs() + " hdFonts=" + Switches.hdFonts();
    }

    /** Logs a hook failure once per place; the hook then falls back to vanilla. */
    public static void reportOnce(String where, Throwable t) {
        synchronized (REPORTED) {
            if (!REPORTED.add(where)) {
                return;
            }
        }
        LOG.warn("customcolors: {} failed, using vanilla there", where, t);
    }
}
