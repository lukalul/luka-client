package dev.cbui.hud.module;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Gui;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;

public final class PotionModule extends AbstractHudModule {
  private static final String SAMPLE = "Speed II 1:24";
  private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");
  private final Setting.Toggle icons = grouped("Display", add(new Setting.Toggle("Show icons", true, "Draw the icon of each effect next to its name and time")));
  private final List<String> lines = new ArrayList<>();
  /** Status icon index of each line (-1: the potion has none). */
  private final List<Integer> iconIndex = new ArrayList<>();

  public PotionModule() { super("Potion status", false, Anchor.BOTTOM_RIGHT, -5, -84, 80, 11); }

  private int iconSize() { return icons.get() ? lineHeight() : 0; }

  /** Space the icon takes in front of the text. */
  private int iconSpace() { return icons.get() ? lineHeight() + 2 : 0; }

  @Override public void tick() {
    lines.clear();
    iconIndex.clear();
    if (mc.thePlayer != null)
      for (Object active : mc.thePlayer.getActivePotionEffects()) {
        PotionEffect effect = (PotionEffect) active;
        Potion potion = Potion.potionTypes[effect.getPotionID()];
        if (potion == null) continue;
        String name = I18n.format(potion.getName());
        if (effect.getAmplifier() > 0) name += " " + (effect.getAmplifier() + 1);
        lines.add(name + " " + Potion.getDurationString(effect));
        iconIndex.add(potion.hasStatusIcon() ? potion.getStatusIconIndex() : -1);
      }
    int widest = textWidth(SAMPLE) + iconSpace();
    for (String line : lines) widest = Math.max(widest, textWidth(line) + iconSpace());
    size(widest, linesHeight(Math.max(1, lines.size())));
  }

  @Override protected boolean nothingToShow() { return lines.isEmpty(); }

  @Override protected void draw(float partialTicks, boolean editor) {
    int space = iconSpace();
    if (lines.isEmpty()) {
      if (editor) {
        drawIcon(1, 0, 0); // speed
        text(SAMPLE, space, 0, textColor.get());
      }
      return;
    }
    float y = 0;
    for (int i = 0; i < lines.size(); i++) {
      drawIcon(iconIndex.get(i), 0, y);
      text(lines.get(i), space, y, textColor.get());
      y += lineStep();
    }
  }

  private void drawIcon(int index, float x, float y) {
    if (!icons.get() || index < 0) return;
    int size = iconSize();
    GlStateManager.color(1, 1, 1, 1);
    GlStateManager.enableBlend();
    mc.getTextureManager().bindTexture(INVENTORY);
    // status icons are an 8-per-row sheet of 18 px cells starting at v = 198 in the inventory texture
    Gui.drawScaledCustomSizeModalRect(Math.round(x), Math.round(y), (index % 8) * 18, 198 + (index / 8) * 18, 18, 18, size, size, 256, 256);
    GlStateManager.color(1, 1, 1, 1);
  }
}
