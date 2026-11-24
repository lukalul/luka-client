package dev.cbui.mixin;

import dev.cbui.feature.saturation.SaturationHud;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Saturation card: draws around vanilla's hunger icons. The profiler sections mark where the hunger bar starts and ends. */
@Mixin(GuiIngame.class)
public abstract class SaturationGuiIngameMixin {
  @Inject(method = "renderPlayerStats", require = 0,
      at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", args = "ldc=food", shift = At.Shift.AFTER))
  private void cbui$saturationUnder(ScaledResolution res, CallbackInfo ci) {
    SaturationHud.beforeFood((Gui) (Object) this, res.getScaledWidth(), res.getScaledHeight());
  }

  // At the end of the method rather than at its "air" section: other mods rewrite that call, and the overlay draws on
  // the hunger row only, which nothing after it covers (air bubbles sit a row higher).
  @Inject(method = "renderPlayerStats", require = 0, at = @At("RETURN"))
  private void cbui$saturationOver(ScaledResolution res, CallbackInfo ci) {
    SaturationHud.afterFood((Gui) (Object) this, res.getScaledWidth(), res.getScaledHeight());
  }
}
