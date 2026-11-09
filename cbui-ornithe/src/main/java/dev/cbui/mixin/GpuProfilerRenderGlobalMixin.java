package dev.cbui.mixin;

import dev.cbui.Profiler;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.util.EnumWorldBlockLayer;

@Mixin(RenderGlobal.class)
public abstract class GpuProfilerRenderGlobalMixin {
  @Inject(method = "renderBlockLayer", at = @At("HEAD"), require = 0)
  private void cbui$terrainBegin(EnumWorldBlockLayer layer, double partialTicks, int pass, net.minecraft.entity.Entity entity, CallbackInfoReturnable<Integer> cir) { Profiler.gpuBegin(cbui$layer(layer)); }

  @Inject(method = "renderBlockLayer", at = @At("RETURN"), require = 0)
  private void cbui$terrainEnd(EnumWorldBlockLayer layer, double partialTicks, int pass, net.minecraft.entity.Entity entity, CallbackInfoReturnable<Integer> cir) { Profiler.gpuEnd(cbui$layer(layer)); }

  private static Profiler.GpuPhase cbui$layer(EnumWorldBlockLayer layer) {
    if (layer == EnumWorldBlockLayer.TRANSLUCENT) return Profiler.GpuPhase.TERRAIN_TRANSLUCENT;
    if (layer == EnumWorldBlockLayer.CUTOUT || layer == EnumWorldBlockLayer.CUTOUT_MIPPED) return Profiler.GpuPhase.TERRAIN_CUTOUT;
    return Profiler.GpuPhase.TERRAIN_SOLID;
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
