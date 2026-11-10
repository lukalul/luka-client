package dev.cbui.feature.polycrosshair;

import dev.cbui.Cbui;
import dev.cbui.feature.polycrosshair.PolyCrosshairModule.Profile;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

final class CrosshairTextures {
  static final class Layer {
    final ResourceLocation location;
    final DynamicTexture texture;
    final int size;
    Layer(ResourceLocation location, DynamicTexture texture, int size) { this.location = location; this.texture = texture; this.size = size; }
    void close() {
      try { Minecraft.getMinecraft().getTextureManager().deleteTexture(location); }
      catch (Throwable unavailable) { /* client is shutting down */ }
    }
  }

  static final class Entry {
    long signature;
    Layer art, silhouette, outline;
    int cells;
    void close() {
      Layer[] layers = {art, silhouette, outline};
      art = silhouette = outline = null;
      for (Layer layer : layers) if (layer != null) layer.close();
    }
  }

  private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
  private static final Map<String, Long> FAILURES = new LinkedHashMap<>();
  private static int sequence;
  private CrosshairTextures() {}

  static Entry get(Profile profile, int texels) {
    long version = versionOf(profile, texels);
    Entry old = ENTRIES.get(profile.key);
    if (old != null && old.signature == version) return old.art == null ? null : old;
    Long rejected = FAILURES.get(profile.key);
    if (rejected != null && rejected == version) return null;
    if (old != null) old.close();
    Entry replacement = new Entry();
    replacement.signature = version;
    try {
      Pixels base = profile.pixels();
      Shapes vectors = profile.shapes(base.size);
      if (!base.isEmpty() || !vectors.isEmpty()) populate(profile, texels, base, vectors, replacement);
    } catch (Throwable problem) {
      replacement.close();
      FAILURES.put(profile.key, version);
      Cbui.reportOnce("crosshair texture " + profile.key, problem);
    }
    ENTRIES.put(profile.key, replacement);
    return replacement.art == null ? null : replacement;
  }

  private static long versionOf(Profile profile, int texels) {
    long value = 0xcbf29ce484222325L;
    value = mix(value, profile.design.get().hashCode());
    value = mix(value, profile.color.get());
    value = mix(value, Float.floatToIntBits(profile.arm.get()));
    value = mix(value, Float.floatToIntBits(profile.thickness.get()));
    value = mix(value, Float.floatToIntBits(profile.gap.get()));
    value = mix(value, profile.centerDot.get() ? 1 : 0);
    value = mix(value, profile.cells());
    value = mix(value, texels);
    value = mix(value, profile.outlineOn.get() ? 1 : 0);
    value = mix(value, Float.floatToIntBits(profile.outlineThickness.get()));
    return mix(value, profile.stamp());
  }

  private static long mix(long hash, long component) { return (hash ^ component) * 0x100000001b3L; }

  private static void populate(Profile profile, int scale, Pixels base, Shapes vectors, Entry entry) {
    Pixels colored = base.scaled(scale);
    vectors.paint(colored, scale, null);
    Pixels mask = base.silhouette().scaled(scale);
    vectors.paint(mask, scale, Integer.valueOf(-1));
    entry.cells = base.size;
    entry.art = upload(profile.key + "-color", colored);
    entry.silhouette = upload(profile.key + "-mask", mask);
    if (profile.outlineOn.get()) {
      Pixels edge = createOutline(base, vectors, mask, profile.outlineThickness.get(), scale);
      if (edge != null) entry.outline = upload(profile.key + "-edge", edge);
    }
  }

  private static Pixels createOutline(Pixels base, Shapes vectors, Pixels mask, float width, int scale) {
    Pixels pixelEdge = base.outline(width, scale);
    float[] vectorEdge = vectors.isEmpty() ? null : vectors.coverage(scale, width);
    if (pixelEdge == null && vectorEdge == null) return null;
    Pixels result = new Pixels(mask.size);
    boolean any = false;
    for (int i = 0; i < result.argb.length; i++) {
      float expanded = pixelEdge != null && pixelEdge.argb[i] != 0 ? 1f : vectorEdge == null ? 0f : vectorEdge[i];
      int alpha = Math.round(expanded * (255 - (mask.argb[i] >>> 24)));
      if (alpha > 0) { result.argb[i] = alpha << 24 | 0xFFFFFF; any = true; }
    }
    return any ? result : null;
  }

  private static Layer upload(String label, Pixels pixels) {
    DynamicTexture texture = new DynamicTexture(pixels.toImage());
    String id = "luka-crosshair-" + (++sequence) + "-" + label;
    ResourceLocation location = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation(id, texture);
    return new Layer(location, texture, pixels.size);
  }
}
