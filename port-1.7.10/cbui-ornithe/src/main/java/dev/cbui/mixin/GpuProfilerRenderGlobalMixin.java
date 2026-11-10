package dev.cbui.mixin;

import dev.cbui.Profiler;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.entity.EntityLivingBase;

@Mixin(RenderGlobal.class)
public abstract class GpuProfilerRenderGlobalMixin {
  @Inject(method = "sortAndRender", at = @At("HEAD"), require = 0)
  private void cbui$terrainBegin(EntityLivingBase viewer, int pass, double partialTicks, CallbackInfoReturnable<Integer> cir) { Profiler.gpuBegin(cbui$layer(pass)); }

  @Inject(method = "sortAndRender", at = @At("RETURN"), require = 0)
  private void cbui$terrainEnd(EntityLivingBase viewer, int pass, double partialTicks, CallbackInfoReturnable<Integer> cir) { Profiler.gpuEnd(cbui$layer(pass)); }

  private static Profiler.GpuPhase cbui$layer(int pass) {
    return pass == 1 ? Profiler.GpuPhase.TERRAIN_TRANSLUCENT : Profiler.GpuPhase.TERRAIN_SOLID;
  }

  @Inject(method = "renderSky", at = @At("HEAD"), require = 0) private void cbui$skyBegin(CallbackInfo ci) { Profiler.gpuBegin(Profiler.GpuPhase.SKY); }
  @Inject(method = "renderSky", at = @At("RETURN"), require = 0) private void cbui$skyEnd(CallbackInfo ci) { Profiler.gpuEnd(Profiler.GpuPhase.SKY); }
  @Inject(method = "renderClouds", at = @At("HEAD"), require = 0) private void cbui$cloudsBegin(CallbackInfo ci) { Profiler.gpuBegin(Profiler.GpuPhase.CLOUDS); }
  @Inject(method = "renderClouds", at = @At("RETURN"), require = 0) private void cbui$cloudsEnd(CallbackInfo ci) { Profiler.gpuEnd(Profiler.GpuPhase.CLOUDS); }
  @Inject(method = "drawSelectionBox", at = @At("HEAD"), require = 0) private void cbui$outlineBegin(CallbackInfo ci) { Profiler.gpuBegin(Profiler.GpuPhase.BLOCK_OUTLINE); }
  @Inject(method = "drawSelectionBox", at = @At("RETURN"), require = 0) private void cbui$outlineEnd(CallbackInfo ci) { Profiler.gpuEnd(Profiler.GpuPhase.BLOCK_OUTLINE); }

  @Inject(method = "renderEntities", at = @At("HEAD"), require = 0)
  private void cbui$entitiesBegin(CallbackInfo ci) { Profiler.gpuBegin(Profiler.GpuPhase.ENTITIES); }

  @Inject(method = "renderEntities", at = @At("RETURN"), require = 0)
  private void cbui$entitiesEnd(CallbackInfo ci) { Profiler.gpuEnd(Profiler.GpuPhase.TILE_ENTITIES); Profiler.gpuEnd(Profiler.GpuPhase.ENTITIES); }
}
