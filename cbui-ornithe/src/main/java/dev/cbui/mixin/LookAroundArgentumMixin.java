package dev.cbui.mixin;

import dev.cbui.feature.lookaround.LookAroundModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Freelook on the Argentum renderer: its terrain camera is built from the player's yaw and pitch, so the camera
 * state it records gets the freelook angles instead (otherwise the world stays put while entities follow the camera).
 */
@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer", remap = false)
public abstract class LookAroundArgentumMixin {
  @ModifyArg(method = "captureCameraState", at = @At(value = "INVOKE",
      target = "Lorg/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer$CameraState;<init>(DDDDDF)V", remap = false),
      index = 3, remap = false, require = 0)
  private static double cbui$lookAroundArgentumPitch(double vanilla) {
    LookAroundModule look = LookAroundModule.instance();
    return look == null ? vanilla : look.cameraPitch((float) vanilla);
  }

  @ModifyArg(method = "captureCameraState", at = @At(value = "INVOKE",
      target = "Lorg/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer$CameraState;<init>(DDDDDF)V", remap = false),
      index = 4, remap = false, require = 0)
  private static double cbui$lookAroundArgentumYaw(double vanilla) {
    LookAroundModule look = LookAroundModule.instance();
    return look == null ? vanilla : look.cameraYaw((float) vanilla);
  }
}
