package dev.cbui.gui;

import dev.cbui.Cbui;
import dev.cbui.font.Fonts;
import dev.cbui.font.LukaFontRenderer;
import dev.cbui.render.Draw;
import dev.cbui.social.LukaNet;
import dev.cbui.social.Ranks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** A conversation with one friend: the messages so far (newest at the bottom) and a box to write the next one. */
public final class MessageScreen extends GuiScreen {
  private static final int MUTED = 0xFFA0A8B5;

  private final GuiScreen parent;
  private final LukaNet.Person friend;
  private GuiTextField field;
  private float px, py, pw, ph;
  private int scroll; // lines scrolled up from the bottom
  private int lastCount = -1;
  private final List<String> lines = new ArrayList<String>();
  private final List<Boolean> mine = new ArrayList<Boolean>();

  public MessageScreen(GuiScreen parent, LukaNet.Person friend) {
    this.parent = parent;
    this.friend = friend;
  }

  @Override public void initGui() {
    Keyboard.enableRepeatEvents(true);
    LukaNet.openChat(friend.uuid);
    pw = 300;
    ph = Math.min(230, height - 20);
    px = (width - pw) / 2f;
    py = (height - ph) / 2f;
    field = new GuiTextField(31, fontRendererObj, (int) px + 14, (int) (py + ph - 28), (int) pw - 90, 12);
    field.setMaxStringLength(LukaNet.MESSAGE_MAX);
    field.setEnableBackgroundDrawing(false);
    field.setTextColor(0xFFFFFFFF);
    field.setFocused(true);
    lastCount = -1;
  }

  @Override public void onGuiClosed() {
    Keyboard.enableRepeatEvents(false);
    LukaNet.openChat("");
  }

  @Override public void updateScreen() {
    if (field != null) field.updateCursorCounter();
  }

  @Override public boolean doesGuiPauseGame() { return false; }

  /** Wraps the messages to the panel width whenever the conversation changes. */
  private void rebuild(LukaFontRenderer font) {
    List<LukaNet.Message> all = LukaNet.conversation(friend.uuid);
    if (all.size() == lastCount) return;
    lastCount = all.size();
    lines.clear();
    mine.clear();
    float width = pw - 36;
    for (LukaNet.Message m : all) {
      boolean fromMe = !m.from.equals(friend.uuid);
      String prefix = fromMe ? "You: " : friend.name + ": ";
      StringBuilder line = new StringBuilder(prefix);
      for (String word : breakLongWords(font, m.text, width - font.getStringWidth(prefix))) {
        String trial = line.length() == prefix.length() ? line + word : line + " " + word;
        if (font.getStringWidth(trial) > width && line.length() > 0 && !line.toString().equals(prefix)) {
          lines.add(line.toString());
          mine.add(fromMe);
          line = new StringBuilder("   " + word);
        } else {
          line = new StringBuilder(trial);
        }
      }
      lines.add(line.toString());
      mine.add(fromMe);
    }
    scroll = 0;
  }

  /** The message's words; a word wider than the space is cut into pieces that fit. */
  private static List<String> breakLongWords(LukaFontRenderer font, String text, float width) {
    List<String> out = new ArrayList<String>();
    for (String word : text.split(" ")) {
      while (word.length() > 1 && font.getStringWidth(word) > width) {
        int cut = word.length() - 1;
        while (cut > 1 && font.getStringWidth(word.substring(0, cut)) > width) cut--;
        out.add(word.substring(0, cut));
        word = word.substring(cut);
      }
      out.add(word);
    }
    return out;
  }

  @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    try {
      if (mc.theWorld == null) drawGradientRect(0, 0, width, height, 0xFF100C13, 0xFF1A1522);
      else drawGradientRect(0, 0, width, height, 0x90000000, 0xC0000000);
      Draw.rounded(px, py, px + pw, py + ph, 6, 0xF0080709);
      LukaFontRenderer font = Fonts.regular();
      int rgb = Ranks.get(friend.rank).color;
      dev.cbui.social.RankIcon.blit(px + 8, py + 7, 14, rgb, 1f);
      Fonts.bold(14).drawStringWithShadow(friend.name, px + 27, py + 8, 0xFF000000 | rgb);
      String state = friend.online ? (friend.server.isEmpty() ? "Online" : "Online  -  " + friend.server) : "Offline";
      font.drawStringWithShadow(state, px + pw - 10 - font.getStringWidth(state), py + 11, friend.online ? 0xFF4DDB6A : MUTED);

      rebuild(font);
      float top = py + 28, bottom = py + ph - 36;
      Draw.rounded(px + 6, top, px + pw - 6, bottom, 4, 0x90140F1C);
      int step = font.lineHeight() + 2;
      int fit = Math.max(1, (int) ((bottom - top - 8) / step));
      int maxScroll = Math.max(0, lines.size() - fit);
      scroll = Math.max(0, Math.min(scroll, maxScroll));
      if (lines.isEmpty()) {
        font.drawCenteredWithShadow("No messages yet. Say hi.", px + pw / 2f, top + 20, MUTED);
      } else {
        int end = lines.size() - scroll;
        int start = Math.max(0, end - fit);
        float y = top + 5;
        for (int i = start; i < end; i++) {
          font.drawStringWithShadow(lines.get(i), px + 12, y, mine.get(i) ? 0xFFD9C2FF : 0xFFFFFFFF);
          y += step;
        }
        if (maxScroll > 0) {
          float track = bottom - top - 8;
          float thumb = Math.max(10, track * fit / lines.size());
          float ty = top + 4 + (track - thumb) * (maxScroll - scroll) / maxScroll;
          Draw.rounded(px + pw - 9, ty, px + pw - 7, ty + thumb, 1, 0xFF8942DA);
        }
      }

      float fy = py + ph - 31;
      Draw.rounded(px + 10, fy, px + pw - 74, fy + 18, 3, 0xFF1A151F);
      field.xPosition = (int) px + 14;
      field.yPosition = (int) fy + 5;
      field.drawTextBox();
      if (field.getText().isEmpty() && !field.isFocused()) font.drawStringWithShadow("Write a message", px + 14, fy + 5, 0xFF6C7380);
      boolean overSend = Draw.inside(mouseX, mouseY, px + pw - 68, fy, 58, 18);
      Draw.rounded(px + pw - 68, fy, px + pw - 10, fy + 18, 3, overSend ? 0xFF7A55A5 : 0xFF4A3070);
      font.drawCenteredWithShadow("SEND", px + pw - 39, fy + 5, 0xFFFFFFFF);
      String note = LukaNet.lastAction();
      if (!note.isEmpty() && !note.equals("...")) font.drawStringWithShadow(note, px + 12, py + ph - 11, 0xFFFF9A9A);
      font.drawStringWithShadow("Esc: back", px + pw - 10 - font.getStringWidth("Esc: back"), py + ph - 11, 0xFF6C7380);
    } catch (Throwable error) {
      Cbui.reportOnce("message screen", error);
      mc.displayGuiScreen(parent);
    }
  }

  private void send() {
    String text = field.getText().trim();
    if (text.isEmpty()) return;
    LukaNet.sendMessage(friend.uuid, text);
    field.setText("");
  }

  @Override protected void mouseClicked(int mx, int my, int button) {
    try {
      if (button != 0) return;
      field.mouseClicked(mx, my, button);
      float fy = py + ph - 31;
      if (Draw.inside(mx, my, px + pw - 68, fy, 58, 18)) send();
    } catch (Throwable error) {
      Cbui.reportOnce("message click", error);
    }
  }

  @Override public void handleMouseInput() {
    super.handleMouseInput();
    int wheel = Mouse.getEventDWheel();
    if (wheel != 0) scroll += wheel > 0 ? 2 : -2;
  }

  @Override protected void keyTyped(char c, int key) {
    if (key == Keyboard.KEY_ESCAPE) {
      mc.displayGuiScreen(parent);
      return;
    }
    if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
      send();
      return;
    }
    field.textboxKeyTyped(c, key);
  }
}
