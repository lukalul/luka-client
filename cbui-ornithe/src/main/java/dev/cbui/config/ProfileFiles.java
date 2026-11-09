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

  public ProfileFiles(File directory) {
    this.directory = directory;
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
    ensureName(name);
    JsonFiles.writeAtomic(file(name), value);
  }

  public void create(String name, JsonObject value) throws IOException {
    ensureName(name);
    File target = file(name);
    if (target.exists()) throw new IOException("Profile already exists: " + name);
    save(name, value);
  }

  public void rename(String from, String to) throws IOException {
    ensureName(from);
    ensureName(to);
    File source = file(from), target = file(to);
    if (!source.isFile()) throw new IOException("Profile does not exist: " + from);
    if (target.exists()) throw new IOException("Profile already exists: " + to);
    JsonObject value = JsonFiles.read(source);
    JsonFiles.writeAtomic(target, value);
    if (!source.delete()) {
      target.delete();
      throw new IOException("Could not remove old profile: " + from);
    }
  }

  public void delete(String name) throws IOException {
    ensureName(name);
    File target = file(name);
    if (target.exists() && !target.delete())
      throw new IOException("Could not delete profile: " + name);
  }

  public File file(String name) {
    return new File(directory, name + ".json");
  }

  public static void ensureName(String name) {
    if (name == null
        || name.length() < 3
        || name.length() > 40
        || !name.matches("[a-zA-Z0-9 _\\-\\[\\]]+"))
      throw new IllegalArgumentException("Profile names must be 3-40 filename-safe characters");
  }
}
