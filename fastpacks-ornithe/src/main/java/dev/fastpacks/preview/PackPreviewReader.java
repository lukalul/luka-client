package dev.fastpacks.preview;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.*;
import javax.imageio.ImageIO;

/**
 * No Minecraft dependencies: safe to execute entirely on an index worker.
 *
 * <p>Packs in the wild are messy: zips with the whole pack inside one wrapper folder ("MyPack/assets/..."), extracted folders with
 * the assets a few levels down, upper-case names, and textures filed under blocks/blocks. So instead of checking fixed paths the
 * reader lists the pack once and picks, for every wanted texture, the shallowest file whose path ends in
 * ".../textures/items/name.png" (or blocks, or the singular item/block directory names of newer packs).</p>
 */
public final class PackPreviewReader {
    /** Slideshow groups: each shows a handful of related textures side by side, then the next group takes over. */
    public static final String[] GROUP_LABELS = {"Swords", "Armor", "Tools", "Combat", "Blocks", "Ores"};
    private static final String[] GROUP_DIRS = {"items", "items", "items", "items", "blocks", "blocks"};
    private static final String[][] GROUP_NAMES = {
        {"wood_sword", "stone_sword", "iron_sword", "gold_sword", "diamond_sword"},
        {"diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots"},
        {"wood_pickaxe", "stone_pickaxe", "iron_pickaxe", "gold_pickaxe", "diamond_pickaxe"},
        {"bow_standby", "arrow", "apple_golden", "ender_pearl", "potion_bottle_splash"},
        {"stone", "dirt", "cobblestone", "planks_oak", "obsidian"},
        {"coal_ore", "iron_ore", "gold_ore", "diamond_ore", "emerald_ore"}
    };
    private static final int MAX_SIZE = 64;
    private static final int MAX_DIRECTORY_FILES = 60000;
    private static final Pattern TEXTURE = Pattern.compile("(?:.*/)?textures/(items?|blocks?)/(?:.*/)?([^/]+)\\.png");

    private PackPreviewReader() {}

    /** Opens one texture of a pack by its index key ("items/diamond_sword"). */
    private interface Source { InputStream open(String path) throws IOException; }

    public static PackPreview read(File pack) {
        ArrayList<PackPreview.Item> out = new ArrayList<>();
        if (pack == null || !pack.exists()) return new PackPreview(out);
        try {
            if (pack.isDirectory()) readDirectory(pack, out); else readZip(pack, out);
        } catch (Throwable ignored) { out.clear(); }
        return new PackPreview(out);
    }

    /** Records a candidate path for a texture, keeping the one with the fewest folders above it. */
    private static void consider(Map<String, String> best, String path) {
        Matcher m = TEXTURE.matcher(path.toLowerCase(Locale.ROOT));
        if (!m.matches()) return;
        String dir = m.group(1).startsWith("item") ? "items" : "blocks";
        String key = dir + "/" + m.group(2);
        String current = best.get(key);
        if (current == null || depth(path) < depth(current)) best.put(key, path);
    }

    private static int depth(String path) {
        int n = 0;
        for (int i = 0; i < path.length(); i++) if (path.charAt(i) == '/') n++;
        return n;
    }

    private static void collect(Map<String, String> best, Source source, List<PackPreview.Item> out) throws IOException {
        for (int g = 0; g < GROUP_NAMES.length; g++) {
            for (String name : GROUP_NAMES[g]) {
                String path = best.get(GROUP_DIRS[g] + "/" + name);
                if (path == null) continue;
                try (InputStream in = source.open(path)) { add(out, label(name), g, in); }
            }
        }
    }

    private static void readDirectory(File root, List<PackPreview.Item> out) throws IOException {
        Map<String, String> best = new HashMap<>();
        Path base = root.toPath();
        try (Stream<Path> walk = Files.walk(base, 10)) {
            Iterator<Path> it = walk.iterator();
            int seen = 0;
            while (it.hasNext() && seen++ < MAX_DIRECTORY_FILES) {
                Path p = it.next();
                if (Files.isRegularFile(p)) consider(best, base.relativize(p).toString().replace('\\', '/'));
            }
        }
        collect(best, path -> new FileInputStream(new File(root, path)), out);
    }

    private static void readZip(File file, List<PackPreview.Item> out) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            Map<String, String> best = new HashMap<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (!e.isDirectory()) consider(best, e.getName());
            }
            collect(best, path -> {
                ZipEntry e = zip.getEntry(path);
                if (e == null) throw new FileNotFoundException(path);
                return zip.getInputStream(e);
            }, out);
        }
    }

    private static String label(String texture) {
        StringBuilder sb = new StringBuilder();
        for (String part : texture.split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static void add(List<PackPreview.Item> out, String label, int group, InputStream in) throws IOException {
        BufferedImage source = ImageIO.read(in);
        if (source == null || source.getWidth() < 1 || source.getHeight() < 1) return;
        // Animations are vertical strips in 1.8 packs; the first square is frame zero.
        int frame = Math.min(source.getWidth(), source.getHeight());
        BufferedImage first = source.getSubimage(0, 0, frame, frame);
        int size = Math.min(MAX_SIZE, frame);
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(first, 0, 0, size, size, null);
        g.dispose();
        out.add(new PackPreview.Item(label, scaled, group));
    }
}
