package dev.cbui.mixin;

import dev.cbui.config.AnimationOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Distance fog row on Argentum: its terrain shaders take their fog from here. Same condition as the vanilla path in AnimationsFogMixin. */
@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.terrain.fog.ArgentumFogService", remap = false)
public abstract class AnimationsArgentumFogMixin {
  @Inject(method = "getFogEnd", at = @At("RETURN"), cancellable = true, require = 0)
  private void cbui$end(CallbackInfoReturnable<Float> cir) {
    if (AnimationOptions.distanceFogCleared) cir.setReturnValue(100001.0F);
    else if (AnimationOptions.fogAdjusted) cir.setReturnValue(AnimationOptions.fogEndNow);
  }

  @Inject(method = "getFogStart", at = @At("RETURN"), cancellable = true, require = 0)
  private void cbui$start(CallbackInfoReturnable<Float> cir) {
    if (AnimationOptions.distanceFogCleared) cir.setReturnValue(100000.0F);
    else if (AnimationOptions.fogAdjusted) cir.setReturnValue(AnimationOptions.fogStartNow);
  }

  @Inject(method = "getFogCutoff", at = @At("RETURN"), cancellable = true, require = 0)
  private void cbui$cutoff(CallbackInfoReturnable<Float> cir) {
    if (AnimationOptions.distanceFogCleared) cir.setReturnValue(100001.0F);
    else if (AnimationOptions.fogAdjusted) cir.setReturnValue(AnimationOptions.fogEndNow);
  }
}
