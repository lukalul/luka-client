package dev.cbui.feature.tablist;

import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiPlayerInfo;

/**
 * Tab list options: ping as a coloured number, a highlight on the player's own row, an optional tint by ping, and
 * switches for the list's dark background and the light box behind each row.
 *
 * <p>1.7.10 draws the list inside the in-game overlay, one row per entry of the connection's player list in order, so
 * the n-th row box drawn after the background belongs to the n-th entry. The mixin only passes calls through; the
 * choices are made here, on the render thread, with nothing allocated per frame.
 */
public final class TabListModule extends FeatureModule {
  /** The colours vanilla draws the list with: the dark background and each row's light box. */
  public static final int VANILLA_BACKGROUND = 0x80000000, VANILLA_ROW = 0x20FFFFFF;
  private static final int GOOD = 0xFF55FF55, OK = 0xFFFFFF55, BAD = 0xFFFF5555, UNKNOWN = 0xFFAAAAAA;
  private static volatile TabListModule instance;

  public final Setting.Toggle pingNumber = grouped("Ping", add(new Setting.Toggle(
      "Ping as number", true, "Show each player's ping in ms instead of the signal bars")));
  public final Setting.Slider goodPing = grouped("Ping", add(new Setting.Slider(
      "Good ping", 80, 10, 300, 10, "Ping up to this is green")));
  public final Setting.Slider badPing = grouped("Ping", add(new Setting.Slider(
      "Bad ping", 200, 50, 1000, 10, "Ping above this is red; between good and bad it is yellow")));
  public final Setting.Toggle tintRows = grouped("Rows", add(new Setting.Toggle(
      "Tint rows by ping", false, "Colour each row lightly green, yellow or red by its ping")));
  public final Setting.Toggle highlightMe = grouped("Rows", add(new Setting.Toggle(
      "Highlight my row", true, "Mark your own row in the list")));
  public final Setting.Color highlightColor = grouped("Rows", add(new Setting.Color(
      "Highlight color", 0x708942DA, "Colour of your own row")));
  public final Setting.Toggle background = grouped("Look", add(new Setting.Toggle(
      "List background", true, "The dark box around the whole list")));
  public final Setting.Toggle borders = grouped("Look", add(new Setting.Toggle(
      "Row borders", true, "The light box behind each row that draws the grid")));

  private static int row = -1;
  private static final String[] PING_TEXT = new String[1000];

  public TabListModule() {
    super("Tab List", false);
    instance = this;
  }

  @Override public String category() { return "VISUALS"; }

  private static TabListModule active() {
    TabListModule m = instance;
    return m != null && m.enabled() ? m : null;
  }

  /** Colour for one of the overlay's boxes; 0 draws nothing. Other boxes keep their colour. */
  public static int box(int color) {
    TabListModule m = active();
    if (m == null) return color;
    if (color == VANILLA_BACKGROUND) {
      row = -1; // the background comes first: the rows follow
      return m.background.get() ? color : 0;
    }
    if (color != VANILLA_ROW) return color;
    row++;
    GuiPlayerInfo info = info(m, row);
    if (info != null) {
      if (m.highlightMe.get() && isMe(m, info)) return m.highlightColor.get();
      if (m.tintRows.get()) return (m.pingColor(info.responseTime) & 0xFFFFFF) | 0x38000000;
    }
    return m.borders.get() ? color : 0;
  }

  /**
   * The ping bars of the current row are about to be drawn at (x, y), 10 wide. With numbers on, the number is drawn
   * there instead and 0 comes back as the bars' width, so they draw nothing.
   */
  public static int ping(int x, int y, int width) {
    TabListModule m = active();
    if (m == null || !m.pingNumber.get()) return width;
    GuiPlayerInfo info = info(m, row);
    if (info == null) return width;
    int ms = info.responseTime;
    String text = ms < 0 ? "?" : pingText(ms);
    net.minecraft.client.gui.FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
    font.drawStringWithShadow(text, x + 10 - font.getStringWidth(text), y, ms < 0 ? UNKNOWN : m.pingColor(ms));
    return 0;
  }

  private static GuiPlayerInfo info(TabListModule m, int index) {
    if (index < 0 || m.mc.thePlayer == null || m.mc.thePlayer.sendQueue == null) return null;
    List<?> list = m.mc.thePlayer.sendQueue.playerInfoList;
    if (list == null || index >= list.size()) return null;
    Object entry = list.get(index);
    return entry instanceof GuiPlayerInfo ? (GuiPlayerInfo) entry : null;
  }

  private int pingColor(int ms) {
    if (ms < 0) return UNKNOWN;
    if (ms <= goodPing.get()) return GOOD;
    return ms > badPing.get() ? BAD : OK;
  }

  /** 1.7.10's list only carries names: the row whose name is the player's own. */
  private static boolean isMe(TabListModule m, GuiPlayerInfo info) {
    return info.name != null && m.mc.thePlayer != null && info.name.equals(m.mc.thePlayer.getCommandSenderName());
  }

  private static String pingText(int ms) {
    if (ms >= PING_TEXT.length) return ms > 9999 ? "9999" : Integer.toString(ms);
    String text = PING_TEXT[ms];
    if (text == null) PING_TEXT[ms] = text = Integer.toString(ms);
    return text;
  }
}
