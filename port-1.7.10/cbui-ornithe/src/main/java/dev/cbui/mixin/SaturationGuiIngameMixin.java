package dev.cbui.mixin;

import dev.cbui.feature.saturation.SaturationHud;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Saturation card: draws around vanilla's hunger icons (1.7.10 draws the bars in renderIcons(width, height)). */
@Mixin(GuiIngame.class)
public abstract class SaturationGuiIngameMixin {
  @Inject(method = "renderIcons", require = 0,
      at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", args = "ldc=food", shift = At.Shift.AFTER))
  private void cbui$saturationUnder(int width, int height, CallbackInfo ci) {
    SaturationHud.beforeFood((Gui) (Object) this, width, height);
  }

  // At the end of the method rather than at its "air" section: other mods rewrite that call, and the overlay draws on
  // the hunger row only, which nothing after it covers (air bubbles sit a row higher).
  @Inject(method = "renderIcons", require = 0, at = @At("RETURN"))
  private void cbui$saturationOver(int width, int height, CallbackInfo ci) {
    SaturationHud.afterFood((Gui) (Object) this, width, height);
  }
}
