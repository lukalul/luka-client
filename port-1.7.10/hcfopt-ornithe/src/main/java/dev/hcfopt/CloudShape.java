package dev.hcfopt;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

/**
 * Which cells of the cloud texture hold cloud, as a 256 by 256 grid (one cell per step of the cloud mesh).
 *
 * <p>The game's fancy clouds cover every 8 by 8 tile with a top, a bottom and eight full-length side strips per
 * direction, and the texture's transparent texels are thrown away pixel by pixel. With the grid the mesh can hold
 * only the faces that can show. It is read again when the resource packs change. A texture whose size is not a
 * multiple of 256 gives no grid, and the game's own mesh is used.
 */
public final class CloudShape {
    /** Kill switch: -Dluka.cloudshape=false keeps the game's mesh (still cached). */
    public static boolean enabled = !"false".equalsIgnoreCase(System.getProperty("luka.cloudshape"));

    public static final int SIZE = 256;
    /**
     * The lowest alpha byte that shows: clouds are drawn at 0.8 alpha and the alpha test drops 0.1 and below, so a texel
     * needs more than 0.125 (byte 32). The game's own clouds.png marks its empty texels with alpha 1, not 0.
     */
    private static final int VISIBLE_ALPHA = 32;
    private static final ResourceLocation CLOUDS = new ResourceLocation("textures/environment/clouds.png");

    private static boolean[] grid;
    private static boolean loaded;
    private static boolean listening;
    private static int generation;

    private CloudShape() { }

    /** Changes whenever the grid may have changed, so a mesh built from the old one is rebuilt. */
    public static int generation() {
        return generation;
    }

    /** The grid, index x + z * 256, true where the texture has cloud; null when the game's mesh must be used. */
    public static boolean[] grid() {
        if (!enabled) return null;
        if (!loaded) load();
        return grid;
    }

    private static void load() {
        loaded = true;
        grid = null;
        Minecraft mc = Minecraft.getMinecraft();
        IResourceManager manager = mc.getResourceManager();
        if (!listening && manager instanceof IReloadableResourceManager) {
            listening = true;
            ((IReloadableResourceManager) manager).registerReloadListener(new IResourceManagerReloadListener() {
                @Override
                public void onResourceManagerReload(IResourceManager ignored) {
                    loaded = false;
                    grid = null;
                    generation++;
                }
            });
            loaded = true;
        }
        try {
            BufferedImage image;
            try (InputStream in = manager.getResource(CLOUDS).getInputStream()) {
                image = ImageIO.read(in);
            }
            if (image == null) return;
            int width = image.getWidth();
            int height = image.getHeight();
            if (width < SIZE || height < SIZE || width % SIZE != 0 || height % SIZE != 0) return;
            int sx = width / SIZE;
            int sz = height / SIZE;
            int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
            boolean[] cells = new boolean[SIZE * SIZE];
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    boolean any = false;
                    for (int dz = 0; dz < sz && !any; dz++) {
                        int row = (z * sz + dz) * width + x * sx;
                        for (int dx = 0; dx < sx; dx++) {
                            if ((argb[row + dx] >>> 24) >= VISIBLE_ALPHA) {
                                any = true;
                                break;
                            }
                        }
                    }
                    cells[x + z * SIZE] = any;
                }
            }
            grid = cells;
        } catch (Throwable t) {
            grid = null;
            HcfOptMod.problemOnce("cloud-shape", t);
        } finally {
            generation++;
        }
    }
}
