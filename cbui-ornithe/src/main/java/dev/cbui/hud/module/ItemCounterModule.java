package dev.cbui.hud.module;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** How many ender pearls, golden apples and arrows are in the inventory (the other half of the HCF kit next to the potion counter). */
public final class ItemCounterModule extends AbstractHudModule {
  private final Setting.Toggle pearls = grouped("Count", add(new Setting.Toggle("Ender pearls", true, "Show how many ender pearls you carry")));
  private final Setting.Toggle apples = grouped("Count", add(new Setting.Toggle("Golden apples", true, "Show how many golden apples you carry (enchanted ones included)")));
  private final Setting.Toggle arrows = grouped("Count", add(new Setting.Toggle("Arrows", false, "Show how many arrows you carry")));
  private final Setting.Slider warnBelow = grouped("Warning", add(new Setting.Slider("Warn below", 2, 0, 16, 1, "Turn a count red when you hold fewer than this")));
  private final Setting.Color warnColor = grouped("Warning", adv(new Setting.Color("Warning color", 0xFFFF5555, "Colour of a count that is low")));

  private int pearlCount = -1, appleCount = -1, arrowCount = -1;
  private String pearlText = "", appleText = "", arrowText = "";

  public ItemCounterModule() { super("Item counter", false, Anchor.CENTER_LEFT, 5, 28, 90, 11); }

  @Override public void tick() {
    int p = 0, a = 0, r = 0;
    if (mc.thePlayer != null) {
      for (ItemStack stack : mc.thePlayer.inventory.mainInventory) {
        if (stack == null) continue;
        Item item = stack.getItem();
        if (item == Items.ender_pearl) p += stack.stackSize;
        else if (item == Items.golden_apple) a += stack.stackSize;
        else if (item == Items.arrow) r += stack.stackSize;
      }
    }
    if (p != pearlCount) { pearlCount = p; pearlText = "Ender pearls: " + p; }
    if (a != appleCount) { appleCount = a; appleText = "Golden apples: " + a; }
    if (r != arrowCount) { arrowCount = r; arrowText = "Arrows: " + r; }
    int lines = 0;
    float widest = 0;
    if (pearls.get()) { lines++; widest = Math.max(widest, textWidth("Ender pearls: 16")); }
    if (apples.get()) { lines++; widest = Math.max(widest, textWidth("Golden apples: 64")); }
    if (arrows.get()) { lines++; widest = Math.max(widest, textWidth("Arrows: 256")); }
    size(Math.max(widest, 20), linesHeight(Math.max(lines, 1)));
  }

  @Override protected void draw(float partialTicks, boolean editor) {
    float y = 0;
    if (pearls.get()) { line(pearlText, pearlCount, y, editor); y += lineStep(); }
    if (apples.get()) { line(appleText, appleCount, y, editor); y += lineStep(); }
    if (arrows.get()) line(arrowText, arrowCount, y, editor);
  }

  private void line(String text, int count, float y, boolean editor) {
    text(text, 0, y, !editor && count < warnBelow.get() ? warnColor.get() : textColor.get());
  }
}
