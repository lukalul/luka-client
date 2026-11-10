package dev.cbui.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/** Small pictures of the service's capes for the cape screen: the first frame of the sheet, shrunk to 66 x 51, made off the render thread. */
public final class CapeArt {
  public static final int W = 66, H = 51;
  private static final Set<String> started = java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
  private static final Map<String, BufferedImage> done = new ConcurrentHashMap<String, BufferedImage>();

  private CapeArt() {}

  /** Starts the download of this cape's picture once; the finished picture appears in {@code sink} under the cape id. */
  public static void load(final String id, final Map<String, BufferedImage> sink) {
    if (!id.matches("[A-Za-z0-9_]{1,32}")) return;
    BufferedImage cached = done.get(id);
    if (cached != null) {
      sink.put(id, cached);
      return;
    }
    if (!started.add(id)) return;
    Thread thread = new Thread(new Runnable() {
      @Override public void run() {
        try {
          byte[] png = LukaNet.fetch("/cdn/capes/" + id + "/cape.png");
          int fh = 68;
          try {
            JsonObject meta = new JsonParser().parse(new String(LukaNet.fetch("/cdn/capes/" + id + "/cape.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            fh = meta.get("fh").getAsInt();
          } catch (Exception ignored) {
            // the usual frame height
          }
          BufferedImage sheet = ImageIO.read(new ByteArrayInputStream(png));
          if (sheet == null || sheet.getWidth() < 22 || sheet.getWidth() > 1024 || fh < 17 || sheet.getHeight() < fh) return;
          BufferedImage frame = sheet.getSubimage(0, 0, sheet.getWidth(), fh);
          BufferedImage small = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
          Graphics2D g = small.createGraphics();
          g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
          g.drawImage(frame, 0, 0, W, H, null);
          g.dispose();
          done.put(id, small);
          sink.put(id, small);
        } catch (Throwable ignored) {
          started.remove(id); // try again when the screen is opened next time
        }
      }
    }, "luka-cape-thumb");
    thread.setDaemon(true);
    thread.start();
  }
}
