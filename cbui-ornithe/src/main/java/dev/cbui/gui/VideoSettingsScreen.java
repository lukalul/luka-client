package dev.cbui.gui;

import dev.cbui.Cbui;
import dev.cbui.config.OptimalPreset;
import dev.cbui.config.PerfOptions;
import dev.cbui.config.VideoOptions;
import dev.cbui.hud.Setting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Video Settings in OptiFine's layout, drawn with Minecraft's own buttons and sliders on both renderers: the main options and
 * five page buttons on the first page, then Quality, Details, Performance, Animations and Other. It is the only settings screen:
 * the vanilla one and the renderers' own screens are swapped for it.
 */
public final class VideoSettingsScreen extends GuiScreen {
  private static final String ARGENTUM = "dev.rdh.argentum.impl.gui.VideoOptionsScreen";
  private static final String ASTATINE = "yt.ren.astatine.gui.AstatineOptionsScreen";
  /** Button label, page title, then the row groups shown on the page. */
  private static final String[][] PAGES = {
      {"Quality", "Quality Settings", "Quality"},
      {"Details", "Detail Settings", "Details"},
      {"Performance", "Performance Settings", "Frame rate", "World", "Particles", "Engine"},
      {"Animations", "Animation Settings", "Animations", "Particle types"},
      {"Other", "Other Settings", "Other"},
  };
  private static final int DONE = 200, ALL_ON = 2, ALL_OFF = 3, RESET = 4, RESET_ASK = 5, PAGE = 100;
  /** OptiFine's grid: columns 150 wide at centre-155 and centre+5, rows 21 apart from height/6-12, Done at height/6+179. */
  private static final int LEFT = 155, COLUMN = 160, BUTTON = 150, ROW = 21, HOVER_MS = 500;

  private final GuiScreen parent;
  /** The grid, two per row; a null keeps its slot empty. */
  private final List<GuiButton> cells = new ArrayList<>();
  private final List<Setting<?>> rows = new ArrayList<>();
  /** Clicks run on the next frame: pages swap the buttons out, and Minecraft is still looping over them during a click. */
  private Runnable pending;
  private String[] page;
  private int scroll;
  private GuiButton hovered;
  private long hoverStart;

  private VideoSettingsScreen(GuiScreen parent) {
    this.parent = parent;
  }

  /** Called with every screen the game is about to show: the vanilla and renderer settings screens become this one. */
  public static GuiScreen replace(GuiScreen screen) {
    if (screen == null || screen instanceof VideoSettingsScreen) return screen;
    String name = screen.getClass().getName();
    if (screen instanceof GuiVideoSettings || name.equals(ARGENTUM) || name.equals(ASTATINE))
      return new VideoSettingsScreen(Minecraft.getMinecraft().currentScreen);
    return screen;
  }

  @Override public void initGui() {
    buttonList.clear();
    cells.clear();
    rows.clear();
    List<Setting<?>> all = new ArrayList<>(VideoOptions.build());
    Set<String> names = new HashSet<>();
    for (Setting<?> setting : all) names.add(setting.name());
    // the Performance page shows the luka menu's PERFORMANCE rows; a row Video Settings already has elsewhere is not repeated
    for (Setting<?> setting : PerfOptions.build()) if (names.add(setting.name())) all.add(setting);
    for (Setting<?> setting : all) if (onPage(setting)) rows.add(setting);
    for (Setting<?> setting : rows)
      cells.add(setting instanceof Setting.Slider ? new SliderButton((Setting.Slider) setting) : new RowButton(setting));
    if (page == null) {
      if (cells.size() % 2 == 1) cells.add(null);
      for (int i = 0; i < PAGES.length; i++) cells.add(new GuiButton(PAGE + i, 0, 0, BUTTON, 20, PAGES[i][0] + "..."));
    }
    for (GuiButton cell : cells) if (cell != null) buttonList.add(cell);
    int footer = footerY();
    if (isPage("Animations")) {
      buttonList.add(new GuiButton(ALL_ON, width / 2 - LEFT, footer, BUTTON, 20, "All ON"));
      buttonList.add(new GuiButton(ALL_OFF, width / 2 - LEFT + COLUMN, footer, BUTTON, 20, "All OFF"));
    } else if (isPage("Other")) {
      buttonList.add(new GuiButton(RESET, width / 2 - 100, footer, 200, 20, "Reset video settings..."));
    }
    buttonList.add(new GuiButton(DONE, width / 2 - 100, doneY(), 200, 20, I18n.format("gui.done")));
    layout();
  }

  private boolean isPage(String label) {
    return page != null && page[0].equals(label);
  }

  private boolean onPage(Setting<?> setting) {
    return page == null ? "Main".equals(setting.group()) : Arrays.asList(page).subList(2, page.length).contains(setting.group());
  }

  private int top() { return Math.max(height / 6 - 12, 27); }

  private int doneY() { return Math.min(height / 6 + 168 + 11, height - 25); }

  /** The extra row above Done (All ON / All OFF, Reset video settings). */
  private int footerY() { return doneY() - 24; }

  private boolean hasFooter() { return isPage("Animations") || isPage("Other"); }

  private int visibleRows() {
    int bottom = hasFooter() ? footerY() : doneY();
    return Math.max(1, (bottom - top() + 1) / ROW);
  }

  private int maxScroll() { return Math.max(0, (cells.size() + 1) / 2 - visibleRows()); }

  private void layout() {
    scroll = Math.max(0, Math.min(scroll, maxScroll()));
    for (int i = 0; i < cells.size(); i++) {
      GuiButton cell = cells.get(i);
      if (cell == null) continue;
      int row = i / 2 - scroll;
      cell.visible = row >= 0 && row < visibleRows();
      cell.xPosition = width / 2 - LEFT + (i % 2) * COLUMN;
      cell.yPosition = top() + row * ROW;
    }
  }

  @Override protected void actionPerformed(GuiButton button) {
    if (!button.enabled || button instanceof SliderButton) return;
    if (button instanceof RowButton) {
      Setting<?> setting = ((RowButton) button).setting;
      pending = () -> {
        flip(setting);
        PerfOptions.apply(setting);
        button.displayString = label(setting);
      };
    } else if (button.id == DONE) {
      pending = this::back;
    } else if (button.id == ALL_ON || button.id == ALL_OFF) {
      boolean on = button.id == ALL_ON;
      pending = () -> {
        for (Setting<?> setting : rows) {
          if (!(setting instanceof Setting.Toggle)) continue;
          ((Setting.Toggle) setting).set(on);
          PerfOptions.apply(setting);
        }
        initGui();
      };
    } else if (button.id == RESET) {
      pending = () -> mc.displayGuiScreen(new GuiYesNo(this, "Reset video settings?",
          "Graphics, particles, render distance and frame rate go back to luka's optimal settings.", RESET_ASK));
    } else if (button.id >= PAGE) {
      String[] target = PAGES[button.id - PAGE];
      pending = () -> show(target);
    }
  }

  /** The answer of the Reset video settings question. */
  @Override public void confirmClicked(boolean yes, int id) {
    if (id == RESET_ASK && yes) {
      commit();
      OptimalPreset.apply();
    }
    mc.displayGuiScreen(this);
  }

  private static void flip(Setting<?> setting) {
    if (setting instanceof Setting.Toggle) {
      Setting.Toggle toggle = (Setting.Toggle) setting;
      toggle.set(!toggle.get());
    } else if (setting instanceof Setting.Choice) {
      ((Setting.Choice) setting).next();
    }
  }

  private void show(String[] target) {
    commit();
    page = target;
    scroll = 0;
    initGui();
  }

  private void back() {
    if (page != null) show(null);
    else mc.displayGuiScreen(parent);
  }

  private static void commit() {
    PerfOptions.flush();
    if (Cbui.config() != null && Cbui.hud() != null) Cbui.config().save(Cbui.hud());
  }

  @Override public void onGuiClosed() {
    commit();
  }

  @Override protected void keyTyped(char typed, int key) {
    if (key == Keyboard.KEY_ESCAPE) pending = this::back;
  }

  @Override public void handleMouseInput() {
    super.handleMouseInput();
    int wheel = Mouse.getEventDWheel();
    if (wheel != 0) {
      scroll += wheel > 0 ? -1 : 1;
      layout();
    }
  }

  @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    if (pending != null) {
      Runnable action = pending;
      pending = null;
      action.run();
      if (mc.currentScreen != this) return;
    }
    drawDefaultBackground();
    String title = page == null ? I18n.format("options.videoTitle") : page[1];
    drawCenteredString(fontRendererObj, title, width / 2, 15, 0xFFFFFF);
    super.drawScreen(mouseX, mouseY, partialTicks);
    drawScrollbar();
    drawTooltip(mouseX, mouseY);
  }

  private void drawScrollbar() {
    int max = maxScroll();
    if (max == 0) return;
    int x = width / 2 + LEFT + 4, top = top(), bottom = top() + visibleRows() * ROW - 1;
    int span = bottom - top, knob = Math.max(16, span * visibleRows() / (visibleRows() + max));
    int y = top + (span - knob) * scroll / max;
    Gui.drawRect(x, top, x + 4, bottom, 0xFF000000);
    Gui.drawRect(x, y, x + 4, y + knob, 0xFF808080);
    Gui.drawRect(x, y, x + 3, y + knob - 1, 0xFFC0C0C0);
  }

  private void drawTooltip(int mouseX, int mouseY) {
    GuiButton over = null;
    for (GuiButton cell : cells) {
      if (cell != null && cell.visible && mouseX >= cell.xPosition && mouseX < cell.xPosition + BUTTON && mouseY >= cell.yPosition && mouseY < cell.yPosition + 20) over = cell;
    }
    if (over != hovered) {
      hovered = over;
      hoverStart = Minecraft.getSystemTime();
    }
    Setting<?> setting = over instanceof RowButton ? ((RowButton) over).setting : over instanceof SliderButton ? ((SliderButton) over).setting : null;
    if (setting == null || setting.tooltip().isEmpty() || Minecraft.getSystemTime() - hoverStart < HOVER_MS) return;
    List<String> lines = new ArrayList<>();
    lines.add(setting.name());
    lines.addAll(fontRendererObj.listFormattedStringToWidth("§7" + setting.tooltip(), 220));
    drawHoveringText(lines, mouseX, mouseY);
    GlStateManager.disableLighting();
  }

  /** "Name: value", shortening the name if the pair would not fit on the button. */
  private String label(Setting<?> setting) {
    String value;
    if (setting instanceof Setting.Toggle) value = ((Setting.Toggle) setting).get() ? "ON" : "OFF";
    else if (setting instanceof Setting.Slider) value = sliderValue(setting.name(), Math.round(((Setting.Slider) setting).get()));
    else value = String.valueOf(setting.get());
    String tail = ": " + value, name = shortName(setting.name());
    int room = BUTTON - 6 - fontRendererObj.getStringWidth(tail);
    if (fontRendererObj.getStringWidth(name) > room)
      name = fontRendererObj.trimStringToWidth(name, room - fontRendererObj.getStringWidth("...")).trim() + "...";
    return name + tail;
  }

  /** A shorter name for the rows whose own name does not fit on a button. The row still acts on its full name. */
  private static String shortName(String name) {
    if (name.equals("Limit FPS when unfocused")) return "Unfocused FPS limit";
    if (name.equals("Item / block break particles")) return "Break particles";
    if (name.equals("Fog in water and lava")) return "Liquid fog";
    return name;
  }

  private static String sliderValue(String name, int value) {
    if (name.equals("Max FPS") && value >= 260) return "Unlimited";
    if (name.equals("Render distance")) return value + " chunks";
    if (name.equals("Fog start") || name.equals("Fog end") || name.equals("Brightness")) return value + "%";
    if (name.equals("Chunk builders") && value == 0) return "Auto";
    if (name.equals("Chunk fade-in")) return value == 0 ? "Off" : value + " ms";
    if (name.equals("Cloud distance") || name.equals("Weather distance")) return value == 0 ? "Vanilla" : value + " blocks";
    if (name.equals("Shadow distance")) return value + " blocks";
    if (name.equals("Portal distortion")) return value + "%";
    if (name.equals("F3 refresh")) return value == 0 ? "Every frame" : value + " ms";
    if (name.equals("F3 scale")) return value == 0 ? "GUI scale" : value + "x";
    return Integer.toString(value);
  }

  /** A click-to-change row. */
  private final class RowButton extends GuiButton {
    final Setting<?> setting;

    RowButton(Setting<?> setting) {
      super(0, 0, 0, BUTTON, 20, label(setting));
      this.setting = setting;
    }
  }

  /** A drag row, drawn the way the game draws its own option sliders. */
  private final class SliderButton extends GuiButton {
    final Setting.Slider setting;
    private boolean dragging;

    SliderButton(Setting.Slider setting) {
      super(0, 0, 0, BUTTON, 20, label(setting));
      this.setting = setting;
    }

    @Override protected int getHoverState(boolean mouseOver) { return 0; }

    @Override protected void mouseDragged(Minecraft minecraft, int mouseX, int mouseY) {
      if (!visible) return;
      if (dragging) moveTo(mouseX);
      float range = setting.max() - setting.min();
      float position = range <= 0 ? 0 : (setting.get() - setting.min()) / range;
      minecraft.getTextureManager().bindTexture(buttonTextures);
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
      int knob = xPosition + (int) (position * (width - 8));
      drawTexturedModalRect(knob, yPosition, 0, 66, 4, 20);
      drawTexturedModalRect(knob + 4, yPosition, 196, 66, 4, 20);
    }

    @Override public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
      if (!super.mousePressed(minecraft, mouseX, mouseY)) return false;
      dragging = true;
      moveTo(mouseX);
      return true;
    }

    @Override public void mouseReleased(int mouseX, int mouseY) {
      dragging = false;
    }

    private void moveTo(int mouseX) {
      float position = Math.max(0.0F, Math.min(1.0F, (mouseX - (xPosition + 4)) / (float) (width - 8)));
      float before = setting.get();
      setting.set(setting.min() + position * (setting.max() - setting.min()));
      if (setting.get() != before) {
        PerfOptions.apply(setting);
        displayString = label(setting);
      }
    }
  }
}
