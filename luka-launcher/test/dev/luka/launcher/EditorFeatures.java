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
import java.util.Properties;
import java.util.function.BiConsumer;
import javax.imageio.ImageIO;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;

/**
 * Test: kill markers, speed per clip, undo / redo and the look options of the replay editor, with a stand-in launcher,
 * never shown (drawn into a PNG). Uses the newest recording in the game folder (the look test scene: two kills).
 *   java -cp <launcher classes> dev.luka.launcher.EditorFeatures <gameDir> <png> [1.8.9|1.7.10]
 */
public final class EditorFeatures {
  private static ReplayEditor e;

  public static void main(String[] args) throws Exception {
    File gameDir = new File(args[0]);
    Font regular = Font.createFont(Font.TRUETYPE_FONT, new File("res/Roboto-Regular.ttf"));
    Font bold = Font.createFont(Font.TRUETYPE_FONT, new File("res/Roboto-Bold.ttf"));
    ReplayEditor.Host host = new ReplayEditor.Host() {
      public File gameDir() { return gameDir; }
      public GameVersion gameVersion() { return args.length > 2 && args[2].equals("1.7.10") ? GameVersion.V1_7_10 : GameVersion.V1_8_9; }
      public Font font(boolean b, float size) { return (b ? bold : regular).deriveFont(size); }
      public List<Image> getIconImages() { return new ArrayList<>(); }
      public void stopRender() { }
      public boolean startRenderJob(File job, boolean hidden, BiConsumer<Integer, String> onExit) { return false; }
    };
    SwingUtilities.invokeAndWait(() -> {
      e = new ReplayEditor(host);
      e.setSize(1320, 820);
      e.addNotify();
      e.validate();
    });
    Thread.sleep(3000);
    run(() -> {
      @SuppressWarnings("unchecked") javax.swing.JList<ReplayEditor.Recording> list = (javax.swing.JList<ReplayEditor.Recording>) get("libraryList");
      list.setSelectedIndex(0);
      ((List<?>) get("clips")).clear();
      call("addSelectedRecording");
    });
    ReplayEditor.Clip clip = clips().get(0);
    System.out.println("recording " + clip.rec.name() + ": " + clip.rec.kills.size() + " kills");
    for (ReplayEditor.Kill k : clip.rec.kills) System.out.printf("  kill %s at %.2f s%n", k.victim, k.t);
    // kill jumps
    run(() -> call("seek", 0.0));
    run(() -> call("jumpKill", 1));
    System.out.printf("K from 0: playhead %.2f%n", playTime());
    run(() -> call("jumpKill", 1));
    System.out.printf("K again: playhead %.2f%n", playTime());
    run(() -> call("jumpKill", -1));
    System.out.printf("SHIFT+K: playhead %.2f%n", playTime());
    // speed 0.5 through the toolbar chips' action, then undo / redo
    double before = total();
    run(() -> {
      Object speed = get("speed");
      setChoice(speed, "0.5");
      Runnable pick = (Runnable) field(speed.getClass(), "onPick").get(speed);
      pick.run();
    });
    System.out.printf("speed 0.5: clip %.2f s of recording lasts %.2f s; edit %.2f -> %.2f s%n", clip.length(), clips().get(0).dur(), before, total());
    run(() -> call("undo"));
    System.out.printf("undo: speed %.2f, edit %.2f s%n", clips().get(0).speed, total());
    run(() -> call("redo"));
    System.out.printf("redo: speed %.2f, edit %.2f s%n", clips().get(0).speed, total());
    run(() -> call("seek", 1.0));
    run(() -> call("splitSelected"));
    System.out.printf("split at 1.0 s of the edit: %d clips, first %.2f-%.2f (speed %.2f)%n", clips().size(), clips().get(0).start, clips().get(0).end, clips().get(0).speed);
    run(() -> call("undo"));
    System.out.printf("undo the split: %d clip(s)%n", clips().size());
    // look options -> job keys
    run(() -> {
      setChoice(get("lookFov"), "110");
      setChoice(get("lookTime"), "6000");
      setChoice(get("lookWeather"), "clear");
      ((JCheckBox) get("hideChat")).setSelected(true);
      ((JCheckBox) get("hideHand")).setSelected(true);
      @SuppressWarnings("unchecked") java.util.Map<String, JCheckBox> parts = (java.util.Map<String, JCheckBox>) get("hudParts");
      if (parts.containsKey("FPS")) parts.get("FPS").setSelected(false);
      Properties p = new Properties();
      call("putLook", p);
      System.out.println("job look keys: " + new java.util.TreeMap<>(p));
    });
    BufferedImage img = new BufferedImage(1320, 820, BufferedImage.TYPE_INT_RGB);
    SwingUtilities.invokeAndWait(() -> {
      e.getContentPane().setSize(1320, 820);
      e.getContentPane().validate();
      Graphics2D g = img.createGraphics();
      e.getContentPane().printAll(g);
      g.dispose();
    });
    ImageIO.write(img, "png", new File(args[1]));
    System.out.println("status: " + get("statusText"));
    System.exit(0);
  }

  @SuppressWarnings("unchecked")
  private static List<ReplayEditor.Clip> clips() throws Exception {
    return (List<ReplayEditor.Clip>) get("clips");
  }

  private static double total() throws Exception {
    double t = 0;
    for (ReplayEditor.Clip c : clips()) t += c.dur();
    return t;
  }

  private static double playTime() throws Exception {
    return (Double) get("playTime");
  }

  private static void setChoice(Object choice, String v) {
    try {
      Method set = choice.getClass().getDeclaredMethod("set", String.class);
      set.setAccessible(true);
      set.invoke(choice, v);
    } catch (Exception x) {
      throw new RuntimeException(x);
    }
  }

  private interface Body {
    void run() throws Exception;
  }

  private static void run(Body b) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        b.run();
      } catch (Exception x) {
        throw new RuntimeException(x);
      }
    });
  }

  private static Field field(Class<?> c, String name) throws Exception {
    Field f = c.getDeclaredField(name);
    f.setAccessible(true);
    return f;
  }

  private static Object get(String name) throws Exception {
    return field(ReplayEditor.class, name).get(e);
  }

  private static Object call(String name, Object... args) throws Exception {
    for (Method m : ReplayEditor.class.getDeclaredMethods()) {
      if (m.getName().equals(name) && m.getParameterCount() == args.length) {
        m.setAccessible(true);
        return m.invoke(e, args);
      }
    }
    throw new NoSuchMethodException(name);
  }
}
