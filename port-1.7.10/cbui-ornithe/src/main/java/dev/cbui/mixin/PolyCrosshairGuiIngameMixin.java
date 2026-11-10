package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.cbui.Cbui;
import dev.cbui.feature.polycrosshair.CrosshairRenderer;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 1.7.10 draws the crosshair directly (a 16x16 icon at the origin of the texture); the custom crosshair takes that call's place. */
@Mixin(GuiIngame.class)
public abstract class PolyCrosshairGuiIngameMixin {
  @WrapWithCondition(
      method = "renderGameOverlay",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;drawTexturedModalRect(IIIIII)V"),
      require = 0)
  private boolean cbui$swapCrosshair(GuiIngame gui, int x, int y, int u, int v, int width, int height) {
    if (u != 0 || v != 0 || width != 16 || height != 16) return true;
    boolean replacedByCustom;
    try {
      replacedByCustom = CrosshairRenderer.render(true);
    } catch (Throwable failure) {
      Cbui.reportOnce("crosshair hook", failure);
      return true;
    }
    return !replacedByCustom;
  }
}
