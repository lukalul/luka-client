package dev.envoy.module.coloredfire;

import java.awt.image.BufferedImage;

/** Turns fire frames grey: half brightest channel, half luminance, stretched so the brightest flame pixel is white. */
public final class FlamePixels {
    private FlamePixels() { }

    public static void greyInto(BufferedImage source, int frames, int size, BufferedImage out, int left) {
        int edge = source.getWidth();
        int[] levels = new int[size * size * frames];
        int[] alphas = new int[levels.length];
        int brightest = 1;
        int index = 0;
        for (int frame = 0; frame < frames; frame++) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++, index++) {
                    int argb = source.getRGB(x * edge / size, frame * edge + y * edge / size);
                    int level = level(argb);
                    levels[index] = level;
                    alphas[index] = argb >>> 24;
                    if (alphas[index] > 0 && level > brightest) brightest = level;
                }
            }
        }
        index = 0;
        for (int frame = 0; frame < frames; frame++) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++, index++) {
                    int grey = Math.min(255, levels[index] * 255 / brightest);
                    out.setRGB(left + x, frame * size + y, alphas[index] << 24 | grey << 16 | grey << 8 | grey);
                }
            }
        }
    }

    static int level(int argb) {
        int red = argb >> 16 & 255, green = argb >> 8 & 255, blue = argb & 255;
        int brightest = Math.max(red, Math.max(green, blue));
        int luminance = (red * 299 + green * 587 + blue * 114) / 1000;
        return (brightest + luminance) / 2;
    }
}
