package dev.cbui.gui;

import dev.cbui.Cbui;
import dev.cbui.config.AnimationOptions;
import dev.cbui.config.GlobalOptions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumParticleTypes;
import org.lwjgl.input.Mouse;

/** OptiFine-style, fine-grained animation controls shared by Video Settings and luka settings. */
public final class AnimationsScreen extends GuiScreen {
  private final GuiScreen parent;
  private final List<Entry> entries = new ArrayList<>();
  private int scroll;

  public AnimationsScreen(GuiScreen parent) {
    this.parent = parent;
    for (EnumParticleTypes type : EnumParticleTypes.values())
      entries.add(new Entry(AnimationOptions.particleKey(type), AnimationOptions.readable(type)));
    for (int i = 0; i < AnimationOptions.OTHER_KEYS.length; i++)
      entries.add(new Entry(AnimationOptions.OTHER_KEYS[i], AnimationOptions.OTHER_NAMES[i]));
  }

  @Override public void initGui() {
    buttonList.clear();
    int x = width / 2 - 155;
    buttonList.add(new GuiButton(20, width / 2 - 155, height - 48, 100, 20, "All on"));
    buttonList.add(new GuiButton(21, width / 2 - 50, height - 48, 100, 20, "All off"));
    buttonList.add(new GuiButton(200, width / 2 + 55, height - 48, 100, 20, "Done"));
  }

  @Override protected void actionPerformed(GuiButton button) {
    if (!button.enabled) return;
    if (button.id == 20 || button.id == 21) {
      boolean on = button.id == 20;
      for (Entry entry : entries) GlobalOptions.animationEnabled(entry.key, on);
      save();
    } else if (button.id == 200) {
      save();
      mc.displayGuiScreen(parent);
    }
  }

  @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    super.mouseClicked(mouseX, mouseY, mouseButton);
    if (mouseButton != 0 || mouseY < 52 || mouseY >= height - 54) return;
    int index = (mouseY - 52 + scroll) / 21;
    if (index >= 0 && index < entries.size()) {
      Entry entry = entries.get(index);
      GlobalOptions.animationEnabled(entry.key, !GlobalOptions.animationEnabled(entry.key));
      save();
    }
  }

  @Override protected void keyTyped(char typed, int key) {
    if (key == org.lwjgl.input.Keyboard.KEY_ESCAPE) {
      save();
      mc.displayGuiScreen(parent);
    }
  }

  @Override public void handleMouseInput() {
    super.handleMouseInput();
    int wheel = Mouse.getEventDWheel();
    if (wheel != 0) {
      scroll -= wheel > 0 ? 42 : -42;
      scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }
  }

  @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    drawDefaultBackground();
    drawCenteredString(fontRendererObj, "Animations", width / 2, 7, 0xFFFFFF);
    int bottom = height - 54;
    for (int i = 0; i < entries.size(); i++) {
      int y = 52 + i * 21 - scroll;
      if (y + 19 <= 52 || y >= bottom) continue;
      Entry entry = entries.get(i);
      boolean hover = mouseX >= width / 2 - 155 && mouseX < width / 2 + 155 && mouseY >= y && mouseY < y + 19;
      drawRect(width / 2 - 155, y, width / 2 + 155, y + 19, hover ? 0x80505050 : 0x60404040);
      drawString(fontRendererObj, entry.name, width / 2 - 149, y + 6, 0xFFFFFF);
      String state = GlobalOptions.animationEnabled(entry.key) ? "ON" : "OFF";
      drawString(fontRendererObj, state, width / 2 + 149 - fontRendererObj.getStringWidth(state), y + 6,
          GlobalOptions.animationEnabled(entry.key) ? 0x55FF55 : 0xFF5555);
    }
    super.drawScreen(mouseX, mouseY, partialTicks);
  }

  private int maxScroll() { return Math.max(0, entries.size() * 21 - (height - 106)); }

  private static void save() {
    if (Cbui.config() != null && Cbui.hud() != null) Cbui.config().save(Cbui.hud());
  }

  private static final class Entry {
    final String key, name;
    Entry(String key, String name) { this.key = key; this.name = name; }
  }
}
