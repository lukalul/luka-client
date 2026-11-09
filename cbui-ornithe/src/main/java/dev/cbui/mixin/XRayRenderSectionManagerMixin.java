package dev.cbui.mixin;

import dev.cbui.feature.staff.XRayState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Staff X-Ray: Celeritas' section occlusion (graph search through air, raster occluders) is switched off while X-Ray is active, so sections inside rock are built and drawn. */
@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.terrain.ArgentumRenderSectionManager", remap = false)
public abstract class XRayRenderSectionManagerMixin {
  @Inject(method = "shouldUseOcclusionCulling", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$noOcclusion(CallbackInfoReturnable<Boolean> cir) {
    if (XRayState.active) cir.setReturnValue(Boolean.FALSE);
  }

  @Inject(method = "useRasterOcclusionCulling", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$noRaster(CallbackInfoReturnable<Boolean> cir) {
    if (XRayState.active) cir.setReturnValue(Boolean.FALSE);
  }
}
