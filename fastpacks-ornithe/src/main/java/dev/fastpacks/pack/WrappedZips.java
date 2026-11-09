package dev.fastpacks.pack;

import java.util.Enumeration;
import java.util.zip.ZipEntry;

/**
 * Many resource packs are zipped with the whole pack inside one wrapper folder ("MyPack/assets/...", "MyPack/pack.mcmeta") because
 * somebody zipped the folder instead of its contents. The vanilla loader only looks for "assets/..." and "pack.mcmeta" at the top
 * of the zip, so those packs show a missing-metadata icon and contribute no textures. Other loaders tolerate them; this finds the
 * wrapper so FastPacks can read them the same way. No Minecraft dependencies.
 */
public final class WrappedZips {
    private WrappedZips() {}

    /**
     * The folder prefix (with a trailing slash) that holds the pack, or "" when the zip is laid out normally or no pack can be found.
     * A zip is only treated as wrapped when it has no top-level pack.mcmeta and no top-level assets folder.
     */
    public static String findRoot(Enumeration<? extends ZipEntry> entries) {
        String best = null;
        int bestDepth = Integer.MAX_VALUE;
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.equals("pack.mcmeta") || name.startsWith("assets/")) return "";
            int at = name.indexOf("/assets/");
            String root = null;
            if (at > 0) root = name.substring(0, at + 1);
            else if (name.endsWith("/pack.mcmeta")) root = name.substring(0, name.length() - "pack.mcmeta".length());
            if (root == null || root.startsWith("__MACOSX/")) continue;
            int depth = 0;
            for (int i = 0; i < root.length(); i++) if (root.charAt(i) == '/') depth++;
            if (depth < bestDepth) { best = root; bestDepth = depth; }
        }
        return best == null ? "" : best;
    }
}
