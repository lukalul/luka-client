package dev.cbui.config;

import dev.cbui.gui.ModuleMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;

/**
 * Frame rate caps for particular screens (Performance, Frame rate): the main menu, the pause menu, open containers and the HUD
 * editor. The first of those that matches the screen decides the cap; with none matching, or that cap set to Off, the normal
 * limit applies. Applied in {@code FpsContextMixin}, only while the window has focus, so the background limits (luka's own
 * "Limit FPS when unfocused" and Dynamic FPS) still decide when it does not. VSync is never changed.
 */
public final class FpsContexts {
  /** Row choices; "Off" leaves the screen to the normal limit. */
  public static final String[] CHOICES = {"Off", "30", "60", "90", "120", "144", "240"};

  private FpsContexts() {}

  /** The cap for what is on screen now, or 0 when no switched-on context matches. */
  public static int cap(Minecraft mc) {
    GuiScreen screen = mc.currentScreen;
    if (screen == null) return 0;
    if (mc.theWorld == null) return GlobalOptions.menuFps;
    if (screen instanceof GuiIngameMenu) return GlobalOptions.pauseFps;
    if (screen instanceof GuiContainer) return GlobalOptions.containerFps;
    if (ModuleMenu.hudEditorOpen()) return GlobalOptions.hudEditorFps;
    return 0;
  }

  /** The row's index for a saved cap. */
  public static int index(int fps) {
    for (int i = 1; i < CHOICES.length; i++) if (Integer.parseInt(CHOICES[i]) == fps) return i;
    return 0;
  }

  /** The cap for a row choice. */
  public static int value(String choice) {
    return choice == null || "Off".equals(choice) ? 0 : Integer.parseInt(choice);
  }
}
