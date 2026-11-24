package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.feature.lookaround.LookAroundModule;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freelook: the camera is placed from the freelook angles (lent to the player while the camera is set up); the orbit
 * distance can glide; optional mouse smoothing.
 */
@Mixin(EntityRenderer.class)
public abstract class LookAroundEntityRendererMixin {
  @Shadow private float thirdPersonDistance;
  @Shadow private float thirdPersonDistanceTemp;
  @Unique private float cbui$ownDistance, cbui$ownDistanceTemp;
  @Unique private boolean cbui$distanceLent;

  @Inject(method = "orientCamera", at = @At("HEAD"), require = 0)
  private void cbui$lookAroundLend(float partialTicks, CallbackInfo ci) {
    LookAroundModule look = LookAroundModule.instance();
    if (look == null) return;
    look.lendAngles();
    float scale = look.orbitScale();
    if (scale < 1f && !cbui$distanceLent) {
      // the orbit distance is read several times; scale the fields themselves for the length of the method
      cbui$distanceLent = true;
      cbui$ownDistance = thirdPersonDistance;
      cbui$ownDistanceTemp = thirdPersonDistanceTemp;
      thirdPersonDistance *= scale;
      thirdPersonDistanceTemp *= scale;
    }
  }

  @Inject(method = "orientCamera", at = @At("RETURN"), require = 0)
  private void cbui$lookAroundGiveBack(float partialTicks, CallbackInfo ci) {
    if (cbui$distanceLent) {
      cbui$distanceLent = false;
      thirdPersonDistance = cbui$ownDistance;
      thirdPersonDistanceTemp = cbui$ownDistanceTemp;
    }
    LookAroundModule look = LookAroundModule.instance();
    if (look != null) look.returnAngles();
  }

  @ModifyExpressionValue(method = {"updateCameraAndRender", "updateRenderer"},
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;smoothCamera:Z"), require = 0)
  private boolean cbui$lookAroundSmoothCamera(boolean vanilla) {
    if (vanilla) return true;
    LookAroundModule look = LookAroundModule.instance();
    return look != null && look.forcesSmoothCamera();
  }

  @ModifyArg(method = "updateRenderer",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MouseFilter;smooth(FF)F"), index = 1, require = 0)
  private float cbui$lookAroundSmoothing(float vanilla) {
    LookAroundModule look = LookAroundModule.instance();
    return look == null ? vanilla : look.smoothingStep(vanilla);
  }
}
