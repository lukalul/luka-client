package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class AnimationsWeatherMixin {
  @Inject(method = "renderRainSnow", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$filterWeather(float partialTicks, CallbackInfo ci) {
    if (!GlobalOptions.animationEnabled("rain_snow")) ci.cancel();
  }
}
