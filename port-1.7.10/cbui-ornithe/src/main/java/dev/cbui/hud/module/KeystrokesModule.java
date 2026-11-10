package dev.cbui.hud.module;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;
import dev.cbui.render.Draw;
import dev.cbui.render.Shapes;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Mouse;

/**
 * Keystrokes: WASD plus optional mouse buttons and space bar. Each key is its own
 * rounded box using the module's background colour; a pressed key fades to the pressed colour.
 */
public final class KeystrokesModule extends AbstractHudModule {
  private static final int KEY = 20, GAP = 2, ROW = 18;
  private final Setting.Color pressedColor = grouped("Pressed keys", adv(new Setting.Color("Pressed color", 0xD0FFFFFF, "Key background while the key is held")));
  private final Setting.Color pressedText = grouped("Pressed keys", adv(new Setting.Color("Pressed text color", 0xFF000000, "Letter colour while the key is held")));
  private final Setting.Toggle mouseButtons = grouped("Keys shown", add(new Setting.Toggle("Show mouse buttons", true, "Add left and right click boxes with your CPS")));
  private final Setting.Toggle spaceBar = grouped("Keys shown", add(new Setting.Toggle("Show space bar", false, "Add a jump bar under the movement keys")));
  private final float[] fade = new float[7];
  private long last;

  public KeystrokesModule() {
    super("Keystrokes", false, Anchor.BOTTOM_RIGHT, -5, -5, 3 * KEY + 2 * GAP, 2 * ROW + GAP);
    background.set(false);
  }

  @Override protected boolean padded() { return false; }

  @Override public void tick() {
    int rows = 2 + (mouseButtons.get() ? 1 : 0);
    float h = rows * ROW + (rows - 1) * GAP + (spaceBar.get() ? GAP + 10 : 0);
    size(3 * KEY + 2 * GAP, h);
  }

  @Override protected void draw(float partialTicks, boolean editor) {
    long now = System.nanoTime();
    float step = last == 0 ? 1 : Math.min(1, (now - last) / 80_000_000f);
    last = now;
    int x2 = KEY + GAP, x3 = 2 * (KEY + GAP);
    key(0, "W", x2, 0, KEY, ROW, mc.gameSettings.keyBindForward, editor, step);
    key(1, "A", 0, ROW + GAP, KEY, ROW, mc.gameSettings.keyBindLeft, editor, step);
    key(2, "S", x2, ROW + GAP, KEY, ROW, mc.gameSettings.keyBindBack, editor, step);
    key(3, "D", x3, ROW + GAP, KEY, ROW, mc.gameSettings.keyBindRight, editor, step);
    float y = 2 * (ROW + GAP);
    if (mouseButtons.get()) {
      float half = (contentWidth() - GAP) / 2f;
      box(4, "LMB", 0, y, half, ROW, Mouse.isButtonDown(0), step);
      box(5, "RMB", half + GAP, y, half, ROW, Mouse.isButtonDown(1), step);
      y += ROW + GAP;
    }
    if (spaceBar.get()) {
      boolean down = mc.gameSettings.keyBindJump.getIsKeyPressed();
      float t = animate(6, down, step);
      int fill = Draw.lerpColor(backgroundColor.get(), pressedColor.get(), t);
      Draw.rounded(0, y, contentWidth(), y + 10, radius.get(), fill);
      if (border.get()) Shapes.roundedOutline(0, y, contentWidth(), y + 10, radius.get(), 1, borderColor.get());
      Draw.rect(contentWidth() / 2f - 10, y + 4.5f, contentWidth() / 2f + 10, y + 5.5f,
          Draw.lerpColor(textColor.get(), pressedText.get(), t));
    }
  }

  private void key(int i, String label, float x, float y, float w, float h, KeyBinding binding, boolean editor, float step) {
    box(i, label, x, y, w, h, binding.getIsKeyPressed(), step);
  }

  private void box(int i, String label, float x, float y, float w, float h, boolean down, float step) {
    float t = animate(i, down, step);
    Draw.rounded(x, y, x + w, y + h, radius.get(), Draw.lerpColor(backgroundColor.get(), pressedColor.get(), t));
    if (border.get()) Shapes.roundedOutline(x, y, x + w, y + h, radius.get(), 1, borderColor.get());
    text(label, x + (w - textWidth(label)) / 2f, y + (h - lineHeight()) / 2f + 1,
        Draw.lerpColor(textColor.get(), pressedText.get(), t));
  }

  private float animate(int i, boolean down, float step) {
    fade[i] = down ? Math.min(1, fade[i] + step) : Math.max(0, fade[i] - step);
    return fade[i];
  }
}
