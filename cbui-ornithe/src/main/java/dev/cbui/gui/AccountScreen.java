package dev.cbui.gui;

import dev.cbui.Cbui;
import dev.cbui.account.AccountStore;
import dev.cbui.account.MicrosoftAuth;
import dev.cbui.font.Fonts;
import dev.cbui.render.Draw;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/**
 * Account switcher: pick an account, add a Microsoft account (device-code sign-in, the same flow as the launcher) or an offline
 * name, remove one. The list is shared with the launcher.
 */
public final class AccountScreen extends GuiScreen {
  private static final int ROW = 28, PANEL_W = 270;
  private final GuiScreen parent;
  private AccountStore store;
  private GuiTextField nameField;
  private volatile String message = "";
  private volatile String deviceCode = "", deviceUrl = "";
  private volatile boolean signingIn;
  private volatile int attempt;
  private int top;

  public AccountScreen(GuiScreen parent) {
    this.parent = parent;
  }

  @Override
  public void initGui() {
    try {
      store = new AccountStore();
      Keyboard.enableRepeatEvents(true);
      nameField = new GuiTextField(0, fontRendererObj, width / 2 - PANEL_W / 2 + 10, 0, PANEL_W - 70, 16);
      nameField.setMaxStringLength(16);
      nameField.setFocused(true);
    } catch (Throwable error) {
      Cbui.reportOnce("account screen", error);
      mc.displayGuiScreen(parent);
    }
  }

  @Override
  public void onGuiClosed() {
    Keyboard.enableRepeatEvents(false);
    attempt++;
  }

  private int panelHeight() {
    return 44 + store.all().size() * ROW + 100;
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    try {
      drawGradientRect(0, 0, width, height, 0xB0100C13, 0xE017121C);
      int h = panelHeight();
      int x = width / 2 - PANEL_W / 2;
      top = Math.max(10, height / 2 - h / 2);
      Draw.rounded(x - 1, top - 1, x + PANEL_W + 1, top + h + 1, 8, 0xFF3A2C52);
      Draw.rounded(x, top, x + PANEL_W, top + h, 8, 0xF50B0910);
      Fonts.bold(14).drawString("ACCOUNTS", x + 12, top + 10, 0xFFD9C2FF);
      int y = top + 34;
      for (AccountStore.Account account : store.all()) {
        boolean active = store.isActive(account);
        boolean hover = Draw.inside(mouseX, mouseY, x + 8, y, PANEL_W - 16, ROW - 3);
        Draw.rounded(x + 8, y, x + PANEL_W - 8, y + ROW - 3, 5, active ? 0xFF3B2A57 : hover ? 0xFF2A2037 : 0xFF1B1524);
        if (active) Draw.rect(x + 8, y + 3, x + 10, y + ROW - 6, 0xFF9D4EDD);
        Fonts.regular(13).drawString(account.name, x + 18, y + 4, 0xFFFFFFFF);
        String tag = (account.microsoft() ? "Microsoft" : "Offline") + (active ? " - in use" : "");
        Fonts.regular(9).drawString(tag, x + 18, y + 15, active ? 0xFFB57BFF : 0xFF8C849A);
        if (store.all().size() > 1 && !active) {
          boolean over = Draw.inside(mouseX, mouseY, x + PANEL_W - 28, y + 4, 14, 14);
          Fonts.bold(13).drawString("x", x + PANEL_W - 24, y + 5, over ? 0xFFFF6B8A : 0xFF6F6580);
        }
        y += ROW;
      }
      y += 6;
      if (signingIn) {
        drawSignIn(x, y, mouseX, mouseY);
      } else {
        // add: a name field with ADD for offline accounts, and the Microsoft button
        nameField.yPosition = y + 2;
        nameField.drawTextBox();
        if (nameField.getText().isEmpty()) Fonts.regular(10).drawString("Offline name", x + 16, y + 6, 0xFF5E5670);
        boolean addHover = Draw.inside(mouseX, mouseY, x + PANEL_W - 54, y, 46, 20);
        Draw.rounded(x + PANEL_W - 54, y, x + PANEL_W - 8, y + 20, 5, addHover ? 0xFF3A2C52 : 0xFF2A2037);
        Fonts.bold(9).drawCentered("OFFLINE", x + PANEL_W - 31, y + 6, 0xFFD9C2FF);
        boolean msHover = Draw.inside(mouseX, mouseY, x + 8, y + 26, PANEL_W - 16, 20);
        Draw.rounded(x + 8, y + 26, x + PANEL_W - 8, y + 46, 5, msHover ? 0xFFB574F0 : 0xFF9D4EDD);
        Fonts.bold(10).drawCentered("ADD MICROSOFT ACCOUNT", x + PANEL_W / 2, y + 32, 0xFFFFFFFF);
        Fonts.regular(10).drawString(message.isEmpty() ? "Pick an account to play as it" : message, x + 10, y + 52, message.isEmpty() ? 0xFF6F6580 : 0xFFE9DDFB);
      }
      boolean doneHover = Draw.inside(mouseX, mouseY, x + PANEL_W / 2 - 40, top + h - 24, 80, 18);
      Draw.rounded(x + PANEL_W / 2 - 40, top + h - 24, x + PANEL_W / 2 + 40, top + h - 6, 5, doneHover ? 0xFF2F2540 : 0xFF241B33);
      Fonts.bold(10).drawCentered("DONE", x + PANEL_W / 2, top + h - 19, 0xFFD9C2FF);
    } catch (Throwable error) {
      Cbui.reportOnce("account screen draw", error);
      mc.displayGuiScreen(parent);
    }
  }

  private void drawSignIn(int x, int y, int mouseX, int mouseY) {
    String code = deviceCode, url = deviceUrl;
    if (code.isEmpty()) {
      Fonts.regular(11).drawString(message.isEmpty() ? "Contacting Microsoft..." : message, x + 12, y + 8, 0xFFE9DDFB);
    } else {
      Fonts.regular(10).drawString("Open " + url + " and enter:", x + 12, y, 0xFF8C849A);
      dev.cbui.font.LukaFontRenderer big = Fonts.bold(14);
      big.drawString(code, x + PANEL_W / 2f - big.getStringWidth(code) / 2f, y + 16, 0xFFFFFFFF);
      Fonts.regular(9).drawString("The code is on your clipboard. " + message, x + 12, y + 42, 0xFF6F6580);
    }
    boolean over = Draw.inside(mouseX, mouseY, x + PANEL_W - 70, y + 56, 62, 16);
    Draw.rounded(x + PANEL_W - 70, y + 56, x + PANEL_W - 8, y + 72, 5, over ? 0xFF3A2C52 : 0xFF2A2037);
    Fonts.bold(9).drawCentered("CANCEL", x + PANEL_W - 39, y + 60, 0xFFD9C2FF);
  }

  @Override
  protected void mouseClicked(int mouseX, int mouseY, int button) {
    try {
      if (button != 0) return;
      int h = panelHeight();
      int x = width / 2 - PANEL_W / 2;
      int y = top + 34;
      if (Draw.inside(mouseX, mouseY, x + PANEL_W / 2 - 40, top + h - 24, 80, 18)) {
        mc.displayGuiScreen(parent);
        return;
      }
      int listBottom = y + store.all().size() * ROW + 6;
      if (signingIn) {
        if (Draw.inside(mouseX, mouseY, x + PANEL_W - 70, listBottom + 56, 62, 16)) {
          attempt++;
          signingIn = false;
          deviceCode = "";
          message = "Sign-in cancelled";
        }
        return;
      }
      for (AccountStore.Account account : new java.util.ArrayList<>(store.all())) {
        if (store.all().size() > 1 && !store.isActive(account) && Draw.inside(mouseX, mouseY, x + PANEL_W - 28, y + 4, 14, 14)) {
          store.remove(account);
          message = "Removed " + account.name;
          return;
        }
        if (Draw.inside(mouseX, mouseY, x + 8, y, PANEL_W - 16, ROW - 3)) {
          switchTo(account);
          return;
        }
        y += ROW;
      }
      y += 6;
      if (Draw.inside(mouseX, mouseY, x + PANEL_W - 54, y, 46, 20)) {
        addName();
        return;
      }
      if (Draw.inside(mouseX, mouseY, x + 8, y + 26, PANEL_W - 16, 20)) {
        startMicrosoft();
        return;
      }
      nameField.mouseClicked(mouseX, mouseY, button);
    } catch (Throwable error) {
      Cbui.reportOnce("account screen click", error);
    }
  }

  private void switchTo(final AccountStore.Account account) {
    if (!account.microsoft()) {
      message = store.switchTo(account) ? "Playing as " + account.name : "Could not switch the account";
      return;
    }
    message = "Signing in as " + account.name + "...";
    new Thread(() -> message = store.switchToMicrosoft(account), "luka-account-switch").start();
  }

  private void startMicrosoft() {
    final MicrosoftAuth auth = new MicrosoftAuth(store.clientId(), store.liveFlow());
    final int mine = ++attempt;
    signingIn = true;
    deviceCode = "";
    message = "";
    new Thread(() -> {
      try {
        MicrosoftAuth.Session session = auth.signIn((code, url) -> {
          deviceCode = code;
          deviceUrl = url;
          GuiScreen.setClipboardString(code);
          openBrowser(url);
        }, text -> { if (attempt == mine) message = text; }, () -> attempt != mine);
        if (attempt == mine) {
          AccountStore.Account account = store.addMicrosoft(session);
          message = "Playing as " + account.name;
        }
      } catch (Throwable error) {
        if (attempt == mine) message = String.valueOf(error.getMessage());
      } finally {
        if (attempt == mine) {
          deviceCode = "";
          signingIn = false;
        }
      }
    }, "luka-ms-login").start();
  }

  /** The JVM is headless, so java.awt.Desktop is not available: ask Windows to open the address. */
  private static void openBrowser(String url) {
    try {
      new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
    } catch (Throwable ignored) {
      // the address is on screen
    }
  }

  private void addName() {
    String name = nameField.getText().trim().replaceAll("[^A-Za-z0-9_]", "");
    if (name.length() < 3) {
      message = "A name needs 3 to 16 letters, numbers or _";
      return;
    }
    AccountStore.Account account = store.addOffline(name);
    store.switchTo(account);
    nameField.setText("");
    message = "Playing as " + account.name;
  }

  @Override
  protected void keyTyped(char typedChar, int keyCode) {
    if (keyCode == Keyboard.KEY_ESCAPE) {
      mc.displayGuiScreen(parent);
      return;
    }
    if (signingIn) return;
    if (keyCode == Keyboard.KEY_RETURN) {
      addName();
      return;
    }
    nameField.textboxKeyTyped(typedChar, keyCode);
  }

  @Override
  public void updateScreen() {
    if (nameField != null) nameField.updateCursorCounter();
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
