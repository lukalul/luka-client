package dev.cbui.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cbui.Cbui;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * The cape library and the cape the player wears. The library is a folder of capes (cape.png sheet with the frames stacked, cape.json,
 * thumb.png) made from the cloak selection; the chosen one is saved in config/luka-cosmetics.json and drawn on the local player.
 * Animated capes keep their frames and the frame time of their .mcmeta.
 */
public final class Capes {
  /** One entry of the library. */
  public static final class Entry {
    public final String id, name, search;
    public final int width, frames;
    /** The most any frame differs from the first, in percent of the pixels (0 for a still cape). */
    public final double motion;

    Entry(String id, String name, int width, int frames, double motion) {
      this.motion = motion;
      this.id = id;
      this.name = name;
      this.search = (name + " " + id).toLowerCase(java.util.Locale.ROOT);
      this.width = width;
      this.frames = frames;
    }
  }


  private static volatile List<Entry> entries = Collections.emptyList();
  private static volatile boolean loaded, loading;
  private static File libraryDir;
  private static volatile String selected = "";

  // the worn cape: sheet decoded off the render thread, texture made on it
  private static volatile String wornId = "";
  /** A cape sheet turned into ARGB ints (and shrunk when huge) on the loader thread, so the render thread only copies a frame. */
  private static final class Prepared {
    final String id;
    final int[] pixels;
    final int width, frameHeight, frames, frameTime;

    Prepared(String id, int[] pixels, int width, int frameHeight, int frames, int frameTime) {
      this.id = id;
      this.pixels = pixels;
      this.width = width;
      this.frameHeight = frameHeight;
      this.frames = frames;
      this.frameTime = frameTime;
    }
  }

  private static volatile Prepared pending;
  private static DynamicTexture texture;
  private static ResourceLocation location;
  private static int fh, frames, frameTime, shownFrame = -1;
  private static int[] pixels;
  private static int sheetWidth;
  private static boolean started;
  /** True draws the cape with a ModelRenderer (the game's way); false with plain quads, which other mods cannot capture or batch. */
  public static boolean useModel = false;
  /** How many times a frame of the worn cape was uploaded (a test reads it to see the animation run). */
  public static int uploads;

  private Capes() {}

  /** The folder the library lives in: <game folder>/luka-cosmetics (supplied by the player; none is shipped). */
  public static synchronized File library() {
    if (libraryDir == null) {
      File here = new File(Minecraft.getMinecraft().mcDataDir, "luka-cosmetics");
      libraryDir = here; // the cape library is supplied by the player: put index.json and the cape files in <game folder>/luka-cosmetics
    }
    return libraryDir;
  }

  public static List<Entry> entries() {
    ensureLoaded();
    return entries;
  }

  public static boolean ready() {
    return loaded;
  }

  public static String selected() {
    ensureLoaded();
    return selected;
  }

  public static Entry find(String id) {
    for (Entry e : entries) if (e.id.equals(id)) return e;
    return null;
  }

  /** The cape to draw on the local player, or null (none chosen, or still loading). Call it from the render thread. */
  public static ResourceLocation current() {
    ensureLoaded();
    String want = selected;
    if (want.isEmpty()) return null;
    try {
      if (!want.equals(wornId)) {
        wornId = want;
        pending = null;
        pixels = null;
        texture = null;
        location = null;
        shownFrame = -1;
        loadSheet(want);
      }
      Prepared ready = pending;
      if (ready != null && ready.id.equals(wornId)) {
        pending = null;
        pixels = ready.pixels;
        sheetWidth = ready.width;
        fh = ready.frameHeight;
        frames = ready.frames;
        frameTime = ready.frameTime;
        texture = new DynamicTexture(sheetWidth, fh);
        location = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("luka_cape", texture);
        if (sheetWidth > 88) {
          dev.luka.compat.GlStateManager.bindTexture(texture.getGlTextureId());
          GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
          GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        }
        shownFrame = -1;
      }
      if (texture == null || pixels == null) return null;
      int frame = frames <= 1 ? 0 : (int) ((System.currentTimeMillis() / (50L * frameTime)) % frames);
      if (frame != shownFrame) {
        shownFrame = frame;
        System.arraycopy(pixels, frame * fh * sheetWidth, texture.getTextureData(), 0, sheetWidth * fh);
        texture.updateDynamicTexture();
        uploads++;
      }
      return location;
    } catch (Throwable error) {
      Cbui.reportOnce("cape", error);
      selected = "";
      return null;
    }
  }

  private static void loadSheet(final String id) {
    Thread thread = new Thread(() -> {
      try {
        File folder = new File(new File(library(), "capes"), id);
        BufferedImage image = ImageIO.read(new File(folder, "cape.png"));
        JsonObject meta;
        try (InputStreamReader in = new InputStreamReader(new FileInputStream(new File(folder, "cape.json")), StandardCharsets.UTF_8)) {
          meta = new JsonParser().parse(in).getAsJsonObject();
        }
        int frameHeight = meta.get("fh").getAsInt(), count = meta.get("frames").getAsInt(), time = Math.max(1, meta.get("frametime").getAsInt());
        int w = image.getWidth();
        BufferedImage argb = new BufferedImage(w, image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = argb.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        int[] all = ((java.awt.image.DataBufferInt) argb.getRaster().getDataBuffer()).getData();
        int outW = w, outFh = frameHeight;
        if (w >= 600) {
          // a cape this big is drawn about 100 pixels tall: half the size looks the same and uploads a quarter of the data
          outW = w / 2;
          outFh = frameHeight / 2;
          int[] half = new int[outW * outFh * count];
          for (int f = 0; f < count; f++) {
            for (int y = 0; y < outFh; y++) {
              int src0 = (f * frameHeight + y * 2) * w, src1 = src0 + w, dst = (f * outFh + y) * outW;
              for (int x = 0; x < outW; x++) {
                int a0 = all[src0 + x * 2], a1 = all[src0 + x * 2 + 1], a2 = all[src1 + x * 2], a3 = all[src1 + x * 2 + 1];
                int a = ((a0 >>> 24) + (a1 >>> 24) + (a2 >>> 24) + (a3 >>> 24)) >> 2;
                int r = (((a0 >> 16) & 255) + ((a1 >> 16) & 255) + ((a2 >> 16) & 255) + ((a3 >> 16) & 255)) >> 2;
                int gr = (((a0 >> 8) & 255) + ((a1 >> 8) & 255) + ((a2 >> 8) & 255) + ((a3 >> 8) & 255)) >> 2;
                int b = ((a0 & 255) + (a1 & 255) + (a2 & 255) + (a3 & 255)) >> 2;
                half[dst + x] = (a << 24) | (r << 16) | (gr << 8) | b;
              }
            }
          }
          all = half;
        }
        if (id.equals(wornId)) pending = new Prepared(id, all, outW, outFh, count, time);
      } catch (Throwable error) {
        Cbui.reportOnce("cape load " + id, error);
      }
    }, "luka-cape-loader");
    thread.setDaemon(true);
    thread.start();
  }

  /** Once per frame (from the HUD overlay): keeps the worn cape's animation running even if the cape layer is not drawn that frame. */
  public static void tick() {
    if (selected.isEmpty()) return;
    current();
  }

  public static void select(String id) {
    selected = id == null ? "" : id;
    saveSelection();
  }

  private static synchronized void ensureLoaded() {
    if (started) return;
    started = true;
    loading = true;
    Thread thread = new Thread(() -> {
      try {
        loadSelection();
        File index = new File(library(), "index.json");
        if (index.isFile()) {
          try (InputStreamReader in = new InputStreamReader(new FileInputStream(index), StandardCharsets.UTF_8)) {
            JsonArray array = new JsonParser().parse(in).getAsJsonObject().getAsJsonArray("capes");
            List<Entry> list = new ArrayList<>(array.size());
            for (JsonElement element : array) {
              JsonObject o = element.getAsJsonObject();
              list.add(new Entry(o.get("id").getAsString(), o.get("name").getAsString(), o.get("w").getAsInt(), o.get("frames").getAsInt(),
                  o.has("motion") ? o.get("motion").getAsDouble() : (o.get("frames").getAsInt() > 1 ? 100.0 : 0.0)));
            }
            entries = list;
          }
        }
      } catch (Throwable error) {
        Cbui.reportOnce("cape library", error);
      } finally {
        loaded = true;
        loading = false;
      }
    }, "luka-cape-index");
    thread.setDaemon(true);
    thread.start();
  }

  private static File selectionFile() {
    return new File(Minecraft.getMinecraft().mcDataDir, "config/luka-cosmetics.json");
  }

  private static void loadSelection() {
    File f = selectionFile();
    if (!f.isFile()) return;
    try (InputStreamReader in = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
      JsonObject o = new JsonParser().parse(in).getAsJsonObject();
      if (o.has("cape")) selected = o.get("cape").getAsString();
    } catch (Throwable ignored) {
      // nothing chosen
    }
  }

  private static void saveSelection() {
    File f = selectionFile();
    try {
      f.getParentFile().mkdirs();
      JsonObject o = new JsonObject();
      o.addProperty("cape", selected);
      try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
        out.write(o.toString());
      }
    } catch (Throwable ignored) {
      // the choice lasts until the game closes
    }
  }
}
