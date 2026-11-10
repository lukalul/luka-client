package dev.cbui.feature.confirmdisconnect;

import dev.cbui.feature.Features;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;

/** Full-screen question shown instead of leaving straight away; "Back" returns to the screen that opened it. */
public class ConfirmDisconnectScreen extends GuiScreen {
  private static final int CONFIRM_ID = 0, BACK_ID = 1;
  private static final int BUTTON_WIDTH = 100, BUTTON_HEIGHT = 20;

  private final GuiScreen cameFrom;
  private int waited;

  public ConfirmDisconnectScreen(GuiScreen parent) { this.cameFrom = parent; }

  private static ConfirmDisconnectModule module() {
    try {
      return Features.get(ConfirmDisconnectModule.class);
    } catch (Throwable failure) {
      return null;
    }
  }

  private static double delaySeconds() {
    ConfirmDisconnectModule module = module();
    return module == null ? 0 : module.delaySeconds();
  }

  private void setAllEnabled(boolean enabled) {
    for (Object entry : this.buttonList) ((GuiButton) entry).enabled = enabled;
  }

  @Override public void initGui() {
    this.buttonList.clear();
    this.waited = 0;
    ConfirmDisconnectModule module = module();
    boolean confirmFirst = module != null && module.confirmOnLeft.get();
    String confirmLabel = this.mc.isSingleplayer() ? "Quit" : "Disconnect";
    int top = this.height / 4 + 104;
    int leftX = this.width / 2 - 120, rightX = this.width / 2 + 20;
    GuiButton confirm = new GuiButton(CONFIRM_ID, confirmFirst ? leftX : rightX, top, BUTTON_WIDTH, BUTTON_HEIGHT, confirmLabel);
    GuiButton back = new GuiButton(BACK_ID, confirmFirst ? rightX : leftX, top, BUTTON_WIDTH, BUTTON_HEIGHT, "Back");
    if (confirmFirst) {
      this.buttonList.add(confirm);
      this.buttonList.add(back);
    } else {
      this.buttonList.add(back);
      this.buttonList.add(confirm);
    }
    if (delaySeconds() != 0) setAllEnabled(false);
  }

  @Override protected void actionPerformed(GuiButton button) {
    if (button.id != CONFIRM_ID) {
      this.mc.displayGuiScreen(this.cameFrom);
      return;
    }
    boolean wasLocal = this.mc.isIntegratedServerRunning();
    button.enabled = false;
    this.mc.theWorld.sendQuittingDisconnectingPacket();
    this.mc.loadWorld(null);
    GuiScreen next = new GuiMainMenu();
    this.mc.displayGuiScreen(wasLocal ? next : new GuiMultiplayer(next));
  }

  @Override public void updateScreen() {
    super.updateScreen();
    if (this.waited >= 20.0 * delaySeconds()) setAllEnabled(true);
    this.waited++;
  }

  @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    this.drawDefaultBackground();
    String question = this.mc.isSingleplayer() ? "Are you sure you want to quit?" : "Are you sure you want to disconnect?";
    this.drawCenteredString(this.fontRendererObj, question, this.width / 2, this.height / 4 + 8, 0xFFFFFF);
    super.drawScreen(mouseX, mouseY, partialTicks);
  }
}
