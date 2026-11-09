package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rain / snow row on Argentum, which draws weather itself instead of through vanilla's renderRainSnow. */
@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.environment.WeatherRenderer", remap = false)
public abstract class AnimationsArgentumWeatherMixin {
  @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$weather(CallbackInfoReturnable<Boolean> cir) {
    if (!GlobalOptions.animationEnabled("rain_snow")) cir.setReturnValue(Boolean.TRUE);
  }
}
