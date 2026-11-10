package dev.cbui.feature.polycrosshair;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;
import javax.imageio.ImageIO;

public final class Pixels {
  public final int size;
  public final int[] argb;

  public Pixels(int size) { this(size, new int[size * size]); }
  public Pixels(int size, int[] argb) { this.size = size; this.argb = argb; }
  public int get(int x, int y) { return inBounds(x, y) ? argb[y * size + x] : 0; }
  public void set(int x, int y, int color) { if (inBounds(x, y)) argb[y * size + x] = color; }
  public boolean inBounds(int x, int y) { return (x | y) >= 0 && x < size && y < size; }
  public boolean isOpaque(int x, int y) { return (get(x, y) & 0xFF000000) != 0; }
  public boolean isEmpty() { for (int pixel : argb) if ((pixel & 0xFF000000) != 0) return false; return true; }
  public Pixels copy() { return new Pixels(size, Arrays.copyOf(argb, argb.length)); }

  public Pixels resized(int newSize, boolean stretch) {
    if (newSize == size) return copy();
    Pixels result = new Pixels(newSize);
    if (!stretch) {
      int shift = Math.round((newSize - size) * .5f);
      for (int source = 0; source < argb.length; source++) result.set(source % size + shift, source / size + shift, argb[source]);
      return result;
    }
    for (int destination = 0; destination < result.argb.length; destination++) {
      int x = destination % newSize, y = destination / newSize;
      int sx = Math.min(size - 1, (2 * x + 1) * size / (2 * newSize));
      int sy = Math.min(size - 1, (2 * y + 1) * size / (2 * newSize));
      result.argb[destination] = get(sx, sy);
    }
    return result;
  }

  public BufferedImage toImage() {
    BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0, 0, size, size, argb, 0, size);
    return image;
  }

  public Pixels silhouette() {
    Pixels mask = new Pixels(size);
    for (int i = 0; i < argb.length; i++) mask.argb[i] = (argb[i] >>> 24) == 0 ? 0 : 0xFFFFFFFF;
    return mask;
  }

  public Pixels scaled(int factor) {
    if (factor == 1) return copy();
    int enlarged = size * factor;
    int[] output = new int[enlarged * enlarged];
    for (int i = 0; i < output.length; i++) output[i] = argb[(i / enlarged / factor) * size + (i % enlarged) / factor];
    return new Pixels(enlarged, output);
  }

  public Pixels outline(float thickness, int scale) {
    float radius = thickness * scale;
    if (radius < .5f || isEmpty()) return null;
    Pixels mask = silhouette().scaled(scale), border = new Pixels(size * scale);
    int reach = (int) Math.ceil(radius);
    float limit = radius * radius;
    boolean present = false;
    for (int y = 0; y < mask.size; y++) for (int x = 0; x < mask.size; x++) {
      if (mask.isOpaque(x, y)) continue;
      boolean near = false;
      for (int dy = -reach; dy <= reach && !near; dy++) for (int dx = -reach; dx <= reach; dx++) {
        if (dx * dx + dy * dy <= limit && mask.isOpaque(x + dx, y + dy)) { near = true; break; }
      }
      if (near) { border.set(x, y, 0xFFFFFFFF); present = true; }
    }
    return present ? border : null;
  }

  public static Pixels fromBase64(String base64) {
    if (base64 == null) return null;
    try { return fromImage(ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64.trim())))); }
    catch (IllegalArgumentException | java.io.IOException ignored) { return null; }
  }

  public static Pixels fromImage(BufferedImage image) {
    if (image == null || image.getWidth() < 1 || image.getWidth() > 256 || image.getHeight() != image.getWidth()) return null;
    int width = image.getWidth();
    return new Pixels(width, image.getRGB(0, 0, width, width, null, 0, width));
  }
}
