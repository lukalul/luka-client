package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.Cbui;
import dev.cbui.feature.polycrosshair.CrosshairRenderer;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Intercepts the vanilla "should the crosshair show" query so the custom crosshair can take its place. */
@Mixin(GuiIngame.class)
public abstract class PolyCrosshairGuiIngameMixin {
  @ModifyExpressionValue(
      method = "renderGameOverlay",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;showCrosshair()Z"),
      require = 0)
  private boolean cbui$swapCrosshair(boolean vanillaWanted) {
    boolean replacedByCustom;
    try {
      replacedByCustom = CrosshairRenderer.render(vanillaWanted);
    } catch (Throwable failure) {
      Cbui.reportOnce("crosshair hook", failure);
      return vanillaWanted;
    }
    return vanillaWanted && !replacedByCustom;
  }
}
