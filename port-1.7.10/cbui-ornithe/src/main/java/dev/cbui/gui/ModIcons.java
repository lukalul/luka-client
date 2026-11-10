package dev.cbui.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

/**
 * Preview pictures for the mod cards: the icon the mod ships (the "icon" of its fabric.mod.json) when there is one,
 * otherwise a tile generated from the mod's name (a coloured gradient with its initials). Textures are built on first
 * use on the render thread and cached.
 */
final class ModIcons {
  private static final int SIZE = 64;
  private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

  private ModIcons() {}

  /** The texture for a card; {@code modId} may be null or not a Fabric mod id, then the name is matched against mod names. */
  static ResourceLocation get(String modId, String name) {
    String key = (modId == null ? "" : modId) + "|" + name;
    ResourceLocation cached = CACHE.get(key);
    if (cached != null) return cached;
    BufferedImage image = bundled(name);
    if (image == null) image = shipped(modId, name);
    debug(modId, name, image != null);
    if (image == null) image = generated(name);
    ResourceLocation location =
        Minecraft.getMinecraft()
            .getTextureManager()
            .getDynamicTextureLocation("cbui_modicon_" + CACHE.size(), new DynamicTexture(image));
    CACHE.put(key, location);
    return location;
  }

  /** With an empty file config/cbui-icon-debug present, records which cards got the mod's own icon. */
  private static void debug(String modId, String name, boolean shipped) {
    try {
      java.io.File marker = new java.io.File(Minecraft.getMinecraft().mcDataDir, "config/cbui-icon-debug");
      if (!marker.exists()) return;
      Files.write(new java.io.File(marker.getParentFile(), "cbui-icon-debug.log").toPath(),
          (name + " | " + modId + " | shipped=" + shipped + System.lineSeparator()).getBytes("UTF-8"),
          java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    } catch (Throwable ignored) {
    }
  }

  /** The picture luka ships for this card (assets/cbui/icons/mods/&lt;name&gt;.png, drawn for it), or null. */
  private static BufferedImage bundled(String name) {
    String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    try (InputStream in = ModIcons.class.getResourceAsStream("/assets/cbui/icons/mods/" + slug + ".png")) {
      if (in == null) return null;
      BufferedImage source = ImageIO.read(in);
      return source == null ? null : fit(source);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static BufferedImage shipped(String modId, String name) {
    try {
      ModContainer container = null;
      if (modId != null) container = FabricLoader.getInstance().getModContainer(modId.toLowerCase(Locale.ROOT)).orElse(null);
      if (container == null) {
        for (ModContainer candidate : FabricLoader.getInstance().getAllMods()) {
          if (candidate.getMetadata().getName().equalsIgnoreCase(name)) {
            container = candidate;
            break;
          }
        }
      }
      if (container == null) return null;
      Optional<String> iconPath = container.getMetadata().getIconPath(SIZE);
      if (!iconPath.isPresent()) return null;
      Optional<Path> file = container.findPath(iconPath.get());
      if (!file.isPresent()) return null;
      try (InputStream in = Files.newInputStream(file.get())) {
        BufferedImage source = ImageIO.read(in);
        return source == null ? null : fit(source);
      }
    } catch (Throwable ignored) {
      return null;
    }
  }

  /** The picture scaled (smoothly) into a square of SIZE, keeping its aspect ratio and transparency. */
  private static BufferedImage fit(BufferedImage source) {
    BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      double scale = Math.min((double) SIZE / source.getWidth(), (double) SIZE / source.getHeight());
      int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
      int h = Math.max(1, (int) Math.round(source.getHeight() * scale));
      BufferedImage stepped = source;
      // halve repeatedly first: a single big jump down looks jagged
      while (stepped.getWidth() > w * 2 && stepped.getHeight() > h * 2) {
        BufferedImage half = new BufferedImage(stepped.getWidth() / 2, stepped.getHeight() / 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D hg = half.createGraphics();
        hg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        hg.drawImage(stepped, 0, 0, half.getWidth(), half.getHeight(), null);
        hg.dispose();
        stepped = half;
      }
      g.drawImage(stepped, (SIZE - w) / 2, (SIZE - h) / 2, w, h, null);
    } finally {
      g.dispose();
    }
    return out;
  }

  /** A tile in a colour taken from the name, with the first letters of the words on it. */
  private static BufferedImage generated(String name) {
    BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      // luka's range only: indigo through violet to magenta, so every generated tile belongs to the purple theme
      float hue = (250f + Math.abs(name.toLowerCase(Locale.ROOT).hashCode()) % 55) / 360f;
      Color top = Color.getHSBColor(hue, 0.55f, 0.92f), bottom = Color.getHSBColor((hue + 0.015f) % 1f, 0.75f, 0.42f);
      g.setPaint(new GradientPaint(0, 0, top, 0, SIZE, bottom));
      g.fillRoundRect(2, 2, SIZE - 4, SIZE - 4, 22, 22);
      g.setColor(new Color(255, 255, 255, 46));
      g.fillRoundRect(2, 2, SIZE - 4, (SIZE - 4) / 2, 22, 22);
      String text = initials(name);
      g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, text.length() > 1 ? 26 : 32));
      FontMetrics fm = g.getFontMetrics();
      g.setColor(Color.WHITE);
      g.drawString(text, (SIZE - fm.stringWidth(text)) / 2, (SIZE - fm.getHeight()) / 2 + fm.getAscent());
    } finally {
      g.dispose();
    }
    return out;
  }

  private static String initials(String name) {
    StringBuilder out = new StringBuilder();
    for (String word : name.trim().split("[\\s_\\-]+")) {
      if (word.isEmpty() || !Character.isLetterOrDigit(word.charAt(0))) continue;
      out.append(Character.toUpperCase(word.charAt(0)));
      if (out.length() == 2) break;
    }
    return out.length() == 0 ? "?" : out.toString();
  }
}
