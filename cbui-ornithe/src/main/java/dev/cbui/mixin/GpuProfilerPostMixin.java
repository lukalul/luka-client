package dev.cbui.mixin;

import dev.cbui.Profiler;
import net.minecraft.client.shader.ShaderGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderGroup.class)
public abstract class GpuProfilerPostMixin {
  @Inject(method = "loadShaderGroup", at = @At("HEAD"), require = 0)
  private void cbui$postBegin(CallbackInfo ci) { Profiler.gpuBegin(Profiler.GpuPhase.POST); }

  @Inject(method = "loadShaderGroup", at = @At("RETURN"), require = 0)
  private void cbui$postEnd(CallbackInfo ci) { Profiler.gpuEnd(Profiler.GpuPhase.POST); }
}
