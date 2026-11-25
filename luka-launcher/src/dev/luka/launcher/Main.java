package dev.luka.launcher;

import java.io.File;
import java.net.URISyntaxException;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Starts the luka launcher. The game files live in the "luka" folder next to this jar (or -Dluka.home=...). */
public final class Main {
  private Main() {}

  public static void main(String[] args) throws Exception {
    // Linux: restart once with the desktop's cursor size and theme if the session doesn't give them to X11 programs
    if (LinuxCursor.relaunchIfNeeded(args)) return;
    final File root = home();
    try {
      UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
    } catch (Exception ignored) {
      // the default look is fine
    }
    final boolean accountMode = args.length > 0 && args[0].equals("--accounts");
    SwingUtilities.invokeLater(() -> new LauncherFrame(root, accountMode).setVisible(true));
  }

  /** The jar this launcher runs from; null when it runs from a classes folder. */
  static File launcherJar() {
    try {
      File jar = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      return jar.isFile() ? jar.getAbsoluteFile() : null;
    } catch (Exception e) {
      return null;
    }
  }

  private static File home() throws URISyntaxException {
    String override = System.getProperty("luka.home");
    if (override != null) return new File(override);
    File jar = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    File dir = jar.isFile() ? jar.getParentFile() : jar;
    return new File(dir, "luka");
  }
}
