package dev.cbui.feature.leaveguard;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import dev.cbui.mixin.LeaveGuardMenuInvoker;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

/**
 * Asks before the pause menu's leave button ("Disconnect" / "Save and Quit to Title") acts: either on a small screen
 * of its own or by turning the button into a question that needs a second press. Only the button with id 1 is
 * touched; every other pause-menu button keeps working as before.
 */
public final class LeaveGuardModule extends FeatureModule {
  /** Id vanilla gives the leave button of the pause menu. */
  public static final int LEAVE_ID = 1;

  private final Setting.Toggle onButton = add(new Setting.Toggle(
      "Ask on the button itself", false,
      "Turn the leave button into a second-click question instead of opening a screen"));
  private final Setting.Toggle inWorlds = add(new Setting.Toggle(
      "Ask in singleplayer", true, "Confirm before leaving a local world"));
  private final Setting.Toggle onServers = add(new Setting.Toggle(
      "Ask on servers", true, "Confirm before disconnecting from a server"));
  private final Setting.Slider delay = add(new Setting.Slider(
      "Confirm delay", 0, 0, 10, 0.1f, "Seconds before the confirm button can be pressed"));
  private final Setting.Toggle leaveFirst = add(new Setting.Toggle(
      "Leave button first", false, "Put the leave button on the left and the back button on the right"));

  /** Pause menu whose leave button currently shows the question, or null. */
  private GuiScreen questionMenu;
  private GuiButton questionButton;
  private int questionWait;
  /** Set while vanilla's own leave action runs after the player confirmed, so the hook lets it through. */
  private boolean confirmed;

  public LeaveGuardModule() { super("Confirm Disconnect", true); }

  @Override public String category() { return "UTILITY"; }

  /** The module when it exists and is switched on, otherwise null. */
  public static LeaveGuardModule active() {
    try {
      LeaveGuardModule module = Features.get(LeaveGuardModule.class);
      return module != null && module.enabled() ? module : null;
    } catch (Throwable failure) {
      return null;
    }
  }

  /** The module even while switched off (to clear its state), or null. */
  public static LeaveGuardModule any() {
    try {
      return Features.get(LeaveGuardModule.class);
    } catch (Throwable failure) {
      return null;
    }
  }

  private boolean inLocalWorld() { return mc.isIntegratedServerRunning(); }

  int waitTicks() { return Math.max(0, Math.round(delay.get() * 20f)); }

  boolean leaveOnLeft() { return leaveFirst.get(); }

  /**
   * The leave button was pressed on a pause menu. Returns true when vanilla must not act now (a question is shown
   * instead), false to let vanilla leave.
   */
  public boolean holdBack(GuiScreen menu, GuiButton button, List<?> buttons) {
    if (confirmed) return false;
    boolean local = inLocalWorld();
    if (local ? !inWorlds.get() : !onServers.get()) return false;
    if (onButton.get() && buttons != null && !buttons.isEmpty()) {
      if (questionMenu == menu && questionButton == button) {
        if (questionWait > 0) return true;
        leaveNow(menu, button);
        return true;
      }
      ask(menu, button, local);
      return true;
    }
    mc.displayGuiScreen(new LeaveGuardScreen(this, menu, button, local));
    return true;
  }

  private void ask(GuiScreen menu, GuiButton button, boolean local) {
    questionMenu = menu;
    questionButton = button;
    questionWait = waitTicks();
    button.displayString = EnumChatFormatting.RED + (local ? "Really save and quit?" : "Really disconnect?");
    button.enabled = questionWait <= 0;
  }

  /** The pause menu was (re)built: its buttons are fresh, so any question on the old one is gone. */
  public void menuRebuilt(GuiScreen menu) {
    questionMenu = null;
    questionButton = null;
    questionWait = 0;
  }

  /** One client tick of the pause menu: counts down the inline delay. */
  public void menuTick(GuiScreen menu) {
    if (questionMenu != menu || questionButton == null || questionWait <= 0) return;
    questionWait--;
    if (questionWait <= 0) questionButton.enabled = true;
  }

  /** Runs vanilla's own leave action of the pause menu without asking again. */
  void leaveNow(GuiScreen menu, GuiButton button) {
    questionMenu = null;
    questionButton = null;
    confirmed = true;
    try {
      ((LeaveGuardMenuInvoker) menu).cbui$press(button);
    } finally {
      confirmed = false;
    }
  }
}
