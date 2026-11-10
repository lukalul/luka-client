package dev.customcolors;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Loads MCPatcher/OptiFine colormaps and lightmaps from resource packs. Adapted from MCPatcher's Colorizer (see NOTICE). */
public final class CustomColors implements IResourceManagerReloadListener {
    private static final Logger LOG = LogManager.getLogger("CustomColors");
    /** Cera already applies these on the OpenGL renderer. */
    private static final boolean ENABLED = !FabricLoader.getInstance().isModLoaded("cera");
    private static final String[] ROOTS = {"mcpatcher", "optifine"};
    private static final int[] WORLDS = {-1, 0, 1};

    private static volatile ColorMap water, swampGrass, swampFoliage, pine, birch, sky, fog, underwater, underlava;
    private static volatile int[] redstone, pumpkinStem, melonStem;
    private static volatile int lilypad = -1;
    private static volatile int waterBottle = -1;
    private static volatile int[] lavaDrop, mycelium, xpOrb;
    private static final Map<String, Integer> POTIONS = new HashMap<>();
    private static final Map<String, Integer> EGG_SHELLS = new HashMap<>();
    private static final Map<String, Integer> EGG_SPOTS = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static final Smoother SKY = new Smoother(), FOG = new Smoother(), UNDERWATER = new Smoother(), UNDERLAVA = new Smoother();

    private CustomColors() {
    }

    public static void register() {
        if (!ENABLED) return;
        RandomMobs.enabled = true;
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(new CustomColors());
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        RandomMobs.clear();
        RandomMobs.enabled = !"false".equals(System.getProperty("luka.randomMobs"));
        Animations.reload(manager);
        water = map(manager, "water");
        swampGrass = map(manager, "swampgrass");
        swampFoliage = map(manager, "swampfoliage");
        pine = map(manager, "pine");
        birch = map(manager, "birch");
        sky = map(manager, "sky0");
        fog = map(manager, "fog0");
        underwater = map(manager, "underwater");
        underlava = map(manager, "underlava");
        redstone = row(manager, "redstone", 16);
        int[] stem = row(manager, "stem", 8);
        int[] pumpkin = row(manager, "pumpkinstem", 8);
        int[] melon = row(manager, "melonstem", 8);
        pumpkinStem = pumpkin != null ? pumpkin : stem;
        melonStem = melon != null ? melon : stem;
        lilypad = lilypad(manager);
        lavaDrop = table(manager, "lavadrop");
        mycelium = table(manager, "myceliumparticle");
        xpOrb = table(manager, "xporb");
        loadItemColors(manager);
        PotionColorCache.clear();
        SKY.reset();
        FOG.reset();
        UNDERWATER.reset();
        UNDERLAVA.reset();
        Lightmaps.clear();
        int lightmaps = 0;
        for (int world : WORLDS) {
            for (String root : ROOTS) {
                BufferedImage image = image(manager, root + "/lightmap/world" + world + ".png");
                if (image == null) continue;
                if (Lightmaps.put(world, image)) lightmaps++;
                else LOG.warn("Ignoring lightmap for world {}: it must be 32 or 64 pixels high", world);
                break;
            }
        }
        LOG.info("Loaded custom colours: water={} swamp={} pine={} birch={} sky={} fog={} underwater={} redstone={} stems={} lilypad={} lightmaps={}",
                water != null, swampGrass != null || swampFoliage != null, pine != null, birch != null, sky != null, fog != null,
                underwater != null, redstone != null, pumpkinStem != null || melonStem != null, lilypad != -1, lightmaps);
    }

    private static ColorMap map(IResourceManager manager, String name) {
        if ("false".equals(System.getProperty("luka.customColors"))) return null;
        for (String root : ROOTS) {
            String base = root + "/colormap/" + name;
            Properties properties = properties(manager, base + ".properties");
            if (properties != null) {
                String format = properties.getProperty("format", "").trim().toLowerCase(java.util.Locale.ROOT);
                if (format.equals("fixed") || format.equals("0")) {
                    try {
                        return ColorMap.fixed(Integer.parseInt(properties.getProperty("color", "ffffff").trim(), 16));
                    } catch (NumberFormatException e) {
                        LOG.warn("Bad colour in {}.properties", base);
                        continue;
                    }
                }
                if (format.equals("grid") || format.equals("2")) {
                    LOG.warn("Skipping {}: biome grid colormaps are not supported yet", base);
                    continue;
                }
            }
            BufferedImage image = image(manager, base + ".png");
            if (image != null) return ColorMap.image(image);
        }
        return null;
    }

    private static int[] row(IResourceManager manager, String name, int entries) {
        if ("false".equals(System.getProperty("luka.customColors"))) return null;
        ColorMap map = map(manager, name);
        return map == null ? null : map.row(entries);
    }

    private static int[] table(IResourceManager manager, String name) {
        ColorMap map = map(manager, name);
        return map == null ? null : map.all();
    }

    private static void loadItemColors(IResourceManager manager) {
        POTIONS.clear();
        EGG_SHELLS.clear();
        EGG_SPOTS.clear();
        waterBottle = -1;
        for (String root : ROOTS) {
            Properties properties = properties(manager, root + "/color.properties");
            if (properties == null) continue;
            for (String key : properties.stringPropertyNames()) {
                int color;
                try {
                    color = Integer.parseInt(properties.getProperty(key).trim(), 16);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (key.equals("potion.water")) waterBottle = color;
                else if (key.startsWith("potion.")) POTIONS.put(key, color);
                else if (key.startsWith("egg.shell.")) EGG_SHELLS.put(key.substring("egg.shell.".length()), color);
                else if (key.startsWith("egg.spots.")) EGG_SPOTS.put(key.substring("egg.spots.".length()), color);
                else if (key.startsWith("egg.")) EGG_SHELLS.put(key.substring("egg.".length()), color);
            }
            return;
        }
    }

    /** {@code name} is the game's potion name, such as potion.moveSpeed. */
    public static int potion(String name, int original) {
        Integer color = POTIONS.get(name);
        return color != null ? color : original;
    }

    public static int waterBottle(int original) {
        return waterBottle != -1 ? waterBottle : original;
    }

    public static int egg(String entity, boolean spots, int original) {
        if (entity == null) return original;
        Integer color = (spots ? EGG_SPOTS : EGG_SHELLS).get(entity);
        return color != null ? color : original;
    }

    /** -1 when the pack has no table. */
    public static int lavaDrop(int age) {
        int[] table = lavaDrop;
        return table == null || table.length == 0 ? -1 : table[Math.max(Math.min(table.length - 1, age - 20), 0)];
    }

    /** -1 when the pack has no table. */
    public static int mycelium() {
        int[] table = mycelium;
        return table == null || table.length == 0 ? -1 : table[RANDOM.nextInt(table.length)];
    }

    /** -1 when the pack has no table. */
    public static int xpOrb(float timer) {
        int[] table = xpOrb;
        return table == null || table.length == 0 ? -1 : table[(int) ((Math.sin(timer / 4.0) + 1.0) * (table.length - 1) / 2.0)];
    }

    private static int lilypad(IResourceManager manager) {
        for (String root : ROOTS) {
            Properties properties = properties(manager, root + "/color.properties");
            if (properties == null) continue;
            String value = properties.getProperty("lilypad");
            if (value == null) continue;
            try {
                return Integer.parseInt(value.trim(), 16);
            } catch (NumberFormatException e) {
                LOG.warn("Bad lilypad colour: {}", value);
            }
        }
        return -1;
    }

    private static Properties properties(IResourceManager manager, String path) {
        try (InputStream in = manager.getResource(new ResourceLocation("minecraft", path)).getInputStream()) {
            Properties properties = new Properties();
            properties.load(in);
            return properties;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static BufferedImage image(IResourceManager manager, String path) {
        try (InputStream in = manager.getResource(new ResourceLocation("minecraft", path)).getInputStream()) {
            return javax.imageio.ImageIO.read(in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ---- block colours ----

    public static boolean hasWater() {
        return water != null;
    }

    public static int water(BiomeGenBase biome, int x, int y, int z) {
        return water.sample(biome, x, y, z);
    }

    public static boolean hasSwampGrass() {
        return swampGrass != null;
    }

    public static int swampGrass(BiomeGenBase biome, int x, int y, int z) {
        return swampGrass.sample(biome, x, y, z);
    }

    public static boolean hasSwampFoliage() {
        return swampFoliage != null;
    }

    public static int swampFoliage(BiomeGenBase biome, int x, int y, int z) {
        return swampFoliage.sample(biome, x, y, z);
    }

    public static boolean hasPine() {
        return pine != null;
    }

    public static int pine(BiomeGenBase biome, int x, int y, int z) {
        return pine.sample(biome, x, y, z);
    }

    public static boolean hasBirch() {
        return birch != null;
    }

    public static int birch(BiomeGenBase biome, int x, int y, int z) {
        return birch.sample(biome, x, y, z);
    }

    /** -1 when the pack sets no lily pad colour. */
    public static int lilypad() {
        return lilypad;
    }

    /** -1 when the pack has no table. */
    public static int redstone(int power) {
        int[] table = redstone;
        return table == null ? -1 : table[power & 15];
    }

    /** -1 when the pack has no table. */
    public static int stem(boolean pumpkin, int age) {
        int[] table = pumpkin ? pumpkinStem : melonStem;
        return table == null ? -1 : table[age & 7];
    }

    // ---- world colours, overworld only ----

    public static boolean hasSky(World world) {
        return sky != null && world.provider.dimensionId == 0;
    }

    public static int sky(World world) {
        return SKY.apply(sky, world);
    }

    public static boolean hasFog(World world) {
        return fog != null && world.provider.dimensionId == 0;
    }

    public static int fog(World world) {
        return FOG.apply(fog, world);
    }

    public static boolean hasUnderwater() {
        return underwater != null;
    }

    public static int underwater(World world) {
        return UNDERWATER.apply(underwater, world);
    }

    public static boolean hasUnderlava() {
        return underlava != null;
    }

    public static int underlava(World world) {
        return UNDERLAVA.apply(underlava, world);
    }

    /** Eases colour changes over about three seconds so crossing a biome border does not snap the sky. */
    private static final class Smoother {
        private static final float MILLIS = 3000.0F;
        private final float[] current = new float[3];
        private boolean started;
        private long last;

        void reset() {
            started = false;
        }

        int apply(ColorMap map, World world) {
            Entity view = Minecraft.getMinecraft().renderViewEntity;
            if (view == null) return 0;
            int x = MathHelper.floor_double(view.posX), y = MathHelper.floor_double(view.posY), z = MathHelper.floor_double(view.posZ);
            int target = map.sample(world.getBiomeGenForCoords(x, z), x, y, z);
            long now = System.currentTimeMillis();
            float step = started ? Math.min(1.0F, (now - last) / MILLIS) : 1.0F;
            for (int k = 0; k < 3; k++) {
                current[k] += ((target >> (16 - 8 * k) & 0xFF) / 255.0F - current[k]) * step;
            }
            started = true;
            last = now;
            return (int) (current[0] * 255.0F) << 16 | (int) (current[1] * 255.0F) << 8 | (int) (current[2] * 255.0F);
        }
    }
}
