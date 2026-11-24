package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cbui.feature.crosshair.CrosshairModule;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Crosshair: 1.7.10 always draws its crosshair as the 16x16 icon at texture origin 0,0. Only that one icon draw is
 * replaceable; every other icon (hearts, armour, ...) has another origin or size and passes straight through.
 */
@Mixin(GuiIngame.class)
public abstract class CrosshairGuiIngameMixin {
  @WrapOperation(method = "renderGameOverlay(FZII)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;drawTexturedModalRect(IIIIII)V"),
      require = 0)
  private void cbui$crosshair(GuiIngame self, int x, int y, int u, int v, int width, int height,
      Operation<Void> original, @Local(argsOnly = true) float partialTicks) {
    if (u == 0 && v == 0 && width == 16 && height == 16 && CrosshairModule.takeOver(true, partialTicks)) return;
    original.call(self, x, y, u, v, width, height);
  }
}
