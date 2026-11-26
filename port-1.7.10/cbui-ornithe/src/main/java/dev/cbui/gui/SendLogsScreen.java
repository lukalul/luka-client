package dev.cbui.gui;

import dev.cbui.Cbui;
import dev.cbui.font.Fonts;
import dev.cbui.font.LukaFontRenderer;
import dev.cbui.render.Draw;
import dev.cbui.report.LogSender;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/**
 * "Send logs" (luka's title screen, bottom right): the player ticks what to send (LogSender.Part), may write what went
 * wrong, and presses SEND; the zip goes to luka's team (api.luka.wtf, then Discord). Shows the report id to quote.
 */
public final class SendLogsScreen extends GuiScreen {
  private static final int MUTED = 0xFFA0A8B5;
  private static final int ROW = 13;

  private final GuiScreen parent;
  private final Set<LogSender.Part> picked = EnumSet.allOf(LogSender.Part.class);
  private GuiTextField note;
  private float px, py, pw, ph;
  private String gpu = "";
  private volatile boolean sending;
  private volatile String status = "";
  private volatile int statusColor = MUTED;

  public SendLogsScreen(GuiScreen parent) {
    this.parent = parent;
  }

  @Override public void initGui() {
    Keyboard.enableRepeatEvents(true);
    pw = 320;
    ph = Math.min(249, height - 12);
    px = (width - pw) / 2f;
    py = (height - ph) / 2f;
    note = new GuiTextField(fontRendererObj, 0, 0, (int) pw - 32, 12);
    note.setMaxStringLength(300);
    note.setEnableBackgroundDrawing(false);
    note.setTextColor(0xFFFFFFFF);
    try {
      gpu = String.valueOf(org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
    } catch (Throwable ignored) {
      gpu = "";
    }
  }

  @Override public void onGuiClosed() {
    Keyboard.enableRepeatEvents(false);
  }

  @Override public void updateScreen() {
    if (note != null) note.updateCursorCounter();
  }

  @Override public boolean doesGuiPauseGame() { return false; }

  private float rowsTop() { return py + 45; }

  private float noteY() { return rowsTop() + LogSender.Part.values().length * ROW + 15; }

  private float buttonsY() { return py + ph - 30; }

  @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    try {
      if (mc.theWorld == null) drawGradientRect(0, 0, width, height, 0xFF100C13, 0xFF1A1522);
      else drawGradientRect(0, 0, width, height, 0x90000000, 0xC0000000);
      Draw.rounded(px, py, px + pw, py + ph, 6, 0xF0080709);
      LukaFontRenderer font = Fonts.regular();
      Fonts.bold(14).drawStringWithShadow("Send logs to luka", px + 12, py + 9, 0xFFE9DDFB);
      font.drawStringWithShadow("Untick what you do not want to send.", px + 12, py + 24, MUTED);
      font.drawStringWithShadow("Never sent: account details, passwords, game chat.", px + 12, py + 33, MUTED);
      float y = rowsTop();
      for (LogSender.Part part : LogSender.Part.values()) {
        boolean on = picked.contains(part);
        boolean over = Draw.inside(mouseX, mouseY, px + 10, y - 2, pw - 20, ROW - 1);
        if (over) Draw.rounded(px + 8, y - 3, px + pw - 8, y + ROW - 4, 3, 0x40FFFFFF);
        Draw.rounded(px + 14, y, px + 23, y + 9, 2, on ? 0xFF8942DA : 0xFF2A2233);
        if (on) Draw.rounded(px + 16.5f, y + 2.5f, px + 20.5f, y + 6.5f, 1, 0xFFFFFFFF);
        font.drawStringWithShadow(part.label, px + 30, y + 1, on ? 0xFFFFFFFF : 0xFF7C8492);
        y += ROW;
      }
      float ny = noteY();
      font.drawStringWithShadow("What went wrong? (optional)", px + 12, ny - 11, MUTED);
      Draw.rounded(px + 10, ny, px + pw - 10, ny + 18, 3, 0xFF1A151F);
      dev.luka.compat.Compat.setPosition(note, (int) px + 16, (int) ny + 5);
      note.drawTextBox();
      float by = buttonsY();
      boolean canSend = !sending;
      boolean overSend = canSend && Draw.inside(mouseX, mouseY, px + pw - 90, by, 80, 20);
      Draw.rounded(px + pw - 90, by, px + pw - 10, by + 20, 3, !canSend ? 0xFF2A2233 : overSend ? 0xFF7A55A5 : 0xFF4A3070);
      font.drawCenteredWithShadow(sending ? "SENDING..." : "SEND", px + pw - 50, by + 6, canSend ? 0xFFFFFFFF : 0xFF7C8492);
      boolean overBack = Draw.inside(mouseX, mouseY, px + pw - 176, by, 80, 20);
      Draw.rounded(px + pw - 176, by, px + pw - 96, by + 20, 3, overBack ? 0xFF3A3046 : 0xFF241D2C);
      font.drawCenteredWithShadow("BACK", px + pw - 136, by + 6, 0xFFFFFFFF);
      if (!status.isEmpty()) {
        float sy = ny + 22;
        for (String line : wrap(font, status, pw - 24)) {
          font.drawStringWithShadow(line, px + 12, sy, statusColor);
          sy += 9;
        }
      }
    } catch (Throwable error) {
      Cbui.reportOnce("send logs screen", error);
      mc.displayGuiScreen(parent);
    }
  }

  private static List<String> wrap(LukaFontRenderer font, String text, float width) {
    List<String> out = new ArrayList<String>();
    StringBuilder line = new StringBuilder();
    for (String word : text.split(" ")) {
      String trial = line.length() == 0 ? word : line + " " + word;
      if (font.getStringWidth(trial) > width && line.length() > 0) {
        out.add(line.toString());
        line = new StringBuilder(word);
      } else {
        line = new StringBuilder(trial);
      }
    }
    if (line.length() > 0) out.add(line.toString());
    return out.size() > 2 ? out.subList(0, 2) : out;
  }

  private void send() {
    if (sending) return;
    sending = true;
    status = "Sending...";
    statusColor = MUTED;
    final String text = note.getText();
    final Set<LogSender.Part> parts = EnumSet.noneOf(LogSender.Part.class);
    parts.addAll(picked);
    final String renderer = classExists("yt.ren.astatine.Native") ? "vulkan" : "normal";
    final List<String> mods = new ArrayList<String>();
    try {
      for (net.fabricmc.loader.api.ModContainer m : net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()) {
        mods.add(m.getMetadata().getId() + " " + m.getMetadata().getVersion().getFriendlyString());
      }
      java.util.Collections.sort(mods);
    } catch (Throwable ignored) {
      // not on Fabric's loader: no mod list
    }
    final java.io.File game = mc.mcDataDir;
    final String card = gpu;
    Thread t = new Thread(new Runnable() {
      @Override public void run() {
        try {
          LogSender sender = new LogSender(game);
          byte[] zip = sender.build(text, parts, renderer, card, mods);
          String id = sender.send(zip);
          status = "Sent. Your report id is " + id + ". Quote it to luka's team on Discord.";
          statusColor = 0xFF4DDB6A;
        } catch (Throwable e) {
          status = "Could not send the logs: " + (e.getMessage() == null ? e.toString() : e.getMessage());
          statusColor = 0xFFFF8A8A;
        } finally {
          sending = false;
        }
      }
    }, "luka-send-logs");
    t.setDaemon(true);
    t.start();
  }

  private static boolean classExists(String name) {
    try {
      Class.forName(name, false, SendLogsScreen.class.getClassLoader());
      return true;
    } catch (Throwable e) {
      return false;
    }
  }

  @Override protected void mouseClicked(int mx, int my, int button) {
    try {
      if (button != 0) return;
      note.mouseClicked(mx, my, button);
      float y = rowsTop();
      for (LogSender.Part part : LogSender.Part.values()) {
        if (Draw.inside(mx, my, px + 10, y - 2, pw - 20, ROW - 1)) {
          if (!picked.remove(part)) picked.add(part);
          return;
        }
        y += ROW;
      }
      float by = buttonsY();
      if (Draw.inside(mx, my, px + pw - 90, by, 80, 20)) send();
      else if (Draw.inside(mx, my, px + pw - 176, by, 80, 20)) mc.displayGuiScreen(parent);
    } catch (Throwable error) {
      Cbui.reportOnce("send logs click", error);
    }
  }

  @Override protected void keyTyped(char c, int key) {
    if (key == Keyboard.KEY_ESCAPE) {
      mc.displayGuiScreen(parent);
      return;
    }
    note.textboxKeyTyped(c, key);
  }
}
