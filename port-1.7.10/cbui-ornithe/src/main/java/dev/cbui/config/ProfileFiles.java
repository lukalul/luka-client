package dev.cbui.config;

import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Local-only JSON profile file operations, kept independent of Minecraft for direct checks. */
public final class ProfileFiles {
  private final File directory;
  /** What one file is called in messages ("Profile", "Preset"). */
  private final String kind;

  public ProfileFiles(File directory) {
    this(directory, "Profile");
  }

  public ProfileFiles(File directory, String kind) {
    this.directory = directory;
    this.kind = kind;
  }

  public File directory() {
    return directory;
  }

  /** The saved name that matches {@code name} ignoring case, or null. Windows file names ignore case, so two names that differ only in case are one file. */
  public String existing(String name) {
    for (String other : list()) if (other.equalsIgnoreCase(name)) return other;
    return null;
  }

  public List<String> list() {
    File[] files =
        directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
    if (files == null) return Collections.emptyList();
    Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
    List<String> names = new ArrayList<>();
    for (File file : files) names.add(file.getName().substring(0, file.getName().length() - 5));
    return names;
  }

  public JsonObject load(String name) throws IOException {
    return JsonFiles.read(file(name));
  }

  public void save(String name, JsonObject value) throws IOException {
    ensureName(name, kind);
    if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create " + directory);
    JsonFiles.writeAtomic(file(name), value);
  }

  public void create(String name, JsonObject value) throws IOException {
    ensureName(name, kind);
    File target = file(name);
    if (target.exists() || existing(name) != null) throw new IOException(kind + " already exists: " + name);
    save(name, value);
  }

  public void rename(String from, String to) throws IOException {
    ensureName(from, kind);
    ensureName(to, kind);
    if (from.equals(to)) return;
    File source = file(from), target = file(to);
    if (!source.isFile()) throw new IOException(kind + " does not exist: " + from);
    String clash = existing(to);
    if (clash != null && !clash.equalsIgnoreCase(from)) throw new IOException(kind + " already exists: " + to);
    if (from.equalsIgnoreCase(to)) {
      // only the case changes: Windows sees one file, so go through a temporary name
      File step = new File(directory, from + ".renaming");
      if (!source.renameTo(step) || !step.renameTo(target)) throw new IOException("Could not rename " + kind.toLowerCase(Locale.ROOT) + ": " + from);
      return;
    }
    JsonObject value = JsonFiles.read(source);
    JsonFiles.writeAtomic(target, value);
    if (!source.delete()) {
      target.delete();
      throw new IOException("Could not remove old " + kind.toLowerCase(Locale.ROOT) + ": " + from);
    }
  }

  public void delete(String name) throws IOException {
    ensureName(name, kind);
    File target = file(name);
    if (target.exists() && !target.delete())
      throw new IOException("Could not delete " + kind.toLowerCase(Locale.ROOT) + ": " + name);
  }

  public File file(String name) {
    return new File(directory, name + ".json");
  }

  public static void ensureName(String name) {
    ensureName(name, "Profile");
  }

  /** 3-40 letters, digits, spaces, _ - [ ], no space at either end, and not a name Windows reserves (CON, NUL, COM1...). */
  public static void ensureName(String name, String kind) {
    if (name == null
        || name.length() < 3
        || name.length() > 40
        || !name.matches("[a-zA-Z0-9 _\\-\\[\\]]+")
        || !name.trim().equals(name))
      throw new IllegalArgumentException(kind + " names must be 3-40 filename-safe characters");
    if (name.toUpperCase(Locale.ROOT).matches("(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])"))
      throw new IllegalArgumentException("Windows does not allow the name " + name);
  }
}
