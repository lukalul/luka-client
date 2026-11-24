package dev.cbui.feature.crosshair;

/** Pure pixel work for the crosshair: built-in shapes on a cell grid, the low-health overlay and the outline. */
final class CrosshairShapes {
  static final int CROSS = 0;
  static final int DIAGONAL = 1;
  static final int DOT = 2;
  static final int CIRCLE = 3;
  static final int BOX = 4;

  private CrosshairShapes() {}

  /** The built-in shape of a profile as ARGB cells; 0 is an empty cell. Imported art falls back to a cross. */
  static int[] grid(CrosshairSet p, int cells) {
    return grid(p.shapeIndex(), cells, Math.round(p.reach.get()), Math.round(p.lineWidth.get()),
        Math.round(p.gap.get()), p.keepMiddle.get(), p.colour.get());
  }

  static int[] grid(int shape, int cells, int reachSetting, int width, int gap, boolean keepMiddle, int colour) {
    int[] grid = new int[cells * cells];
    int mid = cells / 2;
    // Lines are centred on the middle cell, so they are always an odd number of cells wide.
    int half = Math.max(0, width) / 2;
    int reach = Math.max(0, Math.min(reachSetting, mid));
    int hole = gap > 0 ? half + gap - 1 : -1;
    boolean cross = shape != DIAGONAL && shape != DOT && shape != CIRCLE && shape != BOX;
    for (int y = 0; y < cells; y++) {
      for (int x = 0; x < cells; x++) {
        int dx = x - mid, dy = y - mid;
        int ax = Math.abs(dx), ay = Math.abs(dy);
        int ring = Math.max(ax, ay);
        boolean on;
        if (cross || shape == DIAGONAL) {
          if (cross) on = ay <= half && ax <= reach || ax <= half && ay <= reach;
          else on = (Math.abs(dx - dy) <= half || Math.abs(dx + dy) <= half) && ring <= reach;
          if (on && ring <= hole) on = false;
          if (keepMiddle && ax <= half && ay <= half) on = true;
        } else if (shape == DOT) {
          double radius = half + 0.5;
          on = dx * dx + dy * dy <= radius * radius;
        } else if (shape == CIRCLE) {
          on = Math.abs(Math.sqrt(dx * dx + dy * dy) - reach) <= half + 0.5;
        } else {
          on = Math.abs(ring - reach) <= half;
        }
        if (on) grid[y * cells + x] = colour;
      }
    }
    return grid;
  }

  /** Lays a colour over every filled cell; its alpha says how strongly. The cell's own alpha stays. */
  static void overlay(int[] grid, int colour) {
    float k = (colour >>> 24) / 255F;
    int r = colour >> 16 & 255, g = colour >> 8 & 255, b = colour & 255;
    for (int i = 0; i < grid.length; i++) {
      int c = grid[i];
      if ((c >>> 24) == 0) continue;
      int cr = Math.round((c >> 16 & 255) + (r - (c >> 16 & 255)) * k);
      int cg = Math.round((c >> 8 & 255) + (g - (c >> 8 & 255)) * k);
      int cb = Math.round((c & 255) + (b - (c & 255)) * k);
      grid[i] = c & 0xFF000000 | cr << 16 | cg << 8 | cb;
    }
  }

  /**
   * An outline {@code radius} pixels wide around the covered pixels, never on top of them: each pixel takes the
   * strongest cover within the radius, less its own cover.
   */
  static int[] outline(float[] cover, int side, int radius, int colour) {
    float[] rows = new float[cover.length];
    for (int y = 0; y < side; y++) {
      for (int x = 0; x < side; x++) {
        float best = 0F;
        int from = Math.max(0, x - radius), to = Math.min(side - 1, x + radius);
        for (int i = from; i <= to; i++) best = Math.max(best, cover[y * side + i]);
        rows[y * side + x] = best;
      }
    }
    int[] out = new int[cover.length];
    float alpha = (colour >>> 24) / 255F;
    int rgb = colour & 0xFFFFFF;
    for (int y = 0; y < side; y++) {
      for (int x = 0; x < side; x++) {
        float best = 0F;
        int from = Math.max(0, y - radius), to = Math.min(side - 1, y + radius);
        for (int j = from; j <= to; j++) best = Math.max(best, rows[j * side + x]);
        int i = y * side + x;
        float a = alpha * best * (1F - cover[i]);
        if (a > 0F) out[i] = Math.round(a * 255F) << 24 | rgb;
      }
    }
    return out;
  }
}
