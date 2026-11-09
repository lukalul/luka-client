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
        File java = new File(System.getProperty("java.home"), "bin" + File.separator + "javaw.exe");
        String exe = java.isFile() ? java.getPath() : "javaw";
        Process process = new ProcessBuilder(exe, "-Djava.awt.headless=false", "-jar", jar.getPath(), "--accounts")
            .directory(jar.getParentFile()).redirectErrorStream(true).start();
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
