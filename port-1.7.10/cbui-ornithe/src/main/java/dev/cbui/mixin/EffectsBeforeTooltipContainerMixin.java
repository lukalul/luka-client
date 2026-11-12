package dev.cbui.mixin;

import dev.cbui.gui.EffectsBeforeTooltip;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right before the tooltip: the potion effect list of an inventory with effects (see {@link EffectsBeforeTooltip}). */
@Mixin(GuiContainer.class)
public abstract class EffectsBeforeTooltipContainerMixin {
  @Inject(method = "drawScreen", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPopMatrix()V", shift = At.Shift.AFTER, remap = false), require = 0)
  private void cbui$effectsBeforeTooltip(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
    Object self = this;
    if (self instanceof EffectsBeforeTooltip) {
      try {
        ((EffectsBeforeTooltip) self).cbui$drawEffectsBeforeTooltip();
      } catch (Throwable error) {
        dev.cbui.Cbui.reportOnce("inventory effects", error);
      }
    }
  }
}
