package dev.customcolors;

import dev.customcolors.mixin.AbstractResourcePackAccessor;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.resources.ResourcePackRepository$Entry;
import net.minecraft.util.ResourceLocation;

/** Custom animations: mcpatcher/anim/*.properties paint frames from one image onto a region of another texture. */
public final class Animations {
    private static final String[] ROOTS = {"assets/minecraft/mcpatcher/anim/", "assets/minecraft/optifine/anim/"};
    private static final List<Animation> ACTIVE = new ArrayList<>();

    private Animations() {
    }

    static void reload(IResourceManager manager) {
        ACTIVE.clear();
        for (String name : listProperties()) {
            try {
                Animation animation = Animation.load(manager, name);
                if (animation != null) ACTIVE.add(animation);
            } catch (Exception broken) {
                System.err.println("[customcolors] Skipping animation " + name + ": " + broken);
            }
        }
        if (!ACTIVE.isEmpty()) System.out.println("[customcolors] Loaded " + ACTIVE.size() + " custom animations");
    }

    public static void tick() {
        for (int index = 0; index < ACTIVE.size(); index++) ACTIVE.get(index).tick();
    }

    private static TreeSet<String> listProperties() {
        TreeSet<String> names = new TreeSet<>();
        ResourcePackRepository repository = Minecraft.getMinecraft().getResourcePackRepository();
        for (Object listed : repository.getRepositoryEntries()) {
            ResourcePackRepository$Entry entry = (ResourcePackRepository$Entry) listed;
            IResourcePack pack = entry.getResourcePack();
            if (!(pack instanceof AbstractResourcePackAccessor)) continue;
            File file = ((AbstractResourcePackAccessor) pack).customcolors$file();
            try {
                if (file.isDirectory()) listFolder(file, names);
                else listZip(file, names);
            } catch (Exception unreadable) {
                System.err.println("[customcolors] Could not list " + file.getName() + ": " + unreadable);
            }
        }
        return names;
    }

    private static void listZip(File file, TreeSet<String> names) throws Exception {
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) add(entries.nextElement().getName(), names);
        }
    }

    private static void listFolder(File pack, TreeSet<String> names) {
        for (String root : ROOTS) {
            File[] files = new File(pack, root).listFiles();
            if (files != null) for (File file : files) if (file.isFile()) add(root + file.getName(), names);
        }
    }

    private static void add(String entry, TreeSet<String> names) {
        if (!entry.endsWith(".properties") || entry.substring(entry.lastIndexOf('/') + 1).startsWith("._")) return;
        // many packs are zipped with one extra folder around assets/
        for (String root : ROOTS) {
            int at = entry.indexOf(root);
            if (at >= 0 && (at == 0 || entry.charAt(at - 1) == '/')) names.add(entry.substring(at + "assets/minecraft/".length()));
        }
    }

    private static final class Animation {
        private final ResourceLocation target;
        private final int x, y;
        private final BufferedImage[] frames;
        private final int[] order, durations;
        private int step, ticks = -1, uploadedTexture = -1;

        private Animation(ResourceLocation target, int x, int y, BufferedImage[] frames, int[] order, int[] durations) {
            this.target = target;
            this.x = x;
            this.y = y;
            this.frames = frames;
            this.order = order;
            this.durations = durations;
        }

        static Animation load(IResourceManager manager, String name) throws Exception {
            Properties properties = new Properties();
            try (InputStream in = manager.getResource(new ResourceLocation(name)).getInputStream()) {
                properties.load(in);
            }
            String from = properties.getProperty("from"), to = properties.getProperty("to");
            if (from == null || to == null) return null;
            int x = number(properties, "x", 0), y = number(properties, "y", 0);
            int width = number(properties, "w", 0), height = number(properties, "h", 0);
            if (width <= 0 || height <= 0) return null;
            BufferedImage source;
            try (InputStream in = manager.getResource(new ResourceLocation(resolve(from.trim(), name))).getInputStream()) {
                source = ImageIO.read(in);
            }
            if (source == null) return null;
            int frameHeight = Math.max(1, source.getWidth() * height / width);
            int count = Math.max(1, source.getHeight() / frameHeight);
            BufferedImage[] frames = new BufferedImage[count];
            for (int frame = 0; frame < count; frame++) {
                BufferedImage cut = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = cut.createGraphics();
                graphics.drawImage(source.getSubimage(0, frame * frameHeight, source.getWidth(), frameHeight), 0, 0, width, height, null);
                graphics.dispose();
                frames[frame] = cut;
            }
            int defaultDuration = Math.max(1, number(properties, "duration", 1));
            int steps = 0;
            while (properties.getProperty("tile." + steps) != null) steps++;
            if (steps == 0) steps = count;
            int[] order = new int[steps], durations = new int[steps];
            for (int index = 0; index < steps; index++) {
                order[index] = Math.floorMod(number(properties, "tile." + index, index), count);
                durations[index] = Math.max(1, number(properties, "duration." + index, defaultDuration));
            }
            return new Animation(new ResourceLocation(resolve(to.trim(), name)), x, y, frames, order, durations);
        }

        /** "./a.png" sits next to the properties file, "~/a.png" under the pack's mcpatcher folder, anything else is a normal path. */
        private static String resolve(String path, String propertiesName) {
            if (path.startsWith("./")) return propertiesName.substring(0, propertiesName.lastIndexOf('/') + 1) + path.substring(2);
            if (path.startsWith("~/")) return propertiesName.substring(0, propertiesName.indexOf('/') + 1) + path.substring(2);
            return path;
        }

        private static int number(Properties properties, String key, int fallback) {
            String value = properties.getProperty(key);
            if (value == null) return fallback;
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException wrong) {
                return fallback;
            }
        }

        void tick() {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen == null && target.getResourcePath().startsWith("textures/gui/")) return; // nothing shows a menu texture during play
            ITextureObject texture = mc.getTextureManager().getTexture(target);
            if (texture == null) return;
            int id = texture.getGlTextureId();
            boolean fresh = id != uploadedTexture;
            if (order.length > 1 && ++ticks >= durations[step]) {
                ticks = 0;
                step = (step + 1) % order.length;
                fresh = true;
            }
            if (!fresh) return;
            uploadedTexture = id;
            GlStateManager.bindTexture(id);
            TextureUtil.uploadTextureImageSub(id, frames[order[step]], x, y, false, false);
        }
    }
}
