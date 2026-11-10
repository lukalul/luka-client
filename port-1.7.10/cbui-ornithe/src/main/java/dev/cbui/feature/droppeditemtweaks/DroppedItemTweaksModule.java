package dev.cbui.feature.droppeditemtweaks;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import java.util.HashSet;
import java.util.Set;
import dev.luka.compat.GlStateManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/** Controls for items lying on the ground: no bobbing, a cap on how many copies of a stack are drawn, and their size. */
public final class DroppedItemTweaksModule extends FeatureModule {
  /** One row of the "Custom Item Scale List": registry name, menu label, and whether it starts ticked. */
  private static final class Pick {
    final String registryName;
    final String label;
    final boolean tickedByDefault;

    Pick(String registryName, String label, boolean tickedByDefault) {
      this.registryName = registryName;
      this.label = label;
      this.tickedByDefault = tickedByDefault;
    }
  }

  private static final Pick[] PICKS = {
      new Pick("minecraft:apple", "Apple", true), new Pick("minecraft:golden_apple", "Golden Apple", true),
      new Pick("minecraft:gold_ingot", "Gold Ingot", true), new Pick("minecraft:gold_nugget", "Gold Nugget", true),
      new Pick("minecraft:gold_block", "Gold Block", true), new Pick("minecraft:skull", "Skull / Head", true),
      new Pick("minecraft:ender_pearl", "Ender Pearl", false), new Pick("minecraft:diamond", "Diamond", false),
      new Pick("minecraft:emerald", "Emerald", false), new Pick("minecraft:iron_ingot", "Iron Ingot", false),
      new Pick("minecraft:golden_carrot", "Golden Carrot", false), new Pick("minecraft:potion", "Potion", false),
      new Pick("minecraft:experience_bottle", "Experience Bottle", false), new Pick("minecraft:arrow", "Arrow", false),
      new Pick("minecraft:diamond_sword", "Diamond Sword", false), new Pick("minecraft:diamond_helmet", "Diamond Helmet", false),
      new Pick("minecraft:diamond_chestplate", "Diamond Chestplate", false),
      new Pick("minecraft:diamond_leggings", "Diamond Leggings", false), new Pick("minecraft:diamond_boots", "Diamond Boots", false),
      new Pick("minecraft:gold_ore", "Gold Ore", false),
  };

  public final Setting.Toggle staticItems = add(new Setting.Toggle("Static Dropped Items", false,
      "Dropped items stay at one height instead of floating up and down."));
  public final Setting.Slider dropStackCount = add(new Setting.Slider("Dropped Stack Item Count", 0f, 0f, 64f, 1f,
      "Most copies of a stack to draw on the ground. Counts items directly rather than vanilla's steps; 0 leaves vanilla alone."));
  public final Setting.Slider itemScale = add(new Setting.Slider("Item Scale", 1f, 0f, 3f, 0.1f,
      "Size multiplier for items on the ground."));
  public final Setting.Slider customItemScale = add(new Setting.Slider("Custom Item Scale", 0f, 0f, 3f, 0.1f,
      "Size used instead of Item Scale for the ticked items below; 0 turns this off."));

  private final Setting.Toggle[] ticks = new Setting.Toggle[PICKS.length];
  private final Set<String> tickedNames = new HashSet<>();
  private int tickedMask = -1;

  public DroppedItemTweaksModule() {
    super("Dropped Item Tweaks", false);
    for (int i = 0; i < PICKS.length; i++) {
      Pick pick = PICKS[i];
      ticks[i] = add(new Setting.Toggle(pick.label, pick.tickedByDefault,
          "Custom Item Scale List: apply the Custom Item Scale to " + pick.registryName + "."));
      grouped("Custom Item Scale List", ticks[i]);
    }
  }

  @Override public String category() { return "VISUALS"; }

  /** The enabled module, or null when it is off or unavailable. */
  public static DroppedItemTweaksModule active() {
    try {
      DroppedItemTweaksModule module = Features.get(DroppedItemTweaksModule.class);
      return module != null && module.enabled() ? module : null;
    } catch (Throwable failure) {
      return null;
    }
  }

  /** The value the bobbing animation should use; -1 is the sine's lowest point, so it holds the item still. */
  public float bob(float sine) { return staticItems.get() ? -1.0F : sine; }

  /** How many copies of the stack to draw: the configured cap if there is one, otherwise vanilla's own number. */
  public int stackAmount(int vanillaCount, ItemStack stack) {
    int cap = Math.round(dropStackCount.get());
    if (cap == 0 || stack == null) return vanillaCount;
    return Math.min(cap, stack.stackSize);
  }

  /** The size multiplier for this stack: the custom one when it is set and the item is ticked, else the general one. */
  public float scaleFor(ItemStack stack) {
    float custom = customItemScale.get();
    if (custom != 0f && stack != null && isTicked(stack.getItem())) return custom;
    return itemScale.get();
  }

  /** Applies the size multiplier to the current GL matrix, lifting the item so it keeps resting on the ground. */
  public void applyGroundScale(ItemStack stack) {
    float scale = scaleFor(stack);
    if (scale == 1.0F) return;
    GlStateManager.translate(0.0F, 0.125F * (scale - 1.0F), 0.0F);
    GlStateManager.scale(scale, scale, scale);
  }

  private boolean isTicked(Item item) {
    if (item == null) return false;
    refreshTickedNames();
    if (tickedNames.isEmpty()) return false;
    String registered = Item.itemRegistry.getNameForObject(item);
    return registered != null && tickedNames.contains(registered);
  }

  /** Rebuilds the set of ticked registry names only when some tick box actually changed. */
  private void refreshTickedNames() {
    int mask = 0;
    for (int i = 0; i < ticks.length; i++) {
      if (ticks[i].get()) mask |= 1 << i;
    }
    if (mask == tickedMask) return;
    tickedNames.clear();
    for (int i = 0; i < ticks.length; i++) {
      if ((mask >> i & 1) != 0) tickedNames.add(PICKS[i].registryName);
    }
    tickedMask = mask;
  }
}
