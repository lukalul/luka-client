package dev.luka.launcher;

import java.io.File;

/**
 * Test: installs luka into a home folder the way the launcher's PLAY does (Installer.install, then syncMods), without
 * the launcher window or its self-update. For checking luka on Linux (WSL) and other test homes.
 *   java -cp <launcher classes>;res dev.luka.launcher.LinuxInstall <home> <1.8.9|1.7.10> <normal|vulkan>
 */
public final class LinuxInstall {
  public static void main(String[] args) throws Exception {
    File root = new File(args[0]);
    root.mkdirs();
    Settings settings = new Settings(new File(root, "launcher.properties"));
    settings.set("mcVersion", args[1]);
    settings.set("renderer", args[2]);
    settings.set("nvOptimize", "false");
    settings.save();
    GameVersion version = settings.version();
    final String[] last = {""};
    Installer.Progress progress = (status, fraction) -> {
      if (!status.equals(last[0])) {
        last[0] = status;
        System.out.println("install> " + status);
      }
    };
    Installer.retireMods(root, version);
    if (!Installer.isInstalled(root, version)) Installer.install(root, version, progress);
    else Installer.syncMods(root, version, false, progress);
    System.out.println("installed " + version.id + " into " + version.home(root) + ": " + Installer.isInstalled(root, version));
    System.exit(0);
  }
}
