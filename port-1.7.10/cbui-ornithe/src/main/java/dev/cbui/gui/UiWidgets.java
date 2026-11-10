package dev.cbui.gui;

import dev.cbui.font.Fonts;
import dev.cbui.render.Draw;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

/** Small shared widget primitives used by both module screens. */
public final class UiWidgets {
  public static final int RED = 0xFF8942DA,
      GREEN = 0xFF7A55A5,
      PANEL = 0xF218131E,
      FIELD = 0xFF201B27,
      MUTED = 0xFF8E98A8;

  public static void button(
      String label, float x, float y, float w, float h, boolean hover, boolean active) {
    Draw.rounded(x, y, x + w, y + h, 3, active ? RED : hover ? GREEN : FIELD);
    Fonts.regular().drawCentered(label, x + w / 2, y + (h - 9) / 2, 0xFFFFFFFF);
  }

  public static void toggle(float x, float y, boolean on, boolean hover) {
    Draw.rounded(x, y, x + 28, y + 14, 7, on ? RED : hover ? 0xFF596170 : 0xFF444B57);
    Draw.rounded(on ? x + 15 : x + 2, y + 2, on ? x + 26 : x + 13, y + 12, 5, 0xFFFFFFFF);
  }

  /** Original modules-GUI button: dark base, red outline, coloured hover fill rising from below. */
  public static void cbButton(
      String label, float x, float y, float w, float h, boolean hover, boolean active) {
    if (hover) Draw.rounded(x - 2, y - 2, x + w + 2, y + h + 2, 2, RED);
    Draw.rounded(x, y, x + w, y + h, 2, active ? 0xFF49286E : 0xFF1D1A20);
    if (hover) Draw.rounded(x, y + h * .42f, x + w, y + h, 1, active ? RED : 0xFF4E287A);
    if (label != null && !label.isEmpty())
      Fonts.bold().drawCentered(label, x + w / 2, y + (h - 9) / 2, 0xBFFFFFFF);
  }

  public static void stateButton(
      String label, float x, float y, float w, float h, boolean on, boolean hover) {
    Draw.rounded(x, y, x + w, y + h, 2, on ? 0xFF6C36A9 : hover ? 0xFF41295D : 0xFF1F1B23);
    Fonts.regular().drawCentered(label, x + w / 2, y + (h - 9) / 2, 0xFFFFFFFF);
  }

  public static void pill(
      String label, float x, float y, float w, float h, boolean active, boolean hover) {
    Draw.rounded(x, y, x + w, y + h, h / 2, active ? RED : hover ? 0xFF312A37 : 0xFF1F1B23);
    Fonts.regular().drawCentered(label, x + w / 2, y + 3, active ? 0xFFFFFFFF : MUTED);
  }

  public static void toggleSwitch(float x, float y, float w, float h, boolean on, boolean hover) {
    Draw.rounded(x, y, x + w, y + h, h / 2, on ? RED : hover ? 0xFF596170 : 0xFF322D39);
    float d = Math.max(6, h - 6);
    float kx = on ? x + w - d - 3 : x + 3;
    Draw.rect(kx, y + 3, kx + d, y + 3 + d, 0xFFFFFFFF);
    String label = on ? "ON" : "OFF";
    float lw = Fonts.bold().getStringWidth(label);
    float zone = w - d - 6;
    float lx = on ? x + 3 + (zone - lw) / 2 : x + d + 3 + (zone - lw) / 2;
    Fonts.bold().drawString(label, lx, y + (h - 9) / 2, 0xFFFFFFFF);
  }

  public static void slider(float x, float y, float w, float progress, boolean hover) {
    progress = Math.max(0, Math.min(1, progress));
    Draw.rounded(x, y, x + w, y + 2, 1, hover ? 0xFF687180 : 0xFF312A39);
    Draw.rounded(x, y, x + w * progress, y + 2, 1, RED);
    Draw.rounded(x + w * progress - 3, y - 2, x + w * progress + 3, y + 4, 3, 0xFF77A7FF);
    Draw.rounded(
        x + w * progress - 1.5f, y - .5f, x + w * progress + 1.5f, y + 2.5f, 1.5f, 0xFFFFFFFF);
  }

  public static void choice(String value, float x, float y, float w, float h, boolean hover) {
    Draw.rounded(x, y, x + w, y + h, 2, hover ? 0xFF2C2533 : FIELD);
    Fonts.bold().drawString("<", x + 5, y + 5, MUTED);
    Fonts.regular().drawCentered(value, x + w / 2, y + 5, 0xFFFFFFFF);
    Fonts.bold().drawString(">", x + w - 10, y + 5, MUTED);
  }

  public static void field(String value, float x, float y, float w, float h, boolean focused) {
    Draw.rounded(x, y, x + w, y + h, 2, focused ? 0xFF2C2533 : FIELD);
    if (focused) {
      Draw.rect(x, y, x + w, y + 1, RED);
      Draw.rect(x, y + h - 1, x + w, y + h, RED);
    }
    Fonts.regular().drawString(value, x + 4, y + 5, 0xFFFFFFFF);
  }

  /** Menu zoom (framebuffer pixels per menu pixel); set by the open menu screen. */
  public static float zoom = 1f;

  public static void beginClip(int x, int y, int w, int h) {
    int scale =
        dev.luka.compat.Compat.scaled(Minecraft.getMinecraft()).getScaleFactor();
    GL11.glEnable(GL11.GL_SCISSOR_TEST);
    GL11.glScissor(
        x * scale,
        Minecraft.getMinecraft().displayHeight - (y + h) * scale,
        Math.max(0, w * scale),
        Math.max(0, h * scale));
  }

  /** Clip in raw framebuffer pixels (the screen is laid out in pixels, like the original, not in GUI units). */
  public static void beginClipPx(int x, int y, int w, int h) {
    GL11.glEnable(GL11.GL_SCISSOR_TEST);
    // menu coordinates are zoomed pixels: convert to framebuffer pixels
    int zx = Math.round(x * zoom), zy = Math.round(y * zoom), zw = Math.round(w * zoom), zh = Math.round(h * zoom);
    GL11.glScissor(zx, Minecraft.getMinecraft().displayHeight - (zy + zh), Math.max(0, zw), Math.max(0, zh));
  }

  public static void endClip() {
    GL11.glDisable(GL11.GL_SCISSOR_TEST);
  }

  private UiWidgets() {}
}
