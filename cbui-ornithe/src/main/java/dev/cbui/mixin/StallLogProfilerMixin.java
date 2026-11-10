package dev.cbui.mixin;

import dev.cbui.StallLog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The game names every part of its loop for its own profiler; the stall log times the outer ones. */
@Mixin(net.minecraft.profiler.Profiler.class)
public abstract class StallLogProfilerMixin {
  @Inject(method = "startSection", at = @At("HEAD"), require = 0)
  private void cbui$stallBegin(String name, CallbackInfo ci) {
    StallLog.begin(name);
  }

  @Inject(method = "endSection", at = @At("HEAD"), require = 0)
  private void cbui$stallEnd(CallbackInfo ci) {
    StallLog.end();
  }
}
