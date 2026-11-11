package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Sprints for you whenever you move forward, by holding the sprint key for you (the game decides when, as in vanilla). There is deliberately no key that toggles it: pressing the sprint key cannot switch it off by accident,
 * and the only place to change it is this module's switch in the menu.
 */
public final class AlwaysSprintModule extends LabelModule {
  private final Setting.Toggle showLabel = grouped("Display", add(new Setting.Toggle("Show label", true, "Show [Sprinting (Always)] on screen (off: still sprints, nothing is drawn)")));

  public AlwaysSprintModule() {
    super("Always sprint", false, Anchor.BOTTOM_CENTER, 0, -50, "[Sprinting (Always)]");
    background.set(false);
  }

  @Override public void tick() {
    // holds the sprint key; the game itself starts and stops the sprint, as with the key held by hand (see SprintKey)
    EntityPlayer player = mc.thePlayer;
    if (player != null && mc.currentScreen == null) SprintKey.hold(mc, true);
    boolean sprinting = player != null && player.isSprinting();
    label(sprinting ? "[Sprinting (Always)]" : "[Always sprint]");
    if (!showLabel.get()) size(0, 0);
    else if (contentWidth() <= 0) resize();
  }

  @Override protected void draw(float partialTicks, boolean editor) {
    if (showLabel.get()) super.draw(partialTicks, editor);
  }
}
