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
  private static final String SAMPLE_NAME = "Speed II", SAMPLE_TIME = "1:24";
  private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");
  private final Setting.Toggle icons = grouped("Display", add(new Setting.Toggle("Show icons", true, "Draw the icon of each effect next to its name and time")));
  private final Setting.Color timeColor = grouped("Text", add(new Setting.Color("Time color", 0xFFAAAAAA, "Colour of the time under each effect's name")));
  private final List<String> names = new ArrayList<>();
  private final List<String> times = new ArrayList<>();
  /** Status icon index of each line (-1: the potion has none). */
  private final List<Integer> iconIndex = new ArrayList<>();

  public PotionModule() { super("Potion status", false, Anchor.BOTTOM_RIGHT, -5, -84, 80, 11); }

  /** Each effect is two lines (name, then time under it); its icon is as tall as both. */
  private int iconSize() { return icons.get() ? Math.round(linesHeight(2)) : 0; }

  /** Space the icon takes in front of the text. */
  private int iconSpace() { return icons.get() ? iconSize() + 4 : 0; }

  /** Height of one effect, plus a small gap to the next. */
  private float entryStep() { return Math.max(iconSize(), linesHeight(2)) + 2; }

  @Override public void tick() {
    names.clear();
    times.clear();
    iconIndex.clear();
    if (mc.thePlayer != null)
      for (Object active : mc.thePlayer.getActivePotionEffects()) {
        PotionEffect effect = (PotionEffect) active;
        Potion potion = Potion.potionTypes[effect.getPotionID()];
        if (potion == null) continue;
        String name = I18n.format(potion.getName());
        if (effect.getAmplifier() > 0) name += " " + level(effect.getAmplifier() + 1);
        names.add(name);
        times.add(Potion.getDurationString(effect));
        iconIndex.add(potion.hasStatusIcon() ? potion.getStatusIconIndex() : -1);
      }
    int widest = Math.max(textWidth(SAMPLE_NAME), textWidth(SAMPLE_TIME));
    for (int i = 0; i < names.size(); i++) widest = Math.max(widest, Math.max(textWidth(names.get(i)), textWidth(times.get(i))));
    size(widest + iconSpace(), entryStep() * Math.max(1, names.size()) - 2);
  }

  /** "II", "III"... like the inventory; past what the language file has, the plain number. */
  private static String level(int level) {
    String key = "enchantment.level." + level, text = I18n.format(key);
    return text.equals(key) ? Integer.toString(level) : text;
  }

  @Override protected boolean nothingToShow() { return names.isEmpty(); }

  @Override protected void draw(float partialTicks, boolean editor) {
    if (names.isEmpty()) {
      if (editor) drawEntry(1, SAMPLE_NAME, SAMPLE_TIME, 0); // speed
      return;
    }
    float y = 0;
    for (int i = 0; i < names.size(); i++) {
      drawEntry(iconIndex.get(i), names.get(i), times.get(i), y);
      y += entryStep();
    }
  }

  /** Icon on the left, the name, and the time under the name. */
  private void drawEntry(int icon, String name, String time, float y) {
    int space = iconSpace();
    // the two lines sit in the middle of the icon's height
    float textTop = y + Math.max(0f, (iconSize() - linesHeight(2)) / 2f);
    drawIcon(icon, 0, y);
    text(name, space, textTop, textColor.get());
    text(time, space, textTop + lineStep(), timeColor.get());
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
