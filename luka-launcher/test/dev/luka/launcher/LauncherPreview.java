package dev.luka.launcher;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * Test: the launcher window with a throwaway home folder, never shown (its Home page drawn into a PNG), and the replay
 * editor opened from it the way the REPLAY EDITOR button does.
 *   java -cp <launcher classes>;res;<this> dev.luka.launcher.LauncherPreview <empty home dir> <png prefix>
 */
public final class LauncherPreview {
  public static void main(String[] args) throws Exception {
    File root = new File(args[0]);
    root.mkdirs();
    LauncherFrame[] f = new LauncherFrame[1];
    SwingUtilities.invokeAndWait(() -> {
      f[0] = new LauncherFrame(root, false);
      f[0].addNotify();
      f[0].validate();
    });
    Thread.sleep(1500);
    BufferedImage img = new BufferedImage(f[0].getContentPane().getWidth(), f[0].getContentPane().getHeight(), BufferedImage.TYPE_INT_RGB);
    SwingUtilities.invokeAndWait(() -> {
      Graphics2D g = img.createGraphics();
      f[0].getContentPane().printAll(g);
      g.dispose();
    });
    ImageIO.write(img, "png", new File(args[1] + "-home.png"));
    // what the REPLAY EDITOR button builds (not shown here: openReplayEditor would put it on screen)
    ReplayEditor[] editor = new ReplayEditor[1];
    SwingUtilities.invokeAndWait(() -> editor[0] = new ReplayEditor(f[0]));
    System.out.println("editor built: " + (editor[0] != null) + ", game dir " + f[0].gameDir() + ", version " + f[0].gameVersion().id);
    editor[0].dispose();
    System.exit(0);
  }
}
