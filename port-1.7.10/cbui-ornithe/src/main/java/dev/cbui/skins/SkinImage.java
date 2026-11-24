package dev.cbui.skins;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Turns a downloaded player skin into the 64x64 layout the player model reads. Plain Java on purpose, so the pixel
 * mapping can be checked without starting the game.
 */
public final class SkinImage {
  public static final int SIZE = 64;

  private SkinImage() {
  }

  /**
   * Returns a 64x64 ARGB copy of {@code source}. Old 64x32 skins get their left arm and leg filled in from the right
   * ones, because the old model drew the left limbs as mirrored copies and the new model reads its own slots.
   */
  public static BufferedImage toModern(BufferedImage source) {
    if (source == null) return null;
    boolean legacy = source.getHeight() * 2 == source.getWidth();
    BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    int[] pixels = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
    // Pixels are copied exactly rather than drawn, because drawing blends see-through pixels and loses their colour.
    // Oversized skins are cropped like the game always did; a 2:1 skin only fills the top half, so nothing of a larger
    // one leaks into the new lower half.
    int width = Math.min(SIZE, source.getWidth());
    int height = Math.min(legacy ? SIZE / 2 : SIZE, source.getHeight());
    source.getRGB(0, 0, width, height, pixels, 0, SIZE);
    fix(pixels, legacy);
    return out;
  }

  /** Works in place on a 64x64 ARGB pixel array, row by row. */
  public static void fix(int[] pixels, boolean legacy) {
    if (legacy) {
      mirrorLimb(pixels, 0, 16, 16, 48);
      mirrorLimb(pixels, 40, 16, 32, 48);
    }
    // Many old skins were saved with a solid hat area. A hat with no see-through pixel at all is almost always that
    // junk rather than a real hat, so it is cleared to keep the face visible.
    clearIfSolid(pixels, 32, 0, 64, 16);
    // The base layer has no reason to be see-through, and holes in it would show the inside of the model.
    makeOpaque(pixels, 0, 0, 32, 16);
    makeOpaque(pixels, 0, 16, 64, 32);
    makeOpaque(pixels, 16, 48, 48, 64);
  }

  /**
   * Copies a 4x12x4 limb so that, drawn without mirroring, it looks like the old mirrored limb: every face flips left to
   * right, and the two side faces trade places because mirroring moves the outer side to the inside.
   */
  static void mirrorLimb(int[] pixels, int fromU, int fromV, int toU, int toV) {
    copyFlipped(pixels, fromU + 4, fromV, toU + 4, toV, 4, 4);
    copyFlipped(pixels, fromU + 8, fromV, toU + 8, toV, 4, 4);
    copyFlipped(pixels, fromU, fromV + 4, toU + 8, toV + 4, 4, 12);
    copyFlipped(pixels, fromU + 4, fromV + 4, toU + 4, toV + 4, 4, 12);
    copyFlipped(pixels, fromU + 8, fromV + 4, toU, toV + 4, 4, 12);
    copyFlipped(pixels, fromU + 12, fromV + 4, toU + 12, toV + 4, 4, 12);
  }

  private static void copyFlipped(int[] pixels, int fromX, int fromY, int toX, int toY, int width, int height) {
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        pixels[(toY + y) * SIZE + toX + x] = pixels[(fromY + y) * SIZE + fromX + width - 1 - x];
      }
    }
  }

  private static void clearIfSolid(int[] pixels, int x0, int y0, int x1, int y1) {
    for (int y = y0; y < y1; y++) {
      for (int x = x0; x < x1; x++) {
        if ((pixels[y * SIZE + x] >>> 24) < 128) return;
      }
    }
    for (int y = y0; y < y1; y++) {
      for (int x = x0; x < x1; x++) pixels[y * SIZE + x] &= 0x00FFFFFF;
    }
  }

  private static void makeOpaque(int[] pixels, int x0, int y0, int x1, int y1) {
    for (int y = y0; y < y1; y++) {
      for (int x = x0; x < x1; x++) pixels[y * SIZE + x] |= 0xFF000000;
    }
  }
}
