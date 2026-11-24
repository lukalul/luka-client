package dev.cbui.feature.tablist;

import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

/**
 * Tab list options: ping as a coloured number, a highlight on the player's own row, an optional tint by ping, and
 * switches for the list's dark background and the light box behind each row.
 *
 * <p>The tab list mixin only passes calls through; the choices are made here. The list draws its rows in the order it
 * measured the names, so the names are noted while it measures ({@link #record}) and the n-th row box drawn belongs to
 * the n-th noted player. Everything runs on the render thread and nothing is allocated per frame.
 */
public final class TabListModule extends FeatureModule {
  /** The colours vanilla draws the list with: the dark background (and header/footer) and each row's light box. */
  public static final int VANILLA_BACKGROUND = 0x80000000, VANILLA_ROW = 0x20FFFFFF;
  private static final int MAX_ROWS = 80;
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

  private static final NetworkPlayerInfo[] rows = new NetworkPlayerInfo[MAX_ROWS];
  private static int recorded, row = -1;
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

  /** The list is about to be drawn: forget last frame's rows. */
  public static void begin() {
    recorded = 0;
    row = -1;
  }

  /** The list measures each player's name in the order it will draw the rows. */
  public static void record(NetworkPlayerInfo info) {
    if (recorded < MAX_ROWS) rows[recorded++] = info;
  }

  /** Width the list keeps for the ping column: room for a number instead of the 10-pixel bars. */
  public static int pingColumn(int vanilla) {
    TabListModule m = active();
    return m != null && m.pingNumber.get() ? vanilla + 12 : vanilla;
  }

  /** Colour for one of the list's boxes; 0 draws nothing. */
  public static int box(int color) {
    TabListModule m = active();
    if (m == null) return color;
    if (color == VANILLA_BACKGROUND) return m.background.get() ? color : 0;
    if (color != VANILLA_ROW) return color;
    row++;
    NetworkPlayerInfo info = row < recorded ? rows[row] : null;
    if (info != null) {
      if (m.highlightMe.get() && isMe(m, info)) return m.highlightColor.get();
      if (m.tintRows.get()) return (m.pingColor(info.getResponseTime()) & 0xFFFFFF) | 0x38000000;
    }
    return m.borders.get() ? color : 0;
  }

  /**
   * The ping bars of the current row are about to be drawn at (x, y), 10 wide. With numbers on, the number is drawn
   * there instead and 0 comes back as the bars' width, so they draw nothing.
   */
  public static int ping(int x, int y, int width) {
    TabListModule m = active();
    if (m == null || !m.pingNumber.get() || row < 0 || row >= recorded || rows[row] == null) return width;
    int ms = rows[row].getResponseTime();
    String text = ms < 0 ? "?" : pingText(ms);
    net.minecraft.client.gui.FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
    font.drawStringWithShadow(text, x + 10 - font.getStringWidth(text), y, ms < 0 ? UNKNOWN : m.pingColor(ms));
    return 0;
  }

  private int pingColor(int ms) {
    if (ms < 0) return UNKNOWN;
    if (ms <= goodPing.get()) return GOOD;
    return ms > badPing.get() ? BAD : OK;
  }

  private static boolean isMe(TabListModule m, NetworkPlayerInfo info) {
    if (m.mc.thePlayer == null || info.getGameProfile() == null) return false;
    UUID id = info.getGameProfile().getId();
    return id != null && id.equals(m.mc.thePlayer.getUniqueID());
  }

  private static String pingText(int ms) {
    if (ms >= PING_TEXT.length) return ms > 9999 ? "9999" : Integer.toString(ms);
    String text = PING_TEXT[ms];
    if (text == null) PING_TEXT[ms] = text = Integer.toString(ms);
    return text;
  }
}
