package dev.customsky;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Loads MCPatcher/OptiFine custom sky layers from resource packs and draws them with plain GL state and the Tessellator. */
public final class CustomSky implements IResourceManagerReloadListener {
    private static final Logger LOG = LogManager.getLogger("CustomSky");
    /** Cera already draws these skies on the OpenGL renderer. */
    private static final boolean ENABLED = !FabricLoader.getInstance().isModLoaded("cera");
    private static final String[] ROOTS = {"mcpatcher", "optifine"};
    private static final int[] WORLDS = {0, 1};
    /** Index gaps tolerated before a world's layer list is considered finished. */
    private static final int MAX_GAP = 8;
    private static final Map<Integer, List<SkyLayer>> LAYERS = new HashMap<>();
    private static final Map<Integer, ResourceLocation> SUNS = new HashMap<>();
    private static final Map<Integer, ResourceLocation> MOONS = new HashMap<>();

    private CustomSky() {
    }

    public static void register() {
        if (!ENABLED) return;
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(new CustomSky());
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        LAYERS.clear();
        SUNS.clear();
        MOONS.clear();
        if ("false".equals(System.getProperty("luka.customSky"))) return;
        for (int world : WORLDS) {
            for (String root : ROOTS) {
                celestial(manager, SUNS, root, world, "sun.png");
                celestial(manager, MOONS, root, world, "moon_phases.png");
                List<SkyLayer> layers = load(manager, root, world);
                if (!layers.isEmpty()) {
                    LAYERS.put(world, layers);
                    LOG.info("Loaded {} sky layers for world {} from {}", layers.size(), world, root);
                    break;
                }
            }
        }
    }

    private static void celestial(IResourceManager manager, Map<Integer, ResourceLocation> into, String root, int world, String file) {
        if (into.containsKey(world)) return;
        ResourceLocation location = new ResourceLocation("minecraft", root + "/sky/world" + world + "/" + file);
        try {
            manager.getResource(location).getInputStream().close();
            into.put(world, location);
        } catch (IOException | RuntimeException ignored) {
        }
    }

    /** The texture to use for the sun or moon; the pack's when it has one and the world has custom sky layers. */
    public static ResourceLocation celestial(ResourceLocation original, boolean sun) {
        WorldClient world = Minecraft.getMinecraft().theWorld;
        if (world == null || !active()) return original;
        ResourceLocation replacement = (sun ? SUNS : MOONS).get(world.provider.dimensionId);
        return replacement != null ? replacement : original;
    }

    private static List<SkyLayer> load(IResourceManager manager, String root, int world) {
        List<SkyLayer> layers = new ArrayList<>();
        String directory = root + "/sky/world" + world + "/";
        int misses = 0;
        for (int index = 0; misses < MAX_GAP; index++) {
            String path = directory + "sky" + index + ".properties";
            Properties properties = new Properties();
            try (InputStream in = manager.getResource(new ResourceLocation("minecraft", path)).getInputStream()) {
                properties.load(in);
            } catch (IOException e) {
                if (index > 0) misses++;
                continue;
            } catch (RuntimeException e) {
                LOG.warn("Could not read {}: {}", path, e.toString());
                continue;
            }
            misses = 0;
            SkyLayer layer = SkyLayer.parse(properties, path, directory + "sky" + index + ".png");
            if (layer == null) {
                LOG.warn("Skipping invalid sky layer {}", path);
                continue;
            }
            try {
                manager.getResource(layer.texture).getInputStream().close();
                layers.add(layer);
            } catch (IOException e) {
                LOG.warn("Skipping sky layer {}: no texture {}", path, layer.texture);
            }
        }
        return layers;
    }

    private static List<SkyLayer> current() {
        WorldClient world = Minecraft.getMinecraft().theWorld;
        if (world == null || LAYERS.isEmpty()) return Collections.emptyList();
        List<SkyLayer> layers = LAYERS.get(world.provider.dimensionId);
        return layers == null ? Collections.<SkyLayer>emptyList() : layers;
    }

    public static boolean active() {
        return !current().isEmpty();
    }

    /** Draws the current world's layers. Leaves blending on and the colour white; the caller restores its blend function. */
    public static void render(float partialTicks) {
        List<SkyLayer> layers = current();
        if (layers.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.theWorld;
        double worldTime = world.getWorldTime() + partialTicks;
        float rain = world.getRainStrength(partialTicks);
        float angle = world.getCelestialAngle(partialTicks) * 360.0F;
        GlStateManager.disableCull();
        for (SkyLayer layer : layers) {
            float brightness = layer.brightness(worldTime, rain);
            if (brightness < 1.0E-4F) continue;
            mc.getTextureManager().bindTexture(layer.texture);
            layer.blend.apply(brightness);
            GlStateManager.pushMatrix();
            // The cube is laid out in world directions. Layer rotation is defined in the frame the sun rotates in.
            GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F);
            if (layer.rotate) GlStateManager.rotate(angle * layer.speed, layer.axisX, layer.axisY, layer.axisZ);
            GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
            drawCube();
            GlStateManager.popMatrix();
        }
        GlStateManager.enableBlend();
        GlStateManager.enableCull();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** A 3x2 texture grid: top row bottom, top, south; bottom row west, north, east. */
    private static void drawCube() {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        face(tessellator, -100, -100, -100, -100, -100, 100, 100, -100, 100, 100, -100, -100, 0, 0);
        face(tessellator, -100, 100, -100, -100, -100, -100, 100, -100, -100, 100, 100, -100, 1, 1);
        face(tessellator, 100, 100, 100, 100, -100, 100, -100, -100, 100, -100, 100, 100, 2, 0);
        face(tessellator, -100, 100, 100, -100, 100, -100, 100, 100, -100, 100, 100, 100, 1, 0);
        face(tessellator, 100, 100, -100, 100, -100, -100, 100, -100, 100, 100, 100, 100, 2, 1);
        face(tessellator, -100, 100, 100, -100, -100, 100, -100, -100, -100, -100, 100, -100, 0, 1);
        tessellator.draw();
    }

    private static void face(Tessellator b, double ax, double ay, double az, double bx, double by, double bz, double cx, double cy,
                             double cz, double dx, double dy, double dz, int column, int row) {
        double u0 = column / 3.0, u1 = (column + 1) / 3.0, v0 = row * 0.5, v1 = (row + 1) * 0.5;
        b.addVertexWithUV(ax, ay, az, u0, v0);
        b.addVertexWithUV(bx, by, bz, u0, v1);
        b.addVertexWithUV(cx, cy, cz, u1, v1);
        b.addVertexWithUV(dx, dy, dz, u1, v0);
    }
}
