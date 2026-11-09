package dev.fastpacks.pack;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Vanilla only lists packs in the top level of the resourcepacks folder. This finds the packs inside sub-folders too (up to three
 * levels), using vanilla's own rules: a zip file is a pack, a directory with a pack.mcmeta is a pack, any other directory is a
 * folder that is searched. No Minecraft dependencies.
 */
public final class NestedPacks {
    public static final int MAX_DEPTH = 3;

    private NestedPacks() {}

    /** Adds every pack below the folders of {@code root} to {@code out}; the top-level packs are the caller's. */
    public static void collect(File root, List<File> out) {
        File[] children = root.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory() && !isPackDirectory(child)) collectFolder(child, out, 1);
        }
    }

    private static void collectFolder(File folder, List<File> out, int depth) {
        File[] children = folder.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isFile() && child.getName().toLowerCase().endsWith(".zip")) out.add(child);
            else if (child.isDirectory()) {
                if (isPackDirectory(child)) out.add(child);
                else if (depth < MAX_DEPTH) collectFolder(child, out, depth + 1);
            }
        }
    }

    public static boolean isPackDirectory(File dir) {
        return new File(dir, "pack.mcmeta").isFile();
    }

    /** "folder/Pack.zip" for a pack below the root, or null for a top-level pack or one outside the root. */
    public static String nestedName(File root, File pack) {
        try {
            Path rel = root.toPath().relativize(pack.toPath());
            if (rel.getNameCount() > 1 && !rel.startsWith("..")) return rel.toString().replace('\\', '/');
        } catch (Exception ignored) {
        }
        return null;
    }
}
