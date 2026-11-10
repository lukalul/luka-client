package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Tap the sneak key once to sneak until you tap it again (holding it does nothing). "Sneak in containers" keeps
 * you sneaking while a chest, furnace, crafting table or inventory is open, whatever the toggle says, and goes
 * back to the toggled state when it closes. The sneak itself is applied in {@code MovementInputMixin}.
 */
public final class ToggleSneakModule extends LabelModule {
  private static volatile ToggleSneakModule instance;

  private final Setting.Toggle inContainers = grouped("Behaviour", add(new Setting.Toggle("Sneak in containers", true, "Sneak automatically while a chest, furnace, crafting table or inventory is open")));
  private final Setting.Toggle showLabel = grouped("Display", add(new Setting.Toggle("Show label", true, "Show [Sneaking] on screen (off: still toggles, nothing is drawn)")));

  private boolean toggled, lastKey, sneaking;

  public ToggleSneakModule() {
    super("Toggle sneak", false, Anchor.BOTTOM_CENTER, 0, -62, "[Sneaking (Container)]");
    background.set(false);
    instance = this;
  }

  /** True when this module decides the sneak state this tick (the module is on). */
  public static boolean overriding() {
    ToggleSneakModule m = instance;
    return m != null && m.enabled();
  }

  /** The sneak state to use while {@link #overriding()}. */
  public static boolean sneaking() {
    ToggleSneakModule m = instance;
    return m != null && m.sneaking;
  }

  @Override
  public void tick() {
    KeyBinding bind = mc.gameSettings.keyBindSneak;
    int code = bind.getKeyCode();
    boolean down = code < 0 ? Mouse.isButtonDown(code + 100) : code > 0 && Keyboard.isKeyDown(code);
    // only a tap in the world toggles: shift in an inventory means shift-click
    if (mc.currentScreen == null && down && !lastKey) toggled = !toggled;
    lastKey = down;
    boolean container = inContainers.get() && mc.currentScreen instanceof GuiContainer;
    sneaking = toggled || container;
    label(container ? "[Sneaking (Container)]" : toggled ? "[Sneaking (Toggled)]" : "[Toggle sneak]");
    if (!showLabel.get()) size(0, 0);
    else if (contentWidth() <= 0) resize();
  }

  @Override
  public void enabled(boolean on) {
    super.enabled(on);
    if (!on) {
      toggled = false;
      sneaking = false;
    }
  }

  @Override
  protected void draw(float partialTicks, boolean editor) {
    if (showLabel.get()) super.draw(partialTicks, editor);
  }
}
