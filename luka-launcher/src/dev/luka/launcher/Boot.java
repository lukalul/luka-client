package dev.luka.launcher;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Entry point of Luka.exe. It lives in its own tiny jar so the real launcher jar is not held open by the exe's class path: a launcher update
 * staged as luka-launcher.jar.new is moved into place here, before the launcher is loaded, and the launcher then starts as usual.
 */
public final class Boot {
  private Boot() {}

  public static void main(String[] args) throws Exception {
    File dir = new File(Boot.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
    File jar = new File(dir, "luka-launcher.jar");
    File staged = new File(dir, "luka-launcher.jar.new");
    if (staged.isFile()) {
      // the launcher that staged this update may still be closing and holding the jar open
      for (int attempt = 0; attempt < 40 && staged.isFile(); attempt++) {
        try { Files.move(staged.toPath(), jar.toPath(), StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException stillOpen) { Thread.sleep(250); }
      }
    }
    if (!jar.isFile()) throw new IOException("luka-launcher.jar is missing next to Luka.exe");
    URLClassLoader loader = new URLClassLoader(new URL[] {jar.toURI().toURL()}, Boot.class.getClassLoader());
    Thread.currentThread().setContextClassLoader(loader);
    loader.loadClass("dev.luka.launcher.Main").getMethod("main", String[].class).invoke(null, (Object) args);
  }
}
