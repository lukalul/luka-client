package dev.cbui.skins;

import java.awt.image.BufferedImage;

/**
 * Checks the 64x32 to 64x64 skin conversion pixel by pixel. Expected positions are written out by hand from the skin
 * layout rather than computed the way SkinImage does it, so a wrong face mapping cannot pass by agreeing with itself.
 */
public final class SkinImageChecks {
  public static void main(String[] args) {
    run();
    System.out.println("Skin checks passed: legacy limb mirroring, overlay transparency, hat junk, base opacity, 64x64 kept");
  }

  public static void run() {
    legacyLimbs();
    legacyLayers();
    modernKept();
  }

  /** Every source pixel gets a distinct opaque colour, so any copied pixel can be traced back to where it came from. */
  private static BufferedImage numbered(int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) image.setRGB(x, y, 0xFF000000 | (y << 8) | x);
    }
    return image;
  }

  private static void legacyLimbs() {
    BufferedImage legacy = numbered(64, 32);
    BufferedImage out = SkinImage.toModern(legacy);
    equal(64, out.getWidth(), "converted width");
    equal(64, out.getHeight(), "converted height");
    // right leg (0,16) mirrored into left leg (16,48): faces flip left to right, the two sides trade places
    from(legacy, 7, 16, out, 20, 48, "leg top");
    from(legacy, 4, 19, out, 23, 51, "leg top far corner");
    from(legacy, 11, 16, out, 24, 48, "leg bottom");
    from(legacy, 3, 20, out, 24, 52, "leg outer side becomes inner side");
    from(legacy, 0, 31, out, 27, 63, "leg outer side last row");
    from(legacy, 7, 20, out, 20, 52, "leg front");
    from(legacy, 4, 31, out, 23, 63, "leg front last row");
    from(legacy, 11, 20, out, 16, 52, "leg inner side becomes outer side");
    from(legacy, 15, 20, out, 28, 52, "leg back");
    from(legacy, 12, 31, out, 31, 63, "leg back last row");
    // right arm (40,16) mirrored into left arm (32,48)
    from(legacy, 47, 16, out, 36, 48, "arm top");
    from(legacy, 51, 16, out, 40, 48, "arm bottom");
    from(legacy, 43, 20, out, 40, 52, "arm outer side");
    from(legacy, 47, 20, out, 36, 52, "arm front");
    from(legacy, 51, 20, out, 32, 52, "arm inner side");
    from(legacy, 55, 31, out, 44, 63, "arm back last row");
    // the right limbs and the rest of the top half stay where they were
    from(legacy, 5, 25, out, 5, 25, "right leg untouched");
    from(legacy, 44, 20, out, 44, 20, "right arm untouched");
    from(legacy, 20, 20, out, 20, 20, "body untouched");
  }

  private static void legacyLayers() {
    BufferedImage legacy = numbered(64, 32);
    BufferedImage out = SkinImage.toModern(legacy);
    // a fully solid hat is treated as junk and cleared
    equal(0, alpha(out, 40, 8), "solid legacy hat cleared");
    // overlay slots that a 64x32 skin cannot fill stay see-through
    equal(0, alpha(out, 20, 36), "jacket transparent");
    equal(0, alpha(out, 44, 36), "right sleeve transparent");
    equal(0, alpha(out, 4, 36), "right pants transparent");
    equal(0, alpha(out, 4, 52), "left pants transparent");
    equal(0, alpha(out, 52, 52), "left sleeve transparent");

    // a hat with real see-through pixels is kept, and holes in the face are filled
    BufferedImage hatted = numbered(64, 32);
    hatted.setRGB(33, 9, 0x00123456);
    hatted.setRGB(9, 9, 0x00ABCDEF);
    BufferedImage kept = SkinImage.toModern(hatted);
    equal(255, alpha(kept, 40, 8), "real hat kept");
    equal(0, alpha(kept, 33, 9), "real hat hole kept");
    equal(255, alpha(kept, 9, 9), "face hole filled");
    equal(0xABCDEF, kept.getRGB(9, 9) & 0xFFFFFF, "face hole keeps its colour");
  }

  private static void modernKept() {
    BufferedImage modern = numbered(64, 64);
    modern.setRGB(20, 36, 0x80112233);
    modern.setRGB(33, 1, 0x00000000);
    modern.setRGB(20, 52, 0x00445566);
    BufferedImage out = SkinImage.toModern(modern);
    equal(64, out.getHeight(), "modern height");
    equal(0x80112233, out.getRGB(20, 36), "jacket pixel kept with its alpha");
    from(modern, 52, 52, out, 52, 52, "left sleeve kept");
    from(modern, 4, 52, out, 4, 52, "left pants kept");
    from(modern, 40, 60, out, 40, 60, "left arm not overwritten by mirroring");
    equal(255, alpha(out, 20, 52), "left leg base made opaque");
    equal(255, alpha(out, 40, 8), "hat with a hole kept");
  }

  private static int alpha(BufferedImage image, int x, int y) {
    return image.getRGB(x, y) >>> 24;
  }

  private static void from(BufferedImage source, int sx, int sy, BufferedImage out, int dx, int dy, String what) {
    equal(source.getRGB(sx, sy), out.getRGB(dx, dy), what + " (" + dx + "," + dy + " from " + sx + "," + sy + ")");
  }

  private static void equal(int expected, int actual, String what) {
    if (expected != actual) {
      throw new AssertionError(what + ": expected 0x" + Integer.toHexString(expected) + " but got 0x"
          + Integer.toHexString(actual));
    }
  }
}
