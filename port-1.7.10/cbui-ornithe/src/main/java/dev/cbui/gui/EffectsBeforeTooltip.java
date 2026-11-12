package dev.cbui.gui;

/**
 * The inventory's potion effect list, drawn before the item tooltip instead of after it. Vanilla draws the list once the
 * whole inventory, tooltip included, is done, so the boxes covered the tooltip. Implemented on InventoryEffectRenderer
 * by a mixin.
 */
public interface EffectsBeforeTooltip {
  /** Called by GuiContainer right before it draws the tooltip. */
  void cbui$drawEffectsBeforeTooltip();
}
