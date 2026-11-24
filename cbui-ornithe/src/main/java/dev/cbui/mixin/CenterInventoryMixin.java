package dev.cbui.mixin;

import dev.cbui.feature.centerinventory.CenterInventoryModule;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import net.minecraft.inventory.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Auto Center Inventory: vanilla moves the inventory right while potion effects are active (guiLeft = 160 + (width -
 * xSize - 200) / 2); this puts it back in the middle. The effect boxes are drawn relative to guiLeft, so they move along
 * and stay beside it.
 */
@Mixin(InventoryEffectRenderer.class)
public abstract class CenterInventoryMixin extends GuiContainer {
  protected CenterInventoryMixin(Container container) { super(container); }

  @Inject(method = "updateActivePotionEffects", at = @At("RETURN"), require = 0)
  private void cbui$centerInventory(CallbackInfo ci) {
    if (CenterInventoryModule.on()) this.guiLeft = (this.width - this.xSize) / 2;
  }
}
