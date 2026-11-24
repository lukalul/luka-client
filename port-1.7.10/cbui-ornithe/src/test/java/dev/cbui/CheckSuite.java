package dev.cbui;

import com.google.gson.JsonObject;
import dev.cbui.config.JsonFiles;
import dev.cbui.config.ProfileFiles;
import dev.cbui.font.AtlasPacker;
import dev.cbui.gui.ModsLayout;
import dev.cbui.gui.ModuleSearch;
import dev.cbui.gui.ModulesLayout;
import dev.cbui.hud.*;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

public final class CheckSuite {
  public static void main(String[] args) throws Exception {
    dev.cbui.skins.SkinImageChecks.run();
    cpsWindow();
    atlasPacking();
    layoutScalesAndOverlap();
    modsLayout();
    modulesLayout();
    configRoundTrip();
    profileRoundTrip();
    combinedSearch();
    hudEditorSnapping();
    renamedCards();
    timerBook();
    chatMacroText();
    mentions();
    SoundAndSkyChecks.run();
    WheelAndLevelChecks.run();
    System.out.println(
        "CBUI plain-Java checks passed: CPS, atlas packing, GUI scales/layout, config/profile"
            + " round-trips, combined search, HUD editor snapping, renamed-card migration,"
            + " chat macro expansion/sanitising");
  }

  private static void mentions() {
    java.util.regex.Pattern name = dev.cbui.chat.Mentions.pattern("luka");
    truth(dev.cbui.chat.Mentions.mentioned("hey luka, come here", name), "name in a message is a mention");
    truth(dev.cbui.chat.Mentions.mentioned("hey LUKA", name), "mentions ignore case");
    truth(!dev.cbui.chat.Mentions.mentioned("lukas is here", name), "a longer name is not a mention");
    truth(!dev.cbui.chat.Mentions.mentioned("luka: hello", name), "the sender of a line is not a mention");
    truth(!dev.cbui.chat.Mentions.mentioned("<luka> hello", name), "the sender of a line is not a mention");
    truth(!dev.cbui.chat.Mentions.mentioned("[Owner] luka: hello", name), "a ranked sender is not a mention");
    truth(dev.cbui.chat.Mentions.mentioned("luka: hello luka", name), "a mention after the sender counts");
    truth("\u00a7aHi \u00a7eluka\u00a7r\u00a7a!".equals(dev.cbui.chat.Mentions.highlight("\u00a7aHi luka!", name, "")), "name highlight keeps the formatting around it");
    truth("\u00a7eLuka\u00a7r\u00a7b there".equals(dev.cbui.chat.Mentions.highlight("Luka there", name, "\u00a7b")), "name highlight keeps the formatting around it");
    truth("nobody here".equals(dev.cbui.chat.Mentions.highlight("nobody here", name, "")), "name highlight keeps the formatting around it");
  }

  private static void chatMacroText() {
    String expanded = dev.cbui.feature.chatmacros.MacroText.expand(
        "/msg {name} {x},{y},{z} {health}\n", -2, 64, 7, "Alex", 19.5f);
    truth("/msg Alex -2,64,7 19.5".equals(expanded), "macro placeholders and controls");
    char[] longText = new char[120];
    Arrays.fill(longText, 'a');
    equal(100, dev.cbui.feature.chatmacros.MacroText.expand(
        new String(longText), 0, 0, 0, "", 20).length(), "macro vanilla length");
    truth(dev.cbui.feature.chatmacros.MacroText.expand(" \t\n", 0, 0, 0, null, 20).isEmpty(),
        "macro sanitised empty");
    truth(dev.cbui.feature.chatmacros.MacroText.expand("/", 0, 0, 0, null, 20).isEmpty(),
        "macro bare slash");
    truth("red".equals(dev.cbui.feature.chatmacros.MacroText.expand("\u00a7cred", 0, 0, 0, null, 20)),
        "macro formatting code sanitised");
    truth("{missing} {{x}}".equals(dev.cbui.feature.chatmacros.MacroText.expand(
        "{missing} {{x}}", 1, 0, 0, null, 20)), "macro nested braces literal");
    truth(dev.cbui.feature.chatmacros.MacroText.expand(
        new String(longText, 0, 99) + "\ud83d\ude00", 0, 0, 0, null, 20).length() == 99,
        "macro cap does not split unicode");
  }

  private static void cpsWindow() {
    CpsCounter c = new CpsCounter(4);
    c.click(100);
    c.click(500);
    equal(2, c.count(999), "CPS active");
    equal(1, c.count(1200), "CPS expiry");
    equal(0, c.count(1600), "CPS empty");
  }

  private static void atlasPacking() {
    AtlasPacker p = new AtlasPacker(16, 16, 1);
    array(new int[] {1, 1}, p.place(5, 5), "first glyph");
    notNull(p.place(5, 5), "second glyph");
    notNull(p.place(5, 5), "third glyph");
    notNull(p.place(5, 5), "fourth glyph");
    isNull(p.place(5, 5), "full atlas");
    isNull(new AtlasPacker(8, 8, 1).place(8, 1), "oversize glyph");
  }

  private static void layoutScalesAndOverlap() {
    for (int guiScale = 1; guiScale <= 4; guiScale++) {
      int w = 854 / guiScale, h = 480 / guiScale;
      for (Anchor a : Anchor.values()) {
        float[] b = LayoutMath.bounds(a, 0, 0, 60, 18, 1.5f, w, h);
        truth(
            b[0] >= 0 && b[1] >= 0 && b[0] + b[2] <= w + .01f && b[1] + b[3] <= h + .01f,
            "anchor bounds scale " + guiScale);
      }
      float[][] boxes = {
        LayoutMath.bounds(Anchor.TOP_LEFT, 5, 5, 56, 17, 1, w, h),
        LayoutMath.bounds(Anchor.TOP_LEFT, 5, 27, 100, 17, 1, w, h),
        LayoutMath.bounds(Anchor.TOP_CENTER, 0, 5, 72, 17, 1, w, h),
        LayoutMath.bounds(Anchor.TOP_RIGHT, -5, 5, 56, 17, 1, w, h),
        LayoutMath.bounds(Anchor.BOTTOM_LEFT, 5, -5, 82, 20, 1, w, h),
        LayoutMath.bounds(Anchor.BOTTOM_CENTER, 0, -50, 126, 17, 1, w, h),
        LayoutMath.bounds(Anchor.BOTTOM_RIGHT, -5, -5, 64, 42, 1, w, h)
      };
      for (int i = 0; i < boxes.length; i++)
        for (int j = i + 1; j < boxes.length; j++)
          truth(
              !overlap(boxes[i], boxes[j]), "overlap scale " + guiScale + " boxes " + i + "/" + j);
    }
  }

  private static void configRoundTrip() throws Exception {
    File dir = Files.createTempDirectory("cbui-config").toFile(),
        file = new File(dir, "config/cbui.json");
    JsonObject first = new JsonObject();
    first.addProperty("version", 1);
    JsonFiles.writeAtomic(file, first);
    equal(1, JsonFiles.read(file).get("version").getAsInt(), "first config");
    JsonObject second = new JsonObject();
    second.addProperty("version", 2);
    JsonFiles.writeAtomic(file, second);
    equal(2, JsonFiles.read(file).get("version").getAsInt(), "replacement config");
    truth(!new File(file.getPath() + ".tmp").exists(), "temp removed");
  }

  private static void modsLayout() {
    for (int scale = 1; scale <= 4; scale++) {
      ModsLayout l = ModsLayout.fit(854 / scale, 480 / scale);
      truth(l.valid(), "mods layout scale " + scale);
      truth(l.left + l.listWidth <= l.contentLeft, "mods columns overlap scale " + scale);
      truth(
          l.contentLeft + l.contentWidth <= l.left + l.width,
          "mods content overflow scale " + scale);
    }
  }

  private static void modulesLayout() {
    for (int scale = 1; scale <= 4; scale++) {
      int w = 854 / scale, h = 480 / scale;
      ModulesLayout l = ModulesLayout.fit(w, h);
      truth(l.valid(w, h), "modules layout scale " + scale);
      for (int i = 0; i < 12; i++) {
        int x = l.cardX(i), y = l.cardY(i);
        truth(x >= l.panelX && x + l.cardWidth <= l.panelX + l.panelWidth, "card x scale " + scale);
        if (y < l.panelY + l.panelHeight)
          truth(y + l.cardHeight > l.contentTop, "card y scale " + scale);
      }
    }
  }

  private static void timerBook() {
    dev.cbui.hud.TimerBook b = new dev.cbui.hud.TimerBook(2);
    truth(!b.active(0, 0), "timer inactive at start");
    b.start(0, 100, 320);
    equal(320, b.remaining(0, 100), "timer full");
    equal(1, b.activeCount(100), "one active");
    equal(0, b.remaining(0, 420), "timer expires");
    b.start(1, 0, 10);
    b.clearAll();
    equal(0, b.activeCount(0), "clearAll");
    truth("9.5s".equals(dev.cbui.hud.TimerBook.format(190)), "format tenths");
    truth("0.1s".equals(dev.cbui.hud.TimerBook.format(2)), "format last tenth");
    truth("10s".equals(dev.cbui.hud.TimerBook.format(200)), "format whole");
    truth("16s".equals(dev.cbui.hud.TimerBook.format(320)), "format 16s");
  }

  private static void hudEditorSnapping() {
    float[] d = LayoutMath.snap(new float[] {-4, 10, 40, 19}, java.util.Collections.<float[]>emptyList(), 427, 240, 3, 2);
    truth(d[0] == 7 && d[1] == 0, "editor clamps to the left margin");
    d = LayoutMath.snap(new float[] {400, 230, 40, 19}, java.util.Collections.<float[]>emptyList(), 427, 240, 3, 2);
    truth(d[0] == -16 && d[1] == -12, "editor clamps to the bottom-right margin");
    d = LayoutMath.snap(new float[] {51, 81, 40, 19}, java.util.Collections.singletonList(new float[] {50, 40, 60, 19}), 427, 240, 3, 2);
    truth(d[0] == -1 && d[1] == 0, "editor snaps left edges");
    d = LayoutMath.snap(new float[] {100, 60, 40, 19}, java.util.Collections.singletonList(new float[] {10, 61.5f, 20, 19}), 427, 240, 3, 2);
    truth(d[0] == 0 && d[1] == 1.5f, "editor snaps top edges");
  }

  /** 2026-11-22 card renames: old saved entries, hold keys and bound keys move to the new names. */
  private static void renamedCards() {
    JsonObject modules = new JsonObject();
    JsonObject zoom = new JsonObject();
    zoom.addProperty("enabled", false);
    modules.add("PolyZoom", zoom);
    JsonObject both = new JsonObject();
    both.addProperty("enabled", true);
    modules.add("PolyTime", new JsonObject());
    modules.add("Time Changer", both);
    dev.cbui.config.CardRenames.moveCards(modules);
    truth(!modules.has("PolyZoom") && modules.has("Zoom"), "old card entry moves to the new name");
    truth(!modules.getAsJsonObject("Zoom").get("enabled").getAsBoolean(), "moved entry keeps its values");
    truth(modules.getAsJsonObject("Time Changer").get("enabled").getAsBoolean(), "an entry under the new name wins");
    java.util.Map<String, Integer> hold = new java.util.HashMap<String, Integer>();
    hold.put("PolyCrosshair", 42);
    dev.cbui.config.CardRenames.moveCards(hold);
    truth(Integer.valueOf(42).equals(hold.get("Crosshair")) && !hold.containsKey("PolyCrosshair"), "hold key moves");
    java.util.List<String> options = Arrays.asList("key_PolyZoom: magnify:46", "key_Flip hitboxes (PolyHitbox):35",
        "key_Flip hitboxes:36", "key_PolyTime - push clock ahead:bad");
    truth(Integer.valueOf(46).equals(dev.cbui.config.CardRenames.savedKeyCode(options, "Zoom: magnify")), "old bound key is read");
    truth(Integer.valueOf(36).equals(dev.cbui.config.CardRenames.savedKeyCode(options, "Flip hitboxes")), "new bound key wins");
    truth(dev.cbui.config.CardRenames.savedKeyCode(options, "Time Changer - push clock ahead") == null, "damaged line is ignored");
    truth("Zoom: step back".equals(dev.cbui.config.CardRenames.newKey("PolyZoom: step back")), "old key name maps to new");
  }

  private static void profileRoundTrip() throws Exception {
    File dir = Files.createTempDirectory("cbui-profiles").toFile();
    ProfileFiles profiles = new ProfileFiles(dir);
    JsonObject value = new JsonObject();
    value.addProperty("marker", 7);
    profiles.create("Alpha One", value);
    equal(7, profiles.load("Alpha One").get("marker").getAsInt(), "profile create/load");
    profiles.rename("Alpha One", "Beta Two");
    truth(!profiles.file("Alpha One").exists(), "profile old renamed");
    equal(1, profiles.list().size(), "profile list");
    profiles.delete("Beta Two");
    equal(0, profiles.list().size(), "profile delete");
  }

  private static void combinedSearch() {
    java.util.List<SearchEntry> entries =
        Arrays.asList(
            new SearchEntry("Keystrokes keyboard HUD", "HUD"),
            new SearchEntry("Motion Blur level", "VISUALS"),
            new SearchEntry("Chat opacity", "QOL"));
    equal(1, ModuleSearch.filter(entries, "level", "ALL").size(), "bridge search");
    equal(1, ModuleSearch.filter(entries, "keyboard", "HUD").size(), "native search");
    equal(0, ModuleSearch.filter(entries, "keyboard", "VISUALS").size(), "category filter");
  }

  private static final class SearchEntry implements ModuleSearch.Entry {
    private final String text, category;

    SearchEntry(String text, String category) {
      this.text = text;
      this.category = category;
    }

    public String searchText() {
      return text;
    }

    public String category() {
      return category;
    }
  }

  private static boolean overlap(float[] a, float[] b) {
    return a[0] < b[0] + b[2] && a[0] + a[2] > b[0] && a[1] < b[1] + b[3] && a[1] + a[3] > b[1];
  }

  private static void truth(boolean v, String m) {
    if (!v) throw new AssertionError(m);
  }

  private static void equal(int e, int a, String m) {
    if (e != a) throw new AssertionError(m + ": expected " + e + " got " + a);
  }

  private static void notNull(Object v, String m) {
    truth(v != null, m);
  }

  private static void isNull(Object v, String m) {
    truth(v == null, m);
  }

  private static void array(int[] e, int[] a, String m) {
    truth(a != null && e[0] == a[0] && e[1] == a[1], m);
  }

  private CheckSuite() {}
}
