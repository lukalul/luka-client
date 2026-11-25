package dev.luka.launcher;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import javax.imageio.ImageIO;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.SwingUtilities;

/**
 * Test: the replay editor with a stand-in launcher, never shown on screen (drawn into PNGs). Builds an edit of two
 * clips from the newest recording in the given game folder, and with "render" runs it through a script that starts
 * the hidden-desktop test game with the render job.
 *   java -cp <launcher classes>;<this> dev.luka.launcher.EditorPreview <gameDir> <png prefix> [render <script>]
 */
public final class EditorPreview {
  public static void main(String[] args) throws Exception {
    File gameDir = new File(args[0]);
    String prefix = args[1];
    boolean doRender = args.length > 2 && "render".equals(args[2]);
    String script = args.length > 3 ? args[3] : null;
    Font regular = Font.createFont(Font.TRUETYPE_FONT, new File("res/Roboto-Regular.ttf"));
    Font bold = Font.createFont(Font.TRUETYPE_FONT, new File("res/Roboto-Bold.ttf"));
    ReplayEditor.Host host = new ReplayEditor.Host() {
      public File gameDir() { return gameDir; }
      public GameVersion gameVersion() { return GameVersion.V1_8_9; }
      public Font font(boolean b, float size) { return (b ? bold : regular).deriveFont(size); }
      public List<Image> getIconImages() { return new ArrayList<>(); }
      public void stopRender() { }
      public boolean startRenderJob(File job, boolean hidden, BiConsumer<Integer, String> onExit) {
        new Thread(() -> {
          int code = -1;
          try {
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script, "-Job", job.getAbsolutePath())
                .inheritIO().start();
            code = p.waitFor();
          } catch (Exception e) {
            e.printStackTrace();
          }
          final int c = code;
          SwingUtilities.invokeLater(() -> onExit.accept(c, null));
        }).start();
        return true;
      }
    };
    ReplayEditor[] ed = new ReplayEditor[1];
    SwingUtilities.invokeAndWait(() -> {
      ed[0] = new ReplayEditor(host);
      ed[0].setSize(1320, 820);
      ed[0].addNotify();
      ed[0].validate();
    });
    ReplayEditor e = ed[0];
    // the library reads recording lengths on a thread
    Thread.sleep(4000);
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
        set(clips.get(0), 2.5, 5.0);
        set(clips.get(1), 10.0, 13.0);
        Method select = ReplayEditor.class.getDeclaredMethod("select", int.class);
        select.setAccessible(true);
        select.invoke(e, 0);
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
    shot(e, prefix + "-edit.png");
    System.out.println("status: " + ((String) field(e, "statusText")));
    if (args.length > 2 && "previews".equals(args[2])) {
      SwingUtilities.invokeAndWait(() -> {
        try {
          Method m = ReplayEditor.class.getDeclaredMethod("makePreviews");
          m.setAccessible(true);
          m.invoke(e);
        } catch (Exception x) {
          throw new RuntimeException(x);
        }
      });
      waitForJob(e, prefix, "Making previews: clip 1");
      Thread.sleep(3000); // the pictures load on a thread when first painted
      shot(e, prefix + "-loading.png");
      Thread.sleep(2500);
      // playhead 1.0 s into the edit = 3.5 s into the recording: the chest is open there
      Method seek = ReplayEditor.class.getDeclaredMethod("seek", double.class);
      seek.setAccessible(true);
      SwingUtilities.invokeAndWait(() -> {
        try {
          seek.invoke(e, 1.0);
        } catch (Exception x) {
          throw new RuntimeException(x);
        }
      });
      shot(e, prefix + "-previews.png");
      System.out.println("status: " + ((String) field(e, "statusText")));
      @SuppressWarnings("unchecked") ReplayEditor.Recording rec0 = ((List<ReplayEditor.Clip>) field(e, "clips")).get(0).rec;
      System.out.println("preview pictures: " + (rec0.frames == null ? 0 : rec0.frames.length));
      // play from the start of the edit for 1.2 s
      SwingUtilities.invokeAndWait(() -> {
        try {
          seek.invoke(e, 0.0);
        } catch (Exception x) {
          throw new RuntimeException(x);
        }
      });
      call(e, "togglePlay");
      Thread.sleep(1200);
      Field pt = ReplayEditor.class.getDeclaredField("playTime");
      pt.setAccessible(true);
      shot(e, prefix + "-playing.png");
      System.out.printf("playing: playhead %.2f s into the edit%n", pt.getDouble(e));
      call(e, "togglePlay");
      double at = pt.getDouble(e);
      call(e, "splitSelected");
      List<?> clips = (List<?>) field(e, "clips");
      StringBuilder sb = new StringBuilder();
      for (Object c : clips) sb.append(String.format("[%.2f-%.2f] ", ((ReplayEditor.Clip) c).start, ((ReplayEditor.Clip) c).end));
      System.out.printf("split at the playhead (%.2f s into the edit): %s%n", at, sb);
      shot(e, prefix + "-split.png");
      System.out.println("status: " + ((String) field(e, "statusText")));
      System.exit(0);
    }
    if (!doRender) {
      System.exit(0);
    }
    SwingUtilities.invokeAndWait(() -> {
      try {
        Method m = ReplayEditor.class.getDeclaredMethod("startRender");
        m.setAccessible(true);
        m.invoke(e);
      } catch (Exception x) {
        throw new RuntimeException(x);
      }
    });
    boolean midShot = false;
    for (int i = 0; i < 600; i++) {
      Thread.sleep(1000);
      String s = ((String) field(e, "statusText"));
      if (!midShot && s.startsWith("Rendering: clip 2")) {
        shot(e, prefix + "-rendering.png");
        midShot = true;
      }
      if (!(Boolean) field(e, "rendering")) break;
    }
    Thread.sleep(800);
    shot(e, prefix + "-done.png");
    System.out.println("status: " + ((String) field(e, "statusText")));
    System.exit(0);
  }

  private static void waitForJob(ReplayEditor e, String prefix, String midText) throws Exception {
    boolean midShot = false;
    for (int i = 0; i < 600; i++) {
      Thread.sleep(1000);
      String s = ((String) field(e, "statusText"));
      if (!midShot && s.startsWith(midText)) {
        shot(e, prefix + "-busy.png");
        midShot = true;
      }
      if (!(Boolean) field(e, "rendering")) break;
    }
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

  private static void set(Object clip, double start, double end) throws Exception {
    Field s = clip.getClass().getDeclaredField("start"), en = clip.getClass().getDeclaredField("end");
    s.setAccessible(true);
    en.setAccessible(true);
    s.setDouble(clip, start);
    en.setDouble(clip, end);
  }

  private static Object field(Object o, String name) throws Exception {
    Field f = ReplayEditor.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(o);
  }

  private static void shot(ReplayEditor e, String file) throws Exception {
    BufferedImage img = new BufferedImage(1320, 820, BufferedImage.TYPE_INT_RGB);
    SwingUtilities.invokeAndWait(() -> {
      e.getContentPane().setSize(1320, 820);
      e.getContentPane().validate();
      Graphics2D g = img.createGraphics();
      e.getContentPane().printAll(g);
      g.dispose();
    });
    ImageIO.write(img, "png", new File(file));
  }
}
