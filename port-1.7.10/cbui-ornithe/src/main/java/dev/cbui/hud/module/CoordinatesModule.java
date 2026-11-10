package dev.cbui.hud.module;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;
import net.minecraft.util.MathHelper;

/**
 * Position readout. Horizontal is the one-line "(x, y, z)"; vertical stacks X / Y / Z, shows the compass facing, and marks
 * each axis with + or - for the way that coordinate changes when you walk (or look up/down for Y) the way you are facing.
 */
public final class CoordinatesModule extends AbstractHudModule {
  private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
  private static final int UP = 0xFF55FF55, DOWN = 0xFFFF6666;

  private final Setting.Choice layout = grouped("Display", add(Setting.tip(new Setting.Choice("Layout", "Horizontal", "Horizontal", "Vertical"), "One line (x, y, z) or three stacked lines with a compass")));
  private final Setting.Toggle facing = grouped("Display", add(new Setting.Toggle("Show facing", true, "Compass letter under the coordinates (vertical layout)")));
  private final Setting.Toggle trend = grouped("Display", add(new Setting.Toggle("Show +/- trend", true, "A + or - after each axis: does that number go up or down if you walk the way you are facing")));

  private static final String SAMPLE = "(-1200, 64, 1200)";
  private int x, y, z;
  private int dirX, dirY, dirZ; // -1, 0, +1
  private int compass;
  private String line = "";
  private int lastKey = Integer.MIN_VALUE;

  public CoordinatesModule() { super("Coordinates", false, Anchor.TOP_LEFT, 5, 27, 90, 11); }

  private boolean vertical() { return "Vertical".equals(layout.get()); }

  @Override public void tick() {
    if (mc.thePlayer != null) {
      x = MathHelper.floor_double(mc.thePlayer.posX);
      y = MathHelper.floor_double(mc.thePlayer.boundingBox.minY);
      z = MathHelper.floor_double(mc.thePlayer.posZ);
      double yaw = Math.toRadians(mc.thePlayer.rotationYaw), pitch = Math.toRadians(mc.thePlayer.rotationPitch);
      double fx = -Math.sin(yaw) * Math.cos(pitch), fz = Math.cos(yaw) * Math.cos(pitch), fy = -Math.sin(pitch);
      dirX = sign(fx);
      dirZ = sign(fz);
      dirY = sign(fy);
      double deg = (MathHelper.wrapAngleTo180_double(mc.thePlayer.rotationYaw) + 180 + 22.5) % 360;
      compass = (int) (deg / 45) % 8;
    }
    if (!vertical()) {
      String s = "(" + x + ", " + y + ", " + z + ")";
      if (!s.equals(line)) line = s;
    }
    size(vertical() ? Math.max(textWidth("X: -1200  +"), facing.get() ? textWidth("SW") : 0) : textWidth(editor0(line)),
        vertical() ? linesHeight(facing.get() ? 4 : 3) : lineHeight());
  }

  private static String editor0(String s) { return s.isEmpty() ? SAMPLE : s; }

  private static int sign(double v) { return v > 0.35 ? 1 : v < -0.35 ? -1 : 0; }

  @Override protected void draw(float partialTicks, boolean editor) {
    if (!vertical()) {
      text(editor0(line), 0, 0, textColor.get());
      return;
    }
    int step = lineStep();
    axis("X", editor ? -1200 : x, editor ? 1 : dirX, 0);
    axis("Y", editor ? 64 : y, editor ? 0 : dirY, step);
    axis("Z", editor ? 1200 : z, editor ? -1 : dirZ, step * 2);
    if (facing.get()) text(editor ? "N" : COMPASS[compass], 0, step * 3, textColor.get());
  }

  private void axis(String name, int value, int dir, int yPos) {
    String s = name + ": " + value;
    text(s, 0, yPos, textColor.get());
    if (trend.get() && dir != 0) text(dir > 0 ? "+" : "-", textWidth(s) + textWidth(" "), yPos, dir > 0 ? UP : DOWN);
  }
}
