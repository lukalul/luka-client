package dev.cbui.hud.module;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;
import java.util.List;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

/**
 * HCF-style potion counter: how many healing potions (drinkable and splash, any strength) are in the inventory, optionally with speed and fire
 * resistance potions too. The count is refreshed on the tick, never while drawing, and turns red when it drops below the warning level.
 */
public final class PotionCounterModule extends AbstractHudModule {
  private final Setting.Slider warnBelow = grouped("Warning", add(new Setting.Slider("Warn below", 8, 0, 36, 1, "Turn the health potion count red when you hold fewer than this")));
  private final Setting.Color warnColor = grouped("Warning", adv(new Setting.Color("Warning color", 0xFFFF5555, "Colour of the count when it is low")));
  private final Setting.Toggle showSpeed = grouped("Also count", add(new Setting.Toggle("Count speed potions", false, "Add your speed potions to the list")));
  private final Setting.Toggle showFireRes = grouped("Also count", add(new Setting.Toggle("Count fire resistance potions", false, "Add your fire resistance potions to the list")));

  private int health = -1, speed = -1, fireRes = -1;
  private String healthText = "Health potions: 0", speedText = "Speed potions: 0", fireText = "Fire res potions: 0";

  public PotionCounterModule() { super("Potion counter", false, Anchor.CENTER_LEFT, 5, 0, 90, 11); }

  @Override public void tick() {
    int h = 0, s = 0, f = 0;
    if (mc.thePlayer != null) {
      for (ItemStack stack : mc.thePlayer.inventory.mainInventory) {
        if (stack == null || !(stack.getItem() instanceof ItemPotion)) continue;
        List<PotionEffect> effects = ((ItemPotion) stack.getItem()).getEffects(stack);
        if (effects == null) continue;
        for (PotionEffect effect : effects) {
          int id = effect.getPotionID();
          if (id == Potion.heal.id) h += stack.stackSize;
          else if (id == Potion.moveSpeed.id) s += stack.stackSize;
          else if (id == Potion.fireResistance.id) f += stack.stackSize;
        }
      }
    }
    if (h != health) { health = h; healthText = "Health potions: " + h; }
    if (s != speed) { speed = s; speedText = "Speed potions: " + s; }
    if (f != fireRes) { fireRes = f; fireText = "Fire res potions: " + f; }
    int lines = 1 + (showSpeed.get() ? 1 : 0) + (showFireRes.get() ? 1 : 0);
    float widest = Math.max(textWidth(healthText), textWidth("Health potions: 36"));
    if (showSpeed.get()) widest = Math.max(widest, textWidth(speedText));
    if (showFireRes.get()) widest = Math.max(widest, textWidth(fireText));
    size(widest, linesHeight(lines));
  }

  @Override protected void draw(float partialTicks, boolean editor) {
    float y = 0;
    int healthColor = !editor && health < warnBelow.get() ? warnColor.get() : textColor.get();
    text(healthText, 0, y, healthColor);
    y += lineStep();
    if (showSpeed.get()) { text(speedText, 0, y, textColor.get()); y += lineStep(); }
    if (showFireRes.get()) text(fireText, 0, y, textColor.get());
  }
}
