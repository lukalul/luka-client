package dev.cbui.mixin;

import dev.cbui.Profiler;
import net.minecraft.client.particle.EffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EffectRenderer.class)
public abstract class GpuProfilerParticlesMixin {
  @Inject(method = {"renderLitParticles", "renderParticles"}, at = @At("HEAD"), require = 0)
  private void cbui$particlesBegin(CallbackInfo ci) { Profiler.gpuBegin(Profiler.GpuPhase.PARTICLES); }

  @Inject(method = {"renderLitParticles", "renderParticles"}, at = @At("RETURN"), require = 0)
  private void cbui$particlesEnd(CallbackInfo ci) { Profiler.gpuEnd(Profiler.GpuPhase.PARTICLES); }
}
