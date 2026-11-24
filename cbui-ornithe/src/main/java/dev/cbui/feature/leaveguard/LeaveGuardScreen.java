package dev.cbui.feature.leaveguard;

import dev.cbui.Cbui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/** The small "are you sure" screen shown between the pause menu's leave button and actually leaving. */
final class LeaveGuardScreen extends GuiScreen {
  private static final int BACK = 0;
  private static final int LEAVE = 1;

  private final LeaveGuardModule module;
  private final GuiScreen pauseMenu;
  private final GuiButton pauseLeaveButton;
  private final boolean local;
  private int wait;
  private GuiButton leaveButton;

  LeaveGuardScreen(LeaveGuardModule module, GuiScreen pauseMenu, GuiButton pauseLeaveButton, boolean local) {
    this.module = module;
    this.pauseMenu = pauseMenu;
    this.pauseLeaveButton = pauseLeaveButton;
    this.local = local;
    this.wait = module.waitTicks();
  }

  @Override
  public void initGui() {
    buttonList.clear();
    int top = height / 4 + 36;
    int leftX = width / 2 - 102;
    int rightX = width / 2 + 2;
    boolean leaveLeft = module.leaveOnLeft();
    leaveButton = new GuiButton(LEAVE, leaveLeft ? leftX : rightX, top, 100, 20, local ? "Save and quit" : "Disconnect");
    GuiButton back = new GuiButton(BACK, leaveLeft ? rightX : leftX, top, 100, 20, "Back");
    leaveButton.enabled = wait <= 0;
    buttonList.add(back);
    buttonList.add(leaveButton);
  }

  @Override
  public void updateScreen() {
    if (wait > 0) {
      wait--;
      if (wait <= 0 && leaveButton != null) leaveButton.enabled = true;
    }
  }

  @Override
  protected void actionPerformed(GuiButton button) {
    if (button.id == BACK) {
      mc.displayGuiScreen(pauseMenu);
    } else if (button.id == LEAVE && wait <= 0) {
      try {
        module.leaveNow(pauseMenu, pauseLeaveButton);
      } catch (Throwable error) {
        Cbui.reportOnce("Confirm Disconnect leave", error);
      }
    }
  }

  @Override
  protected void keyTyped(char typed, int keyCode) {
    if (keyCode == 1) mc.displayGuiScreen(pauseMenu);
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    drawDefaultBackground();
    String question = local ? "Save and leave this world?" : "Leave this server?";
    drawCenteredString(fontRendererObj, question, width / 2, height / 4 + 10, 0xFFFFFF);
    super.drawScreen(mouseX, mouseY, partialTicks);
  }
}
