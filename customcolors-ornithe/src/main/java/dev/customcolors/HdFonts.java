package dev.customcolors;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;

/** HD fonts from {@code mcpatcher/font} or {@code optifine/font}. Adapted from MCPatcher's FontUtils (see NOTICE). */
public final class HdFonts {
    private static final boolean ENABLED = !FabricLoader.getInstance().isModLoaded("cera");
    private static final String[] ROOTS = {"mcpatcher", "optifine"};
    private static final char[] AVERAGE_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123467890".toCharArray();
    private static final int[] SPACERS = {0x02028BFE, 0x02808080, 0x0DFFFFFF};
    private static final int GRID = 16;

    private HdFonts() {
    }

    /** The pack's HD replacement for a font image, or null when there is none. */
    public static ResourceLocation replacement(ResourceLocation font) {
        if (!ENABLED || "false".equals(System.getProperty("luka.hdFonts"))) return null;
        String name = font.getResourcePath().replaceAll(".*/", "");
        for (String root : ROOTS) {
            ResourceLocation candidate = new ResourceLocation(font.getResourceDomain(), root + "/font/" + name);
            if (exists(candidate)) return candidate;
        }
        return null;
    }

    /** The pack's HD replacement for a unicode page, or the page itself. */
    public static ResourceLocation unicodePage(ResourceLocation page) {
        if (!ENABLED || "false".equals(System.getProperty("luka.hdFonts")) || !page.getResourcePath().startsWith("textures/")) return page;
        String rest = page.getResourcePath().substring("textures/".length());
        for (String root : ROOTS) {
            ResourceLocation candidate = new ResourceLocation(page.getResourceDomain(), root + "/" + rest);
            if (exists(candidate)) return candidate;
        }
        return page;
    }

    private static boolean exists(ResourceLocation location) {
        try (InputStream ignored = Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream()) {
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Fractional glyph widths for an HD font, also written back rounded into {@code charWidth}. Null if the image is unreadable. */
    public static float[] widths(ResourceLocation font, int[] charWidth) {
        BufferedImage image;
        try (InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(font).getInputStream()) {
            image = TextureUtil.readBufferedImage(in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        int width = image.getWidth(), height = image.getHeight();
        int[] rgb = new int[width * height];
        image.getRGB(0, 0, width, height, rgb, 0, width);
        int colWidth = width / GRID, rowHeight = height / GRID;
        float[] widths = new float[charWidth.length];
        for (int ch = 0; ch < charWidth.length; ch++) {
            int row = ch / GRID, col = ch % GRID;
            outer:
            for (int column = colWidth - 1; column >= 0; column--) {
                int x = col * colWidth + column;
                for (int rowIndex = 0; rowIndex < rowHeight; rowIndex++) {
                    if (opaque(rgb[x + (row * rowHeight + rowIndex) * width])) {
                        widths[ch] = 128.0F * (column + 1) / width + 1.0F;
                        break outer;
                    }
                }
            }
        }
        for (int ch = 0; ch < widths.length; ch++) {
            if (widths[ch] <= 0.0F) widths[ch] = 2.0F;
            else if (widths[ch] >= 7.99F) widths[ch] = 7.99F;
        }
        boolean[] overridden = new boolean[charWidth.length];
        readOverrides(font, widths, overridden);
        if (!overridden[32]) widths[32] = spaceWidth(widths);
        for (int ch = 0; ch < charWidth.length; ch++) charWidth[ch] = Math.round(widths[ch]);
        return widths;
    }

    private static boolean opaque(int pixel) {
        for (int spacer : SPACERS) {
            if (pixel == spacer) return false;
        }
        return (pixel >> 24 & 0xF0) > 0;
    }

    private static float spaceWidth(float[] widths) {
        float sum = 0.0F;
        int count = 0;
        for (char ch : AVERAGE_CHARS) {
            if (widths[ch] > 0.0F) {
                sum += widths[ch];
                count++;
            }
        }
        return count > 0 ? sum / count * 7.0F / 12.0F : 4.0F;
    }

    private static void readOverrides(ResourceLocation font, float[] widths, boolean[] overridden) {
        ResourceLocation file = new ResourceLocation(font.getResourceDomain(), font.getResourcePath().replaceFirst("\\.png$", ".properties"));
        Properties properties = new Properties();
        try (InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(file).getInputStream()) {
            properties.load(in);
        } catch (IOException | RuntimeException e) {
            return;
        }
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = entry.getKey().toString().trim();
            if (!key.matches("^width\\.\\d+$")) continue;
            try {
                int ch = Integer.parseInt(key.substring(6));
                float value = Float.parseFloat(entry.getValue().toString().trim());
                if (ch < widths.length) {
                    widths[ch] = value;
                    overridden[ch] = true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
