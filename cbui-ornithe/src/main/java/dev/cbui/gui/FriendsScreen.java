package dev.cbui.gui;

import dev.cbui.Cbui;
import dev.cbui.feature.Features;
import dev.cbui.feature.online.LukaOnlineModule;
import dev.cbui.font.Fonts;
import dev.cbui.font.LukaFontRenderer;
import dev.cbui.render.Draw;
import dev.cbui.social.LukaNet;
import dev.cbui.social.Ranks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * The friends screen: your friends (online first, with the server they are on and a JOIN button), friend requests, and a box to add someone
 * by name. Drawn and hit-tested in one pass: every frame it records where each button is, and a click runs the one under the mouse.
 */
public final class FriendsScreen extends GuiScreen {
  private static final ResourceLocation LOGO = new ResourceLocation("cbui", "logo_white.png");
  private static final int PURPLE = 0xFF8942DA, MUTED = 0xFFA0A8B5, GREEN = 0xFF4DDB6A;
  private static final float ROW = 24;

  private static final class Hit {
    final float x, y, w, h;
    final Runnable action;

    Hit(float x, float y, float w, float h, Runnable action) {
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
      this.action = action;
    }
  }

  private final GuiScreen parent;
  private final List<Hit> hits = new ArrayList<Hit>();
  private GuiTextField nameField;
  private int tab;
  private int firstRow;
  private float px, py, pw, ph;

  public FriendsScreen(GuiScreen parent) {
    this.parent = parent;
  }

  @Override public void initGui() {
    Keyboard.enableRepeatEvents(true);
    LukaNet.fastFriends(true);
    pw = 300;
    ph = Math.min(230, height - 20);
    px = (width - pw) / 2f;
    py = (height - ph) / 2f;
    nameField = new GuiTextField(21, fontRendererObj, (int) px + 12, (int) py + 62, (int) pw - 96, 12);
    nameField.setMaxStringLength(16);
    nameField.setEnableBackgroundDrawing(false);
    nameField.setTextColor(0xFFFFFFFF);
    nameField.setFocused(tab == 2);
  }

  @Override public void onGuiClosed() {
    Keyboard.enableRepeatEvents(false);
    LukaNet.fastFriends(false);
  }

  @Override public void updateScreen() {
    if (nameField != null) nameField.updateCursorCounter();
  }

  @Override public boolean doesGuiPauseGame() { return false; }

  private boolean moduleOn() {
    LukaOnlineModule m = Features.get(LukaOnlineModule.class);
    return m != null && m.enabled();
  }

  @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    try {
      hits.clear();
      if (mc.theWorld == null) drawGradientRect(0, 0, width, height, 0xFF100C13, 0xFF1A1522);
      else drawGradientRect(0, 0, width, height, 0x90000000, 0xC0000000);
      Draw.rounded(px, py, px + pw, py + ph, 6, 0xF0080709);
      LukaFontRenderer font = Fonts.regular();
      dev.cbui.social.RankIcon.blit(px + 8, py + 7, 14, 0xD9C2FF, 1f);
      Fonts.bold(14).drawStringWithShadow("FRIENDS", px + 27, py + 8, 0xFFD9C2FF);
      String status = !moduleOn() ? "Luka Online is off" : LukaNet.status();
      boolean ok = moduleOn() && LukaNet.connected();
      font.drawStringWithShadow(status, px + pw - 10 - font.getStringWidth(status) - 8, py + 11, ok ? GREEN : MUTED);
      Draw.rounded(px + pw - 16, py + 13, px + pw - 10, py + 19, 3, ok ? GREEN : 0xFF5C6370);

      LukaNet.FriendsData data = LukaNet.friends();
      int requests = data.incoming.size();
      int unreadAll = LukaNet.totalUnread();
      String[] names = {"FRIENDS " + data.friends.size() + (unreadAll > 0 ? "  (" + unreadAll + " new)" : ""), "REQUESTS" + (requests > 0 ? " " + requests : ""), "ADD"};
      float tx = px + 8;
      for (int i = 0; i < 3; i++) {
        final int index = i;
        float w = font.getStringWidth(names[i]) + 16;
        button(names[i], tx, py + 28, w, 15, mouseX, mouseY, tab == i, new Runnable() {
          @Override public void run() { setTab(index); }
        });
        tx += w + 3;
      }

      float top = py + 49, bottom = py + ph - 26;
      if (!moduleOn()) {
        font.drawCenteredWithShadow("Switch Luka Online on in the mods menu to use friends.", px + pw / 2f, top + 20, MUTED);
      } else if (tab == 0) {
        drawFriends(data, top, bottom, mouseX, mouseY);
      } else if (tab == 1) {
        drawRequests(data, top, bottom, mouseX, mouseY);
      } else {
        drawAdd(top, mouseX, mouseY);
      }

      String note = LukaNet.lastAction();
      if (!note.isEmpty() && !note.equals("...")) font.drawStringWithShadow(note, px + 10, py + ph - 17, 0xFFD9C2FF);
      button("DONE", px + pw - 62, py + ph - 21, 54, 15, mouseX, mouseY, false, new Runnable() {
        @Override public void run() { close(); }
      });
    } catch (Throwable error) {
      Cbui.reportOnce("friends screen", error);
      close();
    }
  }

  private void setTab(int index) {
    tab = index;
    firstRow = 0;
    if (nameField != null) nameField.setFocused(index == 2);
  }

  private static List<LukaNet.Person> sorted(List<LukaNet.Person> in) {
    List<LukaNet.Person> out = new ArrayList<LukaNet.Person>(in);
    Collections.sort(out, new Comparator<LukaNet.Person>() {
      @Override public int compare(LukaNet.Person a, LukaNet.Person b) {
        if (a.online != b.online) return a.online ? -1 : 1;
        int pa = Ranks.get(a.rank).priority, pb = Ranks.get(b.rank).priority;
        if (pa != pb) return pb - pa;
        return a.name.compareToIgnoreCase(b.name);
      }
    });
    return out;
  }

  private int rowsFit(float top, float bottom) { return Math.max(1, (int) ((bottom - top) / ROW)); }

  private void drawFriends(LukaNet.FriendsData data, float top, float bottom, int mx, int my) {
    List<LukaNet.Person> list = sorted(data.friends);
    if (list.isEmpty()) {
      Fonts.regular().drawCenteredWithShadow(LukaNet.friendsLoaded() ? "No friends yet. Use ADD to send a request." : LukaNet.connected() ? "Loading..." : LukaNet.needsMicrosoftAccount() ? "Friends need a Microsoft account." : "Can't reach luka online right now.", px + pw / 2f, top + 20, MUTED);
      if (!LukaNet.friendsLoaded() && LukaNet.needsMicrosoftAccount()) {
        Fonts.regular().drawCenteredWithShadow("Offline accounts can't sign in to Luka Online.", px + pw / 2f, top + 34, MUTED);
      }
      return;
    }
    int fit = rowsFit(top, bottom);
    firstRow = Math.max(0, Math.min(firstRow, list.size() - fit));
    for (int i = 0; i < fit && firstRow + i < list.size(); i++) {
      final LukaNet.Person p = list.get(firstRow + i);
      float y = top + i * ROW;
      row(p, y, mx, my, p.online ? (p.server.isEmpty() ? "Online" : "Online  -  " + p.server) : "Offline", p.online);
      float right = px + pw - 12;
      final float rowY = y;
      right = rowButton("X", right, rowY, 14, mx, my, true, new Runnable() {
        @Override public void run() { LukaNet.remove(p); }
      });
      if (p.online && !p.server.isEmpty()) {
        right = rowButton("JOIN", right, rowY, 0, mx, my, false, new Runnable() {
          @Override public void run() { join(p.server); }
        });
      }
      int unread = LukaNet.unread(p.uuid);
      if (dev.cbui.Release.MESSAGES) rowButton(unread > 0 ? "MSG " + unread : "MSG", right, rowY, 0, mx, my, unread > 0, new Runnable() {
        @Override public void run() { mc.displayGuiScreen(new MessageScreen(FriendsScreen.this, p)); }
      });
    }
    scrollHint(list.size(), fit, top, bottom);
  }

  private void drawRequests(LukaNet.FriendsData data, float top, float bottom, int mx, int my) {
    List<LukaNet.Person> in = sorted(data.incoming), out = sorted(data.outgoing);
    int total = in.size() + out.size();
    if (total == 0) {
      Fonts.regular().drawCenteredWithShadow("No pending requests.", px + pw / 2f, top + 20, MUTED);
      return;
    }
    int fit = rowsFit(top, bottom);
    firstRow = Math.max(0, Math.min(firstRow, total - fit));
    for (int i = 0; i < fit && firstRow + i < total; i++) {
      int index = firstRow + i;
      boolean incoming = index < in.size();
      final LukaNet.Person p = incoming ? in.get(index) : out.get(index - in.size());
      float y = top + i * ROW;
      row(p, y, mx, my, incoming ? "Wants to be your friend" : "Request sent", false);
      float right = px + pw - 12;
      if (incoming) {
        right = rowButton("DECLINE", right, y, 0, mx, my, true, new Runnable() {
          @Override public void run() { LukaNet.decline(p); }
        });
        rowButton("ACCEPT", right, y, 0, mx, my, false, new Runnable() {
          @Override public void run() { LukaNet.accept(p); }
        });
      } else {
        rowButton("CANCEL", right, y, 0, mx, my, true, new Runnable() {
          @Override public void run() { LukaNet.decline(p); }
        });
      }
    }
    scrollHint(total, fit, top, bottom);
  }

  private void drawAdd(float top, int mx, int my) {
    LukaFontRenderer font = Fonts.regular();
    font.drawStringWithShadow("Add a friend by their Minecraft name. They need to use luka.", px + 12, top + 2, MUTED);
    float fy = top + 18;
    Draw.rounded(px + 10, fy, px + pw - 80, fy + 16, 3, 0xFF1A151F);
    nameField.xPosition = (int) px + 14;
    nameField.yPosition = (int) fy + 4;
    nameField.drawTextBox();
    if (nameField.getText().isEmpty() && !nameField.isFocused()) font.drawStringWithShadow("Player name", px + 14, fy + 4, 0xFF6C7380);
    button("SEND", px + pw - 74, fy, 62, 16, mx, my, false, new Runnable() {
      @Override public void run() { sendRequest(); }
    });
    if (!LukaNet.connected()) font.drawStringWithShadow(LukaNet.needsMicrosoftAccount() ? "Friends need a Microsoft account." : "Not connected to luka online right now.", px + 12, fy + 26, 0xFFFF8A8A);
  }

  private void sendRequest() {
    String name = nameField.getText().trim();
    if (name.isEmpty()) return;
    LukaNet.sendRequest(name);
    nameField.setText("");
  }

  /** One list row: icon, name (in the rank's colour), a second line, plus hover highlight. */
  private void row(LukaNet.Person p, float y, int mx, int my, String second, boolean online) {
    boolean over = Draw.inside(mx, my, px + 6, y, pw - 12, ROW - 2);
    Draw.rounded(px + 6, y, px + pw - 6, y + ROW - 2, 4, over ? 0xB02F2540 : 0x90140F1C);
    int rgb = Ranks.get(p.rank).color, color = 0xFF000000 | rgb;
    Draw.rounded(px + 10, y + 5, px + 24, y + 19, 3, (online ? 0xFF000000 : 0x90000000) | rgb);
    dev.cbui.social.RankIcon.blit(px + 11, y + 6, 12, 0x14101C, online ? 0.95f : 0.5f);
    LukaFontRenderer font = Fonts.regular();
    font.drawStringWithShadow(p.name, px + 30, y + 3, 0xFFFFFFFF);
    float nameW = font.getStringWidth(p.name);
    font.drawStringWithShadow(Ranks.get(p.rank).label, px + 30 + nameW + 5, y + 3, color);
    font.drawStringWithShadow(second, px + 30, y + 13, online ? GREEN : MUTED);
  }

  /** A small button at the right end of a row; returns the x it started at (the next one goes left of it). */
  private float rowButton(String label, float right, float rowY, float fixedWidth, int mx, int my, boolean subtle, Runnable action) {
    float w = fixedWidth > 0 ? fixedWidth : Fonts.regular().getStringWidth(label) + 12;
    float x = right - w;
    button(label, x, rowY + 5, w, 14, mx, my, false, action);
    return x - 3;
  }

  private void button(String label, float x, float y, float w, float h, int mx, int my, boolean active, Runnable action) {
    boolean over = Draw.inside(mx, my, x, y, w, h);
    int idle = active ? 0xFF4A3070 : 0xFF2A2A2A, hot = 0xFF7A55A5;
    int c = over ? hot : idle;
    Draw.gradientH(x, y, x + w / 2f, y + h, Draw.lerpColor(c, 0xFF000000, .12f), c);
    Draw.gradientH(x + w / 2f, y, x + w, y + h, c, Draw.lerpColor(c, 0xFFFFFFFF, .06f));
    Fonts.regular().drawCenteredWithShadow(label, x + w / 2f, y + (h - 8) / 2f, 0xFFFFFFFF);
    hits.add(new Hit(x, y, w, h, action));
  }

  private void scrollHint(int total, int fit, float top, float bottom) {
    if (total <= fit) return;
    float trackH = bottom - top;
    float thumbH = Math.max(12, trackH * fit / total);
    float thumbY = top + (trackH - thumbH) * firstRow / Math.max(1, total - fit);
    Draw.rounded(px + pw - 4, thumbY, px + pw - 2, thumbY + thumbH, 1, 0xFF8942DA);
  }

  private void join(String server) {
    try {
      if (mc.theWorld != null) {
        mc.theWorld.sendQuittingDisconnectingPacket();
        mc.loadWorld(null);
      }
      mc.displayGuiScreen(new GuiConnecting(new net.minecraft.client.gui.GuiMainMenu(), mc, new ServerData("Friend", server, false)));
    } catch (Throwable error) {
      Cbui.reportOnce("friends join", error);
    }
  }

  private void close() {
    mc.displayGuiScreen(parent);
  }

  @Override protected void mouseClicked(int mx, int my, int button) {
    try {
      if (button != 0) return;
      if (tab == 2) nameField.mouseClicked(mx, my, button);
      for (int i = hits.size() - 1; i >= 0; i--) {
        Hit h = hits.get(i);
        if (Draw.inside(mx, my, h.x, h.y, h.w, h.h)) {
          mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0f));
          h.action.run();
          return;
        }
      }
    } catch (Throwable error) {
      Cbui.reportOnce("friends click", error);
    }
  }

  @Override public void handleMouseInput() {
    super.handleMouseInput();
    int wheel = Mouse.getEventDWheel();
    if (wheel != 0) firstRow = Math.max(0, firstRow + (wheel > 0 ? -1 : 1));
  }

  @Override protected void keyTyped(char c, int key) {
    if (key == Keyboard.KEY_ESCAPE) {
      close();
      return;
    }
    if (tab == 2 && nameField != null) {
      if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
        sendRequest();
        return;
      }
      nameField.textboxKeyTyped(c, key);
    }
  }
}
