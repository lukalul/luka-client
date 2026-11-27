package dev.cbui.gui;

import com.google.gson.JsonObject;
import dev.cbui.Cbui;
import dev.cbui.config.ProfileFiles;
import dev.cbui.font.Fonts;
import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;
import dev.cbui.hud.HudElement;
import dev.cbui.hud.LayoutMath;
import dev.cbui.hud.Setting;
import dev.cbui.render.Draw;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import dev.luka.compat.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Responsive card-based modules GUI. */
public class ModuleMenu extends GuiScreen {
  private static final ResourceLocation PROFILE_ICON =
      new ResourceLocation("cbui", "icons/profiles-64.png");
  private static final java.util.Map<String, String> MODULE_ICON_FILE = new java.util.HashMap<>();
  private static final java.util.Map<String, int[]> MODULE_ICON_SIZE = new java.util.HashMap<>();

  static {
    icon("Keystrokes", "wasd.png", 110, 74);
    icon("Armour status", "diamond_chestplate.png", 150, 150);
    icon("Potion status", "speed_icon.png", 60, 60);
  }

  private static void icon(String module, String file, int w, int h) {
    MODULE_ICON_FILE.put(module, file);
    MODULE_ICON_SIZE.put(module, new int[] {w, h});
  }

  private static final ResourceLocation LOGO = new ResourceLocation("cbui", "logo_white.png");
  private static final ResourceLocation COG_ICON = new ResourceLocation("cbui", "icons/cog-64.png");
  /** HUD box buttons: shown on the HUD (eye), hidden from it (crossed eye). */
  private static final ResourceLocation EYE_ICON = new ResourceLocation("cbui", "icons/eye-64.png"),
      EYE_OFF_ICON = new ResourceLocation("cbui", "icons/eye-off-64.png");
  /** The X that switches a HUD box's module off. */
  private static final int REMOVE_RED = 0xFFFF4A4A;
  private static final int RED = 0xFF8942DA, CARD = 0xD51A151F, MUTED = 0xFFA0A8B5;
  private static String activeProfileName = "Default";

  private enum View {
    HUB,
    CARDS,
    SETTINGS,
    PROFILES,
    HUD
  }

  private final GuiScreen parent;
  private final boolean modsOnly;
  private final List<Card> allCards = new ArrayList<>(), visibleCards = new ArrayList<>();
  private final List<String> categories = new ArrayList<>();
  private final List<UiFade> cardFades = new ArrayList<>();
  private ModulesLayout layout;
  private GuiTextField search, editField, profileField;
  private ProfileFiles profiles;
  private List<String> profileNames = Collections.emptyList();
  /** Lunar Client profiles found on this PC ({folder, display name}), offered for import in the profiles view. */
  private List<String[]> lunarProfiles = Collections.emptyList();
  private String lunarStatus = "";
  private String activeProfile = activeProfileName, category = "ALL", profileError = "";
  private Card selected;
  private View view = View.CARDS;

  /** True while the luka menu shows the HUD editor (for the HUD editor frame rate cap). */
  public static boolean hudEditorOpen() {
    net.minecraft.client.gui.GuiScreen s = net.minecraft.client.Minecraft.getMinecraft().currentScreen;
    return s instanceof ModuleMenu && ((ModuleMenu) s).view == View.HUD;
  }
  private float scroll, targetScroll, settingsScroll, settingsTarget;
  private HudElement dragging;
  private float dragX, dragY;
  private float dragGrabX, dragGrabY;
  /** HUD editor: the selected box, the box being resized (and the corner opposite the grabbed one, which stays put), undo history, library panel. */
  private HudElement hudSelected, resizing;
  private float resizeFx, resizeFy, resizeS0, resizeD0;
  private boolean resizeLeft, resizeTop, dragMoved, libraryOpen;
  private List<HudHistory.Entry> pendingSnapshot;
  private final HudHistory history = new HudHistory();
  private Object editing;
  private Object editOriginal;
  private boolean renameProfile;
  /** The name popup is for a style preset (STYLE tab), not a profile. */
  private boolean presetModal;
  private List<String> presetNames = Collections.emptyList();
  private long openedAt;
  /** GUI scale factor: this screen is laid out in framebuffer pixels (width/height are scaled up by it), as the original is. */
  private int sf = 1;
  /** The menu zooms with the window (its layout is in zoomed pixels); the HUD editor always works 1:1. */
  private float zoom = 1f, menuZoom = 1f;
  private int baseW, baseH;
  /** True when the open module settings page came from a card's OPTIONS button, so Back returns to the card list. */
  private boolean fromCards;
  /** The settings page was opened from the HUD editor's cog: Back returns to the editor. */
  private boolean returnToHud;
  /** Where Back goes from a module page opened with a HUD box's settings button. */
  private View hudReturnView = View.HUD;
  /** "More options" expanded on the open module page; sections of another mod's options that are open. */
  private boolean showAdvanced;
  /** The look settings of the global style page (copies; changing one writes to every module). */
  private List<Setting<?>> globalSettings;
  private final java.util.Set<String> openSections = new java.util.HashSet<>();

  public ModuleMenu() {
    this(null, false);
  }

  public ModuleMenu(GuiScreen parent) {
    this(parent, false);
  }

  private boolean startInHud;
  /** The transparent hub (logo, friends, MODS, cosmetics) over the live HUD, which can be dragged right there; MODS opens the full panel. */
  private boolean hubMode, hubShown;
  private float hubX, hubY, hubW, hubH;

  /** Opens straight into the HUD editor (used by the HUD button of the main menu). */
  public ModuleMenu inHud() {
    startInHud = true;
    return this;
  }

  public ModuleMenu(GuiScreen parent, boolean modsOnly) {
    this.parent = parent;
    this.modsOnly = modsOnly;
    this.hubMode = !modsOnly;
  }

  @Override
  public void initGui() {
    try {
      openedAt = System.currentTimeMillis();
      if (startInHud) {
        startInHud = false;
        view = View.HUD;
        hubShown = true;
      } else if (hubMode && !hubShown) {
        hubShown = true;
        view = View.HUB;
      }
      sf = Math.max(1, dev.luka.compat.Compat.scaled(mc).getScaleFactor());
      baseW = width * sf;
      baseH = height * sf;
      // The menu is drawn at Minecraft's own GUI scale (Video Settings > GUI Scale), a whole number: pixel fonts, the live
      // HUD previews and icons stay crisp, and it is the size the player picked for every other screen. (It used to
      // zoom with the window in 0.25 steps, which blurred the Minecraft font at 1.25x, 1.75x...)
      menuZoom = sf;
      zoom = view == View.HUD ? 1f : menuZoom;
      UiWidgets.zoom = zoom;
      width = (int) Math.ceil(baseW / zoom);
      height = (int) Math.ceil(baseH / zoom);
      layout = ModulesLayout.fit(width, height);
      Keyboard.enableRepeatEvents(true);
      search = new GuiTextField(fontRendererObj, layout.panelX + 11, layout.panelY + 10, layout.panelWidth - 22, 8);
      search.setEnableBackgroundDrawing(false);
      search.setTextColor(0xFFFFFFFF);
      search.setMaxStringLength(80);
      profiles = new ProfileFiles(new File(mc.mcDataDir, "config/cbui/profiles"));
      refreshProfiles();
      buildCards();
      if (modsOnly) category = "MODS";
      filterCards();
    } catch (Throwable error) {
      fallback("initialization", error);
    }
  }

  private void buildCards() {
    allCards.clear();
    categories.clear();
    Collections.addAll(categories, "ALL", "ENABLED", "HUD", "UTILITY", "VISUALS", "MODS");
    List<AbstractHudModule> ordered = new ArrayList<>(Cbui.hud().modules());
    Collections.sort(ordered, (m1, m2) -> Integer.compare(usefulness(m1.name()), usefulness(m2.name())));
    for (AbstractHudModule module : ordered) {
      if (module instanceof dev.cbui.feature.FeatureModule && !((dev.cbui.feature.FeatureModule) module).visible()) continue;
      if (livesInSettings(module)) continue; // on a settings tab instead (SETTINGS_HOME)
      allCards.add(new Card(module, nativeCategory(module)));
    }
    // keybinds other mods register with Minecraft go onto one "Keybinds" card under MODS
    try {
      ConfigSnapshot.Mod keys = ModKeys.card();
      if (keys != null) allCards.add(new Card(keys));
    } catch (Throwable error) {
      Cbui.reportOnce("mod keybinds", error);
    }
  }

  /** Most useful first for HCF: timers and status, then movement, then info readouts, then looks. */
  private static final String[] USEFULNESS = {
    "Cooldowns", "Potion status", "Armour status", "Alerts", "Team HUD", "Potion counter", "Scoreboard", "Coordinates",
    "Toggle sprint", "Keystrokes", "CPS", "Combo", "Reach", "FPS", "Ping", "Direction",
    "Server address", "Clock", "Memory", "Resource packs", "Menu Blur"
  };

  private static int usefulness(String name) {
    for (int i = 0; i < USEFULNESS.length; i++) if (USEFULNESS[i].equalsIgnoreCase(name)) return i;
    return USEFULNESS.length;
  }

  private static String nativeCategory(AbstractHudModule module) {
    if (module instanceof dev.cbui.feature.FeatureModule) return ((dev.cbui.feature.FeatureModule) module).category();
    String n = module.name();
    if (n.equals("Toggle sprint")) return "UTILITY";
    if (n.equals("Menu Blur")) return "VISUALS";
    return "HUD";
  }

  private void filterCards() {
    visibleCards.clear();
    visibleCards.addAll(
        ModuleSearch.filter(
            allCards, search == null ? "" : search.getText(), "ENABLED".equals(category) ? "ALL" : category));
    if ("ENABLED".equals(category)) {
      // only what is switched on (a HUD module that is off, or a mod whose HUD is hidden, is not listed)
      for (int i = visibleCards.size() - 1; i >= 0; i--) if (!visibleCards.get(i).enabled()) visibleCards.remove(i);
    }
    cardFades.clear();
    for (int i = 0; i < visibleCards.size(); i++) cardFades.add(new UiFade(175));
    targetScroll = scroll = 0;
  }

  @Override
  public void updateScreen() {
    if (search != null) search.updateCursorCounter();
    if (editField != null) editField.updateCursorCounter();
    if (profileField != null) profileField.updateCursorCounter();
  }

  @Override
  public void onGuiClosed() {
    UiWidgets.zoom = 1f;
    Keyboard.enableRepeatEvents(false);
    if (Cbui.hud() != null) Cbui.hud().editorOpen(false);
    saveCurrent();
  }

  @Override
  public void drawScreen(int guiMouseX, int guiMouseY, float partialTicks) {
    applyZoom();
    GlStateManager.pushMatrix();
    GlStateManager.scale(zoom / sf, zoom / sf, 1f);
    try {
      int mx = (int) (Mouse.getX() * (float) width / mc.displayWidth);
      int my = (int) (height - Mouse.getY() * (float) height / mc.displayHeight - 1);
      drawPixels(mx, my, partialTicks);
    } finally {
      GlStateManager.popMatrix();
    }
  }

  /** Switches between the zoomed menu and the 1:1 HUD editor when the view changes. */
  private void applyZoom() {
    if (Cbui.hud() != null) Cbui.hud().editorOpen(view == View.HUD || view == View.HUB);
    float want = view == View.HUD ? 1f : menuZoom;
    if (want == zoom || baseW == 0) return;
    zoom = want;
    UiWidgets.zoom = zoom;
    width = (int) Math.ceil(baseW / zoom);
    height = (int) Math.ceil(baseH / zoom);
    layout = ModulesLayout.fit(width, height);
    String text = search == null ? "" : search.getText();
    boolean focus = search != null && search.isFocused();
    search = new GuiTextField(fontRendererObj, layout.panelX + 11, layout.panelY + 10, layout.panelWidth - 22, 8);
    search.setEnableBackgroundDrawing(false);
    search.setTextColor(0xFFFFFFFF);
    search.setMaxStringLength(80);
    search.setText(text);
    search.setFocused(focus);
  }

  private void drawPixels(int mouseX, int mouseY, float partialTicks) {
    try {
      if (view == View.HUD) {
        drawHudEditor(mouseX, mouseY, partialTicks);
        return;
      }
      hintText = null;
      if (view == View.HUB) {
        drawHub(mouseX, mouseY, partialTicks);
        return;
      }
      drawGradientRect(0, 0, width, height, 0xB0100C13, 0xE017121C);
      float intro = Math.min(1f, (System.currentTimeMillis() - openedAt) / 240f);
      GlStateManager.pushMatrix();
      GlStateManager.translate(0, (1 - intro) * 8, 0);
      drawBrand();
      drawToolbar(mouseX, mouseY);
      // glass panel over 1.7.10's darker backdrop (the world behind is not blurred here)
      UiWidgets.glass(
          layout.panelX,
          layout.panelY,
          layout.panelX + layout.panelWidth,
          layout.panelY + layout.panelHeight,
          8,
          view == View.CARDS ? 0x6608070A : 0xC708070A);
      if (view == View.CARDS) drawCards(mouseX, mouseY);
      else if (view == View.SETTINGS) drawSettings(mouseX, mouseY);
      else drawProfiles(mouseX, mouseY);
      if (paletteTarget != null && view == View.SETTINGS) drawPalette(mouseX, mouseY);
      drawHint(mouseX, mouseY);
      GlStateManager.popMatrix();
      if (editField != null) editField.drawTextBox();
      if (profileField != null) drawProfileModal();
    } catch (Throwable error) {
      fallback("drawing", error);
    }
  }

  private void drawBrand() {
    float alpha = Math.min(1f, (System.currentTimeMillis() - openedAt) / 350f);
    int color = ((int) (alpha * 255) << 24) | 0xD9C2FF;
    float lockup = 25 + Fonts.bold(16).getStringWidth("luka");
    float lx = Math.round(width / 2f - lockup / 2f);
    Draw.texture(LOGO, lx, layout.toolbarY - 31, 20, 20, alpha);
    Fonts.bold(16).drawString("luka", lx + 25, layout.toolbarY - 29, color);
  }

  private void drawToolbar(int mx, int my) {
    int center = width / 2;
    iconButton(PROFILE_ICON, center - 82, layout.toolbarY, 28, mx, my, view == View.PROFILES);
    UiWidgets.cbButton(
        "MODS",
        center - 50,
        layout.toolbarY,
        100,
        28,
        Draw.inside(mx, my, center - 50, layout.toolbarY, 100, 28),
        view == View.CARDS);
    iconButton(COG_ICON, center + 54, layout.toolbarY, 28, mx, my, view == View.SETTINGS);
    UiWidgets.cbButton(
        "HUD", center + 90, layout.toolbarY, 42, 28, Draw.inside(mx, my, center + 90, layout.toolbarY, 42, 28), false);
  }

  private void iconButton(
      ResourceLocation texture, int x, int y, int size, int mx, int my, boolean active) {
    boolean hover = Draw.inside(mx, my, x, y, size, size);
    UiWidgets.cbButton("", x, y, size, size, hover, active);
    Draw.texture(texture, x + 7, y + 7, size - 14, size - 14, hover ? 1 : .65f);
  }

  private int pendingWheel;

  /** Wheel ticks since the last frame. The per-event delta is collected in handleMouseInput because the polled value is empty on LWJGL 3 backends. */
  private int takeWheel() {
    int polled = Mouse.getDWheel();
    int w = pendingWheel != 0 ? pendingWheel : polled;
    pendingWheel = 0;
    // LWJGL 3 backends may report one unit per notch instead of 120
    if (w != 0 && Math.abs(w) < 120) w = w > 0 ? 120 : -120;
    return w;
  }

  @Override
  public void handleMouseInput() {
    super.handleMouseInput();
    pendingWheel += Mouse.getEventDWheel();
  }
  private void drawCards(int mx, int my) {
    int wheel = takeWheel();
    if (wheel != 0
        && Draw.inside(mx, my, layout.panelX, layout.panelY, layout.panelWidth, layout.panelHeight))
      targetScroll -= wheel / 6f;
    int strip = layout.strip,
        max = Math.max(0, layout.contentHeight(visibleCards.size()) - (layout.panelHeight - strip));
    targetScroll = clamp(targetScroll, 0, max);
    scroll += (targetScroll - scroll) * .32f;
    Draw.rect(layout.panelX + 6, layout.panelY + strip - 1, layout.panelX + layout.panelWidth - 6, layout.panelY + strip, 0x1FFFFFFF);
    UiWidgets.glass(
        layout.panelX + 6,
        layout.panelY + 5,
        layout.panelX + layout.panelWidth - 6,
        layout.panelY + 23,
        3,
        search.isFocused() ? 0x33FFFFFF : 0x1AFFFFFF);
    if (search.getText().isEmpty() && !search.isFocused())
      fontRendererObj.drawString("Search modules...", layout.panelX + 11, layout.panelY + 10, 0xFF737D8D);
    search.drawTextBox();
    if (!layout.compact) drawCategoryBar(mx, my);
    clipX = layout.panelX;
    clipY = layout.panelY + strip;
    clipW = layout.panelWidth;
    clipH = layout.panelHeight - strip;
    UiWidgets.beginClipPx(clipX, clipY, clipW, clipH);
    if (visibleCards.isEmpty())
      Fonts.regular().drawCentered("NO MODULES MATCH", width / 2f, layout.panelY + 66, MUTED);
    long now = System.currentTimeMillis();
    for (int i = 0; i < visibleCards.size(); i++) {
      int x = layout.cardX(i),
          y = layout.panelY + strip + 4 + (i / layout.columns) * layout.cardPitchY - (int) scroll;
      drawCard(visibleCards.get(i), i, x, y, mx, my, now);
    }
    UiWidgets.endClip();
    drawScrollbar(max, scroll, layout.panelY + strip, layout.panelHeight - strip);
  }

  private void drawCategoryBar(int mx, int my) {
    int x = layout.panelX + 7, y = layout.panelY + 28;
    for (String item : categories) {
      int w = Math.max(37, Fonts.regular().getStringWidth(item) + 10);
      if (x + w > layout.panelX + layout.panelWidth - 5) break;
      UiWidgets.pill(item, x, y, w, 15, item.equals(category), Draw.inside(mx, my, x, y, w, 15));
      x += w + 3;
    }
  }

  // glass card tints: green while a module is on, red while it is off, neutral for cards with no switch
  private static final int CARD_ON = 0x8C3B5A34, CARD_ON_HOVER = 0xA6486C40;
  private static final int CARD_OFF = 0x8C642A29, CARD_OFF_HOVER = 0xA6773634;
  private static final int CARD_PLAIN = 0x73201C26, CARD_PLAIN_HOVER = 0x8C2C2733;

  /** A HUD card's buttons: "ADD TO HUD" (left) and the switch (right) share the first row. */
  private static boolean hudCard(Card card) {
    return card.nativeModule != null && card.nativeModule.hasHud() && card.toggleable();
  }

  private void drawCard(Card card, int index, int x, int y, int mx, int my, long now) {
    int w = layout.cardWidth, h = layout.cardHeight;
    boolean hover = Draw.inside(mx, my, x, y, w, h);
    float fade = cardFades.get(index).update(hover, now);
    boolean on = card.enabled();
    int base = !card.toggleable() ? CARD_PLAIN : on ? CARD_ON : CARD_OFF;
    int lit = !card.toggleable() ? CARD_PLAIN_HOVER : on ? CARD_ON_HOVER : CARD_OFF_HOVER;
    UiWidgets.glass(x, y, x + w, y + h, 3, Draw.lerpColor(base, lit, fade));
    // top: the module itself, drawn live where it has a HUD (as it looks on screen), its icon otherwise
    if (!drawPreview(card, x, y, w)) {
      int[] icon = card.nativeModule == null ? null : MODULE_ICON_SIZE.get(card.name);
      if (icon != null) {
        float scale = Math.min(44f / icon[0], 26f / icon[1]);
        float iw = icon[0] * scale, ih = icon[1] * scale;
        Draw.texture(
            new ResourceLocation("cbui", "icons/mods/" + MODULE_ICON_FILE.get(card.name)),
            x + (w - iw) / 2f, y + 4 + (28 - ih) / 2f, iw, ih, on ? 1f : .7f);
      } else {
        String modId = card.bridgeMod != null ? card.bridgeMod.id : null;
        ResourceLocation tile = ModIcons.get(modId, card.name);
        Draw.texture(tile, x + w / 2f - 14, y + 3, 28, 28, on || !card.toggleable() ? 1f : .7f);
      }
    }
    Fonts.regular().drawCentered(ellipsize(card.name, w - 10), x + w / 2f, y + 38, 0xFFF4F4F4);
    if (card.serverBlocked()) Fonts.regular().drawCentered("DISABLED BY SERVER", x + w / 2f, y + 49, 0xFFFF9A9A);
    int bottom = y + h - 38;
    if (hudCard(card)) {
      int half = (w - 8 - 3) / 2;
      // the pixel font has no smaller crisp size: a half-width button too narrow for "ADD TO HUD" says "+ HUD"
      String add = Fonts.regular().getStringWidth("ADD TO HUD") <= half - 4 ? "ADD TO HUD" : "+ HUD";
      UiWidgets.glassButton(add, x + 4, bottom, half, 14,
          Draw.inside(mx, my, x + 4, bottom, half, 14), false, false);
      UiWidgets.glassButton(on ? "DISABLE" : "ENABLE", x + 4 + half + 3, bottom, w - 8 - half - 3, 14,
          Draw.inside(mx, my, x + 4 + half + 3, bottom, w - 8 - half - 3, 14), false, false);
    } else if (card.toggleable()) {
      UiWidgets.glassButton(on ? "DISABLE" : "ENABLE", x + 4, bottom, w - 8, 14,
          Draw.inside(mx, my, x + 4, bottom, w - 8, 14), false, false);
    }
    UiWidgets.glassButton("OPTIONS", x + 4, y + h - 20, w - 8, 15,
        Draw.inside(mx, my, x + 4, y + h - 20, w - 8, 15), false, false);
  }

  /** Draws a HUD module's own box, scaled to fit the top of its card. False when there is nothing to draw. */
  private boolean drawPreview(Card card, int x, int y, int w) {
    AbstractHudModule m = card.nativeModule;
    // Armour status reports a smaller box than the column of items it draws: icon only
    if (m == null || !m.hasHud() || previewFailed.contains(card.name) || "Armour status".equals(card.name)) return false;
    try {
      float bw = m.width(), bh = m.height();
      if (bw <= 0 || bh <= 0) return false;
      float scale = Math.min(Math.min((w - 14) / bw, 28f / bh), 1.25f);
      // only compact boxes (one or two lines) read well at card size; taller ones (lists, armour, scoreboard) show
      // the icon instead
      if (scale < .7f || bh > 24) return false;
      float px = x + (w - bw * scale) / 2f, py = y + 4 + (28 - bh * scale) / 2f;
      // keep it inside the card's top area even if the module draws more than its box says
      int clipTop = Math.max(y + 2, clipY), clipBottom = Math.min(y + 34, clipY + clipH);
      if (clipBottom <= clipTop) return true;
      UiWidgets.beginClipPx(x + 2, clipTop, w - 4, clipBottom - clipTop);
      try {
        m.renderAt(px, py, scale, 0f, true);
      } finally {
        UiWidgets.beginClipPx(clipX, clipY, clipW, clipH); // the card grid's own clip again
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableBlend();
      }
      return true;
    } catch (Throwable error) {
      // one broken preview must not take the menu down: that card shows its icon from now on
      previewFailed.add(card.name);
      Cbui.reportOnce("module preview " + card.name, error);
      return false;
    }
  }

  private final java.util.Set<String> previewFailed = new java.util.HashSet<>();
  /** The card grid's clip rectangle (drawCards), restored after each preview's own clip. */
  private int clipX, clipY, clipW, clipH;

  private void drawSettings(int mx, int my) {
    if (selected == null) {
      drawSettingsList(mx, my);
      return;
    }
    int wheel = takeWheel();
    if (wheel != 0 && editField == null && paletteTarget == null) settingsTarget -= wheel / 6f;
    int max = Math.max(0, settingsHeight() - layout.panelHeight + 8);
    settingsTarget = clamp(settingsTarget, 0, max);
    settingsScroll += (settingsTarget - settingsScroll) * .32f;
    Draw.rect(
        layout.panelX + 32,
        layout.panelY + 4,
        layout.panelX + 33,
        layout.panelY + layout.panelHeight - 4,
        0x2F9A9A9A);
    UiWidgets.cbButton(
        "<",
        layout.panelX + 3,
        layout.panelY + 4,
        27,
        27,
        Draw.inside(mx, my, layout.panelX + 3, layout.panelY + 4, 27, 27),
        false);
    Fonts.bold()
        .drawString(
            (selected.name + " SETTINGS").toUpperCase(Locale.ROOT),
            layout.panelX + 39,
            layout.panelY + 8,
            0xFFFFFFFF);
    Draw.rect(
        layout.panelX + 39,
        layout.panelY + 20,
        layout.panelX + layout.panelWidth - 8,
        layout.panelY + 21,
        0x2F9A9A9A);
    UiWidgets.beginClipPx(
        layout.panelX + 34, layout.panelY + 22, layout.panelWidth - 36, layout.panelHeight - 24);
    float y = layout.panelY + 27 - settingsScroll;
    if (selected.nativeModule != null) drawNativeSettings(selected.nativeModule, mx, my, y);
    else drawBridgeSettings(selected.bridgeMod, mx, my, y);
    UiWidgets.endClip();
    drawScrollbar(max, settingsScroll, layout.panelY + 24, layout.panelHeight - 28);
  }

  private Setting.Toggle achievementsToggle, textShadowToggle, borderlessToggle;

  private static <S extends Setting<?>> S withGroup(String group, S setting) {
    setting.setGroup(group);
    return setting;
  }

  /**
   * Modules that live on a settings tab (STYLE 0, PERFORMANCE 2, GENERAL 3) instead of having a card in the mods grid:
   * module name, tab, heading. Each gets a box on its tab: an on/off row, then its own options. The module itself is
   * unchanged (same saved settings, server rules and Lunar import). Chat and FPS Profiler were moved to cards
   * (2026-11-26, owner: mods and tools belong in the mods list, not in Settings).
   */
  private static final String[][] SETTINGS_HOME = {
      {"Numerical Enchantments", "3", "Numerical Enchantments"},
      {"Raw Input", "3", "Raw Input"},
      {"Scroll Tweaks", "3", "Scroll Tweaks"},
      {"SoundTweaks", "3", "Sound Tweaks"},
      {"Auto Reconnect", "3", "Auto Reconnect"},
      {"Confirm Disconnect", "3", "Confirm Disconnect"},
      {"Auto Center Inventory", "3", "Auto center inventory"},
      {"Screenshot Message Enhancer", "3", "Screenshots"},
      {"Discord Status", "3", "Discord Status"},
      {"Luka Online", "3", "Luka Online"},
      {"CrashPatch", "3", "Crash Patch"},
  };

  /** True when the module is listed on a settings tab, so it gets no card. */
  private static boolean livesInSettings(AbstractHudModule module) {
    for (String[] home : SETTINGS_HOME) if (home[0].equals(module.name())) return true;
    return false;
  }

  /** The on/off rows of the modules on the current settings tab, and the module each one switches. */
  private final java.util.Map<Setting<?>, AbstractHudModule> homeSwitches = new java.util.IdentityHashMap<>();
  /** Every row that belongs to a module on the current settings tab (its own settings, not client-wide ones). */
  private final java.util.Set<Setting<?>> homeRows = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

  private void addHomedModules(List<Setting<?>> list, int tab) {
    homeSwitches.clear();
    homeRows.clear();
    for (String[] home : SETTINGS_HOME) {
      if (Integer.parseInt(home[1]) != tab) continue;
      AbstractHudModule module = null;
      for (AbstractHudModule m : Cbui.hud().modules()) if (m.name().equals(home[0])) module = m;
      if (module == null) continue;
      Setting.Toggle on = new Setting.Toggle("Enabled", true, "Turn " + home[2] + " on or off");
      on.set(module.userEnabled());
      list.add(withGroup(home[2], on));
      homeSwitches.put(on, module);
      homeRows.add(on);
      List<Setting<?>> own = new ArrayList<>(module.basicSettings());
      if (!module.hasHud()) own.addAll(module.advancedSettings());
      for (Setting<?> s : own) {
        list.add(withGroup(home[2], s));
        homeRows.add(s);
      }
    }
  }

  private static final String[] SETTINGS_TABS = dev.cbui.Release.COSMETICS
      ? new String[] {"STYLE", "VIDEO", "PERFORMANCE", "GENERAL", "COSMETICS"}
      : new String[] {"STYLE", "VIDEO", "PERFORMANCE", "GENERAL"};
  private int settingsTab;

  /** The rows of the current settings tab (style copies apply to every module; the rest are client-wide switches). */
  private List<Setting<?>> buildGlobalSettings() {
    List<Setting<?>> list = new ArrayList<>();
    switch (settingsTab) {
      case 0:
        presetNames = dev.cbui.config.StylePresets.list();
        list.addAll(dev.cbui.hud.GlobalStyle.build(Cbui.hud().modules()));
        textShadowToggle = new Setting.Toggle("Text shadow", true, "Drop shadow on all text: chat, hotbar counts, tooltips and every HUD module");
        textShadowToggle.set(dev.cbui.config.GlobalOptions.textShadow);
        list.add(withGroup("Text", textShadowToggle));
        addHomedModules(list, 0);
        break;
      case 1:
        // the VIDEO tab is not shown (tabShown)
        break;
      case 2:
        list.addAll(dev.cbui.config.PerfOptions.build());
        addHomedModules(list, 2);
        break;
      case 3:
        borderlessToggle = new Setting.Toggle("Borderless fullscreen", false, "Remove the window frame and cover the whole screen (keeps alt-tab and other windows working)");
        borderlessToggle.set(dev.cbui.config.GlobalOptions.borderless);
        list.add(withGroup("Window", borderlessToggle));
        achievementsToggle = new Setting.Toggle("Hide achievement popups", false, "Never show the achievement toast");
        achievementsToggle.set(dev.cbui.config.GlobalOptions.hideAchievements);
        list.add(withGroup("Popups", achievementsToggle));
        addHomedModules(list, 3);
        break;
      default:
        break;
    }
    return list;
  }

  private static final int TAB_Y = 38, ROWS_Y = 62;

  /** The VIDEO tab (index 1) is not shown: the game's video options live on the Video Settings screen only. */
  private static boolean tabShown(int i) {
    return i != 1;
  }

  private int tabW(int i) {
    int gap = 3, total = layout.panelWidth - 24, sum = 0, n = 0;
    for (int k = 0; k < SETTINGS_TABS.length; k++) {
      if (!tabShown(k)) continue;
      sum += Fonts.bold().getStringWidth(SETTINGS_TABS[k]) + 22;
      n++;
    }
    int extra = (total - sum - gap * (n - 1)) / n;
    return Fonts.bold().getStringWidth(SETTINGS_TABS[i]) + 22 + Math.max(0, extra);
  }

  private int tabX(int i) {
    int x = layout.panelX + 12;
    for (int k = 0; k < i; k++) if (tabShown(k)) x += tabW(k) + 3;
    return x;
  }

  /** The settings tab: tabs along the top, the rows of the chosen one below (scrolls, clipped to the panel). */
  private void drawSettingsList(int mx, int my) {
    if (globalSettings == null) globalSettings = buildGlobalSettings();
    Fonts.bold().drawString("SETTINGS", layout.panelX + 12, layout.panelY + 10, 0xFFFFFFFF);
    Fonts.regular()
        .drawString(settingsTab == 0 ? "STYLE CHANGES APPLY TO EVERY HUD MODULE" : "CLIENT-WIDE", layout.panelX + 12, layout.panelY + 23, MUTED);
    for (int i = 0; i < SETTINGS_TABS.length; i++)
      if (tabShown(i)) UiWidgets.cbButton(
          SETTINGS_TABS[i], tabX(i), layout.panelY + TAB_Y, tabW(i), 16,
          Draw.inside(mx, my, tabX(i), layout.panelY + TAB_Y, tabW(i), 16), i == settingsTab);
    int wheel = takeWheel();
    if (wheel != 0 && editField == null && paletteTarget == null) settingsTarget -= wheel / 6f;
    int content = (int) rowsHeight(globalSettings) + 12 + (settingsTab == 0 ? presetsHeight() : 0);
    int viewport = layout.panelHeight - ROWS_Y - 4;
    settingsTarget = clamp(settingsTarget, 0, Math.max(0, content - viewport));
    settingsScroll += (settingsTarget - settingsScroll) * .32f;
    UiWidgets.beginClipPx(layout.panelX + 4, layout.panelY + ROWS_Y - 2, layout.panelWidth - 8, viewport + 2);
    int x = layout.panelX + 12, w = layout.panelWidth - 24;
    if (settingsTab == 4) {
      Fonts.bold().drawString("COSMETICS", x, layout.panelY + ROWS_Y + 4 - settingsScroll, 0xFFFFFFFF);
      Fonts.regular().drawString("CAPES: PICK ONE FROM THE LIBRARY. WINGS AND HATS ARE NOT IN YET.", x, layout.panelY + ROWS_Y + 20 - settingsScroll, MUTED);
      float by = layout.panelY + ROWS_Y + 40 - settingsScroll;
      UiWidgets.cbButton("OPEN CAPE SELECTION", x, by, w, 19, Draw.inside(mx, my, x, by, w, 19), false);
    } else {
      float top = layout.panelY + ROWS_Y - settingsScroll;
      if (settingsTab == 0) top = drawPresets(x, top, w, mx, my);
      drawSettingRows(globalSettings, x, top, w, mx, my);
    }
    UiWidgets.endClip();
    drawScrollbar(Math.max(0, content - viewport), settingsScroll, layout.panelY + ROWS_Y, viewport);
  }

  private float drawNativeSettings(AbstractHudModule module, int mx, int my, float y) {
    int x = layout.panelX + 42, w = layout.panelWidth - 54;
    hint("Turn this module on or off", mx, my, x, y, w, 21);
    y = drawToggleRow("ENABLED", module.userEnabled(), x, y, w, mx, my);
    boolean hud = module.hasHud();
    if (hud) {
      hint("Make the whole module bigger or smaller (also: drag a corner in the HUD editor)", mx, my, x, y, w, 21);
      y = drawSliderRow("SCALE", module.scale(), AbstractHudModule.MIN_SCALE, AbstractHudModule.MAX_SCALE, x, y, w, mx, my);
    }
    y = drawSettingRows(module.basicSettings(), x, y, w, mx, my);
    if (hud) {
      hint("Show or hide the fine-tuning options (box colours, padding, border, font)", mx, my, x, y, w, 19);
      UiWidgets.cbButton(
          showAdvanced ? "FEWER OPTIONS" : "MORE OPTIONS", x, y, w, 19, Draw.inside(mx, my, x, y, w, 19), false);
      y += 24;
    }
    if (hud && showAdvanced) {
      hint("The screen corner or edge the module sticks to, so it keeps its place when the window is resized", mx, my, x, y, w, 21);
      y = drawChoiceRow("ANCHOR", module.anchor().name(), x, y, w, mx, my);
      y = drawSettingRows(module.advancedSettings(), x, y, w, mx, my);
    }
    hint("Put this module's position, size and every setting back to the defaults", mx, my, x, y, w, 19);
    UiWidgets.cbButton("RESET MODULE", x, y, w, 19, Draw.inside(mx, my, x, y, w, 19), false);
    return y + 24;
  }

  private static final int GROUP_HEAD = 17, GROUP_GAP = 7;

  private static int rowHeight(Setting<?> setting) {
    return setting instanceof Setting.Slider ? 22 : 23;
  }

  /** Extra space above row {@code i}: the heading of the group it starts. */
  private static int groupBefore(List<Setting<?>> list, int i) {
    String g = list.get(i).group();
    return !g.isEmpty() && (i == 0 || !list.get(i - 1).group().equals(g)) ? GROUP_HEAD : 0;
  }

  /** Extra space below row {@code i}: the gap after the last row of a group. */
  private static int groupAfter(List<Setting<?>> list, int i) {
    String g = list.get(i).group();
    return !g.isEmpty() && (i == list.size() - 1 || !list.get(i + 1).group().equals(g)) ? GROUP_GAP : 0;
  }

  private static float rowsHeight(List<Setting<?>> list) {
    float h = 0;
    for (int i = 0; i < list.size(); i++) h += groupBefore(list, i) + rowHeight(list.get(i)) + groupAfter(list, i);
    return h;
  }

  private String hintText;

  /** Remembers a hover hint for this frame when the mouse is on the row (drawn last, on top). */
  private void hint(String tip, int mx, int my, int x, float y, int w, float h) {
    if (tip == null || tip.isEmpty() || paletteTarget != null || editField != null) return;
    if (my < layout.panelY + 22 || my > layout.panelY + layout.panelHeight - 2) return;
    if (Draw.inside(mx, my, x - 4, y, w + 8, h)) hintText = tip;
  }

  private float drawSettingRows(List<Setting<?>> list, int x, float y, int w, int mx, int my) {
    for (int i = 0; i < list.size(); i++) {
      Setting<?> setting = list.get(i);
      if (groupBefore(list, i) > 0) {
        int j = i;
        float run = 0;
        while (j < list.size() && list.get(j).group().equals(setting.group())) run += rowHeight(list.get(j++));
        Draw.rounded(x - 6, y + GROUP_HEAD - 3, x + w + 6, y + GROUP_HEAD + run + 1, 4, 0xFF130F19);
        Fonts.bold().drawString(setting.group().toUpperCase(Locale.ROOT), x, y + 3, 0xFFB57BFF);
        y += GROUP_HEAD;
      }
      float rowY = y;
      if (setting instanceof Setting.Toggle)
        y = drawToggleRow(setting.name(), ((Setting.Toggle) setting).get(), x, y, w, mx, my);
      else if (setting instanceof Setting.Slider) {
        Setting.Slider sl = (Setting.Slider) setting;
        y = drawSliderRow(sl.name(), sl.get(), sl.min(), sl.max(), x, y, w, mx, my);
      } else if (setting instanceof Setting.Color)
        y = drawColorRow(setting.name(), ((Setting.Color) setting).get(), x, y, w);
      else if (setting instanceof Setting.Choice)
        y = drawChoiceRow(setting.name(), ((Setting.Choice) setting).get(), x, y, w, mx, my);
      else if (setting instanceof Setting.Key)
        y = drawChoiceRow(setting.name(), editing == setting ? "PRESS A KEY" : keyName(((Setting.Key) setting).code()), x, y, w, mx, my);
      else if (setting instanceof Setting.Text)
        y = drawTextRow(setting.name(), ((Setting.Text) setting).get(), x, y, w);
      if (changed(setting)) drawReset(x + 113, rowY, mx, my);
      hint(setting.tooltip(), mx, my, x, rowY, w, rowHeight(setting));
      y += groupAfter(list, i);
    }
    return y;
  }

  private static final int ROW_LIMIT = 7;

  private static boolean changed(Setting<?> setting) {
    return !java.util.Objects.equals(setting.get(), setting.defaultValue());
  }

  private static boolean changed(ConfigSnapshot.Row row) {
    if (row.defaultValue == null) return false;
    Object now = row.get();
    return !java.util.Objects.equals(now, row.defaultValue)
        && !(now instanceof Number
            && row.defaultValue instanceof Number
            && ((Number) now).doubleValue() == ((Number) row.defaultValue).doubleValue());
  }

  /** Rows that work in this menu (not hidden, not unsupported), without the part folded behind "show more". */
  private List<ConfigSnapshot.Row> rowsOf(ConfigSnapshot.Section section) {
    List<ConfigSnapshot.Row> all = supportedRows(section);
    if (all.size() <= ROW_LIMIT + 1 || openSections.contains(section.name + "#more")) return all;
    return all.subList(0, ROW_LIMIT);
  }

  private static List<ConfigSnapshot.Row> supportedRows(ConfigSnapshot.Section section) {
    List<ConfigSnapshot.Row> out = new ArrayList<>();
    for (ConfigSnapshot.Row row : section.rows)
      if (row.display() != ConfigSnapshot.Display.HIDDEN && row.kind != ConfigSnapshot.Kind.UNSUPPORTED) out.add(row);
    return out;
  }

  private int foldedRows(ConfigSnapshot.Section section) {
    return supportedRows(section).size() - rowsOf(section).size();
  }

  private float drawBridgeSettings(ConfigSnapshot.Mod mod, int mx, int my, float y) {
    int x = layout.panelX + 42, w = layout.panelWidth - 54;
    for (ConfigSnapshot.Section section : mod.sections) {
      boolean open = openSections.contains(section.name);
      boolean hover = Draw.inside(mx, my, x, y, w, 15);
      Fonts.bold()
          .drawString(
              (open ? "- " : "+ ") + section.name.toUpperCase(Locale.ROOT) + "  (" + supportedRows(section).size() + ")",
              x,
              y + 3,
              hover ? 0xFFDCE1E9 : MUTED);
      y += 16;
      if (!open) continue;
      for (ConfigSnapshot.Row row : rowsOf(section)) {
        Object value = row.get();
        float rowY = y;
        switch (row.kind) {
          case TOGGLE:
            y = drawToggleRow(row.label, Boolean.TRUE.equals(value), x, y, w, mx, my);
            break;
          case SLIDER:
          case NUMBER:
            y = drawSliderRow(row.label, number(value), row.min, row.max, x, y, w, mx, my);
            break;
          case COLOR:
            y = drawColorRow(row.label, color(value), x, y, w);
            break;
          case DROPDOWN:
            y = drawChoiceRow(row.label, valueText(value), x, y, w, mx, my);
            break;
          case KEYBIND:
            y =
                drawChoiceRow(
                    row.label,
                    editing == row && editField == null ? "PRESS A KEY" : keyName(value),
                    x,
                    y,
                    w,
                    mx,
                    my);
            break;
          case TEXT:
            y = drawTextRow(row.label, valueText(value), x, y, w);
            break;
          case BUTTON:
            UiWidgets.cbButton(row.label, x, y, w, 18, Draw.inside(mx, my, x, y, w, 18), false);
            y += 22;
            break;
          case INFO:
            Fonts.regular()
                .drawString(ellipsize(row.label + ": " + valueText(value), w), x, y + 4, MUTED);
            y += 18;
            break;
          case UNSUPPORTED:
            break;
        }
        if (row.kind != ConfigSnapshot.Kind.INFO
            && row.kind != ConfigSnapshot.Kind.BUTTON
            && changed(row)) drawReset(x + 113, rowY, mx, my);
      }
      int folded = foldedRows(section);
      if (folded > 0) {
        UiWidgets.cbButton(
            "SHOW " + folded + " MORE", x, y, w, 19, Draw.inside(mx, my, x, y, w, 19), false);
        y += 24;
      } else if (open && supportedRows(section).size() > ROW_LIMIT + 1) {
        UiWidgets.cbButton("SHOW LESS", x, y, w, 19, Draw.inside(mx, my, x, y, w, 19), false);
        y += 24;
      }
    }
    return y;
  }

  private void drawReset(float x, float y, int mx, int my) {
    Fonts.bold()
        .drawString("R", x, y + 5, Draw.inside(mx, my, x - 2, y, 12, 18) ? 0xFF7A55A5 : 0xFF6F7886);
  }

  private float drawToggleRow(String label, boolean on, int x, float y, int w, int mx, int my) {
    Fonts.regular().drawString(ellipsize(label, 108), x + 2, y + 5, 0xFFDCE1E9);
    UiWidgets.toggleSwitch(
        x + w - 58, y + 2, 54, 16, on, Draw.inside(mx, my, x + w - 58, y + 2, 54, 16));
    return y + 23;
  }

  private float drawSliderRow(
      String label, double value, double min, double max, int x, float y, int w, int mx, int my) {
    String text = format(value);
    Fonts.regular()
        .drawString(
            ellipsize(label, 100 - Fonts.regular().getStringWidth(text)),
            x + 2,
            y + 5,
            0xFFDCE1E9);
    Fonts.regular().drawString(text, x + 107 - Fonts.regular().getStringWidth(text), y + 5, MUTED);
    float sx = x + 124, sw = w - 128, p = max == min ? 0 : (float) ((value - min) / (max - min));
    UiWidgets.slider(sx, y + 8, sw, p, Draw.inside(mx, my, sx, y, sw, 16));
    return y + 22;
  }

  private float drawColorRow(String label, int color, int x, float y, int w) {
    Fonts.regular().drawString(ellipsize(label, 108), x + 2, y + 5, 0xFFDCE1E9);
    Draw.rounded(x + w - 92, y + 2, x + w - 60, y + 18, 3, color);
    UiWidgets.field(String.format("#%08X", color), x + w - 56, y, 54, 19, false);
    return y + 23;
  }

  private float drawChoiceRow(String label, String value, int x, float y, int w, int mx, int my) {
    Fonts.regular().drawString(ellipsize(label, 108), x + 2, y + 5, 0xFFDCE1E9);
    UiWidgets.choice(
        ellipsize(value, 80),
        x + w - 110,
        y,
        106,
        19,
        Draw.inside(mx, my, x + w - 110, y, 106, 19));
    return y + 23;
  }

  private float drawTextRow(String label, String value, int x, float y, int w) {
    Fonts.regular().drawString(ellipsize(label, 108), x + 2, y + 5, 0xFFDCE1E9);
    UiWidgets.field(ellipsize(value, 92), x + w - 110, y, 106, 19, false);
    return y + 23;
  }

  // ---- style presets (top of the STYLE tab) ----

  private static final int PRESET_ROW = 23;

  /** Height of the presets box: heading, one row per preset, the save button, the gap below. */
  private int presetsHeight() {
    return GROUP_HEAD + presetNames.size() * PRESET_ROW + 24 + GROUP_GAP;
  }

  /** The presets box, drawn like a settings group; returns the y below it. */
  private float drawPresets(int x, float y, int w, int mx, int my) {
    float inner = presetNames.size() * PRESET_ROW + 22;
    Draw.rounded(x - 6, y + GROUP_HEAD - 3, x + w + 6, y + GROUP_HEAD + inner + 1, 4, 0xFF130F19);
    Fonts.bold().drawString("PRESETS", x, y + 3, 0xFFB57BFF);
    y += GROUP_HEAD;
    String active = dev.cbui.config.StylePresets.active();
    for (int i = 0; i < presetNames.size(); i++) {
      String name = presetNames.get(i);
      boolean on = name.equals(active), hover = Draw.inside(mx, my, x - 2, y, w + 4, 20);
      Draw.rounded(x - 2, y, x + w + 2, y + 20, 2, on ? 0xFF49286E : hover ? 0xFF221D28 : 0xCC1A151F);
      Fonts.regular().drawString(ellipsize(name, w - 130), x + 4, y + 6, 0xFFFFFFFF);
      if (on) Fonts.bold().drawString("ACTIVE", x + w - 106, y + 6, 0xFFD9C2FF);
      Fonts.regular().drawString("RENAME", x + w - 56, y + 6, Draw.inside(mx, my, x + w - 60, y, 40, 20) ? 0xFFFFFFFF : MUTED);
      Fonts.bold().drawString("X", x + w - 10, y + 6, Draw.inside(mx, my, x + w - 16, y, 18, 20) ? 0xFFFF6F8A : 0xFFA271DA);
      hint("Use this look on every HUD module", mx, my, x, y, w - 64, 20);
      y += PRESET_ROW;
    }
    hint("Save the style below as a new preset", mx, my, x, y, w, 19);
    UiWidgets.cbButton("SAVE CURRENT LOOK AS PRESET", x, y, w, 19, Draw.inside(mx, my, x, y, w, 19), false);
    return y + 24 + GROUP_GAP;
  }

  /** A click in the presets box; true when it was used. Same layout as {@link #drawPresets}. */
  private boolean presetsClick(int mx, int my, int x, float y, int w) {
    y += GROUP_HEAD;
    for (int i = 0; i < presetNames.size(); i++) {
      String name = presetNames.get(i);
      if (Draw.inside(mx, my, x + w - 16, y, 18, 20)) {
        deletePreset(name);
        return true;
      }
      if (Draw.inside(mx, my, x + w - 60, y, 40, 20)) {
        beginPresetEdit(name, true);
        return true;
      }
      if (Draw.inside(mx, my, x - 2, y, w + 4, 20)) {
        applyPreset(name);
        return true;
      }
      y += PRESET_ROW;
    }
    if (Draw.inside(mx, my, x, y, w, 19)) {
      beginPresetEdit(null, false);
      return true;
    }
    return false;
  }

  private void beginPresetEdit(String name, boolean rename) {
    beginProfileEdit(name, rename);
    presetModal = true;
  }

  private void commitPresetEdit() {
    String name = profileField.getText().trim();
    try {
      ProfileFiles.ensureName(name, "Preset");
      if (renameProfile) dev.cbui.config.StylePresets.rename((String) editing, name);
      else dev.cbui.config.StylePresets.create(name, Cbui.hud().modules());
      profileField = null;
      presetModal = false;
      editing = null;
      globalSettings = buildGlobalSettings();
    } catch (Exception error) {
      profileError = error.getMessage() == null ? "Could not save preset" : error.getMessage();
    }
  }

  private void applyPreset(String name) {
    try {
      dev.cbui.config.StylePresets.apply(name, Cbui.hud().modules());
      saveCurrent();
    } catch (Exception error) {
      Cbui.reportOnce("style preset apply", error);
    }
    globalSettings = buildGlobalSettings();
  }

  private void deletePreset(String name) {
    try {
      dev.cbui.config.StylePresets.delete(name);
    } catch (Exception error) {
      Cbui.reportOnce("style preset delete", error);
    }
    globalSettings = buildGlobalSettings();
  }

  private void drawProfiles(int mx, int my) {
    Fonts.bold().drawString("LOCAL PROFILES", layout.panelX + 10, layout.panelY + 11, 0xFFFFFFFF);
    UiWidgets.cbButton(
        "+ NEW",
        layout.panelX + layout.panelWidth - 57,
        layout.panelY + 6,
        48,
        19,
        Draw.inside(mx, my, layout.panelX + layout.panelWidth - 57, layout.panelY + 6, 48, 19),
        false);
    int y = layout.panelY + 34;
    drawProfileRow("Default", y, mx, my, false);
    y += 25;
    for (String name : profileNames) {
      drawProfileRow(name, y, mx, my, true);
      y += 25;
      if (y > layout.panelY + layout.panelHeight - 24) break;
    }
    if (profileNames.isEmpty() && lunarProfiles.isEmpty())
      Fonts.regular()
          .drawCentered(
              "CREATE A LOCAL PROFILE TO SAVE THIS LAYOUT", width / 2f, layout.panelY + 68, MUTED);
    // Lunar Client profiles on this PC: a click copies one into a new local profile
    int bottom = layout.panelY + layout.panelHeight - 24;
    if (!lunarProfiles.isEmpty() && y + 14 <= bottom) {
      y += 4;
      Fonts.bold().drawString("COPY FROM LUNAR CLIENT", layout.panelX + 10, y, MUTED);
      y += 13;
      for (String[] lunar : lunarProfiles) {
        if (y > bottom) break;
        boolean hover = Draw.inside(mx, my, layout.panelX + 6, y, layout.panelWidth - 12, 21);
        Draw.rounded(layout.panelX + 6, y, layout.panelX + layout.panelWidth - 6, y + 21, 2, hover ? 0xFF221D28 : 0xCC1A151F);
        Fonts.regular().drawString(ellipsize(lunar[1], layout.panelWidth - 100), layout.panelX + 13, y + 7, 0xFFFFFFFF);
        Fonts.bold().drawString("COPY", layout.panelX + layout.panelWidth - 40, y + 7, hover ? RED : MUTED);
        y += 25;
      }
    }
    if (!lunarStatus.isEmpty())
      Fonts.regular().drawString(ellipsize(lunarStatus, layout.panelWidth - 20), layout.panelX + 10,
          layout.panelY + layout.panelHeight - 14, 0xFFD9C2FF);
  }

  private void drawProfileRow(String name, int y, int mx, int my, boolean editable) {
    boolean active = name.equals(activeProfile),
        hover = Draw.inside(mx, my, layout.panelX + 6, y, layout.panelWidth - 12, 21);
    Draw.rounded(
        layout.panelX + 6,
        y,
        layout.panelX + layout.panelWidth - 6,
        y + 21,
        2,
        active ? 0xFF49286E : hover ? 0xFF221D28 : 0xCC1A151F);
    Fonts.regular()
        .drawString(
            ellipsize(name, layout.panelWidth - 100), layout.panelX + 13, y + 7, 0xFFFFFFFF);
    if (active)
      Fonts.bold().drawString("ACTIVE", layout.panelX + layout.panelWidth - 100, y + 7, RED);
    if (editable) {
      Fonts.regular().drawString("RENAME", layout.panelX + layout.panelWidth - 66, y + 7, MUTED);
      Fonts.bold().drawString("X", layout.panelX + layout.panelWidth - 18, y + 7, 0xFFA271DA);
    }
  }

  private void drawProfileModal() {
    drawRect(0, 0, width, height, 0xA0000000);
    int x = width / 2 - 78, y = height / 2 - 24;
    Draw.rounded(x, y, x + 156, y + 55, 4, 0xFF201B27);
    Fonts.bold()
        .drawString(presetModal ? (renameProfile ? "RENAME PRESET" : "SAVE STYLE PRESET") : renameProfile ? "RENAME PROFILE" : "CREATE PROFILE", x + 8, y + 8, 0xFFFFFFFF);
    profileField.drawTextBox();
    if (!profileError.isEmpty())
      Fonts.regular().drawString(ellipsize(profileError, 145), x + 7, y + 42, 0xFFAB71EE);
  }

  /**
   * The HUD layout editor. It edits every HUD box in one place; the boxes are drawn here with their
   * sample content. Drag to move, scroll to scale, right click to switch on/off.
   */
  private void drawHudEditor(int mx, int my, float partial) {
    boolean peek = Mouse.isButtonDown(1); // hold right click to see the real HUD without guides or buttons
    drawGradientRect(0, 0, width, height, peek ? 0x18000000 : 0x38000000, peek ? 0x28000000 : 0x58000000);
    int thirdW = width / 3, thirdH = height / 3;
    if (!peek) {
      Draw.rect(thirdW, 0, thirdW + 1, height, 0x3FFFFFFF);
      Draw.rect(thirdW * 2, 0, thirdW * 2 + 1, height, 0x3FFFFFFF);
      Draw.rect(0, thirdH, width, thirdH + 1, 0x3FFFFFFF);
      Draw.rect(0, thirdH * 2, width, thirdH * 2 + 1, 0x3FFFFFFF);
    }
    List<HudElement> elements = Cbui.hud().editorElements();
    HudElement hover = dragging != null ? dragging : hoveredElement(elements, mx, my);
    int wheel = takeWheel();
    if (wheel != 0 && hover != null && dragging == null) {
      history.push(Cbui.hud().modules());
      hover.scale(hover.scale() + (wheel > 0 ? .05f : -.05f));
      hover.dragEnd();
      saveCurrent();
    }
    ScaledResolution resolution = dev.luka.compat.Compat.scaled(mc);
    GlStateManager.pushMatrix();
    GlStateManager.scale(sf, sf, 1f);
    for (HudElement e : elements) {
      AbstractHudModule m = e.module();
      if (m == null || e.width() <= 0) continue;
      try {
        m.render(resolution, partial, true);
      } catch (Throwable error) {
        Cbui.reportOnce("HUD editor preview: " + m.name(), error);
      }
    }
    GlStateManager.popMatrix();
    if (peek) return;
    int line = sf >= 3 ? 2 : 1;
    for (HudElement e : elements) {
      if (e.width() <= 0 || e.height() <= 0) continue;
      float x = e.x() * sf, y = e.y() * sf, w = e.width() * sf, h = e.height() * sf;
      boolean visible = e.hudVisible(), hot = e == hover;
      if (!visible) Draw.rect(x, y, x + w, y + h, 0xA0080709);
      if (hot) Draw.rect(x, y, x + w, y + h, 0x308942DA);
      int c = e.locked() ? 0xFF8A93A3 : visible ? RED : 0xFF5C6370;
      Draw.rect(x, y, x + w, y + line, c);
      Draw.rect(x, y + h - line, x + w, y + h, c);
      Draw.rect(x, y, x + line, y + h, c);
      Draw.rect(x + w - line, y, x + w, y + h, c);
      if (e == hudSelected && e.module() != null) {
        Draw.rect(x - 1, y - 1, x + w + 1, y, 0xFFFFFFFF);
        Draw.rect(x - 1, y + h, x + w + 1, y + h + 1, 0xFFFFFFFF);
        Draw.rect(x - 1, y, x, y + h, 0xFFFFFFFF);
        Draw.rect(x + w, y, x + w + 1, y + h, 0xFFFFFFFF);
        for (int corner = 0; corner < 4; corner++) {
          float[] r = cornerRect(e, corner);
          Draw.rect(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xFFFFFFFF);
          Draw.rect(r[0] + 1, r[1] + 1, r[0] + r[2] - 1, r[1] + r[3] - 1, RED);
        }
      }
      String action = drawBoxButtons(e, mx, my);
      if (hot) {
        String tag = action != null ? e.name() + action : e.name() + (visible ? "" : "  (HIDDEN)") + String.format(Locale.ROOT, "  %.2fx", e.scale());
        int tw = Fonts.regular().getStringWidth(tag);
        float ty = y >= 30 * ui() ? y - 30 * ui() : y + h + 17 * ui();
        Draw.rounded(x, ty, x + tw + 8, ty + 12, 2, 0xE00E0C10);
        Fonts.regular().drawString(tag, x + 4, ty + 1, 0xFFFFFFFF);
      }
    }
    if (dragging != null) drawSnapGuides(dragging, elements);
    String hint =
        "DRAG TO MOVE  -  CORNERS RESIZE  -  ARROWS NUDGE  -  CTRL+Z UNDO  -  EYE HIDES  -  RED X TURNS OFF  -  HOLD RIGHT CLICK TO PEEK";
    // everything below is drawn in "ui space" (scaled with the window) so the bar stays readable on big screens
    float u = ui();
    int umx = (int) (mx / u), umy = (int) (my / u);
    float uw = width / u;
    GlStateManager.pushMatrix();
    GlStateManager.scale(u, u, 1f);
    // a backing plate keeps the instructions readable when a HUD box (e.g. the direction bar) sits at the top centre
    List<String> hintRows = hintLines(hint, uw - 240); // RESET/ADD on the left, DONE on the right
    float hintW = Fonts.bold().getStringWidth("HUD LAYOUT");
    for (String row : hintRows) hintW = Math.max(hintW, Fonts.regular().getStringWidth(row));
    float plate = hintW / 2f + 10;
    Draw.rounded(uw / 2f - plate, 3, uw / 2f + plate, 23 + 11 * hintRows.size(), 3, 0xEE0E0C10);
    Fonts.bold().drawCentered("HUD LAYOUT", uw / 2f, 9, 0xFFFFFFFF);
    float rowY = 22;
    for (String row : hintRows) {
      Fonts.regular().drawCentered(row, uw / 2f, rowY, 0xFFB2BAC7);
      rowY += 11;
    }
    UiWidgets.cbButton("DONE", uw - 54, 5, 48, 20, Draw.inside(umx, umy, uw - 54, 5, 48, 20), false);
    UiWidgets.cbButton("RESET", 6, 5, 50, 20, Draw.inside(umx, umy, 6, 5, 50, 20), false);
    UiWidgets.cbButton(libraryOpen ? "CLOSE" : "ADD", 60, 5, 50, 20, Draw.inside(umx, umy, 60, 5, 50, 20), libraryOpen);
    if (libraryOpen) drawLibrary(umx, umy);
    GlStateManager.popMatrix();
  }

  /** A hint made of "  -  " separated parts, split into lines no wider than maxWidth (at least one part per line). */
  private static List<String> hintLines(String hint, float maxWidth) {
    List<String> lines = new ArrayList<String>();
    StringBuilder line = new StringBuilder();
    for (String part : hint.split("  -  ")) {
      String trial = line.length() == 0 ? part : line + "  -  " + part;
      if (line.length() > 0 && Fonts.regular().getStringWidth(trial) > maxWidth) {
        lines.add(line.toString());
        line = new StringBuilder(part);
      } else {
        line = new StringBuilder(trial);
      }
    }
    if (line.length() > 0) lines.add(line.toString());
    return lines;
  }

  // ---- HUD editor helpers (resize handles, snap guides, library, undo) ----

  private static final int GUIDE = 0xCCB26FFF;

  private float[] cornerRect(HudElement e, int corner) {
    float x = e.x() * sf, y = e.y() * sf, w = e.width() * sf, h = e.height() * sf;
    float cx = (corner & 1) == 0 ? x : x + w, cy = corner < 2 ? y : y + h, u = ui();
    return new float[] {cx - 4 * u, cy - 4 * u, 8 * u, 8 * u};
  }

  private int cornerAt(HudElement e, int mx, int my) {
    for (int c = 0; c < 4; c++) {
      float[] r = cornerRect(e, c);
      if (Draw.inside(mx, my, r[0], r[1], r[2], r[3])) return c;
    }
    return -1;
  }

  /** Lines where the dragged box lines up with the screen centre or with another box's edges/centre. */
  private void drawSnapGuides(HudElement d, List<HudElement> elements) {
    float x = d.x() * sf, y = d.y() * sf, w = d.width() * sf, h = d.height() * sf;
    float[] xs = {x, x + w / 2, x + w}, ys = {y, y + h / 2, y + h};
    for (float v : xs) if (Math.abs(v - baseW / 2f) < 1.5f) Draw.rect(baseW / 2f, 0, baseW / 2f + 1, baseH, GUIDE);
    for (float v : ys) if (Math.abs(v - baseH / 2f) < 1.5f) Draw.rect(0, baseH / 2f, baseW, baseH / 2f + 1, GUIDE);
    for (HudElement o : elements) {
      if (o == d || o.width() <= 0) continue;
      float ox = o.x() * sf, oy = o.y() * sf, ow = o.width() * sf, oh = o.height() * sf;
      float[] oxs = {ox, ox + ow / 2, ox + ow}, oys = {oy, oy + oh / 2, oy + oh};
      for (float a : xs)
        for (float b : oxs)
          if (Math.abs(a - b) < 1.5f) Draw.rect(b, Math.min(y, oy), b + 1, Math.max(y + h, oy + oh), GUIDE);
      for (float a : ys)
        for (float b : oys)
          if (Math.abs(a - b) < 1.5f) Draw.rect(Math.min(x, ox), b, Math.max(x + w, ox + ow), b + 1, GUIDE);
    }
  }

  private List<AbstractHudModule> libraryModules() {
    List<AbstractHudModule> out = new ArrayList<>();
    for (AbstractHudModule m : Cbui.hud().modules()) if (m.hasHud() && !m.enabled()) out.add(m);
    return out;
  }

  private void drawLibrary(int mx, int my) {
    List<AbstractHudModule> mods = libraryModules();
    int rows = Math.max(1, mods.size());
    float px = 6, py = 30, pw = 150, ph = 24 + rows * 14;
    Draw.rounded(px, py, px + pw, py + ph, 3, 0xEE0E0C10);
    Fonts.bold().drawString("HUD LIBRARY", px + 6, py + 6, 0xFFFFFFFF);
    if (mods.isEmpty()) {
      Fonts.regular().drawString("ALL MODULES ARE ON", px + 6, py + 22, MUTED);
      return;
    }
    for (int i = 0; i < mods.size(); i++) {
      float ry = py + 20 + i * 14;
      boolean hover = Draw.inside(mx, my, px + 2, ry, pw - 4, 14);
      if (hover) Draw.rect(px + 2, ry, px + pw - 2, ry + 14, 0xFF322343);
      Fonts.bold().drawString("+", px + 6, ry + 3, RED);
      Fonts.regular().drawString(mods.get(i).name(), px + 18, ry + 3, 0xFFDCE1E9);
    }
  }

  /** Click inside the library panel: turns a module on and puts it back on the HUD. Returns true when the click was in the panel. */
  private boolean libraryClick(int mx, int my) {
    if (!libraryOpen) return false;
    mx = (int) (mx / ui());
    my = (int) (my / ui());
    List<AbstractHudModule> mods = libraryModules();
    int rows = Math.max(1, mods.size());
    float px = 6, py = 30, pw = 150, ph = 24 + rows * 14;
    if (!Draw.inside(mx, my, px, py, pw, ph)) return false;
    for (int i = 0; i < mods.size(); i++) {
      if (Draw.inside(mx, my, px + 2, py + 20 + i * 14, pw - 4, 14)) {
        history.push(Cbui.hud().modules());
        mods.get(i).enabled(true);
        mods.get(i).hudVisible(true);
        saveCurrent();
        break;
      }
    }
    return true;
  }

  /** Keyboard shortcuts of the HUD editor; true when the key was used. */
  private boolean handleHudKey(int key) {
    boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    List<AbstractHudModule> modules = Cbui.hud().modules();
    if (ctrl && (key == Keyboard.KEY_Z || key == Keyboard.KEY_Y)) {
      boolean did = (key == Keyboard.KEY_Y || shift) ? history.redo(modules) : history.undo(modules);
      if (did) {
        if (hudSelected != null && !hudSelected.enabled()) hudSelected = null;
        saveCurrent();
      }
      return true;
    }
    HudElement e = hudSelected;
    if (e == null || e.locked()) return false;
    float step = shift ? 5 : 1;
    float dx = 0, dy = 0;
    if (key == Keyboard.KEY_LEFT) dx = -step;
    else if (key == Keyboard.KEY_RIGHT) dx = step;
    else if (key == Keyboard.KEY_UP) dy = -step;
    else if (key == Keyboard.KEY_DOWN) dy = step;
    else if (key == Keyboard.KEY_DELETE) {
      history.push(modules);
      e.enabled(false);
      hudSelected = null;
      saveCurrent();
      return true;
    } else return false;
    history.push(modules);
    e.moveTo(e.x() + dx, e.y() + dy);
    e.dragEnd();
    saveCurrent();
    return true;
  }

  /** The buttons above a box's top-right corner (below it when the box is at the top edge): 0 = cog (settings), 1 = eye (hide), 2 = red X (turn off). */
  private float[] hudButton(HudElement e, int index) {
    float u = ui();
    float x = e.x() * sf, y = e.y() * sf, w = e.width() * sf, h = e.height() * sf;
    float bx = x + w - 43 * u + index * 15 * u, by = y >= 15 * u ? y - 15 * u : y + h + 3 * u;
    return new float[] {bx, by, 13 * u, 12 * u};
  }

  /** Draws a box's settings, hide and disable buttons; returns what the one under the mouse does, or null. Takes framebuffer pixels. */
  private String drawBoxButtons(HudElement e, int mx, int my) {
    float[] cog = hudButton(e, 0), i = hudButton(e, 1), xb = hudButton(e, 2);
    boolean visible = e.hudVisible();
    boolean hoverC = e.module() != null && Draw.inside(mx, my, cog[0], cog[1], cog[2], cog[3]);
    boolean hoverI = Draw.inside(mx, my, i[0], i[1], i[2], i[3]), hoverX = Draw.inside(mx, my, xb[0], xb[1], xb[2], xb[3]);
    if (e.module() != null) {
      Draw.rounded(cog[0], cog[1], cog[0] + cog[2], cog[1] + cog[3], 2, hoverC ? RED : 0xF00E0C10);
      Draw.texture(COG_ICON, cog[0] + 2 * ui(), cog[1] + ui(), 9 * ui(), 9 * ui(), 1f);
    }
    // hide/show on the HUD: an eye while shown, a crossed eye while hidden
    Draw.rounded(i[0], i[1], i[0] + i[2], i[1] + i[3], 2, hoverI ? RED : 0xF00E0C10);
    Draw.texture(visible ? EYE_ICON : EYE_OFF_ICON, i[0] + 2 * ui(), i[1] + 1.5f * ui(), 9 * ui(), 9 * ui(), visible ? 1f : .7f);
    // turn the module off: always a red X (white on red while hovered)
    Draw.rounded(xb[0], xb[1], xb[0] + xb[2], xb[1] + xb[3], 2, hoverX ? 0xFFC42B2B : 0xF00E0C10);
    drawHudX(xb, hoverX ? 0xFFFFFFFF : REMOVE_RED);
    if (hoverC) return "  -  SETTINGS";
    if (hoverX) return "Scoreboard".equals(e.name()) ? "  -  TURN OFF (HIDES THE SCOREBOARD)" : "  -  TURN OFF";
    if (hoverI) return visible ? "  -  HIDE FROM HUD" : "  -  SHOW ON HUD";
    return null;
  }

  /** Runs the box button under the mouse, if there is one. Takes framebuffer pixels. */
  private boolean boxButtonClick(List<HudElement> elements, int mx, int my) {
    Object[] clicked = hudButtonAt(elements, mx, my);
    if (clicked == null) return false;
    HudElement target = (HudElement) clicked[0];
    int which = (Integer) clicked[1];
    if (which == 0) {
      openModuleSettings(target);
      return true;
    }
    history.push(Cbui.hud().modules());
    if (which == 1) target.hudVisible(!target.hudVisible());
    else {
      target.enabled(false);
      if (hudSelected == target) hudSelected = null;
    }
    saveCurrent();
    return true;
  }

  /** Size of the editor's own controls (buttons, handles, top bar): grows with the window so they stay readable. */
  private float ui() {
    return Math.max(1f, Math.min(2f, baseH / 600f));
  }

  private boolean inUi(int mx, int my, float x, float y, float w, float h) {
    float u = ui();
    return Draw.inside((int) (mx / u), (int) (my / u), x, y, w, h);
  }

  /** A bold X centred in a HUD box button, in the given colour. */
  private void drawHudX(float[] r, int color) {
    float u = ui();
    GlStateManager.pushMatrix();
    GlStateManager.scale(u, u, 1f);
    Fonts.bold().drawCentered("X", (r[0] + r[2] / 2f) / u, r[1] / u + 2, color);
    GlStateManager.popMatrix();
  }

  /** Opens the settings page of the module behind a HUD box; Back returns to the editor. */
  private void openModuleSettings(HudElement element) {
    AbstractHudModule module = element.module();
    if (module == null) return;
    saveCurrent();
    for (Card card : allCards) {
      if (card.nativeModule != module) continue;
      selected = card;
      resetPage(card);
      fromCards = false;
      returnToHud = true;
      hudReturnView = view;
      settingsScroll = settingsTarget = 0;
      view = View.SETTINGS;
      return;
    }
  }

  /** Which button (0 = cog, 1 = I, 2 = X) of which box is under the mouse, or null. */
  private Object[] hudButtonAt(List<HudElement> elements, int mx, int my) {
    for (int i = elements.size() - 1; i >= 0; i--) {
      HudElement e = elements.get(i);
      if (e.width() <= 0) continue;
      for (int b = 0; b < 3; b++) {
        if (b == 0 && e.module() == null) continue; // another mod's HUD has no CBUI settings page
        float[] r = hudButton(e, b);
        if (Draw.inside(mx, my, r[0], r[1], r[2], r[3])) return new Object[] {e, b};
      }
    }
    return null;
  }

  /** Topmost box under the mouse, or one whose buttons the mouse is on (drawn last = on top). */
  private HudElement hoveredElement(List<HudElement> elements, int mx, int my) {
    Object[] button = hudButtonAt(elements, mx, my);
    if (button != null) return (HudElement) button[0];
    for (int i = elements.size() - 1; i >= 0; i--) {
      HudElement e = elements.get(i);
      if (e.width() > 0 && Draw.inside(mx, my, e.x() * sf, e.y() * sf, e.width() * sf, e.height() * sf))
        return e;
    }
    return null;
  }

  @Override
  protected void mouseClicked(int mx, int my, int button) {
    try {
      // a keybind waiting for its key: middle, back, forward and the other extra mouse buttons can be the key (stored as -100 + button, as in Controls)
      if (button >= 2 && editing instanceof Setting.Key && editField == null) {
        ((Setting.Key) editing).set(button - 100);
        editing = null;
        saveCurrent();
        return;
      }
      if (button >= 2 && editing instanceof ConfigSnapshot.Row && editField == null && ((ConfigSnapshot.Row) editing).kind == ConfigSnapshot.Kind.KEYBIND) {
        setKey((ConfigSnapshot.Row) editing, editOriginal, button - 100);
        editing = null;
        editOriginal = null;
        return;
      }
      if (profileField != null) {
        profileField.mouseClicked(mx, my, button);
        return;
      }
      if (view == View.HUD) {
        hudClick(mx, my, button);
        return;
      }
      if (view == View.HUB) {
        hubClick(mx, my, button);
        return;
      }
      if (paletteTarget != null) {
        if (button == 0) paletteClick(mx, my);
        else paletteTarget = null;
        return;
      }
      if (button != 0) return;
      int center = width / 2;
      if (Draw.inside(mx, my, center - 82, layout.toolbarY, 28, 28)) {
        view = View.PROFILES;
        selected = null;
        return;
      }
      if (Draw.inside(mx, my, center - 50, layout.toolbarY, 100, 28)) {
        view = View.CARDS;
        selected = null;
        return;
      }
      if (Draw.inside(mx, my, center + 54, layout.toolbarY, 28, 28)) {
        view = View.SETTINGS;
        selected = null;
        settingsScroll = settingsTarget = 0;
        globalSettings = buildGlobalSettings();
        return;
      }
      if (Draw.inside(mx, my, width / 2 + 90, layout.toolbarY, 42, 28)) {
        view = View.HUD;
        Cbui.hud().editorOpen(true);
        return;
      }
      if (view == View.CARDS) cardsClick(mx, my);
      else if (view == View.SETTINGS) settingsClick(mx, my);
      else profilesClick(mx, my);
    } catch (Throwable error) {
      Cbui.reportOnce("module menu input", error);
    }
  }

  private void cardsClick(int mx, int my) {
    // the whole search box is clickable, not just the 8 px the text field itself occupies
    boolean inSearchBox = Draw.inside(mx, my, layout.panelX + 6, layout.panelY + 5, layout.panelWidth - 12, 18);
    search.mouseClicked(mx, inSearchBox ? layout.panelY + 12 : my, 0);
    int x = layout.panelX + 7, y = layout.panelY + 28;
    for (String item : layout.compact ? java.util.Collections.<String>emptyList() : categories) {
      int w = Math.max(37, Fonts.regular().getStringWidth(item) + 10);
      if (x + w > layout.panelX + layout.panelWidth - 5) break;
      if (Draw.inside(mx, my, x, y, w, 15)) {
        category = item;
        filterCards();
        return;
      }
      x += w + 3;
    }
    // the search bar and category chips sit over the top of the scrolled grid: a click there must not reach a card behind them
    if (my < layout.panelY + layout.strip || my > layout.panelY + layout.panelHeight) return;
    for (int i = 0; i < visibleCards.size(); i++) {
      Card card = visibleCards.get(i);
      int cx = layout.cardX(i),
          cy = layout.panelY + layout.strip + 4 + (i / layout.columns) * layout.cardPitchY - (int) scroll;
      if (!Draw.inside(mx, my, cx, cy, layout.cardWidth, layout.cardHeight)) continue;
      int row = cy + layout.cardHeight - 38, w = layout.cardWidth;
      if (hudCard(card)) {
        int half = (w - 8 - 3) / 2;
        if (Draw.inside(mx, my, cx + 4, row, half, 14)) {
          // ADD TO HUD: switch it on if needed and open the HUD editor to place it
          if (!card.enabled()) card.toggle();
          saveCurrent();
          view = View.HUD;
          Cbui.hud().editorOpen(true);
          return;
        }
        if (Draw.inside(mx, my, cx + 4 + half + 3, row, w - 8 - half - 3, 14)) {
          card.toggle();
          saveCurrent();
          return;
        }
      } else if (card.toggleable() && Draw.inside(mx, my, cx + 4, row, w - 8, 14)) {
        card.toggle();
        saveCurrent();
        return;
      }
      selected = card;
      resetPage(card);
      fromCards = true;
      view = View.SETTINGS;
      settingsScroll = settingsTarget = 0;
      return;
    }
  }

  /** A freshly opened settings page starts simple: shared options folded away, only the first section of another mod's options open. */
  private void resetPage(Card card) {
    showAdvanced = false;
    openSections.clear();
    if (card.bridgeMod != null && card.bridgeMod.sections.size() == 1)
      openSections.add(card.bridgeMod.sections.get(0).name);
  }

  private void settingsClick(int mx, int my) {
    if (selected == null) {
      for (int i = 0; i < SETTINGS_TABS.length; i++)
        if (tabShown(i) && Draw.inside(mx, my, tabX(i), layout.panelY + TAB_Y, tabW(i), 16)) {
          if (i != settingsTab) {
            dev.cbui.config.PerfOptions.flush();
            settingsTab = i;
            settingsScroll = settingsTarget = 0;
            globalSettings = buildGlobalSettings();
          }
          return;
        }
      if (settingsTab == 4 && hit(mx, my, layout.panelX + 12, layout.panelY + ROWS_Y + 40 - settingsScroll, layout.panelWidth - 24, 19)) {
        mc.displayGuiScreen(new CosmeticsScreen(this));
        return;
      }
      if (globalSettings != null && settingsTab != 4 && my >= layout.panelY + ROWS_Y - 2 && my < layout.panelY + layout.panelHeight - 2) {
        float top = layout.panelY + ROWS_Y - settingsScroll;
        if (settingsTab == 0) {
          if (presetsClick(mx, my, layout.panelX + 12, top, layout.panelWidth - 24)) return;
          top += presetsHeight();
        }
        nativeRowsClick(globalSettings, mx, my, layout.panelX + 12, top, layout.panelWidth - 24);
      }
      return;
    }
    if (Draw.inside(mx, my, layout.panelX + 3, layout.panelY + 4, 27, 27)) {
      selected = null;
      if (returnToHud) view = hudReturnView;
      else if (fromCards) view = View.CARDS;
      returnToHud = false;
      fromCards = false;
      settingsScroll = settingsTarget = 0;
      return;
    }
    float y = layout.panelY + 27 - settingsScroll;
    int x = layout.panelX + 42, w = layout.panelWidth - 54;
    if (selected.nativeModule != null) nativeSettingsClick(selected.nativeModule, mx, my, x, y, w);
    else bridgeSettingsClick(selected.bridgeMod, mx, my, x, y, w);
  }

  private void nativeSettingsClick(
      AbstractHudModule module, int mx, int my, int x, float y, int w) {
    if (hit(mx, my, x, y, w, 21)) {
      module.enabled(!module.userEnabled());
      saveCurrent();
      return;
    }
    y += 23;
    boolean hud = module.hasHud();
    if (hud) {
      if (hit(mx, my, x, y, w, 20)) {
        module.scale((float) sliderValue(mx, x + 124, w - 128, AbstractHudModule.MIN_SCALE, AbstractHudModule.MAX_SCALE));
        saveCurrent();
        return;
      }
      y += 22;
    }
    y = nativeRowsClick(module.basicSettings(), mx, my, x, y, w);
    if (y < 0) return;
    if (hud) {
      if (hit(mx, my, x, y, w, 21)) {
        showAdvanced = !showAdvanced;
        return;
      }
      y += 24;
    }
    if (hud && showAdvanced) {
      if (hit(mx, my, x, y, w, 21)) {
        Anchor[] values = Anchor.values();
        module.anchor(values[(module.anchor().ordinal() + 1) % values.length]);
        saveCurrent();
        return;
      }
      y += 23;
      y = nativeRowsClick(module.advancedSettings(), mx, my, x, y, w);
      if (y < 0) return;
    }
    if (hit(mx, my, x, y, w, 21)) {
      module.resetLayout();
      module.resetSettings();
      saveCurrent();
    }
  }

  /** A change on the global style page is written to every module. */
  private void afterChange(Setting<?> setting) {
    AbstractHudModule switched = homeSwitches.get(setting);
    if (switched != null) {
      switched.enabled(((Setting.Toggle) setting).get());
      return;
    }
    if (homeRows.contains(setting)) return; // a module's own option: the module reads it itself
    if ((settingsTab == 1 || settingsTab == 2) && globalSettings != null && globalSettings.contains(setting)) {
      dev.cbui.config.PerfOptions.apply(setting);
      return;
    }
    if (setting == borderlessToggle) {
      dev.cbui.config.GlobalOptions.borderless = borderlessToggle.get();
      dev.cbui.config.Borderless.apply(borderlessToggle.get());
      return;
    }
    if (setting == achievementsToggle) {
      dev.cbui.config.GlobalOptions.hideAchievements = achievementsToggle.get();
      return;
    }
    if (setting == textShadowToggle) {
      dev.cbui.config.StylePresets.textShadow(textShadowToggle.get(), Cbui.hud().modules());
      return;
    }
    if (globalSettings != null && globalSettings.contains(setting))
      dev.cbui.hud.GlobalStyle.apply(setting, Cbui.hud().modules());
  }

  /** Handles a click on one of the setting rows; returns the y below them, or -1 when the click was consumed. */
  private float nativeRowsClick(List<Setting<?>> list, int mx, int my, int x, float y, int w) {
    for (int i = 0; i < list.size(); i++) {
      Setting<?> setting = list.get(i);
      y += groupBefore(list, i);
      if (changed(setting) && Draw.inside(mx, my, x + 111, y, 14, 19)) {
        setting.reset();
        afterChange(setting);
        saveCurrent();
        return -1;
      }
      if (hit(mx, my, x, y, w, 21)) {
        if (setting instanceof Setting.Key) {
          editing = setting;
          editField = null;
          return -1;
        }
        if (setting instanceof Setting.Text) {
          beginNativeTextEdit((Setting.Text) setting, x + w - 110, y);
          return -1;
        }
        if (setting instanceof Setting.Slider && mx < x + 124) {
          beginNativeEdit((Setting.Slider) setting, x + w - 110, y);
          return -1;
        }
        if (setting instanceof Setting.Color) {
          if (mx > x + w - 58) beginColorEdit(setting, setting.get(), x + w - 110, y);
          else openPalette((Setting.Color) setting, x + w - 164, y);
          return -1;
        }
        changeNative(setting, mx, x, w);
        afterChange(setting);
        saveCurrent();
        return -1;
      }
      y += rowHeight(setting) + groupAfter(list, i);
    }
    return y;
  }

  // ---- colour palette popup ----

  private static final int[] PALETTE = {
    0xFFFFFFFF, 0xFFD0D0D0, 0xFF909090, 0xFF505050, 0xFF202020, 0xFF000000, 0xFFFF5555, 0xFFFF8A3D,
    0xFFFFD23F, 0xFFB6E34A, 0xFF55D65A, 0xFF2FBF9B, 0xFF3DD5F3, 0xFF4F9DFF, 0xFF4F5BFF, 0xFF9D4EDD,
    0xFFB57BFF, 0xFFE05CFF, 0xFFFF6FB5, 0xFFFF4D6D, 0xFF8B5A2B, 0xFFE0B04A, 0xFF9AF2C8, 0xFFFFA8A0
  };
  private Setting.Color paletteTarget;
  private int paletteX, paletteY, paletteRowY;
  private boolean paletteAlphaDrag;
  private static final int PAL_W = 160, PAL_H = 142;

  private void openPalette(Setting.Color target, int px, float rowY) {
    paletteTarget = target;
    paletteRowY = (int) rowY;
    paletteX = Math.max(layout.panelX + 6, Math.min(px, layout.panelX + layout.panelWidth - PAL_W - 6));
    int below = (int) rowY + 22;
    paletteY = below + PAL_H > layout.panelY + layout.panelHeight - 2 ? (int) rowY - PAL_H - 2 : below;
  }

  private void setPaletteColor(int argb, boolean remember) {
    paletteTarget.set(argb);
    afterChange(paletteTarget);
    if (remember) dev.cbui.config.GlobalOptions.pushRecentColor(argb);
    saveCurrent();
  }

  private int[] paletteCell(int index, int mx, int my, int baseY) {
    int col = index % 8, row = index / 8;
    return new int[] {paletteX + 6 + col * 19, baseY + row * 19};
  }

  private void drawPalette(int mx, int my) {
    Draw.rounded(paletteX - 1, paletteY - 1, paletteX + PAL_W + 1, paletteY + PAL_H + 1, 6, 0xFF6C36A9);
    Draw.rounded(paletteX, paletteY, paletteX + PAL_W, paletteY + PAL_H, 6, 0xFF0E0B13);
    Fonts.bold().drawString("COLORS", paletteX + 6, paletteY + 5, 0xFFB57BFF);
    int cur = paletteTarget.get();
    for (int i = 0; i < PALETTE.length; i++) {
      int[] c = paletteCell(i, mx, my, paletteY + 17);
      boolean hover = Draw.inside(mx, my, c[0], c[1], 16, 16);
      if (hover) Draw.rounded(c[0] - 1, c[1] - 1, c[0] + 17, c[1] + 17, 3, 0xFFFFFFFF);
      Draw.rounded(c[0], c[1], c[0] + 16, c[1] + 16, 3, PALETTE[i]);
    }
    int recentY = paletteY + 17 + 3 * 19 + 4;
    Fonts.bold().drawString("RECENT", paletteX + 6, recentY, 0xFFB57BFF);
    List<Integer> recent = dev.cbui.config.GlobalOptions.recentColors;
    for (int i = 0; i < 8; i++) {
      int[] c = paletteCell(i, mx, my, recentY + 12);
      if (i < recent.size()) {
        boolean hover = Draw.inside(mx, my, c[0], c[1], 16, 16);
        if (hover) Draw.rounded(c[0] - 1, c[1] - 1, c[0] + 17, c[1] + 17, 3, 0xFFFFFFFF);
        Draw.rounded(c[0], c[1], c[0] + 16, c[1] + 16, 3, recent.get(i) | 0xFF000000);
      } else Draw.rounded(c[0], c[1], c[0] + 16, c[1] + 16, 3, 0xFF1A1620);
    }
    float ay = recentY + 12 + 19 + 3;
    Fonts.regular().drawString("OPACITY", paletteX + 6, ay + 3, 0xFFDCE1E9);
    float sx = paletteX + 56, sw = PAL_W - 64, p = (cur >>> 24) / 255f;
    UiWidgets.slider(sx, ay + 6, sw, p, Draw.inside(mx, my, sx, ay, sw, 16));
  }

  /** A click while the palette is open: pick a colour, drag opacity, or close it. */
  private void paletteClick(int mx, int my) {
    if (!Draw.inside(mx, my, paletteX, paletteY, PAL_W, PAL_H)) {
      paletteTarget = null;
      return;
    }
    int cur = paletteTarget.get();
    int alpha = (cur >>> 24) == 0 ? 255 : cur >>> 24;
    for (int i = 0; i < PALETTE.length; i++) {
      int[] c = paletteCell(i, mx, my, paletteY + 17);
      if (Draw.inside(mx, my, c[0], c[1], 16, 16)) {
        setPaletteColor((alpha << 24) | (PALETTE[i] & 0xFFFFFF), true);
        return;
      }
    }
    int recentY = paletteY + 17 + 3 * 19 + 4;
    List<Integer> recent = dev.cbui.config.GlobalOptions.recentColors;
    for (int i = 0; i < Math.min(8, recent.size()); i++) {
      int[] c = paletteCell(i, mx, my, recentY + 12);
      if (Draw.inside(mx, my, c[0], c[1], 16, 16)) {
        setPaletteColor((alpha << 24) | (recent.get(i) & 0xFFFFFF), false);
        return;
      }
    }
    float ay = recentY + 12 + 19 + 3, sx = paletteX + 56, sw = PAL_W - 64;
    if (Draw.inside(mx, my, sx - 4, ay, sw + 8, 18)) {
      paletteAlphaDrag = true;
      paletteAlpha(mx);
    }
  }

  private void paletteAlpha(int mx) {
    float sx = paletteX + 56, sw = PAL_W - 64;
    int a = Math.max(0, Math.min(255, Math.round((mx - sx) / sw * 255)));
    setPaletteColor((a << 24) | (paletteTarget.get() & 0xFFFFFF), false);
  }

  private void drawHint(int mx, int my) {
    if (hintText == null || paletteTarget != null || editField != null) return;
    int maxW = 190;
    List<String> lines = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    for (String word : hintText.split(" ")) {
      String next = cur.length() == 0 ? word : cur + " " + word;
      if (Fonts.regular().getStringWidth(next) > maxW && cur.length() > 0) {
        lines.add(cur.toString());
        cur = new StringBuilder(word);
      } else cur = new StringBuilder(next);
    }
    if (cur.length() > 0) lines.add(cur.toString());
    int w = 0;
    for (String l : lines) w = Math.max(w, Fonts.regular().getStringWidth(l));
    int h = lines.size() * 11 + 8;
    int x = Math.min(mx + 12, width - w - 14), y = Math.min(my + 14, height - h - 4);
    Draw.rounded(x - 1, y - 1, x + w + 11, y + h + 1, 4, 0xFF6C36A9);
    Draw.rounded(x, y, x + w + 10, y + h, 4, 0xFF0E0B13);
    for (int i = 0; i < lines.size(); i++) Fonts.regular().drawString(lines.get(i), x + 5, y + 5 + i * 11, 0xFFE9E3F2);
  }

  private void changeNative(Setting<?> setting, int mx, int x, int w) {
    if (setting instanceof Setting.Toggle) {
      Setting.Toggle s = (Setting.Toggle) setting;
      s.set(!s.get());
    } else if (setting instanceof Setting.Slider) {
      Setting.Slider s = (Setting.Slider) setting;
      s.set((float) sliderValue(mx, x + 124, w - 128, s.min(), s.max()));
    } else if (setting instanceof Setting.Choice) ((Setting.Choice) setting).next();
  }

  private void bridgeSettingsClick(ConfigSnapshot.Mod mod, int mx, int my, int x, float y, int w) {
    for (ConfigSnapshot.Section section : mod.sections) {
      if (hit(mx, my, x, y, w, 15)) {
        if (!openSections.remove(section.name)) openSections.add(section.name);
        return;
      }
      y += 16;
      if (!openSections.contains(section.name)) continue;
      for (ConfigSnapshot.Row row : rowsOf(section)) {
        int h =
            row.kind == ConfigSnapshot.Kind.BUTTON
                ? 22
                : row.kind == ConfigSnapshot.Kind.INFO ? 18 : 20;
        if (changed(row) && Draw.inside(mx, my, x + 111, y, 14, 19)) {
          row.reset();
          return;
        }
        if (hit(mx, my, x, y, w, h)) {
          Object old = row.get();
          switch (row.kind) {
            case TOGGLE:
              row.set(!Boolean.TRUE.equals(old));
              break;
            case SLIDER:
            case NUMBER:
              if (mx < x + 124) beginEdit(row, valueText(old), x + w - 110, y);
              else row.set(typed(sliderValue(mx, x + 124, w - 128, row.min, row.max), old));
              break;
            case DROPDOWN:
              cycle(row, old);
              break;
            case COLOR:
              if (mx > x + w - 58) beginColorEdit(row, old, x + w - 110, y);
              else
                row.set(
                    old instanceof Color
                        ? new Color(hue(color(old), (mx - (x + w - 110)) / 106f), true)
                        : hue(color(old), (mx - (x + w - 110)) / 106f));
              break;
            case KEYBIND:
              editing = row;
              editOriginal = old;
              break;
            case TEXT:
              beginEdit(row, valueText(old), x + w - 110, y);
              break;
            case BUTTON:
              row.set(null);
              break;
            case UNSUPPORTED:
              row.openFallback();
              break;
            default:
              break;
          }
          return;
        }
        y +=
            row.kind == ConfigSnapshot.Kind.SLIDER || row.kind == ConfigSnapshot.Kind.NUMBER
                ? 22
                : 23;
      }
      boolean canFold = supportedRows(section).size() > ROW_LIMIT + 1;
      if (canFold) {
        if (hit(mx, my, x, y, w, 21)) {
          String key = section.name + "#more";
          if (!openSections.remove(key)) openSections.add(key);
          return;
        }
        y += 24;
      }
    }
  }

  private void profilesClick(int mx, int my) {
    if (Draw.inside(mx, my, layout.panelX + layout.panelWidth - 57, layout.panelY + 6, 48, 19)) {
      beginProfileEdit(null, false);
      return;
    }
    int y = layout.panelY + 34;
    if (Draw.inside(mx, my, layout.panelX + 6, y, layout.panelWidth - 12, 21)) {
      switchProfile("Default");
      return;
    }
    y += 25;
    for (String name : profileNames) {
      if (Draw.inside(mx, my, layout.panelX + layout.panelWidth - 24, y, 18, 21)) {
        deleteProfile(name);
        return;
      }
      if (Draw.inside(mx, my, layout.panelX + layout.panelWidth - 72, y, 47, 21)) {
        beginProfileEdit(name, true);
        return;
      }
      if (Draw.inside(mx, my, layout.panelX + 6, y, layout.panelWidth - 12, 21)) {
        switchProfile(name);
        return;
      }
      y += 25;
      if (y > layout.panelY + layout.panelHeight - 24) break; // same cut-off as drawProfiles
    }
    // the Lunar rows, laid out exactly as drawProfiles draws them
    int bottom = layout.panelY + layout.panelHeight - 24;
    if (!lunarProfiles.isEmpty() && y + 14 <= bottom) {
      y += 17;
      for (String[] lunar : lunarProfiles) {
        if (y > bottom) break;
        if (Draw.inside(mx, my, layout.panelX + 6, y, layout.panelWidth - 12, 21)) {
          importLunar(lunar);
          return;
        }
        y += 25;
      }
    }
  }

  // ---- the live HUD behind the card list: drag a box right there, no separate editor needed ----

  private boolean overPanel(int mx, int my) {
    return view == View.HUB && Draw.inside(mx, my, hubX, hubY, hubW, hubH);
  }

  private static final ResourceLocation HUB_FRIENDS = new ResourceLocation("cbui", "icons/hub_friends.png");
  private static final ResourceLocation HUB_SHIRT = new ResourceLocation("cbui", "icons/hub_cosmetics.png");

  /** The transparent hub: the HUD live behind it (drag to move, wheel to resize), the logo and three buttons. */
  private void drawHub(int mx, int my, float partial) {
    drawGradientRect(0, 0, width, height, 0x38000000, 0x58000000);
    drawHudGhost(mx, my, partial);
    float cx = width / 2f;
    hubW = 130;
    hubH = 124;
    hubX = cx - hubW / 2f;
    hubY = height / 2f - 84;
    float intro = Math.min(1f, (System.currentTimeMillis() - openedAt) / 240f);
    GlStateManager.pushMatrix();
    GlStateManager.translate(0, (1 - intro) * 6, 0);
    float logo = 72;
    Draw.texture(LOGO, cx - logo / 2f, hubY, logo, logo, intro);
    int brand = Fonts.bold(14).getStringWidth("luka");
    Fonts.bold(14).drawString("luka", cx - brand / 2f, hubY + logo + 3, ((int) (intro * 255) << 24) | 0xD9C2FF);
    float by = hubY + 94;
    if (dev.cbui.Release.SOCIAL) iconButton(HUB_FRIENDS, (int) (cx - 50 - 4 - 28), (int) by, 28, mx, my, false);
    UiWidgets.cbButton("MODS", cx - 50, by, 100, 28, Draw.inside(mx, my, cx - 50, by, 100, 28), false);
    if (dev.cbui.Release.COSMETICS) iconButton(HUB_SHIRT, (int) (cx + 50 + 4), (int) by, 28, mx, my, false);
    GlStateManager.popMatrix();
    String hint = "DRAG A HUD BOX TO MOVE IT  -  SCROLL OVER IT TO RESIZE  -  EYE HIDES  -  RED X TURNS OFF  -  ESC CLOSES";
    // narrow and on a plate: it sits over HUD boxes (boss bar, top-corner boxes) on smaller windows
    List<String> rows = hintLines(hint, Math.min(width - 24, 240));
    float rowW = 0;
    for (String row : rows) rowW = Math.max(rowW, Fonts.regular().getStringWidth(row));
    Draw.rounded(cx - rowW / 2f - 8, 6, cx + rowW / 2f + 8, 6 + 4 + 11 * rows.size(), 3, 0xC00E0C10);
    float hy = 9;
    for (String row : rows) {
      Fonts.regular().drawCentered(row, cx, hy, 0xD0FFFFFF);
      hy += 11;
    }
  }

  private void hubClick(int mx, int my, int button) {
    if (button == 0) {
      float cx = width / 2f, by = hubY + 94;
      if (dev.cbui.Release.SOCIAL && Draw.inside(mx, my, cx - 50 - 4 - 28, by, 28, 28)) { mc.displayGuiScreen(new FriendsScreen(this)); return; }
      if (Draw.inside(mx, my, cx - 50, by, 100, 28)) {
        view = View.CARDS;
        selected = null;
        Cbui.hud().editorOpen(false);
        buildCards();
        filterCards();
        return;
      }
      if (dev.cbui.Release.COSMETICS && Draw.inside(mx, my, cx + 50 + 4, by, 28, 28)) { mc.displayGuiScreen(new CosmeticsScreen(this)); return; }
    }
    if (button != 0) return;
    if (!overPanel(mx, my) && boxButtonClick(Cbui.hud().editorElements(), (int) (mx * zoom), (int) (my * zoom))) return;
    ghostClick(mx, my);
  }

  /**
   * The HUD boxes drawn (with their sample content) behind the card list, with a thin outline; the one under the mouse lights up and can
   * be dragged (mouse wheel resizes it). Positions are in framebuffer pixels, like the HUD editor, whatever the menu's zoom is.
   */
  private void drawHudGhost(int mx, int my, float partial) {
    float z = zoom;
    int rx = (int) (mx * z), ry = (int) (my * z);
    List<HudElement> elements = Cbui.hud().editorElements();
    HudElement hover = dragging != null ? dragging : overPanel(mx, my) ? null : hoveredElement(elements, rx, ry);
    if (hover != null && dragging == null) {
      int wheel = takeWheel();
      if (wheel != 0) {
        history.push(Cbui.hud().modules());
        hover.scale(hover.scale() + (wheel > 0 ? .05f : -.05f));
        hover.dragEnd();
        saveCurrent();
      }
    }
    ScaledResolution resolution = dev.luka.compat.Compat.scaled(mc);
    GlStateManager.pushMatrix();
    GlStateManager.scale(1f / z, 1f / z, 1f);
    GlStateManager.pushMatrix();
    GlStateManager.scale(sf, sf, 1f);
    for (HudElement e : elements) {
      AbstractHudModule m = e.module();
      if (m == null || e.width() <= 0) continue;
      try {
        m.render(resolution, partial, true);
      } catch (Throwable error) {
        Cbui.reportOnce("HUD preview: " + m.name(), error);
      }
    }
    GlStateManager.popMatrix();
    int line = sf >= 3 ? 2 : 1;
    for (HudElement e : elements) {
      if (e.width() <= 0 || e.height() <= 0) continue;
      float x = e.x() * sf, y = e.y() * sf, w = e.width() * sf, h = e.height() * sf;
      boolean hot = e == hover || e == dragging;
      if (!e.hudVisible()) Draw.rect(x, y, x + w, y + h, 0x90080709);
      if (hot) Draw.rect(x, y, x + w, y + h, 0x308942DA);
      int c = hot ? RED : e.locked() ? 0x708A93A3 : 0x55FFFFFF;
      Draw.rect(x, y, x + w, y + line, c);
      Draw.rect(x, y + h - line, x + w, y + h, c);
      Draw.rect(x, y, x + line, y + h, c);
      Draw.rect(x + w - line, y, x + w, y + h, c);
      String action = drawBoxButtons(e, overPanel(mx, my) ? -1 : rx, overPanel(mx, my) ? -1 : ry);
      if (hot || action != null) {
        String tag = action != null ? e.name() + action : e.name() + String.format(Locale.ROOT, "  %.2fx", e.scale()) + (e == dragging ? "" : "  -  drag to move, wheel to resize");
        float u = Math.max(1f, Math.min(2f, baseH / 600f));
        GlStateManager.pushMatrix();
        GlStateManager.scale(u, u, 1f);
        int tw = Fonts.regular().getStringWidth(tag);
        float tx = x / u, ty = y >= 20 * u ? y / u - 14 : (y + h) / u + 3;
        Draw.rounded(tx, ty, tx + tw + 8, ty + 12, 2, 0xE00E0C10);
        Fonts.regular().drawString(tag, tx + 4, ty + 1, 0xFFFFFFFF);
        GlStateManager.popMatrix();
      }
    }
    if (dragging != null) drawSnapGuides(dragging, elements);
    GlStateManager.popMatrix();
  }

  /** A press on a HUD box outside the panel starts dragging it. */
  private boolean ghostClick(int mx, int my) {
    if (overPanel(mx, my)) return false;
    List<HudElement> elements = Cbui.hud().editorElements();
    HudElement e = hoveredElement(elements, (int) (mx * zoom), (int) (my * zoom));
    hudSelected = e;
    if (e == null || e.locked()) return false;
    dragging = e;
    e.dragStart();
    dragX = mx;
    dragY = my;
    dragGrabX = mx * zoom / (float) sf - e.x();
    dragGrabY = my * zoom / (float) sf - e.y();
    pendingSnapshot = HudHistory.take(Cbui.hud().modules());
    dragMoved = false;
    return true;
  }

  private void hudClick(int mx, int my, int button) {
    if (button == 0 && inUi(mx, my, width / ui() - 54, 5, 48, 20)) {
      view = View.CARDS;
      Cbui.hud().editorOpen(false);
      buildCards();
      filterCards();
      saveCurrent();
      return;
    }
    List<HudElement> elements = Cbui.hud().editorElements();
    if (button == 0 && inUi(mx, my, 6, 5, 50, 20)) {
      history.push(Cbui.hud().modules());
      for (HudElement e : elements) e.resetLayout();
      saveCurrent();
      return;
    }
    if (button == 0 && inUi(mx, my, 60, 5, 50, 20)) {
      libraryOpen = !libraryOpen;
      return;
    }
    if (button != 0) return;
    if (libraryClick(mx, my)) return;
    if (boxButtonClick(elements, mx, my)) return;
    // a corner handle of the selected box starts a resize (the opposite corner stays where it is)
    if (hudSelected != null && hudSelected.module() != null) {
      int corner = cornerAt(hudSelected, mx, my);
      if (corner >= 0) {
        resizing = hudSelected;
        resizeS0 = resizing.scale();
        int opposite = 3 - corner;
        resizeFx = (opposite & 1) == 0 ? resizing.x() : resizing.x() + resizing.width();
        resizeFy = opposite < 2 ? resizing.y() : resizing.y() + resizing.height();
        resizeLeft = (corner & 1) == 0;
        resizeTop = corner < 2;
        resizeD0 = Math.max(4f, (float) Math.hypot(mx / (float) sf - resizeFx, my / (float) sf - resizeFy));
        pendingSnapshot = HudHistory.take(Cbui.hud().modules());
        dragMoved = false;
        return;
      }
    }
    HudElement e = hoveredElement(elements, mx, my);
    hudSelected = e;
    if (e == null || e.locked()) return;
    dragging = e;
    e.dragStart();
    dragX = mx;
    dragY = my;
    dragGrabX = mx / (float) sf - e.x();
    dragGrabY = my / (float) sf - e.y();
    pendingSnapshot = HudHistory.take(Cbui.hud().modules());
    dragMoved = false;
  }

  @Override
  protected void mouseClickMove(int mx, int my, int button, long elapsed) {
    if (paletteAlphaDrag && paletteTarget != null) {
      paletteAlpha(mx);
      return;
    }
    if (view == View.HUD && resizing != null) {
      float d = (float) Math.hypot(mx / (float) sf - resizeFx, my / (float) sf - resizeFy);
      resizing.scale(resizeS0 * d / resizeD0);
      float w = resizing.width(), h = resizing.height();
      resizing.moveTo(resizeLeft ? resizeFx - w : resizeFx, resizeTop ? resizeFy - h : resizeFy);
      dragMoved = true;
    } else if ((view == View.HUD || view == View.HUB) && dragging != null) {
      // absolute: the box keeps the point you grabbed under the cursor (no drift when the box is clamped at an edge)
      dragging.moveTo(mx * zoom / (float) sf - dragGrabX, my * zoom / (float) sf - dragGrabY);
      snapAndClamp(dragging);
      dragMoved = true;
    }
  }

  @Override
  protected void mouseReleased(int mx, int my, int state) {
    if (paletteAlphaDrag) {
      paletteAlphaDrag = false;
      if (paletteTarget != null) dev.cbui.config.GlobalOptions.pushRecentColor(paletteTarget.get());
      return;
    }
    if (dragging != null || resizing != null) {
      HudElement done = dragging != null ? dragging : resizing;
      dragging = null;
      resizing = null;
      done.dragEnd();
      dev.cbui.config.PerfOptions.flush();
      if (dragMoved && pendingSnapshot != null) history.pushSnapshot(pendingSnapshot);
      pendingSnapshot = null;
      dragMoved = false;
      saveCurrent();
    }
  }

  @Override
  protected void keyTyped(char c, int key) {
    try {
      if (view == View.HUD && profileField == null && editField == null && handleHudKey(key)) return;
      if (profileField != null) {
        if (key == Keyboard.KEY_ESCAPE) {
          profileField = null;
          return;
        }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
          commitProfileEdit();
          return;
        }
        profileField.textboxKeyTyped(c, key);
        return;
      }
      if (editing instanceof Setting.Key && editField == null) {
        ((Setting.Key) editing).set(key == Keyboard.KEY_ESCAPE ? 0 : key);
        editing = null;
        saveCurrent();
        return;
      }
      if (editing instanceof ConfigSnapshot.Row && editField == null) {
        ConfigSnapshot.Row row = (ConfigSnapshot.Row) editing;
        if (row.kind == ConfigSnapshot.Kind.KEYBIND) {
          setKey(row, editOriginal, key == Keyboard.KEY_ESCAPE ? 0 : key);
          editing = null;
          editOriginal = null;
          return;
        }
      }
      if (editField != null && editField.isFocused()) {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
          commitEditField();
          return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
          editField = null;
          editing = null;
          editOriginal = null;
          return;
        }
        editField.textboxKeyTyped(c, key);
        return;
      }
      if (view == View.CARDS && search.textboxKeyTyped(c, key)) {
        filterCards();
        return;
      }
      if (key == Keyboard.KEY_ESCAPE) {
        if (paletteTarget != null) {
          paletteTarget = null;
          return;
        }
        if (view == View.CARDS && hubMode && selected == null) {
          view = View.HUB;
          return;
        }
        if (view == View.HUD || view == View.PROFILES || selected != null) {
          view = returnToHud && selected != null ? hudReturnView : View.CARDS;
          returnToHud = false;
          selected = null;
          return;
        }
        close();
        return;
      }
      super.keyTyped(c, key);
    } catch (Throwable error) {
      Cbui.reportOnce("module menu keyboard", error);
    }
  }

  private void beginProfileEdit(String name, boolean rename) {
    presetModal = false;
    renameProfile = rename;
    editing = name;
    profileError = "";
    profileField = new GuiTextField(fontRendererObj, width / 2 - 70, height / 2 - 4, 140, 16);
    profileField.setText(name == null ? "" : name);
    profileField.setFocused(true);
  }

  private void commitProfileEdit() {
    if (presetModal) {
      commitPresetEdit();
      return;
    }
    String name = profileField.getText().trim();
    try {
      ProfileFiles.ensureName(name);
      JsonObject state = Cbui.config().snapshot(Cbui.hud());
      if (renameProfile) {
        profiles.rename((String) editing, name);
        if (activeProfile.equals(editing)) activeProfile = name;
      } else {
        profiles.create(name, state);
        activeProfile = name;
      }
      activeProfileName = activeProfile;
      profileField = null;
      editing = null;
      refreshProfiles();
    } catch (Exception error) {
      profileError = error.getMessage() == null ? "Could not save profile" : error.getMessage();
    }
  }

  private void switchProfile(String name) {
    try {
      saveCurrent();
      if (name.equals("Default")) Cbui.config().load(Cbui.hud());
      else Cbui.config().apply(Cbui.hud(), profiles.load(name));
      activeProfile = activeProfileName = name;
    } catch (Exception error) {
      Cbui.reportOnce("profile switch", error);
    }
  }

  private void deleteProfile(String name) {
    try {
      profiles.delete(name);
      if (activeProfile.equals(name)) {
        activeProfile = activeProfileName = "Default";
        Cbui.config().load(Cbui.hud());
      }
      refreshProfiles();
    } catch (Exception error) {
      Cbui.reportOnce("profile delete", error);
    }
  }

  private void refreshProfiles() {
    profileNames = profiles == null ? Collections.<String>emptyList() : profiles.list();
    try {
      lunarProfiles = dev.cbui.config.LunarImport.profiles();
    } catch (Throwable error) {
      lunarProfiles = Collections.emptyList();
    }
  }

  /** Copies a Lunar profile into a new local profile and switches to it; the layout that was active stays as it was. */
  private void importLunar(String[] lunar) {
    String name = lunarProfileName(lunar[1]);
    try {
      saveCurrent();
      int changed = dev.cbui.config.LunarImport.apply(lunar[0], Cbui.hud());
      profiles.save(name, Cbui.config().snapshot(Cbui.hud()));
      activeProfile = activeProfileName = name;
      refreshProfiles();
      lunarStatus = "COPIED " + changed + " MODULES FROM LUNAR INTO \"" + name.toUpperCase(java.util.Locale.ROOT) + "\"";
    } catch (Exception error) {
      lunarStatus = "COULD NOT READ THAT LUNAR PROFILE";
      Cbui.reportOnce("lunar import", error);
    }
  }

  private String lunarProfileName(String display) {
    String clean = ("Lunar " + display).replaceAll("[^a-zA-Z0-9 _\\-\\[\\]]", "").trim();
    if (clean.length() > 34) clean = clean.substring(0, 34).trim();
    String name = clean;
    for (int n = 2; profileNames.contains(name); n++) name = clean + " " + n;
    return name;
  }

  private void saveCurrent() {
    if (Cbui.config() == null || Cbui.hud() == null) return;
    if (activeProfile.equals("Default")) Cbui.config().save(Cbui.hud());
    else
      try {
        profiles.save(activeProfile, Cbui.config().snapshot(Cbui.hud()));
      } catch (IOException error) {
        Cbui.reportOnce("profile save", error);
      }
  }

  private void beginEdit(ConfigSnapshot.Row row, String text, int fx, float fy) {
    editing = row;
    editField = new GuiTextField(fontRendererObj, fx, (int) fy, 106, 19);
    editField.setText(text);
    editField.setFocused(true);
  }

  private void beginNativeEdit(Setting.Slider setting, int fx, float fy) {
    editing = setting;
    editField = new GuiTextField(fontRendererObj, fx, (int) fy, 106, 19);
    editField.setText(format(setting.get()));
    editField.setFocused(true);
  }

  private void beginNativeTextEdit(Setting.Text setting, int fx, float fy) {
    editing = setting;
    editField = new GuiTextField(fontRendererObj, fx, (int) fy, 106, 19);
    editField.setMaxStringLength(256);
    editField.setText(setting.get());
    editField.setFocused(true);
  }

  private void beginColorEdit(Object target, Object old, int fx, float fy) {
    editing = target;
    editOriginal = old;
    editField = new GuiTextField(fontRendererObj, fx, (int) fy, 106, 19);
    editField.setText(String.format("#%08X", color(old)));
    editField.setFocused(true);
  }

  private void commitEditField() {
    try {
      if (editing instanceof Setting.Slider)
        ((Setting.Slider) editing).set(Float.parseFloat(editField.getText()));
      else if (editing instanceof Setting.Text)
        ((Setting.Text) editing).set(editField.getText());
      else if (editing instanceof Setting.Color) {
        int typed = parseColor(editField.getText());
        ((Setting.Color) editing).set(typed);
        dev.cbui.config.GlobalOptions.pushRecentColor(typed);
      }
      else if (editing instanceof ConfigSnapshot.Row) {
        ConfigSnapshot.Row row = (ConfigSnapshot.Row) editing;
        Object old = editOriginal == null ? row.get() : editOriginal;
        if (row.kind == ConfigSnapshot.Kind.SLIDER || row.kind == ConfigSnapshot.Kind.NUMBER)
          row.set(typed(Double.parseDouble(editField.getText()), old));
        else if (row.kind == ConfigSnapshot.Kind.COLOR) {
          int argb = parseColor(editField.getText());
          row.set(old instanceof Color ? new Color(argb, true) : argb);
        } else row.set(editField.getText());
      }
      if (editing instanceof Setting<?>) afterChange((Setting<?>) editing);
      saveCurrent();
    } finally {
      editField = null;
      editing = null;
      editOriginal = null;
    }
  }

  /** Keeps the dragged box 3 units inside the screen and snaps its edges to other enabled boxes. */
  private void snapAndClamp(HudElement element) {
    float[] box = {element.x(), element.y(), element.width(), element.height()};
    List<float[]> others = new ArrayList<>();
    for (HudElement other : Cbui.hud().editorElements()) {
      if (other == element || !other.enabled() || other.width() <= 0) continue;
      others.add(new float[] {other.x(), other.y(), other.width(), other.height()});
    }
    float[] d = LayoutMath.snap(box, others, baseW / (float) sf, baseH / (float) sf, 3, 4);
    if (d[0] != 0 || d[1] != 0) element.moveTo(box[0] + d[0], box[1] + d[1]);
  }

  private void close() {
    dev.cbui.config.PerfOptions.flush();
    saveCurrent();
    mc.displayGuiScreen(parent);
  }

  private void fallback(String area, Throwable error) {
    Cbui.reportOnce("module menu " + area, error);
    mc.displayGuiScreen(parent);
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }

  private int settingsHeight() {
    if (selected == null) return visibleCards.size() * 20 + 30;
    if (selected.nativeModule != null) {
      AbstractHudModule m = selected.nativeModule;
      int rows = m.basicSettings().size() + (showAdvanced ? m.advancedSettings().size() + 1 : 0);
      boolean adv = m.hasHud() && showAdvanced;
      return 100 - (m.hasHud() ? 0 : 46) + (int) (rowsHeight(m.basicSettings()) + (adv ? rowsHeight(m.advancedSettings()) + 23 : 0)) + 23;
    }
    int h = 30;
    if (selected.bridgeMod == null) return h + 18;
    for (ConfigSnapshot.Section s : selected.bridgeMod.sections) {
      h += 16;
      if (!openSections.contains(s.name)) continue;
      h += rowsOf(s).size() * 23;
      if (supportedRows(s).size() > ROW_LIMIT + 1) h += 24;
    }
    return h;
  }

  private void drawScrollbar(int max, float value, int y, int h) {
    if (max <= 0) return;
    float thumb = Math.max(18, h * h / (float) (h + max)), top = y + (h - thumb) * (value / max);
    Draw.rounded(
        layout.panelX + layout.panelWidth - 5,
        y + 3,
        layout.panelX + layout.panelWidth - 3,
        y + h - 3,
        1,
        0x40342D3B);
    Draw.rounded(
        layout.panelX + layout.panelWidth - 6,
        top,
        layout.panelX + layout.panelWidth - 2,
        top + thumb,
        2,
        RED);
  }

  private static boolean hit(int mx, int my, int x, float y, int w, int h) {
    return Draw.inside(mx, my, x, y, w, h);
  }

  private static float clamp(float v, float min, float max) {
    return Math.max(min, Math.min(max, v));
  }

  private static double sliderValue(int mx, float x, float w, double min, double max) {
    return min + Math.max(0, Math.min(1, (mx - x) / w)) * (max - min);
  }

  private static String format(double v) {
    return Math.abs(v - Math.rint(v)) < .0001
        ? String.valueOf((int) v)
        : String.format(Locale.ROOT, "%.2f", v);
  }

  private static double number(Object v) {
    return v instanceof Number ? ((Number) v).doubleValue() : 0;
  }

  private static int color(Object v) {
    return v instanceof Color
        ? ((Color) v).getRGB()
        : v instanceof Number ? ((Number) v).intValue() : 0xFFFFFFFF;
  }

  private static int hue(int old, float position) {
    int alpha = old >>> 24;
    return alpha << 24 | (Color.HSBtoRGB(clamp(position, 0, 1), .78f, 1f) & 0xFFFFFF);
  }

  private static Object typed(double value, Object old) {
    if (old instanceof Integer) return (int) Math.round(value);
    if (old instanceof Float) return (float) value;
    if (old instanceof Long) return Math.round(value);
    return value;
  }

  private static String valueText(Object v) {
    return v == null ? "NONE" : String.valueOf(v);
  }

  private static int parseColor(String text) {
    String clean = text.trim().replace("#", "");
    if (clean.length() == 6) clean = "FF" + clean;
    if (clean.length() != 8) throw new IllegalArgumentException("Use #RRGGBB or #AARRGGBB");
    return (int) Long.parseLong(clean, 16);
  }

  private static String keyName(Object value) {
    try {
      if (value instanceof Number) {
        int key = ((Number) value).intValue();
        if (key < 0) return net.minecraft.client.resources.I18n.format("key.mouseButton", key + 101);
        return key == 0 ? "NONE" : Keyboard.getKeyName(key);
      }
      Method getter = value.getClass().getMethod("getKeyCodes");
      int[] keys = (int[]) getter.invoke(value);
      return keys.length == 0 ? "NONE" : Keyboard.getKeyName(keys[0]);
    } catch (Throwable ignored) {
      return valueText(value);
    }
  }

  private static void setKey(ConfigSnapshot.Row row, Object old, int key) throws Exception {
    if (old instanceof Number) {
      row.set(typed(key, old));
      return;
    }
    try {
      Method setter = old.getClass().getMethod("setKeyCodes", int[].class);
      setter.invoke(old, (Object) (key == 0 ? new int[0] : new int[] {key}));
      row.set(old);
      return;
    } catch (NoSuchMethodException ignored) {
    }
    int[] mouse = (int[]) old.getClass().getMethod("getMouseBtns").invoke(old);
    byte mods = ((Number) old.getClass().getMethod("getMods").invoke(old)).byteValue();
    long duration = ((Number) old.getClass().getMethod("getDurationNanos").invoke(old)).longValue();
    for (Constructor<?> constructor : old.getClass().getConstructors()) {
      Class<?>[] p = constructor.getParameterTypes();
      if (p.length == 5 && p[0] == int[].class && p[1] == int[].class) {
        row.set(
            constructor.newInstance(
                key == 0 ? new int[0] : new int[] {key}, mouse, mods, duration, null));
        return;
      }
    }
  }

  private static void cycle(ConfigSnapshot.Row row, Object old) {
    if (row.options.isEmpty()) return;
    int i = old instanceof Number ? ((Number) old).intValue() : row.options.indexOf(valueText(old));
    i = (i + 1) % row.options.size();
    row.set(old instanceof Number ? typed(i, old) : row.options.get(i));
  }

  private static String ellipsize(String text, int width) {
    if (text == null) return "";
    if (Fonts.regular().getStringWidth(text) <= width) return text;
    String out = text;
    while (out.length() > 1 && Fonts.regular().getStringWidth(out + "...") > width)
      out = out.substring(0, out.length() - 1);
    return out + "...";
  }

  private static final class Card implements ModuleSearch.Entry {
    final String name, category, search, initials;
    final AbstractHudModule nativeModule;
    final ConfigSnapshot.Mod bridgeMod;

    Card(AbstractHudModule module, String category) {
      nativeModule = module;
      bridgeMod = null;
      name = module.name();
      this.category = category;
      StringBuilder s = new StringBuilder(name);
      for (Setting<?> setting : module.settings())
        s.append(' ').append(setting.name()).append(' ').append(setting.tooltip());
      search = s.toString();
      initials = initials(name);
    }

    Card(ConfigSnapshot.Mod mod) {
      nativeModule = null;
      bridgeMod = mod;
      name = mod.name;
      category = "MODS";
      StringBuilder s = new StringBuilder(name).append(' ').append(mod.category);
      for (ConfigSnapshot.Section section : mod.sections) {
        s.append(' ').append(section.name);
        for (ConfigSnapshot.Row row : section.rows)
          s.append(' ').append(row.label).append(' ').append(row.tooltip);
      }
      search = s.toString();
      initials = initials(name);
    }

    public String searchText() {
      return search;
    }

    public String category() {
      return category;
    }

    public String name() {
      return name;
    }

    boolean toggleable() {
      return nativeModule != null;
    }

    void toggle() {
      try {
        if (nativeModule != null) nativeModule.enabled(!nativeModule.userEnabled());
      } catch (Throwable error) {
        Cbui.reportOnce("HUD toggle: " + name, error);
      }
    }

    /** The server has switched this mod off: the card shows the player's own switch but says so. */
    boolean serverBlocked() {
      return nativeModule != null && nativeModule.serverBlocked();
    }

    boolean enabled() {
      if (nativeModule != null) return nativeModule.userEnabled();
      for (ConfigSnapshot.Section s : bridgeMod.sections)
        for (ConfigSnapshot.Row r : s.rows)
          if (r.kind == ConfigSnapshot.Kind.TOGGLE
              && (r.id.toLowerCase(Locale.ROOT).contains("enable")
                  || r.label.toLowerCase(Locale.ROOT).contains("enable")))
            return Boolean.TRUE.equals(r.get());
      return false;
    }

    private static String initials(String name) {
      StringBuilder out = new StringBuilder();
      for (String word : name.split("\\s+"))
        if (!word.isEmpty() && out.length() < 2) out.append(Character.toUpperCase(word.charAt(0)));
      return out.length() == 0 ? "?" : out.toString();
    }
  }
}
