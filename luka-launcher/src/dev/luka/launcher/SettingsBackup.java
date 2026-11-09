package dev.luka.launcher;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

/** Creates and restores privacy-filtered backups of player settings. */
final class SettingsBackup {
  private SettingsBackup() {}

  static synchronized File create(File root) throws IOException {
    File dir = new File(root.getParentFile(), "backups");
    dir.mkdirs();
    File zip = new File(dir, "luka-settings-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".zip");
    if (zip.exists()) zip = new File(dir, zip.getName().replace(".zip", "-" + System.currentTimeMillis() + ".zip"));
    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
      addFile(out, new File(root, "game/options.txt"), "game/options.txt");
      addFile(out, new File(root, "game/servers.dat"), "game/servers.dat");
      addTree(out, new File(root, "game/config"), "game/config/");
      addSanitizedProperties(out, new File(root, "launcher.properties"));
    }
    prune(dir);
    return zip;
  }

  static List<File> list(File root) {
    File[] files = new File(root.getParentFile(), "backups").listFiles((d, n) -> n.startsWith("luka-settings-") && n.endsWith(".zip"));
    List<File> result = files == null ? new ArrayList<File>() : new ArrayList<File>(Arrays.asList(files));
    Collections.sort(result, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
    return result;
  }

  static void restore(File root, File backup) throws IOException {
    String base = root.getCanonicalPath() + File.separator;
    try (ZipInputStream in = new ZipInputStream(new FileInputStream(backup))) {
      ZipEntry entry;
      byte[] buffer = new byte[16384];
      while ((entry = in.getNextEntry()) != null) {
        if (unsafeEntryName(entry.getName())) throw new IOException("Unsafe backup entry: " + entry.getName());
        if (entry.isDirectory() || !allowedEntry(entry.getName())) continue;
        File target = new File(root, entry.getName());
        if (!target.getCanonicalPath().startsWith(base)) throw new IOException("Unsafe backup entry: " + entry.getName());
        target.getParentFile().mkdirs();
        File part = new File(target.getPath() + ".restore-part");
        try (OutputStream out = new FileOutputStream(part)) {
          int n; while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        }
        move(part, target);
      }
    }
  }

  static boolean excluded(File file) {
    String n = file.getName().toLowerCase(Locale.ROOT);
    return n.equals("accounts.properties") || n.contains("token") || n.contains("secret");
  }

  private static boolean allowedEntry(String n) {
    if (unsafeEntryName(n)) return false;
    return n.equals("game/options.txt") || n.equals("game/servers.dat") || n.equals("launcher.properties")
        || n.startsWith("game/config/");
  }

  private static boolean unsafeEntryName(String n) {
    if (n.indexOf('\\') >= 0 || n.startsWith("/") || n.matches("^[A-Za-z]:.*")) return true;
    for (String part : n.split("/")) if (part.isEmpty() || part.equals(".") || part.equals("..")) return true;
    return false;
  }

  private static void addTree(ZipOutputStream out, File dir, String prefix) throws IOException {
    File[] files = dir.listFiles(); if (files == null) return;
    for (File f : files) {
      if (excluded(f)) continue;
      if (f.isDirectory()) addTree(out, f, prefix + f.getName() + "/");
      else addFile(out, f, prefix + f.getName());
    }
  }

  private static void addFile(ZipOutputStream out, File file, String name) throws IOException {
    if (!file.isFile() || excluded(file)) return;
    ZipEntry e = new ZipEntry(name.replace('\\', '/')); e.setTime(file.lastModified()); out.putNextEntry(e);
    Files.copy(file.toPath(), out); out.closeEntry();
  }

  private static void addSanitizedProperties(ZipOutputStream out, File file) throws IOException {
    if (!file.isFile()) return;
    StringBuilder clean = new StringBuilder();
    for (String line : Files.readAllLines(file.toPath(), StandardCharsets.ISO_8859_1)) {
      String s = line.trim().toLowerCase(Locale.ROOT);
      if (s.startsWith("msrefresh") || s.startsWith("accesstoken")) continue;
      clean.append(line).append('\n');
    }
    ZipEntry e = new ZipEntry("launcher.properties"); e.setTime(file.lastModified()); out.putNextEntry(e);
    out.write(clean.toString().getBytes(StandardCharsets.ISO_8859_1)); out.closeEntry();
  }

  private static void prune(File dir) {
    File[] files = dir.listFiles((d, n) -> n.startsWith("luka-settings-") && n.endsWith(".zip"));
    if (files == null || files.length <= 10) return;
    Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
    for (int i = 10; i < files.length; i++) files[i].delete();
  }

  private static void move(File from, File to) throws IOException {
    try { Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
    catch (AtomicMoveNotSupportedException e) { Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING); }
  }
}
