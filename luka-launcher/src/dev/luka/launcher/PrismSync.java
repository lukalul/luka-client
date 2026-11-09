package dev.luka.launcher;

import java.io.File;

/**
 * Runs before the game when luka is played from a Prism Launcher instance. It brings the instance's mods to what the
 * signed update list says, so Prism players get updates without importing again, and mods that luka may not
 * redistribute come straight from their authors' pages.
 */
public final class PrismSync {
  private PrismSync() {}

  public static void main(String[] args) {
    try {
      File instance = new File(args[0]);
      File game = new File(instance, ".minecraft");
      if (!game.isDirectory()) game = new File(instance, "minecraft");
      System.setProperty("luka.gameDir", game.getPath());
      final String[] last = {""};
      Installer.syncMods(new File(instance, "luka"), false, (text, fraction) -> {
        if (!text.equals(last[0])) System.out.println("[luka] " + text);
        last[0] = text;
      });
      System.out.println("[luka] Up to date");
    } catch (Throwable e) {
      System.out.println("[luka] Could not get the game files ready: " + e.getMessage());
      System.exit(1);
    }
  }
}
