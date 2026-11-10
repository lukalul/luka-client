package dev.cbui.hud.module;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;
import dev.cbui.render.Draw;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Worn armour (helmet first) with its remaining durability. */
public final class ArmourModule extends AbstractHudModule {
  private static final int ICON = 16, GAP = 2;
  private final Setting.Choice layout = grouped("Display", add(Setting.tip(new Setting.Choice("Layout", "Vertical", "Vertical", "Horizontal"), "Stack the armour pieces in a column or lay them in a row")));
  private final Setting.Choice value = grouped("Display", add(Setting.tip(new Setting.Choice("Durability", "Percent", "Percent", "Remaining", "Hidden"), "What to show next to each piece: percent left, uses left, or nothing")));
  private final Setting.Color lowColor = grouped("Display", adv(new Setting.Color("Low durability color", 0xFFFF5555, "Colour used when a piece is nearly broken")));
  private final ItemStack[] pieces = new ItemStack[4];
  private ItemStack[] samples;
  private final String[] labels = new String[4];
  private final int[] keys = {-2, -2, -2, -2};
  private final String[] modes = new String[4];
  private String lastLayout;
  private final boolean[] low = new boolean[4];

  public ArmourModule() {
    super("Armour status", false, Anchor.CENTER_LEFT, 5, 50, 70, 16);
    background.set(false);
  }

  @Override public void tick() {
    boolean changed = false;
    for (int i = 0; i < 4; i++) {
      ItemStack stack = mc.thePlayer == null ? null : mc.thePlayer.inventory.armorInventory[3 - i];
      pieces[i] = stack;
      int key = stack == null ? -1 : stack.getMaxDurability() * 4096 + stack.getCurrentDurability();
      String mode = value.get();
      if (key == keys[i] && mode == modes[i]) continue;
      keys[i] = key;
      modes[i] = mode;
      labels[i] = stack == null ? null : label(stack, i);
      changed = true;
    }
    String lay = layout.get();
    if (changed || lay != lastLayout) { lastLayout = lay; resize(); }
  }

  private String label(ItemStack stack, int i) {
    int max = stack.getMaxDurability();
    low[i] = false;
    if (max <= 0 || "Hidden".equals(value.get())) return null;
    int left = max - stack.getCurrentDurability();
    low[i] = left * 4 < max;
    return "Remaining".equals(value.get()) ? Integer.toString(left) : left * 100 / max + "%";
  }

  private void resize() {
    boolean vertical = "Vertical".equals(layout.get());
    boolean text = !"Hidden".equals(value.get());
    int widest = text ? textWidth("100%") : 0;
    for (String l : labels) if (l != null) widest = Math.max(widest, textWidth(l));
    if (vertical) size(ICON + (text ? 4 + widest : 0), 4 * ICON + 3 * GAP);
    else size(Math.max(4 * ICON + 3 * GAP, 4 * Math.max(ICON, widest) + 3 * GAP), ICON + (text ? 1 + lineHeight() : 0));
  }

  @Override protected void draw(float partialTicks, boolean editor) {
    if (mc.thePlayer == null && !editor) return;
    boolean vertical = "Vertical".equals(layout.get());
    float cell = vertical ? ICON : (contentWidth() - 3 * GAP) / 4f;
    for (int i = 0; i < 4; i++) {
      ItemStack stack = pieces[i];
      String label = labels[i];
      if (editor && stack == null) label = "Hidden".equals(value.get()) ? null : "100%";
      float x = vertical ? 0 : i * (cell + GAP), y = vertical ? i * (ICON + GAP) : 0;
      float iconX = vertical ? x : x + (cell - ICON) / 2f;
      if (stack != null) item(stack, (int) iconX, (int) y);
      else if (editor) item(sample(i), (int) iconX, (int) y);
      if (label == null) continue;
      int color = low[i] && stack != null ? lowColor.get() : textColor.get();
      if (vertical) text(label, ICON + 4, y + (ICON - lineHeight()) / 2f + 1, color);
      else text(label, x + (cell - textWidth(label)) / 2f, ICON + 1, color);
    }
  }

  /** Editor-only placeholder armour so an unequipped preview looks like the real thing. */
  private ItemStack sample(int slot) {
    if (samples == null)
      samples = new ItemStack[] {new ItemStack(Items.diamond_helmet), new ItemStack(Items.diamond_chestplate), new ItemStack(Items.diamond_leggings), new ItemStack(Items.diamond_boots)};
    return samples[slot];
  }

  private void item(ItemStack stack, int x, int y) {
    GlStateManager.enableRescaleNormal();
    RenderHelper.enableGUIStandardItemLighting();
    dev.luka.compat.Compat.renderItem(mc, stack, x, y);
    RenderHelper.disableStandardItemLighting();
    GlStateManager.disableRescaleNormal();
    GlStateManager.enableBlend();
  }
}
