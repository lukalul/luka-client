package dev.cbui.feature.grounditems;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import dev.luka.compat.GlStateManager;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * How item entities lying on the ground are drawn: no bobbing, one copy per item, and size (with a separate size for a
 * hand-picked list of valuable items). Only the picture changes; pickup and physics stay vanilla.
 */
public final class GroundItemsModule extends FeatureModule {
  private static final String PICKED = "Picked items";
  private static final String PICK_TIP = "Use the picked-items size for this item";
  /** Distance from an item entity's position down to the ground it rests on (half its 0.25 block height). */
  private static final float FOOT = 0.125f;

  private final Setting.Toggle holdStill = add(new Setting.Toggle(
      "Hold items still", false, "Dropped items stop bobbing up and down"));
  private final Setting.Slider copyCap = add(new Setting.Slider(
      "Copies per stack", 0, 0, 64, 1,
      "Draw one copy per item, up to this many. 0 keeps vanilla. High values cost frames where many items lie around"));
  private final Setting.Slider size = add(new Setting.Slider(
      "Item size", 1, 0, 3, 0.1f, "Size of items on the ground"));
  private final Setting.Slider pickedSize = add(new Setting.Slider(
      "Size for picked items", 0, 0, 3, 0.1f, "Size for the items ticked below. 0 turns it off"));

  /** Row label, registry name and default of every pickable item kind, in menu order. */
  private static final Object[][] KINDS = {
      {"Apples", "minecraft:apple", true},
      {"Golden apples", "minecraft:golden_apple", true},
      {"Gold ingots", "minecraft:gold_ingot", true},
      {"Gold nuggets", "minecraft:gold_nugget", true},
      {"Gold blocks", "minecraft:gold_block", true},
      {"Mob heads", "minecraft:skull", true},
      {"Ender pearls", "minecraft:ender_pearl", false},
      {"Diamonds", "minecraft:diamond", false},
      {"Emeralds", "minecraft:emerald", false},
      {"Iron ingots", "minecraft:iron_ingot", false},
      {"Golden carrots", "minecraft:golden_carrot", false},
      {"Potions", "minecraft:potion", false},
      {"Bottles o' enchanting", "minecraft:experience_bottle", false},
      {"Arrows", "minecraft:arrow", false},
      {"Diamond swords", "minecraft:diamond_sword", false},
      {"Diamond helmets", "minecraft:diamond_helmet", false},
      {"Diamond chestplates", "minecraft:diamond_chestplate", false},
      {"Diamond leggings", "minecraft:diamond_leggings", false},
      {"Diamond boots", "minecraft:diamond_boots", false},
      {"Gold ore blocks", "minecraft:gold_ore", false},
  };

  private final Setting.Toggle[] picks = new Setting.Toggle[KINDS.length];

  /** Items whose toggle is on, rebuilt only when the toggles change. */
  private Set<Item> pickedItems = Collections.emptySet();
  private long pickedMask = -1L;

  public GroundItemsModule() {
    super("Dropped Item Tweaks", false);
    for (int i = 0; i < KINDS.length; i++) {
      picks[i] = grouped(PICKED, add(new Setting.Toggle((String) KINDS[i][0], (Boolean) KINDS[i][2], PICK_TIP)));
    }
  }

  @Override public String category() { return "VISUALS"; }

  /** The module when it exists and is switched on, otherwise null. */
  public static GroundItemsModule active() {
    try {
      GroundItemsModule module = Features.get(GroundItemsModule.class);
      return module != null && module.enabled() ? module : null;
    } catch (Throwable failure) {
      return null;
    }
  }

  /** The bob's sine value: -1 pins the item to the bottom of its float. */
  public float bobWave(float vanillaWave) {
    return holdStill.get() ? -1f : vanillaWave;
  }

  /** True when this stack should not be drawn at all (its size is 0). */
  public boolean hidden(ItemStack stack) {
    return sizeFor(stack) <= 0f;
  }

  /** Copies to draw for this stack: vanilla's count unless a cap is set, then the real count up to the cap. */
  public int copies(ItemStack stack, int vanillaCopies) {
    int cap = Math.round(copyCap.get());
    if (cap <= 0 || stack == null) return vanillaCopies;
    return Math.max(1, Math.min(stack.stackSize, cap));
  }

  /**
   * Called right after vanilla moved the matrix to the item's position. The item's foot sits a fixed eighth of a
   * block below that point, so lifting by that much per unit of extra size, then scaling, keeps it on the ground.
   */
  public void resize(ItemStack stack) {
    float factor = sizeFor(stack);
    if (factor <= 0f || Math.abs(factor - 1f) < 1e-3f) return;
    GlStateManager.translate(0f, FOOT * (factor - 1f), 0f);
    GlStateManager.scale(factor, factor, factor);
  }

  /** Size multiplier for this stack (1 = vanilla). */
  public float sizeFor(ItemStack stack) {
    float picked = pickedSize.get();
    if (picked > 0f && stack != null && isPicked(stack.getItem())) return picked;
    return size.get();
  }

  private boolean isPicked(Item item) {
    if (item == null) return false;
    long mask = 0L;
    for (int i = 0; i < picks.length; i++) if (picks[i].get()) mask |= 1L << i;
    if (mask != pickedMask) rebuildPicked(mask);
    return pickedItems.contains(item);
  }

  private void rebuildPicked(long mask) {
    Set<Item> items = Collections.newSetFromMap(new IdentityHashMap<Item, Boolean>());
    for (int i = 0; i < KINDS.length; i++) {
      if ((mask & (1L << i)) == 0) continue;
      Object item = Item.itemRegistry.getObject((String) KINDS[i][1]);
      if (item instanceof Item) items.add((Item) item);
    }
    pickedItems = items;
    pickedMask = mask;
  }
}
