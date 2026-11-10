package dev.cbui.hud.module;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;
import net.minecraft.util.MathHelper;

/** Compass strip: the facing in the middle, the neighbouring directions dimmed on each side. */
public final class DirectionModule extends AbstractHudModule {
  private static final String[] DIRECTIONS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
  private int index;

  public DirectionModule() { super("Direction", false, Anchor.TOP_CENTER, 0, 5, 60, 11); }

  @Override public void tick() {
    if (mc.thePlayer == null) return;
    double yaw = (MathHelper.wrapAngleTo180_double(mc.thePlayer.rotationYaw) + 202.5) % 360;
    if (yaw < 0) yaw += 360;
    index = (int) (yaw / 45) % 8;
    size(Math.max(60, textWidth("SW") * 3 + 24), lineHeight());
  }

  @Override protected void draw(float partialTicks, boolean editor) {
    int i = editor ? 7 : index;
    float w = contentWidth();
    String left = DIRECTIONS[(i + 7) % 8], mid = DIRECTIONS[i], right = DIRECTIONS[(i + 1) % 8];
    int dim = (textColor.get() & 0xFFFFFF) | ((textColor.get() >>> 24) * 9 / 16) << 24;
    text(left, 0, 0, dim);
    centered(mid, 0);
    text(right, w - textWidth(right), 0, dim);
  }
}
