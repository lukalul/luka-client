package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;

/**
 * Sprints for you whenever you move forward. There is deliberately no key that toggles it: pressing the sprint key cannot switch it off by accident,
 * and the only place to change it is this module's switch in the menu.
 */
public final class AlwaysSprintModule extends LabelModule {
  private final Setting.Toggle whileSneaking = grouped("Behaviour", add(new Setting.Toggle("Sprint while sneaking", false, "Keep sprinting while crouched (off: sneaking stops the sprint like vanilla)")));
  private final Setting.Toggle whileUsingItem = grouped("Behaviour", add(new Setting.Toggle("Sprint while using items", false, "Keep sprinting while blocking with a sword or eating")));
  private final Setting.Toggle showLabel = grouped("Display", add(new Setting.Toggle("Show label", true, "Show [Sprinting (Always)] on screen (off: still sprints, nothing is drawn)")));

  public AlwaysSprintModule() {
    super("Always sprint", false, Anchor.BOTTOM_CENTER, 0, -50, "[Sprinting (Always)]");
    background.set(false);
  }

  @Override public void tick() {
    boolean sprinting = false;
    EntityPlayer player = mc.thePlayer;
    if (player != null && mc.currentScreen == null) {
      boolean moving = player.moveForward > 0;
      boolean blocked = player.isPotionActive(Potion.blindness) || player.getFoodStats().getFoodLevel() <= 6 && !player.capabilities.allowFlying
          || player.isSneaking() && !whileSneaking.get() || player.isUsingItem() && !whileUsingItem.get() || player.isCollidedHorizontally;
      if (moving && !blocked) {
        player.setSprinting(true);
        sprinting = true;
      }
    }
    label(sprinting ? "[Sprinting (Always)]" : "[Always sprint]");
    if (!showLabel.get()) size(0, 0);
    else if (contentWidth() <= 0) resize();
  }

  @Override protected void draw(float partialTicks, boolean editor) {
    if (showLabel.get()) super.draw(partialTicks, editor);
  }
}
