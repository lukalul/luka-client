package dev.cbui.hud.module;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;
import java.util.Collection;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;

/**
 * Warnings you do not want to miss in a fight: low health, armour about to break and a helpful potion running out.
 * Each shows as a coloured line in the box (the box is hidden while there is nothing to warn about) and, when a new
 * warning starts, plays one short sound.
 */
public final class AlertsModule extends AbstractHudModule {
  private static final String[] PIECES = {"Helmet", "Chestplate", "Leggings", "Boots"};
  private static final int MAX_LINES = 8;

  private final Setting.Toggle health = grouped("Health", add(new Setting.Toggle("Low health", true, "Warn when your health is low")));
  private final Setting.Slider healthAt = grouped("Health", add(new Setting.Slider("Low health at", 4, 1, 10, .5f, "Warn at this many hearts or fewer")));
  private final Setting.Toggle armour = grouped("Armour", add(new Setting.Toggle("Armour breaking", true, "Warn when a worn armour piece is nearly broken")));
  private final Setting.Slider armourAt = grouped("Armour", add(new Setting.Slider("Armour warning at", 12, 3, 40, 1, "Warn at this percent of durability left or less")));
  private final Setting.Toggle potions = grouped("Potions", add(new Setting.Toggle("Potion ending", true, "Warn when a helpful potion effect is about to run out")));
  private final Setting.Slider potionSecs = grouped("Potions", add(new Setting.Slider("Potion warning at", 8, 2, 30, 1, "Warn when this many seconds are left or fewer")));
  private final Setting.Toggle sound = grouped("Sound", add(new Setting.Toggle("Warning sound", true, "A short note when a new warning starts")));
  private final Setting.Color healthColor = grouped("Colors", adv(new Setting.Color("Health color", 0xFFFF5555, "Colour of the low health line")));
  private final Setting.Color armourColor = grouped("Colors", adv(new Setting.Color("Armour color", 0xFFFFAA33, "Colour of the armour lines")));
  private final Setting.Color potionColor = grouped("Colors", adv(new Setting.Color("Potion color", 0xFF55D5FF, "Colour of the potion lines")));

  private final String[] lines = new String[MAX_LINES];
  private final int[] colors = new int[MAX_LINES];
  private int count;
  private int ticks;
  private boolean wasLow;
  private final boolean[] armourWasLow = new boolean[4];
  /** Bit per potion id: this effect was already warned about for its current run. */
  private int potionWarned;
  private int lastWidth = -1, lastCount = -1;

  public AlertsModule() {
    super("Alerts", false, Anchor.TOP_CENTER, 0, 96, 100, 11);
    background.set(false);
  }

  @Override public void tick() {
    if (mc.thePlayer == null || mc.thePlayer.isDead) {
      count = 0;
      return;
    }
    // a few times a second is plenty for warnings and keeps the strings from being rebuilt every tick
    if (ticks++ % 4 != 0) return;
    int n = 0;
    boolean newWarning = false;
    // health
    float hp = mc.thePlayer.getHealth();
    boolean low = health.get() && hp > 0 && hp <= healthAt.get() * 2f;
    if (low && n < MAX_LINES) {
      lines[n] = "LOW HEALTH  " + (Math.round(hp * 5f) / 10f);
      colors[n++] = healthColor.get();
      if (!wasLow) newWarning = true;
    }
    wasLow = low;
    // armour
    for (int i = 0; i < 4; i++) {
      ItemStack stack = mc.thePlayer.inventory.armorInventory[3 - i];
      boolean breaking = false;
      if (armour.get() && stack != null && stack.getMaxDurability() > 0) {
        int left = stack.getMaxDurability() - stack.getCurrentDurability();
        int pct = left * 100 / stack.getMaxDurability();
        if (pct <= armourAt.get()) {
          breaking = true;
          if (n < MAX_LINES) {
            lines[n] = PIECES[i] + " breaking  " + pct + "%";
            colors[n++] = armourColor.get();
          }
          if (!armourWasLow[i]) newWarning = true;
        }
      }
      armourWasLow[i] = breaking;
    }
    // helpful potions about to end
    if (potions.get()) {
      int seen = 0;
      Collection<PotionEffect> effects = mc.thePlayer.getActivePotionEffects();
      for (PotionEffect e : effects) {
        int id = e.getPotionID();
        Potion potion = id >= 0 && id < Potion.potionTypes.length ? Potion.potionTypes[id] : null;
        if (potion == null || potion.isBadEffect() || id >= 32) continue;
        int ticksLeft = e.getDuration();
        if (ticksLeft > 20 * 3600) continue; // effectively endless
        if (ticksLeft <= potionSecs.get() * 20f) {
          seen |= 1 << id;
          if (n < MAX_LINES) {
            lines[n] = I18n.format(potion.getName()) + " ends in " + (ticksLeft / 20 + 1) + "s";
            colors[n++] = potionColor.get();
          }
          if ((potionWarned & (1 << id)) == 0) newWarning = true;
        }
      }
      potionWarned = seen;
    } else {
      potionWarned = 0;
    }
    count = n;
    if (newWarning && sound.get() && mc.getSoundHandler() != null) {
      mc.getSoundHandler().playSound(PositionedSoundRecord.createPositionedSoundRecord(new ResourceLocation("note.pling"), 1.7f));
    }
    resize();
  }

  private void resize() {
    if (count == 0) return; // nothing to measure; the editor sizes its own sample lines
    int w = 40;
    for (int i = 0; i < count; i++) w = Math.max(w, textWidth(lines[i]));
    if (w != lastWidth || count != lastCount) {
      lastWidth = w;
      lastCount = count;
      size(w, Math.max(1, count) * lineStep());
    }
  }

  @Override protected boolean nothingToShow() {
    return count == 0;
  }

  @Override protected void draw(float partialTicks, boolean editor) {
    if (count == 0) {
      if (!editor) return;
      String[] sample = {"LOW HEALTH  3.5", "Boots breaking  9%", "Speed ends in 6s"};
      int[] col = {healthColor.get(), armourColor.get(), potionColor.get()};
      int w = 40;
      for (String s : sample) w = Math.max(w, textWidth(s));
      size(w, sample.length * lineStep());
      lastWidth = -1;
      for (int i = 0; i < sample.length; i++) centered(sample[i], i * lineStep(), col[i]);
      return;
    }
    for (int i = 0; i < count; i++) centered(lines[i], i * lineStep(), colors[i]);
  }

  private void centered(String text, float y, int color) {
    text(text, (contentWidth() - textWidth(text)) / 2f, y, color);
  }
}
