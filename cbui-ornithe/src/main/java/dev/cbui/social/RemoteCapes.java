package dev.cbui.social;

import dev.cbui.Cbui;
import dev.cbui.feature.Features;
import dev.cbui.feature.online.LukaOnlineModule;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

/**
 * Capes that luka's service hands to other luka players (by rank, or one they were given). The art is downloaded once per cape id on a
 * background thread; the render thread only turns the finished picture into a texture and steps through the frames of an animated cape.
 */
public final class RemoteCapes {
  private static final class Sheet {
    volatile int[] pixels;
    volatile int width, frameHeight, frames, frameTime;
    volatile boolean failed;
    DynamicTexture texture;
    ResourceLocation location;
    int shown = -1;
  }

  private static final Map<String, Sheet> sheets = new ConcurrentHashMap<String, Sheet>();

  private RemoteCapes() {}

  /** The texture of this player's luka cape, or null (not a luka user, no cape, capes switched off, or still downloading). Render thread only. */
  public static ResourceLocation textureFor(UUID player) {
    try {
      LukaOnlineModule module = Features.get(LukaOnlineModule.class);
      if (module == null || !module.enabled() || !module.showCapes()) return null;
      LukaNet.Info info = LukaNet.info(player);
      if (info == null || !validId(info.cape)) return null;
      Sheet sheet = sheets.get(info.cape);
      if (sheet == null) {
        if (sheets.size() > 40) return null;
        sheet = new Sheet();
        sheets.put(info.cape, sheet);
        load(info.cape, sheet);
        return null;
      }
      if (sheet.failed || sheet.pixels == null) return null;
      if (sheet.texture == null) {
        sheet.texture = new DynamicTexture(sheet.width, sheet.frameHeight);
        sheet.location = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("luka_rcape_" + info.cape, sheet.texture);
      }
      int frame = sheet.frames <= 1 ? 0 : (int) ((System.currentTimeMillis() / (50L * sheet.frameTime)) % sheet.frames);
      if (frame != sheet.shown) {
        sheet.shown = frame;
        System.arraycopy(sheet.pixels, frame * sheet.frameHeight * sheet.width, sheet.texture.getTextureData(), 0, sheet.width * sheet.frameHeight);
        sheet.texture.updateDynamicTexture();
      }
      return sheet.location;
    } catch (Throwable error) {
      Cbui.reportOnce("remote cape", error);
      return null;
    }
  }

  /** 1 to 32 letters, digits or underscores (a loop, not a regex: this runs for every player every frame). */
  private static boolean validId(String id) {
    int n = id.length();
    if (n == 0 || n > 32) return false;
    for (int i = 0; i < n; i++) {
      char c = id.charAt(i);
      if (!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_')) return false;
    }
    return true;
  }

  private static void load(final String id, final Sheet sheet) {
    Thread thread = new Thread(new Runnable() {
      @Override public void run() {
        try {
          byte[] png = LukaNet.fetch("/cdn/capes/" + id + "/cape.png");
          JsonObject meta = new JsonParser().parse(new String(LukaNet.fetch("/cdn/capes/" + id + "/cape.json"), StandardCharsets.UTF_8)).getAsJsonObject();
          int fh = meta.get("fh").getAsInt(), frames = meta.get("frames").getAsInt(), time = Math.max(1, meta.get("frametime").getAsInt());
          BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
          int w = image.getWidth();
          if (w < 22 || w > 1024 || fh < 17 || frames < 1 || frames > 64 || image.getHeight() < fh * frames) throw new IllegalStateException("bad cape sheet");
          sheet.pixels = image.getRGB(0, 0, w, fh * frames, null, 0, w);
          sheet.width = w;
          sheet.frameHeight = fh;
          sheet.frames = frames;
          sheet.frameTime = time;
        } catch (Throwable error) {
          sheet.failed = true;
        }
      }
    }, "luka-cape-download");
    thread.setDaemon(true);
    thread.start();
  }
}
