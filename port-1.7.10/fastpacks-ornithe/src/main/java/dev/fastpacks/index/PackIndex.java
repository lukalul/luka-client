package dev.fastpacks.index;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.fastpacks.pack.ExtraPacks;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

public final class PackIndex implements AutoCloseable {
    private final File root;
    private final ExecutorService workers;
    private volatile List<PackRecord> packs = Collections.emptyList();
    private volatile List<String> folders = Collections.emptyList();
    private volatile long signature = Long.MIN_VALUE;
    private volatile CompletableFuture<List<PackRecord>> active;

    public PackIndex(File root) {
        this.root = root;
        final AtomicInteger n = new AtomicInteger();
        this.workers = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "FastPacks-Indexer-" + n.incrementAndGet()); t.setDaemon(true); return t;
        });
    }

    public synchronized CompletableFuture<List<PackRecord>> refresh(boolean force) {
        long now = signature(root);
        if (!force && now == signature && active == null) return CompletableFuture.completedFuture(packs);
        if (active != null && !active.isDone()) return active;
        active = CompletableFuture.supplyAsync(() -> scan(root), workers).whenComplete((result, error) -> {
            synchronized (PackIndex.this) {
                if (error == null) { packs = result; folders = scanFolders(root); signature = signature(root); }
                active = null;
            }
        });
        return active;
    }

    public boolean isLoading() { CompletableFuture<List<PackRecord>> f = active; return f != null && !f.isDone(); }
    public List<PackRecord> snapshot() { return packs; }
    public List<String> folders() { return folders; }

    public List<PackRecord> search(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return snapshot();
        ArrayList<PackRecord> out = new ArrayList<>();
        for (PackRecord p : snapshot()) {
            PackMetadata m = p.metadata == null ? null : p.metadata.getNow(null);
            if (p.relativePath.toLowerCase(Locale.ROOT).contains(q) ||
                (m != null && m.description.toLowerCase(Locale.ROOT).contains(q))) out.add(p);
        }
        return out;
    }

    public CompletableFuture<PackMetadata> metadata(PackRecord p) {
        synchronized (p) { if (p.metadata == null) p.metadata = CompletableFuture.supplyAsync(() -> readMetadata(p.file), workers); return p.metadata; }
    }

    public CompletableFuture<BufferedImage> icon(PackRecord p) {
        synchronized (p) { if (p.icon == null) p.icon = CompletableFuture.supplyAsync(() -> readIcon(p.file), workers); return p.icon; }
    }

    public <T> CompletableFuture<T> submit(java.util.concurrent.Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> { try { return task.call(); } catch (Exception e) { throw new CompletionException(e); } }, workers);
    }

    private static List<PackRecord> scan(File root) {
        ArrayList<PackRecord> out = new ArrayList<>(); scanInto(root, root, 0, out, "");
        File extra = ExtraPacks.root(root);
        if (extra != null) {
            // the standard launcher's packs, read only, under the folder ".minecraft"; a top-level pack of the same name here wins
            ArrayList<PackRecord> more = new ArrayList<>(); scanInto(extra, extra, 0, more, ExtraPacks.FOLDER + "/");
            Set<String> own = ExtraPacks.ownNames(root);
            for (PackRecord p : more) if (!p.folderPath.equals(ExtraPacks.FOLDER) || !own.contains(p.name.toLowerCase(Locale.ROOT))) out.add(p);
        }
        out.sort(Comparator.comparing(a -> a.relativePath.toLowerCase(Locale.ROOT))); return Collections.unmodifiableList(out);
    }

    private static void scanInto(File root, File dir, int depth, List<PackRecord> out, String prefix) {
        File[] files = dir.listFiles(); if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File f : files) {
            if (f.isDirectory()) {
                if (new File(f, "pack.mcmeta").isFile()) out.add(new PackRecord(f, prefix + relativize(root, f), true));
                else if (depth < 3) scanInto(root, f, depth + 1, out, prefix);
            } else if (isZip(f)) out.add(new PackRecord(f, prefix + relativize(root, f), false));
        }
    }

    private static List<String> scanFolders(File root) {
        ArrayList<String> out = new ArrayList<>(); folderInto(root, root, 0, out, "");
        File extra = ExtraPacks.root(root);
        if (extra != null) { out.add(ExtraPacks.FOLDER); folderInto(extra, extra, 0, out, ExtraPacks.FOLDER + "/"); }
        return Collections.unmodifiableList(out);
    }
    private static void folderInto(File root, File dir, int depth, List<String> out, String prefix) {
        if (depth >= 3) return; File[] files = dir.listFiles(File::isDirectory); if (files == null) return;
        for (File f : files) if (!new File(f, "pack.mcmeta").isFile()) { out.add(prefix + relativize(root, f)); folderInto(root, f, depth + 1, out, prefix); }
    }
    private static boolean isZip(File f) { String n=f.getName().toLowerCase(Locale.ROOT); return n.endsWith(".zip") || n.endsWith(".jar"); }
    private static String relativize(File root, File f) { return root.toPath().relativize(f.toPath()).toString().replace('\\','/'); }

    private static PackMetadata readMetadata(File f) {
        try (InputStream in = entry(f, "pack.mcmeta")) {
            if (in == null) return PackMetadata.MISSING;
            JsonObject pack = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("pack");
            if (pack == null) return PackMetadata.MISSING;
            String desc = pack.has("description") ? flatten(pack.get("description").toString()) : "";
            return new PackMetadata(desc, pack.has("pack_format") ? pack.get("pack_format").getAsInt() : -1);
        } catch (Exception e) { return PackMetadata.MISSING; }
    }
    private static BufferedImage readIcon(File f) {
        try (InputStream in = entry(f, "pack.png")) { return in == null ? null : ImageIO.read(in); }
        catch (Exception e) { return null; }
    }
    private static InputStream entry(File f, String name) throws IOException {
        if (f.isDirectory()) { File child = new File(f, name); return child.isFile() ? new FileInputStream(child) : null; }
        final ZipFile zip = new ZipFile(f); ZipEntry e = zip.getEntry(name); if (e == null) { zip.close(); return null; }
        InputStream raw = zip.getInputStream(e); return new FilterInputStream(raw) { @Override public void close() throws IOException { try { super.close(); } finally { zip.close(); } } };
    }
    private static String flatten(String json) { return json.replaceAll("[{}\\[\\]\\\"]", "").replace("text:", "").replace("\\n", " "); }

    private static long signature(File root) {
        File extra = ExtraPacks.root(root);
        return treeSignature(root, 0) * 31 + (extra == null ? 0 : treeSignature(extra, 0));
    }
    private static long treeSignature(File dir, int depth) {
        if (!dir.exists()) return 0; long s = dir.lastModified() ^ dir.length(); File[] fs = dir.listFiles(); if (fs == null) return s;
        for (File f : fs) { s = s * 31 + f.getName().hashCode(); s ^= f.lastModified() + f.length(); if (f.isDirectory() && depth < 3 && !new File(f,"pack.mcmeta").isFile()) s ^= treeSignature(f, depth + 1); }
        return s;
    }
    @Override public void close() { workers.shutdownNow(); }
}
