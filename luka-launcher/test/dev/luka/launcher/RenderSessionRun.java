package dev.luka.launcher;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test: the replay editor's render game (RenderSession, as LauncherFrame uses it) in a throwaway home whose game files
 * link to the test rig's (see rendersession18.ps1). Several jobs go to one game, one after another; then the session
 * quits it, as closing the editor does. Prints the time of each job and how the game ended.
 *   java -cp <launcher classes>;res dev.luka.launcher.RenderSessionRun <home> <queue dir> <job.properties>...
 */
public final class RenderSessionRun {
  public static void main(String[] args) throws Exception {
    File root = new File(args[0]);
    File queue = new File(args[1]);
    Settings settings = new Settings(new File(root, "launcher.properties"));
    final CountDownLatch ended = new CountDownLatch(1);
    RenderSession session = new RenderSession(new RenderSession.Env() {
      @Override public GameRunner newRunner() {
        GameRunner.Identity id = new GameRunner.Identity();
        id.name = "jobtest";
        id.uuid = Accounts.offlineUuid(id.name);
        GameRunner r = new GameRunner(root, settings, id, line -> {
          if (line.contains("[server]") || line.contains("Headless") || line.contains("hidden desktop")) System.out.println("game> " + line);
        }, (status, f) -> { });
        r.addJvmArg("-Dluka.api=http://127.0.0.1:1");
        return r;
      }

      @Override public void prepareGame() {
        // the rig's game files are in place
      }

      @Override public void log(String line) {
        System.out.println("session> " + line);
      }

      @Override public void ended() {
        System.out.println("session> ended");
        ended.countDown();
      }

      @Override public void later(Runnable r) {
        r.run();
      }
    }, () -> settings);
    long t0 = System.currentTimeMillis();
    for (int i = 2; i < args.length; i++) {
      final CountDownLatch done = new CountDownLatch(1);
      final int[] code = {Integer.MIN_VALUE};
      final String[] error = {null};
      long tj = System.currentTimeMillis();
      boolean started = session.start(new File(args[i]), queue, !"false".equals(System.getProperty("sessionHidden")), (c, e) -> {
        code[0] = c;
        error[0] = e;
        done.countDown();
      });
      if (!started) {
        System.out.println("job " + (i - 1) + ": refused");
        continue;
      }
      boolean finished = done.await(Integer.getInteger("jobTimeout", 300), TimeUnit.SECONDS);
      System.out.println("job " + (i - 1) + ": " + (finished ? "code " + code[0] + (error[0] != null ? " (" + error[0] + ")" : "") : "TIMEOUT")
          + ", " + (System.currentTimeMillis() - tj) / 1000.0 + " s, game was ready before: " + (i > 2));
      Thread.sleep(2500); // a shown game quits after its job: give it the time
      System.out.println("  ready now: " + session.ready() + ", idle: " + session.idle() + ", game still running: " + session.alive());
    }
    if (Boolean.getBoolean("playAfter")) {
      // PLAY while the render game waits (LauncherFrame.play): the player's game may start only once it is gone
      final CountDownLatch played = new CountDownLatch(1);
      long tp = System.currentTimeMillis();
      System.out.println("PLAY pressed: render game idle " + session.idle());
      session.endThen(() -> {
        System.out.printf("PLAY continues after %.1f s; render game still running: %s%n", (System.currentTimeMillis() - tp) / 1000.0, session.alive());
        played.countDown();
      });
      played.await(60, TimeUnit.SECONDS);
      System.out.println("session ended: " + ended.await(5, TimeUnit.SECONDS));
      System.exit(0);
    }
    if (Boolean.getBoolean("exitEarly")) {
      System.out.println("launcher exits without closing the editor (the shutdown hook must end the render game)");
      System.exit(0);
    }
    System.out.println("quitting (as when the editor closes)");
    long tq = System.currentTimeMillis();
    session.quit();
    boolean gone = ended.await(60, TimeUnit.SECONDS);
    System.out.println("session ended: " + gone + " after " + (System.currentTimeMillis() - tq) / 1000.0 + " s; total " + (System.currentTimeMillis() - t0) / 1000.0 + " s");
    System.exit(0);
  }
}
