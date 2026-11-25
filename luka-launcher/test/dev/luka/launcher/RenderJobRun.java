package dev.luka.launcher;

import java.io.File;

/**
 * Test: the launcher's own game start (GameRunner.play, as LauncherFrame.startRenderJob uses it) with a render job and
 * the hidden-desktop helper, in a throwaway home whose game files link to the test rig's (see launcherjob18.ps1).
 * Run with Java 25 (the runtime check takes this Java when the home has none).
 *   java -cp <launcher classes>;res dev.luka.launcher.RenderJobRun <home> <job.properties>
 */
public final class RenderJobRun {
  public static void main(String[] args) throws Exception {
    File root = new File(args[0]);
    Settings settings = new Settings(new File(root, "launcher.properties"));
    GameRunner.Identity id = new GameRunner.Identity();
    id.name = "jobtest";
    id.uuid = Accounts.offlineUuid(id.name);
    GameRunner runner = new GameRunner(root, settings, id, line -> {
      if (line.contains("[job]") || line.startsWith("Command:") && false || line.contains("hidden desktop")) System.out.println("game> " + line);
    }, (status, f) -> System.out.println("status> " + status));
    runner.addJvmArg("-Dsubframe.renderjob=" + new File(args[1]).getAbsolutePath());
    runner.runHidden();
    int code = runner.play();
    System.out.println("exit " + code + "; helper unpacked: " + new File(root, "luka-hidden.exe").isFile());
  }
}
