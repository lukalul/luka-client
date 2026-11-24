package dev.cbui.feature.nickhider;

import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;
import java.util.HashMap;
import java.util.UUID;

/**
 * Nick Hider: draws a made-up name in place of the player's own name in every text the game's font draws (chat, tab
 * list, nametags, scoreboard, titles, luka's HUD in Minecraft font), and can show the default skin instead of the
 * player's own or everyone else's. Client side only: nothing is sent, and the server still sees the real name.
 *
 * <p>The font hook calls {@link #replace} for every string it draws or measures. That stays cheap: one switch check and
 * one {@link String#indexOf} when the name is not in the text. Only a text that holds the name is rebuilt, once, and kept
 * in a small cache, so the same chat line or nametag is not rebuilt every frame.
 */
public final class NickHiderModule extends FeatureModule {
  private static final char SECTION = '§';
  private static final int CACHE_LIMIT = 512;
  private static volatile NickHiderModule instance;

  public final Setting.Toggle hideName = grouped("Name", add(new Setting.Toggle(
      "Hide my name", true, "Draw the fake name below wherever your name shows: chat, tab list, nametag, scoreboard")));
  public final Setting.Text fakeName = grouped("Name", add(new Setting.Text(
      "Fake name", "You", "Shown instead of your name; & colour codes work (&b, &l...)")));
  public final Setting.Toggle hideOwnSkin = grouped("Skins", add(new Setting.Toggle(
      "Hide my skin", false, "Show the default skin on yourself")));
  public final Setting.Toggle hideOtherSkins = grouped("Skins", add(new Setting.Toggle(
      "Hide other skins", false, "Show the default skin on every other player")));

  /** Texts holding the name, and what they become. Render thread only. */
  private final HashMap<String, String> cache = new HashMap<String, String>();
  private String ownName;
  /** The fake-name setting the cache was built for, and that name with & codes turned into colour codes. */
  private String cachedFor, shown = "";

  public NickHiderModule() {
    super("Nick Hider", false);
    instance = this;
  }

  @Override public String category() { return "UTILITY"; }

  /** The text with the player's own name swapped for the fake name; the same object when there is nothing to swap. */
  public static String replace(String text) {
    NickHiderModule m = instance;
    if (m == null || text == null || !m.hideName.get() || !m.enabled()) return text;
    String own = m.ownName;
    if (own == null) {
      own = m.loadName();
      if (own == null) return text;
    }
    if (text.length() < own.length() || text.indexOf(own) < 0) return text;
    return m.swapped(text, own);
  }

  /** True when this player's skin should be drawn as the default one. */
  public static boolean hideSkin(UUID player) {
    NickHiderModule m = instance;
    if (m == null || player == null || !m.enabled()) return false;
    boolean own = m.mc.thePlayer != null && player.equals(m.mc.thePlayer.getUniqueID());
    return own ? m.hideOwnSkin.get() : m.hideOtherSkins.get();
  }

  private String loadName() {
    try {
      String name = mc.getSession().getUsername();
      ownName = name == null || name.isEmpty() ? null : name;
    } catch (Throwable notReady) {
      ownName = null;
    }
    return ownName;
  }

  private synchronized String swapped(String text, String own) {
    String setting = fakeName.get();
    if (setting != cachedFor) {
      cachedFor = setting;
      shown = colours(setting);
      cache.clear();
    }
    String hit = cache.get(text);
    if (hit != null) return hit;
    String out = build(text, own, shown);
    if (cache.size() >= CACHE_LIMIT) cache.clear();
    cache.put(text, out);
    return out;
  }

  /** Swaps every whole-word match (a name next to letters, as in "Steven", is left alone). */
  private static String build(String text, String own, String fake) {
    StringBuilder out = null;
    int from = 0, at = text.indexOf(own);
    while (at >= 0) {
      int end = at + own.length();
      if (wordStart(text, at) && (end == text.length() || !nameChar(text.charAt(end)))) {
        if (out == null) out = new StringBuilder(text.length() + fake.length());
        out.append(text, from, at).append(fake);
        // a fake name with its own colours: carry on in the colour the text had before it
        if (fake.indexOf(SECTION) >= 0) out.append(SECTION).append('r').append(formatBefore(text, at));
        from = end;
      }
      at = text.indexOf(own, end);
    }
    if (out == null) return text;
    return out.append(text, from, text.length()).toString();
  }

  private static boolean wordStart(String text, int at) {
    if (at == 0 || !nameChar(text.charAt(at - 1))) return true;
    return at >= 2 && text.charAt(at - 2) == SECTION; // "§aName": the letter before is a colour code
  }

  private static boolean nameChar(char c) {
    return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
  }

  /** The colour and style codes in effect at {@code end}. */
  private static String formatBefore(String text, int end) {
    String colour = "";
    StringBuilder styles = new StringBuilder();
    for (int i = 0; i + 1 < end; i++) {
      if (text.charAt(i) != SECTION) continue;
      char code = Character.toLowerCase(text.charAt(i + 1));
      if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
        colour = "" + SECTION + code;
        styles.setLength(0);
      } else if (code == 'r') {
        colour = "";
        styles.setLength(0);
      } else if (code >= 'k' && code <= 'o') styles.append(SECTION).append(code);
      i++;
    }
    return colour + styles;
  }

  /** "&b&lName" to colour codes; a lone & stays. */
  private static String colours(String raw) {
    if (raw == null || raw.isEmpty()) return "";
    StringBuilder out = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '&' && i + 1 < raw.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(raw.charAt(i + 1)) >= 0) {
        out.append(SECTION).append(Character.toLowerCase(raw.charAt(++i)));
      } else out.append(c);
    }
    return out.toString();
  }
}
