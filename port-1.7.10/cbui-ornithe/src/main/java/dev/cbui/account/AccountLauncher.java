package dev.cbui.account;

import java.io.File;
import net.minecraft.client.Minecraft;

/**
 * Opens the luka launcher's account window ({@code --accounts}) instead of drawing account screens in the game: the launcher
 * has the sign-in (Microsoft code or offline name) and the account list. When that window closes, the account it saved as
 * selected becomes the game's account. The two share one accounts.properties file.
 */
public final class AccountLauncher {

  /** What the main menu shows while this is going on ("" when idle). */
  public static volatile String status = "";
  private static volatile boolean open;

  private AccountLauncher() {}

  /** The launcher jar: the one this game was started from, else the default install. Null when there is none. */
  public static File jar() {
    // the launcher names itself when it starts the game (any version, any system)
    String given = System.getProperty("luka.launcher");
    if (given != null && new File(given).isFile()) return new File(given);
    File gameDir = Minecraft.getMinecraft().mcDataDir.getAbsoluteFile();
    // started by the launcher: game folder is <dist>/luka/game
    File fromLauncher = new File(gameDir.getParentFile() == null || gameDir.getParentFile().getParentFile() == null
        ? gameDir : gameDir.getParentFile().getParentFile(), "luka-launcher.jar");
    if (fromLauncher.isFile()) return fromLauncher;
    String override = System.getProperty("luka.launcher");
    if (override == null) return null;
    File custom = new File(override);
    return custom.isFile() ? custom : null;
  }

  public static boolean available() {
    return jar() != null;
  }

  public static boolean busy() {
    return open;
  }

  /** Opens the launcher's account window; false when there is no launcher to open. */
  public static boolean open() {
    final File jar = jar();
    if (jar == null || open) return jar != null;
    open = true;
    status = "Waiting for the launcher...";
    Thread thread = new Thread(() -> {
      try {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        File java = new File(System.getProperty("java.home"), "bin" + File.separator + (windows ? "javaw.exe" : "java"));
        String exe = java.isFile() ? java.getPath() : (windows ? "javaw" : "java");
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(exe);
        command.add("-Djava.awt.headless=false");
        // the launcher's own folder, so it opens the same accounts and settings it started the game with
        String home = System.getProperty("luka.home");
        if (home != null && !home.isEmpty()) command.add("-Dluka.home=" + home);
        command.add("-jar");
        command.add(jar.getPath());
        command.add("--accounts");
        Process process = new ProcessBuilder(command).directory(jar.getParentFile()).redirectErrorStream(true).start();
        process.getInputStream().close();
        process.waitFor();
        applyChoice();
      } catch (Throwable error) {
        status = "Could not open the launcher: " + error.getMessage();
        sleep(4000);
        status = "";
      } finally {
        open = false;
      }
    }, "luka-account-launcher");
    thread.setDaemon(true);
    thread.start();
    return true;
  }

  private static void applyChoice() {
    AccountStore store = new AccountStore(true);
    AccountStore.Account account = store.selected();
    if (account == null) {
      status = "";
      return;
    }
    if (account.microsoft()) {
      status = "Signing in as " + account.name + "...";
      status = store.switchToMicrosoft(account);
    } else {
      status = store.switchTo(account) ? "Playing as " + account.name : "Could not switch the account";
    }
    sleep(2500);
    status = "";
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ignored) {
      // done waiting
    }
  }
}
