package dev.fastpacks.index;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CompletableFuture;

public final class PackRecord {
    public final File file;
    public final String relativePath;
    public final String folderPath;
    public final String name;
    public final boolean directory;
    volatile CompletableFuture<PackMetadata> metadata;
    volatile CompletableFuture<BufferedImage> icon;

    PackRecord(File file, String relativePath, boolean directory) {
        this.file = file; this.relativePath = relativePath.replace('\\', '/'); this.directory = directory;
        int slash = this.relativePath.lastIndexOf('/');
        this.folderPath = slash < 0 ? "" : this.relativePath.substring(0, slash);
        this.name = slash < 0 ? this.relativePath : this.relativePath.substring(slash + 1);
    }

    private volatile long existsCheckedAt;
    private volatile boolean existsCached = true;

    /** Stat results are cached for a second: the GUI asks several times per row per frame, and each ask was two filesystem calls. */
    public boolean exists() {
        long now = System.nanoTime();
        long last = existsCheckedAt;
        if (last != 0L && now - last < 1_000_000_000L) return existsCached;
        boolean value = file.isFile() || file.isDirectory();
        existsCached = value;
        existsCheckedAt = now == 0L ? 1L : now;
        return value;
    }
}
