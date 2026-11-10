package dev.luka.launcher;

import java.io.File;

/**
 * The Minecraft versions luka runs on. 1.8.9 keeps its files where they always were, so existing installs are left as
 * they are; any other version gets a folder of its own under the launcher folder (libraries, natives, game folder,
 * renderers, install stamp). Java, the settings, the accounts, the update list and the asset objects are shared.
 */
final class GameVersion {
  static final GameVersion V1_8_9 = new GameVersion("1.8.9", "1.8",
      "https://piston-meta.mojang.com/v1/packages/f6ad102bcaa53b1a58358f16e376d548d44933ec/1.8.json", "classpath.txt", "jvmargs.txt");
  static final GameVersion V1_7_10 = new GameVersion("1.7.10", "1.7.10",
      "https://launchermeta.mojang.com/v1/packages/1863782e33ce7b584fc45b037325a1964e095d3e/1.7.10.json",
      "classpath-1.7.10.txt", "jvmargs-1.7.10.txt");

  final String id;
  /** Mojang's asset index for this version, and where it is downloaded when the install step has not done so. */
  final String assetIndex, assetIndexUrl;
  /** The embedded class path and Java flags this version starts with. */
  final String classpathResource, jvmargsResource;

  private GameVersion(String id, String assetIndex, String assetIndexUrl, String classpathResource, String jvmargsResource) {
    this.id = id;
    this.assetIndex = assetIndex;
    this.assetIndexUrl = assetIndexUrl;
    this.classpathResource = classpathResource;
    this.jvmargsResource = jvmargsResource;
  }

  static GameVersion of(String id) {
    return V1_7_10.id.equals(id) ? V1_7_10 : V1_8_9;
  }

  /** 1.8.9 is the version the update list's own mod lists are for; the others are listed under "versions". */
  boolean primary() {
    return this == V1_8_9;
  }

  /** Where this version's own files live. */
  File home(File root) {
    return primary() ? root : new File(root, id);
  }

  /** The vanilla client jar, relative to the libraries folder. */
  String clientPath() {
    return "com/mojang/minecraft/" + id + "/minecraft-" + id + "-client.jar";
  }

  @Override
  public String toString() {
    return id;
  }
}
