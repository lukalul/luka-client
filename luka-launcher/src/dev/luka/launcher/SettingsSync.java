package dev.luka.launcher;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Privacy-filtered, non-destructive, newest-file-wins folder synchronization. */
final class SettingsSync {
  interface Guard { boolean allowed(); }
  static final class Result {
    int uploaded, downloaded;
    public String toString() { return uploaded + " files uploaded, " + downloaded + " downloaded"; }
  }
  private SettingsSync() {}

  static Result sync(File root, File chosenFolder) throws IOException {
    return sync(root, chosenFolder, () -> true);
  }

  static Result sync(File root, File chosenFolder, Guard guard) throws IOException {
    Result result = new Result();
    if (chosenFolder == null) return result;
    File game = new File(root, "game");
    String gamePath = game.getCanonicalPath(), chosenPath = chosenFolder.getCanonicalPath();
    if (chosenPath.equals(gamePath) || chosenPath.startsWith(gamePath + File.separator))
      throw new IOException("Sync folder cannot be the game folder or inside it");
    File remote = new File(chosenFolder, "luka-settings"); remote.mkdirs();
    syncFile(new File(root, "game/options.txt"), new File(remote, "game/options.txt"), result, guard);
    syncTrees(new File(root, "game/config"), new File(remote, "game/config"), result, guard);
    return result;
  }

  private static void syncTrees(File local, File remote, Result result, Guard guard) throws IOException {
    Set<String> names = new HashSet<String>();
    File[] a = local.listFiles(), b = remote.listFiles();
    if (a != null) for (File f : a) if (!SettingsBackup.excluded(f)) names.add(f.getName());
    if (b != null) for (File f : b) if (!SettingsBackup.excluded(f)) names.add(f.getName());
    for (String name : names) {
      File l = new File(local, name), r = new File(remote, name);
      if (l.isDirectory() || r.isDirectory()) syncTrees(l, r, result, guard); else syncFile(l, r, result, guard);
    }
  }

  private static void syncFile(File local, File remote, Result result, Guard guard) throws IOException {
    if (SettingsBackup.excluded(local) || SettingsBackup.excluded(remote)) return;
    if (!local.isFile() && !remote.isFile()) return;
    if (!guard.allowed()) throw new InterruptedIOException("Game started; sync stopped");
    if (!remote.isFile() || (local.isFile() && local.lastModified() > remote.lastModified())) {
      copy(local, remote); result.uploaded++;
    } else if (!local.isFile() || remote.lastModified() > local.lastModified()) {
      copy(remote, local); result.downloaded++;
    } else if (!same(local, remote)) {
      // Coarse filesystems can assign different edits the same timestamp. Resolve deterministically.
      copy(local, remote); result.uploaded++;
    }
  }

  private static boolean same(File a, File b) throws IOException {
    if (a.length() != b.length()) return false;
    try (InputStream x = new FileInputStream(a); InputStream y = new FileInputStream(b)) {
      byte[] xb = new byte[16384], yb = new byte[16384];
      for (;;) {
        int xn = x.read(xb), yn = y.read(yb);
        if (xn != yn) return false;
        if (xn < 0) return true;
        for (int i = 0; i < xn; i++) if (xb[i] != yb[i]) return false;
      }
    }
  }

  private static void copy(File from, File to) throws IOException {
    to.getParentFile().mkdirs();
    File part = new File(to.getPath() + ".sync-part");
    Files.copy(from.toPath(), part.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    try { Files.move(part.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
    catch (AtomicMoveNotSupportedException e) { Files.move(part.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING); }
    to.setLastModified(from.lastModified());
  }
}
