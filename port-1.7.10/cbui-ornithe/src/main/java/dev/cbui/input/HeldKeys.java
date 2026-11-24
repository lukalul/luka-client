package dev.cbui.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * 1.7.10 lets go of every key binding when a screen opens and only notices a key again when it goes down. When the
 * game takes input back, this gives each keyboard-bound binding the held state the keyboard physically has, so a key
 * held through a screen keeps working. Only the held flag changes, never the press counter, so nothing that reacts to
 * a press (inventory, drop, hotbar, chat) fires again. Mouse-button and unbound bindings are left alone.
 */
public final class HeldKeys {
  /** Highest LWJGL keyboard code; mouse buttons are negative and 0 is unbound. */
  private static final int LAST_KEY = 255;

  private HeldKeys() {}

  /** Call once the game has focus again after a screen closed. */
  public static void matchKeyboard(Minecraft mc) {
    if (mc == null || mc.gameSettings == null || mc.currentScreen != null || !mc.inGameHasFocus) return;
    if (!Keyboard.isCreated()) return;
    KeyBinding[] bindings = mc.gameSettings.keyBindings;
    if (bindings == null) return;
    for (int i = 0; i < bindings.length; i++) {
      KeyBinding binding = bindings[i];
      if (binding == null) continue;
      int code = binding.getKeyCode();
      if (code < 1 || code > LAST_KEY) continue;
      KeyBinding.setKeyBindState(code, Keyboard.isKeyDown(code));
    }
  }
}
