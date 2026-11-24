package dev.envoy.module.coloredfire;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import dev.envoy.Envoy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

/**
 * A grey copy of the two fire textures for Colored Fire. Multiplying the orange fire by the chosen colour turned pure
 * blue into almost black (the fire has next to no blue in it); multiplying a grey fire gives the colour itself, darker
 * towards the edges of the flame like the real one. Layer 0 is the left half, layer 1 the right half, frames are
 * stacked downwards. Built from the current resource packs on first use and again after every resource reload.
 */
public final class FlameTexture {
    private static final ResourceLocation[] LAYERS = {
        new ResourceLocation("textures/blocks/fire_layer_0.png"),
        new ResourceLocation("textures/blocks/fire_layer_1.png")
    };
    private static final int MAX_HEIGHT = 8192;
    private static final long TICK_NANOS = 50000000L;

    private static int textureId = -1;
    private static int[] frameCounts = {1, 1};
    private static int rows = 1;
    private static volatile boolean stale = true;
    private static boolean listening;

    private FlameTexture() { }

    /** Binds the grey fire; false when it could not be built (the caller then draws the normal fire). */
    public static boolean bind() {
        if (stale) rebuild();
        if (textureId < 0) return false;
        GlStateManager.bindTexture(textureId);
        return true;
    }

    public static float minU(int layer) { return layer == 0 ? 0.0F : 0.5F; }
    public static float maxU(int layer) { return layer == 0 ? 0.5F : 1.0F; }

    /** Top of the current animation frame of a layer (one game tick per frame, like the default fire). */
    public static float frameTop(int layer) {
        long tick = System.nanoTime() / TICK_NANOS;
        return (int) (tick % frameCounts[layer]) / (float) rows;
    }

    public static float frameHeight() { return 1.0F / rows; }

    private static void rebuild() {
        stale = false;
        listen();
        if (textureId >= 0) {
            TextureUtil.deleteTexture(textureId);
            textureId = -1;
        }
        try {
            IResourceManager manager = Minecraft.getMinecraft().getResourceManager();
            BufferedImage[] sources = new BufferedImage[LAYERS.length];
            for (int i = 0; i < LAYERS.length; i++) sources[i] = read(manager, LAYERS[i]);
            int size = sources[0].getWidth();
            int[] counts = new int[LAYERS.length];
            int frames = 1;
            for (int i = 0; i < LAYERS.length; i++) {
                counts[i] = Math.max(1, sources[i].getHeight() / sources[i].getWidth());
                frames = Math.max(frames, counts[i]);
            }
            frames = Math.min(frames, Math.max(1, MAX_HEIGHT / size));
            BufferedImage grey = new BufferedImage(size * LAYERS.length, size * frames, BufferedImage.TYPE_INT_ARGB);
            for (int i = 0; i < LAYERS.length; i++) {
                counts[i] = Math.min(counts[i], frames);
                FlamePixels.greyInto(sources[i], counts[i], size, grey, i * size);
            }
            int id = TextureUtil.glGenTextures();
            TextureUtil.uploadTextureImageAllocate(id, grey, false, false);
            textureId = id;
            frameCounts = counts;
            rows = frames;
        } catch (Exception failure) {
            Envoy.LOGGER.warn("[ColoredFire] grey fire texture unavailable, tinting the normal fire: {}", failure.toString());
        }
    }

    private static BufferedImage read(IResourceManager manager, ResourceLocation location) throws IOException {
        InputStream stream = manager.getResource(location).getInputStream();
        try {
            BufferedImage image = ImageIO.read(stream);
            if (image == null || image.getWidth() <= 0) throw new IOException("unreadable " + location);
            return image;
        } finally {
            stream.close();
        }
    }

    private static void listen() {
        if (listening) return;
        IResourceManager manager = Minecraft.getMinecraft().getResourceManager();
        if (!(manager instanceof IReloadableResourceManager)) return;
        listening = true;
        ((IReloadableResourceManager) manager).registerReloadListener(new IResourceManagerReloadListener() {
            @Override public void onResourceManagerReload(IResourceManager reloaded) { stale = true; }
        });
    }
}
