package dev.cbui.feature.polycrosshair;

import java.util.ArrayList;
import java.util.List;

public final class Shapes {
  public final int size;
  private final List<Stroke> strokes = new ArrayList<>();

  public Shapes(int size) { this.size = size; }
  public boolean isEmpty() { return strokes.isEmpty(); }

  public static Shapes decode(String encoded, int targetSize, boolean stretch) {
    Shapes source = read(encoded);
    if (source == null) return new Shapes(targetSize);
    if (source.size == targetSize) return source;
    Shapes adjusted = new Shapes(targetSize);
    float ratio = (float) targetSize / source.size;
    float shift = Math.round((targetSize - source.size) / 2f);
    for (Stroke stroke : source.strokes) adjusted.strokes.add(stroke.transform(stretch ? ratio : 1f, stretch ? 0f : shift));
    return adjusted;
  }

  private static Shapes read(String text) {
    if (text == null || text.trim().isEmpty()) return null;
    try {
      String[] records = text.split(";");
      Shapes result = new Shapes(Integer.parseInt(records[0].trim()));
      for (int i = 1; i < records.length; i++) {
        String[] values = records[i].split(":");
        if (values.length == 5) result.strokes.add(Stroke.circle(values));
        else if (values.length == 6) result.strokes.add(Stroke.line(values));
        else return null;
      }
      return result;
    } catch (RuntimeException malformed) { return null; }
  }

  public void paint(final Pixels target, int scale, final Integer override) {
    for (final Stroke stroke : strokes) rasterize(stroke, scale, 0f, new PixelAction() {
      @Override public void apply(int index, float amount) {
        target.argb[index] = composite(target.argb[index], override == null ? stroke.color : override, amount);
      }
    });
  }

  public float[] coverage(int scale, float grow) {
    final float[] result = new float[size * scale * size * scale];
    for (Stroke stroke : strokes) rasterize(stroke, scale, grow, new PixelAction() {
      @Override public void apply(int index, float amount) { result[index] = Math.max(result[index], amount); }
    });
    return result;
  }

  private void rasterize(Stroke stroke, int scale, float grow, PixelAction action) {
    int width = size * scale;
    float halfWidth = (stroke.width * .5f + grow) * scale;
    if (halfWidth <= 0f) return;
    int margin = (int) Math.ceil(halfWidth + 1f);
    int left = Math.max(0, (int) Math.floor(stroke.minX() * scale) - margin);
    int right = Math.min(width - 1, (int) Math.ceil(stroke.maxX() * scale) + margin);
    int top = Math.max(0, (int) Math.floor(stroke.minY() * scale) - margin);
    int bottom = Math.min(width - 1, (int) Math.ceil(stroke.maxY() * scale) + margin);
    float ceiling = Math.min(1f, halfWidth * 2f);
    for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) {
      float distance = stroke.distance((x + .5f) / scale, (y + .5f) / scale) * scale;
      float alpha = Math.min(ceiling, Math.max(0f, halfWidth - distance + .5f));
      if (alpha > 0f) action.apply(y * width + x, alpha);
    }
  }

  private static int composite(int beneath, int above, float coverage) {
    float topAlpha = (above >>> 24) / 255f * coverage;
    float bottomAlpha = (beneath >>> 24) / 255f * (1f - topAlpha);
    float combined = topAlpha + bottomAlpha;
    if (combined <= 0f) return 0;
    int a = clamp(Math.round(combined * 255f));
    int r = blendChannel(beneath, above, 16, topAlpha, bottomAlpha, combined);
    int g = blendChannel(beneath, above, 8, topAlpha, bottomAlpha, combined);
    int b = blendChannel(beneath, above, 0, topAlpha, bottomAlpha, combined);
    return a << 24 | r << 16 | g << 8 | b;
  }

  private static int blendChannel(int bottom, int top, int shift, float ta, float ba, float total) {
    return clamp(Math.round((((top >>> shift) & 255) * ta + ((bottom >>> shift) & 255) * ba) / total));
  }
  private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }
  private interface PixelAction { void apply(int index, float amount); }

  private static final class Stroke {
    final boolean circle;
    final float x1, y1, x2, y2, width;
    final int color;
    Stroke(boolean circle, float x1, float y1, float x2, float y2, float width, int color) {
      this.circle = circle; this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; this.width = width; this.color = color;
    }
    static Stroke circle(String[] v) { return new Stroke(true, f(v[0]), f(v[1]), f(v[2]), 0f, f(v[3]), hex(v[4])); }
    static Stroke line(String[] v) { return new Stroke(false, f(v[0]), f(v[1]), f(v[2]), f(v[3]), f(v[4]), hex(v[5])); }
    Stroke transform(float scale, float shift) {
      return circle ? new Stroke(true, x1 * scale + shift, y1 * scale + shift, x2 * scale, 0f, width * scale, color)
          : new Stroke(false, x1 * scale + shift, y1 * scale + shift, x2 * scale + shift, y2 * scale + shift, width * scale, color);
    }
    float minX() { return circle ? x1 - x2 : Math.min(x1, x2); }
    float maxX() { return circle ? x1 + x2 : Math.max(x1, x2); }
    float minY() { return circle ? y1 - x2 : Math.min(y1, y2); }
    float maxY() { return circle ? y1 + x2 : Math.max(y1, y2); }
    float distance(float x, float y) {
      if (circle) return Math.abs((float) Math.hypot(x - x1, y - y1) - x2);
      float dx = x2 - x1, dy = y2 - y1, lengthSquared = dx * dx + dy * dy;
      if (lengthSquared == 0f) return Float.POSITIVE_INFINITY;
      float t = Math.max(0f, Math.min(1f, ((x - x1) * dx + (y - y1) * dy) / lengthSquared));
      return (float) Math.hypot(x - (x1 + t * dx), y - (y1 + t * dy));
    }
    private static float f(String value) { return Float.parseFloat(value); }
    private static int hex(String value) { return (int) Long.parseLong(value, 16); }
  }
}
