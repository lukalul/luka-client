package dev.cbui.mixin;

import dev.cbui.Profiler;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class GpuProfilerEntityRendererMixin {
  @Inject(method="updateCameraAndRender", at=@At("HEAD"), require=0) private void cbui$frameBegin(CallbackInfo ci) { Profiler.gpuBegin(Profiler.GpuPhase.FRAME); }
  @Inject(method="updateCameraAndRender", at=@At("RETURN"), require=0) private void cbui$frameEnd(CallbackInfo ci) { Profiler.gpuEnd(Profiler.GpuPhase.FRAME); }
  @Inject(method="renderRainSnow", at=@At("HEAD"), require=0) private void cbui$weatherBegin(CallbackInfo ci) { Profiler.gpuBegin(Profiler.GpuPhase.WEATHER); }
  @Inject(method="renderRainSnow", at=@At("RETURN"), require=0) private void cbui$weatherEnd(CallbackInfo ci) { Profiler.gpuEnd(Profiler.GpuPhase.WEATHER); }
  @Inject(method="renderHand", at=@At("HEAD"), require=0) private void cbui$handBegin(CallbackInfo ci) { Profiler.gpuBegin(Profiler.GpuPhase.HAND); }
  @Inject(method="renderHand", at=@At("RETURN"), require=0) private void cbui$handEnd(CallbackInfo ci) { Profiler.gpuEnd(Profiler.GpuPhase.HAND); }
}
