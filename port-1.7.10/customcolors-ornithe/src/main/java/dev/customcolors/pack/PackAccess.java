package dev.customcolors.pack;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.mixin.CcPackFileAccess;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.resources.ResourcePackRepository$Entry;
import net.minecraft.util.ResourceLocation;

/** Reads pack files through the game's resource manager (so normal pack stacking applies). */
public final class PackAccess {
    /** Pack roots in lookup order. */
    public static final String[] ROOTS = {"mcpatcher", "optifine"};
    private static final String ASSETS = "assets/minecraft/";

    private PackAccess() {
    }

    public static ResourceLocation at(String path) {
        return new ResourceLocation("minecraft", path);
    }

    private static IResourceManager manager() {
        return Minecraft.getMinecraft().getResourceManager();
    }

    private static InputStream open(ResourceLocation loc) throws IOException {
        return manager().getResource(loc).getInputStream();
    }

    public static boolean exists(ResourceLocation loc) {
        InputStream in = null;
        try {
            in = open(loc);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            close(in);
        }
    }

    /** The decoded image, or null when missing (silently) or unreadable (one log line). */
    public static BufferedImage image(ResourceLocation loc) {
        InputStream in = null;
        try {
            in = open(loc);
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                CustomColorsMod.LOG.warn("customcolors: {} is not a readable image", loc);
            }
            return img;
        } catch (FileNotFoundException e) {
            return null;
        } catch (Exception e) {
            CustomColorsMod.LOG.warn("customcolors: cannot read {}: {}", loc, e.toString());
            return null;
        } finally {
            close(in);
        }
    }

    /** The parsed properties, or null when the file is missing or unreadable. */
    public static Properties properties(ResourceLocation loc) {
        InputStream in = null;
        try {
            in = open(loc);
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return p;
        } catch (FileNotFoundException e) {
            return null;
        } catch (Exception e) {
            CustomColorsMod.LOG.warn("customcolors: cannot read {}: {}", loc, e.toString());
            return null;
        } finally {
            close(in);
        }
    }

    public static int[] pixels(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        return img.getRGB(0, 0, w, h, null, 0, w);
    }

    /**
     * Paths (inside the minecraft namespace, e.g. {@code mcpatcher/anim/logo.properties}) of every file
     * under {@code <root>/<folder>/} in the selected packs, for every root. Read from the pack folders and
     * zips directly, since resource packs cannot list their files.
     */
    public static SortedSet<String> listSelected(String folder) {
        SortedSet<String> out = new TreeSet<String>();
        ResourcePackRepository repo = Minecraft.getMinecraft().getResourcePackRepository();
        if (repo == null) {
            return out;
        }
        for (Object o : repo.getRepositoryEntries()) {
            try {
                // 1.7.10's mapped jar exposes the entry class only under its binary name
                IResourcePack pack = ((ResourcePackRepository$Entry) o).getResourcePack();
                if (!(pack instanceof CcPackFileAccess)) {
                    continue;
                }
                File file = ((CcPackFileAccess) pack).customcolors$file();
                if (file == null) {
                    continue;
                }
                for (String root : ROOTS) {
                    String inPack = ASSETS + root + "/" + folder + "/";
                    if (file.isDirectory()) {
                        walk(new File(file, inPack), root + "/" + folder + "/", out);
                    } else if (file.isFile()) {
                        scanZip(file, inPack, out);
                    }
                }
            } catch (Exception e) {
                CustomColorsMod.LOG.warn("customcolors: cannot list a resource pack: {}", e.toString());
            }
        }
        return out;
    }

    private static void walk(File dir, String prefix, SortedSet<String> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                walk(child, prefix + child.getName() + "/", out);
            } else {
                out.add(prefix + child.getName());
            }
        }
    }

    private static void scanZip(File zip, String inPack, SortedSet<String> out) throws IOException {
        ZipFile zf = new ZipFile(zip);
        try {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (!e.isDirectory() && name.startsWith(inPack)) {
                    out.add(name.substring(ASSETS.length()));
                }
            }
        } finally {
            zf.close();
        }
    }

    private static void close(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignored) {
                // nothing to do
            }
        }
    }
}
