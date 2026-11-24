package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cbui.feature.crosshair.CrosshairModule;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Crosshair: takes the game's own "show the crosshair?" answer, lets luka draw its crosshair right there, and answers
 * "no" to the game whenever luka took charge so the vanilla icon is not drawn on top.
 */
@Mixin(GuiIngame.class)
public abstract class CrosshairGuiIngameMixin {
  @WrapOperation(method = "renderGameOverlay",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;showCrosshair()Z"),
      require = 0)
  private boolean cbui$crosshair(GuiIngame self, Operation<Boolean> original, @Local(argsOnly = true) float partialTicks) {
    boolean vanilla = original.call(self);
    return !CrosshairModule.takeOver(vanilla, partialTicks) && vanilla;
  }
}
