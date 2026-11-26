package dev.luka.launcher;

import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import java.awt.Font;
import java.awt.Image;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Test: the real replay editor (never shown on screen) with a stand-in launcher that hands its jobs to a RenderSession,
 * as LauncherFrame does. Makes previews, renders the edit twice, then closes the editor; the render game must start
 * once, take all three jobs and quit when the editor closes. Throwaway home as in RenderSessionRun.
 *   java -cp <launcher classes>;res dev.luka.launcher.EditorSessionRun <home>
 */
public final class EditorSessionRun {
  public static void main(String[] args) throws Exception {
    File root = new File(args[0]);
    Settings settings = new Settings(new File(root, "launcher.properties"));
    File gameDir = new File(root, "game");
    Font regular = Font.createFont(Font.TRUETYPE_FONT, new File("res/Roboto-Regular.ttf"));
    Font bold = Font.createFont(Font.TRUETYPE_FONT, new File("res/Roboto-Bold.ttf"));
    final CountDownLatch ended = new CountDownLatch(1);
    final int[] gamesStarted = {0};
    RenderSession session = new RenderSession(new RenderSession.Env() {
      @Override public GameRunner newRunner() {
        gamesStarted[0]++;
        GameRunner.Identity id = new GameRunner.Identity();
        id.name = "jobtest";
        id.uuid = Accounts.offlineUuid(id.name);
        GameRunner r = new GameRunner(root, settings, id, line -> {
          if (line.contains("[server]")) System.out.println("game> " + line.replaceAll("^.*\\[server\\]", "[server]"));
        }, (status, f) -> { });
        r.addJvmArg("-Dluka.api=http://127.0.0.1:1");
        return r;
      }

      @Override public void prepareGame() { }

      @Override public void log(String line) {
        System.out.println("session> " + line);
      }

      @Override public void ended() {
        System.out.println("session> ended");
        ended.countDown();
      }
    }, () -> settings);
    ReplayEditor.Host host = new ReplayEditor.Host() {
      public File gameDir() { return gameDir; }
      public GameVersion gameVersion() { return GameVersion.V1_8_9; }
      public Font font(boolean b, float size) { return (b ? bold : regular).deriveFont(size); }
      public List<Image> getIconImages() { return new ArrayList<>(); }
      public void stopRender() { session.stop(); }
      public boolean startRenderJob(File job, boolean hidden, BiConsumer<Integer, String> onExit) {
        return session.start(job, new File(job.getParentFile(), "server"), hidden, onExit);
      }
      public void endRenderSession() { if (session.alive()) session.quit(); }
      public boolean renderGameReady() { return session.ready(); }
      public void warmRenderGame(File queue, boolean hidden) { session.warm(queue, hidden); }
    };
    ReplayEditor[] ed = new ReplayEditor[1];
    SwingUtilities.invokeAndWait(() -> {
      ed[0] = new ReplayEditor(host);
      ed[0].setSize(1320, 820);
      ed[0].addNotify();
      ed[0].validate();
    });
    ReplayEditor e = ed[0];
    Thread.sleep(4000); // the library reads recording lengths on a thread (the render game starts meanwhile)
    for (int i = 0; i < 120 && !session.ready(); i++) Thread.sleep(250);
    System.out.println("render game ready after the editor opened: " + session.ready());
    SwingUtilities.invokeAndWait(() -> {
      try {
        @SuppressWarnings("unchecked") JList<ReplayEditor.Recording> list = (JList<ReplayEditor.Recording>) field(e, "libraryList");
        list.setSelectedIndex(0);
        List<?> clips = (List<?>) field(e, "clips");
        clips.clear();
        Method add = ReplayEditor.class.getDeclaredMethod("addSelectedRecording");
        add.setAccessible(true);
        add.invoke(e);
        add.invoke(e);
        set(clips.get(0), 1.0, 3.0);
        set(clips.get(1), 4.0, 6.0);
        Method changed = ReplayEditor.class.getDeclaredMethod("changed");
        changed.setAccessible(true);
        changed.invoke(e);
        Object fpsC = field(e, "fps"), sizeC = field(e, "size");
        Method choiceSet = fpsC.getClass().getDeclaredMethod("set", String.class);
        choiceSet.setAccessible(true);
        choiceSet.invoke(fpsC, "30");
        choiceSet.invoke(sizeC, "1280x720");
        ((JCheckBox) field(e, "blur")).setSelected(false);
        e.validate();
      } catch (Exception x) {
        throw new RuntimeException(x);
      }
    });
    // automatic previews: the editor starts them by itself once the game is up; RENDER pressed meanwhile waits for them
    long ta = System.currentTimeMillis();
    for (int i = 0; i < 80 && !(Boolean) field(e, "rendering"); i++) Thread.sleep(250);
    System.out.printf("automatic previews started by themselves: %s after %.1f s, status '%s'%n", field(e, "rendering"),
        (System.currentTimeMillis() - ta) / 1000.0, field(e, "statusText"));
    call(e, "startRender");
    System.out.println("RENDER pressed during them: '" + field(e, "statusText") + "', renderAfter=" + field(e, "renderAfter"));
    for (int i = 0; i < 600; i++) {
      Thread.sleep(250);
      if (!(Boolean) field(e, "rendering") && !(Boolean) field(e, "renderAfter")) break;
    }
    System.out.printf("previews + queued render: %.1f s, last status '%s'%n", (System.currentTimeMillis() - ta) / 1000.0, field(e, "statusText"));
    String[] steps = {"startRender"};
    for (String step : steps) {
      long t = System.currentTimeMillis();
      call(e, step);
      String firstStatus = (String) field(e, "statusText");
      for (int i = 0; i < 600; i++) {
        Thread.sleep(250);
        if (!(Boolean) field(e, "rendering")) break;
      }
      System.out.printf("%s: %.1f s, first status '%s', last status '%s', render game ready: %s%n", step,
          (System.currentTimeMillis() - t) / 1000.0, firstStatus, field(e, "statusText"), session.ready());
    }
    System.out.println("games started: " + gamesStarted[0]);
    long tq = System.currentTimeMillis();
    SwingUtilities.invokeAndWait(e::dispose);
    boolean gone = ended.await(60, TimeUnit.SECONDS);
    System.out.printf("editor closed: render game ended %s after %.1f s%n", gone, (System.currentTimeMillis() - tq) / 1000.0);
    System.exit(0);
  }

  private static void call(ReplayEditor e, String name) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        Method m = ReplayEditor.class.getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(e);
      } catch (Exception x) {
        throw new RuntimeException(x);
      }
    });
  }

  private static Object field(Object o, String name) throws Exception {
    Field f = o.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(o);
  }

  private static void set(Object clip, double start, double end) throws Exception {
    Field s = clip.getClass().getDeclaredField("start"), en = clip.getClass().getDeclaredField("end");
    s.setAccessible(true);
    en.setAccessible(true);
    s.setDouble(clip, start);
    en.setDouble(clip, end);
  }
}
