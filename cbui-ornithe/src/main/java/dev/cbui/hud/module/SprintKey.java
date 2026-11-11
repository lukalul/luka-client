package dev.cbui.hud.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Always sprint and Toggle sprint hold the sprint key for the player instead of switching sprinting on themselves. The
 * game's own sprint logic then decides every start and stop at the usual moment in the tick, exactly as when the key is
 * held by hand. Setting the sprint state directly from outside that logic made the movement differ from what anti-cheats
 * predict for a vanilla client (Intave flagged it as "Physics: moved incorrectly").
 */
public final class SprintKey {
  private static boolean held;
  private static boolean wanted;

  private SprintKey() {}

  /** Whether the player is pressing the sprint key with their own hands. */
  static boolean physicallyDown(Minecraft mc) {
    int code = mc.gameSettings.keyBindSprint.getKeyCode();
    try {
      if (code == 0) return false;
      return code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
    } catch (Throwable notReady) {
      return false;
    }
  }

  /** Holds the sprint key (want = true) or gives it back to the real keyboard (want = false). */
  static void hold(Minecraft mc, boolean want) {
    int code = mc.gameSettings.keyBindSprint.getKeyCode();
    if (code == 0) return; // no sprint key bound: nothing to hold
    if (want) {
      KeyBinding.setKeyBindState(code, true);
      held = true;
      wanted = true;
    } else if (held) {
      KeyBinding.setKeyBindState(code, physicallyDown(mc));
      held = false;
    }
  }

  /** End of the HUD tick: when no module held the key this tick (switched off, a screen open), the real keyboard gets it back. */
  public static void endTick(Minecraft mc) {
    if (!wanted) hold(mc, false);
    wanted = false;
  }
}
