package dev.luka.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Small stand-ins for Minecraft 1.8 conveniences that 1.7.10 lacks. */
public final class Mc17 {
  private static RenderItem renderItem;

  private Mc17() { }

  /** 1.7.10 has no shared RenderItem on Minecraft; the GUI code builds its own in the same way. */
  public static RenderItem renderItem() {
    if (renderItem == null) renderItem = new RenderItem();
    return renderItem;
  }

  public static void drawItem(Minecraft mc, ItemStack stack, int x, int y) {
    renderItem().renderItemIntoGUI(mc.fontRendererObj, mc.getTextureManager(), stack, x, y);
  }

  public static void drawItemAndEffect(Minecraft mc, ItemStack stack, int x, int y) {
    renderItem().renderItemAndEffectIntoGUI(mc.fontRendererObj, mc.getTextureManager(), stack, x, y);
  }

  /** Item.getByNameOrId: a registry name ("minecraft:stone" or "stone") or a numeric id. */
  public static Item itemByNameOrId(String name) {
    Object named = Item.itemRegistry.getObject(name);
    if (named instanceof Item) return (Item) named;
    try {
      return Item.getItemById(Integer.parseInt(name));
    } catch (NumberFormatException notNumeric) {
      return null;
    }
  }
}
