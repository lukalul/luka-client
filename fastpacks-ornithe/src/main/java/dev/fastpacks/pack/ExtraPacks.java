package dev.fastpacks.pack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The packs of the standard launcher's folder (.minecraft/resourcepacks) are offered as well, under a folder named
 * ".minecraft" in the pack list, so a player sees the packs they already have without copying them over. That folder
 * is only read: nothing is ever created, moved or deleted in it.
 *
 * The system property luka.extraPacks names another folder to use instead, or turns it off with "false".
 */
public final class ExtraPacks {
    /** The folder the extra packs appear under, and the start of their saved names (".minecraft/Pack.zip"). */
    public static final String FOLDER = ".minecraft";
    private static final String PREFIX = FOLDER + "/";

    private static String cachedFor;
    private static File cachedRoot;

    private ExtraPacks() {}

    /** The extra folder, or null when there is none, it is turned off, or it is the game's own folder. */
    public static synchronized File root(File ownRoot) {
        String key = String.valueOf(ownRoot) + "|" + System.getProperty("luka.extraPacks", "");
        if (key.equals(cachedFor)) return cachedRoot;
        cachedFor = key;
        cachedRoot = resolve(ownRoot);
        return cachedRoot;
    }

    private static File resolve(File ownRoot) {
        String setting = System.getProperty("luka.extraPacks", "").trim();
        if ("false".equalsIgnoreCase(setting)) return null;
        File dir = setting.isEmpty() ? defaultFolder() : new File(setting);
        if (dir == null || !dir.isDirectory()) return null;
        try {
            Path extra = dir.getCanonicalFile().toPath();
            if (ownRoot != null) {
                Path own = ownRoot.getCanonicalFile().toPath();
                // the game already uses that folder, or one holds the other: nothing to add
                if (own.equals(extra) || own.startsWith(extra) || extra.startsWith(own)) return null;
                // a real folder of that name in the game's own folder takes the name
                if (new File(ownRoot, FOLDER).exists()) return null;
            }
            return extra.toFile();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static File defaultFolder() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home", "");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return appData == null || appData.isEmpty() ? null : new File(appData, ".minecraft" + File.separator + "resourcepacks");
        }
        if (os.contains("mac")) return new File(home, "Library/Application Support/minecraft/resourcepacks");
        return new File(home, ".minecraft/resourcepacks");
    }

    /** The name a pack in the extra folder is listed and saved under, or null for any other file. */
    public static String nameOf(File ownRoot, File pack) {
        File extra = root(ownRoot);
        if (extra == null || pack == null) return null;
        try {
            Path rel = extra.toPath().relativize(pack.getAbsoluteFile().toPath().normalize());
            if (rel.getNameCount() == 0 || rel.startsWith("..")) return null;
            return PREFIX + rel.toString().replace('\\', '/');
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static boolean isExtra(File ownRoot, File pack) {
        return nameOf(ownRoot, pack) != null;
    }

    /** True for the extra folder itself and anything listed below it ("", ".minecraft", ".minecraft/sub"). */
    public static boolean isExtraPath(String relativePath) {
        if (relativePath == null) return false;
        String p = relativePath.replace('\\', '/');
        return p.equals(FOLDER) || p.startsWith(PREFIX);
    }

    /** File names of the packs at the top of the game's own folder: a pack of the same name in the extra folder is left out. */
    public static Set<String> ownNames(File ownRoot) {
        Set<String> names = new HashSet<String>();
        File[] files = ownRoot == null ? null : ownRoot.listFiles();
        if (files != null) for (File f : files) names.add(f.getName().toLowerCase(Locale.ROOT));
        return names;
    }

    /** Adds every pack of the extra folder (top level and up to three folders down) to {@code out}. */
    public static void collect(File ownRoot, List<File> out) {
        File extra = root(ownRoot);
        if (extra == null) return;
        File[] children = extra.listFiles();
        if (children == null) return;
        Set<String> own = ownNames(ownRoot);
        for (File child : children) {
            boolean pack = child.isFile() ? child.getName().toLowerCase(Locale.ROOT).endsWith(".zip") : NestedPacks.isPackDirectory(child);
            if (pack && !own.contains(child.getName().toLowerCase(Locale.ROOT))) out.add(child);
        }
        NestedPacks.collect(extra, out);
    }
}
