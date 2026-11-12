package dev.cbui.mixin;

import dev.cbui.gui.EffectsBeforeTooltip;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Potion effects in the inventory are drawn under the item tooltip, not over it (see {@link EffectsBeforeTooltip}). */
@Mixin(InventoryEffectRenderer.class)
public abstract class EffectsBeforeTooltipMixin implements EffectsBeforeTooltip {
  @Shadow private boolean hasActivePotionEffects;
  @Shadow private void drawActivePotionEffects() { }

  @Unique private boolean cbui$effectsDrawn;

  @Override
  public void cbui$drawEffectsBeforeTooltip() {
    if (!hasActivePotionEffects) return;
    drawActivePotionEffects();
    cbui$effectsDrawn = true;
  }

  @Redirect(method = "drawScreen", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/renderer/InventoryEffectRenderer;drawActivePotionEffects()V"), require = 0)
  private void cbui$skipIfDrawn(InventoryEffectRenderer self) {
    if (cbui$effectsDrawn) {
      cbui$effectsDrawn = false;
      return;
    }
    drawActivePotionEffects();
  }
}
