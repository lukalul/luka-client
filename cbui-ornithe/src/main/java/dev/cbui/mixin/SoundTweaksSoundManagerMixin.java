package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.feature.Features;
import dev.cbui.feature.soundtweaks.SoundTweaksModule;
import net.minecraft.client.audio.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SoundManager.class)
public abstract class SoundTweaksSoundManagerMixin {
  @WrapOperation(method = "getNormalizedVolume", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MathHelper;clamp_double(DDD)D"), require = 0)
  private double cbui$chooseVolumeLimit(double candidate, double floor, double ceiling, Operation<Double> vanillaClamp) {
    SoundTweaksModule module;
    try { module = Features.get(SoundTweaksModule.class); }
    catch (Throwable ignored) { module = null; }
    return module != null && module.enabled() && module.removeCap.get()
        ? (candidate < floor ? floor : candidate)
        : vanillaClamp.call(candidate, floor, ceiling);
  }
}
