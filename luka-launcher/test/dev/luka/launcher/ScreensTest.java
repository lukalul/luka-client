package dev.luka.launcher;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * Paints every launcher screen to PNG files, and can press Play. Not part of the product.
 * Usage: ScreensTest <luka home> <output folder> [play normal|vulkan]
 */
public final class ScreensTest {
  private static LauncherFrame frame;

  private static Object get(String name) throws Exception {
    Field f = LauncherFrame.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(frame);
  }

  private static void set(String name, Object value) throws Exception {
    Field f = LauncherFrame.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(frame, value);
  }

  private static void call(String name, Class<?>[] types, Object... args) throws Exception {
    Method m = LauncherFrame.class.getDeclaredMethod(name, types);
    m.setAccessible(true);
    m.invoke(frame, args);
  }

  private static void shot(File out, String name) throws Exception {
    final BufferedImage image = new BufferedImage(LauncherFrame.W, LauncherFrame.H, BufferedImage.TYPE_INT_ARGB);
    SwingUtilities.invokeAndWait(() -> {
      Graphics2D g = image.createGraphics();
      ((Component) frame.getContentPane()).paint(g);
      g.dispose();
    });
    ImageIO.write(image, "png", new File(out, name + ".png"));
  }

  public static void main(String[] args) throws Exception {
    File home = new File(args[0]);
    File out = new File(args[1]);
    out.mkdirs();
    home.mkdirs();
    SwingUtilities.invokeAndWait(() -> {
      frame = new LauncherFrame(home, false);
      frame.setLocation(-4000, -4000);
      frame.setVisible(true);
    });
    Thread.sleep(2500);
    String[] tabs = {"home", "servers", "settings", "about"};
    for (int i = 0; i < tabs.length; i++) {
      set("tab", i);
      Thread.sleep(150);
      shot(out, "tab_" + tabs[i]);
    }
    set("tab", 0);
    call("chooseRenderer", new Class<?>[] {String.class}, "vulkan");
    Thread.sleep(150);
    shot(out, "renderer_vulkan");
    call("chooseRenderer", new Class<?>[] {String.class}, "normal");
    Thread.sleep(150);
    shot(out, "renderer_normal");
    set("showLog", true);
    Thread.sleep(150);
    shot(out, "log");
    set("showLog", false);
    set("overlay", 1);
    Thread.sleep(150);
    shot(out, "accounts");
    set("overlay", 2);
    Thread.sleep(150);
    shot(out, "sign_in");
    Field notes = Manifests.class.getDeclaredField("changelog");
    notes.setAccessible(true);
    notes.set(null, java.util.Arrays.asList(
        java.util.Arrays.asList("1.1.0", "A longer note that has to wrap onto a second line so the page can be checked for how it handles text that does not fit on one line at all.", "Short note."),
        java.util.Arrays.asList("1.0.0", "First public release of luka for Minecraft 1.8.9.", "Choose the Normal or Vulkan renderer in the launcher.")));
    set("overlay", 3);
    Thread.sleep(150);
    shot(out, "whats_new");
    set("overlay", 0);
    set("tab", 3);
    Thread.sleep(150);
    shot(out, "about_with_notes");
    set("tab", 0);
    if (args.length >= 4 && args[2].equals("play")) {
      call("chooseRenderer", new Class<?>[] {String.class}, args[3]);
      call("play", new Class<?>[] {String.class}, new Object[] {null});
      int frameNumber = 0;
      long started = System.currentTimeMillis();
      String last = "";
      while (System.currentTimeMillis() - started < 900000) {
        Thread.sleep(500);
        String status = String.valueOf(get("status"));
        if (!status.equals(last)) {
          System.out.println(((System.currentTimeMillis() - started) / 1000) + "s  " + status);
          last = status;
        }
        if (++frameNumber % 20 == 1) shot(out, "progress_" + args[3] + "_" + frameNumber);
        Object running = get("running");
        if ("Playing".equals(status)) {
          shot(out, "playing_" + args[3]);
          Thread.sleep(30000);
          System.out.println("game started, stopping after log check");
          return;
        }
        if (Boolean.FALSE.equals(running) && frameNumber > 8) {
          shot(out, "stopped_" + args[3]);
          System.out.println("stopped: " + status);
          return;
        }
      }
      System.out.println("timed out at: " + last);
    }
    System.exit(0);
  }
}
