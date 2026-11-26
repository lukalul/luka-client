package dev.luka.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.BiConsumer;

/**
 * The replay editor's render game: started with the editor's first preview or render, then kept running for the next
 * ones (SubFrame {@code -Dsubframe.renderserver}: it takes {@code *.job} files from the queue folder). Starting the game
 * is most of the wait for a short render; this way it happens once per editor session.
 *
 * <p>The game quits when the editor closes ({@link #quit}, a quit file), when PLAY needs the game, or when this
 * launcher stops touching the alive file every 2 s (SubFrame quits after 30 s without it): a launcher that crashed
 * never leaves a game behind. Vulkan runs headless (no window at all, Windows and Linux); otherwise the game is hidden
 * as before (a hidden desktop on Windows, a hidden or minimised window on Linux). A headless start that fails falls back to that once.
 */
final class RenderSession {
  /** What the session needs from the launcher (LauncherFrame, or a test). */
  interface Env {
    /** A game runner for the editor's game (identity, log, status, no Luka Online). */
    GameRunner newRunner();

    /** The same checks and downloads as PLAY. */
    void prepareGame() throws Exception;

    void log(String line);

    /** The render game has ended (the launcher may start another game). */
    void ended();

    /** Runs on the UI thread (the editor's callbacks). */
    default void later(Runnable r) {
      javax.swing.SwingUtilities.invokeLater(r);
    }
  }

  private final Env env;
  private final java.util.function.Supplier<Settings> settings;
  private final Object lock = new Object();
  private Thread thread;
  private String key;
  private File queue;
  private BiConsumer<Integer, String> pending;
  private String pendingJob;
  private int jobs;
  private volatile GameRunner runner;
  /** The running game was started hidden (or headless); a game with its window shown quits after its job. */
  private volatile boolean hiddenGame;
  private java.util.concurrent.ScheduledExecutorService beat;

  RenderSession(Env env, java.util.function.Supplier<Settings> settings) {
    this.env = env;
    this.settings = settings;
    // the launcher closing: the render game quits at once instead of after the alive file's timeout
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      if (alive()) quit();
    }, "luka-render-quit"));
  }

  /**
   * Hands the editor's job to the render game, starting it first when none runs (or one runs for other settings: the
   * renderer, the Minecraft version, hidden or not). onExit runs once on the UI thread: (0, null) when the game marked
   * the job done, otherwise the game's exit code and a message. False while another job is still running.
   */
  boolean start(File job, File queueDir, boolean hidden, BiConsumer<Integer, String> onExit) {
    String wanted = settings.get().version().id + "/" + settings.get().renderer() + "/" + hidden;
    synchronized (lock) {
      if (pending != null) return false;
      pending = onExit;
      boolean up = thread != null && thread.isAlive();
      if (up && wanted.equals(key)) {
        queue(job);
        return true;
      }
      final Thread old = up ? thread : null;
      if (old != null) quit();
      key = wanted;
      queue = queueDir;
      hiddenGame = hidden;
      thread = new Thread(() -> run(old, hidden, job), "luka-render");
      thread.start();
    }
    return true;
  }

  /** Starts the render game with no job yet (the editor opened); nothing when one already runs. */
  void warm(File queueDir, boolean hidden) {
    synchronized (lock) {
      if (thread != null && thread.isAlive() || !hidden) return; // never a shown game waiting at the main menu
      hiddenGame = true;
      key = settings.get().version().id + "/" + settings.get().renderer() + "/" + hidden;
      queue = queueDir;
      thread = new Thread(() -> run(null, hidden, null), "luka-render");
      thread.start();
    }
  }

  /** The render game runs (or is starting). */
  boolean alive() {
    synchronized (lock) {
      return thread != null && thread.isAlive();
    }
  }

  /** It runs and has no job: PLAY may end it. */
  boolean idle() {
    synchronized (lock) {
      return thread != null && thread.isAlive() && pending == null;
    }
  }

  /** It is up and waiting for a job (the next job starts at once). */
  boolean ready() {
    synchronized (lock) {
      return thread != null && thread.isAlive() && state().contains("state=idle");
    }
  }

  Thread thread() {
    synchronized (lock) {
      return thread;
    }
  }

  GameRunner runner() {
    return runner;
  }

  /** Asks the game to quit after its current job (the editor closed, or PLAY). */
  void quit() {
    File q = queue;
    if (q == null) return;
    try {
      Files.write(new File(q, "quit").toPath(), new byte[0]);
    } catch (IOException ignored) {
      // the alive file stops being touched: it quits by itself
    }
  }

  /**
   * PLAY while the render game waits: asks it to quit, waits for it (killing it after 15 s), then runs after (on the UI
   * thread, through Env.later). The launcher's own game starts only once the render game is gone.
   */
  void endThen(Runnable after) {
    quit();
    final Thread waitFor = thread();
    new Thread(() -> {
      try {
        if (waitFor != null) waitFor.join(15_000);
        if (waitFor != null && waitFor.isAlive()) {
          stop();
          waitFor.join(10_000);
        }
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
      env.later(after);
    }, "luka-render-end").start();
  }

  /** Ends the game now (the editor's stop during a render). */
  void stop() {
    GameRunner r = runner;
    if (r != null) r.stop();
  }

  private void run(Thread old, boolean hidden, File firstJob) {
    int code = -1;
    String error = null;
    try {
      if (old != null) {
        old.join(15_000);
        if (old.isAlive()) {
          GameRunner r = runner;
          if (r != null) r.stop();
          old.join(10_000);
        }
      }
      File q = queue;
      q.mkdirs();
      File[] leftovers = q.listFiles();
      if (leftovers != null) for (File f : leftovers) f.delete();
      touchAlive();
      startBeat();
      if (firstJob != null) {
        synchronized (lock) {
          queue(firstJob);
        }
      }
      env.prepareGame();
      // Vulkan: no window at all, on Windows and Linux (the renderer's library on both does headless since 1.6.5)
      boolean headless = hidden && (Platform.windows() || Platform.linux()) && "vulkan".equals(settings.get().renderer());
      for (int attempt = 0; attempt < 2; attempt++) {
        GameRunner r = env.newRunner();
        r.addJvmArg("-Dsubframe.renderserver=" + q.getAbsolutePath());
        if (headless) {
          java.awt.Dimension screen = screenSize();
          r.runHeadless(screen.width, screen.height);
        } else if (hidden) {
          r.runHidden();
        }
        runner = r;
        env.log("Render game: " + (headless ? "no window (headless)" : hidden ? "hidden" : "window shown") + ", jobs from " + q);
        long started = System.currentTimeMillis();
        code = r.play();
        String state = state();
        boolean everReady = state.contains("state=idle") || state.contains("state=busy") || state.contains("state=stopped");
        if (headless && !everReady && code != 0 && System.currentTimeMillis() - started < 120_000) {
          env.log("The headless render game did not start (code " + code + "); trying again with a hidden window");
          headless = false;
          File[] files = q.listFiles();
          if (files != null) {
            for (File f : files) {
              if (f.getName().endsWith(".running")) f.renameTo(new File(q, f.getName().replaceAll("\\.running$", ".job")));
            }
          }
          continue;
        }
        break;
      }
      if (code != 0) error = "The render game closed (code " + code + ") - see the log";
    } catch (Throwable t) {
      error = t.getMessage() == null ? t.toString() : t.getMessage();
      env.log("ERROR: " + t);
    } finally {
      boolean last;
      synchronized (lock) {
        runner = null;
        last = thread == Thread.currentThread();
        if (last) {
          thread = null;
          key = null;
          stopBeat();
        }
        // a job still waiting gets the game's end as its result
        finish(code == 0 ? -1 : code, error == null ? "The render game closed before the job finished" : error);
      }
      if (last) env.ended();
    }
  }

  /** Puts the job file into the queue (lock held). */
  private void queue(File job) {
    try {
      String name = String.format("%06d", ++jobs);
      File tmp = new File(queue, name + ".tmp");
      Files.copy(job.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
      Files.move(tmp.toPath(), new File(queue, name + ".job").toPath(), StandardCopyOption.ATOMIC_MOVE);
      pendingJob = name;
    } catch (IOException e) {
      finish(-1, "Could not hand the job to the render game: " + e.getMessage());
    }
  }

  /** Every 0.5 s: notices a finished job; every 2 s: tells the game the launcher is still here. */
  private void startBeat() {
    synchronized (lock) {
      if (beat != null) return;
      beat = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "luka-render-beat");
        t.setDaemon(true);
        return t;
      });
      beat.scheduleWithFixedDelay(() -> {
        File q = queue;
        if (q == null) return;
        if (System.currentTimeMillis() - new File(q, "alive").lastModified() > 2000) touchAlive();
        synchronized (lock) {
          String name = pendingJob;
          if (name != null && new File(q, name + ".done").isFile()) {
            finish(0, null);
            // a game with its window shown ("Hide the game window" unticked) is not kept on screen between jobs
            if (!hiddenGame) quit();
          }
        }
      }, 500, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
  }

  private void stopBeat() {
    if (beat != null) beat.shutdownNow();
    beat = null;
  }

  private void touchAlive() {
    try {
      Files.write(new File(queue, "alive").toPath(), Long.toString(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
    } catch (IOException ignored) {
      // tried again in 0.5 s
    }
  }

  private String state() {
    File q = queue;
    if (q == null) return "";
    try {
      return new String(Files.readAllBytes(new File(q, "server.properties").toPath()), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "";
    }
  }

  /** The editor's callback for the job in progress, once (lock held). */
  private void finish(int code, String error) {
    BiConsumer<Integer, String> done = pending;
    pending = null;
    pendingJob = null;
    if (done != null) env.later(() -> done.accept(code, error));
  }

  private static java.awt.Dimension screenSize() {
    try {
      java.awt.DisplayMode mode = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode();
      if (mode.getWidth() >= 640 && mode.getHeight() >= 480) return new java.awt.Dimension(mode.getWidth(), mode.getHeight());
    } catch (Throwable ignored) {
      // headless Java or no screen: the usual size
    }
    return new java.awt.Dimension(1920, 1080);
  }
}
