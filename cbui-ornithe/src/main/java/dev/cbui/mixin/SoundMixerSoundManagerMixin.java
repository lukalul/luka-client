package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cbui.Cbui;
import dev.cbui.feature.soundmixer.SoundMixerModule;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.audio.SoundPoolEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sound Tweaks: scales each sound's volume by its group or rule, and can drop vanilla's upper limit of 1. */
@Mixin(SoundManager.class)
public abstract class SoundMixerSoundManagerMixin {
  @WrapOperation(method = "getNormalizedVolume", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/util/MathHelper;clamp_double(DDD)D"), require = 0)
  private double cbui$scaleVolume(double value, double min, double max, Operation<Double> original,
      @Local(argsOnly = true) ISound sound) {
    SoundMixerModule module = null;
    double scaled = value;
    try {
      module = SoundMixerModule.active();
      if (module != null) scaled = value * module.factor(sound);
    } catch (Throwable error) {
      Cbui.reportOnce("SoundTweaks volume", error);
      module = null;
      scaled = value;
    }
    if (module != null && module.liftCeiling()) return Math.max(min, scaled);
    return original.call(scaled, min, max);
  }

  @Inject(method = "getNormalizedVolume", at = @At("RETURN"), require = 0)
  private void cbui$noteLoudVolume(ISound sound, SoundPoolEntry entry, SoundCategory category,
      CallbackInfoReturnable<Float> cir) {
    try {
      SoundMixerModule module = SoundMixerModule.active();
      if (module != null) module.volumeWorkedOut(this, sound, cir.getReturnValueF());
    } catch (Throwable error) {
      Cbui.reportOnce("SoundTweaks loud volume", error);
    }
  }
}
