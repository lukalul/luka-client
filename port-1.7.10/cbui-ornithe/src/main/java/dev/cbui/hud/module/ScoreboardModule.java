package dev.cbui.hud.module;

import dev.cbui.Cbui;
import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;
import dev.cbui.hud.LayoutMath;
import dev.cbui.hud.Setting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;

/**
 * Replaces the vanilla sidebar scoreboard. The module box is the scoreboard rectangle, so dragging
 * it in the HUD editor moves the real scoreboard. With default settings the result matches vanilla
 * (see GuiIngame.renderScoreboard). Vanilla rendering is cancelled while this module draws, and
 * also while the player has switched it off: then no scoreboard is shown at all. A server that
 * blocks the module, or a drawing error, gives vanilla's sidebar back.
 */
public final class ScoreboardModule extends AbstractHudModule {
  private static final int TEXT = 0xFFFFFFFF; // opaque white (vanilla's 0x20FFFFFF constant is a low-alpha tint that dims rows)
  private static volatile ScoreboardModule instance;

  private final Setting.Toggle hideNumbers = grouped("Display", add(new Setting.Toggle("Hide red score numbers", false, "Remove the red numbers on the right of the scoreboard")));
  private final Setting.Toggle shadow = grouped("Text", adv(new Setting.Toggle("Text shadow", false, "A dark shadow behind the letters")));
  private final Setting.Toggle bg = grouped("Background", add(new Setting.Toggle("Background", true, "A dark box behind the scoreboard")));
  private final Setting.Color bgColor = grouped("Background", adv(new Setting.Color("Background color", 0x50000000, "Colour and see-through amount of the box")));

  private final List<Setting<?>> visible = new ArrayList<>();

  // Per tick cache, top row first.
  private String[] names = new String[16];
  private String[] points = new String[16];
  private int[] pointWidth = new int[16];
  private int count;
  private String title = "";
  private int titleWidth, contentWidth;
  private boolean built, sample;

  public ScoreboardModule() {
    super("Scoreboard", true, Anchor.CENTER_RIGHT, 0, 0, 60, 40);
    // The inherited text/background settings do not apply; this module has its own.
    background.set(false);
    visible.add(hideNumbers);
    visible.add(shadow);
    visible.add(bg);
    visible.add(bgColor);
    instance = this;
  }

  /** The placeholder rows are for the HUD editor only; in game nothing is drawn while the server shows no sidebar. */
  @Override
  protected boolean nothingToShow() {
    return sample;
  }

  @Override
  protected boolean padded() {
    return false;
  }

  /** Set when drawing or reading the scoreboard threw: vanilla's sidebar takes over again until the next start. */
  private volatile boolean broken;

  /**
   * True when vanilla's renderScoreboard should be skipped: this module draws it instead, or the player switched the
   * Scoreboard off (its card toggle or the X in the HUD editor), which hides the sidebar completely, the server's
   * vanilla one included. A server that blocks the module, or a drawing error, leaves vanilla's sidebar on screen.
   */
  public static boolean replacesVanilla() {
    ScoreboardModule m = instance;
    if (m == null || m.broken) return false;
    if (!m.userEnabled()) return true;
    return m.enabled() && !m.mc.gameSettings.showDebugInfo;
  }

  @Override
  public List<Setting<?>> settings() {
    return Collections.unmodifiableList(visible);
  }

  @Override
  public void resetSettings() {
    super.resetSettings();
    background.set(false);
  }

  @Override
  public void tick() {
    if (broken) return;
    try {
      rebuild();
    } catch (Throwable error) {
      broken = true; // not enabled(false): a switched-off Scoreboard hides the sidebar, an error must give vanilla's back
      Cbui.reportOnce("scoreboard module", error);
    }
  }

  private ScoreObjective objective() {
    if (mc.theWorld == null) return null;
    Scoreboard board = mc.theWorld.getScoreboard();
    ScoreObjective objective = null;
    if (mc.thePlayer != null) {
      // 1.7.10 has no per-team sidebar slots, only the one sidebar objective
    }
    return objective != null ? objective : board.getObjectiveInDisplaySlot(1);
  }

  private static final String[] RED = new String[256];

  /** "red score" text, cached for small scores (rebuilt every tick otherwise). */
  private static String red(int v) {
    if (v < 0 || v >= RED.length) return EnumChatFormatting.RED + String.valueOf(v);
    String s = RED[v];
    return s != null ? s : (RED[v] = EnumChatFormatting.RED + String.valueOf(v));
  }

  private void ensure(int n) {
    if (names.length < n) {
      names = new String[n];
      points = new String[n];
      pointWidth = new int[n];
    }
  }

  private void rebuild() {
    FontRenderer font = mc.fontRendererObj;
    if (font == null) return;
    ScoreObjective objective = objective();
    boolean numbers = !hideNumbers.get();
    int widest;
    if (objective == null) {
      sample = true;
      title = "Scoreboard";
      count = 3;
      ensure(count);
      String[] n = {"Player 1", "Player 2", "Player 3"};
      int[] p = {3, 2, 1};
      widest = font.getStringWidth(title);
      for (int i = 0; i < count; i++) {
        names[i] = n[i];
        points[i] = red(p[i]);
        pointWidth[i] = font.getStringWidth(points[i]);
        widest = Math.max(widest, font.getStringWidth(names[i]) + (numbers ? font.getStringWidth(": " + points[i]) : 0));
      }
    } else {
      sample = false;
      Scoreboard board = objective.getScoreboard();
      Collection<Score> all = board.getSortedScores(objective);
      int total = 0;
      for (Score s : all) {
        String name = s.getPlayerName();
        if (name != null && !name.startsWith("#")) total++;
      }
      int shown = Math.min(15, total), skip = total - shown, seen = 0, slot = shown;
      ensure(shown);
      title = objective.getDisplayName();
      widest = font.getStringWidth(title);
      for (Score s : all) {
        String name = s.getPlayerName();
        if (name == null || name.startsWith("#")) continue;
        if (seen++ < skip) continue;
        slot--; // ascending list; the last entry is the top row
        names[slot] = ScorePlayerTeam.formatPlayerName(board.getPlayersTeam(name), name);
        points[slot] = red(s.getScorePoints());
        pointWidth[slot] = font.getStringWidth(points[slot]);
        widest = Math.max(widest, font.getStringWidth(names[slot]) + (numbers ? font.getStringWidth(": " + points[slot]) : 0));
      }
      count = shown;
    }
    titleWidth = font.getStringWidth(title);
    contentWidth = widest;
    size(widest + 4, (count + 1) * font.FONT_HEIGHT + 1);
    built = true;
  }

  @Override
  public float[] bounds(ScaledResolution r) {
    if (!built) rebuild();
    float s = scale(), w = width() * s, h = height() * s;
    int sw = r.getScaledWidth(), sh = r.getScaledHeight();
    if (anchor() == Anchor.CENTER_RIGHT) {
      // Vanilla pivot: right edge one pixel inside the screen, bottom at height/2 + rows*fontHeight/3.
      int fh = mc.fontRendererObj != null ? mc.fontRendererObj.FONT_HEIGHT : 9;
      float x = sw - w - 1 + offsetX(), y = sh / 2f + count * fh / 3f - h + offsetY();
      return new float[] {clamp(x, 0, Math.max(0, sw - w)), clamp(y, 0, Math.max(0, sh - h)), w, h};
    }
    return LayoutMath.bounds(anchor(), offsetX(), offsetY(), width(), height(), s, sw, sh);
  }

  private static float clamp(float v, float lo, float hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  @Override
  protected void draw(float partialTicks, boolean editor) {
    if (broken) return;
    try {
      if (!built) rebuild();
      if (!built) return;
      FontRenderer font = mc.fontRendererObj;
      int fh = font.FONT_HEIGHT, w = (int) width();
      boolean numbers = !hideNumbers.get(), sh = shadow.get();
      if (bg.get()) {
        int body = bgColor.get();
        int head = (Math.min(255, (body >>> 24) * 6 / 5) << 24) | (body & 0xFFFFFF);
        Gui.drawRect(0, 0, w, fh, head);
        // the divider line and every row share one colour and touch each other: one rect
        Gui.drawRect(0, fh, w, fh + 1 + count * fh, body);
      }
      for (int i = 0; i < count; i++) {
        int y = fh + 1 + i * fh;
        font.drawString(names[i], 2, y, TEXT, sh);
        if (numbers) font.drawString(points[i], w - pointWidth[i], y, TEXT, sh);
      }
      font.drawString(title, 2 + contentWidth / 2 - titleWidth / 2, 0, TEXT, sh);
    } catch (Throwable error) {
      broken = true;
      Cbui.reportOnce("scoreboard module", error);
    }
  }
}
