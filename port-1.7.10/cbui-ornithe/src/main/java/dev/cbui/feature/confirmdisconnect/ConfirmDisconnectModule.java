package dev.cbui.feature.confirmdisconnect;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;

/** Asks "are you sure?" before the pause menu's disconnect/quit button takes effect. */
public final class ConfirmDisconnectModule extends FeatureModule {
  /** What the pause menu should do when its disconnect button is pressed. */
  public enum Response { DISCONNECT_NOW, ARM_BUTTON, OPEN_CONFIRM_SCREEN }

  public final Setting.Toggle useDiscreteConfirmation = add(new Setting.Toggle(
      "Use Discrete Confirmation", false, "Instead of opening a separate screen, the disconnect button turns into an \"Are you sure?\" button first."));
  public final Setting.Toggle enableInSingleplayer = add(new Setting.Toggle(
      "Enable in Singleplayer", true, "Also ask before leaving a singleplayer world."));
  public final Setting.Toggle enableInMultiplayer = add(new Setting.Toggle(
      "Enable in Multiplayer", true, "Also ask before leaving a server."));
  public final Setting.Slider delay = add(new Setting.Slider(
      "Delay", 0f, 0f, 10f, 0.1f, "Seconds the confirm button stays greyed out after the question appears."));
  public final Setting.Toggle confirmOnLeft = add(new Setting.Toggle(
      "Confirm on Left", false, "Swaps the sides: confirm on the left, back on the right."));

  /** In discrete mode: the pause-menu button has already been pressed once and now reads "Are you sure?". */
  public boolean armed;
  /** Client ticks the pause menu has counted since the question appeared. */
  public int ticker;

  public ConfirmDisconnectModule() { super("Confirm Disconnect", true); }

  @Override public String category() { return "UTILITY"; }

  /** The enabled module, or null when it is off or unavailable. */
  public static ConfirmDisconnectModule active() {
    try {
      ConfirmDisconnectModule module = Features.get(ConfirmDisconnectModule.class);
      return module != null && module.enabled() ? module : null;
    } catch (Throwable failure) {
      return null;
    }
  }

  public double delaySeconds() {
    try {
      return delay.get();
    } catch (Throwable failure) {
      return 0;
    }
  }

  public double delayTicks() { return 20.0 * delaySeconds(); }

  /** Decides the reaction to a press of the disconnect button, updating the armed/tick state when a question is started. */
  public Response onDisconnectPressed(boolean singleplayer, boolean menuHasButtons) {
    boolean asks = singleplayer ? enableInSingleplayer.get() : enableInMultiplayer.get();
    if (armed || !asks) return Response.DISCONNECT_NOW;
    ticker = 0;
    if (useDiscreteConfirmation.get() && menuHasButtons) {
      armed = true;
      return Response.ARM_BUTTON;
    }
    return Response.OPEN_CONFIRM_SCREEN;
  }

  /** Counts one pause-menu tick; true once the configured delay has fully passed. */
  public boolean advanceDelay() {
    if (ticker < delayTicks()) {
      ticker++;
      return false;
    }
    return true;
  }
}
